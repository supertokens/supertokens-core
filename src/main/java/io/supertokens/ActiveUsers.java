package io.supertokens;

import io.supertokens.auditlog.lifecycle.ActivityEventType;
import io.supertokens.cronjobs.rollupUserLastActive.RollupDirtySignal;
import io.supertokens.pluginInterface.ActiveUsersSQLStorage;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.auditlog.ActivityLogSQLStorage;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.auditlog.AuditedResult;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

import io.supertokens.auditlog.UnauditedTransaction;

public class ActiveUsers {

    // Status written for a semantic activity row, mirroring the lifecycle events written to the same table.
    private static final String STATUS_SUCCESS = "success";

    /**
     * Records a unit of user activity of the given {@code eventType}, emitting it into the request's tenant.
     * The last-active rollup cron is the sole writer of the {@code user_last_active} projection (PLAN-011
     * cutover); here we only append the activity-log event — the fold's source — and mark the storage dirty
     * so the next rollup pass folds it. The projection updates asynchronously (within a rollup interval).
     *
     * <p>A semantic activity event is a true audit row, so it is written through {@code startAuditedTransaction}
     * — the same mechanism {@link io.supertokens.auditlog.lifecycle.LifecycleAuditEvent lifecycle events} use:
     * a real transaction that is fail-loud (a failed write fails the caller's request) and atomic, not a
     * best-effort {@code AuditLog.emit}. There is no accompanying state mutation at the emit site to co-commit
     * into (the interaction — a sign-in, a refresh, ... — is what the row records), so the audited transaction
     * carries only the event. Every activity is written; there is no throttle (a true audit trail cannot skip
     * rows).
     */
    public static void updateLastActive(TenantIdentifier tenantIdentifier, Main main, String userId,
                                        ActivityEventType eventType)
            throws TenantOrAppNotFoundException, StorageQueryException {
        AppIdentifier appIdentifier = tenantIdentifier.toAppIdentifier();
        long now = System.currentTimeMillis();
        // The activity log and its projection live on the app's public-tenant storage, so the fold (which
        // groups by app_id) and the count read see the same rows. The request's tenant is written into the
        // tenant_id column for provenance only.
        Storage storage = StorageLayer.getStorage(appIdentifier.getAsPublicTenantIdentifier(), main);
        if (!(storage instanceof ActivityLogSQLStorage)) {
            // No SQL activity-log storage to write to (e.g. a non-SQL storage): nothing to record.
            return;
        }
        ActivityLogSQLStorage auditStorage = (ActivityLogSQLStorage) storage;
        try {
            auditStorage.startAuditedTransaction(appIdentifier, con -> {
                AuditLogEvent event = new AuditLogEvent(
                        tenantIdentifier.getAppId(), tenantIdentifier.getTenantId(),
                        userId, userId,
                        eventType.getValue(), STATUS_SUCCESS, null, null,
                        now, null);
                return new AuditedResult<Void>(null, event);
            });
        } catch (StorageTransactionLogicException e) {
            // The audited-transaction logic here only builds an event and never throws a logic exception, so
            // this is unreachable; surface it as a storage error if the combinator's contract ever changes.
            throw new StorageQueryException(e);
        }
        // Signal the last-active rollup cron that this storage now has unfolded activity, so its next tick
        // folds instead of skipping.
        RollupDirtySignal.getInstance(main).markDirty(storage.getUserPoolId());
    }

    /**
     * Overload for callers that only have the app on hand (no request tenant): the event is emitted into the
     * app's public tenant — today's behavior for every activity emit before per-tenant provenance was added.
     */
    public static void updateLastActive(AppIdentifier appIdentifier, Main main, String userId,
                                        ActivityEventType eventType)
            throws TenantOrAppNotFoundException, StorageQueryException {
        updateLastActive(appIdentifier.getAsPublicTenantIdentifier(), main, userId, eventType);
    }

    @TestOnly
    public static void updateLastActive(Main main, String userId) {
        try {
            ActiveUsers.updateLastActive(ResourceDistributor.getAppForTesting().toAppIdentifier(),
                    main, userId, ActivityEventType.SIGN_IN);
        } catch (TenantOrAppNotFoundException | StorageQueryException e) {
            throw new IllegalStateException(e);
        }
    }

    public static int countUsersActiveSince(Main main, AppIdentifier appIdentifier, long time)
            throws StorageQueryException, TenantOrAppNotFoundException {
        Storage storage = StorageLayer.getStorage(appIdentifier.getAsPublicTenantIdentifier(), main);
        return StorageUtils.getActiveUsersStorage(storage).countUsersActiveSince(appIdentifier, time);
    }

    @UnauditedTransaction(justification = "Legacy unaudited transaction (PLAN-012 backlog); pending conversion to startAuditedTransaction or read-only exemption.")
    public static void updateLastActiveAfterLinking(Main main, AppIdentifier appIdentifier, String primaryUserId,
                                                    String recipeUserId)
            throws StorageQueryException, TenantOrAppNotFoundException, StorageTransactionLogicException {
        ActiveUsersSQLStorage activeUsersStorage =
                (ActiveUsersSQLStorage) StorageUtils.getActiveUsersStorage(
                        StorageLayer.getStorage(appIdentifier.getAsPublicTenantIdentifier(), main));

        // Latency optimization only: the rollup's reconcile — driven by the account_linking event that
        // AuthRecipe.linkAccounts emits atomically with the mapping change — is the source of truth for
        // dropping the recipe user's now-stale projection row. Deleting it here just makes the merge visible
        // before the next rollup pass instead of after it. The primary user's refreshed recency comes from
        // the same account_linking event (the fold credits primary_or_recipe_user_id), so no activity ping is
        // emitted here.
        activeUsersStorage.startTransaction(con -> {
            activeUsersStorage.deleteUserActive_Transaction(con, appIdentifier, recipeUserId);
            return null;
        });
    }

    @TestOnly
    public static int countUsersActiveSince(Main main, long time)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return countUsersActiveSince(main, ResourceDistributor.getAppForTesting().toAppIdentifier(), time);
    }
}
