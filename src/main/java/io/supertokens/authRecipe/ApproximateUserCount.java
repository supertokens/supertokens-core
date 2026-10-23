/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.authRecipe;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.auditlog.lifecycle.CountDeltaInterpreter;
import io.supertokens.auditlog.lifecycle.InvalidLifecycleEventPayloadException;
import io.supertokens.auditlog.lifecycle.LifecycleEventPayload;
import io.supertokens.config.Config;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.authRecipe.UsersCountCacheStorage;
import io.supertokens.pluginInterface.authRecipe.UsersCountSnapshot;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.telemetry.TelemetryProvider;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The one pipeline for user counts: telemetry's {@code usersCount}, the license-usage stats and the unfiltered tenant
 * count of {@code /users/count} on CDI 5.7 (or 5.6 with {@code allowApproximate}) go through {@link #serveApp} /
 * {@link #serveTenant} / {@link #serveTenants} (per app resource). Callers wait for a count that is still running.
 * <p>
 * <b>Layer 1, the stored exact count</b> ({@link UsersCountCacheStorage}): a full count, stored in the database and
 * reused by every core while younger than {@code users_count_max_age_ms}; one core counts while the others wait for
 * it. A storage without that capability (the in-memory db) is counted directly.
 * <p>
 * <b>Layer 2, anchors</b>: per user pool, one anchor per scope {@code {count, snapshotMs}} taken from layer 1. Getting a
 * new anchor is single-flighted per scope (one in-flight count, joined by every caller, which gets its result or its
 * failure). Background refreshes and the counts a caller waits for run on separate, bounded pools.
 * <p>
 * <b>Layer 3, the lifecycle events since the anchor</b>: the served count is the anchor plus the delta of the
 * lifecycle events logged after it ({@link CountDeltaInterpreter}): per tenant, the events that change that tenant;
 * for the app, the events that create, remove, merge or split a user group. A user pool keeps one window of parsed
 * events shared by all its scopes; each call re-reads only its last few seconds. When the events after the oldest
 * anchor no longer fit one read (a bulk
 * import), every anchor of the pool is rolled forward to (nearly) now instead of being re-counted, so a burst costs
 * one read of its events, not a full count.
 * <p>
 * An anchor older than the max age keeps serving while a background refresh gets a new one; a refresh that counted
 * anew is checked by the {@link CountShadowAudit shadow audit} against the previous count plus the events between
 * them. A count that fails (or backs off after another core's failure) falls back to the last good anchor, or the last
 * count stored in the database, marked stale; only with neither does the caller get the failure.
 * <p>
 * Accuracy: exact modulo ledger completeness (what the shadow audit checks). Events are stamped when their
 * transaction starts and can commit a little later, so an anchor is never rolled closer than
 * {@link #ROLL_MARGIN_MS} to now, each call re-reads the events of that margin, and a mutation straddling a full
 * count's snapshot self-heals at the next count. An event that commits more than the margin after its timestamp (for
 * example the user imports of a bulk-import partition, committed with the claim of the whole chunk) can be missed
 * until the scope's next full count, at most {@code users_count_max_age_ms} later.
 * <p>
 * A {@code /users/count} request does not wait behind another call's roll-forward of a burst: after
 * {@link #REQUEST_WINDOW_WAIT_MS} it gets its own scope a new count instead, as a burst cost before the window was
 * shared.
 */
public class ApproximateUserCount extends ResourceDistributor.SingletonResource {

    public static final String RESOURCE_KEY = "io.supertokens.authRecipe.ApproximateUserCount";

    // Events read per page. A pool's events after its oldest anchor that do not fit one page are rolled into the
    // anchors instead of being re-read by every call. Matches the shadow audit's burst cap.
    private static volatile int pageSize = CountDeltaInterpreter.DEFAULT_BURST_CAP;

    // An anchor is never rolled forward closer than this to now, and each call re-reads the events of this margin: an
    // event is stamped when its transaction starts and may commit a little later, after a roll-forward or a read
    // that already passed its time. One that commits later than this is only picked up by the next full count (see
    // the class doc). Raising it makes every call's re-read larger.
    static final long ROLL_MARGIN_MS = 10_000;
    private static volatile long rollMarginMs = ROLL_MARGIN_MS;


    // Pools that run the anchor counts. Each count holds a database connection while it runs, which can be minutes on
    // a large tenant, so they are bounded: a read across many tenants queues their counts instead of starting one per
    // tenant and taking the user pool's connections from the rest of the core. Background refreshes run at most 2 at a
    // time; the counts a caller waits for at most 4, on their own pool so a backlog of refreshes never holds them up.
    // Counts are single-flighted per scope, so a queue holds at most one count per scope.
    private static final ExecutorService REFRESH_EXECUTOR = newCountExecutor(2, "user-count-refresh");
    private static final ExecutorService REQUEST_COUNT_EXECUTOR = newCountExecutor(4, "user-count-anchor");
    // How many of the request pool's counts the batches of tenant counts (serveTenants) may hold at once, across all
    // apps: a batch never fills the pool or queues its tenants in it, so a request for a tenant without an anchor
    // never waits behind the counts of every tenant of an app (or, for a tenant in the batch, waits only for its own).
    private static final Semaphore BATCH_COUNT_PERMITS = new Semaphore(2);

    // How long a /users/count request waits for the pool's event window. Reading it takes milliseconds; a longer wait
    // means another call is rolling a burst into the anchors, and the request gets its own scope a new count instead.
    private static final long REQUEST_WINDOW_WAIT_MS = 2_000;

    private static final CountShadowAudit SHADOW_AUDIT = new CountShadowAudit(CountDeltaInterpreter.DEFAULT_BURST_CAP);

    private final AppIdentifier appIdentifier;

    // per user pool (storage.getUserPoolId())
    private final ConcurrentHashMap<String, PoolState> pools = new ConcurrentHashMap<>();

    private ApproximateUserCount(AppIdentifier appIdentifier) {
        this.appIdentifier = appIdentifier;
    }

    private static ExecutorService newCountExecutor(int threads, String name) {
        return Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    public static ApproximateUserCount getInstance(Main main, AppIdentifier appIdentifier)
            throws TenantOrAppNotFoundException {
        try {
            return (ApproximateUserCount) main.getResourceDistributor()
                    .getResource(appIdentifier.getAsPublicTenantIdentifier(), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            // appIdentifier comes from the API request, so guard against filling memory with resources for
            // apps that don't exist (mirrors RequestStats.getInstance).
            if (Multitenancy.getTenantInfo(main, appIdentifier.getAsPublicTenantIdentifier()) == null) {
                throw e;
            }
            return (ApproximateUserCount) main.getResourceDistributor()
                    .setResource(appIdentifier.getAsPublicTenantIdentifier(), RESOURCE_KEY,
                            new ApproximateUserCount(appIdentifier));
        }
    }

    // ---- entry points ----

    /**
     * The app's distinct user count across all its user pools. Waits for a count that is still running.
     */
    public ApproximateCountResult serveApp(Main main) throws StorageQueryException, TenantOrAppNotFoundException {
        return serveApp(main, StorageLayer.getStoragesForApp(main, appIdentifier));
    }

    /**
     * The tenant's distinct user count. Waits for a count that is still running.
     */
    public ApproximateCountResult serveTenant(Main main, TenantIdentifier tenantIdentifier)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return serve(main, tenantIdentifier, StorageLayer.getStorage(tenantIdentifier, main));
    }

    /**
     * The counts of several tenants of this app at once, keyed by tenant id (the license-usage stats). Their missing
     * counts run concurrently instead of one after another, but at most 2 at a time on the request pool (see
     * {@link #BATCH_COUNT_PERMITS}), and every tenant of a user pool is served from one event read, so the counts are
     * consistent with each other.
     */
    public Map<String, ApproximateCountResult> serveTenants(Main main, List<TenantIdentifier> tenantIdentifiers)
            throws StorageQueryException, TenantOrAppNotFoundException {
        List<Storage> storages = new ArrayList<>(tenantIdentifiers.size());
        for (TenantIdentifier tenantIdentifier : tenantIdentifiers) {
            storages.add(StorageLayer.getStorage(tenantIdentifier, main));
        }
        return serveTenants(main, tenantIdentifiers, storages);
    }

    // storages.get(i): the user pool of tenantIdentifiers.get(i)
    private Map<String, ApproximateCountResult> serveTenants(Main main, List<TenantIdentifier> tenantIdentifiers,
                                                             List<Storage> storages) throws StorageQueryException {
        long batchStart = System.currentTimeMillis();
        List<CompletableFuture<Anchor>> pending = new ArrayList<>();
        for (int i = 0; i < tenantIdentifiers.size(); i++) {
            TenantIdentifier tenantIdentifier = tenantIdentifiers.get(i);
            Storage storage = storages.get(i);
            PoolState pool = pools.computeIfAbsent(storage.getUserPoolId(), k -> new PoolState());
            Scope scope = Scope.tenant(tenantIdentifier);
            if (pool.anchors.get(scope.key) == null) {
                try {
                    BATCH_COUNT_PERMITS.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new StorageQueryException(e);
                }
                CompletableFuture<Anchor> count = startOrJoinCount(main, pool, storage, scope, 0, false);
                count.whenComplete((anchor, failure) -> BATCH_COUNT_PERMITS.release());
                pending.add(count);
            }
        }
        for (CompletableFuture<Anchor> count : pending) {
            try {
                count.get();
            } catch (ExecutionException e) {
                // served below: its fallback, or its failure
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new StorageQueryException(e);
            }
        }
        Map<String, ApproximateCountResult> results = new HashMap<>();
        for (int i = 0; i < tenantIdentifiers.size(); i++) {
            TenantIdentifier tenantIdentifier = tenantIdentifiers.get(i);
            results.put(tenantIdentifier.getTenantId(), serve(main, storages.get(i), Scope.tenant(tenantIdentifier),
                    batchStart, false));
        }
        return results;
    }

    /**
     * The tenant count on the given storage (the tenant's user pool), for a request: it does not wait behind another
     * call's roll-forward of the pool's events (see {@link #REQUEST_WINDOW_WAIT_MS}). For callers that already hold
     * the storage, and tests that wrap it.
     */
    public ApproximateCountResult serve(Main main, TenantIdentifier tenantIdentifier, Storage storage)
            throws StorageQueryException {
        return serve(main, storage, Scope.tenant(tenantIdentifier), System.currentTimeMillis(), true);
    }

    ApproximateCountResult serveApp(Main main, Storage[] storages) throws StorageQueryException {
        // Every user pool's missing count is started before any is awaited, so the pools count in parallel.
        Scope scope = Scope.app();
        for (Storage storage : storages) {
            PoolState pool = pools.computeIfAbsent(storage.getUserPoolId(), k -> new PoolState());
            if (pool.anchors.get(scope.key) == null) {
                startOrJoinCount(main, pool, storage, scope, 0, false);
            }
        }
        long count = 0;
        long asOf = Long.MAX_VALUE;
        boolean stale = false;
        for (Storage storage : storages) {
            ApproximateCountResult part = serve(main, storage, scope, System.currentTimeMillis(), false);
            count += part.count;
            asOf = Math.min(asOf, part.asOf);
            stale |= part.stale;
        }
        return new ApproximateCountResult(count, true, asOf == Long.MAX_VALUE ? System.currentTimeMillis() : asOf,
                stale);
    }

    // A shared event window read at or after reuseWindowReadAfter is used as is: a read made after the call started
    // (so concurrent callers queued on the window lock share one tail read), or one consistent read for a batch. A
    // request waits for the window at most REQUEST_WINDOW_WAIT_MS; the background jobs wait for it.
    private ApproximateCountResult serve(Main main, Storage storage, Scope scope, long reuseWindowReadAfter,
                                         boolean request) throws StorageQueryException {
        PoolState pool = pools.computeIfAbsent(storage.getUserPoolId(), k -> new PoolState());
        boolean withEvents = storage instanceof ActivityLogStorage;

        Anchor anchor = pool.anchors.get(scope.key);
        if (anchor == null) {
            anchor = awaitAnchor(main, pool, storage, scope, 0);
        } else if (isStale(main, anchor)) {
            startOrJoinCount(main, pool, storage, scope, 0, true);
        }
        if (anchor.fallback || !withEvents) {
            return new ApproximateCountResult(anchor.count, true, anchor.snapshotMs, anchor.fallback);
        }

        ActivityLogStorage activityLogStorage = (ActivityLogStorage) storage;
        for (int attempt = 0; ; attempt++) {
            Window window;
            try {
                window = window(pool, activityLogStorage, anchor.snapshotMs, attempt > 0, reuseWindowReadAfter,
                        request ? REQUEST_WINDOW_WAIT_MS : Long.MAX_VALUE);
            } catch (WindowBusyException e) {
                // Another call is rolling a burst into the pool's anchors. Rather than wait for the whole burst, get
                // this scope a count taken after it (single-flighted, as for a burst too big to roll) and serve that
                // count as is if the window is still busy.
                if (attempt > 0) {
                    return new ApproximateCountResult(anchor.count, true, anchor.snapshotMs, anchor.fallback);
                }
                anchor = awaitAnchor(main, pool, storage, scope, System.currentTimeMillis() - rollMarginMs);
                if (anchor.fallback) {
                    return new ApproximateCountResult(anchor.count, true, anchor.snapshotMs, true);
                }
                continue;
            }
            Anchor current = pool.anchors.get(scope.key);
            if (current != null && current.snapshotMs >= anchor.snapshotMs) {
                anchor = current; // rolled forward (or re-counted) meanwhile
            }
            if (window != null && anchor.snapshotMs < window.fromExclusive) {
                // a new count replaced this anchor during a roll-forward, so it is older than the shared window
                if (attempt < 3) {
                    continue;
                }
                return new ApproximateCountResult(anchor.count, true, anchor.snapshotMs, anchor.fallback);
            }
            Long forceNotBefore = null;
            Long corruptAt = window == null ? null : window.lastCorruptAfter(anchor.snapshotMs);
            if (window == null) {
                // more events after the anchor than one roll-forward could take (an extreme burst): count after them
                forceNotBefore = System.currentTimeMillis() - rollMarginMs;
            } else if (corruptAt != null) {
                // A lifecycle payload that does not parse cannot be applied. Never serve a value derived from a corrupt
                // ledger: get a count taken after the last such event (the shadow audit flags the same window on its
                // next pass).
                Logging.warn(main, appIdentifier.getAsPublicTenantIdentifier(), "User count of " + scope
                        + ": an unparseable lifecycle event payload at " + corruptAt
                        + "; serving a count taken after it instead");
                forceNotBefore = corruptAt;
            }
            if (forceNotBefore != null) {
                if (attempt > 0) {
                    return new ApproximateCountResult(anchor.count, true, anchor.snapshotMs, anchor.fallback);
                }
                anchor = awaitAnchor(main, pool, storage, scope, forceNotBefore);
                if (anchor.fallback) {
                    return new ApproximateCountResult(anchor.count, true, anchor.snapshotMs, true);
                }
                continue;
            }
            return new ApproximateCountResult(anchor.count + window.deltaAfter(anchor.snapshotMs, scope), true,
                    window.toInclusive, false);
        }
    }

    // ---- layer 2: anchors ----

    private static boolean isStale(Main main, Anchor anchor) {
        return anchor.forceStale
                || System.currentTimeMillis() - anchor.countedMs >= Config.getBaseConfig(main).getUsersCountMaxAgeMs();
    }

    /**
     * An anchor counted at or after {@code notBeforeMs} (0: any): the cached one if recent enough, else the scope's
     * in-flight count (joined or started). A joined count that turns out older than needed is followed by one more.
     * A failed count falls back to the last good anchor, or to the last count stored in the database (marked
     * {@code fallback}); with neither, its failure is thrown.
     */
    private Anchor awaitAnchor(Main main, PoolState pool, Storage storage, Scope scope, long notBeforeMs)
            throws StorageQueryException {
        while (true) {
            Anchor current = pool.anchors.get(scope.key);
            if (current != null && current.snapshotMs >= notBeforeMs && current.countedMs >= notBeforeMs) {
                return current;
            }
            CompletableFuture<Anchor> count = startOrJoinCount(main, pool, storage, scope, notBeforeMs, false);
            Anchor anchor;
            try {
                anchor = count.get();
            } catch (ExecutionException e) {
                Anchor fallback = fallback(pool, storage, scope);
                if (fallback != null) {
                    return fallback;
                }
                Throwable cause = e.getCause();
                if (cause instanceof StorageQueryException) {
                    throw (StorageQueryException) cause;
                }
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new StorageQueryException(cause instanceof Exception ? (Exception) cause : e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new StorageQueryException(e);
            }
            if (anchor.countedMs >= notBeforeMs) {
                return anchor;
            }
        }
    }

    // The last good anchor of the scope, or else the last count stored in the database, marked as a fallback.
    @Nullable
    private Anchor fallback(PoolState pool, Storage storage, Scope scope) {
        Anchor cached = pool.anchors.get(scope.key);
        if (cached != null) {
            return cached;
        }
        if (!(storage instanceof UsersCountCacheStorage)) {
            return null;
        }
        try {
            UsersCountCacheStorage cacheStorage = (UsersCountCacheStorage) storage;
            UsersCountSnapshot stored = scope.tenantId == null
                    ? cacheStorage.getStoredAppUsersCount(appIdentifier, null)
                    : cacheStorage.getStoredTenantUsersCount(scope.tenantIdentifier(appIdentifier), null);
            return stored == null ? null : Anchor.fallback(stored);
        } catch (StorageQueryException e) {
            return null;
        }
    }

    /**
     * The scope's in-flight count, starting one if there is none. It reuses a stored count only if it was counted at
     * or after {@code notBeforeMs}. A count started by the background refresh ({@code refresh}) runs on the refresh
     * pool, reports completion and, if it actually counted, runs the shadow audit against it before it is released.
     */
    private CompletableFuture<Anchor> startOrJoinCount(Main main, PoolState pool, Storage storage, Scope scope,
                                                       long notBeforeMs, boolean refresh) {
        CompletableFuture<Anchor> started = new CompletableFuture<>();
        CompletableFuture<Anchor> running = pool.inFlight.putIfAbsent(scope.key, started);
        if (running != null) {
            return running;
        }
        long configuredMaxAge = Config.getBaseConfig(main).getUsersCountMaxAgeMs();
        long maxAgeMs = notBeforeMs <= 0 ? configuredMaxAge
                : Math.max(0, Math.min(configuredMaxAge, System.currentTimeMillis() - notBeforeMs + 1));
        try {
            (refresh ? REFRESH_EXECUTOR : REQUEST_COUNT_EXECUTOR).execute(() -> {
                Anchor anchor;
                try {
                    anchor = count(storage, scope, maxAgeMs).forScope(scope, storage instanceof ActivityLogStorage);
                    pool.anchors.merge(scope.key, anchor, Anchor::newer);
                } catch (Throwable e) {
                    // removed before completing, so a woken waiter that needs another count starts a new one
                    pool.inFlight.remove(scope.key, started);
                    started.completeExceptionally(e);
                    if (refresh) {
                        // keep serving the existing (stale) anchor; the next call retries the refresh
                        ProcessState.getInstance(main).addState(
                                ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_REFRESH_FAILED,
                                e instanceof Exception ? (Exception) e : new RuntimeException(e));
                    }
                    return;
                }
                if (refresh) {
                    ProcessState.getInstance(main).addState(
                            ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_REFRESH_COMPLETED, null);
                }
                if (anchor.newlyCounted && storage instanceof ActivityLogStorage) {
                    // The exact recount doubles as a correctness audit of the lifecycle-event ledger (PLAN-010
                    // decision 4). A reused stored count is skipped, so every audit window ends at a count taken for
                    // it. Runs before the count is released, so audits of one scope never overlap; it never throws.
                    runShadowAudit(main, pool, (ActivityLogStorage) storage, scope, anchor);
                }
                pool.inFlight.remove(scope.key, started);
                started.complete(anchor);
            });
        } catch (RejectedExecutionException e) {
            pool.inFlight.remove(scope.key, started);
            started.completeExceptionally(e);
        }
        return started;
    }

    // Layer 1: the stored exact count (or a direct count on a storage without one).
    private Anchor count(Storage storage, Scope scope, long maxAgeMs) throws StorageQueryException {
        UsersCountSnapshot snapshot;
        if (storage instanceof UsersCountCacheStorage) {
            UsersCountCacheStorage cacheStorage = (UsersCountCacheStorage) storage;
            snapshot = scope.tenantId == null
                    ? cacheStorage.getOrComputeAppUsersCount(appIdentifier, null, maxAgeMs)
                    : cacheStorage.getOrComputeTenantUsersCount(scope.tenantIdentifier(appIdentifier), null, maxAgeMs);
        } else {
            long countedAt = System.currentTimeMillis();
            long count = scope.tenantId == null
                    ? StorageUtils.getAuthRecipeStorage(storage).getUsersCount(appIdentifier, null)
                    : StorageUtils.getAuthRecipeStorage(storage).getUsersCount(scope.tenantIdentifier(appIdentifier),
                    null);
            snapshot = new UsersCountSnapshot(count, countedAt, true);
        }
        return Anchor.counted(snapshot);
    }

    // ---- layer 3: the events since the anchors ----

    /**
     * The pool's lifecycle events after {@code fromMs} up to now, shared by all the pool's scopes. Each call re-reads
     * only the tail of the shared window - the events after (its end minus {@link #ROLL_MARGIN_MS}), replacing the
     * overlap so an event committed shortly after its timestamp is still picked up - instead of the whole window. If
     * the events no longer fit one page, the pool's anchors are first rolled forward. Returns null if even that leaves
     * too many events (more than a page logged within the roll margin). Throws {@link WindowBusyException} if
     * another call holds the window for longer than {@code lockWaitMs}.
     */
    @Nullable
    private Window window(PoolState pool, ActivityLogStorage storage, long fromMs, boolean reread,
                          long reuseReadAfter, long lockWaitMs) throws StorageQueryException, WindowBusyException {
        try {
            if (lockWaitMs == Long.MAX_VALUE) {
                pool.windowLock.lockInterruptibly();
            } else if (!pool.windowLock.tryLock(lockWaitMs, TimeUnit.MILLISECONDS)) {
                throw new WindowBusyException();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageQueryException(e);
        }
        try {
            Window cached = pool.window;
            if (!reread && reuseReadAfter > 0 && cached != null && cached.fromExclusive <= fromMs
                    && cached.toInclusive >= reuseReadAfter) {
                return cached; // read during this batch
            }
            if (!reread && cached != null && cached.fromExclusive <= fromMs) {
                long cut = Math.max(cached.fromExclusive, cached.toInclusive - rollMarginMs);
                long now = System.currentTimeMillis();
                List<AuditLogEvent> tail = storage.getActivityLogEntriesForApp(appIdentifier,
                        CountShadowAudit.LIFECYCLE_EVENT_TYPES, cut, now, pageSize + 1);
                if (tail.size() <= pageSize) {
                    Window extended = cached.withTail(cut, now, tail);
                    if (extended.size() <= pageSize) {
                        pool.window = extended;
                        return extended;
                    }
                }
            }
            for (int roll = 0; roll < 3; roll++) {
                long from = Math.min(fromMs, pool.oldestFoldedSnapshot());
                long now = System.currentTimeMillis();
                List<AuditLogEvent> events = storage.getActivityLogEntriesForApp(appIdentifier,
                        CountShadowAudit.LIFECYCLE_EVENT_TYPES, from, now, pageSize + 1);
                if (events.size() <= pageSize) {
                    Window window = Window.of(from, now, events);
                    pool.window = window;
                    return window;
                }
                // the time the first page of events took: the size of the roll's first slice
                long firstPageMs = events.get(pageSize - 1).createdAt - from;
                rollForward(pool, storage, now - rollMarginMs, firstPageMs);
                fromMs = Math.max(fromMs, now - rollMarginMs);
            }
            return null;
        } finally {
            pool.windowLock.unlock();
        }
    }

    /**
     * Applies the pool's lifecycle events up to {@code cutoffMs} to every event-folded anchor of the pool, reading
     * them in slices of about one page each, and moves each anchor to the cutoff. Stops before an event whose payload
     * does not parse (it can only be passed by a new count) and before a page whose events all share one millisecond
     * (it cannot be split). Called with the pool's window lock held.
     * <p>
     * Each read covers a time slice sized from the previous one (about one page of events), not everything up to the
     * cutoff: the database sorts all the events of a read's range to return its first page, so reading the rest of the
     * burst for every page would sort the burst once per page. Each event touches only the anchors of the tenants it
     * changes (and the app's), so the cost grows with the events, not with events times anchors.
     */
    private void rollForward(PoolState pool, ActivityLogStorage storage, long cutoffMs, long sliceMs)
            throws StorageQueryException {
        Anchor appAnchor = null;
        Map<String, Anchor> tenantAnchors = new HashMap<>(); // by tenant id
        Map<Anchor, String> keys = new HashMap<>();
        for (Map.Entry<String, Anchor> e : pool.anchors.entrySet()) {
            Anchor anchor = e.getValue();
            if (anchor.folded && anchor.snapshotMs < cutoffMs) {
                if (anchor.tenantId == null) {
                    appAnchor = anchor;
                } else {
                    tenantAnchors.put(anchor.tenantId, anchor);
                }
                keys.put(anchor, e.getKey());
            }
        }
        if (keys.isEmpty()) {
            return;
        }
        Map<Anchor, Long> deltas = new HashMap<>();
        long from = Long.MAX_VALUE;
        for (Anchor anchor : keys.keySet()) {
            from = Math.min(from, anchor.snapshotMs);
        }
        long rolledTo = cutoffMs;
        sliceMs = Math.max(1, sliceMs);
        pages:
        while (from < cutoffMs) {
            long to = cutoffMs - from <= sliceMs ? cutoffMs : from + sliceMs;
            List<AuditLogEvent> page = storage.getActivityLogEntriesForApp(appIdentifier,
                    CountShadowAudit.LIFECYCLE_EVENT_TYPES, from, to, pageSize + 1);
            List<AuditLogEvent> apply = page;
            long next = to;
            if (page.size() > pageSize) {
                // the page is cut at a millisecond boundary, so events sharing one millisecond stay together
                long boundary = page.get(pageSize - 1).createdAt;
                int end = pageSize;
                while (end > 0 && page.get(end - 1).createdAt == boundary) {
                    end--;
                }
                if (end == 0) {
                    rolledTo = from; // a whole page in one millisecond: cannot be split, so stop here
                    break;
                }
                apply = page.subList(0, end);
                next = page.get(end - 1).createdAt;
                sliceMs = Math.max(1, next - from); // the time that held one page
            } else if (page.size() <= pageSize / 2) {
                sliceMs = Math.min(sliceMs * 2, Long.MAX_VALUE / 4);
            }
            for (AuditLogEvent event : apply) {
                LifecycleEventPayload payload;
                try {
                    payload = LifecycleEventPayload.fromJson(event.payload);
                } catch (InvalidLifecycleEventPayloadException e) {
                    rolledTo = event.createdAt - 1; // only a new count can pass a corrupt event
                    break pages;
                }
                long appDelta = CountDeltaInterpreter.appDeltaOf(payload);
                if (appAnchor != null && appDelta != 0 && event.createdAt > appAnchor.snapshotMs) {
                    deltas.merge(appAnchor, appDelta, Long::sum);
                }
                for (Map.Entry<String, Long> tenantDelta : CountDeltaInterpreter.tenantDeltasOf(payload).entrySet()) {
                    Anchor anchor = tenantAnchors.get(tenantDelta.getKey());
                    if (anchor != null && tenantDelta.getValue() != 0 && event.createdAt > anchor.snapshotMs) {
                        deltas.merge(anchor, tenantDelta.getValue(), Long::sum);
                    }
                }
            }
            from = next;
        }
        for (Map.Entry<Anchor, String> e : keys.entrySet()) {
            Anchor anchor = e.getKey();
            if (rolledTo > anchor.snapshotMs) {
                // only if no new count replaced it meanwhile
                pool.anchors.replace(e.getValue(), anchor,
                        anchor.rolledTo(anchor.count + deltas.getOrDefault(anchor, 0L), rolledTo));
            }
        }
        pool.window = null;
    }

    // ---- shadow audit ----

    /**
     * The shadow audit (PLAN-010 unit 3), run after a count that counted anew. Checks that the previous audited count
     * of the scope plus the lifecycle events between the two counts equals the new count (see
     * {@link CountShadowAudit}). Roll-forwards in between are covered, since the window runs from one real count to
     * the next. Best-effort and observational only: any failure is swallowed, and a discrepancy is logged, never
     * served.
     */
    private void runShadowAudit(Main main, PoolState pool, ActivityLogStorage storage, Scope scope, Anchor fresh) {
        TenantIdentifier logTenant = scope.tenantId == null ? appIdentifier.getAsPublicTenantIdentifier()
                : scope.tenantIdentifier(appIdentifier);
        try {
            // Known boundary limitation (observational only): the window filters events on their emit-time
            // created_at (stamped at the start of the mutating txn), whereas the count and the event row only become
            // visible at commit. A txn straddling a count's snapshot can land on one side of the bound and its count
            // effect on the other, yielding a one-time DISCREPANCY that is a boundary artifact, not a ledger bug (it
            // self-heals in the adjacent window).
            AuditSnapshot previous = pool.audits.put(scope.key, new AuditSnapshot(fresh.count, fresh.snapshotMs));
            if (previous == null || previous.snapshotMs >= fresh.snapshotMs) {
                return; // nothing to compare against yet: only seed
            }
            List<AuditLogEvent> windowEvents = storage.getActivityLogEntriesForApp(appIdentifier,
                    CountShadowAudit.LIFECYCLE_EVENT_TYPES, previous.snapshotMs, fresh.snapshotMs,
                    SHADOW_AUDIT.getBurstCap() + 1);
            CountShadowAudit.Result result = scope.tenantId == null
                    ? SHADOW_AUDIT.evaluateApp(previous.count, fresh.count, windowEvents, previous.snapshotMs,
                    fresh.snapshotMs)
                    : SHADOW_AUDIT.evaluate(scope.tenantId, previous.count, fresh.count, windowEvents,
                    previous.snapshotMs, fresh.snapshotMs);
            switch (result.status) {
                case DISCREPANCY:
                    reportDiscrepancy(main, logTenant, result);
                    break;
                case RE_ANCHOR_REQUIRED:
                    Logging.debug(main, logTenant, "Count shadow audit skipped for " + scope + ": burst window of "
                            + result.eventCount + " events exceeds the fold cap.");
                    break;
                case MATCH:
                    ProcessState.getInstance(main).addState(
                            ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_MATCHED, null);
                    break;
                default:
                    throw new IllegalStateException("Unhandled shadow-audit status: " + result.status);
            }
        } catch (Exception e) {
            ProcessState.getInstance(main).addState(
                    ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_FAILED, e);
            Logging.warn(main, logTenant, "Count shadow audit failed for " + scope + ": " + e.getMessage());
        }
    }

    /**
     * Reports a shadow-audit discrepancy: a human-readable {@code warn} log with the full context (scope, window
     * bounds, event count, both values) plus a structured telemetry log event carrying the same fields as queryable
     * attributes. The discrepant value is never served.
     */
    private void reportDiscrepancy(Main main, TenantIdentifier tenantIdentifier, CountShadowAudit.Result result) {
        String message = "Count shadow audit discrepancy for tenant " + result.tenantId
                + ": anchor+fold expected " + result.expectedCount + " but the fresh exact count is "
                + result.freshExactCount + " (previous exact " + result.previousExactCount + " + folded delta "
                + result.foldedDelta + " over " + result.eventCount + " event(s) in window ("
                + result.windowFromExclusiveMs + ", " + result.windowToInclusiveMs
                + "]). Serving the fresh exact count.";
        Logging.warn(main, tenantIdentifier, message);

        TelemetryProvider telemetry = TelemetryProvider.getInstance(main);
        if (telemetry != null) {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("event", "count_shadow_audit_discrepancy");
            attributes.put("audit", "count_shadow");
            attributes.put("tenantId", result.tenantId);
            attributes.put("expectedCount", Long.toString(result.expectedCount));
            attributes.put("freshExactCount", Long.toString(result.freshExactCount));
            attributes.put("previousExactCount", Long.toString(result.previousExactCount));
            attributes.put("foldedDelta", Long.toString(result.foldedDelta));
            attributes.put("eventCount", Integer.toString(result.eventCount));
            attributes.put("windowFromExclusiveMs", Long.toString(result.windowFromExclusiveMs));
            attributes.put("windowToInclusiveMs", Long.toString(result.windowToInclusiveMs));
            telemetry.createLogEvent(tenantIdentifier, message, "warn", attributes);
        }

        JsonObject data = new JsonObject();
        data.addProperty("tenantId", result.tenantId);
        data.addProperty("expectedCount", result.expectedCount);
        data.addProperty("freshExactCount", result.freshExactCount);
        data.addProperty("previousExactCount", result.previousExactCount);
        data.addProperty("foldedDelta", result.foldedDelta);
        data.addProperty("eventCount", result.eventCount);
        ProcessState.getInstance(main).addState(
                ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_DISCREPANCY, null, data);
    }

    // ---- state ----

    // What is counted: the app (tenantId null) or one tenant.
    static final class Scope {
        @Nullable
        final String tenantId;
        final String key;

        private Scope(@Nullable String tenantId) {
            this.tenantId = tenantId;
            this.key = tenantId == null ? "app" : "tenant:" + tenantId;
        }

        static Scope app() {
            return new Scope(null);
        }

        static Scope tenant(TenantIdentifier tenantIdentifier) {
            return new Scope(tenantIdentifier.getTenantId());
        }

        TenantIdentifier tenantIdentifier(AppIdentifier app) {
            return new TenantIdentifier(app.getConnectionUriDomain(), app.getAppId(), tenantId);
        }

        @Override
        public String toString() {
            return key;
        }
    }

    private static final class PoolState {
        final ConcurrentHashMap<String, Anchor> anchors = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, CompletableFuture<Anchor>> inFlight = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, AuditSnapshot> audits = new ConcurrentHashMap<>();
        final ReentrantLock windowLock = new ReentrantLock();
        volatile Window window;

        long oldestFoldedSnapshot() {
            long oldest = Long.MAX_VALUE;
            for (Anchor anchor : anchors.values()) {
                if (anchor.folded && !anchor.fallback) {
                    oldest = Math.min(oldest, anchor.snapshotMs);
                }
            }
            return oldest;
        }
    }

    /**
     * An anchor: the count as of {@code snapshotMs}. {@code countedMs} is the time of the full count it is built on
     * (a roll-forward moves the snapshot, not the count time), which decides when it is stale.
     */
    private static final class Anchor {
        final long count;
        final long snapshotMs;
        final long countedMs;
        final boolean newlyCounted;
        @Nullable
        final String tenantId; // filled in for event-folded anchors (see folded)
        final boolean folded;
        final boolean fallback; // the last stored count, served because a new count failed
        final boolean forceStale; // test seam

        private Anchor(long count, long snapshotMs, long countedMs, boolean newlyCounted, @Nullable String tenantId,
                       boolean folded, boolean fallback, boolean forceStale) {
            this.count = count;
            this.snapshotMs = snapshotMs;
            this.countedMs = countedMs;
            this.newlyCounted = newlyCounted;
            this.tenantId = tenantId;
            this.folded = folded;
            this.fallback = fallback;
            this.forceStale = forceStale;
        }

        static Anchor counted(UsersCountSnapshot snapshot) {
            return new Anchor(snapshot.count, snapshot.countedAtMs, snapshot.countedAtMs, snapshot.newlyCounted, null,
                    false, false, false);
        }

        static Anchor fallback(UsersCountSnapshot stored) {
            return new Anchor(stored.count, stored.countedAtMs, stored.countedAtMs, false, null, false, true, false);
        }

        Anchor forScope(Scope scope, boolean folded) {
            return new Anchor(count, snapshotMs, countedMs, newlyCounted, scope.tenantId, folded, fallback,
                    forceStale);
        }

        Anchor rolledTo(long newCount, long newSnapshotMs) {
            return new Anchor(newCount, newSnapshotMs, countedMs, false, tenantId, folded, false, false);
        }

        Anchor stale() {
            return new Anchor(count, snapshotMs, countedMs, newlyCounted, tenantId, folded, fallback, true);
        }

        // merge rule: a later full count wins; for the same count, the later snapshot (a roll-forward) wins
        static Anchor newer(Anchor old, Anchor fresh) {
            if (fresh.countedMs > old.countedMs) {
                return fresh;
            }
            if (fresh.countedMs == old.countedMs && fresh.snapshotMs > old.snapshotMs) {
                return fresh;
            }
            return old.forceStale && fresh.countedMs == old.countedMs ? fresh : old;
        }
    }

    // A pool's parsed lifecycle events in (fromExclusive, toInclusive].
    private static final class Window {
        final long fromExclusive;
        final long toInclusive;
        final long readAt;
        final long[] times; // per parsed event: its created_at
        final List<Map<String, Long>> tenantDeltas;
        final List<Long> appDeltas;
        final long[] corruptTimes; // the created_at of every unparseable event, ascending

        private Window(long fromExclusive, long toInclusive, long[] times, List<Map<String, Long>> tenantDeltas,
                       List<Long> appDeltas, long[] corruptTimes) {
            this.fromExclusive = fromExclusive;
            this.toInclusive = toInclusive;
            this.readAt = System.currentTimeMillis();
            this.times = times;
            this.tenantDeltas = tenantDeltas;
            this.appDeltas = appDeltas;
            this.corruptTimes = corruptTimes;
        }

        static Window of(long fromExclusive, long toInclusive, List<AuditLogEvent> events) {
            List<Long> times = new ArrayList<>(events.size());
            List<Map<String, Long>> tenantDeltas = new ArrayList<>(events.size());
            List<Long> appDeltas = new ArrayList<>(events.size());
            List<Long> corruptTimes = new ArrayList<>();
            for (AuditLogEvent event : events) {
                try {
                    LifecycleEventPayload payload = LifecycleEventPayload.fromJson(event.payload);
                    times.add(event.createdAt);
                    tenantDeltas.add(CountDeltaInterpreter.tenantDeltasOf(payload));
                    appDeltas.add(CountDeltaInterpreter.appDeltaOf(payload));
                } catch (InvalidLifecycleEventPayloadException e) {
                    corruptTimes.add(event.createdAt);
                }
            }
            long[] sortedCorruptTimes = corruptTimes.stream().mapToLong(Long::longValue).toArray();
            Arrays.sort(sortedCorruptTimes);
            return new Window(fromExclusive, toInclusive, times.stream().mapToLong(Long::longValue).toArray(),
                    tenantDeltas, appDeltas, sortedCorruptTimes);
        }

        // the last unparseable event after snapshotMs, or null if there is none
        @Nullable
        Long lastCorruptAfter(long snapshotMs) {
            if (corruptTimes.length == 0 || corruptTimes[corruptTimes.length - 1] <= snapshotMs) {
                return null;
            }
            return corruptTimes[corruptTimes.length - 1];
        }

        int size() {
            return times.length;
        }

        // this window's events up to and including cut, followed by the given (newly read) events in (cut, now]
        Window withTail(long cut, long now, List<AuditLogEvent> tail) {
            Window fresh = of(cut, now, tail);
            int keep = 0;
            while (keep < times.length && times[keep] <= cut) {
                keep++;
            }
            long[] mergedTimes = new long[keep + fresh.times.length];
            System.arraycopy(times, 0, mergedTimes, 0, keep);
            System.arraycopy(fresh.times, 0, mergedTimes, keep, fresh.times.length);
            List<Map<String, Long>> mergedTenantDeltas = new ArrayList<>(tenantDeltas.subList(0, keep));
            mergedTenantDeltas.addAll(fresh.tenantDeltas);
            List<Long> mergedAppDeltas = new ArrayList<>(appDeltas.subList(0, keep));
            mergedAppDeltas.addAll(fresh.appDeltas);
            int keepCorrupt = 0;
            while (keepCorrupt < corruptTimes.length && corruptTimes[keepCorrupt] <= cut) {
                keepCorrupt++;
            }
            long[] mergedCorruptTimes = new long[keepCorrupt + fresh.corruptTimes.length];
            System.arraycopy(corruptTimes, 0, mergedCorruptTimes, 0, keepCorrupt);
            System.arraycopy(fresh.corruptTimes, 0, mergedCorruptTimes, keepCorrupt, fresh.corruptTimes.length);
            return new Window(fromExclusive, now, mergedTimes, mergedTenantDeltas, mergedAppDeltas,
                    mergedCorruptTimes);
        }

        long deltaAfter(long snapshotMs, Scope scope) {
            long delta = 0;
            for (int i = 0; i < times.length; i++) {
                if (times[i] > snapshotMs) {
                    delta += scope.tenantId == null ? appDeltas.get(i)
                            : tenantDeltas.get(i).getOrDefault(scope.tenantId, 0L);
                }
            }
            return delta;
        }
    }

    // Another call held the pool's event window for longer than the caller waits (it is rolling a burst forward).
    private static final class WindowBusyException extends Exception {
        WindowBusyException() {
            super(null, null, false, false);
        }
    }

    // One audited full count of a scope: the next counted refresh checks the events since it.
    private static final class AuditSnapshot {
        final long count;
        final long snapshotMs;

        AuditSnapshot(long count, long snapshotMs) {
            this.count = count;
            this.snapshotMs = snapshotMs;
        }
    }

    public static class ApproximateCountResult {
        // The served count: the anchor's count plus the delta of the lifecycle events since it.
        public final long count;
        // Whether a cached count was used (always true here; the caller reports false when it serves exact).
        public final boolean approximate;
        // The epoch-ms instant the served value is "as of".
        public final long asOf;
        // A new count failed and this is the last count stored in the database.
        public final boolean stale;

        public ApproximateCountResult(long count, boolean approximate, long asOf) {
            this(count, approximate, asOf, false);
        }

        public ApproximateCountResult(long count, boolean approximate, long asOf, boolean stale) {
            this.count = count;
            this.approximate = approximate;
            this.asOf = asOf;
            this.stale = stale;
        }
    }

    // ---- test seams ----

    // The events per page, and how close to now anchors are rolled; restore with resetForTesting().
    public static void setPageSizeAndRollMarginForTesting(int events, long marginMs) {
        pageSize = events;
        rollMarginMs = marginMs;
    }

    public static void resetForTesting() {
        pageSize = CountDeltaInterpreter.DEFAULT_BURST_CAP;
        rollMarginMs = ROLL_MARGIN_MS;
    }

    // The app count across the given storages (one per user pool), as for telemetry.
    public ApproximateCountResult serveAppForTesting(Main main, Storage[] storages) throws StorageQueryException {
        return serveApp(main, storages);
    }

    // The counts of the given tenants, all on the given storage, as for the license-usage stats.
    public Map<String, ApproximateCountResult> serveTenantsForTesting(Main main, List<TenantIdentifier> tenants,
                                                                      Storage storage) throws StorageQueryException {
        return serveTenants(main, tenants, Collections.nCopies(tenants.size(), storage));
    }

    // The tenant's anchor snapshot time on the given storage's pool, or -1.
    public long anchorSnapshotForTesting(TenantIdentifier tenantIdentifier, Storage storage) {
        PoolState pool = pools.get(storage.getUserPoolId());
        Anchor anchor = pool == null ? null : pool.anchors.get(Scope.tenant(tenantIdentifier).key);
        return anchor == null ? -1 : anchor.snapshotMs;
    }

    // Makes the tenant's cached anchors stale, so the next serve() triggers a background refresh.
    public void expireEntryForTesting(TenantIdentifier tenantIdentifier) {
        expireForTesting(Scope.tenant(tenantIdentifier).key);
    }

    // Makes the app's cached anchors stale, so the next serveApp() triggers a background refresh.
    public void expireAppEntryForTesting() {
        expireForTesting(Scope.app().key);
    }

    private void expireForTesting(String key) {
        for (PoolState pool : pools.values()) {
            pool.anchors.computeIfPresent(key, (k, anchor) -> anchor.stale());
        }
    }

    // Whether the tenant has a cached anchor.
    public boolean hasAnchorForTesting(TenantIdentifier tenantIdentifier) {
        String key = Scope.tenant(tenantIdentifier).key;
        return pools.values().stream().anyMatch(pool -> pool.anchors.containsKey(key));
    }

    // Whether an anchor count (and, for a refresh, its shadow audit) is still running for the tenant.
    public boolean isCountingForTesting(TenantIdentifier tenantIdentifier) {
        String key = Scope.tenant(tenantIdentifier).key;
        return pools.values().stream().anyMatch(pool -> pool.inFlight.containsKey(key));
    }

    // Runs one shadow-audit pass for the tenant synchronously with a new full count, without touching the served
    // anchor: seeds the snapshot on the first call and compares against it on later ones.
    public void runShadowAuditForTesting(Main main, TenantIdentifier tenantIdentifier, Storage storage)
            throws StorageQueryException {
        Scope scope = Scope.tenant(tenantIdentifier);
        PoolState pool = pools.computeIfAbsent(storage.getUserPoolId(), k -> new PoolState());
        runShadowAudit(main, pool, (ActivityLogStorage) storage, scope, count(storage, scope, 0));
    }
}
