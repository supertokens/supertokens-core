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
import io.supertokens.*;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.config.Config;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.inmemorydb.Start;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.LOG_LEVEL;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.authRecipe.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.DbInitException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.SchemaMismatchException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.MultitenancyStorage;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.opentelemetry.WithinOtelSpan;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.telemetry.TelemetryProvider;
import io.supertokens.useridmapping.UserIdType;
import jakarta.servlet.ServletException;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;


public class StorageLayer extends ResourceDistributor.SingletonResource {

    public static final String RESOURCE_KEY = "io.supertokens.storageLayer.StorageLayer";
    private final Storage storage;
    private static URLClassLoader ucl = null;
    private static Storage storageInstanceForEnv = null;

    public Storage getUnderlyingStorage() {
        return storage;
    }

    public static Storage getNewStorageInstance(Main main, JsonObject config, TenantIdentifier tenantIdentifier, boolean doNotLog) throws InvalidConfigException {
        return getNewInstance(main, config, tenantIdentifier, doNotLog);
    }

    public static void updateConfigJsonFromEnv(Main main, JsonObject configJson) {
        if (storageInstanceForEnv == null) {
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
                if (storageLayer != null) {
                    result = storageLayer;
                } else {
                    result = new Start(main);
                }
            }
            storageInstanceForEnv = result;
        }

