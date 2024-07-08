/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.multitenancy;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.session.refreshToken.RefreshTokenKey;
import io.supertokens.signingkeys.AccessTokenSigningKey;
import io.supertokens.signingkeys.JWTSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.utils.SemVer;

import java.io.IOException;
import java.util.*;

import static io.supertokens.multitenancy.Multitenancy.getTenantInfo;

public class MultitenancyHelper extends ResourceDistributor.SingletonResource {

    public static final String RESOURCE_KEY = "io.supertokens.multitenancy.Multitenancy";
    private Main main;
    private TenantConfig[] tenantConfigs;

    // when the core has `supertokens_saas_load_only_cud` set, the tenantConfigs array will be filtered
    // based on the config value. However, we need to keep all the list of CUDs from the db to be able
    // to check if the CUD is present in the DB or not, while processing the requests.
    private final Set<String> dangerous_allCUDsFromDb = new HashSet<>();

    private MultitenancyHelper(Main main) throws StorageQueryException {
        this.main = main;
        TenantConfig[] allTenantsFromDb = getAllTenantsFromDb();
        this.tenantConfigs = this.getFilteredTenantConfigs(allTenantsFromDb);
        this.dangerous_allCUDsFromDb.clear();

        for (TenantConfig config : allTenantsFromDb) {
            this.dangerous_allCUDsFromDb.add(config.tenantIdentifier.getConnectionUriDomain());
        }
    }

