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
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.storageLayer.StorageLayer;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Config extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.config.Config";
    private final Main main;
    private final CoreConfig core;

    // this lock is used to synchronise changes in the config
    // when we are reloading all tenant configs.
    private static final Object lock = new Object();

    private Config(Main main, String configFilePath) {
        this.main = main;
        try {
            final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            CoreConfig config = mapper.readValue(new File(configFilePath), CoreConfig.class);
            config.validateAndInitialise(main);
            this.core = config;
        } catch (IOException e) {
            throw new QuitProgramException(e);
        }
    }

    private Config(Main main, JsonObject jsonConfig) throws IOException {
        this.main = main;
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        CoreConfig config = mapper.readValue(jsonConfig.toString(), CoreConfig.class);
        config.validateAndInitialise(main);
        this.core = config;
    }

    private static Config getInstance(String connectionUriDomain, String tenantId, Main main) {
        return (Config) main.getResourceDistributor().getResource(connectionUriDomain, tenantId, RESOURCE_KEY);
    }

    public static void loadConfig(String connectionUriDomain, String tenantId, Main main, String configFilePath) {
        synchronized (lock) {
            main.getResourceDistributor()
                    .setResource(connectionUriDomain, tenantId, RESOURCE_KEY, new Config(main, configFilePath));
            Logging.info(main, "Loading supertokens config.", true);
        }
    }

    public static TenantConfig[] loadAllTenantConfig(Main main) throws IOException {
        // we load up all the json config from the core for each tenant
        // and then for each tenant, we create merge their jsons from most specific
        // to least specific and then save the final json as a core config in the
        // global resource distributor.
        TenantConfig[] tenants = StorageLayer.getMultitenancyStorage(main).getAllTenants();
        Map<ResourceDistributor.KeyClass, JsonObject> jsonConfigs = new HashMap<>();

        for (TenantConfig tenant : tenants) {
            jsonConfigs.put(
                    new ResourceDistributor.KeyClass(tenant.connectionUriDomain, tenant.tenantId, RESOURCE_KEY),
                    tenant.coreConfig);
        }

        synchronized (lock) {
            Config baseConfig = getInstance(null, null, main);
            main.getResourceDistributor().clearAllResourcesWithResourceKey(RESOURCE_KEY);
            for (TenantConfig tenant : tenants) {
                String connectionUriDomain = tenant.connectionUriDomain;
                String tenantId = tenant.tenantId;
                JsonObject finalJson = new JsonObject();

                JsonObject fetchedConfig = jsonConfigs.get(
                        new ResourceDistributor.KeyClass(connectionUriDomain, tenantId, RESOURCE_KEY));
                if (fetchedConfig != null) {
                    fetchedConfig.entrySet().forEach(stringJsonElementEntry -> {
                        if (!finalJson.has(stringJsonElementEntry.getKey())) {
                            finalJson.add(stringJsonElementEntry.getKey(), stringJsonElementEntry.getValue());
                        }
                    });
                }

                fetchedConfig = jsonConfigs.get(
                        new ResourceDistributor.KeyClass(connectionUriDomain, null, RESOURCE_KEY));
                if (fetchedConfig != null) {
                    fetchedConfig.entrySet().forEach(stringJsonElementEntry -> {
                        if (!finalJson.has(stringJsonElementEntry.getKey())) {
                            finalJson.add(stringJsonElementEntry.getKey(), stringJsonElementEntry.getValue());
                        }
                    });
                }

                fetchedConfig = jsonConfigs.get(
                        new ResourceDistributor.KeyClass(null, tenantId, RESOURCE_KEY));
                if (fetchedConfig != null) {
                    fetchedConfig.entrySet().forEach(stringJsonElementEntry -> {
                        if (!finalJson.has(stringJsonElementEntry.getKey())) {
                            finalJson.add(stringJsonElementEntry.getKey(), stringJsonElementEntry.getValue());
                        }
                    });
                }

                Gson gson = new Gson();
                JsonObject baseConfigJson = gson.toJsonTree(baseConfig.core).getAsJsonObject();
                baseConfigJson.entrySet().forEach(stringJsonElementEntry -> {
                    if (!finalJson.has(stringJsonElementEntry.getKey())) {
                        finalJson.add(stringJsonElementEntry.getKey(), stringJsonElementEntry.getValue());
                    }
                });

                // we now set the final json as a config file in the global resource distributor.
                main.getResourceDistributor()
                        .setResource(connectionUriDomain, tenantId, RESOURCE_KEY, new Config(main, finalJson));
            }

            // we finally add the base config again cause we had removed it above.
            main.getResourceDistributor()
                    .setResource(null, null, RESOURCE_KEY, baseConfig);
        }
        return tenants;
    }

    // this function will check for conflicting configs across all tenants
    public static void assertAllTenantConfigs(TenantConfig[] tenants) throws InvalidTenantConfigException {
        // TODO:
    }

    public static CoreConfig getConfig(String connectionUriDomain, String tenantId, Main main) {
        synchronized (lock) {
            if (getInstance(connectionUriDomain, tenantId, main) == null) {
                throw new QuitProgramException("Please call loadConfig() before calling getConfig()");
            }
            return getInstance(connectionUriDomain, tenantId, main).core;
        }
    }

    @Deprecated
    public static CoreConfig getConfig(Main main) {
        return getConfig(null, null, main);
    }

    public class InvalidTenantConfigException extends Exception {

    }

}
