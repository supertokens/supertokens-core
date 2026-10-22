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

import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.UsersCountCacheStorage;
import io.supertokens.pluginInterface.authRecipe.UsersCountSnapshot;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Exact user counts for the reports billing is based on (the telemetry ping's app count, the license-usage sync's
 * per-tenant counts). Where the storage supports it ({@link UsersCountCacheStorage}) a count is stored in the database
 * and reused by every core while younger than {@link #MAX_AGE_MS}, and a database lock makes cores that need it at the
 * same time wait for one count instead of each running their own. A storage without that support is counted directly.
 * <p>
 * The per-key JVM locks keep this core's threads from each holding a connection while they queue on the database
 * lock.
 */
public final class StoredUsersCount {

    // How old a stored count may be before it is counted again. The reported counts are never older than this.
    public static final long MAX_AGE_MS = 10 * 60 * 1000L;

    private static final ConcurrentHashMap<String, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private StoredUsersCount() {
    }

    /**
     * The app's distinct user count across the given storages (the app's user pools), as
     * {@link AuthRecipe#getUsersCountAcrossAllTenants} with no recipe filter.
     */
    public static long getAppUsersCount(AppIdentifier appIdentifier, Storage[] storages)
            throws StorageQueryException {
        ReentrantLock lock = LOCKS.computeIfAbsent(
                "app~" + appIdentifier.getConnectionUriDomain() + "~" + appIdentifier.getAppId(),
                k -> new ReentrantLock());
        lock.lock();
        try {
            long count = 0;
            for (Storage storage : storages) {
                if (storage instanceof UsersCountCacheStorage) {
                    count += ((UsersCountCacheStorage) storage).getOrComputeAppUsersCount(appIdentifier, MAX_AGE_MS)
                            .count;
                } else {
                    count += StorageUtils.getAuthRecipeStorage(storage).getUsersCount(appIdentifier, null);
                }
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * The tenant's distinct user count, as {@code getUsersCount(tenantIdentifier, null)}.
     */
    public static long getTenantUsersCount(TenantIdentifier tenantIdentifier, Storage storage)
            throws StorageQueryException {
        return getTenantUsersCountSnapshot(tenantIdentifier, storage, MAX_AGE_MS).count;
    }

    /**
     * The tenant's distinct user count with the time it was counted and whether this call counted it. A stored count
     * is reused only if younger than {@code maxAgeMs} (at most {@link #MAX_AGE_MS}); 0 forces a new count.
     */
    public static UsersCountSnapshot getTenantUsersCountSnapshot(TenantIdentifier tenantIdentifier, Storage storage,
                                                                 long maxAgeMs) throws StorageQueryException {
        ReentrantLock lock = LOCKS.computeIfAbsent("tenant~" + tenantIdentifier.getConnectionUriDomain() + "~"
                + tenantIdentifier.getAppId() + "~" + tenantIdentifier.getTenantId(), k -> new ReentrantLock());
        lock.lock();
        try {
            if (storage instanceof UsersCountCacheStorage) {
                return ((UsersCountCacheStorage) storage).getOrComputeTenantUsersCount(tenantIdentifier,
                        Math.min(maxAgeMs, MAX_AGE_MS));
            }
            long countedAtMs = System.currentTimeMillis();
            return new UsersCountSnapshot(StorageUtils.getAuthRecipeStorage(storage).getUsersCount(tenantIdentifier,
                    null), countedAtMs, true);
        } finally {
            lock.unlock();
        }
    }
}
