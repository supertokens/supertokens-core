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
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.output.Logging;
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

import java.io.IOException;
import java.util.*;

import static io.supertokens.multitenancy.Multitenancy.getTenantInfo;

public class MultitenancyHelper extends ResourceDistributor.SingletonResource {

    public static final String RESOURCE_KEY = "io.supertokens.multitenancy.Multitenancy";
    private Main main;
    private TenantConfig[] tenantConfigs;

    private MultitenancyHelper(Main main) throws StorageQueryException {
        this.main = main;
        this.tenantConfigs = getAllTenantsFromDb();
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
                                new EmailPasswordConfig(true), new ThirdPartyConfig(true, null),
                                new PasswordlessConfig(true), new JsonObject()), false);
            } catch (CannotModifyBaseConfigException | BadPermissionException | FeatureNotEnabledException | InvalidConfigException | InvalidProviderConfigException | TenantOrAppNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private TenantConfig[] getAllTenantsFromDb() throws StorageQueryException {
        return StorageLayer.getMultitenancyStorage(main).getAllTenants();
    }

    public void refreshTenantsInCoreIfRequired(boolean reloadAllResources) {
        try {
            main.getResourceDistributor().withResourceDistributorLock(() -> {
                try {
                    TenantConfig[] tenantsFromDb = getAllTenantsFromDb();

                    boolean hasChanged = false;
                    if (tenantsFromDb.length != tenantConfigs.length) {
                        hasChanged = true;
                    } else {
                        Map<TenantIdentifier, TenantConfig> fromDb = new HashMap<>();
                        for (TenantConfig t : tenantsFromDb) {
                            fromDb.put(t.tenantIdentifier, t);
                        }
                        for (TenantConfig t : this.tenantConfigs) {
                            TenantConfig fromDbConfig = fromDb.get(t.tenantIdentifier);
                            if (!t.deepEquals(fromDbConfig)) {
                                hasChanged = true;
                                break;
                            }
                        }
                    }

                    this.tenantConfigs = tenantsFromDb;
                    if (!hasChanged) {
                        return;
                    }

                    // this order is important. For example, storageLayer depends on config, and cronjobs depends on
                    // storageLayer
                    if (reloadAllResources) {
                        forceReloadAllResources();
                    } else {
                        // we do these two here cause they don't really depend on any table in the db, and these
                        // two are required for allocating any further resource for this tenant
                        loadConfig();
                        loadStorageLayer();
                    }
                } catch (Exception e) {
                    Logging.error(main, TenantIdentifier.BASE_TENANT, e.getMessage(), false, e);
                }
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new IllegalStateException(e);
        }
    }

    public void forceReloadAllResources() {
        try {
            main.getResourceDistributor().withResourceDistributorLock(() -> {
                try {
                    loadConfig();
                    loadStorageLayer();
                    loadFeatureFlag();
                    loadSigningKeys();
                    refreshCronjobs();
                } catch (Exception e) {
                    Logging.error(main, TenantIdentifier.BASE_TENANT, e.getMessage(), false, e);
                }
            });
        } catch (ResourceDistributor.FuncException e) {
            throw new IllegalStateException(e);
        }
    }

    public void loadConfig() throws IOException, InvalidConfigException {
        Config.loadAllTenantConfig(main, this.tenantConfigs);
    }

    public void loadStorageLayer() throws IOException, InvalidConfigException {
        StorageLayer.loadAllTenantStorage(main, this.tenantConfigs);
    }

    public void loadFeatureFlag() {
        List<AppIdentifier> apps = new ArrayList<>();
        Set<AppIdentifier> appsSet = new HashSet<>();
        for (TenantConfig t : tenantConfigs) {
            if (appsSet.contains(t.tenantIdentifier.toAppIdentifier())) {
                continue;
            }
            apps.add(t.tenantIdentifier.toAppIdentifier());
            appsSet.add(t.tenantIdentifier.toAppIdentifier());
        }
        FeatureFlag.loadForAllTenants(main, apps);
    }

    public void loadSigningKeys() throws UnsupportedJWTSigningAlgorithmException {
        List<AppIdentifier> apps = new ArrayList<>();
        Set<AppIdentifier> appsSet = new HashSet<>();
        for (TenantConfig t : tenantConfigs) {
            if (appsSet.contains(t.tenantIdentifier.toAppIdentifier())) {
                continue;
            }
            apps.add(t.tenantIdentifier.toAppIdentifier());
            appsSet.add(t.tenantIdentifier.toAppIdentifier());
        }
        AccessTokenSigningKey.loadForAllTenants(main, apps);
        RefreshTokenKey.loadForAllTenants(main, apps);
        JWTSigningKey.loadForAllTenants(main, apps);
        SigningKeys.loadForAllTenants(main, apps);
    }

    private void refreshCronjobs() {
        List<List<TenantIdentifier>> list = StorageLayer.getTenantsWithUniqueUserPoolId(main);
        Cronjobs.getInstance(main).setTenantsInfo(list);
    }

    public TenantConfig[] getAllTenants() {
        try {
            return main.getResourceDistributor().withResourceDistributorLockWithReturn(() -> this.tenantConfigs);
        } catch (ResourceDistributor.FuncException e) {
            throw new IllegalStateException(e);
        }
    }
}
