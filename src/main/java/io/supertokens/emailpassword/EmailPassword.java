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
import io.supertokens.authRecipe.UserPaginationToken;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.emailpassword.exceptions.ResetPasswordInvalidTokenException;
import io.supertokens.emailpassword.exceptions.UnsupportedPasswordHashingFormatException;
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
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jakarta.servlet.ServletException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

public class EmailPassword {

    public static class ImportUserResponse {
        public boolean didUserAlreadyExist;
        public UserInfo user;

        public ImportUserResponse(boolean didUserAlreadyExist, UserInfo user) {
            this.didUserAlreadyExist = didUserAlreadyExist;
            this.user = user;
        }
    }

    @TestOnly
    public static long getPasswordResetTokenLifetimeForTests(Main main) {
        return getPasswordResetTokenLifetime(main);
    }

    private static long getPasswordResetTokenLifetime(Main main) {
        if (Main.isTesting) {
            return EmailPasswordTest.getInstance(main).getPasswordResetTokenLifetime();
        }
        return Config.getConfig(main).getPasswordResetTokenLifetime();
    }

    public static UserInfo signUp(Main main, @Nonnull String email, @Nonnull String password)
            throws DuplicateEmailException, StorageQueryException {

        String hashedPassword = PasswordHashing.getInstance(main).createHashWithSalt(password);

        while (true) {

            String userId = Utils.getUUID();
            long timeJoined = System.currentTimeMillis();

            try {
                UserInfo user = new UserInfo(userId, email, hashedPassword, timeJoined);
                StorageLayer.getEmailPasswordStorage(main).signUp(user);

                return user;

            } catch (DuplicateUserIdException ignored) {
                // we retry with a new userId (while loop)
            }
        }
    }

    public static ImportUserResponse importUserWithPasswordHash(Main main, @Nonnull String email,
            @Nonnull String passwordHash, @Nullable CoreConfig.PASSWORD_HASHING_ALG hashingAlgorithm)
            throws StorageQueryException, StorageTransactionLogicException, UnsupportedPasswordHashingFormatException {

        PasswordHashingUtils.assertSuperTokensSupportInputPasswordHashFormat(main, passwordHash, hashingAlgorithm);

        while (true) {
            String userId = Utils.getUUID();
            long timeJoined = System.currentTimeMillis();

            UserInfo userInfo = new UserInfo(userId, email, passwordHash, timeJoined);
            EmailPasswordSQLStorage storage = StorageLayer.getEmailPasswordStorage(main);

            try {
                StorageLayer.getEmailPasswordStorage(main).signUp(userInfo);
                return new ImportUserResponse(false, userInfo);
            } catch (DuplicateUserIdException e) {
                // we retry with a new userId
            } catch (DuplicateEmailException e) {
                UserInfo userInfoToBeUpdated = StorageLayer.getEmailPasswordStorage(main).getUserInfoUsingEmail(email);
                // if user does not exist we retry signup
                if (userInfoToBeUpdated != null) {
                    String finalPasswordHash = passwordHash;
                    storage.startTransaction(con -> {
                        storage.updateUsersPassword_Transaction(con, userInfoToBeUpdated.id, finalPasswordHash);
                        return null;
                    });
                    return new ImportUserResponse(true, userInfoToBeUpdated);
                }
            }
        }
    }

    @TestOnly
    public static ImportUserResponse importUserWithPasswordHash(Main main, @Nonnull String email,
            @Nonnull String passwordHash)
            throws StorageQueryException, StorageTransactionLogicException, UnsupportedPasswordHashingFormatException {
        return importUserWithPasswordHash(main, email, passwordHash, null);
    }

    public static UserInfo signIn(Main main, @Nonnull String email, @Nonnull String password)
            throws StorageQueryException, WrongCredentialsException {

        UserInfo user = StorageLayer.getEmailPasswordStorage(main).getUserInfoUsingEmail(email);

        if (user == null) {
            throw new WrongCredentialsException();
        }

        try {
            if (!PasswordHashing.getInstance(main).verifyPasswordWithHash(password, user.passwordHash)) {
                throw new WrongCredentialsException();
            }
        } catch (WrongCredentialsException e) {
            throw e;
        } catch (IllegalStateException e) {
            if (e.getMessage().equals("'firebase_password_hashing_signer_key' cannot be null")) {
                throw e;
            }
            throw new WrongCredentialsException();

        } catch (Exception ignored) {
            throw new WrongCredentialsException();
        }

        return user;
    }

