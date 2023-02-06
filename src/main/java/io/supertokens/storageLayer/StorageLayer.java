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

package io.supertokens.storageLayer;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.config.Config;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.exceptions.TenantOrAppNotFoundException;
import io.supertokens.inmemorydb.Start;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.pluginInterface.exceptions.DbInitException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.jwt.JWTRecipeStorage;
import io.supertokens.pluginInterface.multitenancy.MultitenancyStorage;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.passwordless.sqlStorage.PasswordlessSQLStorage;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.pluginInterface.thirdparty.sqlStorage.ThirdPartySQLStorage;
import io.supertokens.pluginInterface.useridmapping.UserIdMappingStorage;
import io.supertokens.pluginInterface.usermetadata.sqlStorage.UserMetadataSQLStorage;
import io.supertokens.pluginInterface.userroles.sqlStorage.UserRolesSQLStorage;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class StorageLayer extends ResourceDistributor.SingletonResource {

    public static final String RESOURCE_KEY = "io.supertokens.storageLayer.StorageLayer";
    private final Storage storage;
    private static URLClassLoader ucl = null;
    private static final Object lock = new Object();

    public static Storage getNewStorageInstance(Main main, JsonObject config) throws InvalidConfigException {
        Storage result;
        if (StorageLayer.ucl == null) {
            result = new Start(main);
        } else {
            Storage storageLayer = null;
            ServiceLoader<Storage> sl = ServiceLoader.load(Storage.class, ucl);
            for (Storage plugin : sl) {
                if (storageLayer == null) {
                    storageLayer = plugin;
                } else {
                    throw new QuitProgramException(
                            "Multiple database plugins found. Please make sure that just one plugin is in the "
                                    + "/plugin" + " "
                                    + "folder of the installation. Alternatively, please redownload and install "
                                    + "SuperTokens" + ".");
                }
            }
            if (storageLayer != null && !main.isForceInMemoryDB()
                    && (storageLayer.canBeUsed(config) || CLIOptions.get(main).isForceNoInMemoryDB())) {
                result = storageLayer;
            } else {
                result = new Start(main);
            }
        }
        result.constructor(main.getProcessId(), Main.makeConsolePrintSilent);

        // this is intentionally null, null below cause log levels is per core and not per tenant anyway
        result.loadConfig(config, Config.getBaseConfig(main).getLogLevels(main));
        return result;
    }

    private StorageLayer(Storage storage) {
        this.storage = storage;
    }

    private StorageLayer(Main main, String pluginFolderPath, JsonObject configJson)
            throws MalformedURLException, InvalidConfigException {
        Logging.info(main, "Loading storage layer.", true);
        File loc = new File(pluginFolderPath);

        File[] flist = loc.listFiles(file -> file.getPath().toLowerCase().endsWith(".jar"));

        if (flist != null) {
            URL[] urls = new URL[flist.length];
            for (int i = 0; i < flist.length; i++) {
                urls[i] = flist[i].toURI().toURL();
            }
            if (StorageLayer.ucl == null) {
                // we have this as a static variable because
                // in prod, this is loaded just once anyway.
                // During testing, we just want to load the jars
                // once too cause the JARs don't change across tests either.
                StorageLayer.ucl = new URLClassLoader(urls);
            }
        }

        this.storage = getNewStorageInstance(main, configJson);

        if (this.storage instanceof Start) {
            Logging.info(main, "Using in memory storage.", true);
        }
    }

    public static void close(Main main) {
        Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> resources =
                main.getResourceDistributor()
                        .getAllResourcesWithResourceKey(RESOURCE_KEY);
        for (ResourceDistributor.SingletonResource resource : resources.values()) {
            ((StorageLayer) resource).storage.close();
        }
    }

    public static void stopLogging(Main main) {
        Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> resources =
                main.getResourceDistributor()
                        .getAllResourcesWithResourceKey(RESOURCE_KEY);
        for (ResourceDistributor.SingletonResource resource : resources.values()) {
            ((StorageLayer) resource).storage.stopLogging();
        }
    }

    @TestOnly
    public static void deleteAllInformation(Main main) throws StorageQueryException {
        Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> resources =
                main.getResourceDistributor()
                        .getAllResourcesWithResourceKey(RESOURCE_KEY);
        for (ResourceDistributor.SingletonResource resource : resources.values()) {
            ((StorageLayer) resource).storage.deleteAllInformation();
        }
    }

    @TestOnly
    public static void close() {
        // TODO: remove this function and remove all the places it's being used.
    }

    @TestOnly
    public static void clearURLClassLoader() {
        /*
         * This is needed for PluginTests where we want to try and load from the plugin directory
         * again and again. If we do not close the static URLCLassLoader before, those tests will fail
         *
         * Also note that closing it doesn't actually remove it from memory (strange..). But we do it anyway
         */
        if (StorageLayer.ucl != null) {
            try {
                StorageLayer.ucl.close();
            } catch (IOException ignored) {
            }
            StorageLayer.ucl = null;
        }
    }

    private static StorageLayer getInstance(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        return (StorageLayer) main.getResourceDistributor().getResource(tenantIdentifier, RESOURCE_KEY);
    }

    public static void initPrimary(Main main, String pluginFolderPath, JsonObject configJson)
            throws MalformedURLException, InvalidConfigException {
        synchronized (lock) {
            main.getResourceDistributor().setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                    new StorageLayer(main, pluginFolderPath, configJson));
        }
    }

    public static void loadAllTenantStorage(Main main, TenantConfig[] tenants)
            throws InvalidConfigException, IOException, DbInitException {
        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE, null);

        Map<ResourceDistributor.KeyClass, JsonObject> normalisedConfigs = Config.getNormalisedConfigsForAllTenants(
                tenants,
                Config.getBaseConfigAsJsonObject(main));

        Map<ResourceDistributor.KeyClass, Storage> resourceKeyToStorageMap = new HashMap<>();
        {
            Map<String, Storage> idToStorageMap = new HashMap<>();
            for (ResourceDistributor.KeyClass key : normalisedConfigs.keySet()) {
                Storage storage = StorageLayer.getNewStorageInstance(main, normalisedConfigs.get(key));
                String userPoolId = storage.getUserPoolId();
                String connectionPoolId = storage.getConnectionPoolId();
                String uniqueId = userPoolId + "~" + connectionPoolId;
                if (idToStorageMap.get(uniqueId) != null) {
                    // this means there already exists a storage object that can be reused
                    // for this tenant
                    resourceKeyToStorageMap.put(key, idToStorageMap.get(uniqueId));
                } else {
                    idToStorageMap.put(uniqueId, storage);
                    resourceKeyToStorageMap.put(key, storage);
                }
            }
        }

        // at this point, we have made sure that all the configs are fine and that the storage
        // objects are shared across tenants based on the config of each tenant.

        // now we loop through existing storage objects in the main resource distributor and reuse them
        // if the unique ID is the same as the storage objects created above.
        synchronized (lock) {
            Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> existingStorageMap =
                    main.getResourceDistributor()
                            .getAllResourcesWithResourceKey(RESOURCE_KEY);
            Map<String, StorageLayer> idToExistingStorageLayerMap = new HashMap<>();
            for (ResourceDistributor.SingletonResource resource : existingStorageMap.values()) {
                StorageLayer currStorageLayer = (StorageLayer) resource;
                String userPoolId = currStorageLayer.storage.getUserPoolId();
                String connectionPoolId = currStorageLayer.storage.getConnectionPoolId();
                String uniqueId = userPoolId + "~" + connectionPoolId;
                idToExistingStorageLayerMap.put(uniqueId, currStorageLayer);
            }
            main.getResourceDistributor().clearAllResourcesWithResourceKey(RESOURCE_KEY);

            for (ResourceDistributor.KeyClass key : resourceKeyToStorageMap.keySet()) {
                Storage currStorage = resourceKeyToStorageMap.get(key);
                String userPoolId = currStorage.getUserPoolId();
                String connectionPoolId = currStorage.getConnectionPoolId();
                String uniqueId = userPoolId + "~" + connectionPoolId;
                if (idToExistingStorageLayerMap.containsKey(uniqueId)) {
                    // we reuse the existing storage layer
                    resourceKeyToStorageMap.put(key, idToExistingStorageLayerMap.get(uniqueId).storage);
                }

                main.getResourceDistributor().setResource(key.getTenantIdentifier(), RESOURCE_KEY,
                        new StorageLayer(resourceKeyToStorageMap.get(key)));
            }

            // we remove storage layers that are no longer being used
            for (ResourceDistributor.KeyClass key : existingStorageMap.keySet()) {
                try {
                    if (((StorageLayer) main.getResourceDistributor()
                            .getResource(key.getTenantIdentifier(), RESOURCE_KEY)).storage !=
                            ((StorageLayer) existingStorageMap.get(key)).storage) {
                        // this means that this storage layer is no longer being used, so we close it
                        ((StorageLayer) existingStorageMap.get(key)).storage.close();
                        ((StorageLayer) existingStorageMap.get(key)).storage.stopLogging();
                    }
                } catch (TenantOrAppNotFoundException e) {
                    throw new IllegalStateException("Should never come here");
                }
            }

            // we call init on all the newly saved storage objects.
            DbInitException lastError = null;
            Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> resources =
                    main.getResourceDistributor()
                            .getAllResourcesWithResourceKey(RESOURCE_KEY);
            for (ResourceDistributor.SingletonResource resource : resources.values()) {
                try {
                    ((StorageLayer) resource).storage.initStorage();
                    ((StorageLayer) resource).storage.initFileLogging(
                            Config.getBaseConfig(main).getInfoLogPath(main),
                            Config.getBaseConfig(main).getErrorLogPath(main));
                } catch (DbInitException e) {
                    lastError = e;
                }
            }
            if (lastError != null) {
                throw lastError;
            }
        }
    }

    public static Storage getBaseStorage(Main main) {
        synchronized (lock) {
            try {
                return getInstance(new TenantIdentifier(null, null, null), main).storage;
            } catch (TenantOrAppNotFoundException e) {
                throw new IllegalStateException("Should never come here");
            }
        }
    }

    public static Storage getStorage(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        synchronized (lock) {
            return getInstance(tenantIdentifier, main).storage;
        }
    }

    @TestOnly
    public static Storage getStorage(Main main) {
        try {
            return getStorage(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static AuthRecipeStorage getAuthRecipeStorage(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        synchronized (lock) {
            return (AuthRecipeStorage) getInstance(tenantIdentifier, main).storage;
        }
    }

    @TestOnly
    public static AuthRecipeStorage getAuthRecipeStorage(Main main) {
        try {
            return getAuthRecipeStorage(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static SessionStorage getSessionStorage(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        synchronized (lock) {
            return (SessionStorage) getInstance(tenantIdentifier, main).storage;
        }
    }

    @TestOnly
    public static SessionStorage getSessionStorage(Main main) {
        try {
            return getSessionStorage(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static EmailPasswordSQLStorage getEmailPasswordStorage(TenantIdentifier tenantIdentifier,
                                                                  Main main) throws TenantOrAppNotFoundException {
        synchronized (lock) {
            if (getInstance(tenantIdentifier, main).storage.getType() != STORAGE_TYPE.SQL) {
                // we only support SQL for now
                throw new UnsupportedOperationException("");
            }
            return (EmailPasswordSQLStorage) getInstance(tenantIdentifier, main).storage;
        }
    }

    @TestOnly
    public static EmailPasswordSQLStorage getEmailPasswordStorage(Main main) {
        try {
            return getEmailPasswordStorage(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static EmailVerificationSQLStorage getEmailVerificationStorage(TenantIdentifier tenantIdentifier,
                                                                          Main main) throws
            TenantOrAppNotFoundException {
        synchronized (lock) {
            if (getInstance(tenantIdentifier, main).storage.getType() != STORAGE_TYPE.SQL) {
                // we only support SQL for now
                throw new UnsupportedOperationException("");
            }
            return (EmailVerificationSQLStorage) getInstance(tenantIdentifier, main).storage;
        }
    }

    @TestOnly
    public static EmailVerificationSQLStorage getEmailVerificationStorage(Main main) {
        try {
            return getEmailVerificationStorage(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static ThirdPartySQLStorage getThirdPartyStorage(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        synchronized (lock) {
            if (getInstance(tenantIdentifier, main).storage.getType() != STORAGE_TYPE.SQL) {
                // we only support SQL for now
                throw new UnsupportedOperationException("");
            }
            return (ThirdPartySQLStorage) getInstance(tenantIdentifier, main).storage;
        }
    }

    @TestOnly
    public static ThirdPartySQLStorage getThirdPartyStorage(Main main) {
        try {
            return getThirdPartyStorage(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static PasswordlessSQLStorage getPasswordlessStorage(TenantIdentifier tenantIdentifier,
                                                                Main main) throws TenantOrAppNotFoundException {
        synchronized (lock) {
            if (getInstance(tenantIdentifier, main).storage.getType() != STORAGE_TYPE.SQL) {
                // we only support SQL for now
                throw new UnsupportedOperationException("");
            }
            return (PasswordlessSQLStorage) getInstance(tenantIdentifier, main).storage;
        }
    }

    @TestOnly
    public static PasswordlessSQLStorage getPasswordlessStorage(Main main) {
        try {
            return getPasswordlessStorage(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static JWTRecipeStorage getJWTRecipeStorage(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        synchronized (lock) {
            return (JWTRecipeStorage) getInstance(tenantIdentifier, main).storage;
        }
    }

    @TestOnly
    public static JWTRecipeStorage getJWTRecipeStorage(Main main) {
        try {
            return getJWTRecipeStorage(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static UserMetadataSQLStorage getUserMetadataStorage(TenantIdentifier tenantIdentifier,
                                                                Main main) throws TenantOrAppNotFoundException {
        synchronized (lock) {
            if (getInstance(tenantIdentifier, main).storage.getType() != STORAGE_TYPE.SQL) {
                // we only support SQL for now
                throw new UnsupportedOperationException("");
            }

            return (UserMetadataSQLStorage) getInstance(tenantIdentifier, main).storage;
        }
    }

    @TestOnly
    public static UserMetadataSQLStorage getUserMetadataStorage(Main main) {
        try {
            return getUserMetadataStorage(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static UserRolesSQLStorage getUserRolesStorage(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        synchronized (lock) {
            if (getInstance(tenantIdentifier, main).storage.getType() != STORAGE_TYPE.SQL) {
                // we only support SQL for now
                throw new UnsupportedOperationException("");
            }
            return (UserRolesSQLStorage) getInstance(tenantIdentifier, main).storage;
        }
    }

    @TestOnly
    public static UserRolesSQLStorage getUserRolesStorage(Main main) {
        try {
            return getUserRolesStorage(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static UserIdMappingStorage getUserIdMappingStorage(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        synchronized (lock) {
            return (UserIdMappingStorage) getInstance(tenantIdentifier, main).storage;
        }
    }

    @TestOnly
    public static UserIdMappingStorage getUserIdMappingStorage(Main main) {
        try {
            return getUserIdMappingStorage(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }


    // This function intentionally doesn't take connectionUriDomain and tenantId
    // cause the data for this is only going to be in the primary db of the core.
    public static MultitenancyStorage getMultitenancyStorage(Main main) {
        synchronized (lock) {
            try {
                return (MultitenancyStorage) getInstance(new TenantIdentifier(null, null, null), main).storage;
            } catch (TenantOrAppNotFoundException ignored) {
                throw new IllegalStateException("Should never come here");
            }
        }
    }

    public static boolean isInMemDb(Main main) {
        try {
            return getInstance(new TenantIdentifier(null, null, null), main).storage instanceof Start;
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    @TestOnly
    public static boolean hasMultipleUserPools(Main main) {
        List<ResourceDistributor.KeyClass> result = new ArrayList<ResourceDistributor.KeyClass>();
        String usedIds = "";

        Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> resources =
                main.getResourceDistributor()
                        .getAllResourcesWithResourceKey(RESOURCE_KEY);
        for (ResourceDistributor.KeyClass key : resources.keySet()) {
            Storage storage = ((StorageLayer) resources.get(key)).storage;
            if (usedIds.equals("")) {
                usedIds = storage.getUserPoolId();
            }
            if (usedIds.equals(storage.getUserPoolId())) {
                continue;
            }
            return true;
        }
        return false;
    }

    public static List<TenantIdentifier> getTenantsWithUniqueUserPoolId(Main main) {
        List<TenantIdentifier> result = new ArrayList<TenantIdentifier>();
        Set<String> usedIds = new HashSet<>();

        Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> resources =
                main.getResourceDistributor()
                        .getAllResourcesWithResourceKey(RESOURCE_KEY);
        for (ResourceDistributor.KeyClass key : resources.keySet()) {
            Storage storage = ((StorageLayer) resources.get(key)).storage;
            if (usedIds.contains(storage.getUserPoolId())) {
                continue;
            }
            usedIds.add(storage.getUserPoolId());
            result.add(key.getTenantIdentifier());
        }
        return result;
    }
}
