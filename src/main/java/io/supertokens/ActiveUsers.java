package io.supertokens;

import io.supertokens.auditlog.AuditLog;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.rollupUserLastActive.RollupDirtySignal;
import io.supertokens.pluginInterface.ActiveUsersSQLStorage;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.auditlog.ActivityEventType;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;
import io.supertokens.auditlog.UnauditedTransaction;

public class ActiveUsers {

    // Skip appending a throttled activity event if we already wrote one for this (app, userId) within
    // this window. The activity log feeds daily/monthly active-user counts (via the fold), so a few
    // minutes of staleness is invisible — but at refresh-token rates an unthrottled insert dominates
    // commit waits on the database. Unthrottled activity classes (sign_in, sign_out) bypass this.
    private static final long THROTTLE_MS = 5 * 60 * 1000L;

    // Throttle policy for the shared plugin-interface {@link ActivityEventType} vocabulary. The vocabulary
    // deliberately carries no throttle flag — its javadoc keeps throttling core-side — so which classes are
    // throttled is decided here: sign_in / sign_out are low-volume, user-initiated and audit-meaningful, so
    // they always emit; every other activity class is high-volume and shares the throttle.
    private static final EnumSet<ActivityEventType> UNTHROTTLED_EVENTS =
            EnumSet.of(ActivityEventType.SIGN_IN, ActivityEventType.SIGN_OUT);

