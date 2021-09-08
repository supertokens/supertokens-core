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
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTAsymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.exceptions.DuplicateKeyIdException;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.pluginInterface.session.noSqlStorage.SessionNoSQLStorage_1;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.pluginInterface.sqlStorage.TransactionConnection;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class JWTSigningKey extends ResourceDistributor.SingletonResource {
    public static final String RESOURCE_KEY = "io.supertokens.jwt.JWTSigningKey";
    private final Main main;

    public enum SupportedAlgorithms {
        RS256;

        public String getAlgorithmType() {
            if (this == SupportedAlgorithms.RS256) {
                return "rsa";
            }

            return "";
        }

        public boolean equalsString(String algorithmString) {
            return this.name().equalsIgnoreCase(algorithmString);
        }
    }

    private JWTSigningKey(Main main) {
        this.main = main;
    }

    public static JWTSigningKey getInstance(Main main) {
        JWTSigningKey instance = (JWTSigningKey) main.getResourceDistributor().getResource(RESOURCE_KEY);

        if (instance == null) {
            instance = new JWTSigningKey(main);
            main.getResourceDistributor().setResource(RESOURCE_KEY, instance);
        }

        return instance;
    }

    /**
     * Used to get all signing keys (symmetric or asymmetric) from storage
     * @return List of {@link JWTSigningKeyInfo}. Asymmetric keys use {@link JWTAsymmetricSigningKeyInfo} and symmetric keys use {@link JWTSymmetricSigningKeyInfo}
     * @throws StorageQueryException If there is an error interacting with the database
     * @throws StorageTransactionLogicException If there is an error interacting with the database
     */
    public List<JWTSigningKeyInfo> getAllSigningKeys()
            throws StorageQueryException, StorageTransactionLogicException {
        SessionStorage storage = StorageLayer.getSessionStorage(main);

        if (storage.getType() == STORAGE_TYPE.SQL) {
            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;

            return sqlStorage.startTransaction(con -> {
               List<JWTSigningKeyInfo> keys = sqlStorage.getJWTSigningKeys_Transaction(con);

               sqlStorage.commitTransaction(con);
               return keys;
            });
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            SessionNoSQLStorage_1 noSQLStorage = (SessionNoSQLStorage_1) storage;

            List<JWTSigningKeyInfo> keys;
            List<JWTSigningKeyInfo> keysFromStorage = noSQLStorage.getJWTSigningKeys_Transaction();

            if (keysFromStorage == null) {
                keys = new ArrayList<>();
            } else {
                keys = keysFromStorage;
            }

            return keys;
        }

        throw new QuitProgramException("Unsupported storage type detected");
    }

    /**
     * Used to retrieve a key for JWT validation, for a given signing algorithm. If there are no keys in storage that match the given algorithm a new one is generated.
     * @param algorithm The signing algorithm
     * @return {@link JWTSigningKeyInfo} key
     * @throws UnsupportedJWTSigningAlgorithmException If there is an error in the provided algorithm when getting/generating keys
     * @throws StorageQueryException If there is an error interacting with the database
     * @throws StorageTransactionLogicException If there is an error interacting with the database
     */
    public JWTSigningKeyInfo getOrCreateAndGetKeyForAlgorithm(SupportedAlgorithms algorithm)
            throws UnsupportedJWTSigningAlgorithmException, StorageQueryException, StorageTransactionLogicException {
        SessionStorage storage = StorageLayer.getSessionStorage(main);

        if (storage.getType() == STORAGE_TYPE.SQL) {
            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;

            try {
                return sqlStorage.startTransaction(con -> {
                    JWTSigningKeyInfo keyInfo = null;

                    List<JWTSigningKeyInfo> keysFromStorage = sqlStorage.getJWTSigningKeys_Transaction(con);

                    // Loop through the keys and find the first one for the algorithm, if the list is empty a new key will be created after the loop
                    for (int i = 0; i < keysFromStorage.size(); i++) {
                        JWTSigningKeyInfo currentKey = keysFromStorage.get(i);
                        if (algorithm.equalsString(currentKey.algorithm)) {
                            keyInfo = currentKey;
                            break;
                        }
                    }

                    // If no key was found create a new one
                    if (keyInfo == null) {
                        keyInfo = generateAndSetKeyInStorage(sqlStorage, con, algorithm);
                    }

                    sqlStorage.commitTransaction(con);
                    return keyInfo;
                });
            } catch (StorageTransactionLogicException e) {
                 if (e.actualException instanceof UnsupportedJWTSigningAlgorithmException) {
                    throw (UnsupportedJWTSigningAlgorithmException) e.actualException;
                }

                throw e;
            }
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            SessionNoSQLStorage_1 noSQLStorage = (SessionNoSQLStorage_1) storage;

            JWTSigningKeyInfo keyInfo = null;

            while (true) {
                List<JWTSigningKeyInfo> keysFromStorage = noSQLStorage.getJWTSigningKeys_Transaction();

                // Loop through the keys and find the first one for the algorithm, if the list is empty a new key will be created after the for loop
                for (int i = 0; i < keysFromStorage.size(); i++) {
                    JWTSigningKeyInfo currentKey = keysFromStorage.get(i);
                    if (currentKey.algorithm.equalsIgnoreCase(algorithm.name())) {
                        keyInfo = currentKey;
                        break;
                    }
                }

                // If no key was found create a new one
                if (keyInfo == null) {
                    keyInfo = generateAndSetKeyInStorage(noSQLStorage, algorithm);

                    if (keyInfo == null) {
                        continue;
                    }
                }

                return keyInfo;
            }
        }

        throw new QuitProgramException("Unsupported storage type detected");
    }

    private JWTSigningKeyInfo generateKeyForAlgorithm(SupportedAlgorithms algorithm) throws NoSuchAlgorithmException,
            UnsupportedJWTSigningAlgorithmException {
        if (algorithm.getAlgorithmType().equalsIgnoreCase("rsa")) {
            long currentTimeInMillis = System.currentTimeMillis();
            Utils.PubPriKey newKey = Utils.generateNewPubPriKey();
            return new JWTAsymmetricSigningKeyInfo(Utils.getUUID(), currentTimeInMillis, algorithm.name(), newKey.publicKey,
                    newKey.privateKey);
        }

        throw new IllegalArgumentException();
    }

    // SQL storage
    private JWTSigningKeyInfo generateAndSetKeyInStorage(SessionSQLStorage storage, TransactionConnection con, SupportedAlgorithms algorithm)
            throws StorageTransactionLogicException, StorageQueryException {
        while (true) {
            try {
                JWTSigningKeyInfo keyInfo = generateKeyForAlgorithm(algorithm);
                storage.setJWTSigningKey_Transaction(con, keyInfo);
                return keyInfo;
            } catch (NoSuchAlgorithmException | UnsupportedJWTSigningAlgorithmException e) {
                throw new StorageTransactionLogicException(e);
            } catch (DuplicateKeyIdException e) {
                // Retry with a new key id
            }
        }
    }

    // No SQL storage
    private JWTSigningKeyInfo generateAndSetKeyInStorage(SessionNoSQLStorage_1 storage, SupportedAlgorithms algorithm) throws StorageTransactionLogicException, UnsupportedJWTSigningAlgorithmException, StorageQueryException {
        while (true) {
            try {
                JWTSigningKeyInfo keyInfo = generateKeyForAlgorithm(algorithm);
                boolean success = storage.setJWTSigningKeyInfo_Transaction(keyInfo);

                if (!success) {
                    return null;
                }

                return keyInfo;
            } catch (NoSuchAlgorithmException e) {
                throw new StorageTransactionLogicException(e);
            } catch (DuplicateKeyIdException e) {
                // Retry with a new key id
            }
        }
    }
}
