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
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config extends ResourceDistributor.SingletonResource {

    public static final String RESOURCE_KEY = "io.supertokens.config.Config";
    private final Main main;
    private final CoreConfig core;

    private Config(Main main, String configFilePath) throws InvalidConfigException, IOException {
        this.main = main;
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        CoreConfig config = mapper.readValue(new File(configFilePath), CoreConfig.class);
        config.normalizeAndValidate(main);
        this.core = config;
    }

    private Config(Main main, JsonObject jsonConfig) throws IOException, InvalidConfigException {
        this.main = main;
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        CoreConfig config = mapper.readValue(jsonConfig.toString(), CoreConfig.class);
        config.normalizeAndValidate(main);
        this.core = config;
    }

    public static Config getInstance(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        return (Config) main.getResourceDistributor().getResource(tenantIdentifier, RESOURCE_KEY);
    }

    public static void loadBaseConfig(Main main)
            throws InvalidConfigException, IOException {
        main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new Config(main, getConfigFilePath(main)));

        // this function is only called for the base config since we only want one logging file(s) for all tenants
        try {
            getInstance(new TenantIdentifier(null, null, null), main).core.createLoggingFile(main);
        } catch (TenantOrAppNotFoundException ignored) {
            // should never come here..
        }

        Logging.info(main, TenantIdentifier.BASE_TENANT, "Loading supertokens config.", true);
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

    @TestOnly
    public static void loadAllTenantConfig(Main main, TenantConfig[] tenants)
            throws IOException, InvalidConfigException {
        loadAllTenantConfig(main, tenants, new ArrayList<>());
    }

    public static void loadAllTenantConfig(Main main, TenantConfig[] tenants, List<TenantIdentifier> tenantsThatChanged)
            throws IOException, InvalidConfigException {
        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.LOADING_ALL_TENANT_CONFIG, null);
        Map<ResourceDistributor.KeyClass, JsonObject> normalisedConfigs = getNormalisedConfigsForAllTenants(
                tenants,
                getBaseConfigAsJsonObject(main));

        assertAllTenantConfigsAreValid(main, normalisedConfigs, tenants);

        // At this point, we know that all configs are valid.
        try {
            main.getResourceDistributor().withResourceDistributorLock(() -> {
                try {
                    Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> existingResources =
                            main.getResourceDistributor()
                                    .getAllResourcesWithResourceKey(RESOURCE_KEY);
                    main.getResourceDistributor().clearAllResourcesWithResourceKey(RESOURCE_KEY);
                    for (ResourceDistributor.KeyClass key : normalisedConfigs.keySet()) {
                        ResourceDistributor.SingletonResource resource = existingResources.get(
                                new ResourceDistributor.KeyClass(
                                        key.getTenantIdentifier(),
                                        RESOURCE_KEY));
                        if (resource != null && !tenantsThatChanged.contains(key.getTenantIdentifier())) {
                            main.getResourceDistributor()
                                    .setResource(key.getTenantIdentifier(),
                                            RESOURCE_KEY,
                                            resource);
                        } else {
                            main.getResourceDistributor()
                                    .setResource(key.getTenantIdentifier(), RESOURCE_KEY,
                                            new Config(main, normalisedConfigs.get(key)));

                        }
                    }
                } catch (InvalidConfigException | IOException e) {
                    throw new ResourceDistributor.FuncException(e);
                }
                return null;
            });
        } catch (ResourceDistributor.FuncException e) {
            if (e.getCause() instanceof InvalidConfigException) {
                throw (InvalidConfigException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }

    // this function will check for conflicting configs across all tenants, including the base config.
    public static void assertAllTenantConfigsAreValid(Main main,
                                                      Map<ResourceDistributor.KeyClass, JsonObject> normalisedConfigs,
                                                      TenantConfig[] tenants)
            throws InvalidConfigException, IOException {
        Map<String, Storage> userPoolToStorage = new HashMap<>();
        Map<String, Config> appIdToConfigMap = new HashMap<>();
        Map<String, String> userPoolIdToConnectionUriDomain = new HashMap<>();
        for (ResourceDistributor.KeyClass key : normalisedConfigs.keySet()) {
            JsonObject currentConfig = normalisedConfigs.get(key);
            // this also checks for the validity of the config from the db's point
            // of view cause getNewStorageInstance calls loadConfig on the db plugin
            // which calls creates a new instance of the Config object, which calls
            // the validate function.

            // doNotLog is set to true so that the plugin loading message is not logged from here
            Storage storage = StorageLayer.getNewStorageInstance(main, currentConfig, key.getTenantIdentifier(), true);
            final String userPoolId = storage.getUserPoolId();
            final String connectionUriAndAppId =
                    key.getTenantIdentifier().getConnectionUriDomain() + "|" + key.getTenantIdentifier().getAppId();

            // we enforce that each connectiondomainurl has a unique user pool ID
            if (userPoolIdToConnectionUriDomain.get(userPoolId) != null) {
                if (!userPoolIdToConnectionUriDomain.get(userPoolId)
                        .equals(key.getTenantIdentifier().getConnectionUriDomain())) {
                    throw new InvalidConfigException(
                            "ConnectionUriDomain: " + userPoolIdToConnectionUriDomain.get(userPoolId) +
                                    " cannot be mapped to the same user pool as " +
                                    key.getTenantIdentifier().getConnectionUriDomain());
                }
            } else {
                userPoolIdToConnectionUriDomain.put(userPoolId, key.getTenantIdentifier().getConnectionUriDomain());
            }

            // we check that conflicting configs for the same user pool ID doesn't exist
            {
                Storage storageForCurrentUserPoolId = userPoolToStorage.get(userPoolId);
                if (storageForCurrentUserPoolId == null) {
                    userPoolToStorage.put(userPoolId, storage);
                } else {
                    // this will check conflicting configs for db plugin related configs..
                    storageForCurrentUserPoolId.assertThatConfigFromSameUserPoolIsNotConflicting(currentConfig);
                }
            }

            {
                // now we check conflicting configs for core related configs.
                // this also checks for the validity of currentConfig itself cause
                // it creates a new Config object, and the constructor calls the validate function.
                Config configForCurrentAppId = appIdToConfigMap.get(connectionUriAndAppId);
                if (configForCurrentAppId == null) {
                    configForCurrentAppId = new Config(main, currentConfig);
                    appIdToConfigMap.put(connectionUriAndAppId, configForCurrentAppId);
                } else {
                    configForCurrentAppId.core.assertThatConfigFromSameAppIdAreNotConflicting(
                            new Config(main, currentConfig).core);
                }
            }
        }

        for (TenantConfig t : tenants) {
            // here we check that non base config doesn't have settings that are only applicable per core.
            CoreConfig.assertThatCertainConfigIsNotSetForAppOrTenants(t.coreConfig);
        }
    }

    public static Map<ResourceDistributor.KeyClass, JsonObject> getNormalisedConfigsForAllTenants(
            TenantConfig[] tenants,
            JsonObject baseConfigJson) {
        Map<ResourceDistributor.KeyClass, JsonObject> result = new HashMap<>();
        Map<ResourceDistributor.KeyClass, JsonObject> jsonConfigs = new HashMap<>();

        for (TenantConfig tenant : tenants) {
            jsonConfigs.put(
                    new ResourceDistributor.KeyClass(tenant.tenantIdentifier, RESOURCE_KEY),
                    tenant.coreConfig);
        }
        for (TenantConfig tenant : tenants) {
            if (tenant.tenantIdentifier.equals(new TenantIdentifier(null, null, null))) {
                // this refers to the base tenant's config which is in the config.yaml file.
                continue;
            }
            JsonObject finalJson = new JsonObject();

            JsonObject fetchedConfig = jsonConfigs.get(
                    new ResourceDistributor.KeyClass(tenant.tenantIdentifier, RESOURCE_KEY));
            if (fetchedConfig != null) {
                fetchedConfig.entrySet().forEach(stringJsonElementEntry -> {
                    if (!finalJson.has(stringJsonElementEntry.getKey())) {
                        finalJson.add(stringJsonElementEntry.getKey(), stringJsonElementEntry.getValue());
                    }
                });
            }

            fetchedConfig = jsonConfigs.get(
                    new ResourceDistributor.KeyClass(
                            new TenantIdentifier(tenant.tenantIdentifier.getConnectionUriDomain(),
                                    tenant.tenantIdentifier.getAppId(), null),
                            RESOURCE_KEY));
            if (fetchedConfig != null) {
                fetchedConfig.entrySet().forEach(stringJsonElementEntry -> {
                    if (!finalJson.has(stringJsonElementEntry.getKey())) {
                        finalJson.add(stringJsonElementEntry.getKey(), stringJsonElementEntry.getValue());
                    }
                });
            }

            // this is the base case config for SaaS users since they will all have a
            // specific connection uri configured for them, and then can edit the
            // config for all their apps via our SaaS dashboard.
            fetchedConfig = jsonConfigs.get(
                    new ResourceDistributor.KeyClass(
                            new TenantIdentifier(tenant.tenantIdentifier.getConnectionUriDomain(),
                                    null, null),
                            RESOURCE_KEY));
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

            result.put(new ResourceDistributor.KeyClass(tenant.tenantIdentifier, RESOURCE_KEY),
                    finalJson);
        }

        result.put(new ResourceDistributor.KeyClass(new TenantIdentifier(null, null, null), RESOURCE_KEY),
                baseConfigJson);

        return result;
    }

    public static CoreConfig getConfig(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        return getInstance(tenantIdentifier, main).core;
    }

    public static CoreConfig getBaseConfig(Main main) {
        try {
            return getInstance(new TenantIdentifier(null, null, null), main).core;
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static CoreConfig getConfig(Main main) {
        try {
            return getConfig(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

}
