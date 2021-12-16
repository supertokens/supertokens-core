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
import io.supertokens.storageLayer.StorageLayer;

import javax.annotation.Nullable;

/*This files contains functions that are common for all auth recipes*/

public class AuthRecipe {

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

    public static void deleteUser(Main main, String userId) throws StorageQueryException {
        // We delete the users last because we want to avoid an error if the user has been deleted.
        // For things created after the intial cleanup and before finishing the operation:
        // - session: the session will expire anyway
        // - email verification: email verification tokens can be created for any userId anyway

        StorageLayer.getSessionStorage(main).deleteSessionsOfUser(userId);
        StorageLayer.getEmailVerificationStorage(main).deleteEmailVerificationUserInfo(userId);
        StorageLayer.getEmailPasswordStorage(main).deleteEmailPasswordUser(userId);
        StorageLayer.getThirdPartyStorage(main).deleteThirdPartyUser(userId);
    }
}
