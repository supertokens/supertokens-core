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
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.totp.sqlStorage.TOTPSQLStorage;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdType;

import javax.annotation.Nullable;

/*This files contains functions that are common for all auth recipes*/

public class AuthRecipe {

    public static final int USER_PAGINATION_LIMIT = 500;

    public static long getUsersCount(Main main, RECIPE_ID[] includeRecipeIds) throws StorageQueryException {
        return StorageLayer.getAuthRecipeStorage(main).getUsersCount(includeRecipeIds);
    }

    public static UserPaginationContainer getUsers(Main main, Integer limit, String timeJoinedOrder,
            @Nullable String paginationToken, @Nullable RECIPE_ID[] includeRecipeIds)
            throws StorageQueryException, UserPaginationToken.InvalidTokenException {
        AuthRecipeUserInfo[] users;
        if (paginationToken == null) {
            users = StorageLayer.getAuthRecipeStorage(main).getUsers(limit + 1, timeJoinedOrder, includeRecipeIds, null,
                    null);
        } else {
            UserPaginationToken tokenInfo = UserPaginationToken.extractTokenInfo(paginationToken);
            users = StorageLayer.getAuthRecipeStorage(main).getUsers(limit + 1, timeJoinedOrder, includeRecipeIds,
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

    public static void deleteUser(Main main, String userId)
            throws StorageQueryException, StorageTransactionLogicException {
        // We clean up the user last so that if anything before that throws an error,
        // then that will throw a 500 to the
        // developer. In this case, they expect that the user has not been deleted
        // (which will be true). This is as
        // opposed to deleting the user first, in which case if something later throws
        // an error, then the user has
        // actually been deleted already (which is not expected by the dev)

        // For things created after the intial cleanup and before finishing the
        // operation:
        // - session: the session will expire anyway
        // - email verification: email verification tokens can be created for any userId
        // anyway

        // If userId mapping exists then delete entries with superTokensUserId from auth
        // related tables and
        // externalUserid from non-auth tables
        UserIdMapping userIdMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(main, userId,
                UserIdType.ANY);
        if (userIdMapping != null) {
            // We check if the mapped externalId is another SuperTokens UserId, this could
            // come up when migrating
            // recipes.
            // in reference to
            // https://docs.google.com/spreadsheets/d/17hYV32B0aDCeLnSxbZhfRN2Y9b0LC2xUF44vV88RNAA/edit?usp=sharing
            // we want to check which state the db is in
            if (StorageLayer.getAuthRecipeStorage(main).doesUserIdExist(userIdMapping.externalUserId)) {
                // db is in state A4
                // delete only from auth tables
                deleteAuthRecipeUser(main, userId);
            } else {
                // db is in state A3
                // delete user from non-auth tables with externalUserId
                deleteNonAuthRecipeUser(main, userIdMapping.externalUserId);
                // delete user from auth tables with superTokensUserId
                deleteAuthRecipeUser(main, userIdMapping.superTokensUserId);
            }
        } else {
            deleteNonAuthRecipeUser(main, userId);
            deleteAuthRecipeUser(main, userId);
        }

    }

    private static void deleteNonAuthRecipeUser(Main main, String userId)
            throws StorageQueryException, StorageTransactionLogicException {
        // non auth recipe deletion
        StorageLayer.getUserMetadataStorage(main).deleteUserMetadata(userId);
        StorageLayer.getSessionStorage(main).deleteSessionsOfUser(userId);
        StorageLayer.getEmailVerificationStorage(main).deleteEmailVerificationUserInfo(userId);
        StorageLayer.getUserRolesStorage(main).deleteAllRolesForUser(userId);

        TOTPSQLStorage storage = StorageLayer.getTOTPStorage(main);
        storage.startTransaction(con -> {
            storage.removeUser_Transaction(con, userId);
            storage.commitTransaction(con);
            return null;
        });
    }

    private static void deleteAuthRecipeUser(Main main, String userId) throws StorageQueryException {
        // auth recipe deletions here only
        StorageLayer.getEmailPasswordStorage(main).deleteEmailPasswordUser(userId);
        StorageLayer.getThirdPartyStorage(main).deleteThirdPartyUser(userId);
        StorageLayer.getPasswordlessStorage(main).deletePasswordlessUser(userId);
    }
}
