/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webauthn;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.exception.DataConversionException;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.verifier.exception.VerificationException;
import io.supertokens.Main;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.webauthn.AccountRecoveryTokenInfo;
import io.supertokens.pluginInterface.webauthn.WebAuthNOptions;
import io.supertokens.pluginInterface.webauthn.WebAuthNStorage;
import io.supertokens.pluginInterface.webauthn.WebAuthNStoredCredential;
import io.supertokens.pluginInterface.webauthn.exceptions.*;
import io.supertokens.pluginInterface.webauthn.slqStorage.WebAuthNSQLStorage;
import io.supertokens.utils.Utils;
import io.supertokens.webauthn.data.WebAuthNSignInUpResult;
import io.supertokens.webauthn.data.WebauthNCredentialRecord;
import io.supertokens.webauthn.data.WebauthNCredentialResponse;
import io.supertokens.webauthn.exception.*;
import io.supertokens.webauthn.utils.WebauthMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

public class WebAuthN {

    public static JsonObject generateOptions(TenantIdentifier tenantIdentifier, Storage storage, String email, String displayName, String relyingPartyName, String relyingPartyId,
                                             String origin, Long timeout, String attestation, String residentKey,
                                             String userVerification, JsonArray supportedAlgorithmIds, boolean userPresenceRequired)
            throws StorageQueryException {

        PublicKeyCredentialRpEntity relyingPartyEntity = new PublicKeyCredentialRpEntity(relyingPartyId,
                relyingPartyName);

        String id = null;
        String optionsId = Utils.getUUID();

        AuthRecipeStorage authStorage = (AuthRecipeStorage) storage;
        AuthRecipeUserInfo[] usersWithEmail = authStorage.listPrimaryUsersByEmail(tenantIdentifier, email);
        if (usersWithEmail.length > 0) {
            id = usersWithEmail[0].getSupertokensUserId();
        } else {
            id = optionsId;
        }

        PublicKeyCredentialUserEntity userEntity = new PublicKeyCredentialUserEntity(
                id.getBytes(StandardCharsets.UTF_8), email, displayName);

        Challenge challenge = getChallenge();

        List<PublicKeyCredentialParameters> credentialParameters = new ArrayList<>();
        for (int i = 0; i < supportedAlgorithmIds.size(); i++) {
            JsonElement supportedAlgoId = supportedAlgorithmIds.get(i);
            COSEAlgorithmIdentifier algorithmIdentifier = COSEAlgorithmIdentifier.create(
                    supportedAlgoId.getAsLong());
            PublicKeyCredentialParameters param = new PublicKeyCredentialParameters(
                    PublicKeyCredentialType.PUBLIC_KEY, algorithmIdentifier);
            credentialParameters.add(param);
        }
        ;

        AuthenticatorSelectionCriteria authenticatorSelectionCriteria = new AuthenticatorSelectionCriteria(null,
                residentKey.equalsIgnoreCase("required"),
                ResidentKeyRequirement.create(residentKey), UserVerificationRequirement.create(userVerification));

        AttestationConveyancePreference attestationConveyancePreference = AttestationConveyancePreference.create(
                attestation);

        PublicKeyCredentialCreationOptions options = new PublicKeyCredentialCreationOptions(relyingPartyEntity,
                userEntity, challenge, credentialParameters, timeout, null, authenticatorSelectionCriteria,
                null, attestationConveyancePreference, null);


        WebAuthNOptions savedOptions = saveGeneratedOptions(tenantIdentifier, storage, options.getChallenge(),
                options.getTimeout(),
                options.getRp().getId(), options.getRp().getName(), origin, email, optionsId, userVerification,
                userPresenceRequired);

        return WebauthMapper.createResponseFromOptions(options, optionsId, savedOptions.createdAt,
                savedOptions.expiresAt, savedOptions.userEmail);
    }

