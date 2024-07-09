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

package io.supertokens.thirdparty;

import io.supertokens.Main;
import io.supertokens.emailpassword.exceptions.EmailChangeNotAllowedException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateThirdPartyUserException;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateUserIdException;
import io.supertokens.pluginInterface.thirdparty.sqlStorage.ThirdPartySQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class ThirdParty {

    public static class SignInUpResponse {
        public boolean createdNewUser;
        public AuthRecipeUserInfo user;

        public SignInUpResponse(boolean createdNewUser, AuthRecipeUserInfo user) {
            this.createdNewUser = createdNewUser;
            this.user = user;
        }
    }

    // we have two signInUp APIs since in version 2.7, we used to also verify the email
    // as seen below. But then, in newer versions, we stopped doing that cause of
    // https://github.com/supertokens/supertokens-core/issues/295, so we changed the API spec.
    @Deprecated
    public static SignInUpResponse signInUp2_7(TenantIdentifier tenantIdentifier, Storage storage,
                                               String thirdPartyId, String thirdPartyUserId, String email,
                                               boolean isEmailVerified)
            throws StorageQueryException, TenantOrAppNotFoundException {
        SignInUpResponse response = null;
        try {
            response = signInUpHelper(tenantIdentifier, storage, thirdPartyId, thirdPartyUserId,
                    email);
        } catch (EmailChangeNotAllowedException e) {
            throw new RuntimeException(e);
        }

        if (isEmailVerified) {
            try {
                SignInUpResponse finalResponse = response;
                EmailVerificationSQLStorage evStorage = StorageUtils.getEmailVerificationStorage(storage);

                evStorage.startTransaction(con -> {
                    try {
                        // this assert is there cause this function should only be used for older CDIs in which
                        // account linking was not available. So loginMethod length will always be 1.
                        assert (finalResponse.user.loginMethods.length == 1);
                        evStorage.updateIsEmailVerified_Transaction(tenantIdentifier.toAppIdentifier(), con,
                                finalResponse.user.getSupertokensUserId(), finalResponse.user.loginMethods[0].email,
                                true);
                        evStorage.commitTransaction(con);
                        return null;
                    } catch (TenantOrAppNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof TenantOrAppNotFoundException) {
                    throw (TenantOrAppNotFoundException) e.actualException;
                }
                throw new StorageQueryException(e);
            }
        }

        return response;
    }

    @TestOnly
    public static SignInUpResponse signInUp2_7(Main main,
                                               String thirdPartyId, String thirdPartyUserId, String email,
                                               boolean isEmailVerified) throws StorageQueryException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return signInUp2_7(
                    new TenantIdentifier(null, null, null), storage,
                    thirdPartyId, thirdPartyUserId, email, isEmailVerified);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static SignInUpResponse signInUp(Main main, String thirdPartyId, String thirdPartyUserId, String email)
            throws StorageQueryException, EmailChangeNotAllowedException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return signInUp(
                    new TenantIdentifier(null, null, null), storage, main,
                    thirdPartyId, thirdPartyUserId, email, false);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static SignInUpResponse signInUp(Main main, String thirdPartyId, String thirdPartyUserId, String email,
                                            boolean isEmailVerified)
            throws StorageQueryException, EmailChangeNotAllowedException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return signInUp(
                    new TenantIdentifier(null, null, null), storage, main,
                    thirdPartyId, thirdPartyUserId, email, isEmailVerified);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static SignInUpResponse signInUp(TenantIdentifier tenantIdentifier, Storage storage, Main main,
                                            String thirdPartyId,
                                            String thirdPartyUserId, String email)
            throws StorageQueryException, TenantOrAppNotFoundException, BadPermissionException,
            EmailChangeNotAllowedException {
        return signInUp(tenantIdentifier, storage, main, thirdPartyId, thirdPartyUserId, email, false);
    }

    public static SignInUpResponse signInUp(TenantIdentifier tenantIdentifier, Storage storage, Main main,
                                            String thirdPartyId,
                                            String thirdPartyUserId, String email, boolean isEmailVerified)
            throws StorageQueryException, TenantOrAppNotFoundException, BadPermissionException,
            EmailChangeNotAllowedException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifier);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifier);
        }

        SignInUpResponse response = signInUpHelper(tenantIdentifier, storage, thirdPartyId, thirdPartyUserId,
                email);

        if (isEmailVerified) {
            for (LoginMethod lM : response.user.loginMethods) {
                if (lM.thirdParty != null && lM.thirdParty.id.equals(thirdPartyId) &&
                        lM.thirdParty.userId.equals(thirdPartyUserId)) {
                    try {
                        EmailVerificationSQLStorage evStorage = StorageUtils.getEmailVerificationStorage(storage);
                        evStorage.startTransaction(con -> {
                            try {
                                evStorage.updateIsEmailVerified_Transaction(tenantIdentifier.toAppIdentifier(), con,
                                        lM.getSupertokensUserId(), lM.email, true);
                                evStorage.commitTransaction(con);

                                return null;
                            } catch (TenantOrAppNotFoundException e) {
                                throw new StorageTransactionLogicException(e);
                            }
                        });
                        lM.setVerified();
                    } catch (StorageTransactionLogicException e) {
                        if (e.actualException instanceof TenantOrAppNotFoundException) {
                            throw (TenantOrAppNotFoundException) e.actualException;
                        }
                        throw new StorageQueryException(e);
                    }
                    break;
                }
            }
        }

        return response;
    }

    private static SignInUpResponse signInUpHelper(TenantIdentifier tenantIdentifier, Storage storage,
                                                   String thirdPartyId, String thirdPartyUserId,
                                                   String email) throws StorageQueryException,
            TenantOrAppNotFoundException, EmailChangeNotAllowedException {
        ThirdPartySQLStorage tpStorage = StorageUtils.getThirdPartyStorage(storage);
        while (true) {
            // loop for sign in + sign up

            while (true) {
                // loop for sign up
                String userId = Utils.getUUID();
                long timeJoined = System.currentTimeMillis();

                try {
                    AuthRecipeUserInfo createdUser = tpStorage.signUp(tenantIdentifier, userId, email,
                            new LoginMethod.ThirdParty(thirdPartyId, thirdPartyUserId), timeJoined);

                    return new SignInUpResponse(true, createdUser);
                } catch (DuplicateUserIdException e) {
                    // we try again..
                } catch (DuplicateThirdPartyUserException e) {
                    // we try to sign in
                    break;
                }
            }

            // we try to get user and update their email
            AppIdentifier appIdentifier = tenantIdentifier.toAppIdentifier();
            AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);

            { // Try without transaction, because in most cases we might not need to update the email
                AuthRecipeUserInfo userFromDb = null;

                AuthRecipeUserInfo[] usersFromDb = authRecipeStorage.listPrimaryUsersByThirdPartyInfo(
                        appIdentifier,
                        thirdPartyId, thirdPartyUserId);
                for (AuthRecipeUserInfo user : usersFromDb) {
                    if (user.tenantIds.contains(tenantIdentifier.getTenantId())) {
                        if (userFromDb != null) {
                            throw new IllegalStateException("Should never happen");
                        }
                        userFromDb = user;
                    }
                }
                if (userFromDb == null) {
                    continue; // try to create the user again
                }

                LoginMethod lM = null;
                for (LoginMethod loginMethod : userFromDb.loginMethods) {
                    if (loginMethod.thirdParty != null && loginMethod.thirdParty.id.equals(thirdPartyId) &&
                            loginMethod.thirdParty.userId.equals(thirdPartyUserId)) {
                        lM = loginMethod;
                        break;
                    }
                }

                if (lM == null) {
                    throw new IllegalStateException("Should never come here");
                }

                if (email.equals(lM.email)) {
                    return new SignInUpResponse(false, userFromDb);
                } else {
                    // Email needs updating, so repeat everything in a transaction
                    try {

                        tpStorage.startTransaction(con -> {
                            AuthRecipeUserInfo userFromDb1 = null;

                            AuthRecipeUserInfo[] usersFromDb1 =
                                    authRecipeStorage.listPrimaryUsersByThirdPartyInfo_Transaction(
                                            appIdentifier,
                                            con,
                                            thirdPartyId, thirdPartyUserId);
                            for (AuthRecipeUserInfo user : usersFromDb1) {
                                if (user.tenantIds.contains(tenantIdentifier.getTenantId())) {
                                    if (userFromDb1 != null) {
                                        throw new IllegalStateException("Should never happen");
                                    }
                                    userFromDb1 = user;
                                }
                            }

                            if (userFromDb1 == null) {
                                tpStorage.commitTransaction(con);
                                return null;
                            }

                            LoginMethod lM1 = null;
                            for (LoginMethod loginMethod : userFromDb1.loginMethods) {
                                if (loginMethod.thirdParty != null && loginMethod.thirdParty.id.equals(thirdPartyId) &&
                                        loginMethod.thirdParty.userId.equals(thirdPartyUserId)) {
                                    lM1 = loginMethod;
                                    break;
                                }
                            }

                            if (lM1 == null) {
                                throw new IllegalStateException("Should never come here");
                            }

                            if (!email.equals(lM1.email)) {
                                // before updating the email, we must check for if another primary user has the same
                                // email, and if they do, then we do not allow the update.
                                if (userFromDb1.isPrimaryUser) {
                                    for (String tenantId : userFromDb1.tenantIds) {
                                        AuthRecipeUserInfo[] userBasedOnEmail =
                                                authRecipeStorage.listPrimaryUsersByEmail_Transaction(
                                                        appIdentifier, con, email
                                                );
                                        for (AuthRecipeUserInfo userWithSameEmail : userBasedOnEmail) {
                                            if (!userWithSameEmail.tenantIds.contains(tenantId)) {
                                                continue;
                                            }
                                            if (userWithSameEmail.isPrimaryUser &&
                                                    !userWithSameEmail.getSupertokensUserId()
                                                            .equals(userFromDb1.getSupertokensUserId())) {
                                                throw new StorageTransactionLogicException(
                                                        new EmailChangeNotAllowedException());
                                            }
                                        }
                                    }
                                }
                                tpStorage.updateUserEmail_Transaction(appIdentifier, con,
                                        thirdPartyId, thirdPartyUserId, email);
                            }

                            tpStorage.commitTransaction(con);
                            return null;
                        });
                    } catch (StorageTransactionLogicException e) {
                        if (e.actualException instanceof EmailChangeNotAllowedException) {
                            throw (EmailChangeNotAllowedException) e.actualException;
                        }
                        throw new StorageQueryException(e);
                    }
                }
            }

            AuthRecipeUserInfo user = getUser(tenantIdentifier, storage, thirdPartyId, thirdPartyUserId);
            return new SignInUpResponse(false, user);
        }
    }

    @Deprecated
    public static AuthRecipeUserInfo getUser(AppIdentifier appIdentifier, Storage storage, String userId)
            throws StorageQueryException {
        AuthRecipeUserInfo result = StorageUtils.getAuthRecipeStorage(storage)
                .getPrimaryUserById(appIdentifier, userId);
        if (result == null) {
            return null;
        }
        for (LoginMethod lM : result.loginMethods) {
            if (lM.getSupertokensUserId().equals(userId) && lM.recipeId == RECIPE_ID.THIRD_PARTY) {
                return AuthRecipeUserInfo.create(lM.getSupertokensUserId(), result.isPrimaryUser,
                        lM);
            }
        }
        return null;
    }

    @Deprecated
    @TestOnly
    public static AuthRecipeUserInfo getUser(Main main, String userId) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getUser(new AppIdentifier(null, null), storage, userId);
    }

    public static AuthRecipeUserInfo getUser(TenantIdentifier tenantIdentifier, Storage storage,
                                             String thirdPartyId,
                                             String thirdPartyUserId)
            throws StorageQueryException {
        return StorageUtils.getThirdPartyStorage(storage)
                .getPrimaryUserByThirdPartyInfo(tenantIdentifier, thirdPartyId, thirdPartyUserId);
    }

    @TestOnly
    public static AuthRecipeUserInfo getUser(Main main, String thirdPartyId, String thirdPartyUserId)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getUser(
                new TenantIdentifier(null, null, null), storage,
                thirdPartyId, thirdPartyUserId);
    }

    @Deprecated
    public static AuthRecipeUserInfo[] getUsersByEmail(TenantIdentifier tenantIdentifier, Storage storage,
                                                       @Nonnull String email)
            throws StorageQueryException {
        AuthRecipeUserInfo[] users = StorageUtils.getThirdPartyStorage(storage)
                .listPrimaryUsersByEmail(tenantIdentifier, email);
        List<AuthRecipeUserInfo> result = new ArrayList<>();
        for (AuthRecipeUserInfo user : users) {
            for (LoginMethod lM : user.loginMethods) {
                if (lM.recipeId == RECIPE_ID.THIRD_PARTY && lM.email.equals(email)) {
                    result.add(user);
                }
            }
        }
        return result.toArray(new AuthRecipeUserInfo[0]);
    }

    public static void verifyThirdPartyProvidersArray(ThirdPartyConfig.Provider[] providers)
            throws InvalidProviderConfigException {

        HashSet<String> thirdPartyIds = new HashSet<>();

        if (providers != null) {
            for (ThirdPartyConfig.Provider provider : providers) {
                if (thirdPartyIds.contains(provider.thirdPartyId)) {
                    throw new InvalidProviderConfigException(
                            "Duplicate ThirdPartyId was specified in the providers list.");
                }
                thirdPartyIds.add(provider.thirdPartyId);

                verifyThirdPartyProvider(provider);
            }
        }
    }

    private static void verifyThirdPartyProvider(ThirdPartyConfig.Provider provider)
            throws InvalidProviderConfigException {

        if (provider.thirdPartyId == null || provider.thirdPartyId.isEmpty()) {
            throw new InvalidProviderConfigException("thirdPartyId cannot be null or empty");
        }

        HashSet<String> clientTypes = new HashSet<>();
        for (ThirdPartyConfig.ProviderClient client : provider.clients) {
            if (clientTypes.contains(client.clientType)) {
                throw new InvalidProviderConfigException("Duplicate clientType was specified in the clients list.");
            }
            clientTypes.add(client.clientType);

            verifyThirdPartyProviderClient(client, provider.thirdPartyId);
        }
    }

    private static void verifyThirdPartyProviderClient(ThirdPartyConfig.ProviderClient client, String thirdPartyId)
            throws InvalidProviderConfigException {

        if (client.clientId == null) {
            throw new InvalidProviderConfigException("clientId cannot be null");
        }

        if (client.scope != null && Arrays.asList(client.scope).contains(null)) {
            throw new InvalidProviderConfigException("scope array cannot contain a null");
        }

        if (thirdPartyId.startsWith("apple")) {
            String errorMessage = "a non empty string value must be specified for keyId, teamId and privateKey in the" +
                    " additionalConfig for Apple provider";

            try {
                if (
                        client.additionalConfig == null ||
                                !client.additionalConfig.has("keyId") ||
                                client.additionalConfig.get("keyId").isJsonNull() ||
                                client.additionalConfig.get("keyId").getAsString().isEmpty() ||
                                !client.additionalConfig.getAsJsonPrimitive("keyId").isString() ||

                                !client.additionalConfig.has("teamId") ||
                                client.additionalConfig.get("teamId").isJsonNull() ||
                                client.additionalConfig.get("teamId").getAsString().isEmpty() ||
                                !client.additionalConfig.getAsJsonPrimitive("teamId").isString() ||

                                !client.additionalConfig.has("privateKey") ||
                                client.additionalConfig.get("privateKey").isJsonNull() ||
                                client.additionalConfig.get("privateKey").getAsString().isEmpty() ||
                                !client.additionalConfig.getAsJsonPrimitive("privateKey").isString()
                ) {

                    throw new InvalidProviderConfigException(errorMessage);
                }
            } catch (ClassCastException e) {
                throw new InvalidProviderConfigException(errorMessage);
            }
        } else if (thirdPartyId.startsWith("google-workspaces")) {
            if (client.additionalConfig != null && client.additionalConfig.has("hd")) {
                String errorMessage = "hd in additionalConfig must be a non empty string value";
                try {
                    if (client.additionalConfig.get("hd").isJsonNull() ||
                            !client.additionalConfig.getAsJsonPrimitive("hd").isString() ||
                            client.additionalConfig.get("hd").getAsString().isEmpty()) {
                        throw new InvalidProviderConfigException(errorMessage);
                    }
                } catch (ClassCastException e) {
                    throw new InvalidProviderConfigException(errorMessage);
                }
            }
        } else if (thirdPartyId.startsWith("boxy-saml")) {
            String errorMessage = "a non empty string value must be specified for boxyURL in the additionalConfig for" +
                    " Boxy SAML provider";

            try {
                if (client.additionalConfig != null && client.additionalConfig.has("boxyURL")) {
                    if (client.additionalConfig.get("boxyURL").isJsonNull() ||
                            client.additionalConfig.get("boxyURL").getAsString().isEmpty() ||
                            !client.additionalConfig.getAsJsonPrimitive("boxyURL").isString()) {

                        throw new InvalidProviderConfigException(errorMessage);
                    }
                }
            } catch (ClassCastException e) {
                throw new InvalidProviderConfigException(errorMessage);
            }
        }
    }
}
