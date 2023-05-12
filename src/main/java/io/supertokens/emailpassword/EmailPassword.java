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
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicatePasswordResetTokenException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.*;
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
        return Config.getConfig(tenantIdentifier, main).getPasswordResetTokenLifetime();
    }

    @TestOnly
    public static UserInfo signUp(Main main, @Nonnull String email, @Nonnull String password)
            throws DuplicateEmailException, StorageQueryException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return signUp(new TenantIdentifierWithStorage(null, null, null, storage),
                    main, email, password);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static UserInfo signUp(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main,
                                  @Nonnull String email, @Nonnull String password)
            throws DuplicateEmailException, StorageQueryException, TenantOrAppNotFoundException,
            BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifierWithStorage);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifierWithStorage);
        }
        if (!config.emailPasswordConfig.enabled) {
            throw new BadPermissionException("Email password login not enabled for tenant");
        }

        String hashedPassword = PasswordHashing.getInstance(main)
                .createHashWithSalt(tenantIdentifierWithStorage.toAppIdentifier(), password);

        while (true) {

            String userId = Utils.getUUID();
            long timeJoined = System.currentTimeMillis();

            try {
                return tenantIdentifierWithStorage.getEmailPasswordStorage().signUp(tenantIdentifierWithStorage, userId, email, hashedPassword, timeJoined);

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
            Storage storage = StorageLayer.getStorage(main);

            return importUserWithPasswordHash(
                    new TenantIdentifierWithStorage(null, null, null, storage), main, email,
                    passwordHash, hashingAlgorithm);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ImportUserResponse importUserWithPasswordHash(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                                Main main, @Nonnull String email,
                                                                @Nonnull String passwordHash, @Nullable
                                                                        CoreConfig.PASSWORD_HASHING_ALG hashingAlgorithm)
            throws StorageQueryException, StorageTransactionLogicException, UnsupportedPasswordHashingFormatException,
            TenantOrAppNotFoundException, BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifierWithStorage);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifierWithStorage);
        }
        if (!config.emailPasswordConfig.enabled) {
            throw new BadPermissionException("Email password login not enabled for tenant");
        }

        PasswordHashingUtils.assertSuperTokensSupportInputPasswordHashFormat(tenantIdentifierWithStorage.toAppIdentifier(), main,
                passwordHash, hashingAlgorithm);

        while (true) {
            String userId = Utils.getUUID();
            long timeJoined = System.currentTimeMillis();

            EmailPasswordSQLStorage storage = tenantIdentifierWithStorage.getEmailPasswordStorage();

            try {
                UserInfo userInfo = storage.signUp(tenantIdentifierWithStorage, userId, email, passwordHash,
                        timeJoined);
                return new ImportUserResponse(false, userInfo);
            } catch (DuplicateUserIdException e) {
                // we retry with a new userId
            } catch (DuplicateEmailException e) {
                UserInfo userInfoToBeUpdated = storage.getUserInfoUsingEmail(tenantIdentifierWithStorage, email);

                if (userInfoToBeUpdated != null) {
                    storage.startTransaction(con -> {
                        storage.updateUsersPassword_Transaction(tenantIdentifierWithStorage.toAppIdentifier(), con,
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
            Storage storage = StorageLayer.getStorage(main);
            return importUserWithPasswordHash(
                    new TenantIdentifierWithStorage(null, null, null, storage),
                    main, email, passwordHash, null);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static UserInfo signIn(Main main, @Nonnull String email,
                                  @Nonnull String password)
            throws StorageQueryException, WrongCredentialsException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return signIn(new TenantIdentifierWithStorage(null, null, null, storage),
                    main, email, password);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static UserInfo signIn(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main, @Nonnull String email,
                                  @Nonnull String password)
            throws StorageQueryException, WrongCredentialsException, TenantOrAppNotFoundException,
            BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifierWithStorage);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifierWithStorage);
        }
        if (!config.emailPasswordConfig.enabled) {
            throw new BadPermissionException("Email password login not enabled for tenant");
        }

        UserInfo user = tenantIdentifierWithStorage.getEmailPasswordStorage()
                .getUserInfoUsingEmail(tenantIdentifierWithStorage, email);

        if (user == null) {
            throw new WrongCredentialsException();
        }

        try {
            if (!PasswordHashing.getInstance(main)
                    .verifyPasswordWithHash(tenantIdentifierWithStorage.toAppIdentifier(), password, user.passwordHash)) {
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
            Storage storage = StorageLayer.getStorage(main);
            return generatePasswordResetToken(
                    new TenantIdentifierWithStorage(null, null, null, storage),
                    main, userId);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String generatePasswordResetToken(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main,
                                                    String userId)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException, UnknownUserIdException,
            TenantOrAppNotFoundException, BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifierWithStorage);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifierWithStorage);
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
                tenantIdentifierWithStorage.getEmailPasswordStorage().addPasswordResetToken(
                        tenantIdentifierWithStorage.toAppIdentifier(), new PasswordResetTokenInfo(userId,
                                hashedToken, System.currentTimeMillis() +
                                getPasswordResetTokenLifetime(tenantIdentifierWithStorage, main)));
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
            Storage storage = StorageLayer.getStorage(main);
            return resetPassword(new TenantIdentifierWithStorage(null, null, null, storage),
                    main, token, password);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String resetPassword(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main, String token,
                                       String password)
            throws ResetPasswordInvalidTokenException, NoSuchAlgorithmException, StorageQueryException,
            StorageTransactionLogicException, TenantOrAppNotFoundException {
        String hashedToken = Utils.hashSHA256(token);
        String hashedPassword = PasswordHashing.getInstance(main)
                .createHashWithSalt(tenantIdentifierWithStorage.toAppIdentifier(), password);
        EmailPasswordSQLStorage storage = tenantIdentifierWithStorage.getEmailPasswordStorage();

        PasswordResetTokenInfo resetInfo = storage.getPasswordResetTokenInfo(
                tenantIdentifierWithStorage.toAppIdentifier(), hashedToken);

        if (resetInfo == null) {
            throw new ResetPasswordInvalidTokenException();
        }

        final String userId = resetInfo.userId;

        try {
            return storage.startTransaction(con -> {

                PasswordResetTokenInfo[] allTokens = storage.getAllPasswordResetTokenInfoForUser_Transaction(
                        tenantIdentifierWithStorage.toAppIdentifier(), con,
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

                storage.deleteAllPasswordResetTokensForUser_Transaction(tenantIdentifierWithStorage.toAppIdentifier(), con,
                        userId);

                if (matchedToken.tokenExpiry < System.currentTimeMillis()) {
                    storage.commitTransaction(con);
                    throw new StorageTransactionLogicException(new ResetPasswordInvalidTokenException());
                }

                storage.updateUsersPassword_Transaction(tenantIdentifierWithStorage.toAppIdentifier(), con, userId,
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
            Storage storage = StorageLayer.getStorage(main);
            updateUsersEmailOrPassword(new AppIdentifierWithStorage(null, null, storage),
                    main, userId, email, password);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void updateUsersEmailOrPassword(AppIdentifierWithStorage appIdentifierWithStorage, Main main,
                                                  @Nonnull String userId, @Nullable String email,
                                                  @Nullable String password)
            throws StorageQueryException, StorageTransactionLogicException,
            UnknownUserIdException, DuplicateEmailException, TenantOrAppNotFoundException {
        EmailPasswordSQLStorage storage = appIdentifierWithStorage.getEmailPasswordStorage();
        try {
            storage.startTransaction(transaction -> {
                try {
                    UserInfo userInfo = storage.getUserInfoUsingId_Transaction(appIdentifierWithStorage, transaction, userId);

                    if (userInfo == null) {
                        throw new StorageTransactionLogicException(new UnknownUserIdException());
                    }

                    if (email != null) {
                        try {
                            storage.updateUsersEmail_Transaction(appIdentifierWithStorage, transaction,
                                    userId, email);
                        } catch (DuplicateEmailException e) {
                            throw new StorageTransactionLogicException(e);
                        }
                    }

                    if (password != null) {
                        String hashedPassword = PasswordHashing.getInstance(main)
                                .createHashWithSalt(appIdentifierWithStorage, password);
                        storage.updateUsersPassword_Transaction(appIdentifierWithStorage, transaction, userId, hashedPassword);
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
            Storage storage = StorageLayer.getStorage(main);
            return getUserUsingId(new AppIdentifierWithStorage(null, null, storage), userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static UserInfo getUserUsingId(AppIdentifierWithStorage appIdentifierWithStorage, String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return appIdentifierWithStorage.getEmailPasswordStorage().getUserInfoUsingId(appIdentifierWithStorage, userId);
    }

    public static UserInfo getUserUsingEmail(TenantIdentifierWithStorage tenantIdentifierWithStorage, String email)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return tenantIdentifierWithStorage.getEmailPasswordStorage().getUserInfoUsingEmail(
                tenantIdentifierWithStorage, email);
    }
}
