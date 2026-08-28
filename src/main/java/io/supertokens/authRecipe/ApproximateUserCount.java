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
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.telemetry.TelemetryProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serving skeleton for the opt-in approximate tenant user count (PLAN-009 step 1). Holds a per-tenant
 * in-memory cache entry {@code {anchor, sinceMs, computedAt}} and serves
 * {@code anchor + storage.countTenantUsersJoinedSince(sinceMs)} at request time (single-digit ms), refreshing
 * the anchor in the background with stale-while-revalidate semantics.
 * <p>
 * The cache is per-instance and per-(app, tenant); there is no cross-instance coordination. A core restart
 * drops the cache, so the first request per tenant after a restart pays the synchronous anchor cost again.
 * <p>
 * Accuracy: creations are reflected immediately and exactly (a new user lands in the live delta); deletions
 * and account linking/unlinking are reflected only on the next anchor refresh (bounded by the refresh TTL).
 * This is not a statistical estimate and has no error margin.
 */
public class ApproximateUserCount extends ResourceDistributor.SingletonResource {

    public static final String RESOURCE_KEY = "io.supertokens.authRecipe.ApproximateUserCount";

    // Entries older than this trigger a background refresh; the stale entry keeps being served until the
    // refresh completes (stale-while-revalidate). A constant for now (not configurable), per the design.
    private static final long REFRESH_TTL_MS = 10 * 60 * 1000L; // 10 minutes

    // Clock-skew margin subtracted from wall-clock when picking the anchor boundary X. Must comfortably
    // exceed clock skew plus the longest insert transaction so the delta window covers every in-flight insert.
    private static final long SKEW_MARGIN_MS = 60 * 1000L; // 60 seconds

