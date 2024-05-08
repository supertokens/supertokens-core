/*
 *    Copyright (c) 2024. VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.cronjobs.bulkimport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.exception.AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.InputUserIdIsNotAPrimaryUserException;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithPrimaryUserIdException;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.bulkimport.BulkImportUserUtils;
import io.supertokens.bulkimport.exceptions.InvalidBulkImportDataException;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.EmailPassword.ImportUserResponse;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.AnotherPrimaryUserWithEmailAlreadyExistsException;
import io.supertokens.multitenancy.exception.AnotherPrimaryUserWithPhoneNumberAlreadyExistsException;
import io.supertokens.multitenancy.exception.AnotherPrimaryUserWithThirdPartyInfoAlreadyExistsException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.exceptions.RestartFlowException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage.BULK_IMPORT_USER_STATUS;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.TotpDevice;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.UserRole;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportSQLStorage;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.pluginInterface.exceptions.DbInitException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicatePhoneNumberException;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.pluginInterface.sqlStorage.TransactionConnection;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateThirdPartyUserException;
import io.supertokens.pluginInterface.totp.exception.DeviceAlreadyExistsException;
import io.supertokens.pluginInterface.useridmapping.exception.UnknownSuperTokensUserIdException;
import io.supertokens.pluginInterface.useridmapping.exception.UserIdMappingAlreadyExistsException;
import io.supertokens.pluginInterface.userroles.exception.UnknownRoleException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.thirdparty.ThirdParty.SignInUpResponse;
import io.supertokens.totp.Totp;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.userroles.UserRoles;
import jakarta.servlet.ServletException;

public class ProcessBulkImportUsers extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.ee.cronjobs.ProcessBulkImportUsers";
    private Map<String, SQLStorage> userPoolToStorageMap = new HashMap<>();

    private ProcessBulkImportUsers(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("ProcessBulkImportUsers", main, tenantsInfo, true);
    }

    public static ProcessBulkImportUsers init(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        return (ProcessBulkImportUsers) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new ProcessBulkImportUsers(main, tenantsInfo));
    }

    @Override
    protected void doTaskPerApp(AppIdentifier app)
            throws TenantOrAppNotFoundException, StorageQueryException, InvalidConfigException, IOException,
            DbInitException {

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        BulkImportSQLStorage bulkImportSQLStorage = (BulkImportSQLStorage) StorageLayer
                .getStorage(app.getAsPublicTenantIdentifier(), main);

        List<BulkImportUser> users = bulkImportSQLStorage.getBulkImportUsersAndChangeStatusToProcessing(app,
                BulkImport.PROCESS_USERS_BATCH_SIZE);

        String[] allUserRoles = StorageUtils.getUserRolesStorage(bulkImportSQLStorage).getRoles(app);
        BulkImportUserUtils bulkImportUserUtils = new BulkImportUserUtils(allUserRoles);

        for (BulkImportUser user : users) {
            processUser(app, user, bulkImportUserUtils, bulkImportSQLStorage);
        }
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        return BulkImport.PROCESS_USERS_INTERVAL_SECONDS;
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        if (Main.isTesting) {
            Integer waitTime = CronTaskTest.getInstance(main).getInitialWaitTimeInSeconds(RESOURCE_KEY);
            if (waitTime != null) {
                return waitTime;
            }
        }
        return 0;
    }

    private synchronized Storage getProxyStorage(TenantIdentifier tenantIdentifier)
            throws InvalidConfigException, IOException, TenantOrAppNotFoundException, DbInitException, StorageQueryException {
        String userPoolId = StorageLayer.getStorage(tenantIdentifier, main).getUserPoolId();
        if (userPoolToStorageMap.containsKey(userPoolId)) {
            return userPoolToStorageMap.get(userPoolId);
        }

        TenantConfig[] allTenants = Multitenancy.getAllTenants(main);

        Map<ResourceDistributor.KeyClass, JsonObject> normalisedConfigs = Config.getNormalisedConfigsForAllTenants(
                allTenants,
                Config.getBaseConfigAsJsonObject(main));

        for (ResourceDistributor.KeyClass key : normalisedConfigs.keySet()) {
            if (key.getTenantIdentifier().equals(tenantIdentifier)) {
                SQLStorage bulkImportProxyStorage = (SQLStorage) StorageLayer.getNewBulkImportProxyStorageInstance(main,
                        normalisedConfigs.get(key), tenantIdentifier, true);

                userPoolToStorageMap.put(userPoolId, bulkImportProxyStorage);
                bulkImportProxyStorage.initStorage(false, new ArrayList<>());
                // `BulkImportProxyStorage` uses `BulkImportProxyConnection`, which overrides the `.commit()` method on the Connection object.
                // The `initStorage()` method runs `select * from table_name limit 1` queries to check if the tables exist but these queries
                // don't get committed due to the overridden `.commit()`, so we need to manually commit the transaction to remove any locks on the tables.

                // Without this commit, a call to `select * from bulk_import_users limit 1` in `doesTableExist()` locks the `bulk_import_users` table,
                // causing other queries to stall indefinitely.
                bulkImportProxyStorage.commitTransactionForBulkImportProxyStorage();
                return bulkImportProxyStorage;
            }
        }
        throw new TenantOrAppNotFoundException(tenantIdentifier);
    }

    public Storage[] getAllProxyStoragesForApp(Main main, AppIdentifier appIdentifier)
            throws TenantOrAppNotFoundException, InvalidConfigException, IOException, DbInitException, StorageQueryException {
        List<Storage> allProxyStorages = new ArrayList<>();

        TenantConfig[] tenantConfigs = Multitenancy.getAllTenantsForApp(appIdentifier, main);
        for (TenantConfig tenantConfig : tenantConfigs) {
            allProxyStorages.add(getProxyStorage(tenantConfig.tenantIdentifier));
        }
        return allProxyStorages.toArray(new Storage[0]);
    }

    private void closeAllProxyStorages() throws StorageQueryException {
        for (SQLStorage storage : userPoolToStorageMap.values()) {
            storage.closeConnectionForBulkImportProxyStorage();
            storage.close();
        }
        userPoolToStorageMap.clear();
    }

    private void processUser(AppIdentifier appIdentifier, BulkImportUser user, BulkImportUserUtils bulkImportUserUtils,
            BulkImportSQLStorage baseTenantStorage)
            throws TenantOrAppNotFoundException, StorageQueryException, InvalidConfigException, IOException,
            DbInitException {

        try {
            if (Main.isTesting && Main.isTesting_skipBulkImportUserValidationInCronJob) {
                // Skip validation when the flag is enabled during testing
            } else {
                // Validate the user
                bulkImportUserUtils.createBulkImportUserFromJSON(main, appIdentifier, user.toJsonObject(), user.id);
            }

            // Since all the tenants of a user must share the storage, we will just use the
            // storage of the first tenantId of the first loginMethod

            TenantIdentifier firstTenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                    appIdentifier.getAppId(), user.loginMethods.get(0).tenantIds.get(0));

            SQLStorage bulkImportProxyStorage = (SQLStorage) getProxyStorage(firstTenantIdentifier);

            LoginMethod primaryLM = getPrimaryLoginMethod(user);

            AuthRecipeSQLStorage authRecipeSQLStorage = (AuthRecipeSQLStorage) getProxyStorage(firstTenantIdentifier);

            /*
            * We use two separate storage instances: one for importing the user and another for managing bulk_import_users entries. 
            * This is necessary because the bulk_import_users entries are always in the public tenant storage, 
            * but the actual user data could be in a different storage.
            * 
            * If transactions are committed individually, in this order:
            * 1. Commit the transaction that imports the user.
            * 2. Commit the transaction that deletes the corresponding bulk import entry.
            *
            * There's a risk where the first commit succeeds, but the second fails. This creates a situation where 
            * the bulk import entry is re-processed, even though the user has already been imported into the database.
            *
            * To resolve this, we added a `primaryUserId` field to the `bulk_import_users` table.
            * The processing logic now follows these steps:
            *
            * 1. Import the user and get the `primaryUserId` (transaction uncommitted).
            * 2. Update the `primaryUserId` in the corresponding bulk import entry.
            * 3. Commit the import transaction from step 1.
            * 4. Delete the bulk import entry.
            *
            * If step 2 or any earlier step fails, nothing is committed, preventing partial state.
            * If step 3 fails, the `primaryUserId` in the bulk import entry is updated, but the user doesn't exist in the database—this results in re-processing on the next run.
            * If step 4 fails, the user exists but the bulk import entry remains; this will be handled by deleting it in the next run.
            *
            * The following code implements this logic.
            */
            if (user.primaryUserId != null) {
                AuthRecipeUserInfo importedUser = authRecipeSQLStorage.getPrimaryUserById(appIdentifier,
                        user.primaryUserId);

                if (importedUser != null && isProcessedUserFromSameBulkImportUserEntry(importedUser, user)) {
                    baseTenantStorage.deleteBulkImportUsers(appIdentifier, new String[] { user.id });
                    return;
                }
            }

            bulkImportProxyStorage.startTransaction(con -> {
                try {
                    for (LoginMethod lm : user.loginMethods) {
                        processUserLoginMethod(appIdentifier, bulkImportProxyStorage, lm);
                    }

                    createPrimaryUserAndLinkAccounts(main, appIdentifier, bulkImportProxyStorage, user, primaryLM);
                    createUserIdMapping(main, appIdentifier, user, primaryLM);
                    verifyEmailForAllLoginMethods(appIdentifier, con, bulkImportProxyStorage, user.loginMethods);
                    createTotpDevices(main, appIdentifier, bulkImportProxyStorage, user, primaryLM);
                    createUserMetadata(appIdentifier, bulkImportProxyStorage, user, primaryLM);
                    createUserRoles(main, appIdentifier, bulkImportProxyStorage, user);

                    // We are updating the primaryUserId in the bulkImportUser entry. This will help us handle the inconsistent transaction commit.
                    // If this update statement fails then the outer transaction will fail as well and the user will simpl be processed again. No inconsistency will happen in this
                    // case.
                    baseTenantStorage.updateBulkImportUserPrimaryUserId(appIdentifier, user.id,
                            primaryLM.superTokensUserId);

                    // We need to commit the transaction manually because we have overridden that in the proxy storage
                    // If this fails, the primaryUserId will be updated in the bulkImportUser but it wouldn’t actually exist.
                    // When processing the user again, we'll check if primaryUserId exists with the same email. In this case the user won't exist, and we'll simply re-process it.
                    bulkImportProxyStorage.commitTransactionForBulkImportProxyStorage();

                    // NOTE: We need to use the baseTenantStorage as bulkImportProxyStorage could have a different storage than the baseTenantStorage
                    // If this fails, the primaryUserId will be updated in the bulkImportUser and it would exist in the database.
                    // When processing the user again, we'll check if primaryUserId exists with the same email. In this case the user will exist, and we'll simply delete the entry.
                    baseTenantStorage.deleteBulkImportUsers(appIdentifier, new String[] { user.id });
                    return null;
                } catch (StorageTransactionLogicException e) {
                    // We need to rollback the transaction manually because we have overridden that in the proxy storage
                    bulkImportProxyStorage.rollbackTransactionForBulkImportProxyStorage();
                    throw e;
                } finally {
                    closeAllProxyStorages();
                }
            });
        } catch (StorageTransactionLogicException | InvalidBulkImportDataException e) {
            handleProcessUserExceptions(appIdentifier, user, e, baseTenantStorage);
        }
    }

    private void handleProcessUserExceptions(AppIdentifier appIdentifier, BulkImportUser user, Exception e,
            BulkImportSQLStorage baseTenantStorage)
            throws StorageQueryException {

        // Java doesn't allow us to reassign local variables inside a lambda expression
        // so we have to use an array.
        String[] errorMessage = { e.getMessage() };

        if (e instanceof StorageTransactionLogicException) {
            StorageTransactionLogicException exception = (StorageTransactionLogicException) e;
            errorMessage[0] = exception.actualException.getMessage();
        } else if (e instanceof InvalidBulkImportDataException) {
            errorMessage[0] = ((InvalidBulkImportDataException) e).errors.toString();
        }

        try {
            baseTenantStorage.startTransaction(con -> {
                baseTenantStorage.updateBulkImportUserStatus_Transaction(appIdentifier, con, user.id,
                        BULK_IMPORT_USER_STATUS.FAILED, errorMessage[0]);
                return null;
            });
        } catch (StorageTransactionLogicException e1) {
            throw new StorageQueryException(e1.actualException);
        }
    }

    private void processUserLoginMethod(AppIdentifier appIdentifier, Storage storage,
            LoginMethod lm) throws StorageTransactionLogicException {
        String firstTenant = lm.tenantIds.get(0);

        TenantIdentifier tenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                appIdentifier.getAppId(), firstTenant);

        if (lm.recipeId.equals("emailpassword")) {
            processEmailPasswordLoginMethod(tenantIdentifier, storage, lm);
        } else if (lm.recipeId.equals("thirdparty")) {
            processThirdPartyLoginMethod(tenantIdentifier, storage, lm);
        } else if (lm.recipeId.equals("passwordless")) {
            processPasswordlessLoginMethod(tenantIdentifier, storage, lm);
        } else {
            throw new StorageTransactionLogicException(
                    new IllegalArgumentException("Unknown recipeId " + lm.recipeId + " for loginMethod "));
        }

        associateUserToTenants(main, appIdentifier, storage, lm, firstTenant);
    }

    private void processEmailPasswordLoginMethod(TenantIdentifier tenantIdentifier, Storage storage,
            LoginMethod lm) throws StorageTransactionLogicException {
        try {
            ImportUserResponse userInfo = EmailPassword.createUserWithPasswordHash(tenantIdentifier, storage, lm.email,
                    lm.passwordHash, lm.timeJoinedInMSSinceEpoch);

            lm.superTokensUserId = userInfo.user.getSupertokensUserId();
        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(e);
        } catch (DuplicateEmailException e) {
            throw new StorageTransactionLogicException(
                    new Exception("A user with email " + lm.email + " already exists"));
        }
    }

    private void processThirdPartyLoginMethod(TenantIdentifier tenantIdentifier, Storage storage, LoginMethod lm)
            throws StorageTransactionLogicException {
        try {
            SignInUpResponse userInfo = ThirdParty.createThirdPartyUser(
                    tenantIdentifier, storage, lm.thirdPartyId, lm.thirdPartyUserId, lm.email,
                    lm.timeJoinedInMSSinceEpoch);

            lm.superTokensUserId = userInfo.user.getSupertokensUserId();
        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(e);
        } catch (DuplicateThirdPartyUserException e) {
            throw new StorageTransactionLogicException(new Exception("A user with thirdPartyId " + lm.thirdPartyId
                    + " and thirdPartyUserId " + lm.thirdPartyUserId + " already exists"));
        }
    }

    private void processPasswordlessLoginMethod(TenantIdentifier tenantIdentifier, Storage storage, LoginMethod lm)
            throws StorageTransactionLogicException {
        try {
            AuthRecipeUserInfo userInfo = Passwordless.createPasswordlessUser(tenantIdentifier, storage, lm.email,
                    lm.phoneNumber, lm.timeJoinedInMSSinceEpoch);

            lm.superTokensUserId = userInfo.getSupertokensUserId();
        } catch (StorageQueryException | TenantOrAppNotFoundException | RestartFlowException e) {
            throw new StorageTransactionLogicException(e);
        }
    }

    private void associateUserToTenants(Main main, AppIdentifier appIdentifier, Storage storage, LoginMethod lm,
            String firstTenant) throws StorageTransactionLogicException {
        for (String tenantId : lm.tenantIds) {
            try {
                if (tenantId.equals(firstTenant)) {
                    continue;
                }

                TenantIdentifier tenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                        appIdentifier.getAppId(), tenantId);
                Multitenancy.addUserIdToTenant(main, tenantIdentifier, storage, lm.getSuperTokenOrExternalUserId());
            } catch (TenantOrAppNotFoundException | UnknownUserIdException | StorageQueryException
                    | FeatureNotEnabledException | DuplicateEmailException | DuplicatePhoneNumberException
                    | DuplicateThirdPartyUserException | AnotherPrimaryUserWithPhoneNumberAlreadyExistsException
                    | AnotherPrimaryUserWithEmailAlreadyExistsException
                    | AnotherPrimaryUserWithThirdPartyInfoAlreadyExistsException e) {
                throw new StorageTransactionLogicException(e);
            }
        }
    }

    private void createPrimaryUserAndLinkAccounts(Main main,
            AppIdentifier appIdentifier, Storage storage, BulkImportUser user, LoginMethod primaryLM)
            throws StorageTransactionLogicException {
        if (user.loginMethods.size() == 1) {
            return;
        }

        try {
            AuthRecipe.createPrimaryUser(main, appIdentifier, storage, primaryLM.getSuperTokenOrExternalUserId());
        } catch (TenantOrAppNotFoundException | FeatureNotEnabledException | StorageQueryException e) {
            throw new StorageTransactionLogicException(e);
        } catch (UnknownUserIdException e) {
            throw new StorageTransactionLogicException(new Exception(
                    "We tried to create the primary user for the userId " + primaryLM.getSuperTokenOrExternalUserId()
                            + " but it doesn't exist. This should not happen. Please contact support."));
        } catch (RecipeUserIdAlreadyLinkedWithPrimaryUserIdException
                | AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            throw new StorageTransactionLogicException(
                    new Exception(e.getMessage() + " This should not happen. Please contact support."));
        }

        for (LoginMethod lm : user.loginMethods) {
            try {
                if (lm.getSuperTokenOrExternalUserId().equals(primaryLM.getSuperTokenOrExternalUserId())) {
                    continue;
                }

                AuthRecipe.linkAccounts(main, appIdentifier, storage, lm.getSuperTokenOrExternalUserId(),
                        primaryLM.getSuperTokenOrExternalUserId());

            } catch (TenantOrAppNotFoundException | FeatureNotEnabledException | StorageQueryException e) {
                throw new StorageTransactionLogicException(e);
            } catch (UnknownUserIdException e) {
                throw new StorageTransactionLogicException(
                        new Exception("We tried to link the userId " + lm.getSuperTokenOrExternalUserId()
                                + " to the primary userId " + primaryLM.getSuperTokenOrExternalUserId()
                                + " but it doesn't exist. This should not happen. Please contact support."));
            } catch (InputUserIdIsNotAPrimaryUserException e) {
                throw new StorageTransactionLogicException(
                        new Exception("We tried to link the userId " + lm.getSuperTokenOrExternalUserId()
                                + " to the primary userId " + primaryLM.getSuperTokenOrExternalUserId()
                                + " but it is not a primary user. This should not happen. Please contact support."));
            } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException
                    | RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException e) {
                throw new StorageTransactionLogicException(
                        new Exception(e.getMessage() + " This should not happen. Please contact support."));
            }
        }
    }

    private void createUserIdMapping(Main main, AppIdentifier appIdentifier,
            BulkImportUser user, LoginMethod primaryLM) throws StorageTransactionLogicException {
        if (user.externalUserId != null) {
            try {
                UserIdMapping.createUserIdMapping(
                        appIdentifier, getAllProxyStoragesForApp(main, appIdentifier),
                        primaryLM.superTokensUserId, user.externalUserId,
                        null, false, true);

                primaryLM.externalUserId = user.externalUserId;
            } catch (StorageQueryException | ServletException | TenantOrAppNotFoundException | InvalidConfigException
                    | IOException | DbInitException e) {
                throw new StorageTransactionLogicException(e);
            } catch (UserIdMappingAlreadyExistsException e) {
                throw new StorageTransactionLogicException(
                        new Exception("A user with externalId " + user.externalUserId + " already exists"));
            } catch (UnknownSuperTokensUserIdException e) {
                throw new StorageTransactionLogicException(
                        new Exception("We tried to create the externalUserId mapping for the superTokenUserId "
                                + primaryLM.superTokensUserId
                                + " but it doesn't exist. This should not happen. Please contact support."));
            }
        }
    }

    private void createUserMetadata(AppIdentifier appIdentifier, Storage storage, BulkImportUser user,
            LoginMethod primaryLM) throws StorageTransactionLogicException {
        if (user.userMetadata != null) {
            try {
                UserMetadata.updateUserMetadata(appIdentifier, storage, primaryLM.getSuperTokenOrExternalUserId(),
                        user.userMetadata);
            } catch (StorageQueryException | TenantOrAppNotFoundException e) {
                throw new StorageTransactionLogicException(e);
            }
        }
    }

    private void createUserRoles(Main main, AppIdentifier appIdentifier, Storage storage,
            BulkImportUser user) throws StorageTransactionLogicException {
        if (user.userRoles != null) {
            for (UserRole userRole : user.userRoles) {
                try {
                    for (String tenantId : userRole.tenantIds) {
                        TenantIdentifier tenantIdentifier = new TenantIdentifier(
                                appIdentifier.getConnectionUriDomain(), appIdentifier.getAppId(),
                                tenantId);

                        UserRoles.addRoleToUser(main, tenantIdentifier, storage, user.externalUserId, userRole.role);
                    }
                } catch (TenantOrAppNotFoundException | StorageQueryException e) {
                    throw new StorageTransactionLogicException(e);
                } catch (UnknownRoleException e) {
                    throw new StorageTransactionLogicException(new Exception("Role " + userRole.role
                            + " does not exist! You need pre-create the role before assigning it to the user."));
                }
            }
        }
    }

    private void verifyEmailForAllLoginMethods(AppIdentifier appIdentifier, TransactionConnection con, Storage storage,
            List<LoginMethod> loginMethods) throws StorageTransactionLogicException {

        for (LoginMethod lm : loginMethods) {
            try {

                TenantIdentifier tenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                        appIdentifier.getAppId(), lm.tenantIds.get(0));

                EmailVerificationSQLStorage emailVerificationSQLStorage = StorageUtils
                        .getEmailVerificationStorage(storage);
                emailVerificationSQLStorage
                        .updateIsEmailVerified_Transaction(tenantIdentifier.toAppIdentifier(), con,
                                lm.getSuperTokenOrExternalUserId(), lm.email, true);
            } catch (TenantOrAppNotFoundException | StorageQueryException e) {
                throw new StorageTransactionLogicException(e);
            }
        }
    }

    private void createTotpDevices(Main main, AppIdentifier appIdentifier, Storage storage,
            BulkImportUser user, LoginMethod primaryLM) throws StorageTransactionLogicException {
        if (user.totpDevices != null) {
            for (TotpDevice totpDevice : user.totpDevices) {
                try {
                    Totp.createDevice(main, appIdentifier, storage, primaryLM.getSuperTokenOrExternalUserId(),
                            totpDevice.deviceName, totpDevice.skew, totpDevice.period, totpDevice.secretKey,
                            true, System.currentTimeMillis());
                } catch (TenantOrAppNotFoundException | StorageQueryException | FeatureNotEnabledException e) {
                    throw new StorageTransactionLogicException(e);
                } catch (DeviceAlreadyExistsException e) {
                    throw new StorageTransactionLogicException(
                            new Exception("A totp device with name " + totpDevice.deviceName + " already exists"));
                }
            }
        }
    }

    // Returns the primary loginMethod of the user. If no loginMethod is marked as
    // primary, then the oldest loginMethod is returned.
    private BulkImportUser.LoginMethod getPrimaryLoginMethod(BulkImportUser user) {
        BulkImportUser.LoginMethod oldestLM = user.loginMethods.get(0);
        for (BulkImportUser.LoginMethod lm : user.loginMethods) {
            if (lm.isPrimary) {
                return lm;
            }

            if (lm.timeJoinedInMSSinceEpoch < oldestLM.timeJoinedInMSSinceEpoch) {
                oldestLM = lm;
            }
        }
        return oldestLM;
    }

    // Checks if the importedUser was processed from the same bulkImportUser entry.
    private boolean isProcessedUserFromSameBulkImportUserEntry(
            AuthRecipeUserInfo importedUser, BulkImportUser bulkImportEntry) {
        if (bulkImportEntry == null || importedUser == null || bulkImportEntry.loginMethods == null ||
                importedUser.loginMethods == null) {
            return false;
        }

        for (LoginMethod lm1 : bulkImportEntry.loginMethods) {
            for (io.supertokens.pluginInterface.authRecipe.LoginMethod lm2 : importedUser.loginMethods) {
                if (lm2.recipeId.toString().equals(lm1.recipeId)) {
                    if (lm1.email != null && !lm1.email.equals(lm2.email)) {
                        return false;
                    }

                    switch (lm1.recipeId) {
                        case "emailpassword":
                            if (lm1.passwordHash != null && !lm1.passwordHash.equals(lm2.passwordHash)) {
                                return false;
                            }
                            break;
                        case "thirdparty":
                            if ((lm1.thirdPartyId != null && !lm1.thirdPartyId.equals(lm2.thirdParty.id))
                                    || (lm1.thirdPartyUserId != null
                                            && !lm1.thirdPartyUserId.equals(lm2.thirdParty.userId))) {
                                return false;
                            }
                            break;
                        case "passwordless":
                            if (lm1.phoneNumber != null && !lm1.phoneNumber.equals(lm2.phoneNumber)) {
                                return false;
                            }
                            break;
                        default:
                            return false;
                    }
                }
            }
        }

        return true;
    }
}
