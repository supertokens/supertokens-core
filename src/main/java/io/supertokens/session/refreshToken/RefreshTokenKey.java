/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.session.refreshToken;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.KeyValueInfoWithLastUpdated;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.pluginInterface.session.noSqlStorage.SessionNoSQLStorage_1;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import org.jetbrains.annotations.TestOnly;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;

public class RefreshTokenKey extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.session.refreshToken.RefreshTokenKey";
    private final Main main;
    private String key;
    private final TenantIdentifier tenantIdentifier;

    private RefreshTokenKey(TenantIdentifier tenantIdentifier, Main main) throws
            TenantOrAppNotFoundException {
        this.main = main;
        this.tenantIdentifier = tenantIdentifier;
        try {
            this.getKey();
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            Logging.error(main, "Error while fetching refresh token key", false, e);
        }
    }

    public static void initForBaseTenant(Main main) {
        try {
            main.getResourceDistributor().setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                    new RefreshTokenKey(new TenantIdentifier(null, null, null), main));
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static RefreshTokenKey getInstance(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        return (RefreshTokenKey) main.getResourceDistributor()
                .getResource(tenantIdentifier, RESOURCE_KEY);
    }

    @TestOnly
    public static RefreshTokenKey getInstance(Main main) {
        try {
            return getInstance(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void loadForAllTenants(Main main, TenantConfig[] tenants) {
        try {
            main.getResourceDistributor().withResourceDistributorLock(() -> {
                Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> existingResources =
                        main.getResourceDistributor()
                                .getAllResourcesWithResourceKey(RESOURCE_KEY);
                main.getResourceDistributor().clearAllResourcesWithResourceKey(RESOURCE_KEY);
                for (TenantConfig tenant : tenants) {
                    ResourceDistributor.SingletonResource resource = existingResources.get(
                            new ResourceDistributor.KeyClass(tenant.tenantIdentifier, RESOURCE_KEY));
                    if (resource != null) {
                        main.getResourceDistributor().setResource(tenant.tenantIdentifier, RESOURCE_KEY,
                                resource);
                    } else {
                        try {
                            main.getResourceDistributor()
                                    .setResource(tenant.tenantIdentifier, RESOURCE_KEY,
                                            new RefreshTokenKey(tenant.tenantIdentifier, main));
                        } catch (TenantOrAppNotFoundException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
                // re add the base config
                main.getResourceDistributor().setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        existingResources.get(
                                new ResourceDistributor.KeyClass(new TenantIdentifier(null, null, null),
                                        RESOURCE_KEY)));
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new RuntimeException(e);
        }
    }

    public String getKey() throws StorageQueryException, StorageTransactionLogicException,
            TenantOrAppNotFoundException {
        if (this.key == null) {
            this.key = maybeGenerateNewKeyAndUpdateInDb();
        }

        return this.key;
    }

    private String maybeGenerateNewKeyAndUpdateInDb()
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        SessionStorage storage = StorageLayer.getSessionStorage(this.tenantIdentifier, main);

        if (storage.getType() == STORAGE_TYPE.SQL) {

            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;

            // start transaction
            return sqlStorage.startTransaction(con -> {
                String key = null;
                KeyValueInfo keyFromStorage = sqlStorage.getRefreshTokenSigningKey_Transaction(tenantIdentifier, con);
                if (keyFromStorage != null) {
                    key = keyFromStorage.value;
                }

                if (key == null) {
                    try {
                        key = Utils.generateNewSigningKey();
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    sqlStorage.setRefreshTokenSigningKey_Transaction(tenantIdentifier, con,
                            new KeyValueInfo(key, System.currentTimeMillis()));
                }

                sqlStorage.commitTransaction(con);
                return key;

            });
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            SessionNoSQLStorage_1 noSQLStorage = (SessionNoSQLStorage_1) storage;

            while (true) {

                String key = null;
                KeyValueInfoWithLastUpdated keyFromStorage = noSQLStorage.getRefreshTokenSigningKey_Transaction();
                if (keyFromStorage != null) {
                    key = keyFromStorage.value;
                }

                if (key == null) {
                    try {
                        key = Utils.generateNewSigningKey();
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    boolean success = noSQLStorage.setRefreshTokenSigningKey_Transaction(
                            new KeyValueInfoWithLastUpdated(key, System.currentTimeMillis(),
                                    keyFromStorage == null ? null : keyFromStorage.lastUpdatedSign));

                    if (!success) {
                        // something else already updated this particular field. So we must try again.
                        continue;
                    }
                }

                return key;
            }
        }

        throw new QuitProgramException("Unsupported storage type detected");
    }
}
