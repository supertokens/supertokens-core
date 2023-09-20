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

import io.supertokens.Main;
import io.supertokens.authRecipe.exception.AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.InputUserIdIsNotAPrimaryUserException;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithPrimaryUserIdException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.dashboard.DashboardSearchTags;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.pluginInterface.sqlStorage.TransactionConnection;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.session.Session;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdType;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nullable;
import java.util.*;

/*This files contains functions that are common for all auth recipes*/

public class AuthRecipe {

    public static final int USER_PAGINATION_LIMIT = 500;

    @TestOnly
    public static boolean unlinkAccounts(Main main, String recipeUserId)
            throws StorageQueryException, UnknownUserIdException, InputUserIdIsNotAPrimaryUserException {
        AppIdentifierWithStorage appId = new AppIdentifierWithStorage(null, null,
                StorageLayer.getStorage(main));
        return unlinkAccounts(main, appId, recipeUserId);
    }


    // returns true if the input user ID was deleted - which can happens if it was a primary user id and
    // there were other accounts linked to it as well.
    public static boolean unlinkAccounts(Main main, AppIdentifierWithStorage appIdentifierWithStorage,
                                         String recipeUserId)
            throws StorageQueryException, UnknownUserIdException, InputUserIdIsNotAPrimaryUserException {
        AuthRecipeSQLStorage storage = (AuthRecipeSQLStorage) appIdentifierWithStorage.getAuthRecipeStorage();
        try {
            UnlinkResult res = storage.startTransaction(con -> {
                AuthRecipeUserInfo primaryUser = storage.getPrimaryUserById_Transaction(appIdentifierWithStorage, con,
                        recipeUserId);
                if (primaryUser == null) {
                    throw new StorageTransactionLogicException(new UnknownUserIdException());
                }

                if (!primaryUser.isPrimaryUser) {
                    throw new StorageTransactionLogicException(new InputUserIdIsNotAPrimaryUserException(recipeUserId));
                }

                io.supertokens.pluginInterface.useridmapping.UserIdMapping mappingResult =
                        io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                                appIdentifierWithStorage,
                                recipeUserId, UserIdType.SUPERTOKENS);

                if (primaryUser.getSupertokensUserId().equals(recipeUserId)) {
                    // we are trying to unlink the user ID which is the same as the primary one.
                    if (primaryUser.loginMethods.length == 1) {
                        storage.unlinkAccounts_Transaction(appIdentifierWithStorage, con, primaryUser.getSupertokensUserId(), recipeUserId);
                        return new UnlinkResult(mappingResult == null ? recipeUserId : mappingResult.externalUserId, false);
                    } else {
                        // Here we delete the recipe user id cause if we just unlink, then there will be two
                        // distinct users with the same ID - which is a broken state.
                        // The delete will also cause the automatic unlinking.
                        // We need to make sure that it only deletes sessions for recipeUserId and not other linked
                        // users who have their sessions for primaryUserId (that is equal to the recipeUserId)
                        deleteUserHelper(con, appIdentifierWithStorage, recipeUserId, false, mappingResult);
                        return new UnlinkResult(mappingResult == null ? recipeUserId : mappingResult.externalUserId, true);
                    }
                } else {
                    storage.unlinkAccounts_Transaction(appIdentifierWithStorage, con, primaryUser.getSupertokensUserId(), recipeUserId);
                    return new UnlinkResult(mappingResult == null ? recipeUserId : mappingResult.externalUserId, false);
                }
            });
            Session.revokeAllSessionsForUser(main, appIdentifierWithStorage, res.userId, false);
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
        AppIdentifierWithStorage appId = new AppIdentifierWithStorage(null, null,
                StorageLayer.getStorage(main));
        return getUserById(appId, userId);
    }

    public static AuthRecipeUserInfo getUserById(AppIdentifierWithStorage appIdentifierWithStorage, String userId)
            throws StorageQueryException {
        return appIdentifierWithStorage.getAuthRecipeStorage().getPrimaryUserById(appIdentifierWithStorage, userId);
    }

