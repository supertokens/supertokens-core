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
import io.supertokens.config.Config;
import io.supertokens.emailverification.exception.EmailAlreadyVerifiedException;
import io.supertokens.emailverification.exception.EmailVerificationInvalidTokenException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.emailverification.EmailVerificationTokenInfo;
import io.supertokens.pluginInterface.emailverification.exception.DuplicateEmailVerificationTokenException;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import org.jetbrains.annotations.TestOnly;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

public class EmailVerification {

    @TestOnly
    public static long getEmailVerificationTokenLifetimeForTests(Main main) {
        try {
            return getEmailVerificationTokenLifetime(
                    new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long getEmailVerificationTokenLifetime(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        return Config.getConfig(tenantIdentifier, main).getEmailVerificationTokenLifetime();
    }

    @TestOnly
    public static String generateEmailVerificationToken(Main main, String userId, String email)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException,
            EmailAlreadyVerifiedException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return generateEmailVerificationToken(
                    new TenantIdentifier(null, null, null), storage,
                    main, userId, email);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String generateEmailVerificationToken(TenantIdentifier tenantIdentifier, Storage storage, Main main,
                                                        String userId, String email)
            throws InvalidKeySpecException, NoSuchAlgorithmException, StorageQueryException,
            EmailAlreadyVerifiedException, TenantOrAppNotFoundException {

        if (StorageUtils.getEmailVerificationStorage(storage)
                .isEmailVerified(tenantIdentifier.toAppIdentifier(), userId, email)) {
            throw new EmailAlreadyVerifiedException();
        }

        while (true) {

            // we first generate a email verification token
            byte[] random = new byte[48];

            new SecureRandom().nextBytes(random);

            String token = Utils.convertToBase64Url(Utils.bytesToString(random));
            String hashedToken = getHashedToken(token);

            try {
                StorageUtils.getEmailVerificationStorage(storage)
                        .addEmailVerificationToken(tenantIdentifier,
                                new EmailVerificationTokenInfo(userId, hashedToken,
                                        System.currentTimeMillis() +
                                                getEmailVerificationTokenLifetime(tenantIdentifier, main), email));
                return token;
            } catch (DuplicateEmailVerificationTokenException ignored) {
            }
        }
    }

    @TestOnly
    public static User verifyEmail(Main main, String token)
            throws StorageQueryException,
            EmailVerificationInvalidTokenException, NoSuchAlgorithmException, StorageTransactionLogicException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return verifyEmail(new TenantIdentifier(null, null, null), storage, token);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static User verifyEmail(TenantIdentifier tenantIdentifier, Storage storage, String token)
            throws StorageQueryException,
            EmailVerificationInvalidTokenException, NoSuchAlgorithmException, StorageTransactionLogicException,
            TenantOrAppNotFoundException {

        String hashedToken = getHashedToken(token);

        EmailVerificationSQLStorage evStorage = StorageUtils.getEmailVerificationStorage(storage);

        final EmailVerificationTokenInfo tokenInfo = evStorage.getEmailVerificationTokenInfo(
                tenantIdentifier, hashedToken);
        if (tokenInfo == null) {
            throw new EmailVerificationInvalidTokenException();
        }

        final String userId = tokenInfo.userId;

        try {
            return evStorage.startTransaction(con -> {

                EmailVerificationTokenInfo[] allTokens = evStorage
                        .getAllEmailVerificationTokenInfoForUser_Transaction(tenantIdentifier, con,
                                userId, tokenInfo.email);

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

                evStorage.deleteAllEmailVerificationTokensForUser_Transaction(tenantIdentifier, con,
                        userId, tokenInfo.email);

                if (matchedToken.tokenExpiry < System.currentTimeMillis()) {
                    evStorage.commitTransaction(con);
                    throw new StorageTransactionLogicException(new EmailVerificationInvalidTokenException());
                }

                try {
                    evStorage.updateIsEmailVerified_Transaction(tenantIdentifier.toAppIdentifier(), con, userId,
                            tokenInfo.email, true);
                } catch (TenantOrAppNotFoundException e) {
                    throw new StorageTransactionLogicException(e);
                }

                evStorage.commitTransaction(con);

                return new User(userId, tokenInfo.email);
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof EmailVerificationInvalidTokenException) {
                throw (EmailVerificationInvalidTokenException) e.actualException;
            } else if (e.actualException instanceof TenantOrAppNotFoundException) {
                throw (TenantOrAppNotFoundException) e.actualException;
            }
            throw e;
        }
    }

    @TestOnly
    public static boolean isEmailVerified(Main main, String userId,
                                          String email) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return isEmailVerified(new AppIdentifier(null, null), storage,
                userId, email);
    }

    public static boolean isEmailVerified(AppIdentifier appIdentifier, Storage storage, String userId,
                                          String email) throws StorageQueryException {
        return StorageUtils.getEmailVerificationStorage(storage)
                .isEmailVerified(appIdentifier, userId, email);
    }

    @TestOnly
    public static void revokeAllTokens(Main main, String userId,
                                       String email) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        revokeAllTokens(new TenantIdentifier(null, null, null), storage,
                userId, email);
    }

    public static void revokeAllTokens(TenantIdentifier tenantIdentifier, Storage storage, String userId,
                                       String email) throws StorageQueryException {
        StorageUtils.getEmailVerificationStorage(storage)
                .revokeAllTokens(tenantIdentifier, userId, email);
    }

    @TestOnly
    public static void unverifyEmail(Main main, String userId,
                                     String email) throws StorageQueryException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            unverifyEmail(new AppIdentifier(null, null), storage, userId, email);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void unverifyEmail(AppIdentifier appIdentifier, Storage storage, String userId,
                                     String email) throws StorageQueryException, TenantOrAppNotFoundException {
        StorageUtils.getEmailVerificationStorage(storage)
                .unverifyEmail(appIdentifier, userId, email);
    }

    @TestOnly
    public static String generateEmailVerificationTokenTheOldWay(Main main, String userId, String email)
            throws NoSuchAlgorithmException, InvalidKeySpecException, StorageQueryException,
            TenantOrAppNotFoundException {
        while(true) {
            // we first generate a email verification token
            byte[] random = new byte[64];
            byte[] salt = new byte[64];

            new SecureRandom().nextBytes(random);
            new SecureRandom().nextBytes(salt);

            int iterations = 1000;
            String token = io.supertokens.utils.Utils
                    .toHex(io.supertokens.utils.Utils.pbkdf2(io.supertokens.utils.Utils.bytesToString(random).toCharArray(), salt, iterations, 64 * 6));

            // we make it URL safe:
            token = io.supertokens.utils.Utils.convertToBase64(token);
            token = token.replace("=", "");
            token = token.replace("/", "");
            token = token.replace("+", "");

            String hashedToken = EmailVerification.getHashedToken(token);

            try {
                StorageUtils.getEmailVerificationStorage(StorageLayer.getStorage(main))
                        .addEmailVerificationToken(new TenantIdentifier(null, null, null),
                                new EmailVerificationTokenInfo(userId, hashedToken,
                                        System.currentTimeMillis() +
                                                EmailVerification.getEmailVerificationTokenLifetimeForTests(main), email));
                return token;
            } catch (DuplicateEmailVerificationTokenException ignored) {
            }
        }
    }

    private static String getHashedToken(String token) throws NoSuchAlgorithmException {
        return Utils.hashSHA256(token);
    }
}
