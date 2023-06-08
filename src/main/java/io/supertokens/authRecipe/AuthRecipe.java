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
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.dashboard.DashboardSearchTags;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.totp.sqlStorage.TOTPSQLStorage;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdType;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nullable;

/*This files contains functions that are common for all auth recipes*/

public class AuthRecipe {

    public static final int USER_PAGINATION_LIMIT = 500;

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
            nextPaginationToken = new UserPaginationToken(users[limit].id,
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

    public static void deleteUser(AppIdentifierWithStorage appIdentifierWithStorage, String userId,
                                  UserIdMapping userIdMapping)
            throws StorageQueryException, StorageTransactionLogicException {
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
            if (appIdentifierWithStorage.getAuthRecipeStorage()
                    .doesUserIdExist(appIdentifierWithStorage, userIdMapping.externalUserId)) {
                // db is in state A4
                // delete only from auth tables
                deleteAuthRecipeUser(appIdentifierWithStorage, userId);
            } else {
                // db is in state A3
                // delete user from non-auth tables with externalUserId
                deleteNonAuthRecipeUser(appIdentifierWithStorage, userIdMapping.externalUserId);
                // delete user from auth tables with superTokensUserId
                deleteAuthRecipeUser(appIdentifierWithStorage, userIdMapping.superTokensUserId);
            }
        } else {
            deleteNonAuthRecipeUser(appIdentifierWithStorage, userId);
            deleteAuthRecipeUser(appIdentifierWithStorage, userId);
        }
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

    private static void deleteNonAuthRecipeUser(AppIdentifierWithStorage
                                                        appIdentifierWithStorage, String userId)
            throws StorageQueryException, StorageTransactionLogicException {
        appIdentifierWithStorage.getUserMetadataStorage()
                .deleteUserMetadata(appIdentifierWithStorage, userId);
        appIdentifierWithStorage.getSessionStorage()
                .deleteSessionsOfUser(appIdentifierWithStorage, userId);
        appIdentifierWithStorage.getEmailVerificationStorage()
                .deleteEmailVerificationUserInfo(appIdentifierWithStorage, userId);
        appIdentifierWithStorage.getUserRolesStorage()
                .deleteAllRolesForUser(appIdentifierWithStorage, userId);
        appIdentifierWithStorage.getActiveUsersStorage()
                .deleteUserActive(appIdentifierWithStorage, userId);

        TOTPSQLStorage storage = appIdentifierWithStorage.getTOTPStorage();
        storage.startTransaction(con -> {
            storage.removeUser_Transaction(con, appIdentifierWithStorage, userId);
            storage.commitTransaction(con);
            return null;
        });
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

    private static void deleteAuthRecipeUser(AppIdentifierWithStorage appIdentifierWithStorage, String
            userId)
            throws StorageQueryException {
        // auth recipe deletions here only
        appIdentifierWithStorage.getEmailPasswordStorage().deleteEmailPasswordUser(appIdentifierWithStorage, userId);
        appIdentifierWithStorage.getThirdPartyStorage().deleteThirdPartyUser(appIdentifierWithStorage, userId);
        appIdentifierWithStorage.getPasswordlessStorage().deletePasswordlessUser(appIdentifierWithStorage, userId);
    }
}
