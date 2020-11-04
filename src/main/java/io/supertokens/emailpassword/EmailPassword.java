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
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicatePasswordResetTokenException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

import javax.annotation.Nonnull;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class EmailPassword {

    public static final long PASSWORD_RESET_TOKEN_LIFETIME_MS = 3600 * 1000;

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

    public static String generatePasswordResetToken(Main main, String userId)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException,
            UnknownUserIdException {

        while (true) {
            String token = Utils.convertToBase64(Utils.generateNewSigningKey());

            // we make it URL safe:
            token = token.replace("=", "");
            token = token.replace("/", "");
            token = token.replace("+", "");

            String hashedToken = Utils.hashSHA256(token);


            try {
                StorageLayer.getEmailPasswordStorageLayer(main).addPasswordResetToken(
                        new PasswordResetTokenInfo(userId, hashedToken,
                                System.currentTimeMillis() + PASSWORD_RESET_TOKEN_LIFETIME_MS));
                return token;
            } catch (DuplicatePasswordResetTokenException ignored) {
            }
        }
    }
}
