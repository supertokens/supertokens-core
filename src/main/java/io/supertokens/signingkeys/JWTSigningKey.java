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
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTAsymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTRecipeStorage;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.exceptions.DuplicateKeyIdException;
import io.supertokens.pluginInterface.jwt.nosqlstorage.JWTRecipeNoSQLStorage_1;
import io.supertokens.pluginInterface.jwt.sqlstorage.JWTRecipeSQLStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import org.jetbrains.annotations.TestOnly;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

public class JWTSigningKey extends ResourceDistributor.SingletonResource {
    public static final String RESOURCE_KEY = "io.supertokens.signingKeys.JWTSigningKey";
    private final Main main;
    private final AppIdentifier appIdentifier;

    public static JWTSigningKey getInstance(AppIdentifier appIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        return (JWTSigningKey) main.getResourceDistributor()
                .getResource(appIdentifier, RESOURCE_KEY);
    }

    @TestOnly
    public static JWTSigningKey getInstance(Main main) {
        try {
            return getInstance(new AppIdentifier(null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void loadForAllTenants(Main main, List<AppIdentifier> apps, List<TenantIdentifier> tenantsThatChanged)
            throws UnsupportedJWTSigningAlgorithmException {
        try {
            main.getResourceDistributor().withResourceDistributorLock(() -> {
                try {
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
                                JWTSigningKey jwtSigningKey = new JWTSigningKey(app, main);
                                main.getResourceDistributor()
                                        .setResource(app, RESOURCE_KEY, jwtSigningKey);

                                jwtSigningKey.generateKeysForSupportedAlgos(main);

                            } catch (TenantOrAppNotFoundException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                    }
                } catch (UnsupportedJWTSigningAlgorithmException e) {
                    throw new ResourceDistributor.FuncException(e);
                }
                return null;
            });
        } catch (ResourceDistributor.FuncException e) {
            if (e.getCause() instanceof UnsupportedJWTSigningAlgorithmException) {
                throw (UnsupportedJWTSigningAlgorithmException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }

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


    private JWTSigningKey(AppIdentifier appIdentifier, Main main)
            throws UnsupportedJWTSigningAlgorithmException, TenantOrAppNotFoundException {
        this.appIdentifier = appIdentifier;
        this.main = main;
    }

    private void generateKeysForSupportedAlgos(Main main)
            throws TenantOrAppNotFoundException, UnsupportedJWTSigningAlgorithmException {
        for (int i = 0; i < SupportedAlgorithms.values().length; i++) {
            SupportedAlgorithms currentAlgorithm = SupportedAlgorithms.values()[i];
            try {
                JWTSigningKey.getInstance(appIdentifier, main).getOrCreateAndGetKeyForAlgorithm(currentAlgorithm);
            } catch (StorageQueryException | StorageTransactionLogicException e) {
                // Do nothing, when a call to /recipe/jwt POST is made the core will attempt to create a new key
            }
        }
    }

    /**
     * Used to get all signing keys (symmetric or asymmetric) from storage
     *
     * @return List of {@link JWTSigningKeyInfo}. Asymmetric keys use {@link JWTAsymmetricSigningKeyInfo} and
     * symmetric keys use {@link JWTSymmetricSigningKeyInfo}
     * @throws StorageQueryException            If there is an error interacting with the database
     * @throws StorageTransactionLogicException If there is an error interacting with the database
     */
    public List<JWTSigningKeyInfo> getAllSigningKeys()
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException,
            UnsupportedJWTSigningAlgorithmException {
        JWTRecipeStorage storage = (JWTRecipeStorage) StorageLayer.getStorage(
                this.appIdentifier.getAsPublicTenantIdentifier(), main);

        List<JWTSigningKeyInfo> res;
        if (storage.getType() == STORAGE_TYPE.SQL) {
            JWTRecipeSQLStorage sqlStorage = (JWTRecipeSQLStorage) storage;

            res = sqlStorage.startTransaction(con -> {
                List<JWTSigningKeyInfo> keys = sqlStorage.getJWTSigningKeys_Transaction(appIdentifier, con);

                sqlStorage.commitTransaction(con);
                return keys;
            });
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            JWTRecipeNoSQLStorage_1 noSQLStorage = (JWTRecipeNoSQLStorage_1) storage;

            res = noSQLStorage.getJWTSigningKeys_Transaction();
        } else {
            throw new QuitProgramException("Unsupported storage type detected");
        }

        if (res.size() == 0) {
            generateKeysForSupportedAlgos(main);
            return getAllSigningKeys();
        }

        return res;
    }

    /**
     * Used to retrieve a key for JWT validation, for a given signing algorithm. If there are no keys in storage
     * that
     * match the given algorithm a new one is generated.
     *
     * @param algorithm The signing algorithm
     * @return {@link JWTSigningKeyInfo} key
     * @throws UnsupportedJWTSigningAlgorithmException If there is an error in the provided algorithm when
     *                                                 getting/generating keys
     * @throws StorageQueryException                   If there is an error interacting with the database
     * @throws StorageTransactionLogicException        If there is an error interacting with the database
     */
    public JWTSigningKeyInfo getOrCreateAndGetKeyForAlgorithm(SupportedAlgorithms algorithm)
            throws UnsupportedJWTSigningAlgorithmException, StorageQueryException, StorageTransactionLogicException,
            TenantOrAppNotFoundException {
        JWTRecipeStorage storage = (JWTRecipeStorage) StorageLayer.getStorage(
                this.appIdentifier.getAsPublicTenantIdentifier(), main);

        if (storage.getType() == STORAGE_TYPE.SQL) {
            JWTRecipeSQLStorage sqlStorage = (JWTRecipeSQLStorage) storage;

            try {
                return sqlStorage.startTransaction(con -> {
                    JWTSigningKeyInfo keyInfo = null;

                    List<JWTSigningKeyInfo> keysFromStorage = sqlStorage.getJWTSigningKeys_Transaction(appIdentifier,
                            con);

                    // Loop through the keys and find the first one for the algorithm, if the list is empty a new
                    // key
                    // will be created after the loop
                    for (int i = 0; i < keysFromStorage.size(); i++) {
                        JWTSigningKeyInfo currentKey = keysFromStorage.get(i);
                        if (algorithm.equalsString(currentKey.algorithm)) {
                            keyInfo = currentKey;
                            break;
                        }
                    }

                    // If no key was found create a new one
                    if (keyInfo == null) {
                        while (true) {
                            try {
                                keyInfo = generateKeyForAlgorithm(algorithm);
                                sqlStorage.setJWTSigningKey_Transaction(appIdentifier, con, keyInfo);
                                break;
                            } catch (NoSuchAlgorithmException | UnsupportedJWTSigningAlgorithmException e) {
                                throw new StorageTransactionLogicException(e);
                            } catch (DuplicateKeyIdException e) {
                                // Retry with a new key id
                            } catch (TenantOrAppNotFoundException e) {
                                throw new StorageTransactionLogicException(e);
                            }
                        }
                    }

                    sqlStorage.commitTransaction(con);
                    return keyInfo;
                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof UnsupportedJWTSigningAlgorithmException) {
                    throw (UnsupportedJWTSigningAlgorithmException) e.actualException;
                }
                if (e.actualException instanceof TenantOrAppNotFoundException) {
                    throw (TenantOrAppNotFoundException) e.actualException;
                }

                throw e;
            }
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            JWTRecipeNoSQLStorage_1 noSQLStorage = (JWTRecipeNoSQLStorage_1) storage;

            JWTSigningKeyInfo keyInfo = null;

            List<JWTSigningKeyInfo> keysFromStorage = noSQLStorage.getJWTSigningKeys_Transaction();

            // Loop through the keys and find the first one for the algorithm, if the list is empty a new key
            // will be created after the for loop
            for (int i = 0; i < keysFromStorage.size(); i++) {
                JWTSigningKeyInfo currentKey = keysFromStorage.get(i);
                if (currentKey.algorithm.equalsIgnoreCase(algorithm.name())) {
                    keyInfo = currentKey;
                    break;
                }
            }

            // If no key was found create a new one
            if (keyInfo == null) {
                while (true) {
                    try {
                        keyInfo = generateKeyForAlgorithm(algorithm);
                        boolean success = noSQLStorage
                                .setJWTSigningKeyInfoIfNoKeyForAlgorithmExists_Transaction(keyInfo);

                        if (!success) {
                            continue;
                        }

                        break;
                    } catch (NoSuchAlgorithmException e) {
                        throw new StorageTransactionLogicException(e);
                    } catch (DuplicateKeyIdException e) {
                        // Retry with a new key id
                    }
                }
            }

            return keyInfo;
        }

        throw new QuitProgramException("Unsupported storage type detected");
    }

    private JWTSigningKeyInfo generateKeyForAlgorithm(SupportedAlgorithms algorithm)
            throws NoSuchAlgorithmException, UnsupportedJWTSigningAlgorithmException {
        if (algorithm.getAlgorithmType().equalsIgnoreCase("rsa")) {
            long currentTimeInMillis = System.currentTimeMillis();
            Utils.PubPriKey newKey = Utils.generateNewPubPriKey();
            return new JWTAsymmetricSigningKeyInfo("s-" + Utils.getUUID(), currentTimeInMillis, algorithm.name(),
                    newKey.publicKey, newKey.privateKey);
        }

        throw new IllegalArgumentException();
    }
}