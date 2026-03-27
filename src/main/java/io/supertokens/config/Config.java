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
import com.google.gson.GsonBuilder;
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
import io.supertokens.utils.ConfigMapper;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Config extends ResourceDistributor.SingletonResource {

    public static final String RESOURCE_KEY = "io.supertokens.config.Config";
    private final Main main;
    private final CoreConfig core;

    private Config(Main main, String configFilePath) throws InvalidConfigException, IOException {
        this.main = main;
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Object configObj = mapper.readValue(new File(configFilePath), Object.class);
        JsonObject jsonConfig = new GsonBuilder().serializeNulls().create().toJsonTree(configObj).getAsJsonObject();
        CoreConfig.updateConfigJsonFromEnv(jsonConfig);
        StorageLayer.updateConfigJsonFromEnv(main, jsonConfig);
        CoreConfig config = ConfigMapper.mapConfig(jsonConfig, CoreConfig.class);
        config.normalizeAndValidate(main, true);
        this.core = config;
    }

    private Config(Main main, JsonObject jsonConfig) throws IOException, InvalidConfigException {
        this.main = main;
        CoreConfig config = ConfigMapper.mapConfig(jsonConfig, CoreConfig.class);
        config.normalizeAndValidate(main, false);
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
        JsonObject configJson = new GsonBuilder().serializeNulls().create().toJsonTree(obj).getAsJsonObject();
        CoreConfig.updateConfigJsonFromEnv(configJson);
        StorageLayer.updateConfigJsonFromEnv(main, configJson);
        return configJson;
    }

    private static String getConfigFilePath(Main main) {
        String configFile = "config.yaml";
        if (Main.isTesting) {
            String workerId = System.getProperty("org.gradle.test.worker", "");
            configFile = "config" + workerId + ".yaml";
        }
        return CLIOptions.get(main).getConfigFilePath() == null
                ? CLIOptions.get(main).getInstallationPath() + configFile
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
                Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> existingResources =
                        main.getResourceDistributor()
                                .getAllResourcesWithResourceKey(RESOURCE_KEY);
                main.getResourceDistributor().clearAllResourcesWithResourceKey(RESOURCE_KEY);
                for (ResourceDistributor.KeyClass key : normalisedConfigs.keySet()) {
                    try {
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
                    } catch (Exception e) {
                        Logging.error(main, key.getTenantIdentifier(), e.getMessage(), false);
                        // continue loading other resources
                    }
                }
                return null;
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new IllegalStateException("should never happen", e);
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

    /**
     * Normalizes config for a single tenant by walking the inheritance chain:
     * tenant's own config → app-level parent → CUD-level parent → base config.
     * This is O(n) to scan for parent configs but avoids normalizing ALL tenants.
     */
    public static JsonObject getNormalisedConfigForTenant(
            TenantIdentifier target, TenantConfig[] allTenants, JsonObject baseConfigJson) {

        if (target.equals(new TenantIdentifier(null, null, null))) {
            return baseConfigJson;
        }

        TenantIdentifier appParent = new TenantIdentifier(
                target.getConnectionUriDomain(), target.getAppId(), null);
        TenantIdentifier cudParent = new TenantIdentifier(
                target.getConnectionUriDomain(), null, null);

        JsonObject ownConfig = null;
        JsonObject appConfig = null;
        JsonObject cudConfig = null;

        for (TenantConfig tenant : allTenants) {
            if (tenant.tenantIdentifier.equals(target)) {
                ownConfig = tenant.coreConfig;
            } else if (tenant.tenantIdentifier.equals(appParent)) {
                appConfig = tenant.coreConfig;
            } else if (tenant.tenantIdentifier.equals(cudParent)) {
                cudConfig = tenant.coreConfig;
            }
        }

        JsonObject finalJson = new JsonObject();
        if (ownConfig != null) mergeConfigInto(finalJson, ownConfig);
        if (appConfig != null) mergeConfigInto(finalJson, appConfig);
        if (cudConfig != null) mergeConfigInto(finalJson, cudConfig);
        mergeConfigInto(finalJson, baseConfigJson);

        return finalJson;
    }

    private static void mergeConfigInto(JsonObject target, JsonObject source) {
        source.entrySet().forEach(entry -> {
            if (!target.has(entry.getKey())) {
                target.add(entry.getKey(), entry.getValue());
            }
        });
    }

    /**
     * Loads Config resources only for the specified changed tenants, without clearing
     * and rebuilding all Config resources. Used for incremental tenant updates.
     */
    public static void loadConfigForChangedTenants(Main main, TenantConfig[] allTenants,
                                                    List<TenantIdentifier> tenantsThatChanged)
            throws IOException, InvalidConfigException {
        if (tenantsThatChanged == null || tenantsThatChanged.isEmpty()) {
            return;
        }

        JsonObject baseConfig = getBaseConfigAsJsonObject(main);

        try {
            main.getResourceDistributor().withResourceDistributorLock(() -> {
                for (TenantIdentifier changed : tenantsThatChanged) {
                    try {
                        JsonObject normalised = getNormalisedConfigForTenant(changed, allTenants, baseConfig);
                        main.getResourceDistributor().removeResource(changed, RESOURCE_KEY);
                        main.getResourceDistributor().setResource(changed, RESOURCE_KEY,
                                new Config(main, normalised));
                    } catch (Exception e) {
                        Logging.error(main, changed, e.getMessage(), false);
                    }
                }
                return null;
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new IllegalStateException("should never happen", e);
        }
    }

    /**
     * Validates a single tenant's config against existing resources, without re-normalizing
     * or re-validating all existing tenants.
     */
    public static void assertSingleTenantConfigIsValid(Main main, TenantIdentifier targetTenant,
                                                        JsonObject normalisedConfig,
                                                        TenantConfig targetTenantConfig)
            throws InvalidConfigException, IOException {

        // 1. Create a Storage instance for the target tenant to get its userPoolId
        Storage storage = StorageLayer.getNewStorageInstance(main, normalisedConfig, targetTenant, true);
        String userPoolId = storage.getUserPoolId();
        String connectionUriAndAppId =
                targetTenant.getConnectionUriDomain() + "|" + targetTenant.getAppId();

        // 2. Build constraint maps from existing StorageLayer resources
        Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> existingStorages =
                main.getResourceDistributor().getAllResourcesWithResourceKey(StorageLayer.RESOURCE_KEY);

        Map<String, String> userPoolIdToCUD = new HashMap<>();
        Map<String, Storage> userPoolToStorage = new HashMap<>();
        for (Map.Entry<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> entry :
                existingStorages.entrySet()) {
            TenantIdentifier existingTenant = entry.getKey().getTenantIdentifier();
            if (existingTenant.equals(targetTenant)) {
                continue; // skip the target tenant itself (may be an update)
            }
            Storage existingStorage = ((StorageLayer) entry.getValue()).getUnderlyingStorage();
            String existingPoolId = existingStorage.getUserPoolId();
            userPoolIdToCUD.put(existingPoolId, existingTenant.getConnectionUriDomain());
            userPoolToStorage.putIfAbsent(existingPoolId, existingStorage);
        }

        // 3. Check userPoolId → CUD uniqueness
        String existingCUD = userPoolIdToCUD.get(userPoolId);
        if (existingCUD != null && !existingCUD.equals(targetTenant.getConnectionUriDomain())) {
            throw new InvalidConfigException(
                    "ConnectionUriDomain: " + existingCUD +
                            " cannot be mapped to the same user pool as " +
                            targetTenant.getConnectionUriDomain());
        }

        // 4. Check storage config conflicts within the same userPoolId
        Storage existingStorageForPool = userPoolToStorage.get(userPoolId);
        if (existingStorageForPool != null) {
            existingStorageForPool.assertThatConfigFromSameUserPoolIsNotConflicting(normalisedConfig);
        }

        // 5. Validate the target config AND check core config conflicts within the same app.
        // Always create a new Config to validate the target config (calls normalizeAndValidate).
        Config targetConfig = new Config(main, normalisedConfig);

        Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> existingConfigs =
                main.getResourceDistributor().getAllResourcesWithResourceKey(RESOURCE_KEY);
        for (Map.Entry<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> entry :
                existingConfigs.entrySet()) {
            TenantIdentifier existingTenant = entry.getKey().getTenantIdentifier();
            if (existingTenant.equals(targetTenant)) {
                continue;
            }
            String existingCUDAndApp =
                    existingTenant.getConnectionUriDomain() + "|" + existingTenant.getAppId();
            if (existingCUDAndApp.equals(connectionUriAndAppId)) {
                CoreConfig existingCore;
                if (existingTenant.equals(new TenantIdentifier(null, null, null))) {
                    // For the base tenant, create a fresh Config from baseConfigJson for comparison.
                    // The stored base Config uses isBaseTenant=true normalization which may produce
                    // different defaults. Using the JSON constructor ensures consistent comparison.
                    existingCore = new Config(main, getBaseConfigAsJsonObject(main)).core;
                } else {
                    existingCore = ((Config) entry.getValue()).core;
                }
                existingCore.assertThatConfigFromSameAppIdAreNotConflicting(targetConfig.core);
                break; // only need one existing config per app
            }
        }

        // 6. Check non-base configs don't have per-core settings
        if (!targetTenant.equals(new TenantIdentifier(null, null, null))) {
            CoreConfig.assertThatCertainConfigIsNotSetForAppOrTenants(targetTenantConfig.coreConfig);
        }
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
            return getConfig(ResourceDistributor.getAppForTesting(), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

}
