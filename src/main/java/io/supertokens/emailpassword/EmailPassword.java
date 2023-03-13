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
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.emailpassword.exceptions.ResetPasswordInvalidTokenException;
import io.supertokens.emailpassword.exceptions.UnsupportedPasswordHashingFormatException;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicatePasswordResetTokenException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
        try {
            return getPasswordResetTokenLifetime(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long getPasswordResetTokenLifetime(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        if (Main.isTesting) {
            return EmailPasswordTest.getInstance(main).getPasswordResetTokenLifetime();
        }
        return Config.getConfig(tenantIdentifier, main).getPasswordResetTokenLifetime();
    }

    @TestOnly
    public static UserInfo signUp(Main main, @Nonnull String email, @Nonnull String password)
            throws DuplicateEmailException, StorageQueryException {
        try {
            return signUp(new TenantIdentifier(null, null, null), main, email, password);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static UserInfo signUp(TenantIdentifier tenantIdentifier, Main main, @Nonnull String email,
                                  @Nonnull String password)
            throws DuplicateEmailException, StorageQueryException, TenantOrAppNotFoundException,
            BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifier);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifier);
        }
        if (!config.emailPasswordConfig.enabled) {
            throw new BadPermissionException("Email password login not enabled for tenant");
        }

        String hashedPassword = PasswordHashing.getInstance(main)
                .createHashWithSalt(tenantIdentifier.toAppIdentifier(), password);

        while (true) {

            String userId = Utils.getUUID();
            long timeJoined = System.currentTimeMillis();

            try {
                UserInfo user = new UserInfo(userId, email, hashedPassword, timeJoined);
                StorageLayer.getEmailPasswordStorage(tenantIdentifier, main).signUp(tenantIdentifier, user);

                return user;

            } catch (DuplicateUserIdException ignored) {
                // we retry with a new userId (while loop)
            }
        }
    }

    @TestOnly
    public static ImportUserResponse importUserWithPasswordHash(Main main, @Nonnull String email,
                                                                @Nonnull String passwordHash, @Nullable
                                                                        CoreConfig.PASSWORD_HASHING_ALG hashingAlgorithm)
            throws StorageQueryException, StorageTransactionLogicException, UnsupportedPasswordHashingFormatException {
        try {
            return importUserWithPasswordHash(new TenantIdentifier(null, null, null), main, email, passwordHash,
                    hashingAlgorithm);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ImportUserResponse importUserWithPasswordHash(TenantIdentifier tenantIdentifier, Main main,
                                                                @Nonnull String email,
                                                                @Nonnull String passwordHash, @Nullable
                                                                        CoreConfig.PASSWORD_HASHING_ALG hashingAlgorithm)
            throws StorageQueryException, StorageTransactionLogicException, UnsupportedPasswordHashingFormatException,
            TenantOrAppNotFoundException, BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifier);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifier);
        }
        if (!config.emailPasswordConfig.enabled) {
            throw new BadPermissionException("Email password login not enabled for tenant");
        }

        PasswordHashingUtils.assertSuperTokensSupportInputPasswordHashFormat(tenantIdentifier.toAppIdentifier(), main,
                passwordHash, hashingAlgorithm);

        while (true) {
            String userId = Utils.getUUID();
            long timeJoined = System.currentTimeMillis();

            UserInfo userInfo = new UserInfo(userId, email, passwordHash, timeJoined);
            EmailPasswordSQLStorage storage = StorageLayer.getEmailPasswordStorage(tenantIdentifier, main);

            try {
                storage.signUp(tenantIdentifier, userInfo);
                return new ImportUserResponse(false, userInfo);
            } catch (DuplicateUserIdException e) {
                // we retry with a new userId
            } catch (DuplicateEmailException e) {
                UserInfo userInfoToBeUpdated = storage.getUserInfoUsingEmail(tenantIdentifier, email);

                if (userInfoToBeUpdated != null) {
                    storage.startTransaction(con -> {
                        storage.updateUsersPassword_Transaction(tenantIdentifier.toAppIdentifier(), con,
                                userInfoToBeUpdated.id, passwordHash);
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
        try {
            return importUserWithPasswordHash(new TenantIdentifier(null, null, null), main, email, passwordHash, null);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static UserInfo signIn(Main main, @Nonnull String email,
                                  @Nonnull String password)
            throws StorageQueryException, WrongCredentialsException {
        try {
            return signIn(new TenantIdentifier(null, null, null), main, email, password);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static UserInfo signIn(TenantIdentifier tenantIdentifier, Main main, @Nonnull String email,
                                  @Nonnull String password)
            throws StorageQueryException, WrongCredentialsException, TenantOrAppNotFoundException,
            BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifier);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifier);
        }
        if (!config.emailPasswordConfig.enabled) {
            throw new BadPermissionException("Email password login not enabled for tenant");
        }

        UserInfo user = StorageLayer.getEmailPasswordStorage(tenantIdentifier, main)
                .getUserInfoUsingEmail(tenantIdentifier, email);

        if (user == null) {
            throw new WrongCredentialsException();
        }

        try {
            if (!PasswordHashing.getInstance(main)
                    .verifyPasswordWithHash(tenantIdentifier.toAppIdentifier(), password, user.passwordHash)) {
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

    @TestOnly
    public static String generatePasswordResetToken(Main main, String userId)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException, UnknownUserIdException {
        try {
            return generatePasswordResetToken(new TenantIdentifier(null, null, null), main, userId);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String generatePasswordResetToken(TenantIdentifier tenantIdentifier, Main main,
                                                    String userId)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException, UnknownUserIdException,
            TenantOrAppNotFoundException, BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifier);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifier);
        }
        if (!config.emailPasswordConfig.enabled) {
            throw new BadPermissionException("Email password login not enabled for tenant");
        }

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
                StorageLayer.getEmailPasswordStorage(tenantIdentifier, main)
                        .addPasswordResetToken(tenantIdentifier.toAppIdentifier(), new PasswordResetTokenInfo(userId,
                                hashedToken, System.currentTimeMillis() +
                                getPasswordResetTokenLifetime(tenantIdentifier, main)));
                return token;
            } catch (DuplicatePasswordResetTokenException ignored) {
            }
        }
    }

    @TestOnly
    public static String resetPassword(Main main, String token,
                                       String password)
            throws ResetPasswordInvalidTokenException, NoSuchAlgorithmException, StorageQueryException,
            StorageTransactionLogicException {
        try {
            return resetPassword(new TenantIdentifier(null, null, null), main, token, password);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String resetPassword(TenantIdentifier tenantIdentifier, Main main, String token,
                                       String password)
            throws ResetPasswordInvalidTokenException, NoSuchAlgorithmException, StorageQueryException,
            StorageTransactionLogicException, TenantOrAppNotFoundException {
        String hashedToken = Utils.hashSHA256(token);
        String hashedPassword = PasswordHashing.getInstance(main)
                .createHashWithSalt(tenantIdentifier.toAppIdentifier(), password);
        EmailPasswordSQLStorage storage = StorageLayer.getEmailPasswordStorage(tenantIdentifier, main);

        PasswordResetTokenInfo resetInfo = storage.getPasswordResetTokenInfo(tenantIdentifier.toAppIdentifier(),
                hashedToken);

        if (resetInfo == null) {
            throw new ResetPasswordInvalidTokenException();
        }

        final String userId = resetInfo.userId;

        try {
            return storage.startTransaction(con -> {

                PasswordResetTokenInfo[] allTokens = storage.getAllPasswordResetTokenInfoForUser_Transaction(
                        tenantIdentifier.toAppIdentifier(), con,
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

                storage.deleteAllPasswordResetTokensForUser_Transaction(tenantIdentifier.toAppIdentifier(), con,
                        userId);

                if (matchedToken.tokenExpiry < System.currentTimeMillis()) {
                    storage.commitTransaction(con);
                    throw new StorageTransactionLogicException(new ResetPasswordInvalidTokenException());
                }

                storage.updateUsersPassword_Transaction(tenantIdentifier.toAppIdentifier(), con, userId,
                        hashedPassword);

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

    @TestOnly
    public static void updateUsersEmailOrPassword(Main main,
                                                  @Nonnull String userId, @Nullable String email,
                                                  @Nullable String password)
            throws StorageQueryException, StorageTransactionLogicException,
            UnknownUserIdException, DuplicateEmailException {
        try {
            updateUsersEmailOrPassword(new TenantIdentifier(null, null, null), main, userId, email, password);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void updateUsersEmailOrPassword(TenantIdentifier tenantIdentifier, Main main,
                                                  @Nonnull String userId, @Nullable String email,
                                                  @Nullable String password)
            throws StorageQueryException, StorageTransactionLogicException,
            UnknownUserIdException, DuplicateEmailException, TenantOrAppNotFoundException {
        EmailPasswordSQLStorage storage = StorageLayer.getEmailPasswordStorage(tenantIdentifier, main);
        try {
            storage.startTransaction(transaction -> {
                try {
                    UserInfo userInfo = storage.getUserInfoUsingId_Transaction(tenantIdentifier.toAppIdentifier(),
                            transaction, userId);

                    if (userInfo == null) {
                        throw new StorageTransactionLogicException(new UnknownUserIdException());
                    }

                    if (email != null) {
                        try {
                            storage.updateUsersEmail_Transaction(tenantIdentifier.toAppIdentifier(), transaction,
                                    userId, email);
                        } catch (DuplicateEmailException e) {
                            throw new StorageTransactionLogicException(e);
                        }
                    }

                    if (password != null) {
                        String hashedPassword = PasswordHashing.getInstance(main)
                                .createHashWithSalt(tenantIdentifier.toAppIdentifier(), password);
                        storage.updateUsersPassword_Transaction(tenantIdentifier.toAppIdentifier(), transaction, userId,
                                hashedPassword);
                    }

                    storage.commitTransaction(transaction);
                    return null;
                } catch (TenantOrAppNotFoundException e) {
                    throw new StorageTransactionLogicException(e);
                }
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof UnknownUserIdException) {
                throw (UnknownUserIdException) e.actualException;
            } else if (e.actualException instanceof DuplicateEmailException) {
                throw (DuplicateEmailException) e.actualException;
            } else if (e.actualException instanceof TenantOrAppNotFoundException) {
                throw (TenantOrAppNotFoundException) e.actualException;
            }
            throw e;
        }
    }

    @TestOnly
    public static UserInfo getUserUsingId(Main main, String userId)
            throws StorageQueryException {
        try {
            return getUserUsingId(new TenantIdentifier(null, null, null), main, userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static UserInfo getUserUsingId(TenantIdentifier tenantIdentifier, Main main, String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getEmailPasswordStorage(tenantIdentifier, main)
                .getUserInfoUsingId(tenantIdentifier.toAppIdentifier(), userId);
    }

    public static UserInfo getUserUsingEmail(TenantIdentifier tenantIdentifier, Main main, String email)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getEmailPasswordStorage(tenantIdentifier, main)
                .getUserInfoUsingEmail(tenantIdentifier, email);
    }
}
