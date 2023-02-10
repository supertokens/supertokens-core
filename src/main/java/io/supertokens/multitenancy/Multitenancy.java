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
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.jwt.JWTSigningKey;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.exception.*;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.DbInitException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateTenantException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.userroles.exception.UnknownRoleException;
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
        this.tenantConfigs = getAllTenants();
    }

    public static Multitenancy getInstance(Main main) {
        try {
            return (Multitenancy) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void init(Main main) {
        main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY, new Multitenancy(main));
    }

    private TenantConfig[] getAllTenants() {
        return StorageLayer.getMultitenancyStorage(main).getAllTenants();
    }

    public void refreshTenantsInCoreIfRequired() {
        try {
            TenantConfig[] tenantsFromDb = getAllTenants();
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

    public static boolean addNewOrUpdateAppOrTenant(Main main, TenantConfig sourceTenant, TenantConfig newTenant)
            throws DeletionInProgressException, CannotModifyBaseConfigException, BadPermissionException {

        // first we don't allow changing of core config for base tenant - since that comes from config.yaml file.
        if (newTenant.tenantIdentifier.equals(new TenantIdentifier(null, null, null))) {
            if (newTenant.coreConfig != null && newTenant.coreConfig.entrySet().size() > 0) {
                throw new CannotModifyBaseConfigException();
            }
        }

        // Then we check for permissions based on sourceTenant
        if (!newTenant.tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
            // this means that we are creating a new tenant and must use the public tenant for the current app to do
            // this
            if (!sourceTenant.tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
                throw new BadPermissionException("You must use the public tenantId to add a new tenant to this app");
            }
        } else if (!newTenant.tenantIdentifier.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID)) {
            // this means that we are creating a new app for this connectionuridomain and must use the public app and
            // public tenant for this
            if (!sourceTenant.tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID) &&
                    !sourceTenant.tenantIdentifier.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID)) {
                throw new BadPermissionException("You must use the public tenantId and public appId to add a new app");
            }
        } else if (!newTenant.tenantIdentifier.getConnectionUriDomain()
                .equals(TenantIdentifier.DEFAULT_CONNECTION_URI)) {
            // this means that we are creating a new connectionuridomain, and must use the base tenant for this
            if (!sourceTenant.tenantIdentifier.equals(new TenantIdentifier(null, null, null))) {
                throw new BadPermissionException("You must use the base tenant to create a new connectionUriDomain");
            }
        }

        boolean creationInSharedDbSucceeded = false;
        try {
            StorageLayer.getMultitenancyStorage(main).createTenant(newTenant);
            creationInSharedDbSucceeded = true;
            Multitenancy.getInstance(main).refreshTenantsInCoreIfRequired();
            try {
                StorageLayer.getMultitenancyStorageWithTargetStorage(newTenant.tenantIdentifier, main)
                        .addTenantIdInUserPool(newTenant.tenantIdentifier);
            } catch (TenantOrAppNotFoundException e) {
                // it should never come here, since we just added the tenant above.. but just in case.
                return addNewOrUpdateAppOrTenant(main, sourceTenant, newTenant);
            }
            return true;
        } catch (DuplicateTenantException e) {
            if (!creationInSharedDbSucceeded) {
                try {
                    StorageLayer.getMultitenancyStorage(main).overwriteTenantConfig(newTenant);
                    Multitenancy.getInstance(main).refreshTenantsInCoreIfRequired();

                    // we do this extra step cause if previously an attempt to add a tenant failed midway,
                    // such that the main tenant was added in the user pool, but did not get created
                    // in the tenant specific db (cause it's not happening in a transaction), then we
                    // do this to make it consistent.
                    StorageLayer.getMultitenancyStorageWithTargetStorage(newTenant.tenantIdentifier, main)
                            .addTenantIdInUserPool(newTenant.tenantIdentifier);
                    return false;
                } catch (TenantOrAppNotFoundException ex) {
                    // this can happen cause of a race condition if the tenant was deleted in the middle
                    // of it being recreated.
                    return addNewOrUpdateAppOrTenant(main, sourceTenant, newTenant);
                } catch (DuplicateTenantException ex) {
                    // we treat this as a success
                    return false;
                }
            } else {
                // we ignore this since it should technically never come here cause it means that the
                // creation in the shared db succeeded, but not in the tenant specific db
                // but if it ever does come here, it doesn't really matter anyway.
                return true;
            }
        }
    }

    public static void deleteTenant(Main main, TenantIdentifier tenantIdentifier)
            throws TenantOrAppNotFoundException, CannotDeleteNullTenantException {
        if (tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
            throw new CannotDeleteNullTenantException();
        }
        try {
            StorageLayer.getMultitenancyStorageWithTargetStorage(tenantIdentifier, main)
                    .deleteTenantIdInUserPool(tenantIdentifier);
        } catch (TenantOrAppNotFoundException e) {
            // we ignore this since it may have been that past deletion attempt deleted this successfully,
            // but not from the main table.
        }
        StorageLayer.getMultitenancyStorage(main).deleteTenant(tenantIdentifier);
        Multitenancy.getInstance(main).refreshTenantsInCoreIfRequired();
    }

    public static void deleteApp(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException, CannotDeleteNullAppIdException, BadPermissionException {
        if (tenantIdentifier.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID)) {
            throw new CannotDeleteNullAppIdException();
        }
        if (!tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
            throw new BadPermissionException("Only the public tenantId is allowed to delete this appId");
        }
        if (getAllTenantsForApp(tenantIdentifier, main).length > 1) {
            throw new BadPermissionException(
                    "Please delete all tenants except the public tenant for this app before calling the delete API");
        }
        StorageLayer.getMultitenancyStorage(main).markAppIdAsDeleted(tenantIdentifier.getAppId());
        Multitenancy.getInstance(main).refreshTenantsInCoreIfRequired();
    }

    public static void deleteConnectionUriDomain(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException, CannotDeleteNulConnectionUriDomainException, BadPermissionException {
        if (tenantIdentifier.getConnectionUriDomain().equals(TenantIdentifier.DEFAULT_CONNECTION_URI)) {
            throw new CannotDeleteNulConnectionUriDomainException();
        }
        if (!tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID) &&
                !tenantIdentifier.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID)) {
            throw new BadPermissionException(
                    "Only the public tenantId and public appId is allowed to delete this connectionUriDomain");
        }
        TenantConfig[] tenants = getAllAppsAndTenantsForConnectionUriDomain(tenantIdentifier, main);
        Set<String> uniqueAppIds = new HashSet<>();
        for (TenantConfig t : tenants) {
            uniqueAppIds.add(t.tenantIdentifier.getAppId());
        }

        if (uniqueAppIds.size() > 1) {
            throw new BadPermissionException(
                    "Please delete all apps except the public appID for this connectionUriDomain before calling the " +
                            "delete API.");
        }
        StorageLayer.getMultitenancyStorage(main)
                .markConnectionUriDomainAsDeleted(tenantIdentifier.getConnectionUriDomain());
        Multitenancy.getInstance(main).refreshTenantsInCoreIfRequired();
    }

    public static boolean addUserIdToTenant(Main main, TenantIdentifier sourceTenantIdentifier, String userId,
                                            String newTenantId)
            throws TenantOrAppNotFoundException, UnknownUserIdException {
        TenantIdentifier targetTenantIdentifier = new TenantIdentifier(sourceTenantIdentifier.getConnectionUriDomain(),
                sourceTenantIdentifier.getAppId(), newTenantId);
        if (sourceTenantIdentifier.equals(targetTenantIdentifier)) {
            return false;
        }
        StorageLayer.getMultitenancyStorageWithTargetStorage(sourceTenantIdentifier, main)
                .addUserIdToTenant(targetTenantIdentifier, userId);
        return true;
    }

    public static boolean addRoleToTenant(Main main, TenantIdentifier sourceTenantIdentifier, String role,
                                          String newTenantId)
            throws TenantOrAppNotFoundException, UnknownRoleException {

        TenantIdentifier targetTenantIdentifier = new TenantIdentifier(sourceTenantIdentifier.getConnectionUriDomain(),
                sourceTenantIdentifier.getAppId(), newTenantId);
        if (sourceTenantIdentifier.equals(targetTenantIdentifier)) {
            return false;
        }
        StorageLayer.getMultitenancyStorageWithTargetStorage(sourceTenantIdentifier, main)
                .addRoleToTenant(targetTenantIdentifier, role);
        return true;
    }

    public static TenantConfig getTenantInfo(Main main, TenantIdentifier tenantIdentifier) {
        Multitenancy.getInstance(main).refreshTenantsInCoreIfRequired();
        TenantConfig[] tenants = Multitenancy.getInstance(main).tenantConfigs;
        for (TenantConfig t : tenants) {
            if (t.tenantIdentifier.equals(tenantIdentifier)) {
                return t;
            }
        }
        return null;
    }

    public static TenantConfig[] getAllTenantsForApp(TenantIdentifier tenantIdentifier, Main main)
            throws BadPermissionException {
        if (!tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
            throw new BadPermissionException(
                    "Only the public tenantId is allowed to list all tenants associated with this app");
        }
        Multitenancy.getInstance(main).refreshTenantsInCoreIfRequired();
        TenantConfig[] tenants = Multitenancy.getInstance(main).tenantConfigs;
        List<TenantConfig> tenantList = new ArrayList<>();

        for (TenantConfig t : tenants) {
            if (!t.tenantIdentifier.getAppId().equals(tenantIdentifier.getAppId())) {
                continue;
            }
            tenantList.add(t);
        }

        TenantConfig[] finalResult = new TenantConfig[tenantList.size()];
        for (int i = 0; i < tenantList.size(); i++) {
            finalResult[i] = tenantList.get(i);
        }
        return finalResult;
    }

    public static TenantConfig[] getAllAppsAndTenantsForConnectionUriDomain(TenantIdentifier tenantIdentifier,
                                                                            Main main)
            throws BadPermissionException {
        if (!tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID) &&
                !tenantIdentifier.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID)) {
            throw new BadPermissionException(
                    "Only the public tenantId and public appId is allowed to list all apps associated with this " +
                            "connectionUriDomain");
        }
        Multitenancy.getInstance(main).refreshTenantsInCoreIfRequired();
        TenantConfig[] tenants = Multitenancy.getInstance(main).tenantConfigs;
        List<TenantConfig> tenantList = new ArrayList<>();

        for (TenantConfig t : tenants) {
            if (!t.tenantIdentifier.getConnectionUriDomain().equals(tenantIdentifier.getConnectionUriDomain())) {
                continue;
            }
            tenantList.add(t);
        }

        TenantConfig[] finalResult = new TenantConfig[tenantList.size()];
        for (int i = 0; i < tenantList.size(); i++) {
            finalResult[i] = tenantList.get(i);
        }
        return finalResult;
    }

    public static TenantConfig[] getAllTenants(TenantIdentifier tenantIdentifier, Main main)
            throws BadPermissionException {
        if (!tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID) &&
                !tenantIdentifier.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID) &&
                !tenantIdentifier.getConnectionUriDomain().equals(TenantIdentifier.DEFAULT_CONNECTION_URI)) {
            throw new BadPermissionException(
                    "Only the public tenantId, public appId and default connectionUriDomain is allowed to list all " +
                            "connectionUriDomains and appIds associated with this " +
                            "core");
        }
        Multitenancy.getInstance(main).refreshTenantsInCoreIfRequired();
        return Multitenancy.getInstance(main).tenantConfigs;
    }
}
