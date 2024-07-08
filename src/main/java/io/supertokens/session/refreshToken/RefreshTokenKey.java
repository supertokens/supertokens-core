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
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
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
import java.util.List;
import java.util.Map;

public class RefreshTokenKey extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.session.refreshToken.RefreshTokenKey";
    private final Main main;
    private String key;
    private final AppIdentifier appIdentifier;

    private RefreshTokenKey(AppIdentifier appIdentifier, Main main) throws
            TenantOrAppNotFoundException {
        this.main = main;
        this.appIdentifier = appIdentifier;
        try {
            this.getKey();
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            Logging.error(main, appIdentifier.getAsPublicTenantIdentifier(), "Error while fetching refresh token key",
                    false, e);
        }
    }

    public static RefreshTokenKey getInstance(AppIdentifier appIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        return (RefreshTokenKey) main.getResourceDistributor()
                .getResource(appIdentifier, RESOURCE_KEY);
    }

    @TestOnly
    public static RefreshTokenKey getInstance(Main main) {
        try {
            return getInstance(new AppIdentifier(null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void loadForAllTenants(Main main, List<AppIdentifier> apps,
                                         List<TenantIdentifier> tenantsThatChanged) {
        try {
            main.getResourceDistributor().withResourceDistributorLock(() -> {
                Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> existingResources =
                        main.getResourceDistributor()
                                .getAllResourcesWithResourceKey(RESOURCE_KEY);
                main.getResourceDistributor().clearAllResourcesWithResourceKey(RESOURCE_KEY);
                for (AppIdentifier app : apps) {
                    ResourceDistributor.SingletonResource resource = existingResources.get(
                            new ResourceDistributor.KeyClass(app, RESOURCE_KEY));
                    if (resource != null && !tenantsThatChanged.contains(app.getAsPublicTenantIdentifier())) {
                        main.getResourceDistributor().setResource(app, RESOURCE_KEY,
                                resource);
                    } else {
                        try {
                            main.getResourceDistributor()
                                    .setResource(app, RESOURCE_KEY,
                                            new RefreshTokenKey(app, main));
                        } catch (TenantOrAppNotFoundException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
                return null;
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
        SessionStorage storage = (SessionStorage) StorageLayer.getStorage(
                this.appIdentifier.getAsPublicTenantIdentifier(), main);

        if (storage.getType() == STORAGE_TYPE.SQL) {

            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;

            try {
                // start transaction
                return sqlStorage.startTransaction(con -> {
                    String key = null;
                    KeyValueInfo keyFromStorage = sqlStorage.getRefreshTokenSigningKey_Transaction(appIdentifier, con);
                    if (keyFromStorage != null) {
                        key = keyFromStorage.value;
                    }

                    if (key == null) {
                        try {
                            key = Utils.generateNewSigningKey();
                        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                            throw new StorageTransactionLogicException(e);
                        }
                        try {
                            sqlStorage.setRefreshTokenSigningKey_Transaction(appIdentifier, con,
                                    new KeyValueInfo(key, System.currentTimeMillis()));
                        } catch (TenantOrAppNotFoundException e) {
                            throw new StorageTransactionLogicException(e);
                        }
                    }

                    sqlStorage.commitTransaction(con);
                    return key;

                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof TenantOrAppNotFoundException) {
                    throw (TenantOrAppNotFoundException) e.actualException;
                }
                throw e;
            }

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