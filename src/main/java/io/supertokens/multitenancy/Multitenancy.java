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
import io.supertokens.session.accessToken.AccessTokenSigningKey;
import io.supertokens.session.refreshToken.RefreshTokenKey;
import io.supertokens.storageLayer.StorageLayer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class Multitenancy extends ResourceDistributor.SingletonResource {

    public static final String RESOURCE_KEY = "io.supertokens.multitenancy.Multitenancy";
    private Main main;
    private TenantConfig[] tenantConfigs;

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
            boolean hasChanged = false;
            if (tenantsFromDb.length != tenantConfigs.length) {
                hasChanged = true;
            } else {
                // TODO: compare the two lists..
            }

            if (!hasChanged) {
                return;
            }

            this.tenantConfigs = tenantsFromDb;

            // TODO: do we need locking?
            loadConfig();
            loadStorageLayer();
            loadSigningKeys();
            refreshCronjobs();
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

    public void refreshCronjobs() throws StorageQueryException {
        if (Arrays.stream(FeatureFlag.getInstance(main).getEnabledFeatures())
                .noneMatch(ee_features -> ee_features == EE_FEATURES.MULTI_TENANCY)) {
            return;
        }
        // TODO..
        Cronjobs.getInstance(main).setTenantsInfo(new ArrayList<>());
    }

}