    public static JsonObject generateSignInOptions(TenantIdentifier tenantIdentifier, Storage storage,
                                                   String relyingPartyId, String relyingPartyName, String origin, Long timeout,
                                                   String userVerification, boolean userPresenceRequired)
            throws StorageQueryException {

        Challenge challenge = getChallenge();

        String optionsId = Utils.getUUID();

        WebAuthNOptions savedOptions = saveGeneratedOptions(tenantIdentifier, storage, challenge, timeout, relyingPartyId, relyingPartyName, origin,
                null, optionsId, userVerification, userPresenceRequired);

        return WebauthMapper.mapOptionsResponse(relyingPartyId, timeout, userVerification, optionsId, challenge, savedOptions.createdAt, userPresenceRequired);
    }



    @NotNull
    private static Challenge getChallenge() {
        Challenge challenge = new Challenge() {
            private final byte[] challenge = new byte[32];

            { //initializer block
                new Random().nextBytes(challenge);
            }

            @NotNull
            @Override
            public byte[] getValue() {
                return challenge;
            }
        };
        return challenge;
    }

    public static WebauthNCredentialResponse registerCredentials(Storage storage, TenantIdentifier tenantIdentifier, String recipeUserId,
                                                                 String optionsId, String credentialId, String registrationResponseJson)
            throws InvalidWebauthNOptionsException, StorageQueryException, WebauthNVerificationFailedException,
            WebauthNInvalidFormatException, WebauthNOptionsNotExistsException {

        WebAuthNStorage webAuthNStorage = (WebAuthNStorage) storage;
        WebAuthNOptions generatedOptions = webAuthNStorage.loadOptionsById(tenantIdentifier, optionsId);

        if(generatedOptions == null) {
            throw new WebauthNOptionsNotExistsException();
        }

        RegistrationData verifiedRegistrationData = getRegistrationData(registrationResponseJson,
                generatedOptions);

        WebAuthNStoredCredential credentialToSave = WebauthMapper.mapRegistrationDataToStoredCredential(
                verifiedRegistrationData,
                recipeUserId, credentialId, generatedOptions.userEmail, generatedOptions.relyingPartyId,
                tenantIdentifier);

        WebAuthNStoredCredential savedCredential = webAuthNStorage.saveCredentials(tenantIdentifier, credentialToSave);

        return WebauthMapper.mapStoredCredentialToResponse(savedCredential, generatedOptions.userEmail,
                generatedOptions.relyingPartyName);
    }

    @NotNull
    private static RegistrationData getRegistrationData(String registrationResponseJson,
                                                        WebAuthNOptions generatedOptions)
            throws InvalidWebauthNOptionsException, WebauthNVerificationFailedException,
            WebauthNInvalidFormatException {
        long now = System.currentTimeMillis();
        if(generatedOptions.expiresAt < now) {
            throw new InvalidWebauthNOptionsException("Options expired");
        }
        if(!generatedOptions.origin.contains(generatedOptions.relyingPartyId)) {
            throw new InvalidWebauthNOptionsException("Origin does not match relying party id");
        }

        WebAuthnManager nonStrictWebAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
        try {
            RegistrationData registrationData = nonStrictWebAuthnManager.parseRegistrationResponseJSON(
                    registrationResponseJson);
            RegistrationParameters registrationParameters = getRegistrationParameters(generatedOptions);
            return nonStrictWebAuthnManager.verify(registrationData,
                    registrationParameters);
        } catch ( VerificationException e) {
            throw new WebauthNVerificationFailedException(e.getMessage());
        } catch (DataConversionException e) {
            throw new WebauthNInvalidFormatException(e.getMessage());
        }
    }

