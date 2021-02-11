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
import io.supertokens.UserPaginationToken;
import io.supertokens.emailpassword.exceptions.ResetPasswordInvalidTokenException;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicatePasswordResetTokenException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

public class EmailPassword {

    public static final String RECIPE_ID = "emailpassword";

    public static final long PASSWORD_RESET_TOKEN_LIFETIME_MS =
            3600 * 1000; // this is related to the interval for the cronjob: DeleteExpiredPasswordResetTokens

    private static long getPasswordResetTokenLifetime(Main main) {
        if (Main.isTesting) {
            return EmailPasswordTest.getInstance(main).getPasswordResetTokenLifetime();
        }
        return PASSWORD_RESET_TOKEN_LIFETIME_MS;
    }

    public static User signUp(Main main, @Nonnull String email, @Nonnull String password) throws
            DuplicateEmailException, StorageQueryException {

        String hashedPassword = UpdatableBCrypt.hash(password);

        while (true) {

            String userId = Utils.getUUID();
            long timeJoined = System.currentTimeMillis();

            try {
                StorageLayer.getEmailPasswordStorage(main)
                        .signUp(new UserInfo(userId, email, hashedPassword, timeJoined));

                return new User(userId, email, timeJoined);

            } catch (DuplicateUserIdException ignored) {
                // we retry with a new userId (while loop)
            }
        }
    }

    public static User signIn(Main main, @Nonnull String email, @Nonnull String password) throws StorageQueryException,
            WrongCredentialsException {

        UserInfo user = StorageLayer.getEmailPasswordStorage(main).getUserInfoUsingEmail(email);

        if (user == null) {
            throw new WrongCredentialsException();
        }

        try {
            if (!UpdatableBCrypt.verifyHash(password, user.passwordHash)) {
                throw new WrongCredentialsException();
            }
        } catch (WrongCredentialsException e) {
            throw e;
        } catch (Exception ignored) {
            throw new WrongCredentialsException();
        }

        return new User(user.id, user.email, user.timeJoined);
    }

    public static String generatePasswordResetToken(Main main, String userId)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException,
            UnknownUserIdException {

        while (true) {

            // we first generate a password reset token
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
                StorageLayer.getEmailPasswordStorage(main).addPasswordResetToken(
                        new PasswordResetTokenInfo(userId, hashedToken,
                                System.currentTimeMillis() + getPasswordResetTokenLifetime(main)));
                return token;
            } catch (DuplicatePasswordResetTokenException ignored) {
            }
        }
    }


    public static void resetPassword(Main main, String token, String password)
            throws ResetPasswordInvalidTokenException, NoSuchAlgorithmException, StorageQueryException,
            StorageTransactionLogicException {

        String hashedToken = Utils.hashSHA256(token);
        String hashedPassword = UpdatableBCrypt.hash(password);

        EmailPasswordSQLStorage storage = StorageLayer.getEmailPasswordStorage(main);

        PasswordResetTokenInfo resetInfo = storage.getPasswordResetTokenInfo(hashedToken);

        if (resetInfo == null) {
            throw new ResetPasswordInvalidTokenException();
        }

        final String userId = resetInfo.userId;

        try {
            storage.startTransaction(con -> {

                PasswordResetTokenInfo[] allTokens = storage
                        .getAllPasswordResetTokenInfoForUser_Transaction(con, userId);

                PasswordResetTokenInfo matchedToken = null;
                for (PasswordResetTokenInfo tok : allTokens) {
                    if (tok.token.equals(hashedToken)) {
                        matchedToken = tok;
                        break;
                    }
                }

                if (matchedToken == null) {
                    throw new StorageTransactionLogicException(new ResetPasswordInvalidTokenException());
                }

                storage.deleteAllPasswordResetTokensForUser_Transaction(con, userId);

                if (matchedToken.tokenExpiry < System.currentTimeMillis()) {
                    storage.commitTransaction(con);
                    throw new StorageTransactionLogicException(new ResetPasswordInvalidTokenException());
                }

                storage.updateUsersPassword_Transaction(con, userId, hashedPassword);

                storage.commitTransaction(con);
                return null;
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof ResetPasswordInvalidTokenException) {
                throw (ResetPasswordInvalidTokenException) e.actualException;
            }
            throw e;
        }
    }

    public static User getUserUsingId(Main main, String userId) throws StorageQueryException {
        UserInfo info = StorageLayer.getEmailPasswordStorage(main).getUserInfoUsingId(userId);
        if (info == null) {
            return null;
        }
        return new User(info.id, info.email, info.timeJoined);
    }

    public static User getUserUsingEmail(Main main, String email) throws StorageQueryException {
        UserInfo info = StorageLayer.getEmailPasswordStorage(main).getUserInfoUsingEmail(email);
        if (info == null) {
            return null;
        }
        return new User(info.id, info.email, info.timeJoined);
    }

    public static UserPaginationContainer getUsers(Main main, @Nullable String paginationToken, Integer limit,
                                                   String timeJoinedOrder)
            throws StorageQueryException, UserPaginationToken.InvalidTokenException {
        UserInfo[] users;
        if (paginationToken == null) {
            users = StorageLayer.getEmailPasswordStorage(main).getUsers(limit + 1, timeJoinedOrder);
        } else {
            UserPaginationToken tokenInfo = UserPaginationToken.extractTokenInfo(paginationToken);
            users = StorageLayer.getEmailPasswordStorage(main)
                    .getUsers(tokenInfo.userId, tokenInfo.timeJoined, limit + 1, timeJoinedOrder);
        }
        String nextPaginationToken = null;
        int maxLoop = users.length;
        if (users.length == limit + 1) {
            maxLoop = limit;
            nextPaginationToken = new UserPaginationToken(users[limit].id, users[limit].timeJoined).generateToken();
        }
        User[] resultUsers = new User[maxLoop];
        for (int i = 0; i < maxLoop; i++) {
            resultUsers[i] = new User(users[i].id, users[i].email, users[i].timeJoined);
        }
        return new UserPaginationContainer(resultUsers, nextPaginationToken);
    }

    public static long getUsersCount(Main main) throws StorageQueryException {
        return StorageLayer.getEmailPasswordStorage(main).getUsersCount();
    }
}
