/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.authRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.authRecipe.exception.BulkImportRecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.InputUserIdIsNotAPrimaryUserException;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithPrimaryUserIdException;
import io.supertokens.bulkimport.BulkImportUserUtils;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.CanBecomePrimaryResult;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.authRecipe.exceptions.AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.exceptions.BulkImportBatchInsertException;
import io.supertokens.pluginInterface.dashboard.DashboardSearchTags;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.pluginInterface.sqlStorage.TransactionConnection;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.session.Session;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.Utils;

/*This files contains functions that are common for all auth recipes*/

public class AuthRecipe {

    public static final int USER_PAGINATION_LIMIT = 500;

    @TestOnly
    public static boolean unlinkAccounts(Main main, String recipeUserId)
            throws StorageQueryException, UnknownUserIdException, InputUserIdIsNotAPrimaryUserException {
        return unlinkAccounts(main, ResourceDistributor.getAppForTesting().toAppIdentifier(), StorageLayer.getStorage(main), recipeUserId);
    }


    // returns true if the input user ID was deleted - which can happens if it was a primary user id and
    // there were other accounts linked to it as well.
    public static boolean unlinkAccounts(Main main, AppIdentifier appIdentifier,
                                         Storage storage, String recipeUserId)
            throws StorageQueryException, UnknownUserIdException, InputUserIdIsNotAPrimaryUserException {
        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        try {
            UnlinkResult res = authRecipeStorage.startTransaction(con -> {
                AuthRecipeUserInfo primaryUser = authRecipeStorage.getPrimaryUserById_Transaction(appIdentifier, con,
                        recipeUserId);
                if (primaryUser == null) {
                    throw new StorageTransactionLogicException(new UnknownUserIdException());
                }

                if (!primaryUser.isPrimaryUser) {
                    throw new StorageTransactionLogicException(new InputUserIdIsNotAPrimaryUserException(recipeUserId));
                }

                io.supertokens.pluginInterface.useridmapping.UserIdMapping mappingResult =
                        io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                                appIdentifier, authRecipeStorage,
                                recipeUserId, UserIdType.SUPERTOKENS);

                if (primaryUser.getSupertokensUserId().equals(recipeUserId)) {
                    // we are trying to unlink the user ID which is the same as the primary one.
                    if (primaryUser.loginMethods.length == 1) {
                        authRecipeStorage.unlinkAccounts_Transaction(appIdentifier, con,
                                primaryUser.getSupertokensUserId(), recipeUserId);
                        return new UnlinkResult(mappingResult == null ? recipeUserId : mappingResult.externalUserId,
                                false);
                    } else {
                        // Here we delete the recipe user id cause if we just unlink, then there will be two
                        // distinct users with the same ID - which is a broken state.
                        // The delete will also cause the automatic unlinking.
                        // We need to make sure that it only deletes sessions for recipeUserId and not other linked
                        // users who have their sessions for primaryUserId (that is equal to the recipeUserId)
                        deleteUserHelper(con, appIdentifier, storage, recipeUserId, false, mappingResult);
                        return new UnlinkResult(mappingResult == null ? recipeUserId : mappingResult.externalUserId,
                                true);
                    }
                } else {
                    authRecipeStorage.unlinkAccounts_Transaction(appIdentifier, con, primaryUser.getSupertokensUserId(),
                            recipeUserId);
                    return new UnlinkResult(mappingResult == null ? recipeUserId : mappingResult.externalUserId, false);
                }
            });
            Session.revokeAllSessionsForUser(main, appIdentifier, storage, res.userId, false);
            return res.wasLinked;
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof UnknownUserIdException) {
                throw (UnknownUserIdException) e.actualException;
            } else if (e.actualException instanceof InputUserIdIsNotAPrimaryUserException) {
                throw (InputUserIdIsNotAPrimaryUserException) e.actualException;
            }
            throw new RuntimeException(e);
        }
    }

    @TestOnly
    public static AuthRecipeUserInfo getUserById(Main main, String userId)
            throws StorageQueryException {
        return getUserById(ResourceDistributor.getAppForTesting().toAppIdentifier(), StorageLayer.getStorage(main), userId);
    }

    public static AuthRecipeUserInfo getUserById(AppIdentifier appIdentifier, Storage storage, String userId)
            throws StorageQueryException {
        return StorageUtils.getAuthRecipeStorage(storage).getPrimaryUserById(appIdentifier, userId);
    }

    public static List<AuthRecipeUserInfo> getUsersById(AppIdentifier appIdentifier, Storage storage, List<String> userIds)
            throws StorageQueryException {
        AuthRecipeSQLStorage authStorage = StorageUtils.getAuthRecipeStorage(storage);
        try {
            return authStorage.startTransaction(con -> {
                return authStorage.getPrimaryUsersByIds_Transaction(appIdentifier, con, userIds);
            });
        } catch (StorageTransactionLogicException e) {
            throw new StorageQueryException(e);
        }
    }

    public static class CreatePrimaryUserResult {
        public AuthRecipeUserInfo user;
        public boolean wasAlreadyAPrimaryUser;

        public CreatePrimaryUserResult(AuthRecipeUserInfo user, boolean wasAlreadyAPrimaryUser) {
            this.user = user;
            this.wasAlreadyAPrimaryUser = wasAlreadyAPrimaryUser;
        }
    }

    public static class CreatePrimaryUserBulkResult {
        public BulkImportUser user;
        public BulkImportUser.LoginMethod primaryLoginMethod;
        public boolean wasAlreadyAPrimaryUser;
        public Exception error;

        public CreatePrimaryUserBulkResult(BulkImportUser user, BulkImportUser.LoginMethod primaryLoginMethod,
                                           boolean wasAlreadyAPrimaryUser, Exception error) {
            this.user = user;
            this.primaryLoginMethod = primaryLoginMethod;
            this.wasAlreadyAPrimaryUser = wasAlreadyAPrimaryUser;
            this.error = error;
        }
    }

    public static class CanLinkAccountsResult {
        public String recipeUserId;
        public String primaryUserId;
        public boolean alreadyLinked;

        public CanLinkAccountsResult(String recipeUserId, String primaryUserId, boolean alreadyLinked) {
            this.recipeUserId = recipeUserId;
            this.primaryUserId = primaryUserId;
            this.alreadyLinked = alreadyLinked;
        }
    }

    public static class CanLinkAccountsBulkResult {
        public String recipeUserId;
        public String primaryUserId;
        public Exception error;
        public BulkImportUser bulkImportUser;
        public boolean alreadyLinked;

        public CanLinkAccountsBulkResult(String recipeUserId, String primaryUserId, boolean alreadyLinked, Exception error,
                                         BulkImportUser bulkImportUser) {
            this.recipeUserId = recipeUserId;
            this.primaryUserId = primaryUserId;
            this.alreadyLinked = alreadyLinked;
            this.error = error;
            this.bulkImportUser = bulkImportUser;
        }
    }

    @TestOnly
    public static CanLinkAccountsResult canLinkAccounts(Main main, String recipeUserId, String primaryUserId)
            throws StorageQueryException, UnknownUserIdException, InputUserIdIsNotAPrimaryUserException,
            RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException,
            AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException {
        return canLinkAccounts(ResourceDistributor.getAppForTesting().toAppIdentifier(), StorageLayer.getStorage(main), recipeUserId,
                primaryUserId);
    }

    public static CanLinkAccountsResult canLinkAccounts(AppIdentifier appIdentifier, Storage storage,
                                                        String recipeUserId, String primaryUserId)
            throws StorageQueryException, UnknownUserIdException, InputUserIdIsNotAPrimaryUserException,
            RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException,
            AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException {
        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        try {
            return authRecipeStorage.startTransaction(con -> {
                try {
                    CanLinkAccountsResult result = canLinkAccountsHelper(con, appIdentifier, authRecipeStorage,
                            recipeUserId, primaryUserId);

                    authRecipeStorage.commitTransaction(con);

                    return result;
                } catch (UnknownUserIdException | InputUserIdIsNotAPrimaryUserException |
                         RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException |
                         AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
                    throw new StorageTransactionLogicException(e);
                }
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof UnknownUserIdException) {
                throw (UnknownUserIdException) e.actualException;
            } else if (e.actualException instanceof InputUserIdIsNotAPrimaryUserException) {
                throw (InputUserIdIsNotAPrimaryUserException) e.actualException;
            } else if (e.actualException instanceof RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException) {
                throw (RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException) e.actualException;
            } else if (e.actualException instanceof AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException) {
                throw (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException) e.actualException;
            }
            throw new StorageQueryException(e);
        }
    }

    private static CanLinkAccountsResult canLinkAccountsHelper(TransactionConnection con,
                                                               AppIdentifier appIdentifier,
                                                               Storage storage,
                                                               String _recipeUserId, String _primaryUserId)
            throws StorageQueryException, UnknownUserIdException, InputUserIdIsNotAPrimaryUserException,
            RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException,
            AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException {
        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        AuthRecipeUserInfo primaryUser = authRecipeStorage.getPrimaryUserById_Transaction(appIdentifier, con,
                _primaryUserId);

        if (primaryUser == null) {
            throw new UnknownUserIdException();
        }

        if (!primaryUser.isPrimaryUser) {
            throw new InputUserIdIsNotAPrimaryUserException(primaryUser.getSupertokensUserId());
        }

        AuthRecipeUserInfo recipeUser = authRecipeStorage.getPrimaryUserById_Transaction(appIdentifier, con,
                _recipeUserId);
        if (recipeUser == null) {
            throw new UnknownUserIdException();
        }

        if (recipeUser.isPrimaryUser) {
            if (recipeUser.getSupertokensUserId().equals(primaryUser.getSupertokensUserId())) {
                return new CanLinkAccountsResult(recipeUser.getSupertokensUserId(), primaryUser.getSupertokensUserId(),
                        true);
            } else {
                throw new RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException(recipeUser,
                        "The input recipe user ID is already linked to another user ID");
            }
        }

        // now we know that the recipe user ID is not a primary user, so we can focus on it's one
        // login method
        assert (recipeUser.loginMethods.length == 1);
        
        Set<String> tenantIds = new HashSet<>();
        tenantIds.addAll(recipeUser.tenantIds);
        tenantIds.addAll(primaryUser.tenantIds);

        Set<String> emails = new HashSet<>();
        Set<String> phoneNumbers = new HashSet<>();
        Set<LoginMethod.ThirdParty> thirdParties = new HashSet<>();

        for (var lm : primaryUser.loginMethods) {
            if (lm.email != null) {
                emails.add(lm.email);
            }
            if (lm.phoneNumber != null) {
                phoneNumbers.add(lm.phoneNumber);
            }
            if (lm.thirdParty != null) {
                thirdParties.add(lm.thirdParty);
            }
        }
        for (var lm : recipeUser.loginMethods) {
            if (lm.email != null) {
                emails.add(lm.email);
            }
            if (lm.phoneNumber != null) {
                phoneNumbers.add(lm.phoneNumber);
            }
            if (lm.thirdParty != null) {
                thirdParties.add(lm.thirdParty);
            }
        }

        io.supertokens.pluginInterface.authRecipe.CanLinkAccountsResult canLinkResult =
                authRecipeStorage.checkIfLoginMethodsCanBeLinked_Transaction(con, appIdentifier, tenantIds, emails,
                        phoneNumbers, thirdParties, primaryUser.getSupertokensUserId());
        if (!canLinkResult.ok) {
            throw new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException(
                    canLinkResult.primaryUserId, canLinkResult.message);
        }
        return new CanLinkAccountsResult(recipeUser.getSupertokensUserId(), primaryUser.getSupertokensUserId(), false);
    }

    private static List<CanLinkAccountsBulkResult> canLinkMultipleAccountsHelperForBulkImport(TransactionConnection con,
                                                                                              AppIdentifier appIdentifier,
                                                                                              Storage storage,
                                                                                              List<BulkImportUser> users,
                                                                                              List<AuthRecipeUserInfo> allUsersWithExtraData)
            throws StorageQueryException {
        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);

        List<CanLinkAccountsBulkResult> results = new ArrayList<>();

        Map<String, String> recipeUserIdByPrimaryUserId = BulkImportUserUtils.collectRecipeIdsToPrimaryIds(users);

        if(recipeUserIdByPrimaryUserId != null && !recipeUserIdByPrimaryUserId.isEmpty()) {

            for(Map.Entry<String, String> recipeUserByPrimaryUser : recipeUserIdByPrimaryUserId.entrySet()) {
                String recipeUserId = recipeUserByPrimaryUser.getKey();
                String primaryUserId = recipeUserByPrimaryUser.getValue();
                BulkImportUser.LoginMethod primaryUser = BulkImportUserUtils.findLoginMethodByRecipeUserId(users, primaryUserId);
                BulkImportUser.LoginMethod recipeUser = BulkImportUserUtils.findLoginMethodByRecipeUserId(users, recipeUserId);
                if(primaryUser == null || recipeUser == null) {
                    results.add(new CanLinkAccountsBulkResult(recipeUserId, primaryUserId, false, new UnknownUserIdException(), null));
                } else if(recipeUser.isPrimary) {
                    if (recipeUser.superTokensUserId.equals(primaryUser.superTokensUserId)) {
                        results.add(new CanLinkAccountsBulkResult(recipeUserId, primaryUserId, true, null, null));
                    } else {
                        results.add(new CanLinkAccountsBulkResult(recipeUserId, primaryUserId, false,
                                new BulkImportRecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException(recipeUserId), null));
                    }
                } else {

                    Set<String> tenantIds = new HashSet<>();
                    tenantIds.addAll(recipeUser.tenantIds);
                    tenantIds.addAll(primaryUser.tenantIds);

                    try {
                        bulkCheckIfLoginMethodCanBeLinkedOnTenant(con, appIdentifier, authRecipeStorage, tenantIds,
                                recipeUser, primaryUserId, allUsersWithExtraData);
                        BulkImportUser currentPrimaryUser = BulkImportUserUtils.findUserByPrimaryId(users, primaryUserId);
                        for (BulkImportUser.LoginMethod currLoginMethod : currentPrimaryUser.loginMethods) {
                            bulkCheckIfLoginMethodCanBeLinkedOnTenant(con, appIdentifier, authRecipeStorage, tenantIds,
                                    currLoginMethod, primaryUserId, allUsersWithExtraData);
                        }

                        results.add(new CanLinkAccountsBulkResult(recipeUserId, primaryUserId, false, null, currentPrimaryUser));

                    } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException exception) {
                        results.add(new CanLinkAccountsBulkResult(recipeUserId, primaryUserId, false, exception, null));
                    }

                }
            }
        }
        return results;
    }

    private static void bulkCheckIfLoginMethodCanBeLinkedOnTenant(TransactionConnection con, AppIdentifier appIdentifier,
                                                                  AuthRecipeSQLStorage authRecipeStorage,
                                                                  Set<String> tenantIds, BulkImportUser.LoginMethod currLoginMethod,
                                                                  String primaryUserId,
                                                                  List<AuthRecipeUserInfo> allUsersWithExtraData)
            throws StorageQueryException, AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException {
        // we loop through the union of both the user's tenantIds and check that the criteria for
        // linking accounts is not violated in any of them. We do a union and not an intersection
        // cause if we did an intersection, and that yields that account linking is allowed, it could
        // result in one tenant having two primary users with the same email. For example:
        // - tenant1 has u1 with email e, and u2 with email e, primary user (one is ep, one is tp)
        // - tenant2 has u3 with email e, primary user (passwordless)
        // now if we want to link u3 with u1, we have to deny it cause if we don't, it will result in
        // u1 and u2 to be primary users with the same email in the same tenant. If we do an
        // intersection, we will get an empty set, but if we do a union, we will get both the tenants and
        // do the checks in both.
        for (String tenantId : tenantIds) {
            // we do not bother with getting the storage for each tenant here because
            // we get the tenants from the user itself, and the user can only be shared across
            // tenants of the same storage - therefore, the storage will be the same.

            if (currLoginMethod.email != null) {
                List<AuthRecipeUserInfo> usersWithSameEmail =
                        allUsersWithExtraData.stream().filter(authRecipeUserInfo -> Arrays.stream(
                                authRecipeUserInfo.loginMethods).map(loginMethod -> loginMethod.email).collect(
                                Collectors.toList()).contains(currLoginMethod.email)).collect(Collectors.toList());
                for (AuthRecipeUserInfo user : usersWithSameEmail) {
                    if (!user.tenantIds.contains(tenantId)) {
                        continue;
                    }
                    if (user.isPrimaryUser && !user.getSupertokensUserId().equals(primaryUserId)) {
                        throw new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException(
                                user.getSupertokensUserId(),
                                "This user's email is already associated with another user ID");
                    }
                }
            }

            if (currLoginMethod.phoneNumber != null) {
                List<AuthRecipeUserInfo> usersWithSamePhoneNumber =
                        allUsersWithExtraData.stream().filter(authRecipeUserInfo -> Arrays.stream(
                                authRecipeUserInfo.loginMethods).map(loginMethod -> loginMethod.phoneNumber).collect(
                                Collectors.toList()).contains(currLoginMethod.phoneNumber)).collect(Collectors.toList());
                for (AuthRecipeUserInfo user : usersWithSamePhoneNumber) {
                    if (!user.tenantIds.contains(tenantId)) {
                        continue;
                    }
                    if (user.isPrimaryUser && !user.getSupertokensUserId().equals(primaryUserId)) {
                        throw new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException(
                                user.getSupertokensUserId(),
                                "This user's phone number is already associated with another user" +
                                        " ID");
                    }
                }
            }

            if (currLoginMethod.thirdPartyId != null) {
                List<AuthRecipeUserInfo> extraUsersWithThirdParty = allUsersWithExtraData.stream().filter(authRecipeUserInfo -> Arrays.stream(
                        authRecipeUserInfo.loginMethods).anyMatch(loginMethod1 -> loginMethod1.thirdParty != null)).collect(Collectors.toList());
                for(AuthRecipeUserInfo extraUser : extraUsersWithThirdParty) {
                    if(extraUser.isPrimaryUser && extraUser.tenantIds.contains(tenantId)
                            && !extraUser.getSupertokensUserId().equals(primaryUserId)) {
                        for (LoginMethod loginMethodExtra : extraUser.loginMethods) {
                            if (loginMethodExtra.thirdParty != null &&
                                    loginMethodExtra.thirdParty.userId.equals(currLoginMethod.thirdPartyUserId)
                                    && loginMethodExtra.thirdParty.id.equals(currLoginMethod.thirdPartyId)) {

                                throw new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException(
                                        extraUser.getSupertokensUserId(),
                                        "This user's third party login is already associated with another" +
                                                " user ID");
                            }
                        }
                    }
                }
            }
        }
    }

    @TestOnly
    public static LinkAccountsResult linkAccounts(Main main, String recipeUserId, String primaryUserId)
            throws StorageQueryException, AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            UnknownUserIdException,
            FeatureNotEnabledException, InputUserIdIsNotAPrimaryUserException,
            RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException {
        try {
            return linkAccounts(main, ResourceDistributor.getAppForTesting().toAppIdentifier(),
                    StorageLayer.getStorage(main), recipeUserId, primaryUserId);
        } catch (TenantOrAppNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static LinkAccountsResult linkAccounts(Main main, AppIdentifier appIdentifier,
                                                  Storage storage, String _recipeUserId, String _primaryUserId)
            throws StorageQueryException,
            AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException, InputUserIdIsNotAPrimaryUserException,
            UnknownUserIdException, TenantOrAppNotFoundException, FeatureNotEnabledException {

        if (!Utils.isAccountLinkingEnabled(main, appIdentifier)) {
            throw new FeatureNotEnabledException(
                    "Account linking feature is not enabled for this app. Please contact support to enable it.");
        }

        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        try {

            LinkAccountsResult result = authRecipeStorage.startTransaction(con -> {
                try {
                    CanLinkAccountsResult canLinkAccounts = canLinkAccountsHelper(con, appIdentifier,
                            authRecipeStorage, _recipeUserId, _primaryUserId);

                    if (canLinkAccounts.alreadyLinked) {
                        return new LinkAccountsResult(
                                getUserById(appIdentifier, authRecipeStorage, canLinkAccounts.primaryUserId), true);
                    }
                    // now we can link accounts in the db.
                    authRecipeStorage.linkAccounts_Transaction(appIdentifier, con, canLinkAccounts.recipeUserId,
                            canLinkAccounts.primaryUserId);

                    authRecipeStorage.commitTransaction(con);

                    return new LinkAccountsResult(
                            getUserById(appIdentifier, authRecipeStorage, canLinkAccounts.primaryUserId), false);
                } catch (UnknownUserIdException | InputUserIdIsNotAPrimaryUserException |
                         RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException |
                         AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
                    throw new StorageTransactionLogicException(e);
                }
            });

            if (!result.wasAlreadyLinked) {
                io.supertokens.pluginInterface.useridmapping.UserIdMapping mappingResult =
                        io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                                appIdentifier, authRecipeStorage,
                                _recipeUserId, UserIdType.SUPERTOKENS);
                // finally, we revoke all sessions of the recipeUser Id cause their user ID has changed.
                Session.revokeAllSessionsForUser(main, appIdentifier, authRecipeStorage,
                        mappingResult == null ? _recipeUserId : mappingResult.externalUserId, false);
            }

            return result;
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof UnknownUserIdException) {
                throw (UnknownUserIdException) e.actualException;
            } else if (e.actualException instanceof InputUserIdIsNotAPrimaryUserException) {
                throw (InputUserIdIsNotAPrimaryUserException) e.actualException;
            } else if (e.actualException instanceof RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException) {
                throw (RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException) e.actualException;
            } else if (e.actualException instanceof AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException) {
                throw (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException) e.actualException;
            }
            throw new StorageQueryException(e);
        }
    }

    public static void linkMultipleAccountsForBulkImport(Main main, AppIdentifier appIdentifier,
                                                         Storage storage,
                                                         List<BulkImportUser> users,
                                                         List<AuthRecipeUserInfo> usersWithSameExtraData)
            throws StorageQueryException, TenantOrAppNotFoundException, FeatureNotEnabledException {

        if (!Utils.isAccountLinkingEnabled(main, appIdentifier)) {
            throw new FeatureNotEnabledException(
                    "Account linking feature is not enabled for this app. Please contact support to enable it.");
        }

        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        Map<String, Exception> errorByUserId = new HashMap<>();
        try {

            authRecipeStorage.startTransaction(con -> {
                List<CanLinkAccountsBulkResult> canLinkAccounts = canLinkMultipleAccountsHelperForBulkImport(con, appIdentifier,
                        authRecipeStorage, users, usersWithSameExtraData);
                Map<String, String> recipeUserByPrimaryUserNeedsLinking = new HashMap<>();
                if(!canLinkAccounts.isEmpty()){
                    for(CanLinkAccountsBulkResult canLinkAccountsBulkResult : canLinkAccounts) {
                        if(!canLinkAccountsBulkResult.alreadyLinked && canLinkAccountsBulkResult.error != null) {
                            errorByUserId.put(canLinkAccountsBulkResult.recipeUserId, canLinkAccountsBulkResult.error);
                        } else {
                            recipeUserByPrimaryUserNeedsLinking.put(canLinkAccountsBulkResult.recipeUserId, canLinkAccountsBulkResult.primaryUserId);
                        }
                    }
                    // link the remaining
                    authRecipeStorage.linkMultipleAccounts_Transaction(appIdentifier, con, recipeUserByPrimaryUserNeedsLinking);

                    authRecipeStorage.commitTransaction(con);
                }
                if(!errorByUserId.isEmpty()) {
                    throw new StorageQueryException(new BulkImportBatchInsertException("link accounts errors", errorByUserId));
                }
                return null;
            });
        } catch (StorageTransactionLogicException e) {
            throw new StorageQueryException(e);
        }
    }

    public static class LinkAccountsResult {
        public final AuthRecipeUserInfo user;
        public final boolean wasAlreadyLinked;

        public LinkAccountsResult(AuthRecipeUserInfo user, boolean wasAlreadyLinked) {
            this.user = user;
            this.wasAlreadyLinked = wasAlreadyLinked;
        }
    }

    public static class LinkAccountsBulkResult {
        public final BulkImportUser user;
        public final boolean wasAlreadyLinked;
        public final Exception error;

        public LinkAccountsBulkResult(BulkImportUser user, boolean wasAlreadyLinked, Exception error) {
            this.user = user;
            this.wasAlreadyLinked = wasAlreadyLinked;
            this.error = error;
        }
    }

    @TestOnly
    public static CreatePrimaryUserResult canCreatePrimaryUser(Main main,
                                                               String recipeUserId)
            throws StorageQueryException, AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            RecipeUserIdAlreadyLinkedWithPrimaryUserIdException, UnknownUserIdException {
        return canCreatePrimaryUser(ResourceDistributor.getAppForTesting().toAppIdentifier(), StorageLayer.getStorage(main), recipeUserId);
    }

    public static CreatePrimaryUserResult canCreatePrimaryUser(AppIdentifier appIdentifier,
                                                               Storage storage,
                                                               String recipeUserId)
            throws StorageQueryException, AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            RecipeUserIdAlreadyLinkedWithPrimaryUserIdException, UnknownUserIdException {

        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        try {
            return authRecipeStorage.startTransaction(con -> {
                try {
                    return canCreatePrimaryUserHelper(con, appIdentifier, storage,
                            recipeUserId);

                } catch (UnknownUserIdException | RecipeUserIdAlreadyLinkedWithPrimaryUserIdException |
                         AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
                    throw new StorageTransactionLogicException(e);
                }
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof UnknownUserIdException) {
                throw (UnknownUserIdException) e.actualException;
            } else if (e.actualException instanceof RecipeUserIdAlreadyLinkedWithPrimaryUserIdException) {
                throw (RecipeUserIdAlreadyLinkedWithPrimaryUserIdException) e.actualException;
            } else if (e.actualException instanceof AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException) {
                throw (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException) e.actualException;
            }
            throw new StorageQueryException(e);
        }
    }

    private static CreatePrimaryUserResult canCreatePrimaryUserHelper(TransactionConnection con,
                                                                      AppIdentifier appIdentifier,
                                                                      Storage storage,
                                                                      String recipeUserId)
            throws StorageQueryException, UnknownUserIdException, RecipeUserIdAlreadyLinkedWithPrimaryUserIdException,
            AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException {
        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);

        AuthRecipeUserInfo targetUser = authRecipeStorage.getPrimaryUserById_Transaction(appIdentifier, con,
                recipeUserId);
        if (targetUser == null) {
            throw new UnknownUserIdException();
        }
        if (targetUser.isPrimaryUser) {
            if (targetUser.getSupertokensUserId().equals(recipeUserId)) {
                return new CreatePrimaryUserResult(targetUser, true);
            } else {
                throw new RecipeUserIdAlreadyLinkedWithPrimaryUserIdException(targetUser.getSupertokensUserId(),
                        "This user ID is already linked to another user ID");
            }
        }

        // this means that the user has only one login method since it's not a primary user
        // nor is it linked to a primary user
        assert (targetUser.loginMethods.length == 1);

        CanBecomePrimaryResult result = authRecipeStorage.checkIfLoginMethodCanBecomePrimary_Transaction(appIdentifier,
                con, targetUser.loginMethods[0]);

        if (!result.ok) {
            throw new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException(result.primaryUserId, result.message);
        }

        return new CreatePrimaryUserResult(targetUser, false);
    }

    private static CreatePrimaryUsersResultHolder canCreatePrimaryUsersHelperForBulkImport(TransactionConnection con,
                                                                                              AppIdentifier appIdentifier,
                                                                                              Storage storage,
                                                                                              List<BulkImportUser> bulkImportUsers)
            throws StorageQueryException, UnknownUserIdException{

        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);

        if (bulkImportUsers == null || bulkImportUsers.isEmpty()) {
            throw new UnknownUserIdException();
        }

        DistinctAuthIdentifiers mailPhoneThirdParty = getDistinctAuthIdentifiers(bulkImportUsers);

        List<CreatePrimaryUserBulkResult> results = new ArrayList<>();
        List<AuthRecipeUserInfo> allUsersWithProvidedExtraData =
                List.of(authRecipeStorage.
                        listPrimaryUsersByMultipleEmailsOrPhoneNumbersOrThirdparty_Transaction(appIdentifier, con,
                                new ArrayList<>(mailPhoneThirdParty.allEmails), new ArrayList<>(mailPhoneThirdParty.allPhoneNumber),
                                mailPhoneThirdParty.allThirdParty)); // this is multiple - not so cheap DB query, but we need to do it

        for (BulkImportUser targetUser : bulkImportUsers) {
            BulkImportUser.LoginMethod primaryLoginMethod = BulkImportUserUtils.getPrimaryLoginMethod(targetUser);

            for (BulkImportUser.LoginMethod loginMethod : targetUser.loginMethods) {
                // note here: account takeover risk checks are done in the sdk. The situation in which someone registers
                // for example with a thirparty which also verifies email address and later someone else tries to register
                // with the same email address but with emailpassword is not handled here. This is because the sdk
                // will handle this. In the bulk import we have no means to check this.
                boolean errorFound = false;
                for (String tenantId : loginMethod.tenantIds) {
                    if (loginMethod.email != null) {
                        List<AuthRecipeUserInfo> usersWithSameEmail = allUsersWithProvidedExtraData.stream()
                                .filter(authRecipeUserInfo -> Arrays.stream(
                                                authRecipeUserInfo.loginMethods).map(loginMethod1 -> loginMethod1.email)
                                        .collect(Collectors.toList()).contains(loginMethod.email)).collect(
                                        Collectors.toList());
                        for (AuthRecipeUserInfo user : usersWithSameEmail) {
                            if (!user.tenantIds.contains(tenantId)) {
                                continue;
                            }
                            if (user.isPrimaryUser) {
                                results.add(new CreatePrimaryUserBulkResult(targetUser, primaryLoginMethod, false,
                                        new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException(
                                                user.getSupertokensUserId(),
                                                "This user's email is already associated with another user ID")));
                                errorFound = true;
                                break;
                            }
                        }
                    }

                    if (loginMethod.phoneNumber != null) {
                        List<AuthRecipeUserInfo> usersWithSamePhoneNumber = allUsersWithProvidedExtraData.stream()
                                .filter(authRecipeUserInfo -> Arrays.stream(
                                                authRecipeUserInfo.loginMethods).map(loginMethod1 -> loginMethod1.phoneNumber)
                                        .collect(Collectors.toList()).contains(loginMethod.phoneNumber)).collect(
                                        Collectors.toList());
                        for (AuthRecipeUserInfo user : usersWithSamePhoneNumber) {
                            if (!user.tenantIds.contains(tenantId)) {
                                continue;
                            }
                            if (user.isPrimaryUser) {
                                results.add(new CreatePrimaryUserBulkResult(targetUser, primaryLoginMethod, false,
                                        new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException(
                                                user.getSupertokensUserId(),
                                                "This user's phone number is already associated with another user" +
                                                        " ID")));
                                errorFound = true;
                                break;
                            }
                        }
                    }

                    if (loginMethod.thirdPartyId != null && loginMethod.thirdPartyUserId != null) {
                        List<AuthRecipeUserInfo> extraUsersWithThirdParty = allUsersWithProvidedExtraData.stream()
                                .filter(authRecipeUserInfo -> Arrays.stream(
                                                authRecipeUserInfo.loginMethods)
                                        .anyMatch(loginMethod1 -> loginMethod1.thirdParty != null))
                                .collect(Collectors.toList());
                        for (AuthRecipeUserInfo extraUser : extraUsersWithThirdParty) {
                            if (extraUser.isPrimaryUser && extraUser.tenantIds.contains(tenantId)) {
                                for (LoginMethod loginMethodExtra : extraUser.loginMethods) {
                                    if (loginMethodExtra.thirdParty != null &&
                                            loginMethodExtra.thirdParty.userId.equals(loginMethod.thirdPartyUserId)
                                            && loginMethodExtra.thirdParty.id.equals(loginMethod.thirdPartyId)) {

                                        results.add(
                                                new CreatePrimaryUserBulkResult(targetUser, primaryLoginMethod, false,
                                                        new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException(
                                                                extraUser.getSupertokensUserId(),
                                                                "This user's third party login is already associated with another" +
                                                                        " user ID")));
                                        errorFound = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (!errorFound) {
                        results.add(new CreatePrimaryUserBulkResult(targetUser, primaryLoginMethod, false, null));
                    }
                }
            }
        }
        CreatePrimaryUsersResultHolder resultHolder = new CreatePrimaryUsersResultHolder();
        resultHolder.createPrimaryUserBulkResults = results;
        resultHolder.usersWithSameExtraData = allUsersWithProvidedExtraData;
        return resultHolder;
    }

    @NotNull
    private static DistinctAuthIdentifiers getDistinctAuthIdentifiers(List<BulkImportUser> bulkImportUsers) {
        Set<String> allEmails = new HashSet<>();
        Set<String> allPhoneNumber = new HashSet<>();
        Map<String, String> allThirdParty = new HashMap<>();
        for (BulkImportUser user : bulkImportUsers) {
            for (BulkImportUser.LoginMethod loginMethod : user.loginMethods) {
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
        DistinctAuthIdentifiers mailPhoneThirdparty = new DistinctAuthIdentifiers(allEmails, allPhoneNumber, allThirdParty);
        return mailPhoneThirdparty;
    }

    private static class DistinctAuthIdentifiers {
        public final Set<String> allEmails;
        public final Set<String> allPhoneNumber;
        public final Map<String, String> allThirdParty;

        public DistinctAuthIdentifiers(Set<String> allEmails, Set<String> allPhoneNumber, Map<String, String> allThirdParty) {
            this.allEmails = allEmails;
            this.allPhoneNumber = allPhoneNumber;
            this.allThirdParty = allThirdParty;
        }
    }


    @TestOnly
    public static CreatePrimaryUserResult createPrimaryUser(Main main,
                                                            String recipeUserId)
            throws StorageQueryException, AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            RecipeUserIdAlreadyLinkedWithPrimaryUserIdException, UnknownUserIdException,
            FeatureNotEnabledException {
        try {
            return createPrimaryUser(main, ResourceDistributor.getAppForTesting().toAppIdentifier(), StorageLayer.getStorage(main), recipeUserId);
        } catch (TenantOrAppNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static CreatePrimaryUserResult createPrimaryUser(Main main,
                                                            AppIdentifier appIdentifier,
                                                            Storage storage,
                                                            String recipeUserId)
            throws StorageQueryException, AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            RecipeUserIdAlreadyLinkedWithPrimaryUserIdException, UnknownUserIdException, TenantOrAppNotFoundException,
            FeatureNotEnabledException {

        if (!Utils.isAccountLinkingEnabled(main, appIdentifier)) {
            throw new FeatureNotEnabledException(
                    "Account linking feature is not enabled for this app. Please contact support to enable it.");
        }

        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        try {
            return authRecipeStorage.startTransaction(con -> {

                try {
                    CreatePrimaryUserResult result = canCreatePrimaryUserHelper(con, appIdentifier, authRecipeStorage,
                            recipeUserId);
                    if (result.wasAlreadyAPrimaryUser) {
                        return result;
                    }
                    authRecipeStorage.makePrimaryUser_Transaction(appIdentifier, con,
                            result.user.getSupertokensUserId());

                    authRecipeStorage.commitTransaction(con);

                    result.user.isPrimaryUser = true;

                    return result;
                } catch (UnknownUserIdException | RecipeUserIdAlreadyLinkedWithPrimaryUserIdException |
                         AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
                    throw new StorageTransactionLogicException(e);
                }
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof UnknownUserIdException) {
                throw (UnknownUserIdException) e.actualException;
            } else if (e.actualException instanceof RecipeUserIdAlreadyLinkedWithPrimaryUserIdException) {
                throw (RecipeUserIdAlreadyLinkedWithPrimaryUserIdException) e.actualException;
            } else if (e.actualException instanceof AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException) {
                throw (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException) e.actualException;
            }
            throw new StorageQueryException(e);
        }
    }

    //helper class to return together the results of primary user creation and the users with the same extradata (email, phone, etc)
    public static class CreatePrimaryUsersResultHolder {
        public List<CreatePrimaryUserBulkResult> createPrimaryUserBulkResults;
        public List<AuthRecipeUserInfo> usersWithSameExtraData;
    }

    public static CreatePrimaryUsersResultHolder createPrimaryUsersForBulkImport(Main main,
                                                                                    AppIdentifier appIdentifier,
                                                                                    Storage storage,
                                                                                    List<BulkImportUser> bulkImportUsers)
            throws StorageQueryException, TenantOrAppNotFoundException,
            FeatureNotEnabledException {

        if (!Utils.isAccountLinkingEnabled(main, appIdentifier)) {
            throw new FeatureNotEnabledException(
                    "Account linking feature is not enabled for this app. Please contact support to enable it.");
        }

        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        Map<String, Exception> errorsByUserId = new HashMap<>();
        try {
            return authRecipeStorage.startTransaction(con -> {

                try {
                    CreatePrimaryUsersResultHolder resultHolder = canCreatePrimaryUsersHelperForBulkImport(con, appIdentifier, authRecipeStorage,
                            bulkImportUsers);
                    List<CreatePrimaryUserBulkResult> results = resultHolder.createPrimaryUserBulkResults;
                    List<CreatePrimaryUserBulkResult> canMakePrimaryUsers = new ArrayList<>();
                    for(CreatePrimaryUserBulkResult result : results) {
                        if (result.wasAlreadyAPrimaryUser) {
                            continue;
                        }
                        if(result.error != null) {
                            errorsByUserId.put(result.user.id, result.error);
                            continue;
                        }
                        canMakePrimaryUsers.add(result);
                    }
                    authRecipeStorage.makePrimaryUsers_Transaction(appIdentifier, con,
                            canMakePrimaryUsers.stream().map(canMakePrimaryUser -> canMakePrimaryUser.user.id).collect(
                                    Collectors.toList()));

                    authRecipeStorage.commitTransaction(con);

                    for(CreatePrimaryUserBulkResult result : results) {
                        if (result.wasAlreadyAPrimaryUser) {
                            continue;
                        }
                        if(result.error != null) {
                            errorsByUserId.put(result.user.id, result.error);
                            continue;
                        }
                        result.primaryLoginMethod.isPrimary = true;
                        result.user.primaryUserId = result.primaryLoginMethod.superTokensUserId;
                    }

                    if(!errorsByUserId.isEmpty()) {
                        throw new StorageTransactionLogicException(new BulkImportBatchInsertException("create primary users errors", errorsByUserId));
                    }

                    return resultHolder;
                } catch (UnknownUserIdException e) {
                    throw new StorageTransactionLogicException(e);
                }
            });
        } catch (StorageTransactionLogicException  e) {
            throw new StorageQueryException(e.actualException);
        }
    }

    public static AuthRecipeUserInfo[] getUsersByAccountInfo(TenantIdentifier tenantIdentifier,
                                                             Storage storage,
                                                             boolean doUnionOfAccountInfo, String email,
                                                             String phoneNumber, String thirdPartyId,
                                                             String thirdPartyUserId,
                                                             String webauthnCredentialId)
            throws StorageQueryException {
        Set<AuthRecipeUserInfo> result = loadAuthRecipeUserInfosByVariousIds(
                tenantIdentifier, storage, email, phoneNumber, thirdPartyId, thirdPartyUserId, webauthnCredentialId);

        if (doUnionOfAccountInfo) {
            return mergeAuthRecipeUserInfosResultWithORMatch(result); // matches any of the provided: email, thirdparty, phone number, webauthnCredential
        } else {
            return mergeAuthRecipeUserInfosResultWithANDMatch(email, phoneNumber, thirdPartyId, thirdPartyUserId, webauthnCredentialId,
                    result); // matches all the provided: email, thirdparty, phone number, webauthnCredential
        }

    }

    private static AuthRecipeUserInfo[] mergeAuthRecipeUserInfosResultWithANDMatch(String email, String phoneNumber,
                                                                                             String thirdPartyId, String thirdPartyUserId,
                                                                                             String webauthnCredentialId,
                                                                                             Set<AuthRecipeUserInfo> result) {
        List<AuthRecipeUserInfo> finalList = new ArrayList<>();
        for (AuthRecipeUserInfo user : result) {
            boolean emailMatch = email == null;
            boolean phoneNumberMatch = phoneNumber == null;
            boolean thirdPartyMatch = thirdPartyId == null;
            boolean webauthnCredentialIdMatch = webauthnCredentialId == null;
            for (LoginMethod lM : user.loginMethods) {
                if (email != null && email.equals(lM.email)) {
                    emailMatch = true;
                }
                if (phoneNumber != null && phoneNumber.equals(lM.phoneNumber)) {
                    phoneNumberMatch = true;
                }
                if (thirdPartyId != null &&
                        (new LoginMethod.ThirdParty(thirdPartyId, thirdPartyUserId)).equals(lM.thirdParty)) {
                    thirdPartyMatch = true;
                }
                if(webauthnCredentialId != null
                        && lM.webauthN != null
                        && lM.webauthN.credentialIds.contains(webauthnCredentialId)){
                    webauthnCredentialIdMatch = true;
                }
            }
            if (emailMatch && phoneNumberMatch && thirdPartyMatch && webauthnCredentialIdMatch) {
                finalList.add(user);
            }
        }
        finalList.sort((o1, o2) -> {
            if (o1.timeJoined < o2.timeJoined) {
                return -1;
            } else if (o1.timeJoined > o2.timeJoined) {
                return 1;
            }
            return 0;
        });
        return finalList.toArray(new AuthRecipeUserInfo[0]);
    }

    private static AuthRecipeUserInfo[] mergeAuthRecipeUserInfosResultWithORMatch(Set<AuthRecipeUserInfo> result) {
        AuthRecipeUserInfo[] finalResult = result.toArray(new AuthRecipeUserInfo[0]);
        return Arrays.stream(finalResult).sorted((o1, o2) -> {
            if (o1.timeJoined < o2.timeJoined) {
                return -1;
            } else if (o1.timeJoined > o2.timeJoined) {
                return 1;
            }
            return 0;
        }).toArray(AuthRecipeUserInfo[]::new);
    }

    @NotNull
    private static Set<AuthRecipeUserInfo> loadAuthRecipeUserInfosByVariousIds(TenantIdentifier tenantIdentifier, Storage storage,
                                                                  String email, String phoneNumber, String thirdPartyId,
                                                                  String thirdPartyUserId, String webauthnCredentialId)
            throws StorageQueryException {
        Set<AuthRecipeUserInfo> result = new HashSet<>();

        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        if (email != null) {
            AuthRecipeUserInfo[] users = authRecipeStorage
                    .listPrimaryUsersByEmail(tenantIdentifier, email);
            result.addAll(List.of(users));
        }
        if (phoneNumber != null) {
            AuthRecipeUserInfo[] users = authRecipeStorage
                    .listPrimaryUsersByPhoneNumber(tenantIdentifier, phoneNumber);
            result.addAll(List.of(users));
        }
        if (thirdPartyId != null && thirdPartyUserId != null) {
            AuthRecipeUserInfo user = authRecipeStorage
                    .getPrimaryUserByThirdPartyInfo(tenantIdentifier, thirdPartyId, thirdPartyUserId);
            if (user != null) {
                result.add(user);
            }
        }
        if(webauthnCredentialId != null){
            AuthRecipeUserInfo user = authRecipeStorage
                    .getPrimaryUserByWebauthNCredentialId(tenantIdentifier, webauthnCredentialId);
            if (user != null) {
                result.add(user);
            }
        }
        return result;
    }

    public static long getUsersCountForTenant(TenantIdentifier tenantIdentifier,
                                              Storage storage,
                                              RECIPE_ID[] includeRecipeIds)
            throws StorageQueryException,
            TenantOrAppNotFoundException, BadPermissionException {
        return StorageUtils.getAuthRecipeStorage(storage).getUsersCount(
                tenantIdentifier, includeRecipeIds);
    }

    public static long getUsersCountAcrossAllTenants(AppIdentifier appIdentifier,
                                                     Storage[] storages,
                                                     RECIPE_ID[] includeRecipeIds)
            throws StorageQueryException,
            TenantOrAppNotFoundException, BadPermissionException {
        long count = 0;

        for (Storage storage : storages) {
            count += StorageUtils.getAuthRecipeStorage(storage).getUsersCount(
                    appIdentifier, includeRecipeIds);
        }

        return count;
    }

    @TestOnly
    public static long getUsersCount(Main main,
                                     RECIPE_ID[] includeRecipeIds) throws StorageQueryException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return getUsersCountForTenant(ResourceDistributor.getAppForTesting(), storage, includeRecipeIds);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static UserPaginationContainer getUsers(TenantIdentifier tenantIdentifier,
                                                   Storage storage,
                                                   Integer limit, String timeJoinedOrder,
                                                   @Nullable String paginationToken,
                                                   @Nullable RECIPE_ID[] includeRecipeIds,
                                                   @Nullable DashboardSearchTags dashboardSearchTags)
            throws StorageQueryException, UserPaginationToken.InvalidTokenException, TenantOrAppNotFoundException {
        AuthRecipeUserInfo[] users;
        if (paginationToken == null) {
            users = StorageUtils.getAuthRecipeStorage(storage)
                    .getUsers(tenantIdentifier, limit + 1, timeJoinedOrder, includeRecipeIds, null,
                            null, dashboardSearchTags);
        } else {
            UserPaginationToken tokenInfo = UserPaginationToken.extractTokenInfo(paginationToken);
            users = StorageUtils.getAuthRecipeStorage(storage)
                    .getUsers(tenantIdentifier, limit + 1, timeJoinedOrder, includeRecipeIds,
                            tokenInfo.userId, tokenInfo.timeJoined, dashboardSearchTags);
        }

        if (dashboardSearchTags != null) {
            return new UserPaginationContainer(users, null);
        }

        String nextPaginationToken = null;
        int maxLoop = users.length;
        if (users.length == limit + 1) {
            maxLoop = limit;
            nextPaginationToken = new UserPaginationToken(users[limit].getSupertokensUserId(),
                    users[limit].timeJoined).generateToken();
        }
        AuthRecipeUserInfo[] resultUsers = new AuthRecipeUserInfo[maxLoop];
        System.arraycopy(users, 0, resultUsers, 0, maxLoop);
        return new UserPaginationContainer(resultUsers, nextPaginationToken);
    }

    @TestOnly
    public static UserPaginationContainer getUsers(Main main,
                                                   Integer limit, String timeJoinedOrder,
                                                   @Nullable String paginationToken,
                                                   @Nullable RECIPE_ID[] includeRecipeIds,
                                                   @Nullable DashboardSearchTags dashboardSearchTags)
            throws StorageQueryException, UserPaginationToken.InvalidTokenException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return getUsers(ResourceDistributor.getAppForTesting(), storage,
                    limit, timeJoinedOrder, paginationToken, includeRecipeIds, dashboardSearchTags);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static void deleteUser(AppIdentifier appIdentifier, Storage storage, String userId,
                                  UserIdMapping userIdMapping)
            throws StorageQueryException, StorageTransactionLogicException {
        deleteUser(appIdentifier, storage, userId, true, userIdMapping);
    }

    public static void deleteUser(AppIdentifier appIdentifier, Storage storage, String userId,
                                  boolean removeAllLinkedAccounts,
                                  UserIdMapping userIdMapping)
            throws StorageQueryException, StorageTransactionLogicException {
        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);

        authRecipeStorage.startTransaction(con -> {
            deleteUserHelper(con, appIdentifier, storage, userId, removeAllLinkedAccounts, userIdMapping);
            authRecipeStorage.commitTransaction(con);
            return null;
        });
    }

    private static void deleteUserHelper(TransactionConnection con, AppIdentifier appIdentifier,
                                         Storage storage,
                                         String userId,
                                         boolean removeAllLinkedAccounts,
                                         UserIdMapping userIdMapping)
            throws StorageQueryException {
        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);

        String userIdToDeleteForNonAuthRecipeForRecipeUserId;
        String userIdToDeleteForAuthRecipe;

        // We clean up the user last so that if anything before that throws an error, then that will throw a
        // 500 to the
        // developer. In this case, they expect that the user has not been deleted (which will be true). This
        // is as
        // opposed to deleting the user first, in which case if something later throws an error, then the
        // user has

        // actually been deleted already (which is not expected by the dev)

        // For things created after the intial cleanup and before finishing the
        // operation:
        // - session: the session will expire anyway
        // - email verification: email verification tokens can be created for any userId
        // anyway

        // If userId mapping exists then delete entries with superTokensUserId from auth
        // related tables and
        // externalUserid from non-auth tables
        if (userIdMapping != null) {
            // We check if the mapped externalId is another SuperTokens UserId, this could
            // come up when migrating
            // recipes.
            // in reference to
            // https://docs.google.com/spreadsheets/d/17hYV32B0aDCeLnSxbZhfRN2Y9b0LC2xUF44vV88RNAA/edit?usp=sharing
            // we want to check which state the db is in
            if (authRecipeStorage
                    .doesUserIdExist_Transaction(con, appIdentifier, userIdMapping.externalUserId)) {
                // db is in state A4
                // delete only from auth tables
                userIdToDeleteForAuthRecipe = userId;
                userIdToDeleteForNonAuthRecipeForRecipeUserId = null;
            } else {
                // db is in state A3
                // delete user from non-auth tables with externalUserId
                userIdToDeleteForAuthRecipe = userIdMapping.superTokensUserId;
                userIdToDeleteForNonAuthRecipeForRecipeUserId = userIdMapping.externalUserId;
            }
        } else {
            userIdToDeleteForAuthRecipe = userId;
            userIdToDeleteForNonAuthRecipeForRecipeUserId = userId;
        }

        assert (userIdToDeleteForAuthRecipe != null);

        // this user ID represents the non auth recipe stuff to delete for the primary user id
        String primaryUserIdToDeleteNonAuthRecipe = null;

        AuthRecipeUserInfo userToDelete = authRecipeStorage.getPrimaryUserById_Transaction(appIdentifier, con,
                userIdToDeleteForAuthRecipe);

        if (userToDelete == null) {
            return;
        }

        if (removeAllLinkedAccounts || userToDelete.loginMethods.length == 1) {
            if (userToDelete.getSupertokensUserId().equals(userIdToDeleteForAuthRecipe)) {
                primaryUserIdToDeleteNonAuthRecipe = userIdToDeleteForNonAuthRecipeForRecipeUserId;
                if (primaryUserIdToDeleteNonAuthRecipe == null) {
                    deleteAuthRecipeUser(con, appIdentifier, storage, userToDelete.getSupertokensUserId(),
                            true);
                    return;
                }
            } else {
                // this is always type supertokens user ID cause it's from a user from the database.
                io.supertokens.pluginInterface.useridmapping.UserIdMapping mappingResult =
                        io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                                con,
                                appIdentifier,
                                storage,
                                userToDelete.getSupertokensUserId(), UserIdType.SUPERTOKENS);
                if (mappingResult != null) {
                    primaryUserIdToDeleteNonAuthRecipe = mappingResult.externalUserId;
                } else {
                    primaryUserIdToDeleteNonAuthRecipe = userToDelete.getSupertokensUserId();
                }

            }
        } else {
            if (userToDelete.getSupertokensUserId().equals(userIdToDeleteForAuthRecipe)) {
                // this means we are deleting the primary user itself, but keeping other linked accounts
                // so we keep the non auth recipe info of this user since other linked accounts can use it
                userIdToDeleteForNonAuthRecipeForRecipeUserId = null;
            }
        }

        if (!removeAllLinkedAccounts) {
            deleteAuthRecipeUser(con, appIdentifier, storage, userIdToDeleteForAuthRecipe,
                    !userIdToDeleteForAuthRecipe.equals(userToDelete.getSupertokensUserId()));

            if (userIdToDeleteForNonAuthRecipeForRecipeUserId != null) {
                deleteNonAuthRecipeUser(con, appIdentifier, storage, userIdToDeleteForNonAuthRecipeForRecipeUserId);
            }

            if (primaryUserIdToDeleteNonAuthRecipe != null) {
                deleteNonAuthRecipeUser(con, appIdentifier, storage, primaryUserIdToDeleteNonAuthRecipe);

                // this is only done to also delete the user ID mapping in case it exists, since we do not delete in the
                // previous call to deleteAuthRecipeUser above.
                deleteAuthRecipeUser(con, appIdentifier, storage, userToDelete.getSupertokensUserId(),
                        true);
            }
        } else {
            for (LoginMethod lM : userToDelete.loginMethods) {
                io.supertokens.pluginInterface.useridmapping.UserIdMapping mappingResult =
                        lM.getSupertokensUserId().equals(
                                userIdToDeleteForAuthRecipe) ? userIdMapping :
                                io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                                        con,
                                        appIdentifier,
                                        storage,
                                        lM.getSupertokensUserId(), UserIdType.SUPERTOKENS);
                deleteUserHelper(con, appIdentifier, storage, lM.getSupertokensUserId(), false, mappingResult);
            }
        }
    }

    @TestOnly
    public static void deleteUser(Main main, String userId, boolean removeAllLinkedAccounts)
            throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = ResourceDistributor.getAppForTesting().toAppIdentifier();
        UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(appIdentifier,
                storage, userId, UserIdType.ANY);

        deleteUser(appIdentifier, storage, userId, removeAllLinkedAccounts, mapping);
    }

    @TestOnly
    public static void deleteUser(Main main, String userId)
            throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = StorageLayer.getStorage(main);
        AppIdentifier appIdentifier = ResourceDistributor.getAppForTesting().toAppIdentifier();
        UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(appIdentifier,
                storage, userId, UserIdType.ANY);

        deleteUser(appIdentifier, storage, userId, mapping);
    }

    @TestOnly
    public static void deleteUser(AppIdentifier appIdentifier, Storage storage, String userId)
            throws StorageQueryException, StorageTransactionLogicException {
        UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(appIdentifier,
                storage, userId, UserIdType.ANY);

        deleteUser(appIdentifier, storage, userId, mapping);
    }

    private static void deleteNonAuthRecipeUser(TransactionConnection con, AppIdentifier appIdentifier,
                                                Storage storage, String userId)
            throws StorageQueryException {
        StorageUtils.getUserMetadataStorage(storage)
                .deleteUserMetadata_Transaction(con, appIdentifier, userId);
        ((SessionSQLStorage) StorageUtils.getSessionStorage(storage))
                .deleteSessionsOfUser_Transaction(con, appIdentifier, userId);
        StorageUtils.getEmailVerificationStorage(storage)
                .deleteEmailVerificationUserInfo_Transaction(con, appIdentifier, userId);
        StorageUtils.getUserRolesStorage(storage)
                .deleteAllRolesForUser_Transaction(con, appIdentifier, userId);

        StorageUtils.getActiveUsersStorage(storage)
                .deleteUserActive_Transaction(con, appIdentifier, userId);
        StorageUtils.getTOTPStorage(storage)
                .removeUser_Transaction(con, appIdentifier, userId);
    }

    private static void deleteAuthRecipeUser(TransactionConnection con,
                                             AppIdentifier appIdentifier,
                                             Storage storage,
                                             String userId, boolean deleteFromUserIdToAppIdTableToo)
            throws StorageQueryException {
        // auth recipe deletions here only
        StorageUtils.getEmailPasswordStorage(storage)
                .deleteEmailPasswordUser_Transaction(con, appIdentifier, userId, deleteFromUserIdToAppIdTableToo);
        StorageUtils.getThirdPartyStorage(storage)
                .deleteThirdPartyUser_Transaction(con, appIdentifier, userId, deleteFromUserIdToAppIdTableToo);
        StorageUtils.getPasswordlessStorage(storage)
                .deletePasswordlessUser_Transaction(con, appIdentifier, userId, deleteFromUserIdToAppIdTableToo);
        StorageUtils.getAuthRecipeStorage(storage)
                .deleteAccountInfoReservations_Transaction(con, appIdentifier, userId);
    }

    public static boolean deleteNonAuthRecipeUser(TenantIdentifier tenantIdentifier, Storage storage, String userId)
            throws StorageQueryException {

        // UserMetadata is per app, so nothing to delete

        boolean finalDidExist = false;
        boolean didExist = false;

        didExist = StorageUtils.getSessionStorage(storage)
                .deleteSessionsOfUser(tenantIdentifier, userId);
        finalDidExist = finalDidExist || didExist;

        didExist = StorageUtils.getEmailVerificationStorage(storage)
                .deleteEmailVerificationUserInfo(tenantIdentifier, userId);
        finalDidExist = finalDidExist || didExist;

        didExist = StorageUtils.getUserRolesStorage(storage)
                .deleteAllRolesForUser(tenantIdentifier, userId) > 0;
        finalDidExist = finalDidExist || didExist;

        didExist = StorageUtils.getTOTPStorage(storage)
                .removeUser(tenantIdentifier, userId);
        finalDidExist = finalDidExist || didExist;

        return finalDidExist;
    }

    private static class UnlinkResult {
        public final String userId;
        public final boolean wasLinked;

        public UnlinkResult(String userId, boolean wasLinked) {
            this.userId = userId;
            this.wasLinked = wasLinked;
        }
    }
}