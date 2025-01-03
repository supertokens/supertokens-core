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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.exception.AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.InputUserIdIsNotAPrimaryUserException;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithPrimaryUserIdException;
import io.supertokens.config.Config;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.PasswordHashing;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.AnotherPrimaryUserWithEmailAlreadyExistsException;
import io.supertokens.multitenancy.exception.AnotherPrimaryUserWithPhoneNumberAlreadyExistsException;
import io.supertokens.multitenancy.exception.AnotherPrimaryUserWithThirdPartyInfoAlreadyExistsException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage.BULK_IMPORT_USER_STATUS;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.TotpDevice;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.UserRole;
import io.supertokens.pluginInterface.bulkimport.ImportUserBase;
import io.supertokens.pluginInterface.bulkimport.exceptions.BulkImportBatchInsertException;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportSQLStorage;
import io.supertokens.pluginInterface.emailpassword.EmailPasswordImportUser;
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
import io.supertokens.pluginInterface.passwordless.PasswordlessImportUser;
import io.supertokens.pluginInterface.passwordless.exception.DuplicatePhoneNumberException;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.pluginInterface.thirdparty.ThirdPartyImportUser;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateThirdPartyUserException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.pluginInterface.useridmapping.exception.UnknownSuperTokensUserIdException;
import io.supertokens.pluginInterface.useridmapping.exception.UserIdMappingAlreadyExistsException;
import io.supertokens.pluginInterface.userroles.exception.UnknownRoleException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.totp.Totp;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.userroles.UserRoles;
import io.supertokens.utils.Utils;
import jakarta.servlet.ServletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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
    public static final int PROCESS_USERS_BATCH_SIZE = 8000;
    // Time interval in seconds between two consecutive runs of ProcessBulkImportUsers Cron Job
    public static final int PROCESS_USERS_INTERVAL_SECONDS = 5*60; // 5 minutes
    private static final Logger log = LoggerFactory.getLogger(BulkImport.class);

    // This map allows reusing proxy storage for all tenants in the app and closing connections after import.
    private static Map<String, SQLStorage> userPoolToStorageMap = new HashMap<>();

    public static void addUsers(AppIdentifier appIdentifier, Storage storage, List<BulkImportUser> users)
            throws StorageQueryException, TenantOrAppNotFoundException {
        while (true) {
            try {
                StorageUtils.getBulkImportStorage(storage).addBulkImportUsers(appIdentifier, users);
                break;
            } catch (StorageQueryException sqe) {
                if(sqe.getCause() instanceof io.supertokens.pluginInterface.bulkimport.exceptions.DuplicateUserIdException) {
                    // We re-generate the user id for every user and retry
                    for (BulkImportUser user : users) {
                        user.id = Utils.getUUID();
                    }
                    continue;
                }
                throw sqe;
            } catch (io.supertokens.pluginInterface.bulkimport.exceptions.DuplicateUserIdException e) {
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
            DbInitException, BulkImportBatchInsertException {
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

                    processUsersImportSteps(main, appIdentifier, bulkImportProxyStorage, List.of(user), allStoragesForApp);

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
            if(e.actualException instanceof BulkImportBatchInsertException){
                throw (BulkImportBatchInsertException) e.actualException;
            }
            throw new StorageQueryException(e.actualException);
        }
    }

    public static void processUsersImportSteps(Main main, AppIdentifier appIdentifier,
            Storage bulkImportProxyStorage, List<BulkImportUser> users, Storage[] allStoragesForApp)
            throws StorageTransactionLogicException {
        try {
            processUsersLoginMethods(main, appIdentifier, bulkImportProxyStorage, users);
            createPrimaryUsersAndLinkAccounts(main, appIdentifier, bulkImportProxyStorage, users);
            createMultipleUserIdMapping(appIdentifier, users, allStoragesForApp);
            verifyMultipleEmailForAllLoginMethods(appIdentifier, bulkImportProxyStorage, users);
            createMultipleTotpDevices(main, appIdentifier, bulkImportProxyStorage, users);
            createMultipleUserMetadata(appIdentifier, bulkImportProxyStorage, users);
            createMultipleUserRoles(main, appIdentifier, bulkImportProxyStorage, users);
        } catch ( StorageQueryException | FeatureNotEnabledException |
                  TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(e);
        }
    }

    public static void processUsersLoginMethods(Main main, AppIdentifier appIdentifier, Storage storage,
                                              List<BulkImportUser> users) throws StorageTransactionLogicException {
        //sort login methods together
        Map<String, List<LoginMethod>> sortedLoginMethods = new HashMap<>();
        for (BulkImportUser user: users) {
            for(LoginMethod loginMethod : user.loginMethods){
                if(!sortedLoginMethods.containsKey(loginMethod.recipeId)) {
                    sortedLoginMethods.put(loginMethod.recipeId, new ArrayList<>());
                }
                sortedLoginMethods.get(loginMethod.recipeId).add(loginMethod);
            }
        }

            List<ImportUserBase> importedUsers = new ArrayList<>();
            if (sortedLoginMethods.containsKey("emailpassword")) {
                importedUsers.addAll(
                        processEmailPasswordLoginMethods(main, storage, sortedLoginMethods.get("emailpassword"),
                                appIdentifier));
            }
            if (sortedLoginMethods.containsKey("thirdparty")) {
                importedUsers.addAll(
                        processThirdpartyLoginMethods(main, storage, sortedLoginMethods.get("thirdparty"),
                                appIdentifier));
            }
            if (sortedLoginMethods.containsKey("passwordless")) {
                importedUsers.addAll(processPasswordlessLoginMethods(main, appIdentifier, storage,
                        sortedLoginMethods.get("passwordless")));
            }
            Set<String> actualKeys = new HashSet<>(sortedLoginMethods.keySet());
            List.of("emailpassword", "thirdparty", "passwordless").forEach(actualKeys::remove);
            if(!actualKeys.isEmpty()){
                throw new StorageTransactionLogicException(
                        new IllegalArgumentException("E001: Unknown recipeId(s) [" +
                                actualKeys.stream().map(s -> s+" ") + "] for loginMethod."));
            }

            Map<String, Exception> errorsById = new HashMap<>();
            for (Map.Entry<String, List<LoginMethod>> loginMethodEntries : sortedLoginMethods.entrySet()) {
                for (LoginMethod loginMethod : loginMethodEntries.getValue()) {
                    try {
                        associateUserToTenants(main, appIdentifier, storage, loginMethod, loginMethod.tenantIds.get(0));
                    } catch (StorageTransactionLogicException e){
                        errorsById.put(loginMethod.superTokensUserId, e.actualException);
                    }
                }
            }
            if(!errorsById.isEmpty()){
                throw new StorageTransactionLogicException(new BulkImportBatchInsertException("tenant association errors", errorsById));
            }
    }

    private static List<? extends ImportUserBase> processPasswordlessLoginMethods(Main main, AppIdentifier appIdentifier, Storage storage,
                                                                              List<LoginMethod> loginMethods)
            throws StorageTransactionLogicException {
        try {
            List<PasswordlessImportUser> usersToImport = new ArrayList<>();
            for (LoginMethod loginMethod : loginMethods) {
                String userId = Utils.getUUID();
                TenantIdentifier tenantIdentifierForLoginMethod = new TenantIdentifier(
                        appIdentifier.getConnectionUriDomain(),
                        appIdentifier.getAppId(), loginMethod.tenantIds.get(
                        0)); // the cron runs per app. The app stays the same, the tenant can change
               usersToImport.add(new PasswordlessImportUser(userId, loginMethod.phoneNumber,
                        loginMethod.email, tenantIdentifierForLoginMethod, loginMethod.timeJoinedInMSSinceEpoch));
                loginMethod.superTokensUserId = userId;
            }

            Passwordless.createPasswordlessUsers(storage, usersToImport);

            return usersToImport;
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            if (e.getCause() instanceof BulkImportBatchInsertException) {
                Map<String, Exception> errorsByPosition = ((BulkImportBatchInsertException) e.getCause()).exceptionByUserId;
                for (String userid : errorsByPosition.keySet()) {
                    Exception exception = errorsByPosition.get(userid);
                    if (exception instanceof DuplicateEmailException) {
                        String message = "E006: A user with email "
                                + loginMethods.stream()
                                .filter(loginMethod -> loginMethod.superTokensUserId.equals(userid))
                                .findFirst().get().email + " already exists in passwordless loginMethod.";
                        errorsByPosition.put(userid, new Exception(message));
                    } else if (exception instanceof DuplicatePhoneNumberException) {
                        String message = "E007: A user with phoneNumber "
                                + loginMethods.stream()
                                .filter(loginMethod -> loginMethod.superTokensUserId.equals(userid))
                                .findFirst().get().phoneNumber + " already exists in passwordless loginMethod.";
                        errorsByPosition.put(userid, new Exception(message));
                    }
                }
                throw new StorageTransactionLogicException(
                        new BulkImportBatchInsertException("translated", errorsByPosition));
            }
            throw new StorageTransactionLogicException(e);
        } catch (TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(new Exception("E008: " + e.getMessage()));
        }
    }

    private static List<? extends ImportUserBase> processThirdpartyLoginMethods(Main main, Storage storage, List<LoginMethod> loginMethods,
                                                      AppIdentifier appIdentifier)
            throws StorageTransactionLogicException {
        try {
            List<ThirdPartyImportUser> usersToImport = new ArrayList<>();
            for (LoginMethod loginMethod: loginMethods){
                String userId = Utils.getUUID();
                TenantIdentifier tenantIdentifierForLoginMethod =  new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                        appIdentifier.getAppId(), loginMethod.tenantIds.get(0)); // the cron runs per app. The app stays the same, the tenant can change

                usersToImport.add(new ThirdPartyImportUser(loginMethod.email, userId, loginMethod.thirdPartyId,
                        loginMethod.thirdPartyUserId, tenantIdentifierForLoginMethod, loginMethod.timeJoinedInMSSinceEpoch));
                loginMethod.superTokensUserId = userId;
            }
            ThirdParty.createMultipleThirdPartyUsers(storage, usersToImport);

            return usersToImport;
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            if (e.getCause() instanceof BulkImportBatchInsertException) {
                Map<String, Exception> errorsByPosition = ((BulkImportBatchInsertException) e.getCause()).exceptionByUserId;
                for (String userid : errorsByPosition.keySet()) {
                    Exception exception = errorsByPosition.get(userid);
                    if (exception instanceof DuplicateThirdPartyUserException) {
                        LoginMethod loginMethodForError = loginMethods.stream()
                                .filter(loginMethod -> loginMethod.superTokensUserId.equals(userid))
                                .findFirst().get();
                        String message = "E005: A user with thirdPartyId " + loginMethodForError.thirdPartyId
                                + " and thirdPartyUserId " + loginMethodForError.thirdPartyUserId
                                + " already exists in thirdparty loginMethod.";
                        errorsByPosition.put(userid, new Exception(message));
                    }
                }
                throw new StorageTransactionLogicException(
                        new BulkImportBatchInsertException("translated", errorsByPosition));
            }
            throw new StorageTransactionLogicException(e);
        } catch (TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(new Exception("E004: " + e.getMessage()));
        }
    }

    private static List<? extends ImportUserBase>  processEmailPasswordLoginMethods(Main main, Storage storage, List<LoginMethod> loginMethods,
                                                               AppIdentifier appIdentifier)
            throws StorageTransactionLogicException {
        try {

            //prepare data for batch import
            List<EmailPasswordImportUser> usersToImport = new ArrayList<>();
            for(LoginMethod emailPasswordLoginMethod : loginMethods) {

                TenantIdentifier tenantIdentifierForLoginMethod =  new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                        appIdentifier.getAppId(), emailPasswordLoginMethod.tenantIds.get(0)); // the cron runs per app. The app stays the same, the tenant can change

                String passwordHash = emailPasswordLoginMethod.passwordHash;
                if (passwordHash == null && emailPasswordLoginMethod.plainTextPassword != null) {
                    passwordHash = PasswordHashing.getInstance(main)
                            .createHashWithSalt(tenantIdentifierForLoginMethod.toAppIdentifier(), emailPasswordLoginMethod.plainTextPassword);
                }
                emailPasswordLoginMethod.passwordHash = passwordHash;
                String userId = Utils.getUUID();
                usersToImport.add(new EmailPasswordImportUser(userId, emailPasswordLoginMethod.email,
                        emailPasswordLoginMethod.passwordHash, tenantIdentifierForLoginMethod, emailPasswordLoginMethod.timeJoinedInMSSinceEpoch));
                emailPasswordLoginMethod.superTokensUserId = userId;
            }

            EmailPassword.createMultipleUsersWithPasswordHash(storage, usersToImport);

            return usersToImport;
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            if(e.getCause() instanceof BulkImportBatchInsertException){
                Map<String, Exception> errorsByPosition = ((BulkImportBatchInsertException) e.getCause()).exceptionByUserId;
                for(String userid : errorsByPosition.keySet()){
                    Exception exception = errorsByPosition.get(userid);
                    if(exception instanceof DuplicateEmailException){
                        String message = "E003: A user with email "
                                + loginMethods.stream().filter(loginMethod -> loginMethod.superTokensUserId.equals(userid))
                                .findFirst().get().email + " already exists in emailpassword loginMethod.";
                        errorsByPosition.put(userid, new Exception(message));
                    }
                }
                throw new StorageTransactionLogicException(new BulkImportBatchInsertException("translated", errorsByPosition));
            }
            throw new StorageTransactionLogicException(e);
        } catch (TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(new Exception("E002: " + e.getMessage()));
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

    private static void createPrimaryUsersAndLinkAccounts(Main main,
                                                          AppIdentifier appIdentifier, Storage storage,
                                                          List<BulkImportUser> users)
            throws StorageTransactionLogicException, StorageQueryException, FeatureNotEnabledException,
            TenantOrAppNotFoundException {
        List<String> userIds =
                users.stream()
                        .map(bulkImportUser -> getPrimaryLoginMethod(bulkImportUser).getSuperTokenOrExternalUserId())
                        .collect(Collectors.toList());
        Set<String> allEmails = new HashSet<>();
        Set<String> allPhoneNumber = new HashSet<>();
        Map<String, String> allThirdParty = new HashMap<>();
        for (BulkImportUser user : users) {
            for (LoginMethod loginMethod : user.loginMethods) {
                if (loginMethod.email != null) {
                    allEmails.add(loginMethod.email);
                }
                if (loginMethod.phoneNumber != null) {
                    allPhoneNumber.add(loginMethod.phoneNumber);
                }
                if (loginMethod.thirdPartyId != null && loginMethod.thirdPartyUserId != null) {
                    allThirdParty.put(loginMethod.thirdPartyUserId, loginMethod.thirdPartyId);
                }

            }
        }

        try {
            AuthRecipe.createPrimaryUsers(main, appIdentifier, storage, userIds, new ArrayList<>(allEmails),
                    new ArrayList<>(allPhoneNumber), allThirdParty);
        } catch (StorageQueryException e) {
            if(e.getCause() instanceof BulkImportBatchInsertException){
                Map<String, Exception> errorsByPosition = ((BulkImportBatchInsertException) e.getCause()).exceptionByUserId;
                for (String userid : errorsByPosition.keySet()) {
                    Exception exception = errorsByPosition.get(userid);
                    if (exception instanceof UnknownUserIdException) {
                        String message =  "E020: We tried to create the primary user for the userId "
                                + userid
                                + " but it doesn't exist. This should not happen. Please contact support.";
                        errorsByPosition.put(userid, new Exception(message));
                    } else if (exception instanceof RecipeUserIdAlreadyLinkedWithPrimaryUserIdException) {
                        String message = "E021: We tried to create the primary user for the userId "
                                + userid
                                + " but it is already linked with another primary user.";
                        errorsByPosition.put(userid, new Exception(message));
                    } else if (exception instanceof AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException) {
                        String message = "E022: We tried to create the primary user for the userId "
                                + userid
                                + " but the account info is already associated with another primary user.";
                        errorsByPosition.put(userid, new Exception(message));
                    }
                }
                throw new StorageTransactionLogicException(
                        new BulkImportBatchInsertException("translated", errorsByPosition));
            }
            throw new StorageTransactionLogicException(e);
        } catch (TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(new Exception("E018: " + e.getMessage()));
        } catch (FeatureNotEnabledException e) {
            throw new StorageTransactionLogicException(new Exception("E019: " + e.getMessage()));
        }

        linkAccountsForMultipleUser(main, appIdentifier, storage, users, new ArrayList<>(allEmails),
                    new ArrayList<>(allPhoneNumber), allThirdParty);
    }

    private static void linkAccountsForMultipleUser(Main main, AppIdentifier appIdentifier, Storage storage,
                                                    List<BulkImportUser> users,
                                                    List<String> allDistinctEmails,
                                                    List<String> allDistinctPhones,
                                                    Map<String, String> thirdpartyUserIdsToThirdpartyIds)
            throws StorageTransactionLogicException {
        Map<String, String> recipeUserIdByPrimaryUserId = collectRecipeIdsToPrimaryIds(users);
        try {
            AuthRecipe.linkMultipleAccounts(main, appIdentifier, storage, recipeUserIdByPrimaryUserId,
                    allDistinctEmails, allDistinctPhones, thirdpartyUserIdsToThirdpartyIds);
        } catch (TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(new Exception("E023: " + e.getMessage()));
        } catch (FeatureNotEnabledException e) {
            throw new StorageTransactionLogicException(new Exception("E024: " + e.getMessage()));
        } catch (StorageQueryException e) {
            if (e.getCause() instanceof BulkImportBatchInsertException) {
                Map<String, Exception> errorByPosition = ((BulkImportBatchInsertException) e.getCause()).exceptionByUserId;
                for (String userId : errorByPosition.keySet()) {
                    Exception currentException = errorByPosition.get(userId);
                    String recipeUID = recipeUserIdByPrimaryUserId.get(userId);
                    if (currentException instanceof UnknownUserIdException) {
                        String message = "E025: We tried to link the userId " + recipeUID
                                + " to the primary userId " + userId
                                + " but it doesn't exist.";
                        errorByPosition.put(userId, new Exception(message));
                    } else if (currentException instanceof InputUserIdIsNotAPrimaryUserException) {
                        String message = "E026: We tried to link the userId " + recipeUID
                                + " to the primary userId " + userId
                                + " but it is not a primary user.";
                        errorByPosition.put(userId, new Exception(message));
                    } else if (currentException instanceof AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException) {
                        String message = "E027: We tried to link the userId " + userId
                                + " to the primary userId " + recipeUID
                                + " but the account info is already associated with another primary user.";
                        errorByPosition.put(userId, new Exception(message));
                    } else if (currentException instanceof RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException) {
                        String message = "E028: We tried to link the userId " + recipeUID
                                + " to the primary userId " + userId
                                + " but it is already linked with another primary user.";
                        errorByPosition.put(userId, new Exception(message));
                    }
                }
                throw new StorageTransactionLogicException(
                        new BulkImportBatchInsertException("link accounts translated", errorByPosition));
            }
            throw new StorageTransactionLogicException(e);
        }
    }

    private static Map<String, String> collectRecipeIdsToPrimaryIds(List<BulkImportUser> users) {
        Map<String, String> recipeUserIdByPrimaryUserId = new HashMap<>();
        for(BulkImportUser user: users){
            LoginMethod primaryLM = getPrimaryLoginMethod(user);
            for (LoginMethod lm : user.loginMethods) {
                if (lm.getSuperTokenOrExternalUserId().equals(primaryLM.getSuperTokenOrExternalUserId())) {
                    continue;
                }
                recipeUserIdByPrimaryUserId.put(lm.getSuperTokenOrExternalUserId(),
                        primaryLM.getSuperTokenOrExternalUserId());
            }
        }
        return recipeUserIdByPrimaryUserId;
    }

    public static void createMultipleUserIdMapping(AppIdentifier appIdentifier,
                                           List<BulkImportUser> users, Storage[] storages) throws StorageTransactionLogicException {
        Map<String, String> superTokensUserIdToExternalUserId = new HashMap<>();
        for(BulkImportUser user: users) {
            if(user.externalUserId != null) {
                LoginMethod primaryLoginMethod = getPrimaryLoginMethod(user);
                superTokensUserIdToExternalUserId.put(primaryLoginMethod.superTokensUserId, user.externalUserId);
                primaryLoginMethod.externalUserId = user.externalUserId;
            }
        }
        try {
            List<UserIdMapping.UserIdBulkMappingResult> mappingResults = UserIdMapping.createMultipleUserIdMappings(
                    appIdentifier, storages,
                    superTokensUserIdToExternalUserId,
                    false,  true);

        } catch (StorageQueryException e) {
            if(e.getCause() instanceof BulkImportBatchInsertException) {
                Map<String, Exception> errorsByPosition = ((BulkImportBatchInsertException) e.getCause()).exceptionByUserId;
                for (String userid : errorsByPosition.keySet()) {
                    Exception exception = errorsByPosition.get(userid);
                    if (exception instanceof ServletException) {
                        String message =  "E030: " + e.getMessage();
                        errorsByPosition.put(userid, new Exception(message));
                    } else if (exception instanceof UserIdMappingAlreadyExistsException) {
                        String message = "E031: A user with externalId " + superTokensUserIdToExternalUserId.get(userid) + " already exists";
                        errorsByPosition.put(userid, new Exception(message));
                    } else if (exception instanceof UnknownSuperTokensUserIdException) {
                        String message = "E032: We tried to create the externalUserId mapping for the superTokenUserId "
                                + userid
                                + " but it doesn't exist. This should not happen. Please contact support.";
                        errorsByPosition.put(userid, new Exception(message));
                    }
                }
                throw new StorageTransactionLogicException(
                        new BulkImportBatchInsertException("translated", errorsByPosition));
            }
            throw new StorageTransactionLogicException(e);
        }
    }

    public static void createMultipleUserMetadata(AppIdentifier appIdentifier, Storage storage, List<BulkImportUser> users)
            throws StorageTransactionLogicException {

        Map<String, JsonObject> usersMetadata = new HashMap<>();
        for(BulkImportUser user: users) {
            if (user.userMetadata != null) {
                usersMetadata.put(getPrimaryLoginMethod(user).getSuperTokenOrExternalUserId(), user.userMetadata);
            }
        }

        try {
            UserMetadata.updateMultipleUsersMetadata(appIdentifier, storage, usersMetadata);
        } catch (TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(new Exception("E040: " + e.getMessage()));
        } catch (StorageQueryException e) {
            throw new StorageTransactionLogicException(e);
        }
    }

    public static void createMultipleUserRoles(Main main, AppIdentifier appIdentifier, Storage storage,
                                               List<BulkImportUser> users) throws StorageTransactionLogicException {
        Map<TenantIdentifier, Map<String, List<String>>> rolesToUserByTenant = new HashMap<>();
        for (BulkImportUser user : users) {

            if (user.userRoles != null) {
                for (UserRole userRole : user.userRoles) {
                    for (String tenantId : userRole.tenantIds) {
                        TenantIdentifier tenantIdentifier = new TenantIdentifier(
                                appIdentifier.getConnectionUriDomain(), appIdentifier.getAppId(),
                                tenantId);
                        if(!rolesToUserByTenant.containsKey(tenantIdentifier)){

                            rolesToUserByTenant.put(tenantIdentifier, new HashMap<>());
                        }
                        if(!rolesToUserByTenant.get(tenantIdentifier).containsKey(user.externalUserId)){
                            rolesToUserByTenant.get(tenantIdentifier).put(user.externalUserId, new ArrayList<>());
                        }
                        rolesToUserByTenant.get(tenantIdentifier).get(user.externalUserId).add(userRole.role);
                    }
                }
            }
        }
        try {

            UserRoles.addMultipleRolesToMultipleUsers(main, appIdentifier, storage, rolesToUserByTenant);
        } catch (TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(new Exception("E033: " + e.getMessage()));
        } catch (StorageTransactionLogicException e) {
            if(e.actualException instanceof BulkImportBatchInsertException){
                Map<String, Exception> errorsByPosition = ((BulkImportBatchInsertException) e.getCause()).exceptionByUserId;
                for (String userid : errorsByPosition.keySet()) {
                    Exception exception = errorsByPosition.get(userid);
                    if (exception instanceof UnknownRoleException) {
                        String message = "E034: Role does not exist! You need to pre-create the role before " +
                                "assigning it to the user.";
                        errorsByPosition.put(userid, new Exception(message));
                    }
                }
                throw new StorageTransactionLogicException(new BulkImportBatchInsertException("roles errors translated", errorsByPosition));
            } else {
                throw new StorageTransactionLogicException(e);
            }
        }

    }

    public static void verifyMultipleEmailForAllLoginMethods(AppIdentifier appIdentifier, Storage storage,
                                                             List<BulkImportUser> users)
            throws StorageTransactionLogicException {
        Map<String, String> emailToUserId = new HashMap<>();
        for (BulkImportUser user : users) {
            for (LoginMethod lm : user.loginMethods) {
                emailToUserId.put(lm.getSuperTokenOrExternalUserId(), lm.email);
            }
        }

        try {

            EmailVerificationSQLStorage emailVerificationSQLStorage = StorageUtils
                    .getEmailVerificationStorage(storage);
            emailVerificationSQLStorage.startTransaction(con -> {
                emailVerificationSQLStorage
                        .updateMultipleIsEmailVerified_Transaction(appIdentifier, con,
                                emailToUserId, true);

                emailVerificationSQLStorage.commitTransaction(con);
                return null;
            });

        } catch (StorageQueryException e) {
            throw new StorageTransactionLogicException(e);
        }
    }

    public static void createMultipleTotpDevices(Main main, AppIdentifier appIdentifier,
                                                 Storage storage, List<BulkImportUser> users)
            throws StorageTransactionLogicException {
        List<TOTPDevice> devices = new ArrayList<>();
        for (BulkImportUser user : users) {
            if (user.totpDevices != null) {
                for(TotpDevice device : user.totpDevices){
                    TOTPDevice totpDevice = new TOTPDevice(getPrimaryLoginMethod(user).getSuperTokenOrExternalUserId(),
                            device.deviceName, device.secretKey, device.period, device.skew, true,
                            System.currentTimeMillis());
                    devices.add(totpDevice);
                }
            }
        }
        try {
            Totp.createDevices(main, appIdentifier, storage, devices);
        } catch (StorageQueryException e) {
            throw new StorageTransactionLogicException(new Exception("E036: " + e.getMessage()));
        } catch (FeatureNotEnabledException e) {
            throw new StorageTransactionLogicException(new Exception("E037: " + e.getMessage()));
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
