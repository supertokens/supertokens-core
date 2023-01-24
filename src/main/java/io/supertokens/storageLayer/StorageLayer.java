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
import io.supertokens.inmemorydb.Start;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.jwt.JWTRecipeStorage;
import io.supertokens.pluginInterface.multitenancy.MultitenancyStorage;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
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
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public class StorageLayer extends ResourceDistributor.SingletonResource {

    public static final String RESOURCE_KEY = "io.supertokens.storageLayer.StorageLayer";
    private final Storage storage;
    private static Storage static_ref_to_storage = null;
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
        result.loadConfig(config, Config.getConfig(null, null, main).getLogLevels(main));
        return result;
    }

    private StorageLayer(Storage storage) {
        this.storage = storage;
    }

    private StorageLayer(Main main, String pluginFolderPath, JsonObject configJson)
            throws MalformedURLException, InvalidConfigException {
        Logging.info(main, "Loading storage layer.", true);
        if (static_ref_to_storage != null && Main.isTesting) {
            // we reuse the storage layer during testing so that we do not waste
            // time reconnecting to the db.
            this.storage = StorageLayer.static_ref_to_storage;
        } else {
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
        if (Main.isTesting && !(this.storage instanceof Start)) {
            // we save the storage layer for testing (if it's not an in mem db) purposes so that
            // next time, we can just reuse this.
            // StorageLayer.static_ref_to_storage is set to null by the testing framework in case
            // something in the config or CLI args change.
            StorageLayer.static_ref_to_storage = this.storage;
        }
    }

    public static void close(Main main) {
        if (getInstance(null, null, main) == null) {
            return;
        }
        Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> resources =
                main.getResourceDistributor()
                        .getAllResourcesWithResourceKey(RESOURCE_KEY);
        for (ResourceDistributor.SingletonResource resource : resources.values()) {
            ((StorageLayer) resource).storage.close();
        }
        StorageLayer.static_ref_to_storage = null;
    }

    @TestOnly
    public static void deleteAllInformation(Main main) throws StorageQueryException {
        if (getInstance(null, null, main) == null) {
            return;
        }
        Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> resources =
                main.getResourceDistributor()
                        .getAllResourcesWithResourceKey(RESOURCE_KEY);
        for (ResourceDistributor.SingletonResource resource : resources.values()) {
            ((StorageLayer) resource).storage.deleteAllInformation();
        }
    }

    @TestOnly
    public static void close() {
        if (StorageLayer.static_ref_to_storage != null) {
            StorageLayer.static_ref_to_storage.close();
        }
        StorageLayer.static_ref_to_storage = null;
    }

    @TestOnly
    public static void closeWithClearingURLClassLoader() {
        /*
         * This is needed for PluginTests where we want to try and load from the plugin directory
         * again and again. If we do not close the static URLCLassLoader before, those tests will fail
         *
         * Also note that closing it doesn't actually remove it from memory (strange..). But we do it anyway
         */
        StorageLayer.close();
        if (StorageLayer.ucl != null) {
            try {
                StorageLayer.ucl.close();
            } catch (IOException ignored) {
            }
            StorageLayer.ucl = null;
        }
    }

    private static StorageLayer getInstance(String connectionUriDomain, String tenantId, Main main) {
        return (StorageLayer) main.getResourceDistributor().getResource(connectionUriDomain, tenantId, RESOURCE_KEY);
    }

    public static void initPrimary(Main main, String pluginFolderPath, JsonObject configJson)
            throws MalformedURLException, InvalidConfigException {
        synchronized (lock) {
            main.getResourceDistributor().setResource(null, null, RESOURCE_KEY,
                    new StorageLayer(main, pluginFolderPath, configJson));
        }
    }

    public static void loadAllTenantStorage(Main main, TenantConfig[] tenants)
            throws InvalidConfigException, IOException {
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
                main.getResourceDistributor().setResource(RESOURCE_KEY, key,
                        new StorageLayer(resourceKeyToStorageMap.get(key)));
            }
        }
    }

    public static void loadAllTenantStorage(Main main)
            throws IOException, InvalidConfigException {
        TenantConfig[] tenants = StorageLayer.getMultitenancyStorage(main).getAllTenants();
        loadAllTenantStorage(main, tenants);
    }

    public static Storage getStorage(String connectionUriDomain, String tenantId, Main main) {
        synchronized (lock) {
            if (getInstance(connectionUriDomain, tenantId, main) == null) {
                throw new QuitProgramException("please call init() before calling getStorageLayer");
            }
            return getInstance(connectionUriDomain, tenantId, main).storage;
        }
    }

    @Deprecated
    public static Storage getStorage(Main main) {
        return getStorage(null, null, main);
    }

    public static AuthRecipeStorage getAuthRecipeStorage(String connectionUriDomain, String tenantId, Main main) {
        synchronized (lock) {
            if (getInstance(connectionUriDomain, tenantId, main) == null) {
                throw new QuitProgramException("please call init() before calling getStorageLayer");
            }
            return (AuthRecipeStorage) getInstance(connectionUriDomain, tenantId, main).storage;
        }
    }

    @Deprecated
    public static AuthRecipeStorage getAuthRecipeStorage(Main main) {
        return getAuthRecipeStorage(null, null, main);
    }

    public static SessionStorage getSessionStorage(String connectionUriDomain, String tenantId, Main main) {
        synchronized (lock) {
            if (getInstance(connectionUriDomain, tenantId, main) == null) {
                throw new QuitProgramException("please call init() before calling getStorageLayer");
            }
            return (SessionStorage) getInstance(connectionUriDomain, tenantId, main).storage;
        }
    }

    @Deprecated
    public static SessionStorage getSessionStorage(Main main) {
        return getSessionStorage(null, null, main);
    }

    public static EmailPasswordSQLStorage getEmailPasswordStorage(String connectionUriDomain, String tenantId,
                                                                  Main main) {
        synchronized (lock) {
            if (getInstance(connectionUriDomain, tenantId, main) == null) {
                throw new QuitProgramException("please call init() before calling getStorageLayer");
            }
            if (getInstance(connectionUriDomain, tenantId, main).storage.getType() != STORAGE_TYPE.SQL) {
                // we only support SQL for now
                throw new UnsupportedOperationException("");
            }
            return (EmailPasswordSQLStorage) getInstance(connectionUriDomain, tenantId, main).storage;
        }
    }

    @Deprecated
    public static EmailPasswordSQLStorage getEmailPasswordStorage(Main main) {
        return getEmailPasswordStorage(null, null, main);
    }

    public static EmailVerificationSQLStorage getEmailVerificationStorage(String connectionUriDomain, String tenantId,
                                                                          Main main) {
        synchronized (lock) {
            if (getInstance(connectionUriDomain, tenantId, main) == null) {
                throw new QuitProgramException("please call init() before calling getStorageLayer");
            }
            if (getInstance(connectionUriDomain, tenantId, main).storage.getType() != STORAGE_TYPE.SQL) {
                // we only support SQL for now
                throw new UnsupportedOperationException("");
            }
            return (EmailVerificationSQLStorage) getInstance(connectionUriDomain, tenantId, main).storage;
        }
    }

    @Deprecated
    public static EmailVerificationSQLStorage getEmailVerificationStorage(Main main) {
        return getEmailVerificationStorage(null, null, main);
    }

    public static ThirdPartySQLStorage getThirdPartyStorage(String connectionUriDomain, String tenantId, Main main) {
        synchronized (lock) {
            if (getInstance(connectionUriDomain, tenantId, main) == null) {
                throw new QuitProgramException("please call init() before calling getStorageLayer");
            }
            if (getInstance(connectionUriDomain, tenantId, main).storage.getType() != STORAGE_TYPE.SQL) {
                // we only support SQL for now
                throw new UnsupportedOperationException("");
            }
            return (ThirdPartySQLStorage) getInstance(connectionUriDomain, tenantId, main).storage;
        }
    }

    @Deprecated
    public static ThirdPartySQLStorage getThirdPartyStorage(Main main) {
        return getThirdPartyStorage(null, null, main);
    }

    public static PasswordlessSQLStorage getPasswordlessStorage(String connectionUriDomain, String tenantId,
                                                                Main main) {
        synchronized (lock) {
            if (getInstance(connectionUriDomain, tenantId, main) == null) {
                throw new QuitProgramException("please call init() before calling getStorageLayer");
            }
            if (getInstance(connectionUriDomain, tenantId, main).storage.getType() != STORAGE_TYPE.SQL) {
                // we only support SQL for now
                throw new UnsupportedOperationException("");
            }
            return (PasswordlessSQLStorage) getInstance(connectionUriDomain, tenantId, main).storage;
        }
    }

    @Deprecated
    public static PasswordlessSQLStorage getPasswordlessStorage(Main main) {
        return getPasswordlessStorage(null, null, main);
    }

    public static JWTRecipeStorage getJWTRecipeStorage(String connectionUriDomain, String tenantId, Main main) {
        synchronized (lock) {
            if (getInstance(connectionUriDomain, tenantId, main) == null) {
                throw new QuitProgramException("please call init() before calling getStorageLayer");
            }

            return (JWTRecipeStorage) getInstance(connectionUriDomain, tenantId, main).storage;
        }
    }

    @Deprecated
    public static JWTRecipeStorage getJWTRecipeStorage(Main main) {
        return getJWTRecipeStorage(null, null, main);
    }

    public static UserMetadataSQLStorage getUserMetadataStorage(String connectionUriDomain, String tenantId,
                                                                Main main) {
        synchronized (lock) {
            if (getInstance(connectionUriDomain, tenantId, main) == null) {
                throw new QuitProgramException("please call init() before calling getStorageLayer");
            }

            if (getInstance(connectionUriDomain, tenantId, main).storage.getType() != STORAGE_TYPE.SQL) {
                // we only support SQL for now
                throw new UnsupportedOperationException("");
            }

            return (UserMetadataSQLStorage) getInstance(connectionUriDomain, tenantId, main).storage;
        }
    }

    @Deprecated
    public static UserMetadataSQLStorage getUserMetadataStorage(Main main) {
        return getUserMetadataStorage(null, null, main);
    }

    public static UserRolesSQLStorage getUserRolesStorage(String connectionUriDomain, String tenantId, Main main) {
        synchronized (lock) {
            if (getInstance(connectionUriDomain, tenantId, main) == null) {
                throw new QuitProgramException("please call init() before calling getStorageLayer");
            }

            if (getInstance(connectionUriDomain, tenantId, main).storage.getType() != STORAGE_TYPE.SQL) {
                // we only support SQL for now
                throw new UnsupportedOperationException("");
            }
            return (UserRolesSQLStorage) getInstance(connectionUriDomain, tenantId, main).storage;
        }
    }

    @Deprecated
    public static UserRolesSQLStorage getUserRolesStorage(Main main) {
        return getUserRolesStorage(null, null, main);
    }

    public static UserIdMappingStorage getUserIdMappingStorage(String connectionUriDomain, String tenantId, Main main) {
        synchronized (lock) {
            if (getInstance(connectionUriDomain, tenantId, main) == null) {
                throw new QuitProgramException("please call init() before calling getStorageLayer");
            }

            return (UserIdMappingStorage) getInstance(connectionUriDomain, tenantId, main).storage;
        }
    }

    @Deprecated
    public static UserIdMappingStorage getUserIdMappingStorage(Main main) {
        return getUserIdMappingStorage(null, null, main);
    }


    // This function intentionally doesn't take connectionUriDomain and tenantId
    // cause the data for this is only going to be in the primary db of the core.
    public static MultitenancyStorage getMultitenancyStorage(Main main) {
        synchronized (lock) {
            if (getInstance(null, null, main) == null) {
                throw new QuitProgramException("please call init() before calling getStorageLayer");
            }

            return (MultitenancyStorage) getInstance(null, null, main).storage;
        }
    }

    public static boolean isInMemDb(Main main) {
        return getInstance(null, null, main).storage instanceof Start;
    }
}
