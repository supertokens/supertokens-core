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

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

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
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    // Tenant ids with an in-flight background refresh - the single-flight guard.
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();

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
    public Result serve(Main main, TenantIdentifier tenantIdentifier, Storage storage)
            throws StorageQueryException {
        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        String tenantId = tenantIdentifier.getTenantId();
        long now = System.currentTimeMillis();

        Entry entry = entries.get(tenantId);
        if (entry == null) {
            // First request for this tenant: compute the anchor synchronously so subsequent requests are fast.
            long sinceMs = now - SKEW_MARGIN_MS;
            long anchor = authRecipeStorage.computeTenantUserCountAnchor(tenantIdentifier, sinceMs);
            entry = new Entry(anchor, sinceMs, now);
            entries.put(tenantId, entry);
        } else if (now - entry.computedAt >= REFRESH_TTL_MS) {
            // Stale: refresh in the background (single-flight) and keep serving the current entry.
            triggerRefresh(main, tenantIdentifier, storage);
        }

        long delta = authRecipeStorage.countTenantUsersJoinedSince(tenantIdentifier, entry.sinceMs);
        return new Result(entry.anchor + delta, true, entry.sinceMs);
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
                    entries.put(tenantId, new Entry(anchor, sinceMs, System.currentTimeMillis()));
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

    private static class Entry {
        final long anchor;
        final long sinceMs; // X: the epoch-ms boundary the anchor was rebased onto
        final long computedAt;

        Entry(long anchor, long sinceMs, long computedAt) {
            this.anchor = anchor;
            this.sinceMs = sinceMs;
            this.computedAt = computedAt;
        }
    }

    public static class Result {
        // The served count: anchor + live joined-since delta.
        public final long count;
        // Whether a cached snapshot was used (always true here; the caller reports false when it serves exact).
        public final boolean approximate;
        // The anchor boundary X (epoch ms) the served value is "as of".
        public final long asOf;

        public Result(long count, boolean approximate, long asOf) {
            this.count = count;
            this.approximate = approximate;
            this.asOf = asOf;
        }
    }
}
