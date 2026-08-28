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

package io.supertokens.cronjobs.rollupUserLastActive;

import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.pluginInterface.ActiveUsersSQLStorage;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.auditlog.ActivityLogSQLStorage;
import io.supertokens.pluginInterface.auditlog.AuditedResult;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Derives {@code user_last_active} from the {@code activity_log} by periodically folding recent
 * {@code user_last_active} events into the projection (and reconciling users linked away within the window).
 * Runs once per unique storage via the representative-tenant overload.
 *
 * <p>Per pass:
 * <ul>
 *   <li>A skip-when-idle gate consumes a per-storage in-memory dirty flag ({@link RollupDirtySignal}) set by
 *   the rollup-relevant emit paths. When the flag is clean and the pass is not forced, the cron returns
 *   without borrowing a connection, so an idle pool (with {@code minimum_idle_connections}) stays asleep.
 *   <li>The first pass after startup cannot trust a clean flag (activity may predate this instance — e.g.
 *   rows written by the direct last-active writer), so on a clean first pass it does a cheap
 *   {@link ActivityLogSQLStorage#hasUnfoldedActivitySince} existence check instead.
 *   <li>Every {@link #BACKSTOP_EVERY_N_TICKS}-th tick is an unconditional backstop fold regardless of the
 *   flag, closing the gap where the sole dirty instance dies before folding.
 *   <li>The fold window is {@code max(watermark - MARGIN, now - retention)}; on a missing watermark it falls
 *   back to {@code now - retention}. Retention comes from {@code activity_log_retention_days} via the
 *   representative tenant.
 *   <li>Fold, reconcile and the watermark advance to the run-start time happen in one transaction. The
 *   watermark lives in the key-value store under a reserved key, addressed via the representative tenant.
 *   Representative selection need not be stable across restarts or config reloads: if a different tenant
 *   in the same userPoolId group is picked, the prior watermark is merely orphaned and the next pass
 *   re-folds an idempotent window from the retention fallback — a heavier catch-up, never an incorrect
 *   result.
 *   <li>On failure the dirty flag is re-armed so the next tick retries.
 * </ul>
 *
 * <p>Correctness does not depend on cross-instance coordination: the fold is idempotent
 * ({@code GREATEST} on conflict) and the watermark is last-writer-wins (it only ever widens the next
 * window). The storage layer's advisory lock — where supported — merely collapses redundant concurrent
 * work and is not a correctness requirement.
 */
public class RollupUserLastActive extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.rollupUserLastActive.RollupUserLastActive";

    // Reserved key under which the per-storage fold watermark (a millisecond timestamp string) is stored in
    // the key-value store, addressed via the representative tenant.
    public static final String WATERMARK_KEY = "last_active_rollup_watermark";

    // How far behind the stored watermark each window reaches, to absorb clock skew and rows that became
    // visible just after a previous pass's snapshot (bounded by transaction visibility and the emit
    // throttle). Comfortably larger than the 5-minute emit throttle; overlap is harmless because the fold
    // is idempotent.
    private static final long WINDOW_MARGIN_MS = 15 * 60 * 1000L;

    // Every Nth tick folds unconditionally (ignoring the dirty flag) so a lost dirty signal — e.g. the only
    // instance that saw the activity dies before folding — cannot stall the projection indefinitely. With a
    // 600s interval this is roughly hourly, well inside the retention horizon.
    private static final int BACKSTOP_EVERY_N_TICKS = 6;

    private static class PerStorageState {
        boolean startupDone = false;
        long passCount = 0;
    }

    // Per-storage cron state (first-run tracking + tick counting for the backstop). Only ever touched by the
    // cron's own thread(s): passes never overlap (scheduleWithFixedDelay) and a given storage is handled by
    // one thread per pass.
    private final ConcurrentHashMap<String, PerStorageState> stateByUserPoolId = new ConcurrentHashMap<>();

    private RollupUserLastActive(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("RollupUserLastActive", main, tenantsInfo, false);
    }

    public static RollupUserLastActive init(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        return (RollupUserLastActive) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new RollupUserLastActive(main, tenantsInfo));
    }

    @Override
    protected void doTaskPerStorage(TenantIdentifier representative, Storage storage) throws Exception {
        // Only SQL storages that carry both the activity log and the active-users projection can be folded;
        // e.g. non-SQL stores are skipped.
        if (!(storage instanceof ActiveUsersSQLStorage) || !(storage instanceof ActivityLogSQLStorage)) {
            return;
        }
        ActiveUsersSQLStorage activeUsersStorage = (ActiveUsersSQLStorage) storage;
        ActivityLogSQLStorage activityLogStorage = (ActivityLogSQLStorage) storage;

        String userPoolId = storage.getUserPoolId();
        PerStorageState state = stateByUserPoolId.computeIfAbsent(userPoolId, k -> new PerStorageState());

        boolean firstRun = !state.startupDone;
        boolean backstopDue = !firstRun && (state.passCount % BACKSTOP_EVERY_N_TICKS == 0);
        boolean forced = firstRun || backstopDue;
        state.passCount++;

        RollupDirtySignal dirtySignal = RollupDirtySignal.getInstance(main);
        boolean wasDirty = dirtySignal.consumeDirty(userPoolId);

        if (!forced && !wasDirty) {
            // Nothing new to fold and no forced pass — skip without touching the connection pool.
            return;
        }

        try {
            long runStart = System.currentTimeMillis();
            int retentionDays = Config.getConfig(representative, main).getActivityLogRetentionDays();
            long retentionMs = retentionDays * 24L * 60L * 60L * 1000L;

            Long watermark = getWatermark(activeUsersStorage, representative);
            long windowStart = watermark == null
                    ? runStart - retentionMs
                    : Math.max(watermark - WINDOW_MARGIN_MS, runStart - retentionMs);

            if (firstRun && !wasDirty && !backstopDue) {
                // A fresh dirty flag can't be trusted; only fold if there is actually something to fold.
                // The fold is inclusive of windowStart (created_at >= windowStart) while
                // hasUnfoldedActivitySince is strict (created_at > sinceMillis), so probe at windowStart - 1
                // to keep the existence check aligned with the fold: a row landing exactly on windowStart
                // must not make the check skip a fold the fold itself would perform.
                if (!activityLogStorage.hasUnfoldedActivitySince(windowStart - 1)) {
                    state.startupDone = true;
                    return;
                }
            }

            final long windowStartMillis = windowStart;
            AppIdentifier appIdentifier = representative.toAppIdentifier();
            // The rollup consumes the activity log to maintain a derived projection; it emits no audit
            // events of its own, so it runs through the audited-transaction combinator's zero-event path.
            // The combinator owns the commit.
            activityLogStorage.startAuditedTransaction(appIdentifier, con -> {
                activeUsersStorage.rollupLastActiveFromActivityLog_Transaction(con, windowStartMillis);
                // Advance the watermark to the run-start time (not "now after the fold"): the next window
                // reaches back to windowStart = watermark - MARGIN, so under-advancing is always safe.
                activeUsersStorage.setKeyValue_Transaction(representative, con, WATERMARK_KEY,
                        new KeyValueInfo(String.valueOf(runStart)));
                return AuditedResult.withoutAudit(null,
                        "The last-active rollup maintains a derived projection by folding existing "
                                + "activity-log events into user_last_active; it consumes the audit log "
                                + "rather than producing new events.");
            });

            state.startupDone = true;
        } catch (Exception e) {
            // Re-arm the dirty flag so the next tick retries this storage.
            dirtySignal.markDirty(userPoolId);
            throw e;
        }
    }

    private Long getWatermark(ActiveUsersSQLStorage storage, TenantIdentifier representative)
            throws Exception {
        // A plain auto-committed read — no transaction needed just to fetch the watermark.
        KeyValueInfo info = storage.getKeyValue(representative, WATERMARK_KEY);
        return info == null ? null : Long.parseLong(info.value);
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        // Every 10 minutes.
        return 600;
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        if (!Main.isTesting) {
            return getIntervalTimeSeconds();
        }
        Integer waitTime = CronTaskTest.getInstance(main).getInitialWaitTimeInSeconds(RESOURCE_KEY);
        if (waitTime != null) {
            return waitTime;
        }
        return 0;
    }

    @TestOnly
    public static RollupUserLastActive getNewInstanceForTesting(Main main,
                                                                List<List<TenantIdentifier>> tenantsInfo) {
        return new RollupUserLastActive(main, tenantsInfo);
    }

    @TestOnly
    public void runOncePerStorageForTesting(TenantIdentifier representative, Storage storage) throws Exception {
        doTaskPerStorage(representative, storage);
    }

    /**
     * Synchronously folds every unique storage once, so a test can make activity emitted through the
     * activity log visible in {@code user_last_active} without waiting for the scheduled cron. After the
     * PLAN-011 cutover the rollup is the sole writer of the projection, so tests that assert active-user
     * counts must trigger a fold between the activity and the assertion. Idempotent and monotonic
     * ({@code GREATEST} on conflict), so it is safe to call repeatedly across a test's checkpoints.
     */
    @TestOnly
    public static void runOnceForAllStoragesForTesting(Main main) throws Exception {
        List<List<TenantIdentifier>> tenantsInfo = StorageLayer.getTenantsWithUniqueUserPoolId(main);
        RollupUserLastActive cron = new RollupUserLastActive(main, tenantsInfo);
        for (List<TenantIdentifier> group : tenantsInfo) {
            TenantIdentifier representative = group.get(0);
            cron.doTaskPerStorage(representative, StorageLayer.getStorage(representative, main));
        }
    }
}
