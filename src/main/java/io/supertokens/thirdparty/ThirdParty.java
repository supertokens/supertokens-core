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

package io.supertokens.thirdparty;

import io.supertokens.Main;
import io.supertokens.authRecipe.UserPaginationToken;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.thirdparty.UserInfo;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateThirdPartyUserException;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateUserIdException;
import io.supertokens.pluginInterface.thirdparty.sqlStorage.ThirdPartySQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ThirdParty {

    public static class SignInUpResponse {
        public boolean createdNewUser;
        public UserInfo user;

        public SignInUpResponse(boolean createdNewUser, UserInfo user) {
            this.createdNewUser = createdNewUser;
            this.user = user;
        }
    }

    // we have two signInUp APIs since in version 2.7, we used to also verify the email
    // as seen below. But then, in newer versions, we stopped doing that cause of
    // https://github.com/supertokens/supertokens-core/issues/295, so we changed the API spec.
    @Deprecated
    public static SignInUpResponse signInUp2_7(String connectionUriDomain, String tenantId, Main main,
                                               String thirdPartyId, String thirdPartyUserId, String email,
                                               boolean isEmailVerified) throws StorageQueryException {
        SignInUpResponse response = signInUpHelper(connectionUriDomain, tenantId, main, thirdPartyId, thirdPartyUserId,
                email);

        if (isEmailVerified) {
            try {
                StorageLayer.getEmailVerificationStorage(connectionUriDomain, tenantId, main).startTransaction(con -> {
                    StorageLayer.getEmailVerificationStorage(connectionUriDomain, tenantId, main)
                            .updateIsEmailVerified_Transaction(con,
                                    response.user.id, response.user.email, true);
                    StorageLayer.getEmailVerificationStorage(connectionUriDomain, tenantId, main)
                            .commitTransaction(con);
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                throw new StorageQueryException(e);
            }
        }

        return response;
    }

    @Deprecated
    @TestOnly
    public static SignInUpResponse signInUp2_7(Main main,
                                               String thirdPartyId, String thirdPartyUserId, String email,
                                               boolean isEmailVerified) throws StorageQueryException {
        return signInUp2_7(null, null, main, thirdPartyId, thirdPartyUserId, email, isEmailVerified);
    }

    @TestOnly
    public static SignInUpResponse signInUp(Main main, String thirdPartyId, String thirdPartyUserId, String email)
            throws StorageQueryException {
        return signInUp(null, null, main, thirdPartyId, thirdPartyUserId, email);
    }

    public static SignInUpResponse signInUp(String connectionUriDomain, String tenantId, Main main, String thirdPartyId,
                                            String thirdPartyUserId, String email)
            throws StorageQueryException {
        return signInUpHelper(connectionUriDomain, tenantId, main, thirdPartyId, thirdPartyUserId, email);
    }

    private static SignInUpResponse signInUpHelper(String connectionUriDomain, String tenantId, Main main,
                                                   String thirdPartyId, String thirdPartyUserId,
                                                   String email) throws StorageQueryException {
        ThirdPartySQLStorage storage = StorageLayer.getThirdPartyStorage(connectionUriDomain, tenantId, main);
        while (true) {
            // loop for sign in + sign up

            while (true) {
                // loop for sign up
                String userId = Utils.getUUID();
                long timeJoined = System.currentTimeMillis();

                try {
                    UserInfo user = new UserInfo(userId, email, new UserInfo.ThirdParty(thirdPartyId, thirdPartyUserId),
                            timeJoined);

                    storage.signUp(user);

                    return new SignInUpResponse(true, user);
                } catch (DuplicateUserIdException e) {
                    // we try again..
                } catch (DuplicateThirdPartyUserException e) {
                    // we try to sign in
                    break;
                }
            }

            // we try to get user and update their email
            SignInUpResponse response = null;
            try {
                response = storage.startTransaction(con -> {
                    UserInfo user = storage.getUserInfoUsingId_Transaction(con, thirdPartyId, thirdPartyUserId);

                    if (user == null) {
                        // we retry everything..
                        storage.commitTransaction(con);
                        return null;
                    }

                    if (!email.equals(user.email)) {
                        storage.updateUserEmail_Transaction(con, thirdPartyId, thirdPartyUserId, email);

                        user = new UserInfo(user.id, email,
                                new UserInfo.ThirdParty(user.thirdParty.id, user.thirdParty.userId), user.timeJoined);
                    }

                    storage.commitTransaction(con);
                    return new SignInUpResponse(false, user);
                });
            } catch (StorageTransactionLogicException ignored) {
            }

            if (response != null) {
                return response;
            }

            // retry..
        }
    }

    public static UserInfo getUser(String connectionUriDomain, String tenantId, Main main, String userId)
            throws StorageQueryException {
        return StorageLayer.getThirdPartyStorage(connectionUriDomain, tenantId, main)
                .getThirdPartyUserInfoUsingId(userId);
    }

    @TestOnly
    public static UserInfo getUser(Main main, String userId)
            throws StorageQueryException {
        return getUser(null, null, main, userId);
    }


    public static UserInfo getUser(String connectionUriDomain, String tenantId, Main main, String thirdPartyId,
                                   String thirdPartyUserId)
            throws StorageQueryException {
        return StorageLayer.getThirdPartyStorage(connectionUriDomain, tenantId, main)
                .getThirdPartyUserInfoUsingId(thirdPartyId, thirdPartyUserId);
    }

    @TestOnly
    public static UserInfo getUser(Main main, String thirdPartyId,
                                   String thirdPartyUserId)
            throws StorageQueryException {
        return getUser(null, null, main, thirdPartyId, thirdPartyUserId);
    }

    @Deprecated
    public static UserPaginationContainer getUsers(String connectionUriDomain, String tenantId, Main main,
                                                   @Nullable String paginationToken, Integer limit,
                                                   String timeJoinedOrder)
            throws StorageQueryException, UserPaginationToken.InvalidTokenException {
        UserInfo[] users;
        if (paginationToken == null) {
            users = StorageLayer.getThirdPartyStorage(connectionUriDomain, tenantId, main)
                    .getThirdPartyUsers(limit + 1, timeJoinedOrder);
        } else {
            UserPaginationToken tokenInfo = UserPaginationToken.extractTokenInfo(paginationToken);
            users = StorageLayer.getThirdPartyStorage(connectionUriDomain, tenantId, main)
                    .getThirdPartyUsers(tokenInfo.userId, tokenInfo.timeJoined,
                            limit + 1, timeJoinedOrder);
        }
        String nextPaginationToken = null;
        int maxLoop = users.length;
        if (users.length == limit + 1) {
            maxLoop = limit;
            nextPaginationToken = new UserPaginationToken(users[limit].id, users[limit].timeJoined).generateToken();
        }
        UserInfo[] resultUsers = new UserInfo[maxLoop];
        System.arraycopy(users, 0, resultUsers, 0, maxLoop);
        return new UserPaginationContainer(resultUsers, nextPaginationToken);
    }

    @TestOnly
    @Deprecated
    public static UserPaginationContainer getUsers(Main main,
                                                   @Nullable String paginationToken, Integer limit,
                                                   String timeJoinedOrder)
            throws StorageQueryException, UserPaginationToken.InvalidTokenException {
        return getUsers(null, null, main, paginationToken, limit, timeJoinedOrder);
    }

    public static UserInfo[] getUsersByEmail(String connectionUriDomain, String tenantId, Main main,
                                             @Nonnull String email) throws StorageQueryException {
        return StorageLayer.getThirdPartyStorage(connectionUriDomain, tenantId, main).getThirdPartyUsersByEmail(email);
    }

    @Deprecated
    public static long getUsersCount(String connectionUriDomain, String tenantId, Main main)
            throws StorageQueryException {
        return StorageLayer.getThirdPartyStorage(connectionUriDomain, tenantId, main).getThirdPartyUsersCount();
    }

    @TestOnly
    @Deprecated
    public static long getUsersCount(Main main)
            throws StorageQueryException {
        return getUsersCount(null, null, main);
    }

}