    // Shared daemon pool for background anchor refreshes across every app/tenant. Kept small: refreshes are
    // infrequent (one per tenant per TTL) and single-flighted, so a couple of workers is plenty.
    private static final ExecutorService REFRESH_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "approximate-user-count-refresh");
        t.setDaemon(true);
        return t;
    });

    // Cache keyed by tenant id within this per-app resource.
    private final ConcurrentHashMap<String, CachedAnchor> entries = new ConcurrentHashMap<>();

    // Tenant ids with an in-flight background refresh - the single-flight guard.
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();

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
     * Serves the approximate user count for {@code tenantIdentifier}. On the very first request for a tenant
     * (no cache entry) the anchor is computed synchronously to prime the cache; afterwards the cached anchor
     * is served plus a live joined-since delta, and a stale entry triggers a background refresh while still
     * being served.
     */
    public ApproximateCountResult serve(Main main, TenantIdentifier tenantIdentifier, Storage storage)
            throws StorageQueryException {
        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        String tenantId = tenantIdentifier.getTenantId();
        long now = System.currentTimeMillis();

        CachedAnchor cachedAnchor = entries.get(tenantId);
        if (cachedAnchor == null) {
            // First request for this tenant: compute the anchor synchronously so subsequent requests are fast.
            long sinceMs = now - SKEW_MARGIN_MS;
            long anchor = authRecipeStorage.computeTenantUserCountAnchor(tenantIdentifier, sinceMs);
            cachedAnchor = new CachedAnchor(anchor, sinceMs, now);
            entries.put(tenantId, cachedAnchor);
        } else if (now - cachedAnchor.computedAt >= REFRESH_TTL_MS) {
            // Stale: refresh in the background (single-flight) and keep serving the current entry.
            triggerRefresh(main, tenantIdentifier, storage);
        }

        long delta = authRecipeStorage.countTenantUsersJoinedSince(tenantIdentifier, cachedAnchor.sinceMs);
        return new ApproximateCountResult(cachedAnchor.anchor + delta, true, cachedAnchor.sinceMs);
    }

    private void triggerRefresh(Main main, TenantIdentifier tenantIdentifier, Storage storage) {
        String tenantId = tenantIdentifier.getTenantId();
        if (!refreshing.add(tenantId)) {
            return; // a refresh for this tenant is already in flight
        }
        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        try {
            REFRESH_EXECUTOR.submit(() -> {
                try {
                    long sinceMs = System.currentTimeMillis() - SKEW_MARGIN_MS;
                    long anchor = authRecipeStorage.computeTenantUserCountAnchor(tenantIdentifier, sinceMs);
                    entries.put(tenantId, new CachedAnchor(anchor, sinceMs, System.currentTimeMillis()));
                    ProcessState.getInstance(main).addState(
                            ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_REFRESH_COMPLETED, null);
                    // The slow exact recompute this refresh performs doubles as a correctness audit of the
                    // lifecycle-event ledger (PLAN-010 decision 4). Kept entirely separate from serving.
                    runShadowAudit(main, tenantIdentifier, storage);
                } catch (Exception e) {
                    // Keep serving the existing (stale) entry; the next request retries the refresh.
                    ProcessState.getInstance(main).addState(
                            ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_REFRESH_FAILED, e);
                } finally {
                    refreshing.remove(tenantId);
                }
            });
        } catch (RuntimeException e) {
            // e.g. the executor rejected the task - don't leave the single-flight guard stuck.
            refreshing.remove(tenantId);
        }
    }

    /**
     * The shadow audit (PLAN-010 unit 3), run once per successful background refresh. Reads the fresh exact
     * tenant count and the lifecycle events committed since the previous refresh, and checks that the count
     * moved by exactly the folded delta (see {@link CountShadowAudit}). Best-effort and observational only:
     * any failure here is swallowed so it can never disturb serving or the refresh that just succeeded, and a
     * discrepancy is logged, never served — the fresh exact count already won.
     */
    private void runShadowAudit(Main main, TenantIdentifier tenantIdentifier, Storage storage) {
        String tenantId = tenantIdentifier.getTenantId();
        try {
            if (!(storage instanceof ActivityLogStorage)) {
                return; // storage keeps no lifecycle-event ledger, so there is nothing to fold against
            }
            ActivityLogStorage activityLogStorage = (ActivityLogStorage) storage;
            AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);

            // Known boundary limitation (observational only): the window below filters events on their
            // emit-time created_at (stamped at the start of the mutating txn), whereas freshExactCount and the
            // event row only become visible at commit. A txn straddling snapshotMs can land its created_at on
            // one side of the bound and its count effect on the other, yielding a one-time DISCREPANCY that is
            // a boundary artifact, not a ledger bug (it self-heals as the misassigned mutation is folded in
            // the adjacent window). Serving compensates for the same lag with SKEW_MARGIN_MS; the audit
            // applies no analogous margin here because a robust fix needs an as-of-time exact count we do not
            // have (and gating on persistence across refreshes would also mask genuine one-time missed emits,
            // which produce the same single self-healing discrepancy). Tracked as a soak follow-up.
            long snapshotMs = System.currentTimeMillis();
            long freshExactCount = authRecipeStorage.getUsersCount(tenantIdentifier, null);

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
                default:
                    ProcessState.getInstance(main).addState(
                            ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_MATCHED, null);
                    break;
            }
        } catch (Exception e) {
            // Observability, not correctness: never let an audit failure escape into the refresh path.
            ProcessState.getInstance(main).addState(
                    ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_FAILED, e);
            Logging.debug(main, tenantIdentifier,
                    "Count shadow audit failed for tenant " + tenantId + ": " + e.getMessage());
        }
    }

    /**
     * Reports a shadow-audit discrepancy: a human-readable {@code warn} log with the full context the issue
     * asks for (tenant, window bounds, event count, both values) plus a structured telemetry event — the
     * "metric" — carrying the same fields for querying/alerting. The discrepant value is never served.
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
            attributes.put("audit", "count_shadow");
            attributes.put("tenantId", result.tenantId);
            attributes.put("expectedCount", Long.toString(result.expectedCount));
            attributes.put("freshExactCount", Long.toString(result.freshExactCount));
            attributes.put("previousExactCount", Long.toString(result.previousExactCount));
            attributes.put("foldedDelta", Long.toString(result.foldedDelta));
            attributes.put("eventCount", Integer.toString(result.eventCount));
            attributes.put("windowFromExclusiveMs", Long.toString(result.windowFromExclusiveMs));
            attributes.put("windowToInclusiveMs", Long.toString(result.windowToInclusiveMs));
            telemetry.createLogEvent(tenantIdentifier, "count_shadow_audit_discrepancy", "warn", attributes);
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

    // One cached anchor snapshot for a tenant: the anchor count, the boundary it was rebased onto, and when
    // it was computed (used to decide staleness).
    private static class CachedAnchor {
        final long anchor;
        final long sinceMs; // X: the epoch-ms boundary the anchor was rebased onto
        final long computedAt;

        CachedAnchor(long anchor, long sinceMs, long computedAt) {
            this.anchor = anchor;
            this.sinceMs = sinceMs;
            this.computedAt = computedAt;
        }
    }

    public static class ApproximateCountResult {
        // The served count: anchor + live joined-since delta.
        public final long count;
        // Whether a cached snapshot was used (always true here; the caller reports false when it serves exact).
        public final boolean approximate;
        // The anchor boundary X (epoch ms) the served value is "as of".
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
                (k, e) -> new CachedAnchor(e.anchor, e.sinceMs, 0));
    }

    // Test-only seam: runs one shadow-audit pass synchronously - seeding the snapshot on the first call for a
    // tenant and comparing against the previous snapshot on later calls - so tests can exercise the audit's
    // real storage reads and fold deterministically, without driving the background refresh executor (which
    // staleEntryTriggersBackgroundRefresh already covers). Not a runtime entry point; mirrors
    // expireEntryForTesting.
    public void runShadowAuditForTesting(Main main, TenantIdentifier tenantIdentifier, Storage storage) {
        runShadowAudit(main, tenantIdentifier, storage);
    }
}
