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

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.config.Config;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.inmemorydb.Start;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.dashboard.DashboardStorage;
import io.supertokens.pluginInterface.dashboard.sqlStorage.DashboardSQLStorage;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.pluginInterface.jwt.JWTRecipeStorage;
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
import java.util.Iterator;
import java.util.ServiceLoader;

public class StorageLayer extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.storageLayer.StorageLayer";
    private final Storage storage;
    private static Storage static_ref_to_storage = null;
    private static URLClassLoader ucl = null;

    private StorageLayer(Main main, String pluginFolderPath, String configFilePath) throws MalformedURLException {
        Logging.info(main, "Loading storage layer.", true);
        if (static_ref_to_storage != null && Main.isTesting) {
            // we reuse the storage layer during testing so that we do not waste
            // time reconnecting to the db.
            this.storage = StorageLayer.static_ref_to_storage;
        } else {
            File loc = new File(pluginFolderPath);
            Storage storageLayerTemp = null;

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

                ServiceLoader<Storage> sl = ServiceLoader.load(Storage.class, ucl);
                Iterator<Storage> it = sl.iterator();
                while (it.hasNext()) {
                    Storage plugin = it.next();
                    if (storageLayerTemp == null) {
                        storageLayerTemp = plugin;
                    } else {
                        throw new QuitProgramException(
                                "Multiple database plugins found. Please make sure that just one plugin is in the "
                                        + "/plugin" + " "
                                        + "folder of the installation. Alternatively, please redownload and install "
                                        + "SuperTokens" + ".");
                    }
                }
            }

            if (storageLayerTemp != null && !main.isForceInMemoryDB()
                    && (storageLayerTemp.canBeUsed(configFilePath) || CLIOptions.get(main).isForceNoInMemoryDB())) {
                this.storage = storageLayerTemp;
            } else {
                Logging.info(main, "Using in memory storage.", true);
                this.storage = new Start(main);
            }
        }
        this.storage.constructor(main.getProcessId(), Main.makeConsolePrintSilent);
        this.storage.loadConfig(configFilePath, Config.getConfig(main).getLogLevels(main));
        if (Main.isTesting && !(this.storage instanceof Start)) {
            // we save the storage layer for testing (if it's not an in mem db) purposes so that
            // next time, we can just reuse this.
            // StorageLayer.static_ref_to_storage is set to null by the testing framework in case
            // something in the config or CLI args change.
            StorageLayer.static_ref_to_storage = this.storage;
        }
    }

    public static void close(Main main) {
        if (getInstance(main) == null) {
            return;
        }
        getInstance(main).storage.close();
        StorageLayer.static_ref_to_storage = null;
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

    public static StorageLayer getInstance(Main main) {
        return (StorageLayer) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    public static void init(Main main, String pluginFolderPath, String configFilePath) throws MalformedURLException {
        if (getInstance(main) != null) {
            return;
        }
        main.getResourceDistributor().setResource(RESOURCE_KEY,
                new StorageLayer(main, pluginFolderPath, configFilePath));
    }

    public static Storage getStorage(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("please call init() before calling getStorageLayer");
        }
        return getInstance(main).storage;
    }

    public static AuthRecipeStorage getAuthRecipeStorage(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("please call init() before calling getStorageLayer");
        }
        return (AuthRecipeStorage) getInstance(main).storage;
    }

    public static SessionStorage getSessionStorage(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("please call init() before calling getStorageLayer");
        }
        return (SessionStorage) getInstance(main).storage;
    }

    public static EmailPasswordSQLStorage getEmailPasswordStorage(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("please call init() before calling getStorageLayer");
        }
        if (getInstance(main).storage.getType() != STORAGE_TYPE.SQL) {
            // we only support SQL for now
            throw new UnsupportedOperationException("");
        }
        return (EmailPasswordSQLStorage) getInstance(main).storage;
    }

    public static EmailVerificationSQLStorage getEmailVerificationStorage(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("please call init() before calling getStorageLayer");
        }
        if (getInstance(main).storage.getType() != STORAGE_TYPE.SQL) {
            // we only support SQL for now
            throw new UnsupportedOperationException("");
        }
        return (EmailVerificationSQLStorage) getInstance(main).storage;
    }

    public static ThirdPartySQLStorage getThirdPartyStorage(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("please call init() before calling getStorageLayer");
        }
        if (getInstance(main).storage.getType() != STORAGE_TYPE.SQL) {
            // we only support SQL for now
            throw new UnsupportedOperationException("");
        }
        return (ThirdPartySQLStorage) getInstance(main).storage;
    }

    public static PasswordlessSQLStorage getPasswordlessStorage(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("please call init() before calling getStorageLayer");
        }
        if (getInstance(main).storage.getType() != STORAGE_TYPE.SQL) {
            // we only support SQL for now
            throw new UnsupportedOperationException("");
        }
        return (PasswordlessSQLStorage) getInstance(main).storage;
    }

    public static JWTRecipeStorage getJWTRecipeStorage(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("please call init() before calling getStorageLayer");
        }

        return (JWTRecipeStorage) getInstance(main).storage;
    }

    public static UserMetadataSQLStorage getUserMetadataStorage(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("please call init() before calling getStorageLayer");
        }

        if (getInstance(main).storage.getType() != STORAGE_TYPE.SQL) {
            // we only support SQL for now
            throw new UnsupportedOperationException("");
        }

        return (UserMetadataSQLStorage) getInstance(main).storage;
    }

    public static UserRolesSQLStorage getUserRolesStorage(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("please call init() before calling getStorageLayer");
        }

        if (getInstance(main).storage.getType() != STORAGE_TYPE.SQL) {
            // we only support SQL for now
            throw new UnsupportedOperationException("");
        }
        return (UserRolesSQLStorage) getInstance(main).storage;
    }

    public static UserIdMappingStorage getUserIdMappingStorage(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("please call init() before calling getStorageLayer");
        }

        return (UserIdMappingStorage) getInstance(main).storage;
    }

    public static DashboardSQLStorage getDashboardStorage(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("please call init() before calling getStorageLayer");
        }

        return (DashboardSQLStorage) getInstance(main).storage;
    }

    public boolean isInMemDb() {
        return this.storage instanceof Start;
    }
}