        storageInstanceForEnv.updateConfigJsonFromEnv(configJson);
    }

    @WithinOtelSpan
    private static Storage getNewInstance(Main main, JsonObject config, TenantIdentifier tenantIdentifier, boolean doNotLog) throws InvalidConfigException {
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
                    && (storageLayer. canBeUsed(config) || CLIOptions.get(main).isForceNoInMemoryDB())) {
                result = storageLayer;
            } else {
                result = new Start(main);
            }
        }
        result.constructor(main.getProcessId(), Main.makeConsolePrintSilent, Main.isTesting);

        Set<LOG_LEVEL> logLevels = null;
        if (doNotLog) {
            logLevels = new HashSet<>();
        } else {
            logLevels = Config.getBaseConfig(main).getLogLevels(main);
        }
        // this is intentionally null, null below cause log levels is per core and not per tenant anyway
        result.loadConfig(config, logLevels, tenantIdentifier);
        return result;
    }

    private StorageLayer(Storage storage) {
        this.storage = storage;
    }

    public static void loadStorageUCL(String pluginFolderPath) throws MalformedURLException {
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

    }

    private StorageLayer(Main main, JsonObject configJson, TenantIdentifier tenantIdentifier)
            throws InvalidConfigException {
        Logging.info(main, tenantIdentifier, "Loading storage layer.", true);

        this.storage = getNewStorageInstance(main, configJson, tenantIdentifier, false);

        if (this.storage instanceof Start) {
            Logging.info(main, TenantIdentifier.BASE_TENANT, "Using in memory storage.", true);
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
        Set<Storage> uniqueStorages = new HashSet<>();
        for (ResourceDistributor.SingletonResource resource : resources.values()) {
            uniqueStorages.add(((StorageLayer) resource).storage);
        }

        for (Storage storage : uniqueStorages) {
            storage.deleteAllInformation();
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

    public static void initPrimary(Main main, JsonObject configJson)
            throws MalformedURLException, InvalidConfigException {
        main.getResourceDistributor().setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                new StorageLayer(main, configJson, TenantIdentifier.BASE_TENANT));
    }

    public static void loadAllTenantStorage(Main main, TenantConfig[] tenants)
            throws InvalidConfigException, IOException {
        // We decided not to include tenantsThatChanged in this function because we do not want to reload the storage
        // when the db config has not change. And when db config has changed, it results in a
        // different userPoolId + connectionPoolId, which in turn results in a new storage instance

        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_STORAGE, null);

        Map<ResourceDistributor.KeyClass, JsonObject> normalisedConfigs = Config.getNormalisedConfigsForAllTenants(
                tenants,
                Config.getBaseConfigAsJsonObject(main));

        Map<ResourceDistributor.KeyClass, Storage> resourceKeyToStorageMap = new HashMap<>();
        {
            Map<String, Storage> idToStorageMap = new HashMap<>();
            for (ResourceDistributor.KeyClass key : normalisedConfigs.keySet()) {
                // setting doNotLog to true so that plugin loading is not logged here
                Storage storage = StorageLayer.getNewStorageInstance(main, normalisedConfigs.get(key),
                        key.getTenantIdentifier(), true);
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

        // Populated inside the lock (fast — no I/O), then consumed after the lock is released.
        Map<Storage, Set<TenantIdentifier>> storagesToInit = new HashMap<>();

        // now we loop through existing storage objects in the main resource distributor and reuse them
        // if the unique ID is the same as the storage objects created above.
        try {
            main.getResourceDistributor().withResourceDistributorLock(() -> {
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

                Set<String> uniquePoolsInUse = new HashSet<>();

                for (ResourceDistributor.KeyClass key : resourceKeyToStorageMap.keySet()) {
                    Storage currStorage = resourceKeyToStorageMap.get(key);
                    String userPoolId = currStorage.getUserPoolId();
                    String connectionPoolId = currStorage.getConnectionPoolId();
                    String uniqueId = userPoolId + "~" + connectionPoolId;
                    if (idToExistingStorageLayerMap.containsKey(uniqueId)) {
                        // we reuse the existing storage layer
                        resourceKeyToStorageMap.put(key, idToExistingStorageLayerMap.get(uniqueId).storage);
                    }

                    resourceKeyToStorageMap.get(key).setLogLevels(Config.getBaseConfig(main).getLogLevels(main));

                    main.getResourceDistributor().setResource(key.getTenantIdentifier(), RESOURCE_KEY,
                            new StorageLayer(resourceKeyToStorageMap.get(key)));

                    uniquePoolsInUse.add(uniqueId);
                }

                for (ResourceDistributor.KeyClass key : existingStorageMap.keySet()) {
                    Storage existingStorage = ((StorageLayer) existingStorageMap.get(key)).storage;
                    String userPoolId = existingStorage.getUserPoolId();
                    String connectionPoolId = existingStorage.getConnectionPoolId();
                    String uniqueId = userPoolId + "~" + connectionPoolId;

                    if (!uniquePoolsInUse.contains(uniqueId)) {
                        ((StorageLayer) existingStorageMap.get(key)).storage.close();
                        ((StorageLayer) existingStorageMap.get(key)).storage.stopLogging();
                    }
                }

                // Build storage → tenants map while holding the lock (no I/O).
                // initStorage() involves network connections and DDL; those run after
                // the lock is released via initStoragesInParallel().
                Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> resources =
                        main.getResourceDistributor()
                                .getAllResourcesWithResourceKey(RESOURCE_KEY);
                for (ResourceDistributor.KeyClass key : resources.keySet()) {
                    Storage s = ((StorageLayer) resources.get(key)).storage;
                    storagesToInit.computeIfAbsent(s, k -> new HashSet<>()).add(key.getTenantIdentifier());
                }

                return null;
            });


        } catch (ResourceDistributor.FuncException e) {
            throw new RuntimeException(e);
        }

        // Connect every pool concurrently now that the ResourceDistributor lock is released.
        initStoragesInParallel(main, storagesToInit);
    }

    /**
     * Loads StorageLayer resources only for the specified changed tenants, without clearing
     * and rebuilding all storage resources. Reuses existing storage instances when the
     * userPoolId + connectionPoolId match.
     */
    public static void loadStorageForChangedTenants(Main main, TenantConfig[] allTenants,
                                                     List<TenantIdentifier> tenantsThatChanged)
            throws InvalidConfigException, IOException {
        if (tenantsThatChanged == null || tenantsThatChanged.isEmpty()) {
            return;
        }

        JsonObject baseConfig = Config.getBaseConfigAsJsonObject(main);
        Map<Storage, Set<TenantIdentifier>> storagesToInit = new HashMap<>();

        try {
            main.getResourceDistributor().withResourceDistributorLock(() -> {
                // Build existing pool ID → storage mapping from current resources
                Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> existingStorageMap =
                        main.getResourceDistributor().getAllResourcesWithResourceKey(RESOURCE_KEY);
                Map<String, Storage> existingPoolToStorage = new HashMap<>();
                for (ResourceDistributor.SingletonResource resource : existingStorageMap.values()) {
                    Storage s = ((StorageLayer) resource).storage;
                    String uniqueId = s.getUserPoolId() + "~" + s.getConnectionPoolId();
                    existingPoolToStorage.putIfAbsent(uniqueId, s);
                }

                for (TenantIdentifier changed : tenantsThatChanged) {
                    try {
                        JsonObject normConfig = Config.getNormalisedConfigForTenant(
                                changed, allTenants, baseConfig);
                        Storage newStorage = getNewStorageInstance(main, normConfig, changed, true);
                        String userPoolId = newStorage.getUserPoolId();
                        String connectionPoolId = newStorage.getConnectionPoolId();
                        String uniqueId = userPoolId + "~" + connectionPoolId;

                        // Reuse existing storage if same pool exists
                        Storage storageToUse;
                        boolean isNewPool;
                        if (existingPoolToStorage.containsKey(uniqueId)) {
                            storageToUse = existingPoolToStorage.get(uniqueId);
                            isNewPool = false;
                        } else {
                            storageToUse = newStorage;
                            isNewPool = true;
                        }

                        storageToUse.setLogLevels(Config.getBaseConfig(main).getLogLevels(main));

                        // Remove old resource for this tenant (if any), then set new
                        main.getResourceDistributor().removeResource(changed, RESOURCE_KEY);
                        main.getResourceDistributor().setResource(changed, RESOURCE_KEY,
                                new StorageLayer(storageToUse));

                        // Defer initStorage() to after the lock — it involves TCP + DDL.
                        if (isNewPool) {
                            storagesToInit.computeIfAbsent(storageToUse, k -> new HashSet<>()).add(changed);
                            existingPoolToStorage.put(uniqueId, storageToUse);
                        }
                    } catch (Exception e) {
                        Logging.error(main, TenantIdentifier.BASE_TENANT, e.getMessage(), false, e);
                    }
                }
                return null;
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new RuntimeException(e);
        }

        initStoragesInParallel(main, storagesToInit);
    }

    public static Storage getBaseStorage(Main main) {
        try {
            return getInstance(new TenantIdentifier(null, null, null), main).storage;
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Storage getStorage(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        return getInstance(tenantIdentifier, main).storage;
    }

    @TestOnly
    public static Storage getStorage(Main main) {
        try {
            return getStorage(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // This function intentionally doesn't take connectionUriDomain and tenantId
    // cause the data for this is only going to be in the primary db of the core.
    public static MultitenancyStorage getMultitenancyStorage(Main main) {
        try {
            return (MultitenancyStorage) getInstance(new TenantIdentifier(null, null, null), main).storage;
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isInMemDb(Main main) {
        try {
            return getInstance(new TenantIdentifier(null, null, null), main).storage instanceof Start;
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
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

    public static List<List<TenantIdentifier>> getTenantsWithUniqueUserPoolId(Main main) {
        List<List<TenantIdentifier>> result = new ArrayList<>();
        Map<String, List<TenantIdentifier>> uniquePoolList = new HashMap<>();

        Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> resources =
                main.getResourceDistributor()
                        .getAllResourcesWithResourceKey(RESOURCE_KEY);
        for (ResourceDistributor.KeyClass key : resources.keySet()) {
            Storage storage = ((StorageLayer) resources.get(key)).storage;
            if (uniquePoolList.get(storage.getUserPoolId()) != null) {
                uniquePoolList.get(storage.getUserPoolId()).add(key.getTenantIdentifier());
            } else {
                uniquePoolList.put(storage.getUserPoolId(), new ArrayList<>());
                uniquePoolList.get(storage.getUserPoolId()).add(key.getTenantIdentifier());
            }
        }
        for (String s : uniquePoolList.keySet()) {
            result.add(uniquePoolList.get(s));
        }
        return result;
    }

    public static Storage[] getStoragesForApp(Main main, AppIdentifier appIdentifier)
            throws TenantOrAppNotFoundException {
        Map<String, Storage> userPoolToStorage = new HashMap<>();

        Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> resources =
                main.getResourceDistributor()
                        .getAllResourcesWithResourceKey(RESOURCE_KEY);
        for (ResourceDistributor.KeyClass key : resources.keySet()) {
            Storage storage = ((StorageLayer) resources.get(key)).storage;
            if (key.getTenantIdentifier().toAppIdentifier().equals(appIdentifier)) {
                userPoolToStorage.put(storage.getUserPoolId(), storage);
            }
        }
        Storage[] storages = userPoolToStorage.values().toArray(new Storage[0]);
        if (storages.length == 0) {
            throw new TenantOrAppNotFoundException(appIdentifier);
        }
        return storages;
    }

    public static StorageAndUserIdMapping findStorageAndUserIdMappingForUser(
            Main main, TenantIdentifier tenantIdentifier, String userId, UserIdType userIdType)
            throws StorageQueryException, TenantOrAppNotFoundException, UnknownUserIdException {
        Storage storage = getStorage(tenantIdentifier, main);


        if (userIdType == UserIdType.SUPERTOKENS) {
            if (((AuthRecipeStorage) storage).doesUserIdExist(tenantIdentifier.toAppIdentifier(), userId)) {
                UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                        tenantIdentifier.toAppIdentifier(), storage, userId, userIdType);

                return new StorageAndUserIdMapping(storage, mapping);
            }

        } else if (userIdType == UserIdType.EXTERNAL) {
            UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                    tenantIdentifier.toAppIdentifier(), storage,
                    userId, userIdType);
            if (mapping != null) {
                return new StorageAndUserIdMapping(storage, mapping);
            }
        } else if (userIdType == UserIdType.ANY) {
            if (((AuthRecipeStorage) storage).doesUserIdExist(tenantIdentifier.toAppIdentifier(), userId)) {
                UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                        tenantIdentifier.toAppIdentifier(), storage, userId, userIdType);

                return new StorageAndUserIdMapping(storage, mapping);
            }

            UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                    tenantIdentifier.toAppIdentifier(), storage,
                    userId, userIdType);
            if (mapping != null) {
                return new StorageAndUserIdMapping(storage, mapping);
            }

            try {
                io.supertokens.useridmapping.UserIdMapping.findNonAuthStoragesWhereUserIdIsUsedOrAssertIfUsed(
                        tenantIdentifier.toAppIdentifier(), storage, userId, true);
            } catch (ServletException e) {
                // this means that the userId is being used for a non auth recipe.
                return new StorageAndUserIdMapping(
                        storage, null);
            }

        } else {
            throw new IllegalStateException("should never come here");
        }

        throw new UnknownUserIdException();
    }

    public static StorageAndUserIdMapping findStorageAndUserIdMappingForUser(
            AppIdentifier appIdentifier, Storage[] storages, String userId,
            UserIdType userIdType) throws StorageQueryException, UnknownUserIdException {

        if (storages.length == 0) {
            throw new IllegalStateException("should never come here");
        }

        if (storages[0].getType() != STORAGE_TYPE.SQL) {
            // for non sql plugin, there will be only one storage as multitenancy is not supported
            assert storages.length == 1;
            return new StorageAndUserIdMapping(storages[0], null);
        }

        if (userIdType == UserIdType.SUPERTOKENS) {
            for (Storage storage : storages) {
                if (((AuthRecipeStorage) storage).doesUserIdExist(appIdentifier, userId)) {
                    UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                            appIdentifier, storage,
                            userId, userIdType);

                    return new StorageAndUserIdMapping(storage, mapping);
                }
            }

            // Not found in any of the storages
            throw new UnknownUserIdException();

        } else if (userIdType == UserIdType.EXTERNAL) {
            for (Storage storage : storages) {
                UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                        appIdentifier, storage,
                        userId, userIdType);

                if (mapping != null) {
                    return new StorageAndUserIdMapping(storage, mapping);
                }
            }

            throw new UnknownUserIdException();
        } else if (userIdType == UserIdType.ANY) {

            // look for the user in auth recipes as supertokens user id
            for (Storage storage : storages) {
                if (((AuthRecipeStorage) storage).doesUserIdExist(appIdentifier, userId)) {
                    UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                            appIdentifier, storage,
                            userId, userIdType);

                    return new StorageAndUserIdMapping(storage, mapping);
                }
            }

            // Look for user in auth recipes using user id mapping
            for (Storage storage : storages) {
                UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                        appIdentifier, storage,
                        userId, userIdType);

                if (mapping != null) {
                    return new StorageAndUserIdMapping(storage, mapping);
                }
            }

            // Look for non auth recipes
            for (Storage storage : storages) {
                try {
                    io.supertokens.useridmapping.UserIdMapping.findNonAuthStoragesWhereUserIdIsUsedOrAssertIfUsed(
                            appIdentifier, storage, userId, true);
                } catch (ServletException e) {
                    // this means that the userId is being used for a non auth recipe.
                    return new StorageAndUserIdMapping(storage, null);
                }
            }

            throw new UnknownUserIdException();
        } else {
            throw new IllegalStateException("should never come here");
        }
    }

    public static List<StorageAndUserIdMapping> findStorageAndUserIdMappingForBulkUserImport(
            AppIdentifier appIdentifier, Storage[] storages, List<String> userIds,
            UserIdType userIdType) throws StorageQueryException {

        if (storages.length == 0) {
            throw new IllegalStateException("No storages were provided!");
        }

        if (storages[0].getType() != STORAGE_TYPE.SQL) {
            // for non sql plugin, there will be only one storage as multitenancy is not supported
            assert storages.length == 1;
            List<StorageAndUserIdMapping> results = new ArrayList<>();
            for(String userId : userIds) {
                results.add(new StorageAndUserIdMapping(storages[0], new UserIdMapping(userId, null, null)));
            }
            return results;
        }
        List<StorageAndUserIdMapping> allMappingsFromAllStorages = new ArrayList<>();
        if (userIdType != UserIdType.ANY) {
            for (Storage storage : storages) {
                List<UserIdMapping> mappingsFromThisStorage = io.supertokens.useridmapping.UserIdMapping.getMultipleUserIdMapping(
                        appIdentifier, storage,
                        userIds, userIdType);

                if (userIdType == UserIdType.EXTERNAL) {
                    for (UserIdMapping mapping : mappingsFromThisStorage) {
                        allMappingsFromAllStorages.add(new StorageAndUserIdMappingForBulkImport(storage, mapping,
                                mapping.externalUserId));
                    }
                    continue;
                }

                List<String> existingIdsInStorage = ((AuthRecipeStorage) storage).findExistingUserIds(appIdentifier,
                        userIds);
                for(String existingId : existingIdsInStorage) {
                    UserIdMapping mappingForId = mappingsFromThisStorage.stream()
                                .filter(userIdMapping -> userIdMapping.superTokensUserId.equals(existingId))
                                .findFirst().orElse(null);
                    allMappingsFromAllStorages.add(new StorageAndUserIdMappingForBulkImport(storage, mappingForId, existingId));
                }
            }
        } else {
            throw new IllegalStateException("UserIdType.ANY is not supported for this method");
        }
        return allMappingsFromAllStorages;
    }

    /**
     * Upper bound on the number of storages {@link #initStoragesInParallel} initialises at the same time. Boot
     * has tens of short, blocking tasks (TCP connect + DDL), so a small pool is all the parallelism that pays
     * off, and the bound also caps the connection burst sent at a database many tenants share.
     */
    static final int STORAGE_INIT_MAX_PARALLELISM = 16;

    /** How often (seconds) the storages still initialising are reported while boot waits for them. */
    static final long STORAGE_INIT_PROGRESS_LOG_INTERVAL_SECONDS = 60;

    /**
     * How long (seconds) boot waits for every storage to initialise before continuing without the stragglers.
     * Generous on purpose: a legitimate first-boot migration on a large table can take minutes. The bound exists
     * to turn a silent hang into a diagnosable log line, not to race migrations.
     */
    static final long STORAGE_INIT_TIMEOUT_SECONDS = 600;

    /**
     * The executor {@link #initStoragesInParallel} runs on: a fixed pool of daemon platform threads.
     *
     * <p>Platform threads, deliberately. This used to be a virtual-thread-per-task executor, and on JDK 21 a
     * virtual thread that parks inside a {@code synchronized} section is pinned to its carrier. The plugins'
     * pool initialisation was one such section, and every Hikari DEBUG line was logged from inside it. With a
     * carrier pool sized to the CPU count (2 on a small container) and dozens of storages contending for the
     * shared console-appender and {@code System.out} locks, every carrier ended up occupied by a pinned
     * waiter, the lock owner could never be scheduled to release it, and boot hung forever. A pool of platform
     * threads cannot be wedged that way, whatever the plugin or its libraries lock on. Daemon, so a straggler
     * left behind by {@link #awaitStorageInit} never keeps the JVM alive.
     */
    static ExecutorService newStorageInitExecutor(int storageCount) {
        AtomicInteger threadNumber = new AtomicInteger();
        return Executors.newFixedThreadPool(Math.max(1, Math.min(storageCount, STORAGE_INIT_MAX_PARALLELISM)),
                runnable -> {
                    Thread thread = new Thread(runnable, "storage-init-" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /**
     * Waits for the storage-init futures without ever waiting forever. While some are still running, the
     * tenants they serve are logged every {@code progressIntervalSeconds}; after {@code timeoutSeconds} the
     * stragglers are reported as an error, {@link ProcessState.PROCESS_STATE#STORAGE_INIT_TIMED_OUT} is
     * recorded and the method returns so boot can continue without them. Stragglers are neither cancelled nor
     * interrupted (one may be mid-DDL): one that eventually finishes is fully usable, and until then queries
     * against it wait in the plugin's own pool-initialisation guard rather than failing.
     *
     * <p>Continuing without the stragglers can never leave the core without a working base tenant: the base
     * tenant's storage is initialised (and schema-verified) <em>synchronously</em> in {@code Main.init}
     * (via {@code StorageLayer.getBaseStorage(main).initStorage(...)}) before {@code loadStorageLayer} ever
     * reaches this parallel path, and a base-storage failure crashes startup there rather than reaching here.
     * Only secondary tenant storages can time out on this path, so the base tenant and every tenant that did
     * finish stay fully functional.
     *
     * <p>An unexpected exception from a task is rethrown as a {@link CompletionException}, exactly as the
     * previous {@code CompletableFuture.join()} did, so such a failure still crashes startup.
     *
     * @param tenantsPerFuture the tenants of the storage behind the future at the same index, for the reports
     * @return {@code true} if every future finished, {@code false} if the timeout was hit
     */
    static boolean awaitStorageInit(Main main, List<CompletableFuture<Void>> futures,
                                    List<List<TenantIdentifier>> tenantsPerFuture, long progressIntervalSeconds,
                                    long timeoutSeconds) {
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (true) {
            try {
                all.get(progressIntervalSeconds, TimeUnit.SECONDS);
                return true;
            } catch (TimeoutException e) {
                List<List<TenantIdentifier>> stillInitialising = new ArrayList<>();
                for (int i = 0; i < futures.size(); i++) {
                    if (!futures.get(i).isDone()) {
                        stillInitialising.add(tenantsPerFuture.get(i));
                    }
                }
                if (System.nanoTime() < deadlineNanos) {
                    Logging.warn(main, TenantIdentifier.BASE_TENANT,
                            "Still waiting for " + stillInitialising.size() + " storage(s) to initialise (tenants: "
                                    + stillInitialising + ")");
                } else {
                    Logging.error(main, TenantIdentifier.BASE_TENANT,
                            "Storage initialisation did not finish within " + timeoutSeconds + "s for "
                                    + stillInitialising.size() + " storage(s) (tenants: " + stillInitialising
                                    + "). Continuing startup without them; they keep initialising in the "
                                    + "background and queries against them wait until their pool is up. Take a "
                                    + "thread dump to see what they are blocked on.", true);
                    ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.STORAGE_INIT_TIMED_OUT, null);
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new CompletionException(e.getCause());
            }
        }
    }

    /**
     * Initializes each storage's connection pool concurrently on the bounded pool of platform threads from
     * {@link #newStorageInitExecutor} (see there for why not virtual threads), waiting with
     * {@link #awaitStorageInit} so that a storage that never finishes cannot hang boot.
     *
     * <p>This method MUST be called outside any ResourceDistributor lock. {@code initStorage()}
     * opens TCP connections to the database and runs DDL (CREATE TABLE IF NOT EXISTS). That
     * work can take tens of milliseconds per pool; holding the global lock during I/O would
     * serialize all N pools and turn startup into O(N × latency) instead of O(latency).
     *
     * <p>The map is deduplicated by {@link Storage} instance, so each pool is initialized
     * exactly once even when multiple tenants share a pool. We are therefore not actually
     * racing concurrent {@code initStorage()} calls against each other on a single instance —
     * the per-instance {@code isAlreadyInitialised} guard inside {@code initStorage()} is
     * belt-and-braces, not load-bearing here.
     *
     * <p>Per-pool failures are caught and logged: a non-base-tenant pool failure must not
     * prevent other tenants from working, matching the original sequential behaviour.
     * {@code DbInitException} is the only checked exception either method declares;
     * {@code initFileLogging} declares none. Anything else (e.g. a {@link RuntimeException}
     * from log file setup) is propagated by {@code awaitStorageInit} as a
     * {@code CompletionException} and crashes startup — same as the original sequential
     * code, where such an exception would have escaped the for-loop unhandled.
     */
    private static void initStoragesInParallel(Main main, Map<Storage, Set<TenantIdentifier>> storagesToInit) {
        if (storagesToInit.isEmpty()) {
            return;
        }
        String infoLogPath = Config.getBaseConfig(main).getInfoLogPath(main);
        String errorLogPath = Config.getBaseConfig(main).getErrorLogPath(main);
        TelemetryProvider telemetry = TelemetryProvider.getInstance(main);

        ExecutorService executor = newStorageInitExecutor(storagesToInit.size());
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            List<List<TenantIdentifier>> tenantsPerFuture = new ArrayList<>();
            for (Map.Entry<Storage, Set<TenantIdentifier>> entry : storagesToInit.entrySet()) {
                Storage storage = entry.getKey();
                List<TenantIdentifier> tenants = new ArrayList<>(entry.getValue());
                tenantsPerFuture.add(tenants);
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        storage.initStorage(false, tenants);
                        storage.initFileLogging(infoLogPath, errorLogPath, telemetry);
                    } catch (DbInitException e) {
                        Logging.error(main, TenantIdentifier.BASE_TENANT, e.getMessage(), false, e);
                        return;
                    }
                    // Schema verification runs once per storage (the plugin caches a success), so this is a
                    // no-op for already-verified pools on refresh. A tenant mismatch never takes the core down:
                    // in strict mode (schema_check_strict_mode, the default) the storage refuses all queries
                    // until a re-verification passes (retrySchemaVerification runs one every minute, so the
                    // storage resumes within a minute of the migration being applied); in non-strict mode it
                    // stays fully in use and only queries touching the missing schema fail (with a hint).
                    try {
                        storage.verifySchema(Config.getBaseConfig(main).getSchemaCheckStrictMode());
                    } catch (SchemaMismatchException e) {
                        Logging.error(main, TenantIdentifier.BASE_TENANT,
                                "Schema verification failed for storage of tenants " + tenants + ": "
                                        + e.getMessage(), true, e);
                        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.SCHEMA_MISMATCH, e);
                    } catch (StorageQueryException e) {
                        Logging.error(main, TenantIdentifier.BASE_TENANT,
                                "Could not verify schema for storage of tenants " + tenants, false, e);
                    }
                }, executor));
            }
            awaitStorageInit(main, futures, tenantsPerFuture, STORAGE_INIT_PROGRESS_LOG_INTERVAL_SECONDS,
                    STORAGE_INIT_TIMEOUT_SECONDS);
        } finally {
            // no shutdownNow(): a straggler may be mid-DDL and must not be interrupted. The threads are daemon, so
            // they never keep the JVM alive; the pool simply exits once its queue drains.
            executor.shutdown();
        }
    }

    /**
     * Gives every loaded storage whose schema verification has not yet passed another chance to pass. Called
     * from {@link io.supertokens.cronjobs.syncCoreConfigWithDb.SyncCoreConfigWithDb} every minute, so a tenant
     * storage that is refusing queries in strict mode (schema_check_strict_mode) resumes within a minute of
     * the operator applying the migration SQL - no restart or tenant change needed. A storage that already
     * verified returns from {@link Storage#verifySchema} without touching the database, so this is free in the
     * steady state; a still-mismatched storage costs one schema-inspection query per run and stays quiet here,
     * because the startup ERROR log already carries the full report.
     */
    public static void retrySchemaVerification(Main main) {
        Set<Storage> storages = new HashSet<>();
        for (ResourceDistributor.SingletonResource resource : main.getResourceDistributor()
                .getAllResourcesWithResourceKey(RESOURCE_KEY).values()) {
            storages.add(((StorageLayer) resource).storage);
        }
        boolean strictMode = Config.getBaseConfig(main).getSchemaCheckStrictMode();
        for (Storage storage : storages) {
            try {
                storage.verifySchema(strictMode);
            } catch (SchemaMismatchException e) {
                // still mismatched: the startup ERROR already reported the details, and affected queries
                // carry a hint pointing at it - do not repeat the report every minute
            } catch (StorageQueryException e) {
                Logging.debug(main, TenantIdentifier.BASE_TENANT,
                        "Could not re-verify the database schema: " + e.getMessage());
            }
        }
    }

}
