/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.jwt;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.pluginInterface.session.noSqlStorage.SessionNoSQLStorage_1;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class JWTSigningKey extends ResourceDistributor.SingletonResource {
    public static final String RESOURCE_KEY = "io.supertokens.jwt.JWTSigningKey";
    private final Main main;
    private List<JWTSigningKeyInfo> keys;

    private JWTSigningKey(Main main) {
        this.main = main;

        try {
            this.getAllKeys();
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            Logging.error(main, "Error fetching JWT signing keys", false, e);
        }
    }

    public static void init(Main main) {
        JWTSigningKey instance = (JWTSigningKey) main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance != null) {
            return;
        }

        main.getResourceDistributor().setResource(RESOURCE_KEY, new JWTSigningKey(main));
    }

    public static JWTSigningKey getInstance(Main main) {
        JWTSigningKey instance = (JWTSigningKey) main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance == null) {
            init(main);
        }
        return (JWTSigningKey) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    public synchronized List<JWTSigningKeyInfo> getAllKeys()
            throws StorageQueryException, StorageTransactionLogicException {
        if (this.keys == null) {
            this.keys = this.maybeGenerateNewKeyAndUpdateInDb();
        }

        return this.keys;
    }

    public synchronized JWTSigningKeyInfo getLatestSigningKey()
            throws StorageQueryException, StorageTransactionLogicException {
        SessionStorage storage = StorageLayer.getSessionStorage(main);

        if (storage.getType() == STORAGE_TYPE.SQL) {
            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;

            return sqlStorage.startTransaction(con -> {
               return sqlStorage.getLatestJWTSigningKey(con);
            });
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            SessionNoSQLStorage_1 noSQLStorage = (SessionNoSQLStorage_1) storage;

            return noSQLStorage.getLatestJWTSigningKey();
        }

        throw new QuitProgramException("Unsupported storage type detected");
    }

    public synchronized JWTSigningKeyInfo getKeyForKeyId(String keyId)
            throws StorageQueryException, StorageTransactionLogicException {
        if (this.keys == null) {
            this.keys = this.maybeGenerateNewKeyAndUpdateInDb();
        }

        if (this.keys.isEmpty()) {
            return null;
        }

        for (int i = 0; i < this.keys.size(); i++) {
            JWTSigningKeyInfo currentKey = this.keys.get(i);

            if (currentKey.keyId.equals(keyId)) {
                return currentKey;
            }
        }

        return null;
    }

    private List<JWTSigningKeyInfo> maybeGenerateNewKeyAndUpdateInDb()
            throws StorageQueryException, StorageTransactionLogicException {
        SessionStorage storage = StorageLayer.getSessionStorage(main);

        if (storage.getType() == STORAGE_TYPE.SQL) {
            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;

            return sqlStorage.startTransaction(con -> {
                List<JWTSigningKeyInfo> keys;
                List<JWTSigningKeyInfo> keysFromStorage = sqlStorage.getJWTSigningKey_Transaction(con);

                if (keysFromStorage == null) {
                    keys = new ArrayList<>();
                } else {
                    keys = keysFromStorage;
                }

                if (keys.isEmpty()) {
                    try {
                        long currentTimeInMillis = System.currentTimeMillis();
                        Utils.PubPriKey newKey = Utils.generateNewPubPriKey();
                        JWTSigningKeyInfo keyInfo = new JWTSigningKeyInfo(Utils.getUUID(), newKey.publicKey, newKey.privateKey, currentTimeInMillis);
                        sqlStorage.setJWTSigningKey_Transaction(con, keyInfo);
                        keys.add(keyInfo);

                    } catch (NoSuchAlgorithmException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                }

                sqlStorage.commitTransaction(con);
                return keys;
            });
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            SessionNoSQLStorage_1 noSQLStorage = (SessionNoSQLStorage_1) storage;

            while (true) {
                List<JWTSigningKeyInfo> keys;
                List<JWTSigningKeyInfo> keysFromStorage = noSQLStorage.getJWTSigningKeysInfo_Transaction();

                if (keysFromStorage == null) {
                    keys = new ArrayList<>();
                } else {
                    keys = keysFromStorage;
                }

                if (keys.isEmpty()) {
                    try {
                        long currentTimeInMillis = System.currentTimeMillis();
                        Utils.PubPriKey newKey = Utils.generateNewPubPriKey();
                        JWTSigningKeyInfo keyInfo = new JWTSigningKeyInfo(Utils.getUUID(), newKey.publicKey, newKey.privateKey, currentTimeInMillis);
                        boolean success = noSQLStorage.setJWTSigningKeyInfo_Transaction(keyInfo);

                        if (!success) {
                            continue;
                        } else {
                            keys.add(keyInfo);
                        }
                    } catch (NoSuchAlgorithmException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                }

                return keys;
            }
        }

        throw new QuitProgramException("Unsupported storage type detected");
    }
}
