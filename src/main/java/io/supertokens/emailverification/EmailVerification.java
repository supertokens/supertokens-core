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

package io.supertokens.emailverification;

import io.supertokens.Main;
import io.supertokens.emailpassword.User;
import io.supertokens.emailverification.exception.EmailAlreadyVerifiedException;
import io.supertokens.emailverification.exception.EmailVerificationInvalidTokenException;
import io.supertokens.pluginInterface.emailpassword.EmailVerificationTokenInfo;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailVerificationTokenException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

public class EmailVerification {

    public static final long EMAIL_VERIFICATION_TOKEN_LIFETIME_MS =
            24 * 3600 * 1000; // this is related to the interval for the cronjob: DeleteExpiredEmailVerificationTokens

    private static long getEmailVerificationTokenLifetime(Main main) {
        if (Main.isTesting) {
            return EmailVerificationTest.getInstance(main).getEmailVerificationTokenLifetime();
        }
        return EMAIL_VERIFICATION_TOKEN_LIFETIME_MS;
    }

    public static String generateEmailVerificationToken(Main main, String userId)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException,
            UnknownUserIdException, EmailAlreadyVerifiedException {

        UserInfo user = StorageLayer.getEmailPasswordStorage(main).getUserInfoUsingId(userId);

        if (user == null) {
            throw new UnknownUserIdException();
        }

        if (user.isEmailVerified) {
            throw new EmailAlreadyVerifiedException();
        }

        while (true) {

            // we first generate a email verification token
            byte[] random = new byte[64];
            byte[] salt = new byte[64];

            new SecureRandom().nextBytes(random);
            new SecureRandom().nextBytes(salt);

            int iterations = 1000;
            String token = Utils
                    .toHex(Utils.pbkdf2(Utils.bytesToString(random).toCharArray(), salt, iterations, 64 * 6));

            // we make it URL safe:
            token = Utils.convertToBase64(token);
            token = token.replace("=", "");
            token = token.replace("/", "");
            token = token.replace("+", "");

            String hashedToken = Utils.hashSHA256(token);

            try {
                StorageLayer.getEmailPasswordStorage(main).addEmailVerificationToken(
                        new EmailVerificationTokenInfo(userId, hashedToken,
                                System.currentTimeMillis() + getEmailVerificationTokenLifetime(main), user.email));
                return token;
            } catch (DuplicateEmailVerificationTokenException ignored) {
            }
        }
    }

    public static User verifyEmail(Main main, String token)
            throws StorageQueryException, EmailVerificationInvalidTokenException, NoSuchAlgorithmException,
            StorageTransactionLogicException {

        String hashedToken = Utils.hashSHA256(token);

        EmailPasswordSQLStorage storage = StorageLayer.getEmailPasswordStorage(main);

        final EmailVerificationTokenInfo tokenInfo = storage.getEmailVerificationTokenInfo(hashedToken);
        if (tokenInfo == null) {
            throw new EmailVerificationInvalidTokenException();
        }

        final String userId = tokenInfo.userId;

        try {
            return storage.startTransaction(con -> {

                EmailVerificationTokenInfo[] allTokens = storage
                        .getAllEmailVerificationTokenInfoForUser_Transaction(con, userId);

                EmailVerificationTokenInfo matchedToken = null;
                for (EmailVerificationTokenInfo tok : allTokens) {
                    if (tok.token.equals(hashedToken)) {
                        matchedToken = tok;
                        break;
                    }
                }

                if (matchedToken == null) {
                    throw new StorageTransactionLogicException(new EmailVerificationInvalidTokenException());
                }

                storage.deleteAllEmailVerificationTokensForUser_Transaction(con, userId);

                if (matchedToken.tokenExpiry < System.currentTimeMillis()) {
                    storage.commitTransaction(con);
                    throw new StorageTransactionLogicException(new EmailVerificationInvalidTokenException());
                }

                UserInfo userInfo = storage.getUserInfoUsingId_Transaction(con, userId);

                if (!userInfo.email.equals(tokenInfo.email)) {
                    throw new StorageTransactionLogicException(new EmailVerificationInvalidTokenException());
                }

                storage.updateUsersIsEmailVerified_Transaction(con, userId, true);

                storage.commitTransaction(con);
                return new User(userInfo.id, userInfo.email, userInfo.timeJoined);
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof EmailVerificationInvalidTokenException) {
                throw (EmailVerificationInvalidTokenException) e.actualException;
            }
            throw e;
        }
    }

    public static boolean isEmailVerified(Main main, String userId) throws UnknownUserIdException,
            StorageQueryException {
        UserInfo user = StorageLayer.getEmailPasswordStorage(main).getUserInfoUsingId(userId);

        if (user == null) {
            throw new UnknownUserIdException();
        }

        return user.isEmailVerified;
    }
}