    private static AuthenticationData getAuthenticationData(JsonObject authenticationResponse,
                                                          WebAuthNOptions generatedOptions,
                                                          WebAuthNStoredCredential storedCredential)
            throws InvalidWebauthNOptionsException, WebauthNVerificationFailedException,
            WebauthNInvalidFormatException {
        long now = System.currentTimeMillis();
        if(generatedOptions.expiresAt < now) {
            throw new InvalidWebauthNOptionsException("Options expired");
        }
        if(!generatedOptions.origin.contains(generatedOptions.relyingPartyId)) {
            throw new InvalidWebauthNOptionsException("Origin does not match relying party id");
        }

        WebAuthnManager nonStrictWebAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
        try {
            AuthenticationData authenticationData = nonStrictWebAuthnManager.parseAuthenticationResponseJSON(new Gson().toJson(authenticationResponse));

            List<byte[]> allowCredentials = null;
            boolean userVerificationRequired = generatedOptions.userVerification.equalsIgnoreCase("required");
            boolean userPresenceRequired = generatedOptions.userPresenceRequired;

            WebauthNCredentialRecord credentialRecord = WebauthMapper.mapStoredCredentialToCredentialRecord(storedCredential);

            AuthenticationParameters authenticationParameters = new AuthenticationParameters(
                    new ServerProperty(new Origin(generatedOptions.origin), generatedOptions.relyingPartyId,
                            new Challenge() {
                                @NotNull
                                @Override
                                public byte[] getValue() {
                                    return Base64.getUrlDecoder().decode(generatedOptions.challenge);
                                }
                            }),
                    credentialRecord,
                    null,
                    userVerificationRequired,
                    userPresenceRequired);

            return nonStrictWebAuthnManager.verify(authenticationData, authenticationParameters);
        } catch (VerificationException e) {
            throw new WebauthNVerificationFailedException(e.getMessage());
        } catch (DataConversionException e) {
            throw new WebauthNInvalidFormatException(e.getMessage());
        }
    }

    public static WebAuthNSignInUpResult signUp(Storage storage, TenantIdentifier tenantIdentifier,
                                                String optionsId, String credentialId, String registrationResponseJson)
            throws InvalidWebauthNOptionsException, DuplicateUserEmailException, WebauthNVerificationFailedException,
            StorageQueryException, WebauthNOptionsNotExistsException, WebauthNInvalidFormatException{
        // create a new user in the auth recipe storage
        // create new credentials
        // all within a transaction
        try {
            WebAuthNSQLStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
            return webAuthNStorage.startTransaction(con -> {

                while (true) {
                    try {
                        String recipeUserId = Utils.getUUID();

                        WebAuthNOptions generatedOptions = webAuthNStorage.loadOptionsById_Transaction(tenantIdentifier,
                                con,
                                optionsId);

                        if(generatedOptions == null) {
                            throw new WebauthNOptionsNotExistsException();
                        }

                        RegistrationData verifiedRegistrationData = getRegistrationData(registrationResponseJson,
                                generatedOptions);
                        WebAuthNStoredCredential credentialToSave = WebauthMapper.mapRegistrationDataToStoredCredential(
                                verifiedRegistrationData,
                                recipeUserId, credentialId, generatedOptions.userEmail, generatedOptions.relyingPartyId,
                                tenantIdentifier);
                        AuthRecipeUserInfo userInfo = webAuthNStorage.signUpWithCredentialsRegister_Transaction(
                                tenantIdentifier, con, recipeUserId, generatedOptions.userEmail,
                                generatedOptions.relyingPartyId, credentialToSave);
                        userInfo.setExternalUserId(null);

                        return new WebAuthNSignInUpResult(credentialToSave, userInfo, generatedOptions);
                    } catch (DuplicateUserIdException duplicateUserIdException) {
                        //ignore and retry
                    } catch (InvalidWebauthNOptionsException | TenantOrAppNotFoundException |
                             DuplicateUserEmailException | WebauthNVerificationFailedException |
                             WebauthNInvalidFormatException | WebauthNOptionsNotExistsException e) {
                        throw new StorageQueryException(e);
                    }
                }
            });

        } catch (StorageQueryException exception) {
            if (exception.getCause() instanceof InvalidWebauthNOptionsException) {
                throw (InvalidWebauthNOptionsException) exception.getCause();
            } else if (exception.getCause() instanceof DuplicateUserEmailException) {
                throw (DuplicateUserEmailException) exception.getCause();
            } else if (exception.getCause() instanceof WebauthNVerificationFailedException) {
                throw (WebauthNVerificationFailedException) exception.getCause();
            } else if (exception.getCause() instanceof WebauthNOptionsNotExistsException) {
                throw (WebauthNOptionsNotExistsException) exception.getCause();
            } else if (exception.getCause() instanceof WebauthNInvalidFormatException) {
                throw (WebauthNInvalidFormatException) exception.getCause();
            } else {
                throw exception;
            }
        } catch (StorageTransactionLogicException e) {
            throw new StorageQueryException(e);
        }
    }

