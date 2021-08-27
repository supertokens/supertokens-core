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
import java.util.Set;

public class JWTSigningKey extends ResourceDistributor.SingletonResource {
    public static final String RESOURCE_KEY = "io.supertokens.jwt.JWTSigningKey";
    private final Main main;

    // Used to validate the algorithm sent from the backend SDK and to fetch an appropriate key when creating JWTs
    public static final Set<String> supportedAlgorithms = Set.of("rs256");

    private JWTSigningKey(Main main) {
        this.main = main;
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

    public synchronized List<JWTSigningKeyInfo> getAllSigningKeys()
            throws StorageQueryException, StorageTransactionLogicException {
        SessionStorage storage = StorageLayer.getSessionStorage(main);

        if (storage.getType() == STORAGE_TYPE.SQL) {
            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;

            return sqlStorage.startTransaction(con -> {
               List<JWTSigningKeyInfo> keys;
               List<JWTSigningKeyInfo> keysFromStorage = sqlStorage.getJWTSigningKeys_Transaction(con);

               if (keysFromStorage == null) {
                   keys = new ArrayList<>();
               } else {
                   keys = keysFromStorage;
               }

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

    public synchronized JWTSigningKeyInfo getKeyForAlgorithm(String algorithm)
            throws NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException {
        if (!JWTSigningKey.isJWTAlgorithmSupported(algorithm)) {
            throw new NoSuchAlgorithmException(algorithm + "is not a supported signing algorithm");
        }

        SessionStorage storage = StorageLayer.getSessionStorage(main);

        if (storage.getType() == STORAGE_TYPE.SQL) {
            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;

            return sqlStorage.startTransaction(con -> {
               JWTSigningKeyInfo keyInfo = null;

               List<JWTSigningKeyInfo> keysFromStorage = sqlStorage.getJWTSigningKeys_Transaction(con);

               if (keysFromStorage.isEmpty()) {
                   try {
                       long currentTimeInMillis = System.currentTimeMillis();
                       Utils.PubPriKey newKey = Utils.generateNewPubPriKey();
                       keyInfo = new JWTSigningKeyInfo(Utils.getUUID(), currentTimeInMillis, algorithm, newKey.publicKey + "|" +newKey.privateKey);
                       sqlStorage.setJWTSigningKey_Transaction(con, keyInfo);
                   } catch (NoSuchAlgorithmException e) {
                       throw new StorageTransactionLogicException(e);
                   }
               } else {
                   // Loop through the keys and find the first one for the algorithm
                   for (int i = 0; i < keysFromStorage.size(); i++) {
                       JWTSigningKeyInfo currentKey = keysFromStorage.get(i);
                       if (currentKey.algorithm.equalsIgnoreCase(algorithm)) {
                           keyInfo = currentKey;
                           break;
                       }
                   }

                   // If no key was found create a new one
                   if (keyInfo == null) {
                       try {
                           long currentTimeInMillis = System.currentTimeMillis();
                           Utils.PubPriKey newKey = Utils.generateNewPubPriKey();
                           keyInfo = new JWTSigningKeyInfo(Utils.getUUID(), currentTimeInMillis, algorithm, newKey.publicKey + "|" +newKey.privateKey);
                           sqlStorage.setJWTSigningKey_Transaction(con, keyInfo);
                       } catch (NoSuchAlgorithmException e) {
                           throw new StorageTransactionLogicException(e);
                       }
                   }
               }

               sqlStorage.commitTransaction(con);
               return keyInfo;
            });
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            SessionNoSQLStorage_1 noSQLStorage = (SessionNoSQLStorage_1) storage;

            JWTSigningKeyInfo keyInfo = null;

            List<JWTSigningKeyInfo> keysFromStorage = noSQLStorage.getJWTSigningKeys_Transaction();

            if (keysFromStorage.isEmpty()) {
                long currentTimeInMillis = System.currentTimeMillis();
                Utils.PubPriKey newKey = Utils.generateNewPubPriKey();
                keyInfo = new JWTSigningKeyInfo(Utils.getUUID(), currentTimeInMillis, algorithm, newKey.publicKey + "|" + newKey.privateKey);
                noSQLStorage.setJWTSigningKeyInfo_Transaction(keyInfo);
            } else {
                for (int i = 0; i < keysFromStorage.size(); i++) {
                    JWTSigningKeyInfo currentKey = keysFromStorage.get(i);
                    if (currentKey.algorithm.equalsIgnoreCase(algorithm)) {
                        keyInfo = currentKey;
                        break;
                    }
                }

                // If no key was found create a new one
                if (keyInfo == null) {
                    try {
                        long currentTimeInMillis = System.currentTimeMillis();
                        Utils.PubPriKey newKey = Utils.generateNewPubPriKey();
                        keyInfo = new JWTSigningKeyInfo(Utils.getUUID(), currentTimeInMillis, algorithm, newKey.publicKey + "|" +newKey.privateKey);
                        noSQLStorage.setJWTSigningKeyInfo_Transaction(keyInfo);
                    } catch (NoSuchAlgorithmException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                }
            }

            return keyInfo;
        }

        throw new QuitProgramException("Unsupported storage type detected");
    }

    public static String getAlgorithmType(String algorithm) throws NoSuchAlgorithmException {
        if (algorithm.equalsIgnoreCase("rs256")) {
            return "RSA";
        }

        throw new NoSuchAlgorithmException();
    }

    public static boolean isJWTAlgorithmSupported(String algorithm) {
        return supportedAlgorithms.contains(algorithm.toLowerCase());
    }
}
