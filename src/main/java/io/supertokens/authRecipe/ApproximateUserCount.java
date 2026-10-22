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
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.authRecipe.UsersCountSnapshot;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.telemetry.TelemetryProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Serving path for the tenant user count (PLAN-009 skeleton, PLAN-010 units "ledger-fold" and "default-flip").
 * As of "default-flip" this is the default single-tenant, unfiltered count path - the {@code allowApproximate}
 * request parameter is a vestigial no-op; the slow exact recompute survives only as the background anchor
 * refresh (which doubles as the shadow audit).
 * Holds a per-tenant in-memory anchor {@code {exactCount, snapshotMs, computedAt}} — the exact count taken at
 * {@code snapshotMs} — and serves {@code exactCount + fold(lifecycle events in (snapshotMs, now])} at request
 * time (single-digit ms), refreshing the anchor in the background with stale-while-revalidate semantics.
 * <p>
 * The delta is folded from the lifecycle-event ledger by the shared {@link CountDeltaInterpreter}, replacing
 * PLAN-009's joined-since query. That query could only see creations; the ledger records every count-affecting
 * mutation, so the served value is now exact for deletions, account linking and unlinking too — a silent
 * accuracy upgrade with no API surface change. This is the same {@code anchor + fold} arithmetic the
 * {@link CountShadowAudit shadow audit} continuously checks against a fresh exact recompute, so the audit
 * directly validates what is served.
 * <p>
 * <b>Burst cap.</b> If the window since the anchor holds more than {@link CountDeltaInterpreter#getBurstCap()}
 * events (a bulk import or mass deletion), folding is slow and pointless because the anchor is far behind
 * regardless, so the anchor is re-computed immediately and the fresh exact count served — the semantically
 * correct response to mass mutation.
 * <p>
 * The served anchors are cached per instance and per (app, tenant). The exact counts behind them come from
 * {@link StoredUsersCount}: stored in the database and reused by every core for up to 10 minutes, with one core
 * counting while the others wait. So a restarted core usually starts from a stored count instead of running its
 * own, and within a core the tenant's anchor counts are single-flighted (one in flight, joined by every caller).
 * <p>
 * Accuracy: exact modulo ledger completeness (the invariant the shadow audit gates on). The only residual lag
 * is the emit-time/commit-time boundary artifact documented on the shadow audit, which self-heals at the next
 * anchor refresh. This is not a statistical estimate and has no error margin.
 */
public class ApproximateUserCount extends ResourceDistributor.SingletonResource {

    public static final String RESOURCE_KEY = "io.supertokens.authRecipe.ApproximateUserCount";

    // Entries older than this trigger a background refresh; the stale entry keeps being served until the
    // refresh completes (stale-while-revalidate). A constant for now (not configurable), per the design.
    private static final long REFRESH_TTL_MS = 10 * 60 * 1000L; // 10 minutes

    // The read-side fold of lifecycle events into per-tenant count deltas, and the burst-cap policy. Shares the
    // interpreter's default cap with the shadow audit so serving and its audit re-anchor on the same threshold;
    // making the cap configurable is a follow-up the interpreter's Javadoc anticipates.
    private static final CountDeltaInterpreter INTERPRETER = new CountDeltaInterpreter(
            CountDeltaInterpreter.DEFAULT_BURST_CAP);

    // Pools that run the anchor counts. Each count holds a database connection while it runs, which can be minutes on
    // a large tenant, so they are bounded: a read across many tenants queues their counts instead of starting one per
    // tenant and taking the user pool's connections from the rest of the core. Background refreshes run at most 2 at a
    // time (as before); the counts a request waits for (the first-request prime, a burst or corrupt-ledger re-anchor)
    // at most 4, on their own pool so a backlog of refreshes never holds them up. Counts are single-flighted per
    // tenant, so a queue holds at most one count per tenant.
    private static final ExecutorService REFRESH_EXECUTOR = newCountExecutor(2, "approximate-user-count-refresh");
    private static final ExecutorService REQUEST_COUNT_EXECUTOR = newCountExecutor(4, "approximate-user-count-anchor");

    // How long a request waits for an anchor count before failing - the bound the pool's 60s socket timeout used to
    // put on a synchronous count. The count keeps running past it and stores its anchor for the requests after.
    private static final long ANCHOR_WAIT_MS = 60 * 1000L;

    // Cache keyed by tenant id within this per-app resource.
    private final ConcurrentHashMap<String, CachedAnchor> entries = new ConcurrentHashMap<>();

    // The in-flight anchor count per tenant. Every caller that needs a new anchor while one is being counted joins it,
    // so one count runs and all of them get its anchor - or its failure, instead of each retrying it in turn.
    private final ConcurrentHashMap<String, CompletableFuture<CachedAnchor>> inFlight = new ConcurrentHashMap<>();

    // Shadow audit (PLAN-010 unit 3): once per background refresh, check that the exact count moved by exactly
    // the delta folded from the lifecycle events in the window since the last refresh. Purely observational -
    // it never affects the value served (the fresh exact count always wins). The burst cap matches the
    // interpreter's default; the serving path (a later unit) is where it becomes configurable.
    private static final CountShadowAudit SHADOW_AUDIT = new CountShadowAudit(CountDeltaInterpreter.DEFAULT_BURST_CAP);

    // Per-tenant audit snapshot {exact count, snapshot time} from the last refresh, so the next refresh knows
    // the window's lower bound and the count to advance from. Separate from the served anchor cache above.
    private final ConcurrentHashMap<String, AuditSnapshot> auditSnapshots = new ConcurrentHashMap<>();

    private ApproximateUserCount() {
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
                            new ApproximateUserCount());
        }
    }

    /**
     * Serves the approximate user count for {@code tenantIdentifier} as {@code exactCount + fold(lifecycle
     * events since the anchor snapshot)}. On the very first request for a tenant (no anchor) the exact count is
     * computed synchronously to prime the anchor; afterwards each request folds the cheap event window over the
     * cached exact count, and a stale anchor triggers a background refresh while the current one keeps serving.
     * A window over the burst cap re-anchors immediately instead of folding.
     */
    public ApproximateCountResult serve(Main main, TenantIdentifier tenantIdentifier, Storage storage)
            throws StorageQueryException {
        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        String tenantId = tenantIdentifier.getTenantId();

        if (!(storage instanceof ActivityLogStorage)) {
            // No lifecycle-event ledger to fold against (no SQL storage in the fleet is in this state). Serve
            // the exact count directly rather than an approximation we cannot bring up to date. This is a
            // freshly-computed exact count as of now, so approximate is false (matching the contract: the
            // caller reports false when it serves exact).
            return new ApproximateCountResult(authRecipeStorage.getUsersCount(tenantIdentifier, null), false,
                    System.currentTimeMillis());
        }
        ActivityLogStorage activityLogStorage = (ActivityLogStorage) storage;

        long now = System.currentTimeMillis();
        CachedAnchor cachedAnchor = entries.get(tenantId);
        if (cachedAnchor == null) {
            // First request for this tenant: get the exact anchor before serving so subsequent requests fold off a
            // cheap event window instead of recomputing the exact count.
            cachedAnchor = awaitAnchor(main, tenantIdentifier, storage, 0);
        } else if (now - cachedAnchor.computedAt >= REFRESH_TTL_MS) {
            // Stale: refresh the anchor in the background (single-flight) and keep folding off the current one.
            triggerRefresh(main, tenantIdentifier, storage);
        }

        // Fetch the lifecycle events committed since the anchor snapshot, app-scoped (BRIN-friendly created_at
        // scan over an append-only table), bounded to burstCap+1 rows so a burst is detected without
        // materialising all of it. The window is half-open (snapshotMs, now]: the anchor already reflects
        // everything up to and including snapshotMs.
        List<AuditLogEvent> windowEvents = activityLogStorage.getActivityLogEntriesForApp(
                tenantIdentifier.toAppIdentifier(), CountShadowAudit.LIFECYCLE_EVENT_TYPES,
                cachedAnchor.snapshotMs, now, INTERPRETER.getBurstCap() + 1);

        if (windowEvents.size() > INTERPRETER.getBurstCap()) {
            // Burst (bulk import / mass deletion): folding is slow and pointless because the anchor is far
            // behind regardless, so re-anchor immediately and serve an exact count taken after the burst.
            CachedAnchor reAnchored = awaitAnchor(main, tenantIdentifier, storage, newestEventTime(windowEvents));
            return new ApproximateCountResult(reAnchored.exactCount, true, reAnchored.snapshotMs);
        }

        try {
            List<LifecycleEventPayload> payloads = new ArrayList<>(windowEvents.size());
            for (AuditLogEvent event : windowEvents) {
                payloads.add(LifecycleEventPayload.fromJson(event.payload));
            }
            long delta = CountDeltaInterpreter.computeDeltaForTenant(payloads, tenantId);
            return new ApproximateCountResult(cachedAnchor.exactCount + delta, true, now);
        } catch (InvalidLifecycleEventPayloadException e) {
            // A stored payload does not parse against the schema — a ledger-integrity problem, not a serving
            // input we can fold. Never serve a value derived from a corrupt ledger: re-anchor to the fresh
            // exact count and surface the problem (the shadow audit will flag the same window on its next pass).
            Logging.warn(main, tenantIdentifier, "Approximate user count fold aborted for tenant " + tenantId
                    + " due to an unparseable lifecycle event payload; serving the fresh exact count instead: "
                    + e.getMessage());
            CachedAnchor reAnchored = awaitAnchor(main, tenantIdentifier, storage, newestEventTime(windowEvents));
            return new ApproximateCountResult(reAnchored.exactCount, true, reAnchored.snapshotMs);
        }
    }

    private static long newestEventTime(List<AuditLogEvent> events) {
        long newest = 0;
        for (AuditLogEvent event : events) {
            newest = Math.max(newest, event.createdAt);
        }
        return newest;
    }

    /**
     * Gets an anchor: the tenant's exact user count paired with the snapshot time bounding the fold window it will be
     * carried forward by. The count comes from {@link StoredUsersCount}, so it is shared by every core through the
     * database, and a stored count is reused only if younger than {@code maxAgeMs}. {@code snapshotMs} is the time
     * the count started, so a mutation committing after it is folded (its created_at is at or after snapshotMs)
     * rather than lost - the same boundary discipline the shadow audit uses. A mutation whose transaction straddles
     * snapshotMs (created_at before, commit after) is the one documented boundary artifact; it self-heals at the next
     * refresh.
     */
    private CachedAnchor computeAnchor(Storage storage, TenantIdentifier tenantIdentifier, long maxAgeMs)
            throws StorageQueryException {
        UsersCountSnapshot count = StoredUsersCount.getTenantUsersCountSnapshot(tenantIdentifier, storage, maxAgeMs);
        return new CachedAnchor(count.count, count.countedAtMs, System.currentTimeMillis(), count.newlyCounted);
    }

    /**
     * Returns an anchor counted at or after {@code notBeforeMs} (0: any), waiting at most {@link #ANCHOR_WAIT_MS}. It
     * reuses the cached anchor if that is recent enough, else joins the tenant's in-flight count, else starts one; a
     * joined count that turns out older than needed (it was started for an earlier bound) is followed by one more.
     * A failed count fails every caller waiting on it.
     */
    private CachedAnchor awaitAnchor(Main main, TenantIdentifier tenantIdentifier, Storage storage, long notBeforeMs)
            throws StorageQueryException {
        long deadline = System.currentTimeMillis() + ANCHOR_WAIT_MS;
        while (true) {
            CachedAnchor current = entries.get(tenantIdentifier.getTenantId());
            if (current != null && current.snapshotMs >= notBeforeMs) {
                return current;
            }
            CompletableFuture<CachedAnchor> count = startOrJoinCount(main, tenantIdentifier, storage, notBeforeMs,
                    false);
            CachedAnchor anchor;
            try {
                anchor = count.get(Math.max(0, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof StorageQueryException) {
                    throw (StorageQueryException) cause;
                }
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new StorageQueryException(cause instanceof Exception ? (Exception) cause : e);
            } catch (TimeoutException e) {
                throw new StorageQueryException(new TimeoutException("Gave up waiting for the user count of tenant "
                        + tenantIdentifier.getTenantId() + " after " + ANCHOR_WAIT_MS
                        + "ms; it is still being counted and later requests will use it"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new StorageQueryException(e);
            }
            if (anchor.snapshotMs >= notBeforeMs) {
                return anchor;
            }
        }
    }

    /**
     * The tenant's in-flight anchor count, starting one if there is none: on {@link #REFRESH_EXECUTOR} for the
     * background refresh, else on {@link #REQUEST_COUNT_EXECUTOR}. A new count reuses a stored count only if it was
     * counted at or after {@code notBeforeMs}. A count started by the background refresh ({@code refresh}) reports
     * completion and, if it actually counted (not a reused stored count), runs the shadow audit against it.
     */
    private CompletableFuture<CachedAnchor> startOrJoinCount(Main main, TenantIdentifier tenantIdentifier,
                                                             Storage storage, long notBeforeMs, boolean refresh) {
        String tenantId = tenantIdentifier.getTenantId();
        CompletableFuture<CachedAnchor> started = new CompletableFuture<>();
        CompletableFuture<CachedAnchor> running = inFlight.putIfAbsent(tenantId, started);
        if (running != null) {
            return running;
        }
        long maxAgeMs = notBeforeMs <= 0 ? StoredUsersCount.MAX_AGE_MS
                : Math.max(0, System.currentTimeMillis() - notBeforeMs + 1);
        try {
            (refresh ? REFRESH_EXECUTOR : REQUEST_COUNT_EXECUTOR).execute(() -> {
                CachedAnchor anchor;
                try {
                    anchor = computeAnchor(storage, tenantIdentifier, maxAgeMs);
                    // keep whichever anchor is newer (an equal snapshot is the same count, re-dated)
                    entries.merge(tenantId, anchor, (old, fresh) -> fresh.snapshotMs >= old.snapshotMs ? fresh : old);
                } catch (Throwable e) {
                    // removed before completing, so a woken waiter that needs another count starts a new one
                    inFlight.remove(tenantId, started);
                    started.completeExceptionally(e);
                    if (refresh) {
                        // keep serving the existing (stale) entry; the next request retries the refresh
                        ProcessState.getInstance(main).addState(
                                ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_REFRESH_FAILED,
                                e instanceof Exception ? (Exception) e : new RuntimeException(e));
                    }
                    return;
                }
                if (refresh) {
                    ProcessState.getInstance(main).addState(
                            ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_REFRESH_COMPLETED, null);
                    if (anchor.newlyCounted) {
                        // The exact recount this refresh performed doubles as a correctness audit of the
                        // lifecycle-event ledger (PLAN-010 decision 4). A reused stored count is skipped, so every
                        // audit window ends at a count taken for it. Runs before the count is released (it is a
                        // bounded event-window read), so audits of one tenant never overlap; it never throws.
                        runShadowAudit(main, tenantIdentifier, storage, anchor);
                    }
                }
                inFlight.remove(tenantId, started);
                started.complete(anchor);
            });
        } catch (RejectedExecutionException e) {
            inFlight.remove(tenantId, started);
            started.completeExceptionally(e);
        }
        return started;
    }

    // Stale anchor: count a new one in the background (joining any count already in flight) and keep serving.
    private void triggerRefresh(Main main, TenantIdentifier tenantIdentifier, Storage storage) {
        startOrJoinCount(main, tenantIdentifier, storage, 0, true);
    }

    /**
     * The shadow audit (PLAN-010 unit 3), run after a background refresh that counted anew. Takes that refresh's
     * exact tenant count ({@code fresh}, with the time it was counted), reads the lifecycle events committed since
     * the previous audited count, and checks that the count
     * moved by exactly the folded delta (see {@link CountShadowAudit}). Best-effort and observational only:
     * any failure here is swallowed so it can never disturb serving or the refresh that just succeeded, and a
     * discrepancy is logged, never served — the fresh exact count already won.
     */
    private void runShadowAudit(Main main, TenantIdentifier tenantIdentifier, Storage storage, CachedAnchor fresh) {
        String tenantId = tenantIdentifier.getTenantId();
        try {
            if (!(storage instanceof ActivityLogStorage)) {
                return; // storage keeps no lifecycle-event ledger, so there is nothing to fold against
            }
            ActivityLogStorage activityLogStorage = (ActivityLogStorage) storage;

            // Known boundary limitation (observational only): the window below filters events on their
            // emit-time created_at (stamped at the start of the mutating txn), whereas freshExactCount and the
            // event row only become visible at commit. A txn straddling snapshotMs can land its created_at on
            // one side of the bound and its count effect on the other, yielding a one-time DISCREPANCY that is
            // a boundary artifact, not a ledger bug (it self-heals as the misassigned mutation is folded in
            // the adjacent window). Serving now uses this same anchor+fold model with the same no-margin
            // window, so it carries the identical self-healing artifact; no analogous margin is applied here
            // because a robust fix needs an as-of-time exact count we do not have (and gating on persistence
            // across refreshes would also mask genuine one-time missed emits, which produce the same single
            // self-healing discrepancy). Tracked as a soak follow-up.
            long snapshotMs = fresh.snapshotMs;
            long freshExactCount = fresh.exactCount;

            AuditSnapshot previous = auditSnapshots.get(tenantId);
            // Advance the snapshot regardless, so the next refresh's window starts where this one ends.
            auditSnapshots.put(tenantId, new AuditSnapshot(freshExactCount, snapshotMs));
            if (previous == null) {
                return; // first refresh for this tenant: no prior snapshot to bound a window, only seed one
            }

            // cap + 1 so an over-cap window is detected from the row count without materialising all of it.
            // Note the read is app-scoped (getActivityLogEntriesForApp filters on app_id, not tenant), so the
            // burst cap bounds app-wide event volume, not this tenant's alone: on a busy multi-tenant app the
            // app-wide window between two refreshes can exceed the cap and RE_ANCHOR a quiet tenant's audit,
            // and each per-tenant refresh may fold up to burstCap app-wide rows. Tenant-scoping the read would
            // tighten coverage but is a plugin-interface storage-contract change, deferred to a follow-up.
            List<AuditLogEvent> windowEvents = activityLogStorage.getActivityLogEntriesForApp(
                    tenantIdentifier.toAppIdentifier(), CountShadowAudit.LIFECYCLE_EVENT_TYPES,
                    previous.snapshotMs, snapshotMs, SHADOW_AUDIT.getBurstCap() + 1);

            CountShadowAudit.Result result = SHADOW_AUDIT.evaluate(tenantId, previous.exactCount,
                    freshExactCount, windowEvents, previous.snapshotMs, snapshotMs);

            switch (result.status) {
                case DISCREPANCY:
                    reportDiscrepancy(main, tenantIdentifier, result);
                    break;
                case RE_ANCHOR_REQUIRED:
                    // Window too large to fold; the fresh anchor has already re-based past it. Not a bug.
                    Logging.debug(main, tenantIdentifier, "Count shadow audit skipped for tenant " + tenantId
                            + ": burst window of " + result.eventCount + " events exceeds the fold cap.");
                    break;
                case MATCH:
                    ProcessState.getInstance(main).addState(
                            ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_MATCHED, null);
                    break;
                default:
                    // Future-proofing: a status added later must be handled explicitly, never fall through
                    // to "matched" silently. Surfacing it as an audit failure (via the outer catch: FAILED
                    // ProcessState + warn) makes the gap loud without disturbing serving.
                    throw new IllegalStateException("Unhandled shadow-audit status: " + result.status);
            }
        } catch (Exception e) {
            // Observability, not correctness: never let an audit failure escape into the refresh path.
            ProcessState.getInstance(main).addState(
                    ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_FAILED, e);
            Logging.warn(main, tenantIdentifier,
                    "Count shadow audit failed for tenant " + tenantId + ": " + e.getMessage());
        }
    }

    /**
     * Reports a shadow-audit discrepancy: a human-readable {@code warn} log with the full context the issue
     * asks for (tenant, window bounds, event count, both values) plus a structured telemetry log event —
     * the same message body carrying the individual fields as queryable attributes for alerting. The
     * telemetry event is the only telemetry primitive the core exposes (no metric/counter API); it is
     * emitted directly rather than only via {@code Logging.warn} so the discrepancy still reaches the
     * collector when {@code warn} logging is turned down in config. The discrepant value is never served.
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

    // One audit snapshot for a tenant: the exact count taken at a background refresh and when it was taken.
    // The next refresh reads the window since {@code snapshotMs} and checks the count moved by the folded
    // delta. Deliberately independent of the served anchor, which is rebased/creations-only-exact.
    private static class AuditSnapshot {
        final long exactCount;
        final long snapshotMs;

        AuditSnapshot(long exactCount, long snapshotMs) {
            this.exactCount = exactCount;
            this.snapshotMs = snapshotMs;
        }
    }

    // One cached anchor for a tenant: the exact count, the snapshot time it was taken at (the exclusive lower
    // bound of the fold window that carries it forward), and when it was computed (used to decide staleness).
    private static class CachedAnchor {
        final long exactCount;
        final long snapshotMs; // the epoch-ms snapshot the exact count is as-of; fold window lower bound (excl.)
        final long computedAt; // when this core got the anchor; drives the refresh TTL
        final boolean newlyCounted; // the count was run for this anchor, not reused from the stored count

        CachedAnchor(long exactCount, long snapshotMs, long computedAt, boolean newlyCounted) {
            this.exactCount = exactCount;
            this.snapshotMs = snapshotMs;
            this.computedAt = computedAt;
            this.newlyCounted = newlyCounted;
        }
    }

    public static class ApproximateCountResult {
        // The served count: the anchor's exact count plus the delta folded from the lifecycle events since it.
        public final long count;
        // Whether a cached snapshot was used (always true here; the caller reports false when it serves exact).
        public final boolean approximate;
        // The epoch-ms instant the served value is "as of" (the fold window's upper bound).
        public final long asOf;

        public ApproximateCountResult(long count, boolean approximate, long asOf) {
            this.count = count;
            this.approximate = approximate;
            this.asOf = asOf;
        }
    }

    // Test-only seam: ages the cached anchor for a tenant so the next serve() sees it as stale and triggers a
    // background refresh. Does not exist as a runtime knob - the refresh TTL stays a constant per the design;
    // this only lets tests exercise the stale-while-revalidate path without waiting out the real TTL.
    public void expireEntryForTesting(TenantIdentifier tenantIdentifier) {
        entries.computeIfPresent(tenantIdentifier.getTenantId(),
                (k, e) -> new CachedAnchor(e.exactCount, e.snapshotMs, 0, e.newlyCounted));
    }

    // Test-only seam: whether a tenant has a cached anchor.
    public boolean hasAnchorForTesting(TenantIdentifier tenantIdentifier) {
        return entries.containsKey(tenantIdentifier.getTenantId());
    }

    // Test-only seam: whether an anchor count (and, for a refresh, its shadow audit) is still running for a tenant.
    public boolean isCountingForTesting(TenantIdentifier tenantIdentifier) {
        return inFlight.containsKey(tenantIdentifier.getTenantId());
    }

    // Test-only seam: runs one shadow-audit pass synchronously - seeding the snapshot on the first call for a
    // tenant and comparing against the previous snapshot on later calls - so tests can exercise the audit's
    // real storage reads and fold deterministically, without driving the background refresh executor (which
    // staleEntryTriggersBackgroundRefresh already covers). Not a runtime entry point; mirrors
    // expireEntryForTesting. Takes a new exact count, as a refresh that counts would, without touching the served
    // anchor.
    public void runShadowAuditForTesting(Main main, TenantIdentifier tenantIdentifier, Storage storage)
            throws StorageQueryException {
        runShadowAudit(main, tenantIdentifier, storage, computeAnchor(storage, tenantIdentifier, 0));
    }
}
