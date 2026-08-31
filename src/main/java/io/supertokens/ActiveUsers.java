package io.supertokens;

import io.supertokens.auditlog.AuditLog;
import io.supertokens.auditlog.lifecycle.ActivityEventType;
import io.supertokens.cronjobs.rollupUserLastActive.RollupDirtySignal;
import io.supertokens.pluginInterface.ActiveUsersSQLStorage;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.ConcurrentHashMap;
import io.supertokens.auditlog.UnauditedTransaction;

public class ActiveUsers {

    // Skip appending a throttled activity event if we already wrote one for this (app, userId) within
    // this window. The activity log feeds daily/monthly active-user counts (via the fold), so a few
    // minutes of staleness is invisible — but at refresh-token rates an unthrottled insert dominates
    // commit waits on the database. Unthrottled activity classes (sign_in, sign_out) bypass this.
    private static final long THROTTLE_MS = 5 * 60 * 1000L;

    // Hard cap on cache size. Beyond this we sweep expired entries; if still over we clear.
    // Extra upserts for a window are acceptable; unbounded memory growth is not.
    private static final int MAX_CACHE_ENTRIES = 200_000;

    private static final ConcurrentHashMap<String, Long> recentlyActiveCache = new ConcurrentHashMap<>();

    private static String cacheKey(AppIdentifier appIdentifier, String userId) {
        return appIdentifier.getConnectionUriDomain() + "|" + appIdentifier.getAppId() + "|" + userId;
    }

    private static boolean isRecentlyActive(String key, long now) {
        Long last = recentlyActiveCache.get(key);
        return last != null && (now - last) < THROTTLE_MS;
    }

    private static void recordActiveAt(String key, long now) {
        if (recentlyActiveCache.size() >= MAX_CACHE_ENTRIES) {
            long cutoff = now - THROTTLE_MS;
            recentlyActiveCache.entrySet().removeIf(e -> e.getValue() < cutoff);
            if (recentlyActiveCache.size() >= MAX_CACHE_ENTRIES) {
                recentlyActiveCache.clear();
            }
        }
        recentlyActiveCache.put(key, now);
    }

    /**
     * Returns true if updateLastActive has been called for this (app, userId) within the
     * throttle window. Callers can use this to short-circuit work that exists only to feed
     * updateLastActive (e.g. resolving a user-id mapping).
     */
    public static boolean wasRecentlyActive(AppIdentifier appIdentifier, String userId) {
        if (Main.isTesting) {
            return false;
        }
        return isRecentlyActive(cacheKey(appIdentifier, userId), System.currentTimeMillis());
    }

    /**
     * Marks (app, userId) as recently active without performing a DB upsert. Used when the
     * upsert was performed under an alias (e.g. supertokensUserId) and the caller wants future
     * lookups by a different key (e.g. external userId) to short-circuit.
     */
    public static void markRecentlyActive(AppIdentifier appIdentifier, String userId) {
        recordActiveAt(cacheKey(appIdentifier, userId), System.currentTimeMillis());
    }

    /**
     * Records a unit of user activity of the given {@code eventType}, emitting it into the request's tenant.
     * The last-active rollup cron is the sole writer of the {@code user_last_active} projection (PLAN-011
     * cutover); here we only append the activity-log event — the fold's source — and mark the storage dirty
     * so the next rollup pass folds it. Throttled activity classes ({@link ActivityEventType#isThrottled()})
     * skip the append when this (app, user) was seen within the throttle window; unthrottled classes always
     * append. Either way the recency cache is refreshed. The projection updates asynchronously (within a
     * rollup interval).
     */
    public static void updateLastActive(TenantIdentifier tenantIdentifier, Main main, String userId,
                                        ActivityEventType eventType)
            throws TenantOrAppNotFoundException {
        AppIdentifier appIdentifier = tenantIdentifier.toAppIdentifier();
        long now = System.currentTimeMillis();
        String key = cacheKey(appIdentifier, userId);
        if (eventType.isThrottled() && !Main.isTesting && isRecentlyActive(key, now)) {
            return;
        }
        // The activity log and its projection live on the app's public-tenant storage — as before, so the
        // fold (which groups by app_id) and the count read see the same rows. The request's tenant is written
        // into the tenant_id column for provenance only.
        Storage storage = StorageLayer.getStorage(appIdentifier.getAsPublicTenantIdentifier(), main);
        recordActiveAt(key, now);
        emitActivityAuditLog(main, storage, tenantIdentifier, userId, eventType, now);
    }

    /**
     * Overload for callers that only have the app on hand (no request tenant): the event is emitted into the
     * app's public tenant — today's behavior for every activity emit before per-tenant provenance was added.
     */
    public static void updateLastActive(AppIdentifier appIdentifier, Main main, String userId,
                                        ActivityEventType eventType)
            throws TenantOrAppNotFoundException {
        updateLastActive(appIdentifier.getAsPublicTenantIdentifier(), main, userId, eventType);
    }

    /**
     * Appends an activity event to the activity_log so the last-active fold captures the user's activity.
     * Best-effort: {@link AuditLog#emit} swallows its own failures, so a failed audit write never affects the
     * request. {@code tenant_id} carries the request's tenant; {@code event_type} is {@code eventType}'s value.
     */
    private static void emitActivityAuditLog(Main main, Storage storage, TenantIdentifier tenantIdentifier,
                                             String userId, ActivityEventType eventType, long now) {
        AuditLog.emit(main, storage, tenantIdentifier, new AuditLogEvent(
                tenantIdentifier.getAppId(), tenantIdentifier.getTenantId(),
                userId, userId,
                eventType.getValue(), "success", null, null,
                now, null));
        // Signal the last-active rollup cron that this storage now has unfolded activity, so its next tick
        // folds instead of skipping.
        RollupDirtySignal.getInstance(main).markDirty(storage.getUserPoolId());
    }

    @TestOnly
    public static void updateLastActive(Main main, String userId) {
        try {
            ActiveUsers.updateLastActive(ResourceDistributor.getAppForTesting().toAppIdentifier(),
                    main, userId, ActivityEventType.SIGN_IN);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static void clearCacheForTesting() {
        recentlyActiveCache.clear();
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
        recentlyActiveCache.remove(cacheKey(appIdentifier, recipeUserId));
    }

    @TestOnly
    public static int countUsersActiveSince(Main main, long time)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return countUsersActiveSince(main, ResourceDistributor.getAppForTesting().toAppIdentifier(), time);
    }
}
