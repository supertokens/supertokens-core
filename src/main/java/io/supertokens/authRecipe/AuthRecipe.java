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
import io.supertokens.exceptions.TenantNotFoundException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdType;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nullable;

/*This files contains functions that are common for all auth recipes*/

public class AuthRecipe {

    public static final int USER_PAGINATION_LIMIT = 500;

    public static long getUsersCount(String connectionUriDomain, String tenantId, Main main,
                                     RECIPE_ID[] includeRecipeIds) throws StorageQueryException,
            TenantNotFoundException {
        return StorageLayer.getAuthRecipeStorage(connectionUriDomain, tenantId, main).getUsersCount(includeRecipeIds);
    }

    @TestOnly
    public static long getUsersCount(Main main,
                                     RECIPE_ID[] includeRecipeIds) throws StorageQueryException {
        try {
            return getUsersCount(null, null, main, includeRecipeIds);
        } catch (TenantNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static UserPaginationContainer getUsers(String connectionUriDomain, String tenantId, Main main,
                                                   Integer limit, String timeJoinedOrder,
                                                   @Nullable String paginationToken,
                                                   @Nullable RECIPE_ID[] includeRecipeIds)
            throws StorageQueryException, UserPaginationToken.InvalidTokenException, TenantNotFoundException {
        AuthRecipeUserInfo[] users;
        if (paginationToken == null) {
            users = StorageLayer.getAuthRecipeStorage(connectionUriDomain, tenantId, main)
                    .getUsers(limit + 1, timeJoinedOrder, includeRecipeIds, null,
                            null);
        } else {
            UserPaginationToken tokenInfo = UserPaginationToken.extractTokenInfo(paginationToken);
            users = StorageLayer.getAuthRecipeStorage(connectionUriDomain, tenantId, main)
                    .getUsers(limit + 1, timeJoinedOrder, includeRecipeIds,
                            tokenInfo.userId, tokenInfo.timeJoined);
        }
        String nextPaginationToken = null;
        int maxLoop = users.length;
        if (users.length == limit + 1) {
            maxLoop = limit;
            nextPaginationToken = new UserPaginationToken(users[limit].id, users[limit].timeJoined).generateToken();
        }
        AuthRecipeUserInfo[] resultUsers = new AuthRecipeUserInfo[maxLoop];
        System.arraycopy(users, 0, resultUsers, 0, maxLoop);
        return new UserPaginationContainer(resultUsers, nextPaginationToken);
    }

    @TestOnly
    public static UserPaginationContainer getUsers(Main main,
                                                   Integer limit, String timeJoinedOrder,
                                                   @Nullable String paginationToken,
                                                   @Nullable RECIPE_ID[] includeRecipeIds)
            throws StorageQueryException, UserPaginationToken.InvalidTokenException {
        try {
            return getUsers(null, null, main, limit, timeJoinedOrder, paginationToken, includeRecipeIds);
        } catch (TenantNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    public static void deleteUser(String connectionUriDomain, String tenantId, Main main, String userId)
            throws StorageQueryException, TenantNotFoundException {
        // We clean up the user last so that if anything before that throws an error, then that will throw a 500 to the
        // developer. In this case, they expect that the user has not been deleted (which will be true). This is as
        // opposed to deleting the user first, in which case if something later throws an error, then the user has
        // actually been deleted already (which is not expected by the dev)

        // For things created after the intial cleanup and before finishing the operation:
        // - session: the session will expire anyway
        // - email verification: email verification tokens can be created for any userId anyway

        // If userId mapping exists then delete entries with superTokensUserId from auth related tables and
        // externalUserid from non-auth tables
        UserIdMapping userIdMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(connectionUriDomain,
                tenantId, main, userId, UserIdType.ANY);
        if (userIdMapping != null) {
            // We check if the mapped externalId is another SuperTokens UserId, this could come up when migrating
            // recipes.
            // in reference to
            // https://docs.google.com/spreadsheets/d/17hYV32B0aDCeLnSxbZhfRN2Y9b0LC2xUF44vV88RNAA/edit?usp=sharing
            // we want to check which state the db is in
            if (StorageLayer.getAuthRecipeStorage(connectionUriDomain, tenantId, main)
                    .doesUserIdExist(userIdMapping.externalUserId)) {
                // db is in state A4
                // delete only from auth tables
                deleteAuthRecipeUser(connectionUriDomain, tenantId, main, userId);
            } else {
                // db is in state A3
                // delete user from non-auth tables with externalUserId
                deleteNonAuthRecipeUser(connectionUriDomain, tenantId, main, userIdMapping.externalUserId);
                // delete user from auth tables with superTokensUserId
                deleteAuthRecipeUser(connectionUriDomain, tenantId, main, userIdMapping.superTokensUserId);
            }
        } else {
            deleteNonAuthRecipeUser(connectionUriDomain, tenantId, main, userId);
            deleteAuthRecipeUser(connectionUriDomain, tenantId, main, userId);
        }
    }

    @TestOnly
    public static void deleteUser(Main main, String userId)
            throws StorageQueryException {
        try {
            deleteUser(null, null, main, userId);
        } catch (TenantNotFoundException e) {
            throw new IllegalStateException("Should never come here");
        }
    }

    private static void deleteNonAuthRecipeUser(String connectionUriDomain, String tenantId, Main main, String userId)
            throws StorageQueryException, TenantNotFoundException {
        // non auth recipe deletion
        StorageLayer.getUserMetadataStorage(connectionUriDomain, tenantId, main).deleteUserMetadata(userId);
        StorageLayer.getSessionStorage(connectionUriDomain, tenantId, main).deleteSessionsOfUser(userId);
        StorageLayer.getEmailVerificationStorage(connectionUriDomain, tenantId, main)
                .deleteEmailVerificationUserInfo(userId);
        StorageLayer.getUserRolesStorage(connectionUriDomain, tenantId, main).deleteAllRolesForUser(userId);
    }

    private static void deleteAuthRecipeUser(String connectionUriDomain, String tenantId, Main main, String userId)
            throws StorageQueryException, TenantNotFoundException {
        // auth recipe deletions here only
        StorageLayer.getEmailPasswordStorage(connectionUriDomain, tenantId, main).deleteEmailPasswordUser(userId);
        StorageLayer.getThirdPartyStorage(connectionUriDomain, tenantId, main).deleteThirdPartyUser(userId);
        StorageLayer.getPasswordlessStorage(connectionUriDomain, tenantId, main).deletePasswordlessUser(userId);
    }
}
