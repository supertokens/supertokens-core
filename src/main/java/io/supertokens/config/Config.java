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
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
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

    private Config(Main main, String configFilePath) throws InvalidConfigException, IOException {
        this.main = main;
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        CoreConfig config = mapper.readValue(new File(configFilePath), CoreConfig.class);
        config.validate(main);
        this.core = config;
    }

    private Config(Main main, JsonObject jsonConfig) throws IOException, InvalidConfigException {
        this.main = main;
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        CoreConfig config = mapper.readValue(jsonConfig.toString(), CoreConfig.class);
        config.validate(main);
        this.core = config;
    }

    private static Config getInstance(String connectionUriDomain, String tenantId, Main main) {
        return (Config) main.getResourceDistributor().getResource(connectionUriDomain, tenantId, RESOURCE_KEY);
    }

    public static void loadBaseConfig(String connectionUriDomain, String tenantId, Main main)
            throws InvalidConfigException, IOException {
        synchronized (lock) {
            main.getResourceDistributor()
                    .setResource(connectionUriDomain, tenantId, RESOURCE_KEY,
                            new Config(main, getConfigFilePath(main)));

            // this function is only called for the base config since we only want one logging file(s) for all tenants
            getInstance(null, null, main).core.createLoggingFile(main);

            Logging.info(main, "Loading supertokens config.", true);
        }
    }

    public static JsonObject getBaseConfigAsJsonObject(Main main) throws IOException {
        // we do not use the CoreConfig class here cause the actual config.yaml file may
        // contain other fields which the CoreConfig doesn't have, and we do not want to
        // omit them from the output json.
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        Object obj = yamlReader.readValue(new File(getConfigFilePath(main)), Object.class);
        return new Gson().toJsonTree(obj).getAsJsonObject();
    }

    private static String getConfigFilePath(Main main) {
        return CLIOptions.get(main).getConfigFilePath() == null
                ? CLIOptions.get(main).getInstallationPath() + "config.yaml"
                : CLIOptions.get(main).getConfigFilePath();
    }

    public static void loadAllTenantConfig(Main main)
            throws IOException, InvalidConfigException {
        // we load up all the json config from the core for each tenant
        // and then for each tenant, we create merge their jsons from most specific
        // to least specific and then save the final json as a core config in the
        // global resource distributor.
        TenantConfig[] tenants = StorageLayer.getMultitenancyStorage(main).getAllTenants();

        loadAllTenantConfig(main, tenants);
    }

    public static void loadAllTenantConfig(Main main, TenantConfig[] tenants)
            throws IOException, InvalidConfigException {
        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_CONFIG, null);
        synchronized (lock) {
            Map<ResourceDistributor.KeyClass, JsonObject> normalisedConfigs = getNormalisedConfigsForAllTenants(
                    tenants,
                    getBaseConfigAsJsonObject(main));

            assertAllTenantConfigsAreValid(main, normalisedConfigs);

            // At this point, we know that all configs are valid.
            main.getResourceDistributor().clearAllResourcesWithResourceKey(RESOURCE_KEY);
            for (ResourceDistributor.KeyClass key : normalisedConfigs.keySet()) {
                main.getResourceDistributor().setResource(key, new Config(main, normalisedConfigs.get(key)));
            }
        }
    }

    // this function will check for conflicting configs across all tenants, including the base config.
    private static void assertAllTenantConfigsAreValid(Main main,
                                                       Map<ResourceDistributor.KeyClass, JsonObject> normalisedConfigs)
            throws InvalidConfigException, IOException {
        Map<String, Storage> userPoolIdToStorage = new HashMap<>();
        Map<String, Config> userPoolIdToConfigArray = new HashMap<>();
        for (ResourceDistributor.KeyClass key : normalisedConfigs.keySet()) {
            JsonObject currentConfig = normalisedConfigs.get(key);
            // this also checks for the validity of the config from the db's point
            // of view cause getNewStorageInstance calls loadConfig on the db plugin
            // which calls creates a new instance of the Config object, which calls
            // the validate function.
            Storage storage = StorageLayer.getNewStorageInstance(main, currentConfig);
            final String userPoolId = storage.getUserPoolId();
            {
                Storage storageForCurrentUserPoolId = userPoolIdToStorage.get(userPoolId);
                if (storageForCurrentUserPoolId == null) {
                    userPoolIdToStorage.put(userPoolId, storage);
                } else {
                    // this will check conflicting configs for db plugin related configs..
                    storageForCurrentUserPoolId.assertThatConfigFromSameUserPoolIsNotConflicting(currentConfig);
                }
            }

            {
                // now we check conflicting configs for core related configs.
                // this also checks for the validity of currentConfig itself cause
                // it creates a new Config object, and the constructor calls the validate function.
                Config configForCurrentUserPoolId = userPoolIdToConfigArray.get(userPoolId);
                if (configForCurrentUserPoolId == null) {
                    configForCurrentUserPoolId = new Config(main, currentConfig);
                    userPoolIdToConfigArray.put(userPoolId, configForCurrentUserPoolId);
                } else {
                    configForCurrentUserPoolId.assertThatConfigFromSameUserPoolIsNotConflicting(
                            new Config(main, currentConfig).core);
                }
            }
        }
    }

    public static Map<ResourceDistributor.KeyClass, JsonObject> getNormalisedConfigsForAllTenants(
            TenantConfig[] tenants,
            JsonObject baseConfigJson) {
        Map<ResourceDistributor.KeyClass, JsonObject> result = new HashMap<>();
        Map<ResourceDistributor.KeyClass, JsonObject> jsonConfigs = new HashMap<>();

        for (TenantConfig tenant : tenants) {
            jsonConfigs.put(
                    new ResourceDistributor.KeyClass(tenant.connectionUriDomain, tenant.tenantId, RESOURCE_KEY),
                    tenant.coreConfig);
        }
        for (TenantConfig tenant : tenants) {
            if (tenant.tenantId == null && tenant.connectionUriDomain == null) {
                // this refers to the base tenant's config which is in the config.yaml file.
                continue;
            }
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

            baseConfigJson.entrySet().forEach(stringJsonElementEntry -> {
                if (!finalJson.has(stringJsonElementEntry.getKey())) {
                    finalJson.add(stringJsonElementEntry.getKey(), stringJsonElementEntry.getValue());
                }
            });

            result.put(new ResourceDistributor.KeyClass(connectionUriDomain, tenantId, RESOURCE_KEY),
                    finalJson);
        }

        result.put(new ResourceDistributor.KeyClass(null, null, RESOURCE_KEY),
                baseConfigJson);

        return result;
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

    private void assertThatConfigFromSameUserPoolIsNotConflicting(CoreConfig otherConfig)
            throws InvalidConfigException {
        core.assertThatConfigFromSameUserPoolIsNotConflicting(otherConfig);
    }

}
