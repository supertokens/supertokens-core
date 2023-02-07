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

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.exceptions.TenantOrAppNotFoundException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.jwt.JWTSigningKey;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.exceptions.DbInitException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateTenantException;
import io.supertokens.pluginInterface.multitenancy.exceptions.UnknownTenantException;
import io.supertokens.session.accessToken.AccessTokenSigningKey;
import io.supertokens.session.refreshToken.RefreshTokenKey;
import io.supertokens.storageLayer.StorageLayer;

import java.io.IOException;
import java.util.*;

public class Multitenancy extends ResourceDistributor.SingletonResource {

    public static final String RESOURCE_KEY = "io.supertokens.multitenancy.Multitenancy";
    private Main main;
    private TenantConfig[] tenantConfigs;
    private final Object lock = new Object();

    private Multitenancy(Main main) {
        this.main = main;
        this.tenantConfigs = StorageLayer.getMultitenancyStorage(main).getAllTenants();
    }

    public static Multitenancy getInstance(Main main) {
        try {
            return (Multitenancy) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static void init(Main main) {
        main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY, new Multitenancy(main));
    }

    public void refreshTenantsInCoreIfRequired() {
        try {
            TenantConfig[] tenantsFromDb = StorageLayer.getMultitenancyStorage(main).getAllTenants();
            synchronized (lock) {
                boolean hasChanged = false;
                if (tenantsFromDb.length != tenantConfigs.length) {
                    hasChanged = true;
                } else {
                    Set<TenantIdentifier> fromDb = new HashSet<>();
                    for (TenantConfig t : tenantsFromDb) {
                        fromDb.add(t.tenantIdentifier);
                    }
                    for (TenantConfig t : this.tenantConfigs) {
                        if (!fromDb.contains(t.tenantIdentifier)) {
                            hasChanged = true;
                            break;
                        }
                    }
                }

                this.tenantConfigs = tenantsFromDb;
                if (!hasChanged) {
                    return;
                }

                loadConfig();
                loadStorageLayer();
                loadSigningKeys();
                refreshCronjobs();
            }
        } catch (Exception e) {
            Logging.error(main, e.getMessage(), false, e);
        }
    }

    public void loadConfig() throws IOException, InvalidConfigException, StorageQueryException {
        if (Arrays.stream(FeatureFlag.getInstance(main).getEnabledFeatures())
                .noneMatch(ee_features -> ee_features == EE_FEATURES.MULTI_TENANCY)) {
            return;
        }
        Config.loadAllTenantConfig(main, this.tenantConfigs);
    }

    public void loadStorageLayer() throws DbInitException, IOException, InvalidConfigException, StorageQueryException {
        if (Arrays.stream(FeatureFlag.getInstance(main).getEnabledFeatures())
                .noneMatch(ee_features -> ee_features == EE_FEATURES.MULTI_TENANCY)) {
            return;
        }
        StorageLayer.loadAllTenantStorage(main, this.tenantConfigs);
    }

    public void loadSigningKeys() throws UnsupportedJWTSigningAlgorithmException, StorageQueryException {
        if (Arrays.stream(FeatureFlag.getInstance(main).getEnabledFeatures())
                .noneMatch(ee_features -> ee_features == EE_FEATURES.MULTI_TENANCY)) {
            return;
        }
        AccessTokenSigningKey.loadForAllTenants(main, this.tenantConfigs);
        RefreshTokenKey.loadForAllTenants(main, this.tenantConfigs);
        JWTSigningKey.loadForAllTenants(main, this.tenantConfigs);
    }

    private void refreshCronjobs() throws StorageQueryException {
        if (Arrays.stream(FeatureFlag.getInstance(main).getEnabledFeatures())
                .noneMatch(ee_features -> ee_features == EE_FEATURES.MULTI_TENANCY)) {
            return;
        }
        List<TenantIdentifier> list = new ArrayList<>();
        for (TenantConfig t : this.tenantConfigs) {
            list.add(t.tenantIdentifier);
        }
        Cronjobs.getInstance(main).setTenantsInfo(list);
    }

    public static boolean addNewOrUpdateAppOrTenant(Main main, TenantConfig tenant) {
        try {
            StorageLayer.getMultitenancyStorage(main).createTenant(tenant);
            Multitenancy.getInstance(main).refreshTenantsInCoreIfRequired();
            return true;
        } catch (DuplicateTenantException e) {
            try {
                StorageLayer.getMultitenancyStorage(main).overwriteTenantConfig(tenant);
                Multitenancy.getInstance(main).refreshTenantsInCoreIfRequired();
                return false;
            } catch (UnknownTenantException ex) {
                return addNewOrUpdateAppOrTenant(main, tenant);
            }
        }
    }

    public static void deleteTenant(Main main, TenantIdentifier tenantIdentifier)
            throws UnknownTenantException {
        StorageLayer.getMultitenancyStorage(main).deleteTenant(tenantIdentifier);
        Multitenancy.getInstance(main).refreshTenantsInCoreIfRequired();
    }

    public static void deleteApp(Main main, TenantIdentifier tenantIdentifier)
            throws UnknownTenantException {
        StorageLayer.getMultitenancyStorage(main).deleteApp(tenantIdentifier);
        Multitenancy.getInstance(main).refreshTenantsInCoreIfRequired();
    }

    public static void deleteConnectionUriDomain(Main main, TenantIdentifier tenantIdentifier)
            throws UnknownTenantException {
        StorageLayer.getMultitenancyStorage(main).deleteConnectionUriDomainMapping(tenantIdentifier);
        Multitenancy.getInstance(main).refreshTenantsInCoreIfRequired();
    }

    // TODO: add functions to associate users and roles to a tenant (as long as they are in the same tenantId)
    // TODO: add functions to check if a user is part of a tenant before running their logic for all recipes. For
    //  example, if an email password user is added for t1, only if that teannt is used should the user id be
    //  returned, else unknown user ID error.

}