    @TestOnly
    public static AuthRecipeUserInfo saveUser(Storage storage, TenantIdentifier tenantIdentifier, String email, String userId, String rpId)
            throws StorageQueryException, TenantOrAppNotFoundException, DuplicateUserEmailException,
            DuplicateUserIdException {
        WebAuthNSQLStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
        try {
            return webAuthNStorage.startTransaction(con -> {
                try {
                    return webAuthNStorage.signUp_Transaction(tenantIdentifier, con, userId, email, rpId);
                } catch (TenantOrAppNotFoundException | DuplicateUserEmailException | DuplicateUserIdException e) {
                    throw new StorageTransactionLogicException(e);
                }
            });
        } catch (StorageTransactionLogicException  e) {
            if (e.actualException instanceof TenantOrAppNotFoundException) {
                throw (TenantOrAppNotFoundException) e.actualException;
            } else if (e.actualException instanceof DuplicateUserEmailException) {
                throw (DuplicateUserEmailException) e.actualException;
            } else if (e.actualException instanceof DuplicateUserIdException) {
                throw (DuplicateUserIdException) e.actualException;
            } else {
                throw new StorageQueryException(e.actualException);
            }
        }
    }

    public static WebAuthNSignInUpResult signIn(Storage storage, TenantIdentifier tenantIdentifier,
                                              String webauthnGeneratedOptionsId, JsonObject credentialsData,
                                              String credentialId)
            throws InvalidWebauthNOptionsException, WebauthNVerificationFailedException,
            WebauthNInvalidFormatException, StorageQueryException, WebauthNOptionsNotExistsException {
        try {
            WebAuthNSQLStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
            return webAuthNStorage.startTransaction(con -> {

                WebAuthNOptions generatedOptions = webAuthNStorage.loadOptionsById_Transaction(tenantIdentifier,
                        con,
                        webauthnGeneratedOptionsId);

                if(generatedOptions == null) {
                    throw new StorageTransactionLogicException(new WebauthNOptionsNotExistsException());
                }

                AuthRecipeUserInfo userInfo = webAuthNStorage.getUserInfoByCredentialId_Transaction(tenantIdentifier,
                        con, credentialId);
                WebAuthNStoredCredential credential = webAuthNStorage.loadCredentialById_Transaction(tenantIdentifier,
                        con, credentialId);

                try {
                    getAuthenticationData(credentialsData, generatedOptions, credential);
                } catch (InvalidWebauthNOptionsException | WebauthNVerificationFailedException |
                         WebauthNInvalidFormatException  e) {
                    throw new StorageTransactionLogicException(e);
                }
                webAuthNStorage.updateCounter_Transaction(tenantIdentifier, con, credentialId, credential.counter + 1);
                return new WebAuthNSignInUpResult(credential, userInfo, generatedOptions);
            });
        } catch (StorageTransactionLogicException e) {
            if (e.getCause() instanceof InvalidWebauthNOptionsException) {
                throw (InvalidWebauthNOptionsException) e.getCause();
            } else if (e.getCause() instanceof WebauthNVerificationFailedException) {
                throw (WebauthNVerificationFailedException) e.getCause();
            } else if (e.getCause() instanceof WebauthNInvalidFormatException) {
                throw (WebauthNInvalidFormatException) e.getCause();
            } else if (e.getCause() instanceof WebauthNOptionsNotExistsException) {
                throw (WebauthNOptionsNotExistsException) e.getCause();
            } else {
                throw new StorageQueryException(e);
            }
        }
    }

