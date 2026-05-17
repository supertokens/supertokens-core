package io.supertokens;

import io.supertokens.pluginInterface.ActiveUsersSQLStorage;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.ConcurrentHashMap;

public class ActiveUsers {

    // Skip the user_last_active upsert if we already wrote one for this (app, userId) within
    // this window. The table feeds daily/monthly active-user counts, so a few minutes of
    // staleness is invisible — but at refresh-token rates the unthrottled upsert dominates
    // commit waits on the database.
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

    public static void updateLastActive(AppIdentifier appIdentifier, Main main, String userId)
            throws TenantOrAppNotFoundException {
        long now = System.currentTimeMillis();
        String key = cacheKey(appIdentifier, userId);
        if (isRecentlyActive(key, now)) {
            return;
        }
        Storage storage = StorageLayer.getStorage(appIdentifier.getAsPublicTenantIdentifier(), main);
        try {
            StorageUtils.getActiveUsersStorage(storage).updateLastActive(appIdentifier, userId);
            recordActiveAt(key, now);
        } catch (StorageQueryException ignored) {
        }
    }

    @TestOnly
    public static void updateLastActive(Main main, String userId) {
        try {
            ActiveUsers.updateLastActive(ResourceDistributor.getAppForTesting().toAppIdentifier(),
                    main, userId);
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

    public static void updateLastActiveAfterLinking(Main main, AppIdentifier appIdentifier, String primaryUserId,
                                                    String recipeUserId)
            throws StorageQueryException, TenantOrAppNotFoundException, StorageTransactionLogicException {
        ActiveUsersSQLStorage activeUsersStorage =
                (ActiveUsersSQLStorage) StorageUtils.getActiveUsersStorage(
                        StorageLayer.getStorage(appIdentifier.getAsPublicTenantIdentifier(), main));

        activeUsersStorage.startTransaction(con -> {
            activeUsersStorage.deleteUserActive_Transaction(con, appIdentifier, recipeUserId);
            return null;
        });
        recentlyActiveCache.remove(cacheKey(appIdentifier, recipeUserId));

        // Bypass throttle: linking merges two users into primaryUserId, so its timestamp must
        // be refreshed to "now" regardless of cache state — it now represents the merged
        // activity and an undercounted timestamp would lose the recipeUser's recency.
        long now = System.currentTimeMillis();
        try {
            activeUsersStorage.updateLastActive(appIdentifier, primaryUserId);
            recordActiveAt(cacheKey(appIdentifier, primaryUserId), now);
        } catch (StorageQueryException ignored) {
        }
    }

    @TestOnly
    public static int countUsersActiveSince(Main main, long time)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return countUsersActiveSince(main, ResourceDistributor.getAppForTesting().toAppIdentifier(), time);
    }
}
