/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.emailpassword;

import io.supertokens.Main;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

import javax.annotation.Nonnull;

public class EmailPassword {

    public static User signUp(Main main, @Nonnull String email, @Nonnull String password) throws
            DuplicateEmailException, StorageQueryException {

        String hashedPassword = UpdatableBCrypt.hash(password);

        while (true) {

            String userId = Utils.getUUID();

            try {
                StorageLayer.getEmailPasswordStorageLayer(main)
                        .signUp(new UserInfo(userId, email, hashedPassword, System.currentTimeMillis()));

                return new User(userId, email);

            } catch (DuplicateUserIdException ignored) {
                // we retry with a new userId (while loop)
            }
        }
    }

    public static User signIn(Main main, @Nonnull String email, @Nonnull String password) throws StorageQueryException,
            WrongCredentialsException {

        UserInfo user = StorageLayer.getEmailPasswordStorageLayer(main).getUserInfoUsingEmail(email);

        if (user == null || !UpdatableBCrypt.verifyHash(password, user.passwordHash)) {
            throw new WrongCredentialsException();
        }

        return new User(user.id, user.email);
    }
}
