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

package io.supertokens.cliOptions;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

import java.io.File;

public class CLIOptions extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.cliOptions.CLIOptions";
    private static final String CONFIG_FILE_KEY = "configFile=";
    private static final String PORT_FILE_KEY = "port=";
    private static final String HOST_FILE_KEY = "host=";
    private static final String TEST_MODE = "test_mode";
    private static final String FORCE_NO_IN_MEM_DB = "forceNoInMemDB=true";
    private final String installationPath;
    private final String configFilePath;
    private final Integer port;
    private final String host;

    // if this is true, then even in DEV mode, we will not use in memory db, even if there is an error in the plugin
    private final boolean forceNoInMemoryDB;

    private CLIOptions(String[] args) {
        checkIfArgsIsCorrect(args);
        String installationPath = args[0];
        String configFilePathTemp = null;
        Integer portTemp = null;
        String hostTemp = null;
        boolean forceNoInMemoryDBTemp = false;
        for (int i = 1; i < args.length; i++) {
            String curr = args[i];
            if (curr.startsWith(CONFIG_FILE_KEY)) {
                configFilePathTemp = curr.split(CONFIG_FILE_KEY)[1];
                if (!new File(configFilePathTemp).isAbsolute()) {
                    throw new QuitProgramException("configPath option must be an absolute path only");
                }
            } else if (curr.startsWith(PORT_FILE_KEY)) {
                portTemp = Integer.parseInt(curr.split(PORT_FILE_KEY)[1]);
            } else if (curr.startsWith(HOST_FILE_KEY)) {
                hostTemp = curr.split(HOST_FILE_KEY)[1];
            } else if (curr.startsWith(FORCE_NO_IN_MEM_DB)) {
                forceNoInMemoryDBTemp = true;
            } else if (curr.equals(TEST_MODE)) {
                Main.isTesting = true;
            }
        }
        this.configFilePath = configFilePathTemp;
        this.installationPath = installationPath;
        this.port = portTemp;
        this.host = hostTemp;
        this.forceNoInMemoryDB = forceNoInMemoryDBTemp;
    }

    private static CLIOptions getInstance(Main main) {
        try {
            return (CLIOptions) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            throw new QuitProgramException(e);
        }
    }

    public static void load(Main main, String[] args) {
        main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY, new CLIOptions(args));
    }

    public static CLIOptions get(Main main) {
        if (getInstance(main) == null) {
            throw new QuitProgramException("Please call load() function before get");
        }
        return getInstance(main);
    }

    private void checkIfArgsIsCorrect(String[] args) {
        if (args.length == 0) {
            throw new QuitProgramException("Please provide installation path location for SuperTokens");
        }
    }

    public String getConfigFilePath() {
        return configFilePath;
    }

    // NOTE: this value will be fixed depending on operating system being used.. it
    // will be passed from the CLI
    public String getInstallationPath() {
        if (installationPath.endsWith("/")) {
            return installationPath;
        } else {
            return installationPath + "/";
        }
    }

    public Integer getPort() {
        return this.port;
    }

    public String getHost() {
        return this.host;
    }

    public boolean isForceNoInMemoryDB() {
        return this.forceNoInMemoryDB;
    }
}
