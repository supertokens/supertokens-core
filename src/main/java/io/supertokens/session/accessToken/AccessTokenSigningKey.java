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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AccessTokenSigningKey extends ResourceDistributor.SingletonResource {
    // We keep the signing keys after generating a new one for accessTokenValidity multiplied by this value
    // JWTs are still checked for expiration after signature verification, this doesn't extend the lifetime of the sessions.
    private static final int SIGNING_KEY_VALIDITY_OVERLAP = 2;
    private static final String RESOURCE_KEY = "io.supertokens.session.accessToken.AccessTokenSigningKey";
    private final Main main;
    private List<KeyInfo> validKeys;

    private AccessTokenSigningKey(Main main) {
        this.main = main;
        try {
            this.getAllKeys();
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

    synchronized void removeKeyFromMemoryIfItHasNotChanged(List<KeyInfo> oldKeyInfo) {
        // we cannot use read write locks for keyInfo because in getKey, we would
        // have to upgrade from the readLock to a
        // writeLock - which is not possible:
        // https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/locks/ReentrantReadWriteLock.html

        // This reference comparison should work, since we recreate the list object each time we refresh and it's unmodifiable
        if (this.validKeys == oldKeyInfo) {
            // key has not changed since we previously tried to use it.. So we can make it null.
            // otherwise we might end up making this null unnecessarily.

            ProcessState.getInstance(this.main)
                    .addState(ProcessState.PROCESS_STATE.SETTING_ACCESS_TOKEN_SIGNING_KEY_TO_NULL, null);
            this.validKeys = null;
        }
    }

    public synchronized List<KeyInfo> getAllKeys()
            throws StorageQueryException, StorageTransactionLogicException {
        if (this.validKeys == null || this.validKeys.size() == 0) {
            this.validKeys = maybeGenerateNewKeyAndUpdateInDb();
        }

        CoreConfig config = Config.getConfig(main);
        if (config.getAccessTokenSigningKeyDynamic() && System.currentTimeMillis() > (this.validKeys.get(0).createdAtTime
                + config.getAccessTokenSigningKeyUpdateInterval())) {
            // key has expired, we need to change it.
            this.validKeys = maybeGenerateNewKeyAndUpdateInDb();
        }
        return this.validKeys;
    }

    public KeyInfo getLatestIssuedKey()
            throws StorageQueryException, StorageTransactionLogicException {
        return this.getAllKeys().get(0);
    }

    public synchronized long getKeyExpiryTime() throws StorageQueryException, StorageTransactionLogicException {
        if (Config.getConfig(this.main).getAccessTokenSigningKeyDynamic()) {
            this.getAllKeys();
            // getKey ensures we have at least 1 valid keys
            long createdAtTime = this.validKeys.get(0).createdAtTime;
            return createdAtTime + Config.getConfig(main).getAccessTokenSigningKeyUpdateInterval();
        } else {
            // return 10 years from now
            return System.currentTimeMillis() + (10L * 365 * 24 * 3600 * 1000);
        }
    }

    private List<KeyInfo> maybeGenerateNewKeyAndUpdateInDb() throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = StorageLayer.getSessionStorage(main);
        CoreConfig config = Config.getConfig(main);
        
        // Keys are kept for this amount of time
        final long signingKeyLifetime = config.getAccessTokenSigningKeyUpdateInterval() + SIGNING_KEY_VALIDITY_OVERLAP * config.getAccessTokenValidity();
        // Keys created after this timestamp can be used to sign access tokens
        final long keysCreatedAfterCanSign = System.currentTimeMillis() - config.getAccessTokenSigningKeyUpdateInterval();
        // Keys created after this timestamp can be used to verify access token signatures
        final long keysCreatedAfterCanVerify = System.currentTimeMillis() - signingKeyLifetime;
        
        List<KeyInfo> validKeys = null;

        if (storage.getType() == STORAGE_TYPE.SQL) {

            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;

            // start transaction
            validKeys = sqlStorage.startTransaction(con -> {
                List<KeyInfo> validKeysFromSQL = new ArrayList<KeyInfo>();

                KeyValueInfo[] keysFromStorage = sqlStorage.getAccessTokenSigningKeys_Transaction(con);
                
                boolean generateNewKey = true;
                boolean clearKeys = false;

                if (keysFromStorage.length == 0) {
                    KeyValueInfo legacyKey = sqlStorage.getLegacyAccessTokenSigningKey_Transaction(con);

                    if (legacyKey != null && config.getAccessTokenSigningKeyDynamic()) {
                        if (keysCreatedAfterCanSign <= legacyKey.createdAtTime) {
                            generateNewKey = false;
                            validKeysFromSQL.add(new KeyInfo(legacyKey.value, legacyKey.createdAtTime, signingKeyLifetime));
                        } else if (legacyKey.createdAtTime < keysCreatedAfterCanVerify) {
                            sqlStorage.removeLegacyAccessTokenSigningKey_Transaction(con);
                        }
                    }
                } else {
                    for (KeyValueInfo key: keysFromStorage) {
                        if (config.getAccessTokenSigningKeyDynamic()) {
                            if (keysCreatedAfterCanSign <= key.createdAtTime) {
                                generateNewKey = false;
                            }
                            if (key.createdAtTime < keysCreatedAfterCanVerify) {
                                clearKeys = true;
                            } else {
                                validKeysFromSQL.add(new KeyInfo(key.value, key.createdAtTime, signingKeyLifetime));
                            }
                        }
                    }
                }
                
                if (generateNewKey) {
                    String signingKey;
                    try {
                        Utils.PubPriKey rsaKeys = Utils.generateNewPubPriKey();
                        signingKey = rsaKeys.toString();
                    } catch (NoSuchAlgorithmException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    KeyInfo newKey = new KeyInfo(signingKey, System.currentTimeMillis(), signingKeyLifetime);
                    sqlStorage.addAccessTokenSigningKey_Transaction(con, new KeyValueInfo(newKey.value, newKey.createdAtTime));
                    validKeysFromSQL.add(newKey);
                }

                if (clearKeys) {
                    sqlStorage.removeAccessTokenSigningKeysBefore_Transaction(con, keysCreatedAfterCanVerify);
                }

                sqlStorage.commitTransaction(con);
                return validKeysFromSQL;
            });
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            SessionNoSQLStorage_1 noSQLStorage = (SessionNoSQLStorage_1) storage;

            while (true) {
                validKeys = new ArrayList<KeyInfo>();
                long lastCreated = -1;
                KeyValueInfo[] keysFromStorage = noSQLStorage.getAccessTokenSigningKeys_Transaction();
                boolean generateNewKey = true;
                boolean clearKeys = false;

                if (keysFromStorage.length == 0) {
                    KeyValueInfoWithLastUpdated legacyKey = noSQLStorage.getLegacyAccessTokenSigningKey_Transaction();

                    if (legacyKey != null && config.getAccessTokenSigningKeyDynamic()) {
                        if (keysCreatedAfterCanSign <= legacyKey.createdAtTime) {
                            generateNewKey = false;
                        }
                        if (keysCreatedAfterCanVerify < legacyKey.createdAtTime) {
                            validKeys.add(new KeyInfo(legacyKey.value, legacyKey.createdAtTime, signingKeyLifetime));
                        } else {
                            noSQLStorage.removeLegacyAccessTokenSigningKey_Transaction();
                        }
                    }
                } else {
                    for (KeyValueInfo key: keysFromStorage) {
                        if (config.getAccessTokenSigningKeyDynamic()) {
                            lastCreated = lastCreated < key.createdAtTime ? key.createdAtTime : lastCreated;
                            
                            if (keysCreatedAfterCanSign <= key.createdAtTime) {
                                generateNewKey = false;
                            } 
                            if (key.createdAtTime < keysCreatedAfterCanVerify) {
                                clearKeys = true;
                            } else {
                                validKeys.add(new KeyInfo(key.value, key.createdAtTime, signingKeyLifetime));
                            }
                        }
                    }
                }

                if (generateNewKey) {
                    String signingKey;
                    try {
                        Utils.PubPriKey rsaKeys = Utils.generateNewPubPriKey();
                        signingKey = rsaKeys.toString();
                    } catch (NoSuchAlgorithmException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    KeyInfo newKey = new KeyInfo(signingKey, System.currentTimeMillis(), signingKeyLifetime);
                    boolean success = noSQLStorage.addAccessTokenSigningKey_Transaction(
                        new KeyValueInfo(newKey.value, newKey.createdAtTime), lastCreated
                    );
                    validKeys.add(newKey);

                    if (!success) {
                        // something else already updated this particular field. So we must try again.
                        continue;
                    }
                }
                
                if (clearKeys) {
                    noSQLStorage.removeAccessTokenSigningKeysBefore_Transaction(keysCreatedAfterCanVerify);
                }
            }
        } else {
            throw new QuitProgramException("Unsupported storage type detected");
        }

        validKeys.sort(Comparator.comparingLong((KeyInfo key) -> key.createdAtTime).reversed());

        return Collections.unmodifiableList(validKeys);
    }

    public static class KeyInfo {
        public String value;
        public long createdAtTime;
        public long expiryTime;

        KeyInfo(String value, long createdAtTime, long validityDuration) {
            this.value = value;
            this.createdAtTime = createdAtTime;
            this.expiryTime = createdAtTime + validityDuration;
        }
    }

}
