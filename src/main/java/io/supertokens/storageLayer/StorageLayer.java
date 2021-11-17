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
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.inmemorydb.Start;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.pluginInterface.jwt.JWTRecipeStorage;
import io.supertokens.pluginInterface.passwordless.sqlStorage.PasswordlessSQLStorage;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.pluginInterface.thirdparty.sqlStorage.ThirdPartySQLStorage;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.ServiceLoader;

public class StorageLayer extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.storageLayer.StorageLayer";
    private final Storage storage;

    private StorageLayer(Main main, String pluginFolderPath, String configFilePath) throws MalformedURLException {
        Logging.info(main, "Loading storage layer.");
        File loc = new File(pluginFolderPath);
        Storage storageLayerTemp = null;

        File[] flist = loc.listFiles(file -> file.getPath().toLowerCase().endsWith(".jar"));

        if (flist != null) {
            URL[] urls = new URL[flist.length];
            for (int i = 0; i < flist.length; i++) {
                urls[i] = flist[i].toURI().toURL();
            }
            URLClassLoader ucl = new URLClassLoader(urls);

            ServiceLoader<Storage> sl = ServiceLoader.load(Storage.class, ucl);
            Iterator<Storage> it = sl.iterator();
            while (it.hasNext()) {
                Storage plugin = it.next();
                if (storageLayerTemp == null) {
                    storageLayerTemp = plugin;
                } else {
                    throw new QuitProgramException(
                            "Multiple database plugins found. Please make sure that just one plugin is in the /plugin"
                                    + " " + "folder of the installation. Alternatively, please redownload and install "
                                    + "SuperTokens" + ".");
                }
            }
        }

        if (storageLayerTemp != null && !main.isForceInMemoryDB()
                && (storageLayerTemp.canBeUsed(configFilePath) || CLIOptions.get(main).isForceNoInMemoryDB())) {
            this.storage = storageLayerTemp;
        } else {
            Logging.info(main, "Using in memory storage.");
            this.storage = new Start(main);
        }
        this.storage.constructor(main.getProcessId(), Main.makeConsolePrintSilent);
        this.storage.loadConfig(configFilePath);
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

    public boolean isInMemDb() {
        return this.storage instanceof Start;
    }
}
