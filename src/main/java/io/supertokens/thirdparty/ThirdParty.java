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
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.thirdparty.UserInfo;
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
    public static SignInUpResponse signInUp2_7(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main,
                                               String thirdPartyId, String thirdPartyUserId, String email,
                                               boolean isEmailVerified)
            throws StorageQueryException, TenantOrAppNotFoundException {
        SignInUpResponse response = null;
        try {
            response = signInUpHelper(tenantIdentifierWithStorage, main, thirdPartyId, thirdPartyUserId,
                    email);
        } catch (EmailChangeNotAllowedException e) {
            throw new RuntimeException(e);
        }

        if (isEmailVerified) {
            try {
                SignInUpResponse finalResponse = response;
                tenantIdentifierWithStorage.getEmailVerificationStorage().startTransaction(con -> {
                    try {
                        // this assert is there cause this function should only be used for older CDIs in which
                        // account linking was not available. So loginMethod length will always be 1.
                        assert (finalResponse.user.loginMethods.length == 1);
                        tenantIdentifierWithStorage.getEmailVerificationStorage()
                                .updateIsEmailVerified_Transaction(tenantIdentifierWithStorage.toAppIdentifier(), con,
                                        finalResponse.user.id, finalResponse.user.loginMethods[0].email, true);
                        tenantIdentifierWithStorage.getEmailVerificationStorage()
                                .commitTransaction(con);
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
                    new TenantIdentifierWithStorage(null, null, null, storage), main,
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
                    new TenantIdentifierWithStorage(null, null, null, storage), main,
                    thirdPartyId, thirdPartyUserId, email);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static SignInUpResponse signInUp(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main,
                                            String thirdPartyId,
                                            String thirdPartyUserId, String email)
            throws StorageQueryException, TenantOrAppNotFoundException, BadPermissionException,
            EmailChangeNotAllowedException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifierWithStorage);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifierWithStorage);
        }
        if (!config.thirdPartyConfig.enabled) {
            throw new BadPermissionException("Third Party login not enabled for tenant");
        }

        return signInUpHelper(tenantIdentifierWithStorage, main, thirdPartyId, thirdPartyUserId, email);
    }

    private static SignInUpResponse signInUpHelper(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                   Main main, String thirdPartyId, String thirdPartyUserId,
                                                   String email) throws StorageQueryException,
            TenantOrAppNotFoundException, EmailChangeNotAllowedException {
        ThirdPartySQLStorage storage = tenantIdentifierWithStorage.getThirdPartyStorage();
        while (true) {
            // loop for sign in + sign up

            while (true) {
                // loop for sign up
                String userId = Utils.getUUID();
                long timeJoined = System.currentTimeMillis();

                try {
                    UserInfo createdUser = storage.signUp(tenantIdentifierWithStorage, userId, email,
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
            try {
                AppIdentifier appIdentifier = tenantIdentifierWithStorage.toAppIdentifier();
                AuthRecipeSQLStorage authRecipeStorage =
                        (AuthRecipeSQLStorage) tenantIdentifierWithStorage.getAuthRecipeStorage();

                storage.startTransaction(con -> {
                    AuthRecipeUserInfo userFromDb = authRecipeStorage.getPrimaryUsersByThirdPartyInfo_Transaction(
                            tenantIdentifierWithStorage,
                            con,
                            thirdPartyId, thirdPartyUserId);

                    if (userFromDb == null) {
                        storage.commitTransaction(con);
                        return null;
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


                    if (!email.equals(lM.email)) {
                        // before updating the email, we must check for if another primary user has the same
                        // email, and if they do, then we do not allow the update.
                        if (userFromDb.isPrimaryUser) {
                            for (String tenantId : userFromDb.tenantIds) {
                                // we do not bother with getting the tenantIdentifierWithStorage here because
                                // we get the tenants from the user itself, and the user can only be shared across
                                // tenants of the same storage - therefore, the storage will be the same.
                                TenantIdentifier tenantIdentifier = new TenantIdentifier(
                                        tenantIdentifierWithStorage.getConnectionUriDomain(),
                                        tenantIdentifierWithStorage.getAppId(),
                                        tenantId);

                                AuthRecipeUserInfo[] userBasedOnEmail =
                                        authRecipeStorage.listPrimaryUsersByEmail_Transaction(
                                                tenantIdentifier, con, email
                                        );
                                for (AuthRecipeUserInfo userWithSameEmail : userBasedOnEmail) {
                                    if (userWithSameEmail.isPrimaryUser &&
                                            !userWithSameEmail.id.equals(userFromDb.id)) {
                                        throw new StorageTransactionLogicException(
                                                new EmailChangeNotAllowedException());
                                    }
                                }
                            }
                        }
                        storage.updateUserEmail_Transaction(appIdentifier, con,
                                thirdPartyId, thirdPartyUserId, email);
                    }

                    storage.commitTransaction(con);
                    return null;
                });

                AuthRecipeUserInfo user = getUser(tenantIdentifierWithStorage, thirdPartyId, thirdPartyUserId);
                return new SignInUpResponse(false, user);
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof EmailChangeNotAllowedException) {
                    throw (EmailChangeNotAllowedException) e.actualException;
                }
                throw new StorageQueryException(e);
            }

            // retry..
        }
    }

    @Deprecated
    public static UserInfo getUser(AppIdentifierWithStorage appIdentifierWithStorage, String userId)
            throws StorageQueryException {
        AuthRecipeUserInfo result = appIdentifierWithStorage.getAuthRecipeStorage()
                .getPrimaryUserById(appIdentifierWithStorage, userId);
        if (result == null) {
            return null;
        }
        for (LoginMethod lM : result.loginMethods) {
            if (lM.recipeUserId.equals(userId)) {
                return new io.supertokens.pluginInterface.thirdparty.UserInfo(lM.recipeUserId, result.isPrimaryUser,
                        lM);
            }
        }
        return null;
    }

    @Deprecated
    @TestOnly
    public static UserInfo getUser(Main main, String userId) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getUser(new AppIdentifierWithStorage(null, null, storage), userId);
    }

    public static AuthRecipeUserInfo getUser(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                             String thirdPartyId,
                                             String thirdPartyUserId)
            throws StorageQueryException {
        return tenantIdentifierWithStorage.getThirdPartyStorage()
                .getPrimaryUserByThirdPartyInfo(tenantIdentifierWithStorage, thirdPartyId, thirdPartyUserId);
    }

    @TestOnly
    public static AuthRecipeUserInfo getUser(Main main, String thirdPartyId, String thirdPartyUserId)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getUser(
                new TenantIdentifierWithStorage(null, null, null, storage),
                thirdPartyId, thirdPartyUserId);
    }

    @Deprecated
    public static AuthRecipeUserInfo[] getUsersByEmail(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                       @Nonnull String email)
            throws StorageQueryException {
        AuthRecipeUserInfo[] users = tenantIdentifierWithStorage.getThirdPartyStorage()
                .listPrimaryUsersByEmail(tenantIdentifierWithStorage, email);
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

        for (ThirdPartyConfig.Provider provider : providers) {
            if (thirdPartyIds.contains(provider.thirdPartyId)) {
                throw new InvalidProviderConfigException("Duplicate ThirdPartyId was specified in the providers list.");
            }
            thirdPartyIds.add(provider.thirdPartyId);

            verifyThirdPartyProvider(provider);
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
                if (client.additionalConfig == null ||
                        !client.additionalConfig.has("boxyURL") ||
                        client.additionalConfig.get("boxyURL").isJsonNull() ||
                        client.additionalConfig.get("boxyURL").getAsString().isEmpty() ||
                        !client.additionalConfig.getAsJsonPrimitive("boxyURL").isString()) {

                    throw new InvalidProviderConfigException(errorMessage);
                }
            } catch (ClassCastException e) {
                throw new InvalidProviderConfigException(errorMessage);
            }
        }
    }
}
