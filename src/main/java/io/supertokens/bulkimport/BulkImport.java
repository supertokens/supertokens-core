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

package io.supertokens.bulkimport;

import io.supertokens.pluginInterface.bulkimport.BulkImportStorage.BULK_IMPORT_USER_STATUS;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportSQLStorage;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.exception.AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.InputUserIdIsNotAPrimaryUserException;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithPrimaryUserIdException;
import io.supertokens.config.Config;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.EmailPassword.ImportUserResponse;
import io.supertokens.emailpassword.PasswordHashing;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.AnotherPrimaryUserWithEmailAlreadyExistsException;
import io.supertokens.multitenancy.exception.AnotherPrimaryUserWithPhoneNumberAlreadyExistsException;
import io.supertokens.multitenancy.exception.AnotherPrimaryUserWithThirdPartyInfoAlreadyExistsException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.exceptions.RestartFlowException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.TotpDevice;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.UserRole;
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
import io.supertokens.utils.Utils;
import jakarta.servlet.ServletException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

// Error codes ensure globally unique and identifiable errors in Bulk Import.
// Current range: E001 to E046.

public class BulkImport {

    // Maximum number of users that can be added in a single /bulk-import/users POST request
    public static final int MAX_USERS_TO_ADD = 10000;
    // Maximum number of users to return in a single page when calling /bulk-import/users GET
    public static final int GET_USERS_PAGINATION_MAX_LIMIT = 500;
    // Default number of users to return when no specific limit is given in /bulk-import/users GET
    public static final int GET_USERS_DEFAULT_LIMIT = 100;
    // Maximum number of users that can be deleted in a single operation
    public static final int DELETE_USERS_MAX_LIMIT = 500;
    // Number of users to process in a single batch of ProcessBulkImportUsers Cron Job
    public static final int PROCESS_USERS_BATCH_SIZE = 10000;
    // Time interval in seconds between two consecutive runs of ProcessBulkImportUsers Cron Job
    public static final int PROCESS_USERS_INTERVAL_SECONDS = 1;

    // This map allows reusing proxy storage for all tenants in the app and closing connections after import.
    private static Map<String, SQLStorage> userPoolToStorageMap = new HashMap<>();

    public static void addUsers(AppIdentifier appIdentifier, Storage storage, List<BulkImportUser> users)
            throws StorageQueryException, TenantOrAppNotFoundException {
        while (true) {
            try {
                StorageUtils.getBulkImportStorage(storage).addBulkImportUsers(appIdentifier, users);
                break;
            } catch (io.supertokens.pluginInterface.bulkimport.exceptions.DuplicateUserIdException ignored) {
                // We re-generate the user id for every user and retry
                for (BulkImportUser user : users) {
                    user.id = Utils.getUUID();
                }
            }
        }
    }

    public static BulkImportUserPaginationContainer getUsers(AppIdentifier appIdentifier, Storage storage,
            int limit, @Nullable BULK_IMPORT_USER_STATUS status, @Nullable String paginationToken)
            throws StorageQueryException, BulkImportUserPaginationToken.InvalidTokenException {
        List<BulkImportUser> users;

        BulkImportSQLStorage bulkImportStorage = StorageUtils.getBulkImportStorage(storage);

        if (paginationToken == null) {
            users = bulkImportStorage
                    .getBulkImportUsers(appIdentifier, limit + 1, status, null, null);
        } else {
            BulkImportUserPaginationToken tokenInfo = BulkImportUserPaginationToken.extractTokenInfo(paginationToken);
            users = bulkImportStorage
                    .getBulkImportUsers(appIdentifier, limit + 1, status, tokenInfo.bulkImportUserId,
                            tokenInfo.createdAt);
        }

        String nextPaginationToken = null;
        int maxLoop = users.size();
        if (users.size() == limit + 1) {
            maxLoop = limit;
            BulkImportUser user = users.get(limit);
            nextPaginationToken = new BulkImportUserPaginationToken(user.id, user.createdAt).generateToken();
        }

        List<BulkImportUser> resultUsers = users.subList(0, maxLoop);
        return new BulkImportUserPaginationContainer(resultUsers, nextPaginationToken);
    }