    public static class CreatePrimaryUserResult {
        public AuthRecipeUserInfo user;
        public boolean wasAlreadyAPrimaryUser;

        public CreatePrimaryUserResult(AuthRecipeUserInfo user, boolean wasAlreadyAPrimaryUser) {
            this.user = user;
            this.wasAlreadyAPrimaryUser = wasAlreadyAPrimaryUser;
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

    @TestOnly
    public static CanLinkAccountsResult canLinkAccounts(Main main, String recipeUserId, String primaryUserId)
            throws StorageQueryException, UnknownUserIdException, InputUserIdIsNotAPrimaryUserException,
            RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException,
            AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException {
        AppIdentifierWithStorage appId = new AppIdentifierWithStorage(null, null,
                StorageLayer.getStorage(main));
        return canLinkAccounts(appId, recipeUserId, primaryUserId);
    }

    public static CanLinkAccountsResult canLinkAccounts(AppIdentifierWithStorage appIdentifierWithStorage,
                                                        String recipeUserId, String primaryUserId)
            throws StorageQueryException, UnknownUserIdException, InputUserIdIsNotAPrimaryUserException,
            RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException,
            AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException {
        AuthRecipeSQLStorage storage = (AuthRecipeSQLStorage) appIdentifierWithStorage.getAuthRecipeStorage();
        try {
            return storage.startTransaction(con -> {
                try {
                    CanLinkAccountsResult result = canLinkAccountsHelper(con, appIdentifierWithStorage,
                            recipeUserId, primaryUserId);

                    storage.commitTransaction(con);

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
                                                               AppIdentifierWithStorage appIdentifierWithStorage,
                                                               String _recipeUserId, String _primaryUserId)
            throws StorageQueryException, UnknownUserIdException, InputUserIdIsNotAPrimaryUserException,
            RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException,
            AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException {
        AuthRecipeSQLStorage storage = (AuthRecipeSQLStorage) appIdentifierWithStorage.getAuthRecipeStorage();
        AuthRecipeUserInfo primaryUser = storage.getPrimaryUserById_Transaction(appIdentifierWithStorage, con,
                _primaryUserId);

        if (primaryUser == null) {
            throw new UnknownUserIdException();
        }

        if (!primaryUser.isPrimaryUser) {
            throw new InputUserIdIsNotAPrimaryUserException(primaryUser.getSupertokensUserId());
        }

        AuthRecipeUserInfo recipeUser = storage.getPrimaryUserById_Transaction(appIdentifierWithStorage, con,
                _recipeUserId);
        if (recipeUser == null) {
            throw new UnknownUserIdException();
        }

        if (recipeUser.isPrimaryUser) {
            if (recipeUser.getSupertokensUserId().equals(primaryUser.getSupertokensUserId())) {
                return new CanLinkAccountsResult(recipeUser.getSupertokensUserId(), primaryUser.getSupertokensUserId(), true);
            } else {
                throw new RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException(recipeUser,
                        "The input recipe user ID is already linked to another user ID");
            }
        }

        // now we know that the recipe user ID is not a primary user, so we can focus on it's one
        // login method
        assert (recipeUser.loginMethods.length == 1);
        LoginMethod recipeUserIdLM = recipeUser.loginMethods[0];

        Set<String> tenantIds = new HashSet<>();
        tenantIds.addAll(recipeUser.tenantIds);
        tenantIds.addAll(primaryUser.tenantIds);

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
            TenantIdentifier tenantIdentifier = new TenantIdentifier(
                    appIdentifierWithStorage.getConnectionUriDomain(), appIdentifierWithStorage.getAppId(),
                    tenantId);
            // we do not bother with getting the tenantIdentifierWithStorage here because
            // we get the tenants from the user itself, and the user can only be shared across
            // tenants of the same storage - therefore, the storage will be the same.

            if (recipeUserIdLM.email != null) {
                AuthRecipeUserInfo[] usersWithSameEmail = storage
                        .listPrimaryUsersByEmail_Transaction(appIdentifierWithStorage, con,
                                recipeUserIdLM.email);
                for (AuthRecipeUserInfo user : usersWithSameEmail) {
                    if (!user.tenantIds.contains(tenantId)) {
                        continue;
                    }
                    if (user.isPrimaryUser && !user.getSupertokensUserId().equals(primaryUser.getSupertokensUserId())) {
                        throw new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException(user.getSupertokensUserId(),
                                "This user's email is already associated with another user ID");
                    }
                }
            }

            if (recipeUserIdLM.phoneNumber != null) {
                AuthRecipeUserInfo[] usersWithSamePhoneNumber = storage
                        .listPrimaryUsersByPhoneNumber_Transaction(appIdentifierWithStorage, con,
                                recipeUserIdLM.phoneNumber);
                for (AuthRecipeUserInfo user : usersWithSamePhoneNumber) {
                    if (!user.tenantIds.contains(tenantId)) {
                        continue;
                    }
                    if (user.isPrimaryUser && !user.getSupertokensUserId().equals(primaryUser.getSupertokensUserId())) {
                        throw new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException(user.getSupertokensUserId(),
                                "This user's phone number is already associated with another user" +
                                        " ID");
                    }
                }
            }

            if (recipeUserIdLM.thirdParty != null) {
                AuthRecipeUserInfo[] usersWithSameThirdParty = storage
                        .listPrimaryUsersByThirdPartyInfo_Transaction(appIdentifierWithStorage, con,
                                recipeUserIdLM.thirdParty.id, recipeUserIdLM.thirdParty.userId);
                for (AuthRecipeUserInfo userWithSameThirdParty : usersWithSameThirdParty) {
                    if (!userWithSameThirdParty.tenantIds.contains(tenantId)) {
                        continue;
                    }
                    if (userWithSameThirdParty.isPrimaryUser &&
                            !userWithSameThirdParty.getSupertokensUserId().equals(primaryUser.getSupertokensUserId())) {
                        throw new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException(
                                userWithSameThirdParty.getSupertokensUserId(),
                                "This user's third party login is already associated with another" +
                                        " user ID");
                    }
                }

            }
        }

        return new CanLinkAccountsResult(recipeUser.getSupertokensUserId(), primaryUser.getSupertokensUserId(), false);
    }

    @TestOnly
    public static LinkAccountsResult linkAccounts(Main main, String recipeUserId, String primaryUserId)
            throws StorageQueryException, AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            UnknownUserIdException,
            FeatureNotEnabledException, InputUserIdIsNotAPrimaryUserException,
            RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException {
        AppIdentifierWithStorage appId = new AppIdentifierWithStorage(null, null,
                StorageLayer.getStorage(main));
        try {
            return linkAccounts(main, appId, recipeUserId, primaryUserId);
        } catch (TenantOrAppNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static LinkAccountsResult linkAccounts(Main main, AppIdentifierWithStorage appIdentifierWithStorage,
                                       String _recipeUserId, String _primaryUserId)
            throws StorageQueryException,
            AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException, InputUserIdIsNotAPrimaryUserException,
            UnknownUserIdException, TenantOrAppNotFoundException, FeatureNotEnabledException {

        if (Arrays.stream(FeatureFlag.getInstance(main, appIdentifierWithStorage).getEnabledFeatures())
                .noneMatch(t -> t == EE_FEATURES.ACCOUNT_LINKING)) {
            throw new FeatureNotEnabledException(
                    "Account linking feature is not enabled for this app. Please contact support to enable it.");
        }

        AuthRecipeSQLStorage storage = (AuthRecipeSQLStorage) appIdentifierWithStorage.getAuthRecipeStorage();
        try {
            LinkAccountsResult result = storage.startTransaction(con -> {

                try {
                    CanLinkAccountsResult canLinkAccounts = canLinkAccountsHelper(con, appIdentifierWithStorage,
                            _recipeUserId, _primaryUserId);

                    if (canLinkAccounts.alreadyLinked) {
                        return new LinkAccountsResult(getUserById(appIdentifierWithStorage, canLinkAccounts.primaryUserId), true);
                    }
                    // now we can link accounts in the db.
                    storage.linkAccounts_Transaction(appIdentifierWithStorage, con, canLinkAccounts.recipeUserId,
                            canLinkAccounts.primaryUserId);

                    storage.commitTransaction(con);

                    return new LinkAccountsResult(getUserById(appIdentifierWithStorage, canLinkAccounts.primaryUserId), false);
                } catch (UnknownUserIdException | InputUserIdIsNotAPrimaryUserException |
                         RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException |
                         AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
                    throw new StorageTransactionLogicException(e);
                }
            });

            if (!result.wasAlreadyLinked) {
                io.supertokens.pluginInterface.useridmapping.UserIdMapping mappingResult =
                        io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                                appIdentifierWithStorage,
                                _recipeUserId, UserIdType.SUPERTOKENS);
                // finally, we revoke all sessions of the recipeUser Id cause their user ID has changed.
                Session.revokeAllSessionsForUser(main, appIdentifierWithStorage,
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

    public static class LinkAccountsResult {
        public final AuthRecipeUserInfo user;
        public final boolean wasAlreadyLinked;

        public LinkAccountsResult(AuthRecipeUserInfo user, boolean wasAlreadyLinked) {
            this.user = user;
            this.wasAlreadyLinked = wasAlreadyLinked;
        }
    }

    @TestOnly
    public static CreatePrimaryUserResult canCreatePrimaryUser(Main main,
                                                               String recipeUserId)
            throws StorageQueryException, AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            RecipeUserIdAlreadyLinkedWithPrimaryUserIdException, UnknownUserIdException {
        AppIdentifierWithStorage appId = new AppIdentifierWithStorage(null, null,
                StorageLayer.getStorage(main));
        return canCreatePrimaryUser(appId, recipeUserId);
    }

    public static CreatePrimaryUserResult canCreatePrimaryUser(AppIdentifierWithStorage appIdentifierWithStorage,
                                                               String recipeUserId)
            throws StorageQueryException, AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            RecipeUserIdAlreadyLinkedWithPrimaryUserIdException, UnknownUserIdException {

        AuthRecipeSQLStorage storage = (AuthRecipeSQLStorage) appIdentifierWithStorage.getAuthRecipeStorage();
        try {
            return storage.startTransaction(con -> {
                try {
                    return canCreatePrimaryUserHelper(con, appIdentifierWithStorage,
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
                                                                      AppIdentifierWithStorage appIdentifierWithStorage,
                                                                      String recipeUserId)
            throws StorageQueryException, UnknownUserIdException, RecipeUserIdAlreadyLinkedWithPrimaryUserIdException,
            AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException {
        AuthRecipeSQLStorage storage = (AuthRecipeSQLStorage) appIdentifierWithStorage.getAuthRecipeStorage();

        AuthRecipeUserInfo targetUser = storage.getPrimaryUserById_Transaction(appIdentifierWithStorage, con,
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
        LoginMethod loginMethod = targetUser.loginMethods[0];

        for (String tenantId : targetUser.tenantIds) {
            if (loginMethod.email != null) {
                AuthRecipeUserInfo[] usersWithSameEmail = storage
                        .listPrimaryUsersByEmail_Transaction(appIdentifierWithStorage, con,
                                loginMethod.email);
                for (AuthRecipeUserInfo user : usersWithSameEmail) {
                    if (!user.tenantIds.contains(tenantId)) {
                        continue;
                    }
                    if (user.isPrimaryUser) {
                        throw new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException(user.getSupertokensUserId(),
                                "This user's email is already associated with another user ID");
                    }
                }
            }

            if (loginMethod.phoneNumber != null) {
                AuthRecipeUserInfo[] usersWithSamePhoneNumber = storage
                        .listPrimaryUsersByPhoneNumber_Transaction(appIdentifierWithStorage, con,
                                loginMethod.phoneNumber);
                for (AuthRecipeUserInfo user : usersWithSamePhoneNumber) {
                    if (!user.tenantIds.contains(tenantId)) {
                        continue;
                    }
                    if (user.isPrimaryUser) {
                        throw new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException(user.getSupertokensUserId(),
                                "This user's phone number is already associated with another user" +
                                        " ID");
                    }
                }
            }

            if (loginMethod.thirdParty != null) {
                AuthRecipeUserInfo[] usersWithSameThirdParty = storage
                        .listPrimaryUsersByThirdPartyInfo_Transaction(appIdentifierWithStorage, con,
                                loginMethod.thirdParty.id, loginMethod.thirdParty.userId);
                for (AuthRecipeUserInfo userWithSameThirdParty : usersWithSameThirdParty) {
                    if (!userWithSameThirdParty.tenantIds.contains(tenantId)) {
                        continue;
                    }
                    if (userWithSameThirdParty.isPrimaryUser) {
                        throw new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException(
                                userWithSameThirdParty.getSupertokensUserId(),
                                "This user's third party login is already associated with another" +
                                        " user ID");
                    }
                }
            }
        }

        return new CreatePrimaryUserResult(targetUser, false);
    }

    @TestOnly
    public static CreatePrimaryUserResult createPrimaryUser(Main main,
                                                            String recipeUserId)
            throws StorageQueryException, AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            RecipeUserIdAlreadyLinkedWithPrimaryUserIdException, UnknownUserIdException,
            FeatureNotEnabledException {
        AppIdentifierWithStorage appId = new AppIdentifierWithStorage(null, null,
                StorageLayer.getStorage(main));
        try {
            return createPrimaryUser(main, appId, recipeUserId);
        } catch (TenantOrAppNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static CreatePrimaryUserResult createPrimaryUser(Main main,
                                                            AppIdentifierWithStorage appIdentifierWithStorage,
                                                            String recipeUserId)
            throws StorageQueryException, AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            RecipeUserIdAlreadyLinkedWithPrimaryUserIdException, UnknownUserIdException, TenantOrAppNotFoundException,
            FeatureNotEnabledException {

        if (Arrays.stream(FeatureFlag.getInstance(main, appIdentifierWithStorage).getEnabledFeatures())
                .noneMatch(t -> t == EE_FEATURES.ACCOUNT_LINKING)) {
            throw new FeatureNotEnabledException(
                    "Account linking feature is not enabled for this app. Please contact support to enable it.");
        }

        AuthRecipeSQLStorage storage = (AuthRecipeSQLStorage) appIdentifierWithStorage.getAuthRecipeStorage();
        try {
            return storage.startTransaction(con -> {

                try {
                    CreatePrimaryUserResult result = canCreatePrimaryUserHelper(con, appIdentifierWithStorage,
                            recipeUserId);
                    if (result.wasAlreadyAPrimaryUser) {
                        return result;
                    }
                    storage.makePrimaryUser_Transaction(appIdentifierWithStorage, con, result.user.getSupertokensUserId());

                    storage.commitTransaction(con);

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

    public static AuthRecipeUserInfo[] getUsersByAccountInfo(TenantIdentifierWithStorage tenantIdentifier,
                                                             boolean doUnionOfAccountInfo, String email,
                                                             String phoneNumber, String thirdPartyId,
                                                             String thirdPartyUserId)
            throws StorageQueryException {
        Set<AuthRecipeUserInfo> result = new HashSet<>();

        if (email != null) {
            AuthRecipeUserInfo[] users = tenantIdentifier.getAuthRecipeStorage()
                    .listPrimaryUsersByEmail(tenantIdentifier, email);
            result.addAll(List.of(users));
        }
        if (phoneNumber != null) {
            AuthRecipeUserInfo[] users = tenantIdentifier.getAuthRecipeStorage()
                    .listPrimaryUsersByPhoneNumber(tenantIdentifier, phoneNumber);
            result.addAll(List.of(users));
        }
        if (thirdPartyId != null && thirdPartyUserId != null) {
            AuthRecipeUserInfo user = tenantIdentifier.getAuthRecipeStorage()
                    .getPrimaryUserByThirdPartyInfo(tenantIdentifier, thirdPartyId, thirdPartyUserId);
            if (user != null) {
                result.add(user);
            }
        }

        if (doUnionOfAccountInfo) {
            AuthRecipeUserInfo[] finalResult = result.toArray(new AuthRecipeUserInfo[0]);
            return Arrays.stream(finalResult).sorted((o1, o2) -> {
                if (o1.timeJoined < o2.timeJoined) {
                    return -1;
                } else if (o1.timeJoined > o2.timeJoined) {
                    return 1;
                }
                return 0;
            }).toArray(AuthRecipeUserInfo[]::new);
        } else {
            List<AuthRecipeUserInfo> finalList = new ArrayList<>();
            for (AuthRecipeUserInfo user : result) {
                boolean emailMatch = email == null;
                boolean phoneNumberMatch = phoneNumber == null;
                boolean thirdPartyMatch = thirdPartyId == null;
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
                }
                if (emailMatch && phoneNumberMatch && thirdPartyMatch) {
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

    }

    public static long getUsersCountForTenant(TenantIdentifierWithStorage tenantIdentifier,
                                              RECIPE_ID[] includeRecipeIds)
            throws StorageQueryException,
            TenantOrAppNotFoundException, BadPermissionException {
        return tenantIdentifier.getAuthRecipeStorage().getUsersCount(
                tenantIdentifier, includeRecipeIds);
    }

    public static long getUsersCountAcrossAllTenants(AppIdentifierWithStorage appIdentifierWithStorage,
                                                     RECIPE_ID[] includeRecipeIds)
            throws StorageQueryException,
            TenantOrAppNotFoundException, BadPermissionException {
        long count = 0;

        for (Storage storage : appIdentifierWithStorage.getStorages()) {
            if (storage.getType() != STORAGE_TYPE.SQL) {
                // we only support SQL for now
                throw new UnsupportedOperationException("");
            }

            count += ((AuthRecipeStorage) storage).getUsersCount(
                    appIdentifierWithStorage, includeRecipeIds);
        }

        return count;
    }

    @TestOnly
    public static long getUsersCount(Main main,
                                     RECIPE_ID[] includeRecipeIds) throws StorageQueryException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return getUsersCountForTenant(new TenantIdentifierWithStorage(
                            null, null, null, storage),
                    includeRecipeIds);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static UserPaginationContainer getUsers(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                   Integer limit, String timeJoinedOrder,
                                                   @Nullable String paginationToken,
                                                   @Nullable RECIPE_ID[] includeRecipeIds,
                                                   @Nullable DashboardSearchTags dashboardSearchTags)
            throws StorageQueryException, UserPaginationToken.InvalidTokenException, TenantOrAppNotFoundException {
        AuthRecipeUserInfo[] users;
        if (paginationToken == null) {
            users = tenantIdentifierWithStorage.getAuthRecipeStorage()
                    .getUsers(tenantIdentifierWithStorage, limit + 1, timeJoinedOrder, includeRecipeIds, null,
                            null, dashboardSearchTags);
        } else {
            UserPaginationToken tokenInfo = UserPaginationToken.extractTokenInfo(paginationToken);
            users = tenantIdentifierWithStorage.getAuthRecipeStorage()
                    .getUsers(tenantIdentifierWithStorage, limit + 1, timeJoinedOrder, includeRecipeIds,
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
            return getUsers(new TenantIdentifierWithStorage(
                            null, null, null, storage),
                    limit, timeJoinedOrder, paginationToken, includeRecipeIds, dashboardSearchTags);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static void deleteUser(AppIdentifierWithStorage appIdentifierWithStorage, String userId,
                                  UserIdMapping userIdMapping)
            throws StorageQueryException, StorageTransactionLogicException {
        deleteUser(appIdentifierWithStorage, userId, true, userIdMapping);
    }

    public static void deleteUser(AppIdentifierWithStorage appIdentifierWithStorage, String userId,
                                  boolean removeAllLinkedAccounts,
                                  UserIdMapping userIdMapping)
            throws StorageQueryException, StorageTransactionLogicException {
        AuthRecipeSQLStorage storage = (AuthRecipeSQLStorage) appIdentifierWithStorage.getAuthRecipeStorage();

        storage.startTransaction(con -> {
            deleteUserHelper(con, appIdentifierWithStorage, userId, removeAllLinkedAccounts, userIdMapping);
            storage.commitTransaction(con);
            return null;
        });
    }

    private static void deleteUserHelper(TransactionConnection con, AppIdentifierWithStorage appIdentifierWithStorage,
                                         String userId,
                                         boolean removeAllLinkedAccounts,
                                         UserIdMapping userIdMapping)
            throws StorageQueryException {
        AuthRecipeSQLStorage storage = (AuthRecipeSQLStorage) appIdentifierWithStorage.getAuthRecipeStorage();

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
            if (((AuthRecipeSQLStorage) appIdentifierWithStorage.getAuthRecipeStorage())
                    .doesUserIdExist_Transaction(con, appIdentifierWithStorage, userIdMapping.externalUserId)) {
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

        AuthRecipeUserInfo userToDelete = storage.getPrimaryUserById_Transaction(appIdentifierWithStorage, con,
                userIdToDeleteForAuthRecipe);

        if (userToDelete == null) {
            return;
        }

        if (removeAllLinkedAccounts || userToDelete.loginMethods.length == 1) {
            if (userToDelete.getSupertokensUserId().equals(userIdToDeleteForAuthRecipe)) {
                primaryUserIdToDeleteNonAuthRecipe = userIdToDeleteForNonAuthRecipeForRecipeUserId;
                if (primaryUserIdToDeleteNonAuthRecipe == null) {
                    deleteAuthRecipeUser(con, appIdentifierWithStorage, userToDelete.getSupertokensUserId(),
                            true);
                    return;
                }
            } else {
                // this is always type supertokens user ID cause it's from a user from the database.
                io.supertokens.pluginInterface.useridmapping.UserIdMapping mappingResult =
                        io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                                con,
                                appIdentifierWithStorage,
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
            deleteAuthRecipeUser(con, appIdentifierWithStorage, userIdToDeleteForAuthRecipe,
                    !userIdToDeleteForAuthRecipe.equals(userToDelete.getSupertokensUserId()));

            if (userIdToDeleteForNonAuthRecipeForRecipeUserId != null) {
                deleteNonAuthRecipeUser(con, appIdentifierWithStorage, userIdToDeleteForNonAuthRecipeForRecipeUserId);
            }

            if (primaryUserIdToDeleteNonAuthRecipe != null) {
                deleteNonAuthRecipeUser(con, appIdentifierWithStorage, primaryUserIdToDeleteNonAuthRecipe);

                // this is only done to also delete the user ID mapping in case it exists, since we do not delete in the
                // previous call to deleteAuthRecipeUser above.
                deleteAuthRecipeUser(con, appIdentifierWithStorage, userToDelete.getSupertokensUserId(),
                        true);
            }
        } else {
            for (LoginMethod lM : userToDelete.loginMethods) {
                io.supertokens.pluginInterface.useridmapping.UserIdMapping mappingResult = lM.getSupertokensUserId().equals(
                        userIdToDeleteForAuthRecipe) ? userIdMapping :
                        io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                                con,
                                appIdentifierWithStorage,
                                lM.getSupertokensUserId(), UserIdType.SUPERTOKENS);
                deleteUserHelper(con, appIdentifierWithStorage, lM.getSupertokensUserId(), false, mappingResult);
            }
        }
    }

    @TestOnly
    public static void deleteUser(Main main, String userId, boolean removeAllLinkedAccounts)
            throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = StorageLayer.getStorage(main);
        AppIdentifierWithStorage appIdentifier = new AppIdentifierWithStorage(
                null, null, storage);
        UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(appIdentifier,
                userId, UserIdType.ANY);

        deleteUser(appIdentifier, userId, removeAllLinkedAccounts, mapping);
    }

    @TestOnly
    public static void deleteUser(Main main, String userId)
            throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = StorageLayer.getStorage(main);
        AppIdentifierWithStorage appIdentifier = new AppIdentifierWithStorage(
                null, null, storage);
        UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(appIdentifier,
                userId, UserIdType.ANY);

        deleteUser(appIdentifier, userId, mapping);
    }

    @TestOnly
    public static void deleteUser(AppIdentifierWithStorage appIdentifierWithStorage, String userId)
            throws StorageQueryException, StorageTransactionLogicException {
        Storage storage = appIdentifierWithStorage.getStorage();
        UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(appIdentifierWithStorage,
                userId, UserIdType.ANY);

        deleteUser(appIdentifierWithStorage, userId, mapping);
    }

    private static void deleteNonAuthRecipeUser(TransactionConnection con, AppIdentifierWithStorage
            appIdentifierWithStorage, String userId)
            throws StorageQueryException {
        appIdentifierWithStorage.getUserMetadataStorage()
                .deleteUserMetadata_Transaction(con, appIdentifierWithStorage, userId);
        ((SessionSQLStorage) appIdentifierWithStorage.getSessionStorage())
                .deleteSessionsOfUser_Transaction(con, appIdentifierWithStorage, userId);
        appIdentifierWithStorage.getEmailVerificationStorage()
                .deleteEmailVerificationUserInfo_Transaction(con, appIdentifierWithStorage, userId);
        appIdentifierWithStorage.getUserRolesStorage()
                .deleteAllRolesForUser_Transaction(con, appIdentifierWithStorage, userId);
        appIdentifierWithStorage.getActiveUsersStorage()
                .deleteUserActive_Transaction(con, appIdentifierWithStorage, userId);
        appIdentifierWithStorage.getTOTPStorage().removeUser_Transaction(con, appIdentifierWithStorage, userId);
    }

    private static void deleteAuthRecipeUser(TransactionConnection con,
                                             AppIdentifierWithStorage appIdentifierWithStorage, String
                                                     userId, boolean deleteFromUserIdToAppIdTableToo)
            throws StorageQueryException {
        // auth recipe deletions here only
        appIdentifierWithStorage.getEmailPasswordStorage()
                .deleteEmailPasswordUser_Transaction(con, appIdentifierWithStorage, userId, deleteFromUserIdToAppIdTableToo);
        appIdentifierWithStorage.getThirdPartyStorage()
                .deleteThirdPartyUser_Transaction(con, appIdentifierWithStorage, userId, deleteFromUserIdToAppIdTableToo);
        appIdentifierWithStorage.getPasswordlessStorage()
                .deletePasswordlessUser_Transaction(con, appIdentifierWithStorage, userId, deleteFromUserIdToAppIdTableToo);
    }

    public static boolean deleteNonAuthRecipeUser(TenantIdentifierWithStorage
                                                          tenantIdentifierWithStorage, String userId)
            throws StorageQueryException {

        // UserMetadata is per app, so nothing to delete

        boolean finalDidExist = false;
        boolean didExist = false;

        didExist = tenantIdentifierWithStorage.getSessionStorage()
                .deleteSessionsOfUser(tenantIdentifierWithStorage, userId);
        finalDidExist = finalDidExist || didExist;

        didExist = tenantIdentifierWithStorage.getEmailVerificationStorage()
                .deleteEmailVerificationUserInfo(tenantIdentifierWithStorage, userId);
        finalDidExist = finalDidExist || didExist;

        didExist = (tenantIdentifierWithStorage.getUserRolesStorage()
                .deleteAllRolesForUser(tenantIdentifierWithStorage, userId) > 0);
        finalDidExist = finalDidExist || didExist;

        didExist = tenantIdentifierWithStorage.getTOTPStorage()
                .removeUser(tenantIdentifierWithStorage, userId);
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
