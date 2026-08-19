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

import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportProxySQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

import java.util.Map;

/**
 * The single-connection proxy storages one bulk import worker works with: exactly one per user pool
 * (database) of the app, all borrowed from the app's {@link BulkImportProxyStoragePools}. Closing returns
 * every connection to its pool, rolling back whatever was left uncommitted.
 */
public final class BulkImportWorkerStorages implements AutoCloseable {

    private final AppIdentifier app;
    private final Map<String, BulkImportProxySQLStorage> storagesByUserPoolId;
    private final Map<TenantIdentifier, String> userPoolIdByTenant;

    BulkImportWorkerStorages(AppIdentifier app, Map<String, BulkImportProxySQLStorage> storagesByUserPoolId,
                             Map<TenantIdentifier, String> userPoolIdByTenant) {
        this.app = app;
        this.storagesByUserPoolId = storagesByUserPoolId;
        this.userPoolIdByTenant = userPoolIdByTenant;
    }

    /** The storage of the app's public tenant — where the {@code bulk_import_users} queue lives. */
    public BulkImportProxySQLStorage forPublicTenant() {
        try {
            return forTenant(app.getAsPublicTenantIdentifier());
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e); // guaranteed present by BulkImportProxyStoragePools.openForApp
        }
    }

    public BulkImportProxySQLStorage forTenant(TenantIdentifier tenantIdentifier) throws TenantOrAppNotFoundException {
        String userPoolId = userPoolIdByTenant.get(tenantIdentifier);
        if (userPoolId == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifier);
        }
        return storagesByUserPoolId.get(userPoolId);
    }

    /** All storages of the app, as the recipe code expects them (e.g. for cross-storage user id mapping checks). */
    public Storage[] all() {
        return storagesByUserPoolId.values().toArray(new Storage[0]);
    }

    @Override
    public void close() throws StorageQueryException {
        StorageQueryException firstFailure = null;
        for (BulkImportProxySQLStorage storage : storagesByUserPoolId.values()) {
            try {
                storage.closeConnectionForBulkImportProxyStorage();
            } catch (StorageQueryException e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }
}
