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

package io.supertokens.bulkimport;

import io.supertokens.Main;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportProxySQLStorage;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportProxyStoragePool;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportSQLStorage;
import io.supertokens.pluginInterface.exceptions.DbInitException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The dedicated bulk import connection pools of one app: one {@link BulkImportProxyStoragePool} per user pool
 * (database) that the app's tenants live on, each sized to {@code maxConnectionsPerPool}.
 *
 * <p>Opened only once there is something to import and closed when the run is over, so an import adds a
 * known, bounded number of server connections and never borrows from the live pools that serve API traffic.
 * Workers obtain their own single-connection storages via {@link #createStoragesForWorker()}.
 */
public final class BulkImportProxyStoragePools implements AutoCloseable {

    private final AppIdentifier app;
    private final Map<String, BulkImportProxyStoragePool> poolsByUserPoolId;
    private final Map<TenantIdentifier, String> userPoolIdByTenant;

    private BulkImportProxyStoragePools(AppIdentifier app, Map<String, BulkImportProxyStoragePool> poolsByUserPoolId,
                                        Map<TenantIdentifier, String> userPoolIdByTenant) {
        this.app = app;
        this.poolsByUserPoolId = poolsByUserPoolId;
        this.userPoolIdByTenant = userPoolIdByTenant;
    }

    /**
     * Opens one pool per distinct user pool among {@code app}'s tenants. The tenant list is resolved once,
     * here; tenants created while an import is running are picked up by the next run.
     */
    public static BulkImportProxyStoragePools openForApp(Main main, AppIdentifier app, int maxConnectionsPerPool)
            throws TenantOrAppNotFoundException, DbInitException, StorageQueryException {
        Map<String, BulkImportProxyStoragePool> pools = new LinkedHashMap<>();
        Map<TenantIdentifier, String> userPoolIdByTenant = new HashMap<>();
        try {
            for (TenantConfig tenant : Multitenancy.getAllTenantsForApp(app, main)) {
                Storage storage = StorageLayer.getStorage(tenant.tenantIdentifier, main);
                if (storage.getType() != STORAGE_TYPE.SQL || !(storage instanceof BulkImportSQLStorage)) {
                    throw new StorageQueryException(new IllegalStateException(
                            "Bulk import requires an SQL storage; tenant " + tenant.tenantIdentifier + " has none"));
                }
                String userPoolId = storage.getUserPoolId();
                if (!pools.containsKey(userPoolId)) {
                    pools.put(userPoolId,
                            ((BulkImportSQLStorage) storage).openBulkImportProxyStoragePool(maxConnectionsPerPool));
                }
                userPoolIdByTenant.put(tenant.tenantIdentifier, userPoolId);
            }
        } catch (TenantOrAppNotFoundException | DbInitException | StorageQueryException | RuntimeException e) {
            closeAll(pools);
            throw e;
        }
        if (!userPoolIdByTenant.containsKey(app.getAsPublicTenantIdentifier())) {
            closeAll(pools);
            throw new TenantOrAppNotFoundException(app.getAsPublicTenantIdentifier());
        }
        return new BulkImportProxyStoragePools(app, pools, userPoolIdByTenant);
    }

    /** Creates one proxy storage per pool for the calling worker; the worker owns and must close them. */
    public BulkImportWorkerStorages createStoragesForWorker() throws StorageQueryException {
        Map<String, BulkImportProxySQLStorage> storages = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, BulkImportProxyStoragePool> pool : poolsByUserPoolId.entrySet()) {
                storages.put(pool.getKey(), pool.getValue().createProxyStorage());
            }
        } catch (StorageQueryException | RuntimeException e) {
            for (BulkImportProxySQLStorage s : storages.values()) {
                try {
                    s.closeConnectionForBulkImportProxyStorage();
                } catch (StorageQueryException ignored) {
                    // best effort; the original failure is what matters
                }
            }
            throw e;
        }
        return new BulkImportWorkerStorages(app, storages, userPoolIdByTenant);
    }

    public int getPoolCount() {
        return poolsByUserPoolId.size();
    }

    @Override
    public void close() throws StorageQueryException {
        closeAll(poolsByUserPoolId);
    }

    private static void closeAll(Map<String, BulkImportProxyStoragePool> pools) throws StorageQueryException {
        StorageQueryException firstFailure = null;
        for (BulkImportProxyStoragePool pool : pools.values()) {
            try {
                pool.close();
            } catch (StorageQueryException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        pools.clear();
        if (firstFailure != null) {
            throw firstFailure;
        }
    }
}
