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

package io.supertokens.session.accessToken;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.KeyValueInfoWithLastUpdated;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.session.noSqlStorage.SessionNoSQLStorage_1;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

import java.security.NoSuchAlgorithmException;

public class AccessTokenSigningKey extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.session.accessToken.AccessTokenSigningKey";
    private final Main main;
    private KeyInfo keyInfo;

    private AccessTokenSigningKey(Main main) {
        this.main = main;
        try {
            this.getKey();
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            Logging.error(main, "Error while fetching access token signing key", false, e);
        }
    }

    public static void init(Main main) {
        AccessTokenSigningKey instance = (AccessTokenSigningKey) main.getResourceDistributor()
                .getResource(RESOURCE_KEY);
        if (instance != null) {
            return;
        }
        main.getResourceDistributor().setResource(RESOURCE_KEY, new AccessTokenSigningKey(main));
    }

    public static AccessTokenSigningKey getInstance(Main main) {
        AccessTokenSigningKey instance = (AccessTokenSigningKey) main.getResourceDistributor()
                .getResource(RESOURCE_KEY);
        if (instance == null) {
            init(main);
        }
        return (AccessTokenSigningKey) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    synchronized void removeKeyFromMemoryIfItHasNotChanged(KeyInfo oldKeyInfo) {
        // we cannot use read write locks for keyInfo because in getKey, we would
        // have to upgrade from the readLock to a
        // writeLock - which is not possible:
        // https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/locks/ReentrantReadWriteLock.html
        if (this.keyInfo == oldKeyInfo) {
            // key has not changed since we previously tried to use it.. So we can make it null.
            // otherwise we might end up making this null unnecessarily.

            ProcessState.getInstance(this.main)
                    .addState(ProcessState.PROCESS_STATE.SETTING_ACCESS_TOKEN_SIGNING_KEY_TO_NULL, null);
            this.keyInfo = null;
        }
    }

    public synchronized AccessTokenSigningKey.KeyInfo getKey()
            throws StorageQueryException, StorageTransactionLogicException {
        if (this.keyInfo == null) {
            this.keyInfo = maybeGenerateNewKeyAndUpdateInDb();
        }

        CoreConfig config = Config.getConfig(main);
        if (config.getAccessTokenSigningKeyDynamic() && System.currentTimeMillis() > (this.keyInfo.createdAtTime
                + config.getAccessTokenSigningKeyUpdateInterval())) {
            // key has expired, we need to change it.
            this.keyInfo = maybeGenerateNewKeyAndUpdateInDb();
        }
        return this.keyInfo;
    }

    public synchronized long getKeyExpiryTime() throws StorageQueryException, StorageTransactionLogicException {
        if (Config.getConfig(this.main).getAccessTokenSigningKeyDynamic()) {
            this.getKey();
            long createdAtTime = this.keyInfo.createdAtTime;
            return createdAtTime + Config.getConfig(main).getAccessTokenSigningKeyUpdateInterval();
        } else {
            // return 10 years from now
            return System.currentTimeMillis() + (10L * 365 * 24 * 3600 * 1000);
        }
    }

    private KeyInfo maybeGenerateNewKeyAndUpdateInDb() throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = StorageLayer.getSessionStorage(main);
        CoreConfig config = Config.getConfig(main);

        if (storage.getType() == STORAGE_TYPE.SQL) {

            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;

            // start transaction
            return sqlStorage.startTransaction(con -> {
                KeyInfo key = null;
                KeyValueInfo keyFromStorage = sqlStorage.getAccessTokenSigningKey_Transaction(con);
                if (keyFromStorage != null) {
                    key = new KeyInfo(keyFromStorage.value, keyFromStorage.createdAtTime);
                }

                boolean generateNewKey = false;

                if (key != null) {
                    if (config.getAccessTokenSigningKeyDynamic() && System.currentTimeMillis() > key.createdAtTime
                            + config.getAccessTokenSigningKeyUpdateInterval()) {
                        generateNewKey = true;
                    }
                }

                if (key == null || generateNewKey) {
                    String signingKey;
                    try {
                        Utils.PubPriKey rsaKeys = Utils.generateNewPubPriKey();
                        signingKey = rsaKeys.toString();
                    } catch (NoSuchAlgorithmException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    key = new KeyInfo(signingKey, System.currentTimeMillis());
                    sqlStorage.setAccessTokenSigningKey_Transaction(con,
                            new KeyValueInfo(key.value, key.createdAtTime));
                }

                sqlStorage.commitTransaction(con);
                return key;

            });
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            SessionNoSQLStorage_1 noSQLStorage = (SessionNoSQLStorage_1) storage;

            while (true) {

                KeyInfo key = null;
                KeyValueInfoWithLastUpdated keyFromStorage = noSQLStorage.getAccessTokenSigningKey_Transaction();
                if (keyFromStorage != null) {
                    key = new KeyInfo(keyFromStorage.value, keyFromStorage.createdAtTime);
                }

                boolean generateNewKey = false;

                if (key != null) {
                    if (config.getAccessTokenSigningKeyDynamic() && System.currentTimeMillis() > key.createdAtTime
                            + config.getAccessTokenSigningKeyUpdateInterval()) {
                        generateNewKey = true;
                    }
                }

                if (key == null || generateNewKey) {
                    String signingKey;
                    try {
                        Utils.PubPriKey rsaKeys = Utils.generateNewPubPriKey();
                        signingKey = rsaKeys.toString();
                    } catch (NoSuchAlgorithmException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    key = new KeyInfo(signingKey, System.currentTimeMillis());
                    boolean success = noSQLStorage.setAccessTokenSigningKey_Transaction(
                            new KeyValueInfoWithLastUpdated(key.value, key.createdAtTime,
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

    public static class KeyInfo {
        public String value;
        public long createdAtTime;

        KeyInfo(String value, long createdAtTime) {
            this.value = value;
            this.createdAtTime = createdAtTime;
        }
    }

}
