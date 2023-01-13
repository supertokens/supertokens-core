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

package io.supertokens.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.output.Logging;

import java.io.File;
import java.io.IOException;

public class Config extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.config.Config";
    private final Main main;
    private final CoreConfig core;

    private Config(Main main, String configFilePath) {
        this.main = main;
        try {
            this.core = loadCoreConfig(configFilePath);
        } catch (IOException e) {
            throw new QuitProgramException(e);
        }
    }

    private static Config getInstance(String connectionUriDomain, String tenantId, Main main) {
        return (Config) main.getResourceDistributor().getResource(connectionUriDomain, tenantId, RESOURCE_KEY);
    }

    public static void loadConfig(String connectionUriDomain, String tenantId, Main main, String configFilePath) {
        main.getResourceDistributor()
                .setResource(connectionUriDomain, tenantId, RESOURCE_KEY, new Config(main, configFilePath));
        Logging.info(main, "Loading supertokens config.", true);
    }

    public static CoreConfig getConfig(String connectionUriDomain, String tenantId, Main main) {
        if (getInstance(connectionUriDomain, tenantId, main) == null) {
            throw new QuitProgramException("Please call loadConfig() before calling getConfig()");
        }
        return getInstance(connectionUriDomain, tenantId, main).core;
    }

    @Deprecated
    public static CoreConfig getConfig(Main main) {
        return getConfig(null, null, main);
    }

    private CoreConfig loadCoreConfig(String configFilePath) throws IOException {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        CoreConfig config = mapper.readValue(new File(configFilePath), CoreConfig.class);
        config.validateAndInitialise(main);
        return config;
    }

}