    public static WebAuthNOptions loadGeneratedOptionsById(Storage storage, TenantIdentifier tenantIdentifier,
                                           String webauthnGeneratedOptionsId) throws StorageQueryException {
        WebAuthNSQLStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
        return webAuthNStorage.loadOptionsById(tenantIdentifier, webauthnGeneratedOptionsId);
    }

    public static WebAuthNStoredCredential loadCredentialByIdForUser(Storage storage, TenantIdentifier tenantIdentifier,
                                                              String credentialId, String recipeUserId) throws StorageQueryException {
        WebAuthNStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
        return webAuthNStorage.loadCredentialByIdForUser(tenantIdentifier, credentialId, recipeUserId);
    }

    @NotNull
    private static RegistrationParameters getRegistrationParameters(WebAuthNOptions generatedOptions) {
        List<PublicKeyCredentialParameters> pubKeyCredParams = null;
        boolean userVerificationRequired = generatedOptions.userVerification.equalsIgnoreCase("required");
        boolean userPresenceRequired = generatedOptions.userPresenceRequired;

        RegistrationParameters registrationParameters = new RegistrationParameters(
                new ServerProperty(new Origin(generatedOptions.origin), generatedOptions.relyingPartyId,
                        new Challenge() {
                            @NotNull
                            @Override
                            public byte[] getValue() {
                                return Base64.getUrlDecoder().decode(generatedOptions.challenge);
                            }
                        }),
                pubKeyCredParams,
                userVerificationRequired,
                userPresenceRequired
        );
        return registrationParameters;
    }

