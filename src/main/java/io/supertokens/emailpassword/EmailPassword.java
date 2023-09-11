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
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.emailpassword.exceptions.EmailChangeNotAllowedException;
import io.supertokens.emailpassword.exceptions.ResetPasswordInvalidTokenException;
import io.supertokens.emailpassword.exceptions.UnsupportedPasswordHashingFormatException;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicatePasswordResetTokenException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.WebserverAPI;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

public class EmailPassword {

    public static class ImportUserResponse {
        public boolean didUserAlreadyExist;
        public AuthRecipeUserInfo user;

        public ImportUserResponse(boolean didUserAlreadyExist, AuthRecipeUserInfo user) {
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
    public static AuthRecipeUserInfo signUp(Main main, @Nonnull String email, @Nonnull String password)
            throws DuplicateEmailException, StorageQueryException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return signUp(new TenantIdentifierWithStorage(null, null, null, storage),
                    main, email, password);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static AuthRecipeUserInfo signUp(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main,
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
                return tenantIdentifierWithStorage.getEmailPasswordStorage()
                        .signUp(tenantIdentifierWithStorage, userId, email, hashedPassword, timeJoined);

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

        PasswordHashingUtils.assertSuperTokensSupportInputPasswordHashFormat(
                tenantIdentifierWithStorage.toAppIdentifier(), main,
                passwordHash, hashingAlgorithm);

        while (true) {
            String userId = Utils.getUUID();
            long timeJoined = System.currentTimeMillis();

            EmailPasswordSQLStorage storage = tenantIdentifierWithStorage.getEmailPasswordStorage();

            try {
                AuthRecipeUserInfo userInfo = storage.signUp(tenantIdentifierWithStorage, userId, email, passwordHash,
                        timeJoined);
                return new ImportUserResponse(false, userInfo);
            } catch (DuplicateUserIdException e) {
                // we retry with a new userId
            } catch (DuplicateEmailException e) {
                AuthRecipeUserInfo[] allUsers = storage.listPrimaryUsersByEmail(tenantIdentifierWithStorage, email);
                AuthRecipeUserInfo userInfoToBeUpdated = null;
                LoginMethod loginMethod = null;
                for (AuthRecipeUserInfo currUser : allUsers) {
                    for (LoginMethod currLM : currUser.loginMethods) {
                        if (currLM.email.equals(email) && currLM.recipeId == RECIPE_ID.EMAIL_PASSWORD && currLM.tenantIds.contains(tenantIdentifierWithStorage.getTenantId())) {
                            userInfoToBeUpdated = currUser;
                            loginMethod = currLM;
                            break;
                        }
                    }
                }

                if (userInfoToBeUpdated != null) {
                    LoginMethod finalLoginMethod = loginMethod;
                    storage.startTransaction(con -> {
                        storage.updateUsersPassword_Transaction(tenantIdentifierWithStorage.toAppIdentifier(), con,
                                finalLoginMethod.getSupertokensUserId(), passwordHash);
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
    public static AuthRecipeUserInfo signIn(Main main, @Nonnull String email,
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

    public static AuthRecipeUserInfo signIn(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main,
                                            @Nonnull String email,
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

        AuthRecipeUserInfo[] users = tenantIdentifierWithStorage.getAuthRecipeStorage()
                .listPrimaryUsersByEmail(tenantIdentifierWithStorage, email);

        AuthRecipeUserInfo user = null;
        LoginMethod lM = null;
        for (AuthRecipeUserInfo currUser : users) {
            for (LoginMethod currLM : currUser.loginMethods) {
                if (currLM.recipeId == RECIPE_ID.EMAIL_PASSWORD && currLM.email.equals(email) && currLM.tenantIds.contains(tenantIdentifierWithStorage.getTenantId())) {
                    user = currUser;
                    lM = currLM;
                }
            }
        }

        if (user == null) {
            throw new WrongCredentialsException();
        }

        try {
            if (!PasswordHashing.getInstance(main)
                    .verifyPasswordWithHash(tenantIdentifierWithStorage.toAppIdentifier(), password,
                            lM.passwordHash)) {
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
    public static String generatePasswordResetTokenBeforeCdi4_0(Main main, String userId)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException, UnknownUserIdException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return generatePasswordResetTokenBeforeCdi4_0(
                    new TenantIdentifierWithStorage(null, null, null, storage),
                    main, userId);
        } catch (TenantOrAppNotFoundException | BadPermissionException | WebserverAPI.BadRequestException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static String generatePasswordResetTokenBeforeCdi4_0WithoutAddingEmail(Main main, String userId)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException, UnknownUserIdException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return generatePasswordResetToken(
                    new TenantIdentifierWithStorage(null, null, null, storage),
                    main, userId, null);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static String generatePasswordResetToken(Main main, String userId, String email)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException, UnknownUserIdException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return generatePasswordResetToken(
                    new TenantIdentifierWithStorage(null, null, null, storage),
                    main, userId, email);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String generatePasswordResetTokenBeforeCdi4_0(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                                Main main,
                                                                String userId)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException, UnknownUserIdException,
            TenantOrAppNotFoundException, BadPermissionException, WebserverAPI.BadRequestException {
        AppIdentifierWithStorage appIdentifierWithStorage =
                tenantIdentifierWithStorage.toAppIdentifierWithStorage();
        AuthRecipeUserInfo user = AuthRecipe.getUserById(appIdentifierWithStorage, userId);
        if (user == null) {
            throw new UnknownUserIdException();
        }
        if (user.loginMethods.length > 1) {
            throw new WebserverAPI.BadRequestException("Please use CDI version >= 4.0");
        }
        if (user.loginMethods[0].email == null ||
                user.loginMethods[0].recipeId != RECIPE_ID.EMAIL_PASSWORD) {
            // this used to be the behaviour of the older CDI version and it was enforced via a fkey constraint
            throw new UnknownUserIdException();
        }
        return generatePasswordResetToken(tenantIdentifierWithStorage, main, userId, user.loginMethods[0].email);
    }

    public static String generatePasswordResetToken(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main,
                                                    String userId, String email)
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
                                getPasswordResetTokenLifetime(tenantIdentifierWithStorage, main), email));
                return token;
            } catch (DuplicatePasswordResetTokenException ignored) {
            }
        }
    }

    @TestOnly
    @Deprecated
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

    @Deprecated
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

                storage.deleteAllPasswordResetTokensForUser_Transaction(tenantIdentifierWithStorage.toAppIdentifier(),
                        con,
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
    public static ConsumeResetPasswordTokenResult consumeResetPasswordToken(Main main, String token)
            throws ResetPasswordInvalidTokenException, NoSuchAlgorithmException, StorageQueryException,
            StorageTransactionLogicException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return consumeResetPasswordToken(new TenantIdentifierWithStorage(null, null, null, storage),
                    token);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class ConsumeResetPasswordTokenResult {
        public String userId;
        public String email;

        public ConsumeResetPasswordTokenResult(String userId, String email) {
            this.userId = userId;
            this.email = email;
        }
    }

    public static ConsumeResetPasswordTokenResult consumeResetPasswordToken(
            TenantIdentifierWithStorage tenantIdentifierWithStorage, String token)
            throws ResetPasswordInvalidTokenException, NoSuchAlgorithmException, StorageQueryException,
            StorageTransactionLogicException, TenantOrAppNotFoundException {
        String hashedToken = Utils.hashSHA256(token);

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

                storage.deleteAllPasswordResetTokensForUser_Transaction(tenantIdentifierWithStorage.toAppIdentifier(),
                        con,
                        userId);

                if (matchedToken.tokenExpiry < System.currentTimeMillis()) {
                    storage.commitTransaction(con);
                    throw new StorageTransactionLogicException(new ResetPasswordInvalidTokenException());
                }

                storage.commitTransaction(con);
                if (matchedToken.email == null) {
                    // this is possible if the token was generated before migration, and then consumed
                    // after migration
                    AppIdentifierWithStorage appIdentifierWithStorage =
                            tenantIdentifierWithStorage.toAppIdentifierWithStorage();
                    AuthRecipeUserInfo user = AuthRecipe.getUserById(appIdentifierWithStorage, userId);
                    if (user == null) {
                        throw new StorageTransactionLogicException(new ResetPasswordInvalidTokenException());
                    }
                    if (user.loginMethods.length > 1) {
                        throw new StorageTransactionLogicException(new ResetPasswordInvalidTokenException());
                    }
                    if (user.loginMethods[0].email == null ||
                            user.loginMethods[0].recipeId != RECIPE_ID.EMAIL_PASSWORD) {
                        throw new StorageTransactionLogicException(new ResetPasswordInvalidTokenException());
                    }
                    return new ConsumeResetPasswordTokenResult(matchedToken.userId, user.loginMethods[0].email);
                }
                return new ConsumeResetPasswordTokenResult(matchedToken.userId, matchedToken.email);
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
            UnknownUserIdException, DuplicateEmailException, EmailChangeNotAllowedException {
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
            UnknownUserIdException, DuplicateEmailException, TenantOrAppNotFoundException,
            EmailChangeNotAllowedException {
        EmailPasswordSQLStorage storage = appIdentifierWithStorage.getEmailPasswordStorage();
        AuthRecipeSQLStorage authRecipeStorage = (AuthRecipeSQLStorage) appIdentifierWithStorage.getAuthRecipeStorage();
        try {
            storage.startTransaction(transaction -> {
                try {
                    AuthRecipeUserInfo user = authRecipeStorage.getPrimaryUserById_Transaction(appIdentifierWithStorage,
                            transaction, userId);

                    if (user == null) {
                        throw new StorageTransactionLogicException(new UnknownUserIdException());
                    }
                    boolean foundEmailPasswordLoginMethod = false;
                    for (LoginMethod lm : user.loginMethods) {
                        if (lm.recipeId == RECIPE_ID.EMAIL_PASSWORD && lm.getSupertokensUserId().equals(userId)) {
                            foundEmailPasswordLoginMethod = true;
                            break;
                        }
                    }
                    if (!foundEmailPasswordLoginMethod) {
                        throw new StorageTransactionLogicException(new UnknownUserIdException());
                    }

                    if (email != null) {
                        if (user.isPrimaryUser) {
                            for (String tenantId : user.tenantIds) {
                                AuthRecipeUserInfo[] existingUsersWithNewEmail =
                                        authRecipeStorage.listPrimaryUsersByEmail_Transaction(
                                                appIdentifierWithStorage, transaction,
                                                email);

                                for (AuthRecipeUserInfo userWithSameEmail : existingUsersWithNewEmail) {
                                    if (!userWithSameEmail.tenantIds.contains(tenantId)) {
                                        continue;
                                    }
                                    if (userWithSameEmail.isPrimaryUser && !userWithSameEmail.getSupertokensUserId().equals(user.getSupertokensUserId())) {
                                        throw new StorageTransactionLogicException(
                                                new EmailChangeNotAllowedException());
                                    }
                                }
                            }
                        }

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
                        storage.updateUsersPassword_Transaction(appIdentifierWithStorage, transaction, userId,
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
            } else if (e.actualException instanceof EmailChangeNotAllowedException) {
                throw (EmailChangeNotAllowedException) e.actualException;
            }
            throw e;
        }
    }

    @Deprecated
    @TestOnly
    public static AuthRecipeUserInfo getUserUsingId(Main main, String userId)
            throws StorageQueryException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return getUserUsingId(new AppIdentifierWithStorage(null, null, storage), userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Deprecated
    public static AuthRecipeUserInfo getUserUsingId(AppIdentifierWithStorage appIdentifierWithStorage, String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        AuthRecipeUserInfo result = appIdentifierWithStorage.getAuthRecipeStorage()
                .getPrimaryUserById(appIdentifierWithStorage, userId);
        if (result == null) {
            return null;
        }
        for (LoginMethod lM : result.loginMethods) {
            if (lM.getSupertokensUserId().equals(userId) && lM.recipeId == RECIPE_ID.EMAIL_PASSWORD) {
                return AuthRecipeUserInfo.create(lM.getSupertokensUserId(), result.isPrimaryUser, lM);
            }
        }
        return null;
    }

    @Deprecated
    public static AuthRecipeUserInfo getUserUsingEmail(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                       String email)
            throws StorageQueryException, TenantOrAppNotFoundException {
        AuthRecipeUserInfo[] users = tenantIdentifierWithStorage.getAuthRecipeStorage().listPrimaryUsersByEmail(
                tenantIdentifierWithStorage, email);
        // filter used based on login method
        for (AuthRecipeUserInfo user : users) {
            for (LoginMethod lM : user.loginMethods) {
                if (lM.email.equals(email) && lM.recipeId == RECIPE_ID.EMAIL_PASSWORD) {
                    return user;
                }
            }
        }
        return null;
    }
}
