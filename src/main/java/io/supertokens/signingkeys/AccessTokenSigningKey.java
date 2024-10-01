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
import io.supertokens.pluginInterface.jwt.JWTRecipeStorage;
import io.supertokens.pluginInterface.jwt.exceptions.DuplicateKeyIdException;
import io.supertokens.pluginInterface.jwt.nosqlstorage.JWTRecipeNoSQLStorage_1;
import io.supertokens.pluginInterface.jwt.sqlstorage.JWTRecipeSQLStorage;
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
import java.util.*;

public class AccessTokenSigningKey extends ResourceDistributor.SingletonResource {
    private static final String ACCESS_TOKEN_SIGNING_ALGO = "RS256";
    // We keep the signing keys after generating a new one for accessTokenValidity multiplied by this value
    // JWTs are still checked for expiration after signature verification, this doesn't extend the lifetime of the
    // sessions.
    private static final int SIGNING_KEY_VALIDITY_OVERLAP = 2;

    // We create signing keys with this overlap, since we want to add the keys to the jwks endpoint long before they
    // are used
    // The default overlap is only overridden by tests
    private int dynamicSigningKeyOverlapMS = 60000; // 60 seconds

    private static final String RESOURCE_KEY = "io.supertokens.signingKeys.AccessTokenSigningKey";
    private final Main main;
    private List<SigningKeys.KeyInfo> validKeys;
    private final AppIdentifier appIdentifier;

    private AccessTokenSigningKey(AppIdentifier appIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        this.main = main;
        this.appIdentifier = appIdentifier;
        try {
            this.transferLegacyKeyToNewTable();
            this.getOrCreateAndGetSigningKeys();
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            Logging.error(main, appIdentifier.getAsPublicTenantIdentifier(),
                    "Error while fetching access token signing key", false, e);
        }
    }

    public static AccessTokenSigningKey getInstance(AppIdentifier appIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        return (AccessTokenSigningKey) main.getResourceDistributor()
                .getResource(appIdentifier, RESOURCE_KEY);
    }

