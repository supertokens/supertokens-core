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
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.*;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateClientTypeException;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateTenantException;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateThirdPartyIdException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.userroles.exception.UnknownRoleException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.thirdparty.ThirdParty;

import java.io.IOException;
import java.util.*;

public class Multitenancy extends ResourceDistributor.SingletonResource {

    public static boolean addNewOrUpdateAppOrTenant(Main main, TenantIdentifier sourceTenant, TenantConfig newTenant)
            throws DeletionInProgressException, CannotModifyBaseConfigException, BadPermissionException,
            StorageQueryException, FeatureNotEnabledException, IOException, InvalidConfigException,
            InvalidProviderConfigException {

        // TODO: adding a new tenant is not thread safe here - for example, one can add a new connectionuridomain
        //  such that they both point to the same user pool ID by trying to add them in parallel. This is not such
        //  a big issue at the moment, but we want to solve this by taking appropriate database locks on
        //  connectionuridomain, appid and tenantid.

        // first we don't allow changing of core config for base tenant - since that comes from config.yaml file.
        {
            if (newTenant.tenantIdentifier.equals(new TenantIdentifier(null, null, null))) {
                if (newTenant.coreConfig.entrySet().size() > 0) {
                    throw new CannotModifyBaseConfigException();
                }
            } else {
                if (Arrays.stream(FeatureFlag.getInstance(main).getEnabledFeatures())
                        .noneMatch(ee_features -> ee_features == EE_FEATURES.MULTI_TENANCY)) {
                    throw new FeatureNotEnabledException(EE_FEATURES.MULTI_TENANCY);
                }
            }
        }

        // Then we check for permissions based on sourceTenant
        {
            if (!newTenant.tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
                // this means that we are creating a new tenant and must use the public tenant for the current app to do
                // this
                if (!sourceTenant.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
                    throw new BadPermissionException(
                            "You must use the public tenantId to add a new tenant to this app");
                }
                if (!sourceTenant.toAppIdentifier().equals(newTenant.tenantIdentifier.toAppIdentifier())) {
                    throw new BadPermissionException("You must use the same app to create new tenant");
                }
            } else if (!newTenant.tenantIdentifier.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID)) {
                // this means that we are creating a new app for this connectionuridomain and must use the public app
                // and
                // public tenant for this
                if (!sourceTenant.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID) ||
                        !sourceTenant.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID)) {
                    throw new BadPermissionException(
                            "You must use the public tenantId and public appId to add a new app");
                }
                if (!sourceTenant.getConnectionUriDomain().equals(newTenant.tenantIdentifier.getConnectionUriDomain())) {
                    throw new BadPermissionException("You must use the same connection URI domain to create new app");
                }
            } else if (!newTenant.tenantIdentifier.getConnectionUriDomain()
                    .equals(TenantIdentifier.DEFAULT_CONNECTION_URI)) {
                // this means that we are creating a new connectionuridomain, and must use the base tenant for this
                if (!sourceTenant.equals(new TenantIdentifier(null, null, null))) {
                    throw new BadPermissionException(
                            "You must use the base tenant to create a new connectionUriDomain");
                }
            }
        }

        // we check if the core config provided is correct
        {
            TenantConfig[] existingTenants = getAllTenants(new TenantIdentifier(null, null, null), main);
            boolean updated = false;
            for (int i = 0; i < existingTenants.length; i++) {
                if (existingTenants[i].tenantIdentifier.equals(newTenant.tenantIdentifier)) {
                    existingTenants[i] = newTenant;
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                TenantConfig[] oldTenants = existingTenants;
                existingTenants = new TenantConfig[oldTenants.length + 1];
                for (int i = 0; i < oldTenants.length; i++) {
                    existingTenants[i] = oldTenants[i];
                }
                existingTenants[existingTenants.length - 1] = newTenant;
            }
            Map<ResourceDistributor.KeyClass, JsonObject> normalisedConfigs = Config.getNormalisedConfigsForAllTenants(
                    existingTenants,
                    Config.getBaseConfigAsJsonObject(main));
            Config.assertAllTenantConfigsAreValid(main, normalisedConfigs, existingTenants);
        }

        // validate third party config
        {
            if (newTenant.thirdPartyConfig.providers.length > 0) {
                ThirdParty.verifyThirdPartyProvidersArray(newTenant.thirdPartyConfig.providers);
            }
        }

        boolean creationInSharedDbSucceeded = false;
        try {
            StorageLayer.getMultitenancyStorage(main).createTenant(newTenant);
            creationInSharedDbSucceeded = true;
            MultitenancyHelper.getInstance(main).refreshTenantsInCoreIfRequired();
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
                    MultitenancyHelper.getInstance(main).refreshTenantsInCoreIfRequired();

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
                } catch (DuplicateThirdPartyIdException overWriteException) {
                    throw new InvalidProviderConfigException("Duplicate ThirdPartyId was specified in the providers list.");
                } catch (DuplicateClientTypeException overWriteException) {
                    throw new InvalidProviderConfigException("Duplicate clientType was specified in the clients list.");
                }
            } else {
                // we ignore this since it should technically never come here cause it means that the
                // creation in the shared db succeeded, but not in the tenant specific db
                // but if it ever does come here, it doesn't really matter anyway.
                return true;
            }
        } catch (DuplicateThirdPartyIdException e) {
            throw new InvalidProviderConfigException("Duplicate ThirdPartyId was specified in the providers list.");
        } catch (DuplicateClientTypeException e) {
            throw new InvalidProviderConfigException("Duplicate clientType was specified in the clients list.");
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
        MultitenancyHelper.getInstance(main).refreshTenantsInCoreIfRequired();
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
        StorageLayer.getMultitenancyStorage(main).deleteAppId(tenantIdentifier.getAppId());
        MultitenancyHelper.getInstance(main).refreshTenantsInCoreIfRequired();
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
                .deleteConnectionUriDomain(tenantIdentifier.getConnectionUriDomain());
        MultitenancyHelper.getInstance(main).refreshTenantsInCoreIfRequired();
    }

    // TODO: removeUserIdFromTenant
    // TODO: removeRoleFromTenant

    public static boolean addUserIdToTenant(Main main, TenantIdentifier tenantIdentifier, String userId)
            throws TenantOrAppNotFoundException, UnknownUserIdException, StorageQueryException,
            FeatureNotEnabledException {
        if (Arrays.stream(FeatureFlag.getInstance(main).getEnabledFeatures())
                .noneMatch(ee_features -> ee_features == EE_FEATURES.MULTI_TENANCY)) {
            throw new FeatureNotEnabledException(EE_FEATURES.MULTI_TENANCY);
        }

        StorageLayer.getMultitenancyStorageWithTargetStorage(tenantIdentifier, main)
                .addUserIdToTenant(tenantIdentifier, userId);
        return true;
    }

    public static boolean addRoleToTenant(Main main, TenantIdentifier tenantIdentifier, String role)
            throws TenantOrAppNotFoundException, UnknownRoleException, StorageQueryException,
            FeatureNotEnabledException {

        if (Arrays.stream(FeatureFlag.getInstance(main).getEnabledFeatures())
                .noneMatch(ee_features -> ee_features == EE_FEATURES.MULTI_TENANCY)) {
            throw new FeatureNotEnabledException(EE_FEATURES.MULTI_TENANCY);
        }

        StorageLayer.getMultitenancyStorageWithTargetStorage(tenantIdentifier, main)
                .addRoleToTenant(tenantIdentifier, role);
        return true;
    }

    public static TenantConfig getTenantInfo(Main main, TenantIdentifier tenantIdentifier) {
        // we do not refresh the tenant list here cause this function is called
        // often from all the APIs and anyway, we have a cronjob that refreshes this list
        // regularly.
        TenantConfig[] tenants = MultitenancyHelper.getInstance(main).getAllTenants();
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
        MultitenancyHelper.getInstance(main).refreshTenantsInCoreIfRequired();
        TenantConfig[] tenants = MultitenancyHelper.getInstance(main).getAllTenants();
        List<TenantConfig> tenantList = new ArrayList<>();

        for (TenantConfig t : tenants) {
            if (!t.tenantIdentifier.toAppIdentifier().equals(tenantIdentifier.toAppIdentifier())) {
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
        MultitenancyHelper.getInstance(main).refreshTenantsInCoreIfRequired();
        TenantConfig[] tenants = MultitenancyHelper.getInstance(main).getAllTenants();
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
        if (!tenantIdentifier.equals(new TenantIdentifier(null, null, null))) {
            throw new BadPermissionException(
                    "Only the public tenantId, public appId and default connectionUriDomain is allowed to list all " +
                            "connectionUriDomains and appIds associated with this " +
                            "core");
        }
        MultitenancyHelper.getInstance(main).refreshTenantsInCoreIfRequired();
        return MultitenancyHelper.getInstance(main).getAllTenants();
    }
}