    public static List<String> deleteUsers(AppIdentifier appIdentifier, Storage storage, String[] userIds)
            throws StorageQueryException {
        return StorageUtils.getBulkImportStorage(storage).deleteBulkImportUsers(appIdentifier, userIds);
    }

    public static long getBulkImportUsersCount(AppIdentifier appIdentifier, Storage storage,
            @Nullable BULK_IMPORT_USER_STATUS status)
            throws StorageQueryException {
        return StorageUtils.getBulkImportStorage(storage).getBulkImportUsersCount(appIdentifier, status);
    }

    public static synchronized AuthRecipeUserInfo importUser(Main main, AppIdentifier appIdentifier,
            BulkImportUser user)
            throws StorageQueryException, InvalidConfigException, IOException, TenantOrAppNotFoundException,
            DbInitException {
        // Since all the tenants of a user must share the storage, we will just use the
        // storage of the first tenantId of the first loginMethod
        TenantIdentifier firstTenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                appIdentifier.getAppId(), user.loginMethods.get(0).tenantIds.get(0));

        SQLStorage bulkImportProxyStorage = (SQLStorage) getBulkImportProxyStorage(main, firstTenantIdentifier);

        LoginMethod primaryLM = getPrimaryLoginMethod(user);

        try {
            return bulkImportProxyStorage.startTransaction(con -> {
                try {
                    Storage[] allStoragesForApp = getAllProxyStoragesForApp(main, appIdentifier);
                    processUserImportSteps(main, con, appIdentifier, bulkImportProxyStorage, user, primaryLM,
                            allStoragesForApp);

                    bulkImportProxyStorage.commitTransactionForBulkImportProxyStorage();

                    AuthRecipeUserInfo importedUser = AuthRecipe.getUserById(appIdentifier, bulkImportProxyStorage,
                            primaryLM.superTokensUserId);
                    io.supertokens.useridmapping.UserIdMapping.populateExternalUserIdForUsers(appIdentifier,
                            bulkImportProxyStorage, new AuthRecipeUserInfo[] { importedUser });

                    return importedUser;
                } catch (StorageTransactionLogicException e) {
                    // We need to rollback the transaction manually because we have overridden that in the proxy storage
                    bulkImportProxyStorage.rollbackTransactionForBulkImportProxyStorage();
                    throw e;
                } finally {
                    closeAllProxyStorages();
                }
            });
        } catch (StorageTransactionLogicException e) {
            throw new StorageQueryException(e.actualException);
        }
    }

    public static void processUserImportSteps(Main main, TransactionConnection con, AppIdentifier appIdentifier,
            Storage bulkImportProxyStorage, BulkImportUser user, LoginMethod primaryLM, Storage[] allStoragesForApp)
            throws StorageTransactionLogicException {
        for (LoginMethod lm : user.loginMethods) {
            processUserLoginMethod(main, appIdentifier, bulkImportProxyStorage, lm);
        }

        createPrimaryUserAndLinkAccounts(main, appIdentifier, bulkImportProxyStorage, user, primaryLM);
        createUserIdMapping(appIdentifier, user, primaryLM, allStoragesForApp);
        verifyEmailForAllLoginMethods(appIdentifier, con, bulkImportProxyStorage, user.loginMethods);
        createTotpDevices(main, appIdentifier, bulkImportProxyStorage, user, primaryLM);
        createUserMetadata(appIdentifier, bulkImportProxyStorage, user, primaryLM);
        createUserRoles(main, appIdentifier, bulkImportProxyStorage, user);
    }

    public static void processUserLoginMethod(Main main, AppIdentifier appIdentifier, Storage storage,
            LoginMethod lm) throws StorageTransactionLogicException {
        String firstTenant = lm.tenantIds.get(0);

        TenantIdentifier tenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                appIdentifier.getAppId(), firstTenant);

        if (lm.recipeId.equals("emailpassword")) {
            processEmailPasswordLoginMethod(main, tenantIdentifier, storage, lm);
        } else if (lm.recipeId.equals("thirdparty")) {
            processThirdPartyLoginMethod(tenantIdentifier, storage, lm);
        } else if (lm.recipeId.equals("passwordless")) {
            processPasswordlessLoginMethod(tenantIdentifier, storage, lm);
        } else {
            throw new StorageTransactionLogicException(
                    new IllegalArgumentException("E001: Unknown recipeId " + lm.recipeId + " for loginMethod."));
        }

        associateUserToTenants(main, appIdentifier, storage, lm, firstTenant);
    }

    private static void processEmailPasswordLoginMethod(Main main, TenantIdentifier tenantIdentifier, Storage storage,
            LoginMethod lm) throws StorageTransactionLogicException {
        try {

            String passwordHash = lm.passwordHash;
            if (passwordHash == null && lm.plainTextPassword != null) {
                passwordHash = PasswordHashing.getInstance(main)
                        .createHashWithSalt(tenantIdentifier.toAppIdentifier(), lm.plainTextPassword);
            }

            ImportUserResponse userInfo = EmailPassword.createUserWithPasswordHash(tenantIdentifier, storage, lm.email,
                    passwordHash, lm.timeJoinedInMSSinceEpoch);

            lm.superTokensUserId = userInfo.user.getSupertokensUserId();
        } catch (StorageQueryException e) {
            throw new StorageTransactionLogicException(e);
        } catch (TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(new Exception("E002: " + e.getMessage()));
        } catch (DuplicateEmailException e) {
            throw new StorageTransactionLogicException(
                    new Exception(
                            "E003: A user with email " + lm.email + " already exists in emailpassword loginMethod."));
        }
    }

    private static void processThirdPartyLoginMethod(TenantIdentifier tenantIdentifier, Storage storage, LoginMethod lm)
            throws StorageTransactionLogicException {
        try {
            SignInUpResponse userInfo = ThirdParty.createThirdPartyUser(
                    tenantIdentifier, storage, lm.thirdPartyId, lm.thirdPartyUserId, lm.email,
                    lm.timeJoinedInMSSinceEpoch);

            lm.superTokensUserId = userInfo.user.getSupertokensUserId();
        } catch (StorageQueryException e) {
            throw new StorageTransactionLogicException(e);
        } catch (TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(new Exception("E004: " + e.getMessage()));
        } catch (DuplicateThirdPartyUserException e) {
            throw new StorageTransactionLogicException(new Exception("E005: A user with thirdPartyId " + lm.thirdPartyId
                    + " and thirdPartyUserId " + lm.thirdPartyUserId + " already exists in thirdparty loginMethod."));
        }
    }

    private static void processPasswordlessLoginMethod(TenantIdentifier tenantIdentifier, Storage storage,
            LoginMethod lm)
            throws StorageTransactionLogicException {
        try {
            AuthRecipeUserInfo userInfo = Passwordless.createPasswordlessUser(tenantIdentifier, storage, lm.email,
                    lm.phoneNumber, lm.timeJoinedInMSSinceEpoch);

            lm.superTokensUserId = userInfo.getSupertokensUserId();
        } catch (RestartFlowException e) {
            String errorMessage = lm.email != null
                    ? "E006: A user with email " + lm.email + " already exists in passwordless loginMethod."
                    : "E007: A user with phoneNumber " + lm.phoneNumber
                            + " already exists in passwordless loginMethod.";
            throw new StorageTransactionLogicException(new Exception(errorMessage));
        } catch (StorageQueryException e) {
            throw new StorageTransactionLogicException(e);
        } catch (TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(new Exception("E008: " + e.getMessage()));
        }
    }

    private static void associateUserToTenants(Main main, AppIdentifier appIdentifier, Storage storage, LoginMethod lm,
            String firstTenant) throws StorageTransactionLogicException {
        for (String tenantId : lm.tenantIds) {
            try {
                if (tenantId.equals(firstTenant)) {
                    continue;
                }

                TenantIdentifier tenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                        appIdentifier.getAppId(), tenantId);
                Multitenancy.addUserIdToTenant(main, tenantIdentifier, storage, lm.getSuperTokenOrExternalUserId());
            } catch (TenantOrAppNotFoundException e) {
                throw new StorageTransactionLogicException(new Exception("E009: " + e.getMessage()));
            } catch (StorageQueryException e) {
                throw new StorageTransactionLogicException(e);
            } catch (UnknownUserIdException e) {
                throw new StorageTransactionLogicException(new Exception("E010: " + "We tried to add the userId "
                        + lm.getSuperTokenOrExternalUserId() + " to the tenantId " + tenantId
                        + " but it doesn't exist. This should not happen. Please contact support."));
            } catch (AnotherPrimaryUserWithEmailAlreadyExistsException e) {
                throw new StorageTransactionLogicException(new Exception("E011: " + "We tried to add the userId "
                        + lm.getSuperTokenOrExternalUserId() + " to the tenantId " + tenantId
                        + " but another primary user with email " + lm.email + " already exists."));
            } catch (AnotherPrimaryUserWithPhoneNumberAlreadyExistsException e) {
                throw new StorageTransactionLogicException(new Exception("E012: " + "We tried to add the userId "
                        + lm.getSuperTokenOrExternalUserId() + " to the tenantId " + tenantId
                        + " but another primary user with phoneNumber " + lm.phoneNumber + " already exists."));
            } catch (AnotherPrimaryUserWithThirdPartyInfoAlreadyExistsException e) {
                throw new StorageTransactionLogicException(new Exception("E013: " + "We tried to add the userId "
                        + lm.getSuperTokenOrExternalUserId() + " to the tenantId " + tenantId
                        + " but another primary user with thirdPartyId " + lm.thirdPartyId + " and thirdPartyUserId "
                        + lm.thirdPartyUserId + " already exists."));
            } catch (DuplicateEmailException e) {
                throw new StorageTransactionLogicException(new Exception("E014: " + "We tried to add the userId "
                        + lm.getSuperTokenOrExternalUserId() + " to the tenantId " + tenantId
                        + " but another user with email " + lm.email + " already exists."));
            } catch (DuplicatePhoneNumberException e) {
                throw new StorageTransactionLogicException(new Exception("E015: " + "We tried to add the userId "
                        + lm.getSuperTokenOrExternalUserId() + " to the tenantId " + tenantId
                        + " but another user with phoneNumber " + lm.phoneNumber + " already exists."));
            } catch (DuplicateThirdPartyUserException e) {
                throw new StorageTransactionLogicException(new Exception("E016: " + "We tried to add the userId "
                        + lm.getSuperTokenOrExternalUserId() + " to the tenantId " + tenantId
                        + " but another user with thirdPartyId " + lm.thirdPartyId + " and thirdPartyUserId "
                        + lm.thirdPartyUserId + " already exists."));
            } catch (FeatureNotEnabledException e) {
                throw new StorageTransactionLogicException(new Exception("E017: " + e.getMessage()));
            }
        }
    }

    public static void createPrimaryUserAndLinkAccounts(Main main,
            AppIdentifier appIdentifier, Storage storage, BulkImportUser user, LoginMethod primaryLM)
            throws StorageTransactionLogicException {
        if (user.loginMethods.size() == 1) {
            return;
        }

        try {
            AuthRecipe.createPrimaryUser(main, appIdentifier, storage, primaryLM.getSuperTokenOrExternalUserId());
        } catch (TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(new Exception("E018: " + e.getMessage()));
        } catch (StorageQueryException e) {
            throw new StorageTransactionLogicException(e);
        } catch (FeatureNotEnabledException e) {
            throw new StorageTransactionLogicException(new Exception("E019: " + e.getMessage()));
        } catch (UnknownUserIdException e) {
            throw new StorageTransactionLogicException(new Exception(
                    "E020: We tried to create the primary user for the userId "
                            + primaryLM.getSuperTokenOrExternalUserId()
                            + " but it doesn't exist. This should not happen. Please contact support."));
        } catch (RecipeUserIdAlreadyLinkedWithPrimaryUserIdException e) {
            throw new StorageTransactionLogicException(new Exception(
                    "E021: We tried to create the primary user for the userId "
                            + primaryLM.getSuperTokenOrExternalUserId()
                            + " but it is already linked with another primary user."));
        } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            throw new StorageTransactionLogicException(new Exception(
                    "E022: We tried to create the primary user for the userId "
                            + primaryLM.getSuperTokenOrExternalUserId()
                            + " but the account info is already associated with another primary user."));
        }

        for (LoginMethod lm : user.loginMethods) {
            try {
                if (lm.getSuperTokenOrExternalUserId().equals(primaryLM.getSuperTokenOrExternalUserId())) {
                    continue;
                }

                AuthRecipe.linkAccounts(main, appIdentifier, storage, lm.getSuperTokenOrExternalUserId(),
                        primaryLM.getSuperTokenOrExternalUserId());

            } catch (TenantOrAppNotFoundException e) {
                throw new StorageTransactionLogicException(new Exception("E023: " + e.getMessage()));
            } catch (StorageQueryException e) {
                throw new StorageTransactionLogicException(e);
            } catch (FeatureNotEnabledException e) {
                throw new StorageTransactionLogicException(new Exception("E024: " + e.getMessage()));
            } catch (UnknownUserIdException e) {
                throw new StorageTransactionLogicException(
                        new Exception("E025: We tried to link the userId " + lm.getSuperTokenOrExternalUserId()
                                + " to the primary userId " + primaryLM.getSuperTokenOrExternalUserId()
                                + " but it doesn't exist."));
            } catch (InputUserIdIsNotAPrimaryUserException e) {
                throw new StorageTransactionLogicException(
                        new Exception("E026: We tried to link the userId " + lm.getSuperTokenOrExternalUserId()
                                + " to the primary userId " + primaryLM.getSuperTokenOrExternalUserId()
                                + " but it is not a primary user."));
            } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
                throw new StorageTransactionLogicException(new Exception(
                        "E027: We tried to link the userId " + lm.getSuperTokenOrExternalUserId()
                                + " to the primary userId " + primaryLM.getSuperTokenOrExternalUserId()
                                + " but the account info is already associated with another primary user."));
            } catch (RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException e) {
                throw new StorageTransactionLogicException(new Exception(
                        "E028: We tried to link the userId " + lm.getSuperTokenOrExternalUserId()
                                + " to the primary userId " + primaryLM.getSuperTokenOrExternalUserId()
                                + " but it is already linked with another primary user."));
            }
        }
    }

    public static void createUserIdMapping(AppIdentifier appIdentifier,
            BulkImportUser user, LoginMethod primaryLM, Storage[] storages) throws StorageTransactionLogicException {
        if (user.externalUserId != null) {
            try {
                UserIdMapping.createUserIdMapping(
                        appIdentifier, storages,
                        primaryLM.superTokensUserId, user.externalUserId,
                        null, false, true);

                primaryLM.externalUserId = user.externalUserId;
            } catch (TenantOrAppNotFoundException e) {
                throw new StorageTransactionLogicException(new Exception("E029: " + e.getMessage()));
            } catch (StorageQueryException e) {
                throw new StorageTransactionLogicException(e);
            } catch (ServletException e) {
                throw new StorageTransactionLogicException(new Exception("E030: " + e.getMessage()));
            } catch (UserIdMappingAlreadyExistsException e) {
                throw new StorageTransactionLogicException(
                        new Exception("E031: A user with externalId " + user.externalUserId + " already exists"));
            } catch (UnknownSuperTokensUserIdException e) {
                throw new StorageTransactionLogicException(
                        new Exception("E032: We tried to create the externalUserId mapping for the superTokenUserId "
                                + primaryLM.superTokensUserId
                                + " but it doesn't exist. This should not happen. Please contact support."));
            }
        }
    }

    public static void createUserMetadata(AppIdentifier appIdentifier, Storage storage, BulkImportUser user,
            LoginMethod primaryLM) throws StorageTransactionLogicException {
        if (user.userMetadata != null) {
            try {
                UserMetadata.updateUserMetadata(appIdentifier, storage, primaryLM.getSuperTokenOrExternalUserId(),
                        user.userMetadata);
            } catch (TenantOrAppNotFoundException e) {
                throw new StorageTransactionLogicException(new Exception("E040: " + e.getMessage()));
            } catch (StorageQueryException e) {
                throw new StorageTransactionLogicException(e);
            }
        }
    }

    public static void createUserRoles(Main main, AppIdentifier appIdentifier, Storage storage,
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
                } catch (TenantOrAppNotFoundException e) {
                    throw new StorageTransactionLogicException(new Exception("E033: " + e.getMessage()));
                } catch (StorageQueryException e) {
                    throw new StorageTransactionLogicException(e);
                } catch (UnknownRoleException e) {
                    throw new StorageTransactionLogicException(new Exception("E034: Role " + userRole.role
                            + " does not exist! You need pre-create the role before assigning it to the user."));
                }
            }
        }
    }

    public static void verifyEmailForAllLoginMethods(AppIdentifier appIdentifier, TransactionConnection con,
            Storage storage,
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
            } catch (TenantOrAppNotFoundException e) {
                throw new StorageTransactionLogicException(new Exception("E035: " + e.getMessage()));
            } catch (StorageQueryException e) {
                throw new StorageTransactionLogicException(e);
            }
        }
    }

    public static void createTotpDevices(Main main, AppIdentifier appIdentifier, Storage storage,
            BulkImportUser user, LoginMethod primaryLM) throws StorageTransactionLogicException {
        if (user.totpDevices != null) {
            for (TotpDevice totpDevice : user.totpDevices) {
                try {
                    Totp.createDevice(main, appIdentifier, storage, primaryLM.getSuperTokenOrExternalUserId(),
                            totpDevice.deviceName, totpDevice.skew, totpDevice.period, totpDevice.secretKey,
                            true, System.currentTimeMillis());
                } catch (TenantOrAppNotFoundException e) {
                    throw new StorageTransactionLogicException(new Exception("E036: " + e.getMessage()));
                } catch (StorageQueryException e) {
                    throw new StorageTransactionLogicException(e);
                } catch (FeatureNotEnabledException e) {
                    throw new StorageTransactionLogicException(new Exception("E037: " + e.getMessage()));
                } catch (DeviceAlreadyExistsException e) {
                    throw new StorageTransactionLogicException(
                            new Exception(
                                    "E038: A totp device with name " + totpDevice.deviceName + " already exists"));
                }
            }
        }
    }

    // Returns the primary loginMethod of the user. If no loginMethod is marked as
    // primary, then the oldest loginMethod is returned.
    public static BulkImportUser.LoginMethod getPrimaryLoginMethod(BulkImportUser user) {
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

    private static synchronized Storage getBulkImportProxyStorage(Main main, TenantIdentifier tenantIdentifier)
            throws InvalidConfigException, IOException, TenantOrAppNotFoundException, DbInitException {
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
                return bulkImportProxyStorage;
            }
        }
        throw new TenantOrAppNotFoundException(tenantIdentifier);
    }

    private static Storage[] getAllProxyStoragesForApp(Main main, AppIdentifier appIdentifier)
            throws StorageTransactionLogicException {

        try {
            List<Storage> allProxyStorages = new ArrayList<>();

            TenantConfig[] tenantConfigs = Multitenancy.getAllTenantsForApp(appIdentifier, main);
            for (TenantConfig tenantConfig : tenantConfigs) {
                allProxyStorages.add(getBulkImportProxyStorage(main, tenantConfig.tenantIdentifier));
            }
            return allProxyStorages.toArray(new Storage[0]);
        } catch (TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(new Exception("E039: " + e.getMessage()));
        } catch (InvalidConfigException e) {
            throw new StorageTransactionLogicException(new InvalidConfigException("E040: " + e.getMessage()));
        } catch (DbInitException e) {
            throw new StorageTransactionLogicException(new DbInitException("E041: " + e.getMessage()));
        } catch (IOException e) {
            throw new StorageTransactionLogicException(new IOException("E042: " + e.getMessage()));
        }
    }

    private static void closeAllProxyStorages() throws StorageQueryException {
        for (SQLStorage storage : userPoolToStorageMap.values()) {
            storage.closeConnectionForBulkImportProxyStorage();
            storage.close();
        }
        userPoolToStorageMap.clear();
    }
}
