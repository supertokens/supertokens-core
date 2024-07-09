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
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicatePasswordResetTokenException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
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
            return signUp(new TenantIdentifier(null, null, null), storage,
                    main, email, password);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static AuthRecipeUserInfo signUp(TenantIdentifier tenantIdentifier, Storage storage, Main main,
                                            @Nonnull String email, @Nonnull String password)
            throws DuplicateEmailException, StorageQueryException, TenantOrAppNotFoundException,
            BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifier);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifier);
        }

        String hashedPassword = PasswordHashing.getInstance(main)
                .createHashWithSalt(tenantIdentifier.toAppIdentifier(), password);

        while (true) {
            String userId = Utils.getUUID();
            long timeJoined = System.currentTimeMillis();

            try {
                AuthRecipeUserInfo newUser = StorageUtils.getEmailPasswordStorage(storage)
                        .signUp(tenantIdentifier, userId, email, hashedPassword, timeJoined);

                if (Utils.isFakeEmail(email)) {
                    try {
                        EmailVerificationSQLStorage evStorage = StorageUtils.getEmailVerificationStorage(storage);
                        evStorage.startTransaction(con -> {
                            try {
                                evStorage.updateIsEmailVerified_Transaction(tenantIdentifier.toAppIdentifier(), con,
                                        newUser.getSupertokensUserId(), email, true);
                                evStorage.commitTransaction(con);

                                return null;
                            } catch (TenantOrAppNotFoundException e) {
                                throw new StorageTransactionLogicException(e);
                            }
                        });
                        newUser.loginMethods[0].setVerified(); // newly created user has only one loginMethod
                    } catch (StorageTransactionLogicException e) {
                        if (e.actualException instanceof TenantOrAppNotFoundException) {
                            throw (TenantOrAppNotFoundException) e.actualException;
                        }
                        throw new StorageQueryException(e);
                    }
                }

                return newUser;
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
                    new TenantIdentifier(null, null, null), storage, main, email,
                    passwordHash, hashingAlgorithm);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ImportUserResponse importUserWithPasswordHash(TenantIdentifier tenantIdentifier, Storage storage,
                                                                Main main, @Nonnull String email,
                                                                @Nonnull String passwordHash, @Nullable
                                                                CoreConfig.PASSWORD_HASHING_ALG hashingAlgorithm)
            throws StorageQueryException, StorageTransactionLogicException, UnsupportedPasswordHashingFormatException,
            TenantOrAppNotFoundException, BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifier);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifier);
        }

        PasswordHashingUtils.assertSuperTokensSupportInputPasswordHashFormat(
                tenantIdentifier.toAppIdentifier(), main,
                passwordHash, hashingAlgorithm);

        while (true) {
            String userId = Utils.getUUID();
            long timeJoined = System.currentTimeMillis();

            EmailPasswordSQLStorage epStorage = StorageUtils.getEmailPasswordStorage(storage);

            try {
                AuthRecipeUserInfo userInfo = epStorage.signUp(tenantIdentifier, userId, email, passwordHash,
                        timeJoined);
                return new ImportUserResponse(false, userInfo);
            } catch (DuplicateUserIdException e) {
                // we retry with a new userId
            } catch (DuplicateEmailException e) {
                AuthRecipeUserInfo[] allUsers = epStorage.listPrimaryUsersByEmail(tenantIdentifier, email);
                AuthRecipeUserInfo userInfoToBeUpdated = null;
                LoginMethod loginMethod = null;
                for (AuthRecipeUserInfo currUser : allUsers) {
                    for (LoginMethod currLM : currUser.loginMethods) {
                        if (currLM.email.equals(email) && currLM.recipeId == RECIPE_ID.EMAIL_PASSWORD &&
                                currLM.tenantIds.contains(tenantIdentifier.getTenantId())) {
                            userInfoToBeUpdated = currUser;
                            loginMethod = currLM;
                            break;
                        }
                    }
                }

                if (userInfoToBeUpdated != null) {
                    LoginMethod finalLoginMethod = loginMethod;
                    epStorage.startTransaction(con -> {
                        epStorage.updateUsersPassword_Transaction(tenantIdentifier.toAppIdentifier(), con,
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
                    new TenantIdentifier(null, null, null), storage,
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
            return signIn(new TenantIdentifier(null, null, null), storage,
                    main, email, password);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static AuthRecipeUserInfo signIn(TenantIdentifier tenantIdentifier, Storage storage, Main main,
                                            @Nonnull String email,
                                            @Nonnull String password)
            throws StorageQueryException, WrongCredentialsException, TenantOrAppNotFoundException,
            BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifier);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifier);
        }

        AuthRecipeUserInfo[] users = StorageUtils.getEmailPasswordStorage(storage)
                .listPrimaryUsersByEmail(tenantIdentifier, email);

        AuthRecipeUserInfo user = null;
        LoginMethod lM = null;
        for (AuthRecipeUserInfo currUser : users) {
            for (LoginMethod currLM : currUser.loginMethods) {
                if (currLM.recipeId == RECIPE_ID.EMAIL_PASSWORD && currLM.email.equals(email) &&
                        currLM.tenantIds.contains(tenantIdentifier.getTenantId())) {
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
                    .verifyPasswordWithHash(tenantIdentifier.toAppIdentifier(), password,
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
                    new TenantIdentifier(null, null, null), storage,
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
                    new TenantIdentifier(null, null, null), storage,
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
                    new TenantIdentifier(null, null, null), storage,
                    main, userId, email);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String generatePasswordResetTokenBeforeCdi4_0(TenantIdentifier tenantIdentifier, Storage storage,
                                                                Main main,
                                                                String userId)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException, UnknownUserIdException,
            TenantOrAppNotFoundException, BadPermissionException, WebserverAPI.BadRequestException {
        AuthRecipeUserInfo user = AuthRecipe.getUserById(tenantIdentifier.toAppIdentifier(), storage, userId);
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
        return generatePasswordResetToken(tenantIdentifier, storage, main, userId, user.loginMethods[0].email);
    }

    public static String generatePasswordResetToken(TenantIdentifier tenantIdentifier, Storage storage, Main main,
                                                    String userId, String email)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException, UnknownUserIdException,
            TenantOrAppNotFoundException, BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifier);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifier);
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
                StorageUtils.getEmailPasswordStorage(storage).addPasswordResetToken(
                        tenantIdentifier.toAppIdentifier(), new PasswordResetTokenInfo(userId,
                                hashedToken, System.currentTimeMillis() +
                                getPasswordResetTokenLifetime(tenantIdentifier, main), email));
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
            return resetPassword(new TenantIdentifier(null, null, null), storage,
                    main, token, password);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Deprecated
    public static String resetPassword(TenantIdentifier tenantIdentifier, Storage storage, Main main, String token,
                                       String password)
            throws ResetPasswordInvalidTokenException, NoSuchAlgorithmException, StorageQueryException,
            StorageTransactionLogicException, TenantOrAppNotFoundException {
        String hashedToken = Utils.hashSHA256(token);
        String hashedPassword = PasswordHashing.getInstance(main)
                .createHashWithSalt(tenantIdentifier.toAppIdentifier(), password);
        EmailPasswordSQLStorage epStorage = StorageUtils.getEmailPasswordStorage(storage);

        PasswordResetTokenInfo resetInfo = epStorage.getPasswordResetTokenInfo(
                tenantIdentifier.toAppIdentifier(), hashedToken);

        if (resetInfo == null) {
            throw new ResetPasswordInvalidTokenException();
        }

        final String userId = resetInfo.userId;

        try {
            return epStorage.startTransaction(con -> {

                PasswordResetTokenInfo[] allTokens = epStorage.getAllPasswordResetTokenInfoForUser_Transaction(
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

                epStorage.deleteAllPasswordResetTokensForUser_Transaction(tenantIdentifier.toAppIdentifier(),
                        con,
                        userId);

                if (matchedToken.tokenExpiry < System.currentTimeMillis()) {
                    epStorage.commitTransaction(con);
                    throw new StorageTransactionLogicException(new ResetPasswordInvalidTokenException());
                }

                epStorage.updateUsersPassword_Transaction(tenantIdentifier.toAppIdentifier(), con, userId,
                        hashedPassword);

                epStorage.commitTransaction(con);
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
            return consumeResetPasswordToken(new TenantIdentifier(null, null, null), storage,
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
            TenantIdentifier tenantIdentifier, Storage storage, String token)
            throws ResetPasswordInvalidTokenException, NoSuchAlgorithmException, StorageQueryException,
            StorageTransactionLogicException, TenantOrAppNotFoundException {
        String hashedToken = Utils.hashSHA256(token);

        EmailPasswordSQLStorage epStorage = StorageUtils.getEmailPasswordStorage(storage);

        PasswordResetTokenInfo resetInfo = epStorage.getPasswordResetTokenInfo(
                tenantIdentifier.toAppIdentifier(), hashedToken);

        if (resetInfo == null) {
            throw new ResetPasswordInvalidTokenException();
        }

        final String userId = resetInfo.userId;

        try {
            return epStorage.startTransaction(con -> {

                PasswordResetTokenInfo[] allTokens = epStorage.getAllPasswordResetTokenInfoForUser_Transaction(
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

                epStorage.deleteAllPasswordResetTokensForUser_Transaction(tenantIdentifier.toAppIdentifier(),
                        con,
                        userId);

                if (matchedToken.tokenExpiry < System.currentTimeMillis()) {
                    epStorage.commitTransaction(con);
                    throw new StorageTransactionLogicException(new ResetPasswordInvalidTokenException());
                }

                epStorage.commitTransaction(con);
                if (matchedToken.email == null) {
                    // this is possible if the token was generated before migration, and then consumed
                    // after migration
                    AuthRecipeUserInfo user = AuthRecipe.getUserById(tenantIdentifier.toAppIdentifier(), storage,
                            userId);
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
            updateUsersEmailOrPassword(new AppIdentifier(null, null), storage,
                    main, userId, email, password);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void updateUsersEmailOrPassword(AppIdentifier appIdentifier, Storage storage, Main main,
                                                  @Nonnull String userId, @Nullable String email,
                                                  @Nullable String password)
            throws StorageQueryException, StorageTransactionLogicException,
            UnknownUserIdException, DuplicateEmailException, TenantOrAppNotFoundException,
            EmailChangeNotAllowedException {
        EmailPasswordSQLStorage epStorage = StorageUtils.getEmailPasswordStorage(storage);
        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        try {
            epStorage.startTransaction(transaction -> {
                try {
                    AuthRecipeUserInfo user = authRecipeStorage.getPrimaryUserById_Transaction(appIdentifier,
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
                                                appIdentifier, transaction,
                                                email);

                                for (AuthRecipeUserInfo userWithSameEmail : existingUsersWithNewEmail) {
                                    if (!userWithSameEmail.tenantIds.contains(tenantId)) {
                                        continue;
                                    }
                                    if (userWithSameEmail.isPrimaryUser && !userWithSameEmail.getSupertokensUserId()
                                            .equals(user.getSupertokensUserId())) {
                                        throw new StorageTransactionLogicException(
                                                new EmailChangeNotAllowedException());
                                    }
                                }
                            }
                        }

                        try {
                            epStorage.updateUsersEmail_Transaction(appIdentifier, transaction,
                                    userId, email);
                        } catch (DuplicateEmailException e) {
                            throw new StorageTransactionLogicException(e);
                        }
                    }

                    if (password != null) {
                        String hashedPassword = PasswordHashing.getInstance(main)
                                .createHashWithSalt(appIdentifier, password);
                        epStorage.updateUsersPassword_Transaction(appIdentifier, transaction, userId,
                                hashedPassword);
                    }

                    epStorage.commitTransaction(transaction);
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
            return getUserUsingId(new AppIdentifier(null, null), storage, userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Deprecated
    public static AuthRecipeUserInfo getUserUsingId(AppIdentifier appIdentifier, Storage storage, String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        AuthRecipeUserInfo result = StorageUtils.getAuthRecipeStorage(storage)
                .getPrimaryUserById(appIdentifier, userId);
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
    public static AuthRecipeUserInfo getUserUsingEmail(TenantIdentifier tenantIdentifier, Storage storage,
                                                       String email)
            throws StorageQueryException, TenantOrAppNotFoundException {
        AuthRecipeUserInfo[] users = StorageUtils.getEmailPasswordStorage(storage).listPrimaryUsersByEmail(
                tenantIdentifier, email);
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
