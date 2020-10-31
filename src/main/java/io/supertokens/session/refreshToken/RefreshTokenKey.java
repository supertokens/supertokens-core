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
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.pluginInterface.session.noSqlStorage.SessionNoSQLStorage_1;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class RefreshTokenKey extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.session.refreshToken.RefreshTokenKey";
    private final Main main;
    private String key;

    private RefreshTokenKey(Main main) {
        this.main = main;
        try {
            this.getKey();
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            Logging.error(main, "Error while fetching refresh token key", false, e);
        }
    }

    public static void init(Main main) {
        RefreshTokenKey instance = (RefreshTokenKey) main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance != null) {
            return;
        }
        main.getResourceDistributor().setResource(RESOURCE_KEY, new RefreshTokenKey(main));
    }

    public static RefreshTokenKey getInstance(Main main) {
        RefreshTokenKey instance = (RefreshTokenKey) main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance == null) {
            init(main);
        }
        return (RefreshTokenKey) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    public String getKey() throws StorageQueryException, StorageTransactionLogicException {
        if (this.key == null) {
            this.key = maybeGenerateNewKeyAndUpdateInDb();
        }

        return this.key;
    }

    private String maybeGenerateNewKeyAndUpdateInDb() throws StorageQueryException, StorageTransactionLogicException {
        SessionStorage storage = StorageLayer.getSessionStorageLayer(main);

        if (storage.getType() == STORAGE_TYPE.SQL) {

            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;

            // start transaction
            return sqlStorage.startTransaction(con -> {
                String key = null;
                KeyValueInfo keyFromStorage = sqlStorage.getRefreshTokenSigningKey_Transaction(con);
                if (keyFromStorage != null) {
                    key = keyFromStorage.value;
                }

                if (key == null) {
                    try {
                        key = Utils.generateNewSigningKey();
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    sqlStorage.setRefreshTokenSigningKey_Transaction(con,
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