    @TestOnly
    public static AccessTokenSigningKey getInstance(Main main) {
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
                            new ResourceDistributor.KeyClass(
                                    app,
                                    RESOURCE_KEY));
                    if (resource != null && !tenantsThatChanged.contains(app.getAsPublicTenantIdentifier())) {
                        main.getResourceDistributor()
                                .setResource(app,
                                        RESOURCE_KEY,
                                        resource);
                    } else {
                        try {
                            main.getResourceDistributor()
                                    .setResource(
                                            app,
                                            RESOURCE_KEY,
                                            new AccessTokenSigningKey(app, main));
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

    public synchronized void transferLegacyKeyToNewTable()
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        Storage storage = StorageLayer.getStorage(this.appIdentifier.getAsPublicTenantIdentifier(), main);
        JWTRecipeStorage jwtRecipeStorage = (JWTRecipeStorage) storage;
        final boolean isLegacyKeyDynamic = Config.getConfig(this.appIdentifier.getAsPublicTenantIdentifier(), main)
                .getAccessTokenSigningKeyDynamic();

        if (storage.getType() == STORAGE_TYPE.SQL) {
            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;
            JWTRecipeSQLStorage sqlJWTRecipeStorage = (JWTRecipeSQLStorage) jwtRecipeStorage;

            try {
                // start transaction
                sqlStorage.startTransaction(con -> {
                    KeyValueInfo legacyKey = sqlStorage.getLegacyAccessTokenSigningKey_Transaction(appIdentifier, con);

                    if (legacyKey != null) {
                        if (isLegacyKeyDynamic) {
                            try {
                                sqlStorage.addAccessTokenSigningKey_Transaction(this.appIdentifier, con, legacyKey);
                            } catch (TenantOrAppNotFoundException e) {
                                throw new StorageTransactionLogicException(e);
                            }
                        } else {
                            try {
                                sqlJWTRecipeStorage.setJWTSigningKey_Transaction(appIdentifier, con,
                                        new JWTAsymmetricSigningKeyInfo(
                                                "s-" + Utils.getUUID(), legacyKey.createdAtTime,
                                                ACCESS_TOKEN_SIGNING_ALGO,
                                                legacyKey.value
                                        ));
                            } catch (DuplicateKeyIdException e) {
                                // This should be exceedingly rare, since we are generating the UUID above.
                                throw new StorageTransactionLogicException(e);
                            } catch (TenantOrAppNotFoundException e) {
                                throw new StorageTransactionLogicException(e);
                            }
                        }
                        sqlStorage.removeLegacyAccessTokenSigningKey_Transaction(appIdentifier, con);
                        sqlStorage.commitTransaction(con);
                    }
                    return legacyKey;
                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof TenantOrAppNotFoundException) {
                    throw (TenantOrAppNotFoundException) e.actualException;
                }
                throw e;
            }
        } else {
            SessionNoSQLStorage_1 noSQLStorage = (SessionNoSQLStorage_1) storage;
            JWTRecipeNoSQLStorage_1 noSQLJWTRecipeStorage = (JWTRecipeNoSQLStorage_1) jwtRecipeStorage;
            KeyValueInfoWithLastUpdated legacyKey = noSQLStorage.getLegacyAccessTokenSigningKey_Transaction();

            if (legacyKey != null) {
                // We should only get here once, after an upgrade.
                if (isLegacyKeyDynamic) {
                    noSQLStorage.addAccessTokenSigningKey_Transaction(
                            new KeyValueInfo(legacyKey.value, legacyKey.createdAtTime), null);
                } else {
                    try {
                        noSQLJWTRecipeStorage.setJWTSigningKeyInfoIfNoKeyForAlgorithmExists_Transaction(
                                new JWTAsymmetricSigningKeyInfo(
                                        "s-" + Utils.getUUID(), legacyKey.createdAtTime, ACCESS_TOKEN_SIGNING_ALGO,
                                        legacyKey.value
                                ));
                    } catch (DuplicateKeyIdException e) {
                        // This should be exceedingly rare, since we are generating the UUID above.
                        throw new StorageTransactionLogicException(e);
                    }
                }
                // We don't need to check the lastUpdatedSign here, since we never update or set
                // legacy keys anymore.
                noSQLStorage.removeLegacyAccessTokenSigningKey_Transaction();
            }
        }
    }

    public synchronized void cleanExpiredAccessTokenSigningKeys() throws StorageQueryException,
            TenantOrAppNotFoundException {
        SessionStorage storage = (SessionStorage) StorageLayer.getStorage(
                this.appIdentifier.getAsPublicTenantIdentifier(), main);
        CoreConfig config = Config.getConfig(this.appIdentifier.getAsPublicTenantIdentifier(), main);

        final long signingKeyLifetime = config.getAccessTokenDynamicSigningKeyUpdateIntervalInMillis()
                + SIGNING_KEY_VALIDITY_OVERLAP * config.getAccessTokenValidityInMillis();

        storage.removeAccessTokenSigningKeysBefore(appIdentifier, System.currentTimeMillis() - signingKeyLifetime);
    }

    public List<SigningKeys.KeyInfo> getOrCreateAndGetSigningKeys()
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        Storage storage = StorageLayer.getStorage(this.appIdentifier.getAsPublicTenantIdentifier(), main);
        CoreConfig config = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main);

        // Access token signing keys older than this are deleted (ms)
        final long signingKeyLifetime = config.getAccessTokenDynamicSigningKeyUpdateIntervalInMillis()
                + SIGNING_KEY_VALIDITY_OVERLAP * config.getAccessTokenValidityInMillis();
        // Keys created after this timestamp can be used to sign access tokens (ms) after the overlap period
        final long keysCreatedAfterCanSign = System.currentTimeMillis()
                - config.getAccessTokenDynamicSigningKeyUpdateIntervalInMillis() + getDynamicSigningKeyOverlapMS();
        // Keys created after this timestamp can be used to verify access token signatures (ms)
        final long keysCreatedAfterCanVerify = System.currentTimeMillis() - signingKeyLifetime;

        // Keys we can use for signature verification
        List<SigningKeys.KeyInfo> validKeys = null;

        if (storage.getType() == STORAGE_TYPE.SQL) {
            SessionSQLStorage sqlStorage = (SessionSQLStorage) storage;
            try {
                // start transaction
                validKeys = sqlStorage.startTransaction(con -> {
                    List<SigningKeys.KeyInfo> validKeysFromSQL = new ArrayList<>();

                    // We have to generate a new key if we couldn't find one we can use for signing
                    boolean generateNewKey = true;

                    KeyValueInfo[] keysFromStorage = sqlStorage.getAccessTokenSigningKeys_Transaction(appIdentifier,
                            con);

                    for (KeyValueInfo key : keysFromStorage) {
                        if (keysCreatedAfterCanVerify <= key.createdAtTime) {
                            if (keysCreatedAfterCanSign <= key.createdAtTime) {
                                generateNewKey = false;
                            }
                            validKeysFromSQL.add(
                                    new SigningKeys.KeyInfo("d-" + key.createdAtTime, key.value, key.createdAtTime,
                                            signingKeyLifetime, ACCESS_TOKEN_SIGNING_ALGO));
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
                        SigningKeys.KeyInfo newKey = new SigningKeys.KeyInfo("d-" + creationTime, signingKey,
                                creationTime, signingKeyLifetime,
                                ACCESS_TOKEN_SIGNING_ALGO);
                        try {
                            sqlStorage.addAccessTokenSigningKey_Transaction(appIdentifier, con,
                                    new KeyValueInfo(newKey.value, newKey.createdAtTime));
                        } catch (TenantOrAppNotFoundException e) {
                            throw new StorageTransactionLogicException(e);
                        }
                        validKeysFromSQL.add(newKey);
                    }

                    sqlStorage.commitTransaction(con);
                    return validKeysFromSQL;
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
                // We need to clear the array after every retry.
                validKeys = new ArrayList<SigningKeys.KeyInfo>();
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
                        validKeys.add(
                                new SigningKeys.KeyInfo("d-" + key.createdAtTime, key.value, key.createdAtTime,
                                        signingKeyLifetime,
                                        ACCESS_TOKEN_SIGNING_ALGO));
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
                    SigningKeys.KeyInfo newKey = new SigningKeys.KeyInfo("d-" + creationTime, signingKey, creationTime,
                            signingKeyLifetime,
                            ACCESS_TOKEN_SIGNING_ALGO);
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

        validKeys.sort(Comparator.comparingLong((SigningKeys.KeyInfo key) -> key.createdAtTime).reversed());

        return Collections.unmodifiableList(validKeys);
    }

    @TestOnly()
    public void setDynamicSigningKeyOverlapMS(int overlap) {
        dynamicSigningKeyOverlapMS = overlap;
    }

    public int getDynamicSigningKeyOverlapMS() throws TenantOrAppNotFoundException {
        CoreConfig config = Config.getConfig(this.appIdentifier.getAsPublicTenantIdentifier(), main);
        // We do this, because otherwise we could get issues in testing if
        // getAccessTokenDynamicSigningKeyUpdateInterval is shorter than dynamicSigningKeyOverlapMS
        // If we didn't explicitly set it, we try to set it to a sensible default. In tests where this matters
        // setDynamicSigningKeyOverlapMS should be used.
        if (Main.isTesting && dynamicSigningKeyOverlapMS == 60000 &&
                config.getAccessTokenDynamicSigningKeyUpdateIntervalInMillis() < 60000) {
            return (int) (config.getAccessTokenDynamicSigningKeyUpdateIntervalInMillis() / 5);
        }
        return dynamicSigningKeyOverlapMS;
    }

}