    /**
     * @return whether emits of {@code eventType} are subject to the shared 5-minute per-{@code (app, user)}
     * throttle. {@code sign_in} / {@code sign_out} return {@code false} (always emitted); the rest return
     * {@code true}. Core-side policy over the plugin-interface {@link ActivityEventType} vocabulary.
     */
    public static boolean isThrottled(ActivityEventType eventType) {
        return !UNTHROTTLED_EVENTS.contains(eventType);
    }

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
     * so the next rollup pass folds it. When the {@code activity_log_throttle_enabled} config is on (the
     * default), throttled activity classes ({@link #isThrottled(ActivityEventType)}) skip the append when
     * this (app, user) was seen within the throttle window and unthrottled classes always append — either way
     * the recency cache is refreshed. When the config is off, the throttle and its cache are bypassed and
     * every activity is recorded as its own row (a complete audit trail). The projection updates
     * asynchronously (within a rollup interval).
     *
     * <p>The append is best-effort: {@link AuditLog#emit} swallows its own write failures, so a failed
     * activity write never fails the caller's request. That matters because these events are emitted after an
     * already-committed (and, for OAuth, externally non-reversible) auth operation — a transient activity-log
     * error must not turn a succeeded sign-in / refresh / session-create / sign-out / oauth call into a 500.
     * Dropped rows self-heal for active-user counting: the next event for the user re-credits them, and the
     * reliable recency anchors are the transactional {@code user_creation} / {@code account_linking} lifecycle
     * events.
     */
    public static void updateLastActive(TenantIdentifier tenantIdentifier, Main main, String userId,
                                        ActivityEventType eventType)
            throws TenantOrAppNotFoundException {
        // The activity log and its projection live on the storage backing the request's tenant — the same
        // storage the user's auth record and their transactional lifecycle events (user_creation /
        // account_linking) live on. Per-storage routing keeps a user's activity colocated with their auth
        // record, so the per-storage fold credits them on their own storage and the summed count read
        // (countUsersActiveSince) sees them exactly once. The request's tenant is also written into the
        // tenant_id column for provenance. (Pre-rework this redirected to the app's public-tenant storage,
        // which for a tenant with its own database projected the user onto a storage the count read never
        // summed — and, once the fold's app_id_to_user_id guard is in place, dropped entirely.)
        updateLastActive(tenantIdentifier, StorageLayer.getStorage(tenantIdentifier, main), main, userId, eventType);
    }

    /**
     * Explicit-storage core for callers that have already resolved the storage backing the user's auth
     * record. Emits the activity onto {@code storage} and marks that storage's pool dirty, so both the
     * per-storage fold and the summed count read see the user on their own storage. {@code tenantIdentifier}
     * supplies the provenance written into the event's {@code app_id}/{@code tenant_id} columns (and the app
     * whose config gates throttling); it need not resolve to {@code storage}.
     */
    public static void updateLastActive(TenantIdentifier tenantIdentifier, Storage storage, Main main, String userId,
                                        ActivityEventType eventType)
            throws TenantOrAppNotFoundException {
        AppIdentifier appIdentifier = tenantIdentifier.toAppIdentifier();
        long now = System.currentTimeMillis();
        String key = cacheKey(appIdentifier, userId);
        boolean throttleEnabled = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main)
                .getActivityLogThrottleEnabled();
        if (throttleEnabled && !Main.isTesting) {
            if (isThrottled(eventType) && isRecentlyActive(key, now)) {
                return;
            }
            // Refresh the recency cache so a subsequent throttled event (and wasRecentlyActive) sees this
            // activity. Only meaningful while throttling is on; when off we never touch the cache, so
            // wasRecentlyActive stays false and every activity is recorded.
            recordActiveAt(key, now);
        }
        emitActivityAuditLog(main, storage, tenantIdentifier, userId, eventType, now);
    }

    /**
     * Overload for the one app-wide activity path that has no request tenant on hand — {@code
     * SessionRemoveAPI}'s app-wide sign-out — but has already resolved the storage backing the user's auth
     * record. Emits onto that resolved {@code storage} (not the app's public-tenant storage), colocating the
     * SIGN_OUT with the user's other activity so the per-storage fold and the summed count read stay
     * consistent for a separate-database tenant. Provenance is recorded against the app's public tenant, as
     * an app-wide sign-out is not scoped to a single tenant. The fold's {@code app_id_to_user_id} residency
     * guard remains as pure insurance against any future misroute rather than the primary defence.
     */
    public static void updateLastActive(AppIdentifier appIdentifier, Storage storage, Main main, String userId,
                                        ActivityEventType eventType)
            throws TenantOrAppNotFoundException {
        updateLastActive(appIdentifier.getAsPublicTenantIdentifier(), storage, main, userId, eventType);
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

    /**
     * Wakes the last-active rollup for a user whose fold credit comes from a transactional lifecycle event —
     * {@code user_creation} on sign-up, {@code account_linking} on link (the two lifecycle members of the
     * fold set, see {@code RollupEventTypes#FOLD_SET}) — rather than from {@link #updateLastActive}. Those events
     * are written on the mutation's own connection via {@code startAuditedTransaction}, which — unlike
     * {@code updateLastActive} / {@link #emitActivityAuditLog} — does not touch the rollup dirty signal.
     *
     * <p>Without this nudge a user who only signs up (or is only linked) and produces no other activity would
     * not be folded into {@code user_last_active} until the periodic backstop pass — up to a backstop
     * interval — a promptness regression versus the pre-semantic-event behaviour where sign-up went through
     * {@code updateLastActive → markDirty} and folded on the next rollup tick.
     *
     * <p>Call after the lifecycle event's transaction has committed. Marking dirty only signals <em>that</em>
     * there is something to fold, never the fold window, so it is idempotent and safe to over-signal; a lost
     * signal is corrected by the cron's periodic backstop.
     */
    public static void markLastActiveRollupDirty(Main main, TenantIdentifier tenantIdentifier)
            throws TenantOrAppNotFoundException {
        // The projection and its dirty flag are keyed by the storage backing the tenant the fold-relevant
        // lifecycle event was written on — the same storage updateLastActive marks dirty — so the one rollup
        // pass that folds that storage is woken. (Pre-rework this redirected to the app's public-tenant pool,
        // which for a tenant with its own database woke a storage the event was never written to and left the
        // event's own storage unflagged.)
        Storage storage = StorageLayer.getStorage(tenantIdentifier, main);
        RollupDirtySignal.getInstance(main).markDirty(storage.getUserPoolId());
    }

    @TestOnly
    public static void updateLastActive(Main main, String userId) {
        try {
            ActiveUsers.updateLastActive(ResourceDistributor.getAppForTesting(),
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
        // A user's auth record — and, with per-storage routing, their activity and last-active projection —
        // lives on exactly one storage per app (that is why findStorageAndUserIdMappingForUser iterates
        // storages). Sum the per-storage active-user counts across every storage backing the app; the storages
        // are disjoint by user, so there is no double count. (Pre-rework this read only the app's public-tenant
        // storage, which missed every user whose tenant has its own database.)
        Storage[] storages = StorageLayer.getStoragesForApp(main, appIdentifier);
        int count = 0;
        for (Storage storage : storages) {
            count += StorageUtils.getActiveUsersStorage(storage).countUsersActiveSince(appIdentifier, time);
        }
        return count;
    }

    /**
     * Reconciles the last-active projection after two accounts are linked, routing to {@code storage} — the
     * storage backing the linked users' auth records (both linked accounts share one user pool, so the caller's
     * already-resolved user storage is correct for both). Per-storage routing keeps this consistent with the rest
     * of the active-user machinery: for a separate-database tenant the linked users live on the tenant's storage,
     * so the stale-row delete and the dirty nudge must target that storage — resolving the app's public-tenant
     * storage here (pre-rework) made the delete a silent no-op and woke the wrong pool.
     */
    @UnauditedTransaction(justification = "Legacy unaudited transaction (PLAN-012 backlog); pending conversion to startAuditedTransaction or read-only exemption.")
    public static void updateLastActiveAfterLinking(Main main, AppIdentifier appIdentifier, Storage storage,
                                                    String primaryUserId, String recipeUserId)
            throws StorageQueryException, TenantOrAppNotFoundException, StorageTransactionLogicException {
        ActiveUsersSQLStorage activeUsersStorage =
                (ActiveUsersSQLStorage) StorageUtils.getActiveUsersStorage(storage);

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

        // The primary user's refreshed recency is credited by the account_linking lifecycle event
        // AuthRecipe.linkAccounts emitted transactionally — but that event, written via startAuditedTransaction,
        // does not mark the rollup dirty. Wake the rollup here so a link with no other activity folds on the
        // next tick rather than waiting for the periodic backstop.
        RollupDirtySignal.getInstance(main).markDirty(activeUsersStorage.getUserPoolId());
    }

    @TestOnly
    public static int countUsersActiveSince(Main main, long time)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return countUsersActiveSince(main, ResourceDistributor.getAppForTesting().toAppIdentifier(), time);
    }
}
