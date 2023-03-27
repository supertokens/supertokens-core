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
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.thirdparty.UserInfo;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateThirdPartyUserException;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateUserIdException;
import io.supertokens.pluginInterface.thirdparty.sqlStorage.ThirdPartySQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.HashSet;

public class ThirdParty {

    public static class SignInUpResponse {
        public boolean createdNewUser;
        public UserInfo user;

        public SignInUpResponse(boolean createdNewUser, UserInfo user) {
            this.createdNewUser = createdNewUser;
            this.user = user;
        }
    }

    // we have two signInUp APIs since in version 2.7, we used to also verify the email
    // as seen below. But then, in newer versions, we stopped doing that cause of
    // https://github.com/supertokens/supertokens-core/issues/295, so we changed the API spec.
    @Deprecated
    public static SignInUpResponse signInUp2_7(TenantIdentifier tenantIdentifier, Main main,
                                               String thirdPartyId, String thirdPartyUserId, String email,
                                               boolean isEmailVerified)
            throws StorageQueryException, TenantOrAppNotFoundException {
        SignInUpResponse response = signInUpHelper(tenantIdentifier, main, thirdPartyId, thirdPartyUserId,
                email);

        if (isEmailVerified) {
            try {
                ((EmailVerificationSQLStorage) StorageLayer.getStorage(tenantIdentifier, main)).startTransaction(con -> {
                    try {
                        ((EmailVerificationSQLStorage) StorageLayer.getStorage(tenantIdentifier, main))
                                .updateIsEmailVerified_Transaction(tenantIdentifier.toAppIdentifier(), con,
                                        response.user.id, response.user.email, true);
                        ((EmailVerificationSQLStorage) StorageLayer.getStorage(tenantIdentifier, main))
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

    @Deprecated
    @TestOnly
    public static SignInUpResponse signInUp2_7(Main main,
                                               String thirdPartyId, String thirdPartyUserId, String email,
                                               boolean isEmailVerified) throws StorageQueryException {
        try {
            return signInUp2_7(new TenantIdentifier(null, null, null), main, thirdPartyId, thirdPartyUserId, email,
                    isEmailVerified);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static SignInUpResponse signInUp(Main main, String thirdPartyId, String thirdPartyUserId, String email)
            throws StorageQueryException {
        try {
            return signInUp(new TenantIdentifier(null, null, null), main, thirdPartyId, thirdPartyUserId, email);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static SignInUpResponse signInUp(TenantIdentifier tenantIdentifier, Main main, String thirdPartyId,
                                            String thirdPartyUserId, String email)
            throws StorageQueryException, TenantOrAppNotFoundException, BadPermissionException {

        TenantConfig config = Multitenancy.getTenantInfo(main, tenantIdentifier);
        if (config == null) {
            throw new TenantOrAppNotFoundException(tenantIdentifier);
        }
        if (!config.thirdPartyConfig.enabled) {
            throw new BadPermissionException("Third Party login not enabled for tenant");
        }

        return signInUpHelper(tenantIdentifier, main, thirdPartyId, thirdPartyUserId, email);
    }

    private static SignInUpResponse signInUpHelper(TenantIdentifier tenantIdentifier, Main main,
                                                   String thirdPartyId, String thirdPartyUserId,
                                                   String email) throws StorageQueryException,
            TenantOrAppNotFoundException {
        ThirdPartySQLStorage storage = StorageLayer.getThirdPartyStorage(tenantIdentifier, main);
        while (true) {
            // loop for sign in + sign up

            while (true) {
                // loop for sign up
                String userId = Utils.getUUID();
                long timeJoined = System.currentTimeMillis();

                try {
                    UserInfo user = new UserInfo(userId, email, new UserInfo.ThirdParty(thirdPartyId, thirdPartyUserId),
                            timeJoined);

                    storage.signUp(tenantIdentifier, user);

                    return new SignInUpResponse(true, user);
                } catch (DuplicateUserIdException e) {
                    // we try again..
                } catch (DuplicateThirdPartyUserException e) {
                    // we try to sign in
                    break;
                }
            }

            // we try to get user and update their email
            SignInUpResponse response = null;
            try {
                response = storage.startTransaction(con -> {
                    UserInfo user = storage.getUserInfoUsingId_Transaction(tenantIdentifier, con, thirdPartyId,
                            thirdPartyUserId);

                    if (user == null) {
                        // we retry everything..
                        storage.commitTransaction(con);
                        return null;
                    }

                    if (!email.equals(user.email)) {
                        storage.updateUserEmail_Transaction(tenantIdentifier, con, thirdPartyId, thirdPartyUserId,
                                email);

                        user = new UserInfo(user.id, email,
                                new UserInfo.ThirdParty(user.thirdParty.id, user.thirdParty.userId), user.timeJoined);
                    }

                    storage.commitTransaction(con);
                    return new SignInUpResponse(false, user);
                });
            } catch (StorageTransactionLogicException ignored) {
            }

            if (response != null) {
                return response;
            }

            // retry..
        }
    }

    public static UserInfo getUser(TenantIdentifier tenantIdentifier, Main main, String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getThirdPartyStorage(tenantIdentifier, main)
                .getThirdPartyUserInfoUsingId(tenantIdentifier.toAppIdentifier(), userId);
    }

    @TestOnly
    public static UserInfo getUser(Main main, String userId)
            throws StorageQueryException {
        try {
            return getUser(new TenantIdentifier(null, null, null), main, userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }


    public static UserInfo getUser(TenantIdentifier tenantIdentifier, Main main, String thirdPartyId,
                                   String thirdPartyUserId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getThirdPartyStorage(tenantIdentifier, main)
                .getThirdPartyUserInfoUsingId(tenantIdentifier, thirdPartyId, thirdPartyUserId);
    }

    @TestOnly
    public static UserInfo getUser(Main main, String thirdPartyId,
                                   String thirdPartyUserId)
            throws StorageQueryException {
        try {
            return getUser(new TenantIdentifier(null, null, null), main, thirdPartyId, thirdPartyUserId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static UserInfo[] getUsersByEmail(TenantIdentifier tenantIdentifier, Main main,
                                             @Nonnull String email)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getThirdPartyStorage(tenantIdentifier, main)
                .getThirdPartyUsersByEmail(tenantIdentifier, email);
    }

    public static void verifyThirdPartyProvidersArray(ThirdPartyConfig.Provider[] providers)
            throws InvalidProviderConfigException {

        HashSet<String> thirdPartyIds = new HashSet<>();

        for (ThirdPartyConfig.Provider provider: providers) {
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

        if (thirdPartyId.startsWith("active-directory")) {
            String errorMessage = "a non empty string value must be specified for directoryId in the additionalConfig for Active Directory provider";
            try {
                if (client.additionalConfig == null || !client.additionalConfig.has("directoryId") ||
                        client.additionalConfig.get("directoryId").isJsonNull() ||
                        client.additionalConfig.get("directoryId").getAsString().isEmpty() ||
                        !client.additionalConfig.getAsJsonPrimitive("directoryId").isString()
                ) {
                    throw new InvalidProviderConfigException(errorMessage);
                }
            } catch (ClassCastException e) {
                throw new InvalidProviderConfigException(errorMessage);
            }
        } else if (thirdPartyId.startsWith("apple")) {
            String errorMessage = "a non empty string value must be specified for keyId, teamId and privateKey in the additionalConfig for Apple provider";

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
        } else if (thirdPartyId.startsWith("discord")) {
            // Nothing here
        } else if (thirdPartyId.startsWith("facebook")) {
            // Nothing here
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
        } else if (thirdPartyId.startsWith("google")) {
            // Nothing here
        } else if (thirdPartyId.startsWith("linkedin")) {
            // Nothing here
        } else if (thirdPartyId.startsWith("okta")) {
            String errorMessage = "a non empty string value must be specified for oktaDomain in the additionalConfig for Okta provider";
            try {
                if (client.additionalConfig == null || !client.additionalConfig.has("oktaDomain") ||
                        client.additionalConfig.get("oktaDomain").isJsonNull() ||
                        client.additionalConfig.get("oktaDomain").getAsString().isEmpty() ||
                        !client.additionalConfig.getAsJsonPrimitive("oktaDomain").isString()) {

                    throw new InvalidProviderConfigException(errorMessage);
                }
            } catch (ClassCastException e) {
                throw new InvalidProviderConfigException(errorMessage);
            }
        } else if (thirdPartyId.startsWith("boxy-saml")) {
            String errorMessage = "a non empty string value must be specified for boxyURL in the additionalConfig for Boxy SAML provider";

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
