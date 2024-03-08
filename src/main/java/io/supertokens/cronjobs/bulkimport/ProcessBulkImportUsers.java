/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

import java.util.List;

import io.supertokens.Main;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.exception.AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.InputUserIdIsNotAPrimaryUserException;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithPrimaryUserIdException;
import io.supertokens.bulkimport.BulkImport;
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
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage.BULK_IMPORT_USER_STATUS;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.UserRole;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportSQLStorage;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicatePhoneNumberException;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.pluginInterface.sqlStorage.TransactionConnection;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateThirdPartyUserException;
import io.supertokens.pluginInterface.useridmapping.exception.UnknownSuperTokensUserIdException;
import io.supertokens.pluginInterface.useridmapping.exception.UserIdMappingAlreadyExistsException;
import io.supertokens.pluginInterface.userroles.exception.UnknownRoleException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.thirdparty.ThirdParty.SignInUpResponse;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.userroles.UserRoles;
import jakarta.servlet.ServletException;

public class ProcessBulkImportUsers extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.ee.cronjobs.ProcessBulkImportUsers";

    private ProcessBulkImportUsers(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("ProcessBulkImportUsers", main, tenantsInfo, true);
    }

    public static ProcessBulkImportUsers init(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        return (ProcessBulkImportUsers) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new ProcessBulkImportUsers(main, tenantsInfo));
    }

    @Override
    protected void doTaskPerApp(AppIdentifier app) throws TenantOrAppNotFoundException, StorageQueryException {
        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        BulkImportSQLStorage bulkImportSQLStorage = (BulkImportSQLStorage) StorageLayer
                .getStorage(app.getAsPublicTenantIdentifier(), main);

        AppIdentifierWithStorage appIdentifierWithStorage = new AppIdentifierWithStorage(
                app.getConnectionUriDomain(), app.getAppId(),
                StorageLayer.getStorage(app.getAsPublicTenantIdentifier(), main));

        List<BulkImportUser> users = bulkImportSQLStorage.getBulkImportUsersForProcessing(appIdentifierWithStorage,
                BulkImport.PROCESS_USERS_BATCH_SIZE);

        for (BulkImportUser user : users) {
            processUser(appIdentifierWithStorage, user);
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
        return BulkImport.PROCESS_USERS_INTERVAL;
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        return 0;
    }

    private void processUser(AppIdentifierWithStorage appIdentifierWithStorage, BulkImportUser user)
            throws TenantOrAppNotFoundException, StorageQueryException {
        // Since all the tenants of a user must share the storage, we will just use the
        // storage of the first tenantId of the first loginMethod
        SQLStorage userStorage = (SQLStorage) StorageLayer
                .getStorage(new TenantIdentifier(appIdentifierWithStorage.getConnectionUriDomain(),
                        appIdentifierWithStorage.getAppId(), user.loginMethods.get(0).tenantIds.get(0)), main);

        BulkImportSQLStorage bulkImportSQLStorage = (BulkImportSQLStorage) userStorage;

        LoginMethod primaryLM = getPrimaryLoginMethod(user);

        try {
            userStorage.startTransaction(con -> {
                for (LoginMethod lm : user.loginMethods) {
                    processUserLoginMethod(appIdentifierWithStorage, userStorage, con, lm);
                }

                createPrimaryUserAndLinkAccounts(main, con, appIdentifierWithStorage, user, primaryLM);
                createUserIdMapping(appIdentifierWithStorage, con, user, primaryLM);
                verifyEmailForAllLoginMethods(appIdentifierWithStorage, con, userStorage, user.loginMethods);
                createUserMetadata(appIdentifierWithStorage, con, user, primaryLM);
                createUserRoles(appIdentifierWithStorage, con, user);

                bulkImportSQLStorage.deleteBulkImportUser_Transaction(appIdentifierWithStorage, con, user.id);

                return null;
            });

        // We are intentionally catching all exceptions here because we want to mark the user as failed and process the next user
        } catch (Exception e) {
            handleProcessUserExceptions(appIdentifierWithStorage, user, bulkImportSQLStorage, e);
        }
    }

    private void handleProcessUserExceptions(AppIdentifierWithStorage appIdentifierWithStorage, BulkImportUser user,
            BulkImportSQLStorage bulkImportSQLStorage, Exception e)
            throws StorageQueryException {

        // Java doesn't allow us to reassign local variables inside a lambda expression
        // so we have to use an array.
        String[] errorMessage = { e.getMessage() };

        if (e instanceof StorageTransactionLogicException) {
            StorageTransactionLogicException exception = (StorageTransactionLogicException) e;
            errorMessage[0] = exception.actualException.getMessage();
        }

        String[] userId = { user.id };

        try {
            bulkImportSQLStorage.startTransaction(con -> {
                bulkImportSQLStorage.updateBulkImportUserStatus_Transaction(appIdentifierWithStorage, con, userId,
                        BULK_IMPORT_USER_STATUS.FAILED, errorMessage[0]);
                return null;
            });
        } catch (StorageTransactionLogicException e1) {
            throw new StorageQueryException(e1.actualException);
        }
    }

    private void processUserLoginMethod(AppIdentifierWithStorage appIdentifierWithStorage, SQLStorage userStorage,
            TransactionConnection con,
            LoginMethod lm) throws StorageTransactionLogicException {
        String firstTenant = lm.tenantIds.get(0);

        TenantIdentifierWithStorage tenantIdentifierWithStorage = new TenantIdentifierWithStorage(
                appIdentifierWithStorage.getConnectionUriDomain(), appIdentifierWithStorage.getAppId(), firstTenant,
                userStorage);

        if (lm.recipeId.equals("emailpassword")) {
            processEmailPasswordLoginMethod(tenantIdentifierWithStorage, con, lm);
        } else if (lm.recipeId.equals("thirdparty")) {
            processThirdPartyLoginMethod(tenantIdentifierWithStorage, con, lm);
        } else if (lm.recipeId.equals("passwordless")) {
            processPasswordlessLoginMethod(tenantIdentifierWithStorage, con, lm);
        } else {
            throw new StorageTransactionLogicException(
                    new IllegalArgumentException("Unknown recipeId " + lm.recipeId + " for loginMethod "));
        }

        associateUserToTenants(main, con, appIdentifierWithStorage, lm, firstTenant, userStorage);
    }

    private void processEmailPasswordLoginMethod(TenantIdentifierWithStorage tenantIdentifierWithStorage,
            TransactionConnection con,
            LoginMethod lm) throws StorageTransactionLogicException {
        try {
            ImportUserResponse userInfo = EmailPassword.bulkImport_createUserWithPasswordHash_Transaction(con,
                    tenantIdentifierWithStorage, lm.email, lm.passwordHash, lm.timeJoinedInMSSinceEpoch);

            lm.superTokensOrExternalUserId = userInfo.user.getSupertokensUserId();
        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(e);
        } catch (DuplicateEmailException e) {
            throw new StorageTransactionLogicException(
                    new Exception("A user with email " + lm.email + " already exists"));
        }
    }

    private void processThirdPartyLoginMethod(TenantIdentifierWithStorage tenantIdentifierWithStorage,
            TransactionConnection con,
            LoginMethod lm) throws StorageTransactionLogicException {
        try {
            SignInUpResponse userInfo = ThirdParty.bulkImport_createThirdPartyUser_Transaction(con,
                    tenantIdentifierWithStorage, lm.thirdPartyId,
                    lm.thirdPartyUserId, lm.email, lm.timeJoinedInMSSinceEpoch);

            lm.superTokensOrExternalUserId = userInfo.user.getSupertokensUserId();
        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(e);
        } catch (DuplicateThirdPartyUserException e) {
            throw new StorageTransactionLogicException(new Exception("A user with thirdPartyId " + lm.thirdPartyId
                    + " and thirdPartyUserId " + lm.thirdPartyUserId + " already exists"));
        }
    }

    private void processPasswordlessLoginMethod(TenantIdentifierWithStorage tenantIdentifierWithStorage,
            TransactionConnection con,
            LoginMethod lm) throws StorageTransactionLogicException {
        try {
            AuthRecipeUserInfo userInfo = Passwordless.bulkImport_createPasswordlessUser_Transaction(con,
                    tenantIdentifierWithStorage, lm.email, lm.phoneNumber, lm.timeJoinedInMSSinceEpoch);

            lm.superTokensOrExternalUserId = userInfo.getSupertokensUserId();
        } catch (StorageQueryException | TenantOrAppNotFoundException | RestartFlowException e) {
            throw new StorageTransactionLogicException(e);
        }
    }

    private void associateUserToTenants(Main main, TransactionConnection con,
            AppIdentifierWithStorage appIdentifierWithStorage, LoginMethod lm, String firstTenant,
            SQLStorage userStorage) throws StorageTransactionLogicException {
        for (String tenantId : lm.tenantIds) {
            try {
                if (tenantId.equals(firstTenant)) {
                    continue;
                }

                TenantIdentifierWithStorage tIWithStorage = new TenantIdentifierWithStorage(
                        appIdentifierWithStorage.getConnectionUriDomain(), appIdentifierWithStorage.getAppId(),
                        tenantId, userStorage);
                Multitenancy.bulkImport_addUserIdToTenant_Transaction(main, con, tIWithStorage,
                        lm.superTokensOrExternalUserId);
            } catch (TenantOrAppNotFoundException | UnknownUserIdException | StorageQueryException
                    | FeatureNotEnabledException | DuplicateEmailException | DuplicatePhoneNumberException
                    | DuplicateThirdPartyUserException | AnotherPrimaryUserWithPhoneNumberAlreadyExistsException
                    | AnotherPrimaryUserWithEmailAlreadyExistsException
                    | AnotherPrimaryUserWithThirdPartyInfoAlreadyExistsException e) {
                throw new StorageTransactionLogicException(e);
            }
        }
    }

    private void createPrimaryUserAndLinkAccounts(Main main, TransactionConnection con,
            AppIdentifierWithStorage appIdentifierWithStorage, BulkImportUser user, LoginMethod primaryLM)
            throws StorageTransactionLogicException {
        if (user.loginMethods.size() == 1) {
            return;
        }

        try {
            AuthRecipe.bulkImport_createPrimaryUser_Transaction(main, con, appIdentifierWithStorage,
                    primaryLM.superTokensOrExternalUserId);
        } catch (TenantOrAppNotFoundException | FeatureNotEnabledException | StorageQueryException e) {
            throw new StorageTransactionLogicException(e);
        } catch (UnknownUserIdException e) {
            throw new StorageTransactionLogicException(new Exception(
                    "We tried to create the primary user for the userId " + primaryLM.superTokensOrExternalUserId
                            + " but it doesn't exist. This should not happen. Please contact support"));
        } catch (RecipeUserIdAlreadyLinkedWithPrimaryUserIdException
                | AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            throw new StorageTransactionLogicException(
                    new Exception(e.getMessage() + " This should not happen. Please contact support."));
        }

        for (LoginMethod lm : user.loginMethods) {
            try {
                if (lm.superTokensOrExternalUserId.equals(primaryLM.superTokensOrExternalUserId)) {
                    continue;
                }

                AuthRecipe.bulkImport_linkAccounts_Transaction(main, con, appIdentifierWithStorage,
                        lm.superTokensOrExternalUserId, primaryLM.superTokensOrExternalUserId);

            } catch (TenantOrAppNotFoundException | FeatureNotEnabledException | StorageQueryException e) {
                throw new StorageTransactionLogicException(e);
            } catch (UnknownUserIdException e) {
                throw new StorageTransactionLogicException(
                        new Exception("We tried to link the userId " + lm.superTokensOrExternalUserId
                                + " to the primary userId " + primaryLM.superTokensOrExternalUserId
                                + " but it doesn't exist. This should not happen. Please contact support"));
            } catch (InputUserIdIsNotAPrimaryUserException e) {
                throw new StorageTransactionLogicException(
                        new Exception("We tried to link the userId " + lm.superTokensOrExternalUserId
                                + " to the primary userId " + primaryLM.superTokensOrExternalUserId
                                + " but it is not a primary user. This should not happen. Please contact support"));
            } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException
                    | RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException e) {
                throw new StorageTransactionLogicException(
                        new Exception(e.getMessage() + " This should not happen. Please contact support."));
            }
        }
    }

    private void createUserIdMapping(AppIdentifierWithStorage appIdentifierWithStorage, TransactionConnection con,
            BulkImportUser user, LoginMethod primaryLM) throws StorageTransactionLogicException {
        if (user.externalUserId != null) {
            try {
                UserIdMapping.bulkImport_createUserIdMapping_Transaction(main, con,
                        appIdentifierWithStorage, primaryLM.superTokensOrExternalUserId, user.externalUserId,
                        null, false, true);

                primaryLM.superTokensOrExternalUserId = user.externalUserId;
            } catch (StorageQueryException | ServletException | TenantOrAppNotFoundException e) {
                throw new StorageTransactionLogicException(e);
            } catch (UserIdMappingAlreadyExistsException e) {
                throw new StorageTransactionLogicException(
                        new Exception("A user with externalId " + user.externalUserId + " already exists"));
            } catch (UnknownSuperTokensUserIdException e) {
                throw new StorageTransactionLogicException(
                        new Exception("We tried to create the externalUserId mapping for the superTokenUserId "
                                + primaryLM.superTokensOrExternalUserId
                                + " but it doesn't exist. This should not happen. Please contact support"));
            }
        }
    }

    private void createUserMetadata(AppIdentifierWithStorage appIdentifierWithStorage, TransactionConnection con,
            BulkImportUser user, LoginMethod primaryLM) throws StorageTransactionLogicException {
        if (user.userMetadata != null) {
            try {
                UserMetadata.bulkImport_updateUserMetadata_Transaction(con,
                        appIdentifierWithStorage,
                        primaryLM.superTokensOrExternalUserId, user.userMetadata);
            } catch (StorageQueryException | TenantOrAppNotFoundException e) {
                throw new StorageTransactionLogicException(e);
            }
        }
    }

    private void createUserRoles(AppIdentifierWithStorage appIdentifierWithStorage, TransactionConnection con,
            BulkImportUser user) throws StorageTransactionLogicException {
        if (user.userRoles != null) {
            for (UserRole userRole : user.userRoles) {
                try {
                    for (String tenantId : userRole.tenantIds) {
                        TenantIdentifierWithStorage tenantIdentifierWithStorage = new TenantIdentifierWithStorage(
                                appIdentifierWithStorage.getConnectionUriDomain(), appIdentifierWithStorage.getAppId(),
                                tenantId,
                                appIdentifierWithStorage.getStorage());

                        UserRoles.bulkImport_addRoleToUser_Transaction(con, tenantIdentifierWithStorage,
                                user.externalUserId, userRole.role);
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

    private void verifyEmailForAllLoginMethods(AppIdentifierWithStorage appIdentifierWithStorage,
            TransactionConnection con,
            SQLStorage userStorage, List<LoginMethod> loginMethods) throws StorageTransactionLogicException {

        for (LoginMethod lm : loginMethods) {
            try {

                TenantIdentifierWithStorage tenantIdentifierWithStorage = new TenantIdentifierWithStorage(
                        appIdentifierWithStorage.getConnectionUriDomain(), appIdentifierWithStorage.getAppId(),
                        lm.tenantIds.get(0),
                        userStorage);

                EmailVerificationSQLStorage emailVerificationSQLStorage = tenantIdentifierWithStorage
                        .getEmailVerificationStorage();
                emailVerificationSQLStorage
                        .updateIsEmailVerified_Transaction(tenantIdentifierWithStorage.toAppIdentifier(), con,
                                lm.superTokensOrExternalUserId, lm.email, true);
            } catch (TenantOrAppNotFoundException | StorageQueryException e) {
                throw new StorageTransactionLogicException(e);
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
}
