/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.signingkeys;

import io.supertokens.Main;
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
import io.supertokens.pluginInterface.jwt.JWTAsymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.exceptions.DuplicateKeyIdException;
import io.supertokens.pluginInterface.jwt.nosqlstorage.JWTRecipeNoSQLStorage_1;
import io.supertokens.pluginInterface.jwt.sqlstorage.JWTRecipeSQLStorage;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.pluginInterface.session.noSqlStorage.SessionNoSQLStorage_1;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.signingkeys.SigningKeys.KeyInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

import java.security.NoSuchAlgorithmException;
import java.util.*;

public class AccessTokenSigningKey extends ResourceDistributor.SingletonResource {
    private static final String ACCESS_TOKEN_SIGNING_ALGO = "RS256";
    // We keep the signing keys after generating a new one for accessTokenValidity multiplied by this value
    // JWTs are still checked for expiration after signature verification, this doesn't extend the lifetime of the
    // sessions.
    private static final int SIGNING_KEY_VALIDITY_OVERLAP = 2;
    private static final String RESOURCE_KEY = "io.supertokens.signingKeys.AccessTokenSigningKey";
    private final Main main;

    private AccessTokenSigningKey(Main main) {
        this.main = main;
        if (!Main.isTesting) {
            try {
                this.transferLegacyKeyToNewTable();
                this.getOrCreateAndGetSigningKeys();
            } catch (StorageQueryException | StorageTransactionLogicException e) {
                Logging.error(main, "Error while fetching access token signing key", false, e);
            }
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

    public synchronized void transferLegacyKeyToNewTable()
            throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = StorageLayer.getSessionStorage(main);
        Storage jwtRecipeStorage = StorageLayer.getJWTRecipeStorage(main);

        if (storage.getType() == STORAGE_TYPE.SQL) {
            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;
            JWTRecipeSQLStorage sqlJWTRecipeStorage = (JWTRecipeSQLStorage) jwtRecipeStorage;

            // start transaction
            sqlStorage.startTransaction(con -> {
                KeyValueInfo legacyKey = sqlStorage.getLegacyAccessTokenSigningKey_Transaction(con);

                if (legacyKey != null) {
                    try {
                        sqlJWTRecipeStorage.setJWTSigningKey_Transaction(con, new JWTAsymmetricSigningKeyInfo(
                                Utils.getUUID(), legacyKey.createdAtTime, ACCESS_TOKEN_SIGNING_ALGO, legacyKey.value
                        ));
                    } catch (DuplicateKeyIdException e) {
                        // This should be exceedingly rare, since we are generating the UUID above.
                        throw new StorageTransactionLogicException(e);
                    }
                    sqlStorage.removeLegacyAccessTokenSigningKey_Transaction(con);
                    sqlStorage.commitTransaction(con);
                }
                return legacyKey;
            });
        } else {
            SessionNoSQLStorage_1 noSQLStorage = (SessionNoSQLStorage_1) storage;
            JWTRecipeNoSQLStorage_1 noSQLJWTRecipeStorage = (JWTRecipeNoSQLStorage_1) jwtRecipeStorage;
            KeyValueInfoWithLastUpdated legacyKey = noSQLStorage.getLegacyAccessTokenSigningKey_Transaction();

            if (legacyKey != null) {
                // We should only get here once, after an upgrade.
                try {
                    noSQLJWTRecipeStorage.setJWTSigningKeyInfoIfNoKeyForAlgorithmExists_Transaction(new JWTAsymmetricSigningKeyInfo(
                            Utils.getUUID(), legacyKey.createdAtTime, ACCESS_TOKEN_SIGNING_ALGO, legacyKey.value
                    ));
                } catch (DuplicateKeyIdException e) {
                    // This should be exceedingly rare, since we are generating the UUID above.
                    throw new StorageTransactionLogicException(e);
                }
                // We don't need to check the lastUpdatedSign here, since we never update or set
                // legacy keys anymore.
                noSQLStorage.removeLegacyAccessTokenSigningKey_Transaction();
            }
        }
    }

    public synchronized void cleanExpiredAccessTokenSigningKeys() throws StorageQueryException {
        SessionStorage storage = StorageLayer.getSessionStorage(main);
        CoreConfig config = Config.getConfig(main);

        final long signingKeyLifetime = config.getAccessTokenDynamicSigningKeyUpdateInterval()
                + SIGNING_KEY_VALIDITY_OVERLAP * config.getAccessTokenValidity();

        storage.removeAccessTokenSigningKeysBefore(System.currentTimeMillis() - signingKeyLifetime);
    }

    public List<KeyInfo> getOrCreateAndGetSigningKeys()
            throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = StorageLayer.getSessionStorage(main);
        CoreConfig config = Config.getConfig(main);

        // Access token signing keys older than this are deleted (ms)
        final long signingKeyLifetime = config.getAccessTokenDynamicSigningKeyUpdateInterval()
                + SIGNING_KEY_VALIDITY_OVERLAP * config.getAccessTokenValidity();
        // Keys created after this timestamp can be used to sign access tokens (ms)
        final long keysCreatedAfterCanSign = System.currentTimeMillis()
                - config.getAccessTokenDynamicSigningKeyUpdateInterval();
        // Keys created after this timestamp can be used to verify access token signatures (ms)
        final long keysCreatedAfterCanVerify = System.currentTimeMillis() - signingKeyLifetime;

        // Keys we can use for signature verification
        List<KeyInfo> validKeys = null;

        if (storage.getType() == STORAGE_TYPE.SQL) {
            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;

            // start transaction
            validKeys = sqlStorage.startTransaction(con -> {
                List<KeyInfo> validKeysFromSQL = new ArrayList<>();

                // We have to generate a new key if we couldn't find one we can use for signing
                boolean generateNewKey = true;

                KeyValueInfo[] keysFromStorage = sqlStorage.getAccessTokenSigningKeys_Transaction(con);

                for (KeyValueInfo key : keysFromStorage) {
                    if (keysCreatedAfterCanVerify <= key.createdAtTime) {
                        if (keysCreatedAfterCanSign <= key.createdAtTime) {
                            generateNewKey = false;
                        }
                        validKeysFromSQL.add(new KeyInfo(String.valueOf(key.createdAtTime), key.value, key.createdAtTime, signingKeyLifetime, ACCESS_TOKEN_SIGNING_ALGO));
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
                    long creationTime = System.currentTimeMillis();
                    KeyInfo newKey = new KeyInfo(String.valueOf(creationTime), signingKey, creationTime, signingKeyLifetime, ACCESS_TOKEN_SIGNING_ALGO);
                    sqlStorage.addAccessTokenSigningKey_Transaction(con,
                            new KeyValueInfo(newKey.value, newKey.createdAtTime));
                    validKeysFromSQL.add(newKey);
                }

                sqlStorage.commitTransaction(con);
                return validKeysFromSQL;
            });
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            SessionNoSQLStorage_1 noSQLStorage = (SessionNoSQLStorage_1) storage;

            while (true) {
                // We need to clear the array after every retry.
                validKeys = new ArrayList<KeyInfo>();
                // lastCreated is used to emulate transactions in the NoSQL calls
                Long lastCreated = null;
                // We have to generate a new key if we couldn't find one we can use for signing
                boolean generateNewKey = true;

                KeyValueInfo[] keysFromStorage = noSQLStorage.getAccessTokenSigningKeys_Transaction();

                for (KeyValueInfo key : keysFromStorage) {
                    lastCreated = lastCreated == null || lastCreated < key.createdAtTime ? key.createdAtTime
                            : lastCreated;

                    if (keysCreatedAfterCanVerify <= key.createdAtTime) {
                        if (keysCreatedAfterCanSign <= key.createdAtTime) {
                            generateNewKey = false;
                        }
                        validKeys.add(new KeyInfo(String.valueOf(key.createdAtTime), key.value, key.createdAtTime, signingKeyLifetime, ACCESS_TOKEN_SIGNING_ALGO));
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
                    long creationTime = System.currentTimeMillis();
                    KeyInfo newKey = new KeyInfo(String.valueOf(creationTime), signingKey, creationTime, signingKeyLifetime, ACCESS_TOKEN_SIGNING_ALGO);
                    boolean success = noSQLStorage.addAccessTokenSigningKey_Transaction(
                            new KeyValueInfo(newKey.value, newKey.createdAtTime), lastCreated);

                    // If success is false, someone else already updated this particular field. So we must try again.
                    if (success) {
                        validKeys.add(newKey);
                        break;
                    }
                } else {
                    break;
                }
            }
        } else {
            throw new QuitProgramException("Unsupported storage type detected");
        }

        validKeys.sort(Comparator.comparingLong((KeyInfo key) -> key.createdAtTime).reversed());

        return Collections.unmodifiableList(validKeys);
    }
}
