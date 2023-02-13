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
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
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
    public static SignInUpResponse signInUp2_7(TenantIdentifier tenantIdentifier, Main main,
                                               String thirdPartyId, String thirdPartyUserId, String email,
                                               boolean isEmailVerified)
            throws StorageQueryException, TenantOrAppNotFoundException {
        SignInUpResponse response = signInUpHelper(tenantIdentifier, main, thirdPartyId, thirdPartyUserId,
                email);

        if (isEmailVerified) {
            try {
                StorageLayer.getEmailVerificationStorage(tenantIdentifier, main).startTransaction(con -> {
                    try {
                        StorageLayer.getEmailVerificationStorage(tenantIdentifier, main)
                                .updateIsEmailVerified_Transaction(con,
                                        response.user.id, response.user.email, true);
                        StorageLayer.getEmailVerificationStorage(tenantIdentifier, main)
                                .commitTransaction(con);
                        return null;
                    } catch (TenantOrAppNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof TenantOrAppNotFoundException) {
                    throw (TenantOrAppNotFoundException) e.actualException;
                }
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
        try {
            return signInUp2_7(new TenantIdentifier(null, null, null), main, thirdPartyId, thirdPartyUserId, email,
                    isEmailVerified);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static SignInUpResponse signInUp(Main main, String thirdPartyId, String thirdPartyUserId, String email)
            throws StorageQueryException {
        try {
            return signInUp(new TenantIdentifier(null, null, null), main, thirdPartyId, thirdPartyUserId, email);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static SignInUpResponse signInUp(TenantIdentifier tenantIdentifier, Main main, String thirdPartyId,
                                            String thirdPartyUserId, String email)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return signInUpHelper(tenantIdentifier, main, thirdPartyId, thirdPartyUserId, email);
    }

    private static SignInUpResponse signInUpHelper(TenantIdentifier tenantIdentifier, Main main,
                                                   String thirdPartyId, String thirdPartyUserId,
                                                   String email) throws StorageQueryException,
            TenantOrAppNotFoundException {
        ThirdPartySQLStorage storage = StorageLayer.getThirdPartyStorage(tenantIdentifier, main);
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

    public static UserInfo getUser(TenantIdentifier tenantIdentifier, Main main, String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getThirdPartyStorage(tenantIdentifier, main)
                .getThirdPartyUserInfoUsingId(userId);
    }

    @TestOnly
    public static UserInfo getUser(Main main, String userId)
            throws StorageQueryException {
        try {
            return getUser(new TenantIdentifier(null, null, null), main, userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }


    public static UserInfo getUser(TenantIdentifier tenantIdentifier, Main main, String thirdPartyId,
                                   String thirdPartyUserId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getThirdPartyStorage(tenantIdentifier, main)
                .getThirdPartyUserInfoUsingId(thirdPartyId, thirdPartyUserId);
    }

    @TestOnly
    public static UserInfo getUser(Main main, String thirdPartyId,
                                   String thirdPartyUserId)
            throws StorageQueryException {
        try {
            return getUser(new TenantIdentifier(null, null, null), main, thirdPartyId, thirdPartyUserId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Deprecated
    public static UserPaginationContainer getUsers(TenantIdentifier tenantIdentifier, Main main,
                                                   @Nullable String paginationToken, Integer limit,
                                                   String timeJoinedOrder)
            throws StorageQueryException, UserPaginationToken.InvalidTokenException, TenantOrAppNotFoundException {
        UserInfo[] users;
        if (paginationToken == null) {
            users = StorageLayer.getThirdPartyStorage(tenantIdentifier, main)
                    .getThirdPartyUsers(limit + 1, timeJoinedOrder);
        } else {
            UserPaginationToken tokenInfo = UserPaginationToken.extractTokenInfo(paginationToken);
            users = StorageLayer.getThirdPartyStorage(tenantIdentifier, main)
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
        try {
            return getUsers(new TenantIdentifier(null, null, null), main, paginationToken, limit, timeJoinedOrder);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static UserInfo[] getUsersByEmail(TenantIdentifier tenantIdentifier, Main main,
                                             @Nonnull String email)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getThirdPartyStorage(tenantIdentifier, main).getThirdPartyUsersByEmail(email);
    }

    @Deprecated
    public static long getUsersCount(TenantIdentifier tenantIdentifier, Main main)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getThirdPartyStorage(tenantIdentifier, main).getThirdPartyUsersCount();
    }

    @TestOnly
    @Deprecated
    public static long getUsersCount(Main main)
            throws StorageQueryException {
        try {
            return getUsersCount(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void verifyThirdPartyProvidersArray(ThirdPartyConfig.Provider[] providers)
            throws InvalidProviderConfigException {
        // TODO:
    }

}
