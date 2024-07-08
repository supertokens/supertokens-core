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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.*;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.MultitenancyStorage;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateClientTypeException;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateTenantException;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateThirdPartyIdException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.multitenancy.sqlStorage.MultitenancySQLStorage;
import io.supertokens.pluginInterface.passwordless.exception.DuplicatePhoneNumberException;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateThirdPartyUserException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.thirdparty.ThirdParty;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.*;

public class Multitenancy extends ResourceDistributor.SingletonResource {

    /*
        Permissions for Multitenancy CRUD operations:
            Create or update - Parent and self can perform the operation
            List - Queried from parent -> returns parent + all the children
            Delete - Only parent can delete a child

        `checkPermissionsForCreateOrUpdate` below checks for the create/update API.
        For the list and delete APIs, checks are implemented in their respective API implementations
     */

    public static void checkPermissionsForCreateOrUpdate(Main main, TenantIdentifier sourceTenant,
                                                         TenantIdentifier targetTenant)
            throws BadPermissionException, CannotModifyBaseConfigException, FeatureNotEnabledException,
            TenantOrAppNotFoundException, StorageQueryException, InvalidConfigException,
            InvalidProviderConfigException {

        {
            if (!targetTenant.equals(new TenantIdentifier(null, null, null))) {
                if (Arrays.stream(FeatureFlag.getInstance(main, new AppIdentifier(null, null)).getEnabledFeatures())
                        .noneMatch(ee_features -> ee_features == EE_FEATURES.MULTI_TENANCY)) {
                    throw new FeatureNotEnabledException(EE_FEATURES.MULTI_TENANCY);
                }
            }
        }

        // Then we check for permissions based on sourceTenant
        {
            if (!targetTenant.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
                // this means that we are creating or updating a tenant and must use the public or same tenant
                // to do this
                if (!sourceTenant.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)
                        && !sourceTenant.getTenantId().equals(targetTenant.getTenantId())) {
                    throw new BadPermissionException(
                            "You must use the public or same tenant to add/update a tenant");
                }
                if (!sourceTenant.toAppIdentifier().equals(targetTenant.toAppIdentifier())) {
                    throw new BadPermissionException("You must use the same app to create/update a tenant");
                }
            } else if (!targetTenant.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID)) {
                // this means that we are creating a new app for this connectionuridomain and must use the public or
                // same app and public tenant for this
                if (!sourceTenant.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)
                        || (!sourceTenant.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID) &&
                        !sourceTenant.getAppId().equals(targetTenant.getAppId()))) {
                    throw new BadPermissionException(
                            "You must use the public or same app to add/update an app");
                }
                if (!sourceTenant.getConnectionUriDomain()
                        .equals(targetTenant.getConnectionUriDomain())) {
                    throw new BadPermissionException(
                            "You must use the same connection URI domain to create/update an app");
                }
            } else if (!targetTenant.getConnectionUriDomain()
                    .equals(TenantIdentifier.DEFAULT_CONNECTION_URI)) {
                // this means that we are creating a new connectionuridomain, and must use the base tenant for this
                if (!sourceTenant.equals(new TenantIdentifier(null, null, null))
                        && !sourceTenant.getConnectionUriDomain().equals(targetTenant.getConnectionUriDomain())) {
                    throw new BadPermissionException(
                            "You must use the default or same connectionUriDomain to create/update a " +
                                    "connectionUriDomain");
                }
            }
        }
    }

    private static void validateConfigJsonForInvalidKeys(Main main, JsonObject coreConfig)
            throws InvalidConfigException {
        Set<String> coreFields = CoreConfig.getValidFields();
        Set<String> storageFields = StorageLayer.getBaseStorage(main).getValidFieldsInConfig();

        for (Map.Entry<String, JsonElement> entry : coreConfig.entrySet()) {
            if (!coreFields.contains(entry.getKey()) && !storageFields.contains(entry.getKey())) {
                throw new InvalidConfigException("Invalid config key: " + entry.getKey());
            }
        }
    }

    private static void validateTenantConfig(Main main, TenantConfig targetTenantConfig,
                                             boolean shouldPreventProtecterdConfigUpdate,
                                             boolean skipThirdPartyConfigValidation)
            throws IOException, InvalidConfigException, InvalidProviderConfigException, BadPermissionException,
            TenantOrAppNotFoundException, CannotModifyBaseConfigException {

        // first we don't allow changing of core config for base tenant - since that comes from config.yaml file.
        if (targetTenantConfig.tenantIdentifier.equals(new TenantIdentifier(null, null, null))) {
            if (targetTenantConfig.coreConfig.entrySet().size() > 0) {
                throw new CannotModifyBaseConfigException();
            }
        }

        // Verify that the keys in the coreConfig is valid
        validateConfigJsonForInvalidKeys(main, targetTenantConfig.coreConfig);

        // we check if the core config provided is correct
        {
            if (shouldPreventProtecterdConfigUpdate) {

                JsonObject currentConfig = new JsonObject();
                TenantConfig tenantInfo = getTenantInfo(main, targetTenantConfig.tenantIdentifier);
                if (tenantInfo != null) {
                    currentConfig = tenantInfo.coreConfig;
                }

                for (String s : StorageLayer.getStorage(new TenantIdentifier(null, null, null), main)
                        .getProtectedConfigsFromSuperTokensSaaSUsers()) {
                    if (targetTenantConfig.coreConfig.has(s) &&
                            !targetTenantConfig.coreConfig.get(s).equals(currentConfig.get(s))) {
                        throw new BadPermissionException("Not allowed to modify DB related configs.");
                    }
                }

                for (String s : CoreConfig.PROTECTED_CONFIGS) {
                    if (targetTenantConfig.coreConfig.has(s) &&
                            !targetTenantConfig.coreConfig.get(s).equals(currentConfig.get(s))) {
                        throw new BadPermissionException("Not allowed to modify protected configs.");
                    }
                }
            }

            TenantConfig[] existingTenants = getAllTenants(main);
            boolean updated = false;
            for (int i = 0; i < existingTenants.length; i++) {
                if (existingTenants[i].tenantIdentifier.equals(targetTenantConfig.tenantIdentifier)) {
                    existingTenants[i] = targetTenantConfig;
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
                existingTenants[existingTenants.length - 1] = targetTenantConfig;
            }

            Map<ResourceDistributor.KeyClass, JsonObject> normalisedConfigs = Config.getNormalisedConfigsForAllTenants(
                    existingTenants,
                    Config.getBaseConfigAsJsonObject(main));
            Config.assertAllTenantConfigsAreValid(main, normalisedConfigs, existingTenants);
        }

        // validate third party config
        if (!skipThirdPartyConfigValidation) {
            ThirdParty.verifyThirdPartyProvidersArray(targetTenantConfig.thirdPartyConfig.providers);
        }
    }

    @TestOnly
    public static boolean addNewOrUpdateAppOrTenant(Main main, TenantIdentifier sourceTenant, TenantConfig newTenant)
            throws CannotModifyBaseConfigException, BadPermissionException,
            StorageQueryException, FeatureNotEnabledException, IOException, InvalidConfigException,
            InvalidProviderConfigException, TenantOrAppNotFoundException {
        checkPermissionsForCreateOrUpdate(main, sourceTenant, newTenant.tenantIdentifier);
        return addNewOrUpdateAppOrTenant(main, newTenant, false);
    }

    @TestOnly
    public static boolean addNewOrUpdateAppOrTenant(Main main, TenantConfig newTenant,
                                                    boolean shouldPreventDbConfigUpdate)
            throws InvalidProviderConfigException, StorageQueryException, FeatureNotEnabledException,
            TenantOrAppNotFoundException, IOException, InvalidConfigException, CannotModifyBaseConfigException,
            BadPermissionException {
        return addNewOrUpdateAppOrTenant(main, newTenant, shouldPreventDbConfigUpdate, false, true);
    }


    public static boolean addNewOrUpdateAppOrTenant(Main main, TenantConfig newTenant,
                                                    boolean shouldPreventProtectedConfigUpdate,
                                                    boolean skipThirdPartyConfigValidation,
                                                    boolean forceReloadResources)
            throws CannotModifyBaseConfigException, BadPermissionException,
            StorageQueryException, FeatureNotEnabledException, IOException, InvalidConfigException,
            InvalidProviderConfigException, TenantOrAppNotFoundException {

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL) {
            if (newTenant.tenantIdentifier.equals(TenantIdentifier.BASE_TENANT)) {
                return true;
            } else {
                throw new UnsupportedOperationException();
            }
        }

        // TODO: adding a new tenant is not thread safe here - for example, one can add a new connectionuridomain
        //  such that they both point to the same user pool ID by trying to add them in parallel. This is not such
        //  a big issue at the moment, but we want to solve this by taking appropriate database locks on
        //  connectionuridomain, appid and tenantid.

        validateTenantConfig(main, newTenant, shouldPreventProtectedConfigUpdate, skipThirdPartyConfigValidation);

        boolean creationInSharedDbSucceeded = false;
        List<TenantIdentifier> tenantsThatChanged = new ArrayList<>();
        try {
            StorageLayer.getMultitenancyStorage(main).createTenant(newTenant);
            creationInSharedDbSucceeded = true;
            // we do not want to refresh the resources for this new tenant here cause
            // it will cause creation of signing keys in the key_value table, which depends on
            // the tenant being there in the tenants table. But that insertion is done in the addTenantIdInUserPool
            // function below. So in order to actually refresh the resources, we have a finally block here which
            // calls the forceReloadAllResources function.
            tenantsThatChanged = MultitenancyHelper.getInstance(main)
                    .refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(false);
            try {
                ((MultitenancyStorage) StorageLayer.getStorage(newTenant.tenantIdentifier, main))
                        .addTenantIdInTargetStorage(newTenant.tenantIdentifier);
            } catch (TenantOrAppNotFoundException e) {
                // it should never come here, since we just added the tenant above.. but just in case.
                return addNewOrUpdateAppOrTenant(main, newTenant, shouldPreventProtectedConfigUpdate,
                        skipThirdPartyConfigValidation, forceReloadResources);
            }
            return true;
        } catch (DuplicateTenantException e) {
            if (!creationInSharedDbSucceeded) {
                try {
                    StorageLayer.getMultitenancyStorage(main).overwriteTenantConfig(newTenant);
                    tenantsThatChanged = MultitenancyHelper.getInstance(main)
                            .refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(false);

                    // we do this extra step cause if previously an attempt to add a tenant failed midway,
                    // such that the main tenant was added in the user pool, but did not get created
                    // in the tenant specific db (cause it's not happening in a transaction), then we
                    // do this to make it consistent.
                    ((MultitenancyStorage) StorageLayer.getStorage(newTenant.tenantIdentifier, main))
                            .addTenantIdInTargetStorage(newTenant.tenantIdentifier);
                    return false;
                } catch (TenantOrAppNotFoundException ex) {
                    // this can happen cause of a race condition if the tenant was deleted in the middle
                    // of it being recreated.
                    return addNewOrUpdateAppOrTenant(main, newTenant, shouldPreventProtectedConfigUpdate,
                            skipThirdPartyConfigValidation, forceReloadResources);
                } catch (DuplicateTenantException ex) {
                    // we treat this as a success
                    return false;
                } catch (DuplicateThirdPartyIdException overWriteException) {
                    throw new InvalidProviderConfigException(
                            "Duplicate ThirdPartyId was specified in the providers list.");
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
        } finally {
            if (forceReloadResources) {
                MultitenancyHelper.getInstance(main).forceReloadAllResources(tenantsThatChanged);
            }
        }
    }

    public static boolean deleteTenant(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException, CannotDeleteNullTenantException, StorageQueryException {
        if (tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
            throw new CannotDeleteNullTenantException();
        }
        try {
            ((MultitenancyStorage) StorageLayer.getStorage(tenantIdentifier, main))
                    .deleteTenantIdInTargetStorage(tenantIdentifier);
        } catch (TenantOrAppNotFoundException e) {
            // we ignore this since it may have been that past deletion attempt deleted this successfully,
            // but not from the main table.
        }
        boolean didExist = StorageLayer.getMultitenancyStorage(main).deleteTenantInfoInBaseStorage(tenantIdentifier);
        MultitenancyHelper.getInstance(main).refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(true);
        return didExist;
    }

    public static boolean deleteApp(AppIdentifier appIdentifier, Main main)
            throws TenantOrAppNotFoundException, CannotDeleteNullAppIdException, BadPermissionException,
            StorageQueryException {
        if (appIdentifier.getAppId().equals(AppIdentifier.DEFAULT_APP_ID)) {
            throw new CannotDeleteNullAppIdException();
        }
        if (getAllTenantsForApp(appIdentifier, main).length > 1) {
            throw new BadPermissionException(
                    "Please delete all tenants except the public tenant for this app before calling the delete API");
        }
        try {
            ((MultitenancyStorage) StorageLayer.getStorage(appIdentifier.getAsPublicTenantIdentifier(), main))
                    .deleteTenantIdInTargetStorage(appIdentifier.getAsPublicTenantIdentifier());
        } catch (TenantOrAppNotFoundException e) {
            // we ignore this since it may have been that past deletion attempt deleted this successfully,
            // but not from the main table.
        }
        boolean didExist = StorageLayer.getMultitenancyStorage(main).deleteAppInfoInBaseStorage(appIdentifier);
        MultitenancyHelper.getInstance(main).refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(true);
        return didExist;
    }

    public static boolean deleteConnectionUriDomain(String connectionUriDomain, Main main)
            throws TenantOrAppNotFoundException, CannotDeleteNullConnectionUriDomainException, BadPermissionException,
            StorageQueryException {
        if (connectionUriDomain == null || connectionUriDomain.equals(TenantIdentifier.DEFAULT_CONNECTION_URI)) {
            throw new CannotDeleteNullConnectionUriDomainException();
        }
        TenantConfig[] tenants = getAllAppsAndTenantsForConnectionUriDomain(connectionUriDomain, main);
        Set<String> uniqueAppIds = new HashSet<>();
        for (TenantConfig t : tenants) {
            uniqueAppIds.add(t.tenantIdentifier.getAppId());
        }

        if (uniqueAppIds.size() > 1) {
            throw new BadPermissionException(
                    "Please delete all apps except the public appID for this connectionUriDomain before calling the " +
                            "delete API.");
        }
        try {
            if (StorageLayer.getStorage(
                    new TenantIdentifier(connectionUriDomain, null, null), main) == StorageLayer.getBaseStorage(main)) {
                // This means that the CUD does not exist, and nothing needs to be done
                return false;
            }

            ((MultitenancyStorage) StorageLayer.getStorage(
                    new TenantIdentifier(connectionUriDomain, null, null), main))
                    .deleteTenantIdInTargetStorage(new TenantIdentifier(connectionUriDomain, null, null));
        } catch (TenantOrAppNotFoundException e) {
            // we ignore this since it may have been that past deletion attempt deleted this successfully,
            // but not from the main table.
        }
        boolean didExist = StorageLayer.getMultitenancyStorage(main)
                .deleteConnectionUriDomainInfoInBaseStorage(connectionUriDomain);
        MultitenancyHelper.getInstance(main).refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(true);
        return didExist;
    }

    public static boolean addUserIdToTenant(Main main, TenantIdentifier tenantIdentifier, Storage storage,
                                            String userId)
            throws TenantOrAppNotFoundException, UnknownUserIdException, StorageQueryException,
            FeatureNotEnabledException, DuplicateEmailException, DuplicatePhoneNumberException,
            DuplicateThirdPartyUserException, AnotherPrimaryUserWithPhoneNumberAlreadyExistsException,
            AnotherPrimaryUserWithEmailAlreadyExistsException,
            AnotherPrimaryUserWithThirdPartyInfoAlreadyExistsException {
        if (Arrays.stream(FeatureFlag.getInstance(main, new AppIdentifier(null, null)).getEnabledFeatures())
                .noneMatch(ee_features -> ee_features == EE_FEATURES.MULTI_TENANCY)) {
            throw new FeatureNotEnabledException(EE_FEATURES.MULTI_TENANCY);
        }

        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        try {
            return authRecipeStorage.startTransaction(con -> {
                String tenantId = tenantIdentifier.getTenantId();
                AuthRecipeUserInfo userToAssociate = authRecipeStorage.getPrimaryUserById_Transaction(
                        tenantIdentifier.toAppIdentifier(), con, userId);

                if (userToAssociate != null && userToAssociate.isPrimaryUser) {
                    Set<String> emails = new HashSet<>();
                    Set<String> phoneNumbers = new HashSet<>();
                    Set<LoginMethod.ThirdParty> thirdParties = new HashSet<>();

                    // Loop through all the emails, phoneNumbers and thirdPartyInfos and check for conflicts
                    for (LoginMethod lM : userToAssociate.loginMethods) {
                        if (lM.email != null) {
                            emails.add(lM.email);
                        }
                        if (lM.phoneNumber != null) {
                            phoneNumbers.add(lM.phoneNumber);
                        }
                        if (lM.thirdParty != null) {
                            thirdParties.add(lM.thirdParty);
                        }
                    }

                    for (String email : emails) {
                        AuthRecipeUserInfo[] usersWithSameEmail = authRecipeStorage.listPrimaryUsersByEmail_Transaction(
                                tenantIdentifier.toAppIdentifier(), con, email);
                        for (AuthRecipeUserInfo userWithSameEmail : usersWithSameEmail) {
                            if (userWithSameEmail.getSupertokensUserId()
                                    .equals(userToAssociate.getSupertokensUserId())) {
                                continue; // it's the same user, no need to check anything
                            }
                            if (userWithSameEmail.isPrimaryUser && userWithSameEmail.tenantIds.contains(tenantId) &&
                                    !userWithSameEmail.getSupertokensUserId().equals(userId)) {
                                for (LoginMethod lm1 : userWithSameEmail.loginMethods) {
                                    if (lm1.tenantIds.contains(tenantId)) {
                                        for (LoginMethod lm2 : userToAssociate.loginMethods) {
                                            if (lm1.recipeId.equals(lm2.recipeId) && email.equals(lm1.email) &&
                                                    lm1.email.equals(lm2.email)) {
                                                throw new StorageTransactionLogicException(
                                                        new DuplicateEmailException());
                                            }
                                        }
                                    }
                                }
                                throw new StorageTransactionLogicException(
                                        new AnotherPrimaryUserWithEmailAlreadyExistsException(
                                                userWithSameEmail.getSupertokensUserId()));
                            }
                        }
                    }

                    for (String phoneNumber : phoneNumbers) {
                        AuthRecipeUserInfo[] usersWithSamePhoneNumber =
                                authRecipeStorage.listPrimaryUsersByPhoneNumber_Transaction(
                                        tenantIdentifier.toAppIdentifier(), con, phoneNumber);
                        for (AuthRecipeUserInfo userWithSamePhoneNumber : usersWithSamePhoneNumber) {
                            if (userWithSamePhoneNumber.getSupertokensUserId()
                                    .equals(userToAssociate.getSupertokensUserId())) {
                                continue; // it's the same user, no need to check anything
                            }
                            if (userWithSamePhoneNumber.tenantIds.contains(tenantId) &&
                                    !userWithSamePhoneNumber.getSupertokensUserId().equals(userId)) {
                                for (LoginMethod lm1 : userWithSamePhoneNumber.loginMethods) {
                                    if (lm1.tenantIds.contains(tenantId)) {
                                        for (LoginMethod lm2 : userToAssociate.loginMethods) {
                                            if (lm1.recipeId.equals(lm2.recipeId) &&
                                                    phoneNumber.equals(lm1.phoneNumber) &&
                                                    lm1.phoneNumber.equals(lm2.phoneNumber)) {
                                                throw new StorageTransactionLogicException(
                                                        new DuplicatePhoneNumberException());
                                            }
                                        }
                                    }
                                }
                                throw new StorageTransactionLogicException(
                                        new AnotherPrimaryUserWithPhoneNumberAlreadyExistsException(
                                                userWithSamePhoneNumber.getSupertokensUserId()));
                            }
                        }
                    }

                    for (LoginMethod.ThirdParty tp : thirdParties) {
                        AuthRecipeUserInfo[] usersWithSameThirdPartyInfo =
                                authRecipeStorage.listPrimaryUsersByThirdPartyInfo_Transaction(
                                        tenantIdentifier.toAppIdentifier(), con, tp.id, tp.userId);
                        for (AuthRecipeUserInfo userWithSameThirdPartyInfo : usersWithSameThirdPartyInfo) {
                            if (userWithSameThirdPartyInfo.getSupertokensUserId()
                                    .equals(userToAssociate.getSupertokensUserId())) {
                                continue; // it's the same user, no need to check anything
                            }
                            if (userWithSameThirdPartyInfo.tenantIds.contains(tenantId) &&
                                    !userWithSameThirdPartyInfo.getSupertokensUserId().equals(userId)) {
                                for (LoginMethod lm1 : userWithSameThirdPartyInfo.loginMethods) {
                                    if (lm1.tenantIds.contains(tenantId)) {
                                        for (LoginMethod lm2 : userToAssociate.loginMethods) {
                                            if (lm1.recipeId.equals(lm2.recipeId) && tp.equals(lm1.thirdParty) &&
                                                    lm1.thirdParty.equals(lm2.thirdParty)) {
                                                throw new StorageTransactionLogicException(
                                                        new DuplicateThirdPartyUserException());
                                            }
                                        }
                                    }
                                }

                                throw new StorageTransactionLogicException(
                                        new AnotherPrimaryUserWithThirdPartyInfoAlreadyExistsException(
                                                userWithSameThirdPartyInfo.getSupertokensUserId()));
                            }
                        }
                    }
                }

                // userToAssociate may be null if the user is not associated to any tenants, we can still try and
                // associate it. This happens only in CDI 3.0 where we allow disassociation from all tenants
                // This will not happen in CDI >= 4.0 because we will not allow disassociation from all tenants
                try {
                    boolean result = ((MultitenancySQLStorage) storage).addUserIdToTenant_Transaction(tenantIdentifier,
                            con, userId);
                    authRecipeStorage.commitTransaction(con);
                    return result;
                } catch (TenantOrAppNotFoundException | UnknownUserIdException | DuplicatePhoneNumberException |
                         DuplicateThirdPartyUserException | DuplicateEmailException e) {
                    throw new StorageTransactionLogicException(e);
                }
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof DuplicateEmailException) {
                throw (DuplicateEmailException) e.actualException;
            } else if (e.actualException instanceof DuplicatePhoneNumberException) {
                throw (DuplicatePhoneNumberException) e.actualException;
            } else if (e.actualException instanceof DuplicateThirdPartyUserException) {
                throw (DuplicateThirdPartyUserException) e.actualException;
            } else if (e.actualException instanceof TenantOrAppNotFoundException) {
                throw (TenantOrAppNotFoundException) e.actualException;
            } else if (e.actualException instanceof UnknownUserIdException) {
                throw (UnknownUserIdException) e.actualException;
            } else if (e.actualException instanceof AnotherPrimaryUserWithPhoneNumberAlreadyExistsException) {
                throw (AnotherPrimaryUserWithPhoneNumberAlreadyExistsException) e.actualException;
            } else if (e.actualException instanceof AnotherPrimaryUserWithEmailAlreadyExistsException) {
                throw (AnotherPrimaryUserWithEmailAlreadyExistsException) e.actualException;
            } else if (e.actualException instanceof AnotherPrimaryUserWithThirdPartyInfoAlreadyExistsException) {
                throw (AnotherPrimaryUserWithThirdPartyInfoAlreadyExistsException) e.actualException;
            }
            throw new StorageQueryException(e.actualException);
        }
    }

    public static boolean removeUserIdFromTenant(Main main, TenantIdentifier tenantIdentifier, Storage storage,
                                                 String userId, String externalUserId)
            throws FeatureNotEnabledException, TenantOrAppNotFoundException, StorageQueryException,
            UnknownUserIdException {
        if (Arrays.stream(FeatureFlag.getInstance(main, new AppIdentifier(null, null)).getEnabledFeatures())
                .noneMatch(ee_features -> ee_features == EE_FEATURES.MULTI_TENANCY)) {
            throw new FeatureNotEnabledException(EE_FEATURES.MULTI_TENANCY);
        }

        boolean finalDidExist = false;
        boolean didExist = AuthRecipe.deleteNonAuthRecipeUser(tenantIdentifier, storage,
                externalUserId == null ? userId : externalUserId);
        finalDidExist = finalDidExist || didExist;

        didExist = StorageUtils.getMultitenancyStorage(storage)
                .removeUserIdFromTenant(tenantIdentifier, userId);
        finalDidExist = finalDidExist || didExist;

        return finalDidExist;
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

    public static TenantConfig[] getAllTenantsForApp(AppIdentifier appIdentifier, Main main) {
        MultitenancyHelper.getInstance(main).refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(true);
        TenantConfig[] tenants = MultitenancyHelper.getInstance(main).getAllTenants();
        List<TenantConfig> tenantList = new ArrayList<>();

        for (TenantConfig t : tenants) {
            if (!t.tenantIdentifier.toAppIdentifier().equals(appIdentifier)) {
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

    public static TenantConfig[] getAllAppsAndTenantsForConnectionUriDomain(String connectionUriDomain, Main main) {
        if (connectionUriDomain == null) {
            connectionUriDomain = TenantIdentifier.DEFAULT_CONNECTION_URI;
        }
        MultitenancyHelper.getInstance(main).refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(true);
        TenantConfig[] tenants = MultitenancyHelper.getInstance(main).getAllTenants();
        List<TenantConfig> tenantList = new ArrayList<>();

        for (TenantConfig t : tenants) {
            if (!t.tenantIdentifier.getConnectionUriDomain().equals(connectionUriDomain)) {
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

    public static TenantConfig[] getAllTenants(Main main) {
        MultitenancyHelper.getInstance(main).refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(true);
        return MultitenancyHelper.getInstance(main).getAllTenants();
    }

    public static void saveWebsiteAndAPIDomainForApp(Storage storage, AppIdentifier appIdentifier, String websiteDomain,
                                                     String apiDomain)
            throws StorageQueryException, TenantOrAppNotFoundException {
        if (websiteDomain != null) {
            storage.setKeyValue(appIdentifier.getAsPublicTenantIdentifier(), "websiteDomain",
                    new KeyValueInfo(websiteDomain, System.currentTimeMillis()));
        }
        if (apiDomain != null) {
            storage.setKeyValue(appIdentifier.getAsPublicTenantIdentifier(), "apiDomain",
                    new KeyValueInfo(apiDomain, System.currentTimeMillis()));
        }
    }

    public static String getWebsiteDomain(Storage storage, AppIdentifier appIdentifier)
            throws StorageQueryException, TenantOrAppNotFoundException {
        KeyValueInfo websiteDomain = storage.getKeyValue(appIdentifier.getAsPublicTenantIdentifier(), "websiteDomain");
        return websiteDomain == null ? null : websiteDomain.value;
    }

    public static String getAPIDomain(Storage storage, AppIdentifier appIdentifier)
            throws StorageQueryException, TenantOrAppNotFoundException {
        KeyValueInfo apiDomain = storage.getKeyValue(appIdentifier.getAsPublicTenantIdentifier(), "apiDomain");
        return apiDomain == null ? null : apiDomain.value;
    }
}
