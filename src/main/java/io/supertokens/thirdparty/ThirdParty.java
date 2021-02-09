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
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.thirdparty.UserInfo;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateThirdPartyUserException;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateUserIdException;
import io.supertokens.pluginInterface.thirdparty.sqlStorage.ThirdPartySQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

public class ThirdParty {

    public static final String RECIPE_ID = "thirdparty";

    public static class SignInUpResponse {
        public boolean createdNewUser;
        public UserInfo user;

        public SignInUpResponse(boolean createdNewUser, UserInfo user) {
            this.createdNewUser = createdNewUser;
            this.user = user;
        }
    }

    public static SignInUpResponse signInUp(Main main, String thirdPartyId, String thirdPartyUserId, String email,
                                            boolean isEmailVerified)
            throws StorageQueryException {
        SignInUpResponse response = signInUpHelper(main, thirdPartyId, thirdPartyUserId, email);

        if (isEmailVerified) {
            try {
                StorageLayer.getEmailVerificationStorage(main).startTransaction(con -> {
                    StorageLayer.getEmailVerificationStorage(main)
                            .updateIsEmailVerified_Transaction(con, response.user.id, response.user.thirdParty.email,
                                    true);
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                throw new StorageQueryException(e);
            }
        }

        return response;
    }

    private static SignInUpResponse signInUpHelper(Main main, String thirdPartyId, String thirdPartyUserId,
                                                   String email)
            throws StorageQueryException {
        ThirdPartySQLStorage storage = StorageLayer.getThirdPartyStorage(main);
        while (true) {
            // loop for sign in + sign up

            while (true) {
                // loop for sign up
                String userId = Utils.getUUID();
                long timeJoined = System.currentTimeMillis();

                try {
                    UserInfo user = new UserInfo(userId,
                            new UserInfo.ThirdParty(thirdPartyId, thirdPartyUserId, email), timeJoined);

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
                        return null;
                    }

                    storage.updateUserEmail_Transaction(con, thirdPartyId, thirdPartyUserId, email);

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

    public static UserInfo getUser(Main main, String userId) throws StorageQueryException {
        return StorageLayer.getThirdPartyStorage(main).getUserInfoUsingId(userId);
    }

    public static UserInfo getUser(Main main, String thirdPartyId, String thirdPartyUserId)
            throws StorageQueryException {
        return StorageLayer.getThirdPartyStorage(main).getUserInfoUsingId(thirdPartyId, thirdPartyUserId);
    }

}