    public static String generatePasswordResetToken(Main main, String userId)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException, UnknownUserIdException {

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
                StorageLayer.getEmailPasswordStorage(main).addPasswordResetToken(new PasswordResetTokenInfo(userId,
                        hashedToken, System.currentTimeMillis() + getPasswordResetTokenLifetime(main)));
                return token;
            } catch (DuplicatePasswordResetTokenException ignored) {
            }
        }
    }

    public static String resetPassword(Main main, String token, String password)
            throws ResetPasswordInvalidTokenException, NoSuchAlgorithmException, StorageQueryException,
            StorageTransactionLogicException {

        String hashedToken = Utils.hashSHA256(token);
        String hashedPassword = PasswordHashing.getInstance(main).createHashWithSalt(password);

        EmailPasswordSQLStorage storage = StorageLayer.getEmailPasswordStorage(main);

        PasswordResetTokenInfo resetInfo = storage.getPasswordResetTokenInfo(hashedToken);

        if (resetInfo == null) {
            throw new ResetPasswordInvalidTokenException();
        }

        final String userId = resetInfo.userId;

        try {
            return storage.startTransaction(con -> {

                PasswordResetTokenInfo[] allTokens = storage.getAllPasswordResetTokenInfoForUser_Transaction(con,
                        userId);

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
                return userId;
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof ResetPasswordInvalidTokenException) {
                throw (ResetPasswordInvalidTokenException) e.actualException;
            }
            throw e;
        }
    }

    public static void updateUsersEmailOrPassword(Main main, @Nonnull String userId, @Nullable String email,
            @Nullable String password) throws StorageQueryException, StorageTransactionLogicException,
            UnknownUserIdException, DuplicateEmailException {
        EmailPasswordSQLStorage storage = StorageLayer.getEmailPasswordStorage(main);
        try {
            storage.startTransaction(transaction -> {
                UserInfo userInfo = storage.getUserInfoUsingId_Transaction(transaction, userId);

                if (userInfo == null) {
                    throw new StorageTransactionLogicException(new UnknownUserIdException());
                }

                if (email != null) {
                    try {
                        storage.updateUsersEmail_Transaction(transaction, userId, email);
                    } catch (DuplicateEmailException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                }

                if (password != null) {
                    String hashedPassword = PasswordHashing.getInstance(main).createHashWithSalt(password);
                    storage.updateUsersPassword_Transaction(transaction, userId, hashedPassword);
                }

                storage.commitTransaction(transaction);
                return null;
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof UnknownUserIdException) {
                throw (UnknownUserIdException) e.actualException;
            } else if (e.actualException instanceof DuplicateEmailException) {
                throw (DuplicateEmailException) e.actualException;
            }
            throw e;
        }
    }

    public static UserInfo getUserUsingId(Main main, String userId) throws StorageQueryException {
        return StorageLayer.getEmailPasswordStorage(main).getUserInfoUsingId(userId);
    }

    public static UserInfo getUserUsingEmail(Main main, String email) throws StorageQueryException {
        return StorageLayer.getEmailPasswordStorage(main).getUserInfoUsingEmail(email);
    }

    @Deprecated
    public static UserPaginationContainer getUsers(Main main, @Nullable String paginationToken, Integer limit,
            String timeJoinedOrder) throws StorageQueryException, UserPaginationToken.InvalidTokenException {
        UserInfo[] users;
        if (paginationToken == null) {
            users = StorageLayer.getEmailPasswordStorage(main).getUsers(limit + 1, timeJoinedOrder);
        } else {
            UserPaginationToken tokenInfo = UserPaginationToken.extractTokenInfo(paginationToken);
            users = StorageLayer.getEmailPasswordStorage(main).getUsers(tokenInfo.userId, tokenInfo.timeJoined,
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

    @Deprecated
    public static long getUsersCount(Main main) throws StorageQueryException {
        return StorageLayer.getEmailPasswordStorage(main).getUsersCount();
    }
}
