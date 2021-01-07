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
import io.supertokens.emailpassword.exceptions.EmailAlreadyVerifiedException;
import io.supertokens.emailpassword.exceptions.EmailVerificationInvalidTokenException;
import io.supertokens.emailpassword.exceptions.ResetPasswordInvalidTokenException;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.pluginInterface.emailpassword.EmailVerificationTokenInfo;
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.*;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

import javax.annotation.Nonnull;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

public class EmailPassword {

    public static final long PASSWORD_RESET_TOKEN_LIFETIME_MS =
            3600 * 1000; // this is related to the interval for the cronjob: DeleteExpiredPasswordResetTokens

    public static final long EMAIL_VERIFICATION_TOKEN_LIFETIME_MS =
            24 * 3600 * 1000; // this is related to the interval for the cronjob: DeleteExpiredEmailVerificationTokens

    private static long getPasswordResetTokenLifetime(Main main) {
        if (Main.isTesting) {
            return EmailPasswordTest.getInstance(main).getPasswordResetTokenLifetime();
        }
        return PASSWORD_RESET_TOKEN_LIFETIME_MS;
    }

    private static long getEmailVerificationTokenLifetime(Main main) {
        if (Main.isTesting) {
            return EmailPasswordTest.getInstance(main).getEmailVerificationTokenLifetime();
        }
        return EMAIL_VERIFICATION_TOKEN_LIFETIME_MS;
    }

    public static User signUp(Main main, @Nonnull String email, @Nonnull String password) throws
            DuplicateEmailException, StorageQueryException {

        String hashedPassword = UpdatableBCrypt.hash(password);

        while (true) {

            String userId = Utils.getUUID();

            try {
                StorageLayer.getEmailPasswordStorage(main)
                        .signUp(new UserInfo(userId, email, hashedPassword, System.currentTimeMillis()));

                return new User(userId, email);

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

        return new User(user.id, user.email);
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
        return new User(info.id, info.email);
    }

    public static User getUserUsingEmail(Main main, String email) throws StorageQueryException {
        UserInfo info = StorageLayer.getEmailPasswordStorage(main).getUserInfoUsingEmail(email);
        if (info == null) {
            return null;
        }
        return new User(info.id, info.email);
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
                return new User(userInfo.id, userInfo.email);
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