    private static WebAuthNOptions saveGeneratedOptions(TenantIdentifier tenantIdentifier, Storage storage, Challenge challenge,
            Long timeout, String relyingPartyId, String relyingPartyName, String origin, String userEmail, String id,
                                                        String userVerification, boolean userPresenceRequired)
            throws StorageQueryException {
        WebAuthNStorage webAuthNStorage = (WebAuthNStorage) storage;
        WebAuthNOptions savableOptions = new WebAuthNOptions();
        savableOptions.generatedOptionsId = id;
        savableOptions.challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge.getValue());
        savableOptions.origin = origin;
        savableOptions.timeout = timeout;
        savableOptions.createdAt = System.currentTimeMillis();
        savableOptions.expiresAt = savableOptions.createdAt + savableOptions.timeout;
        savableOptions.relyingPartyId = relyingPartyId;
        savableOptions.relyingPartyName = relyingPartyName;
        savableOptions.userEmail = userEmail;
        savableOptions.userVerification = userVerification;
        savableOptions.userPresenceRequired = userPresenceRequired;
        return webAuthNStorage.saveGeneratedOptions(tenantIdentifier, savableOptions);
    }


    public static String generateRecoverAccountToken(Main main, Storage storage, TenantIdentifier tenantIdentifier, String email)
            throws NoSuchAlgorithmException, InvalidKeySpecException, TenantOrAppNotFoundException,
            StorageQueryException {
        // find the recipe user with the email
        AuthRecipeUserInfo[] users = AuthRecipe.getUsersByAccountInfo(tenantIdentifier, storage, true, email, null,
                null, null, null);

        String userId = null;

        for (AuthRecipeUserInfo user : users) {
            for (LoginMethod lm : user.loginMethods) {
                if (lm.recipeId == RECIPE_ID.WEBAUTHN && lm.email.equals(email)) {
                    userId = lm.getSupertokensUserId();
                }
            }
        }

        if (userId == null) {
            throw new WebAuthNEmailNotFoundException();
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
                StorageUtils.getWebAuthNStorage(storage).addRecoverAccountToken(
                        tenantIdentifier, new AccountRecoveryTokenInfo(userId,
                                email, hashedToken, System.currentTimeMillis() +
                                getRecoverAccountTokenLifetime(tenantIdentifier, main)));
                return token;
            } catch (DuplicateRecoverAccountTokenException ignored) {
            }
        }
    }

    public static AccountRecoveryTokenInfo consumeRecoverAccountToken(Main main, TenantIdentifier tenantIdentifier, Storage storage, String token)
            throws StorageQueryException, NoSuchAlgorithmException {
        WebAuthNSQLStorage webauthnStorage = StorageUtils.getWebAuthNStorage(storage);

        String hashedToken = Utils.hashSHA256(token);

        try {
            AccountRecoveryTokenInfo tokenInfo = webauthnStorage.startTransaction(con -> {
                AccountRecoveryTokenInfo recoveryTokenInfo = webauthnStorage.getAccountRecoveryTokenInfoByToken_Transaction(tenantIdentifier, con, hashedToken);

                if (recoveryTokenInfo != null) {
                    if (recoveryTokenInfo.expiresAt < System.currentTimeMillis()) {
                        return null;
                    }

                    webauthnStorage.deleteAccountRecoveryTokenByEmail_Transaction(tenantIdentifier, con, recoveryTokenInfo.email);
                }

                return recoveryTokenInfo;
            });

            if (tokenInfo == null) {
                throw new InvalidTokenException();
            }

            return tokenInfo;
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof StorageQueryException) {
                throw (StorageQueryException) e.actualException;
            }
            throw new IllegalStateException(e.actualException);
        }
    }

    public static AuthRecipeUserInfo getUserForToken(Storage storage, TenantIdentifier tenantIdentifier, String token)
            throws
            InvalidTokenException, StorageQueryException, NoSuchAlgorithmException {
        WebAuthNSQLStorage webauthnStorage = StorageUtils.getWebAuthNStorage(storage);
        String hashedToken = Utils.hashSHA256(token);

        try {
            AccountRecoveryTokenInfo tokenInfo = webauthnStorage.startTransaction(con -> {
                AccountRecoveryTokenInfo recoveryTokenInfo = webauthnStorage.getAccountRecoveryTokenInfoByToken_Transaction(tenantIdentifier, con, hashedToken);
                if (recoveryTokenInfo != null && recoveryTokenInfo.expiresAt < System.currentTimeMillis()) {
                    return null;
                }
                return recoveryTokenInfo;
            });

            if (tokenInfo == null) {
                throw new InvalidTokenException();
            }

            return AuthRecipe.getUserById(tenantIdentifier.toAppIdentifier(), storage, tokenInfo.userId);
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof StorageQueryException) {
                throw (StorageQueryException) e.actualException;
            }
            throw new IllegalStateException(e.actualException);
        }
    }

    private static long getRecoverAccountTokenLifetime(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        return 300000; // TODO add config
    }

    public static void removeCredential(Storage storage, TenantIdentifier tenantIdentifier,
                                        String userId, String credentialId)
            throws StorageQueryException, WebauthNCredentialNotExistsException {
        WebAuthNStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
        webAuthNStorage.removeCredential(tenantIdentifier, userId, credentialId);
    }

    public static void removeOptions(Storage storage, TenantIdentifier tenantIdentifier,
                                    String optionsId)
            throws StorageQueryException, WebauthNOptionsNotExistsException {
        WebAuthNStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
        webAuthNStorage.removeOptions(tenantIdentifier, optionsId);
    }

    public static List<WebAuthNStoredCredential> listCredentialsForUser(Storage storage, TenantIdentifier tenantIdentifier,
                                                                          String userId) throws StorageQueryException {
        WebAuthNStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
        return webAuthNStorage.listCredentialsForUser(tenantIdentifier, userId);
    }

    public static void updateUserEmail(Storage storage, TenantIdentifier tenantIdentifier, String userId, String newEmail)
            throws StorageQueryException,
            UserIdNotFoundException, DuplicateUserEmailException {
        WebAuthNStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
        webAuthNStorage.updateUserEmail(tenantIdentifier, userId, newEmail);
    }
}