    public static MultitenancyHelper getInstance(Main main) {
        try {
            return (MultitenancyHelper) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void init(Main main) throws StorageQueryException, IOException {
        main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY, new MultitenancyHelper(main));
        if (getTenantInfo(main, new TenantIdentifier(null, null, null)) == null) {
            // we create the default base tenant
            try {
                Multitenancy.addNewOrUpdateAppOrTenant(main,
                        new TenantConfig(
                                new TenantIdentifier(null, null, null),
                                new EmailPasswordConfig(true),
                                new ThirdPartyConfig(true, null),
                                new PasswordlessConfig(true),
                                null, null, new JsonObject()), false, false, false);
                // Not force reloading all resources here (the last boolean in the function above)
                // because the ucl for the FeatureFlag is not yet loaded and results in an empty
                // instance of eeFeatureFlag. This is applicable only when the core is starting on
                // an empty database as no tenants are loaded from the db yet.
            } catch (CannotModifyBaseConfigException | BadPermissionException | FeatureNotEnabledException |
                     InvalidConfigException | InvalidProviderConfigException | TenantOrAppNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private TenantConfig[] getAllTenantsFromDb() throws StorageQueryException {
        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL) {
            return new TenantConfig[]{
                    new TenantConfig(
                            TenantIdentifier.BASE_TENANT,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            null, null, new JsonObject()
                    )
            };
        }
        return StorageLayer.getMultitenancyStorage(main).getAllTenants();
    }

    public List<TenantIdentifier> refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(
            boolean reloadAllResources) {
        try {
            return main.getResourceDistributor().withResourceDistributorLock(() -> {
                try {
                    TenantConfig[] tenantsFromDb = getAllTenantsFromDb();
                    TenantConfig[] filteredTenantsFromDb = this.getFilteredTenantConfigs(tenantsFromDb);

                    Map<ResourceDistributor.KeyClass, JsonObject> normalizedTenantsFromDb =
                            Config.getNormalisedConfigsForAllTenants(
                                    filteredTenantsFromDb, Config.getBaseConfigAsJsonObject(main));

                    Map<ResourceDistributor.KeyClass, JsonObject> normalizedTenantsFromMemory =
                            Config.getNormalisedConfigsForAllTenants(
                                    this.tenantConfigs, Config.getBaseConfigAsJsonObject(main));

                    List<TenantIdentifier> tenantsThatChanged = new ArrayList<>();

                    for (Map.Entry<ResourceDistributor.KeyClass, JsonObject> entry :
                            normalizedTenantsFromMemory.entrySet()) {
                        JsonObject tenantConfigFromMemory = entry.getValue();
                        JsonObject tenantConfigFromDb = normalizedTenantsFromDb.get(entry.getKey());

                        if (!tenantConfigFromMemory.equals(tenantConfigFromDb)) {
                            tenantsThatChanged.add(entry.getKey().getTenantIdentifier());
                        }
                    }

                    boolean sameNumberOfTenants =
                            filteredTenantsFromDb.length == this.tenantConfigs.length;

                    this.dangerous_allCUDsFromDb.clear();
                    for (TenantConfig tenant : tenantsFromDb) {
                        this.dangerous_allCUDsFromDb.add(tenant.tenantIdentifier.getConnectionUriDomain());
                    }
                    this.tenantConfigs = filteredTenantsFromDb;
                    if (tenantsThatChanged.size() == 0 && sameNumberOfTenants) {
                        return tenantsThatChanged;
                    }

                    ProcessState.getInstance(main)
                            .addState(ProcessState.PROCESS_STATE.TENANTS_CHANGED_DURING_REFRESH_FROM_DB, null);

                    // this order is important. For example, storageLayer depends on config, and cronjobs depends on
                    // storageLayer
                    if (reloadAllResources) {
                        forceReloadAllResources(tenantsThatChanged);
                    } else {
                        // we do these two here cause they don't really depend on any table in the db, and these
                        // two are required for allocating any further resource for this tenant
                        loadConfig(tenantsThatChanged);
                        loadStorageLayer();
                    }
                    return tenantsThatChanged;
                } catch (Exception e) {
                    Logging.error(main, TenantIdentifier.BASE_TENANT, e.getMessage(), false, e);
                    return new ArrayList<>();
                }
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new IllegalStateException(e);
        }
    }

    public void forceReloadAllResources(List<TenantIdentifier> tenantsThatChanged) {
        try {
            main.getResourceDistributor().withResourceDistributorLock(() -> {
                try {
                    loadConfig(tenantsThatChanged);
                    loadStorageLayer();
                    loadFeatureFlag(tenantsThatChanged);
                    loadSigningKeys(tenantsThatChanged);
                    refreshCronjobs();
                } catch (Exception e) {
                    Logging.error(main, TenantIdentifier.BASE_TENANT, e.getMessage(), false, e);
                }
                return null;
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new IllegalStateException(e);
        }
    }

    public void loadConfig(List<TenantIdentifier> tenantsThatChanged) throws IOException, InvalidConfigException {
        Config.loadAllTenantConfig(main, this.tenantConfigs, tenantsThatChanged);
    }

    public void loadStorageLayer() throws IOException, InvalidConfigException {
        StorageLayer.loadAllTenantStorage(main, this.tenantConfigs);
    }

    public void loadFeatureFlag(List<TenantIdentifier> tenantsThatChanged) {
        List<AppIdentifier> apps = new ArrayList<>();
        Set<AppIdentifier> appsSet = new HashSet<>();
        for (TenantConfig t : this.tenantConfigs) {
            if (appsSet.contains(t.tenantIdentifier.toAppIdentifier())) {
                continue;
            }
            apps.add(t.tenantIdentifier.toAppIdentifier());
            appsSet.add(t.tenantIdentifier.toAppIdentifier());
        }
        FeatureFlag.loadForAllTenants(main, apps, tenantsThatChanged);
    }

    public void loadSigningKeys(List<TenantIdentifier> tenantsThatChanged)
            throws UnsupportedJWTSigningAlgorithmException {
        List<AppIdentifier> apps = new ArrayList<>();
        Set<AppIdentifier> appsSet = new HashSet<>();
        for (TenantConfig t : this.tenantConfigs) {
            if (appsSet.contains(t.tenantIdentifier.toAppIdentifier())) {
                continue;
            }
            apps.add(t.tenantIdentifier.toAppIdentifier());
            appsSet.add(t.tenantIdentifier.toAppIdentifier());
        }
        AccessTokenSigningKey.loadForAllTenants(main, apps, tenantsThatChanged);
        RefreshTokenKey.loadForAllTenants(main, apps, tenantsThatChanged);
        JWTSigningKey.loadForAllTenants(main, apps, tenantsThatChanged);
        SigningKeys.loadForAllTenants(main, apps, tenantsThatChanged);
    }

    public void refreshCronjobs() {
        List<List<TenantIdentifier>> list = StorageLayer.getTenantsWithUniqueUserPoolId(main);
        Cronjobs.getInstance(main).setTenantsInfo(list);
    }

    public TenantConfig[] getAllTenants() {
        try {
            return main.getResourceDistributor().withResourceDistributorLockWithReturn(() -> {
                // Returning a deep copy of the tenantConfigs array so that the functions consuming it
                // do not modify the original array
                TenantConfig[] tenantConfigs = new TenantConfig[this.tenantConfigs.length];

                for (int i = 0; i < this.tenantConfigs.length; i++) {
                    tenantConfigs[i] = new TenantConfig(this.tenantConfigs[i]);
                }
                return tenantConfigs;
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new IllegalStateException(e);
        }
    }

    private TenantConfig[] getFilteredTenantConfigs(TenantConfig[] inputTenantConfigs) {
        String loadOnlyCUD = Config.getBaseConfig(main).getSuperTokensLoadOnlyCUD();

        if (loadOnlyCUD == null) {
            return inputTenantConfigs;
        }

        return Arrays.stream(inputTenantConfigs)
                .filter(tenantConfig -> tenantConfig.tenantIdentifier.getConnectionUriDomain().equals(loadOnlyCUD)
                        || tenantConfig.tenantIdentifier.getConnectionUriDomain()
                        .equals(TenantIdentifier.DEFAULT_CONNECTION_URI))
                .toArray(TenantConfig[]::new);
    }

    public boolean isConnectionUriDomainPresentInDb(String cud) {
        return this.dangerous_allCUDsFromDb.contains(cud);
    }

    public static boolean isEmailPasswordEnabled(TenantConfig tenantConfig, SemVer version) {
        if (version.greaterThanOrEqualTo(SemVer.v5_1)) {
            return true;
        } else if (version.greaterThanOrEqualTo(SemVer.v5_0)) {
            return tenantConfig.emailPasswordConfig.isEnabledIn5_0(tenantConfig.firstFactors);
        } else {
            return tenantConfig.emailPasswordConfig.isEnabledInLesserThanOrEqualTo4_0(tenantConfig.firstFactors);
        }
    }

    public static boolean isThirdPartyEnabled(TenantConfig tenantConfig, SemVer version) {
        if (version.greaterThanOrEqualTo(SemVer.v5_1)) {
            return true;
        } else if (version.greaterThanOrEqualTo(SemVer.v5_0)) {
            return tenantConfig.thirdPartyConfig.isEnabledIn5_0(tenantConfig.firstFactors);
        } else {
            return tenantConfig.thirdPartyConfig.isEnabledInLesserThanOrEqualTo4_0(tenantConfig.firstFactors);
        }
    }

    public static boolean isPasswordlessEnabled(TenantConfig tenantConfig, SemVer version) {
        if (version.greaterThanOrEqualTo(SemVer.v5_1)) {
            return true;
        } else if (version.greaterThanOrEqualTo(SemVer.v5_0)) {
            return tenantConfig.passwordlessConfig.isEnabledIn5_0(tenantConfig.firstFactors);
        } else {
            return tenantConfig.passwordlessConfig.isEnabledInLesserThanOrEqualTo4_0(tenantConfig.firstFactors);
        }
    }
}
