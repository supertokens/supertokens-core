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
import io.supertokens.config.Config;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.authRecipe.exceptions.EmailChangeNotAllowedException;
import io.supertokens.pluginInterface.authRecipe.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.auditlog.lifecycle.LifecycleAuditEvent;
import io.supertokens.pluginInterface.auditlog.ActivityLogSQLStorage;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.auditlog.AuditedResult;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.webauthn.AccountRecoveryTokenInfo;
import io.supertokens.pluginInterface.webauthn.WebAuthNOptions;
import io.supertokens.pluginInterface.webauthn.WebAuthNStorage;
import io.supertokens.pluginInterface.webauthn.WebAuthNStoredCredential;
import io.supertokens.pluginInterface.webauthn.exceptions.*;
import io.supertokens.pluginInterface.webauthn.slqStorage.WebAuthNSQLStorage;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.utils.Utils;
import io.supertokens.webauthn.data.WebAuthNSignInUpResult;
import io.supertokens.webauthn.data.WebauthNCredentialRecord;
import io.supertokens.webauthn.data.WebauthNCredentialResponse;
import io.supertokens.webauthn.exception.*;
import io.supertokens.webauthn.utils.WebauthMapper;
import io.supertokens.webauthn.validator.CredentialsValidator;
import io.supertokens.webauthn.validator.OptionsValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import io.supertokens.auditlog.UnauditedTransaction;

public class WebAuthN {

    public static JsonObject generateRegisterOptions(TenantIdentifier tenantIdentifier, Storage storage, String email, String displayName, String relyingPartyName, String relyingPartyId,
                                                     String origin, Long timeout, String attestation, String residentKey,
                                                     String userVerification, JsonArray supportedAlgorithmIds, Boolean userPresenceRequired)
            throws StorageQueryException, InvalidWebauthNOptionsException, TenantOrAppNotFoundException {

        OptionsValidator.validateOptions(origin, relyingPartyId, timeout, attestation, userVerification, residentKey,
                supportedAlgorithmIds, userPresenceRequired);

        PublicKeyCredentialRpEntity relyingPartyEntity = new PublicKeyCredentialRpEntity(relyingPartyId,
                relyingPartyName);

        while (true) {
            try {
                String optionsId = Utils.getUUID();

                // The WebAuthn user handle is a fresh random value, independent of the options id and of any
                // existing account. Register options are served to callers that are not signed in, so the
                // handle must not depend on whether the email belongs to an existing user, and must never
                // carry an internal user id. The core does not read the user handle back at sign in.
                String userHandle = Utils.getUUID();

                PublicKeyCredentialUserEntity userEntity = new PublicKeyCredentialUserEntity(
                        userHandle.getBytes(StandardCharsets.UTF_8), email, displayName);

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


                AuthenticatorSelectionCriteria authenticatorSelectionCriteria = new AuthenticatorSelectionCriteria(null,
                        residentKey.equalsIgnoreCase("required"),
                        ResidentKeyRequirement.create(residentKey),
                        UserVerificationRequirement.create(userVerification));

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
            } catch (DuplicateOptionsIdException e) {
                //ignore and retry with different optionsId
            }
        }
    }

    public static JsonObject generateSignInOptions(TenantIdentifier tenantIdentifier, Storage storage,
                                                   String relyingPartyId, String relyingPartyName, String origin, Long timeout,
                                                   String userVerification, boolean userPresenceRequired)
            throws StorageQueryException, InvalidWebauthNOptionsException, TenantOrAppNotFoundException {

        OptionsValidator.validateOptions(origin, relyingPartyId, timeout, "none", userVerification, "required",
        new JsonArray(), userPresenceRequired);

        Challenge challenge = getChallenge();
        while (true) {
            try {
                String optionsId = Utils.getUUID();

                WebAuthNOptions savedOptions = saveGeneratedOptions(tenantIdentifier, storage, challenge, timeout,
                        relyingPartyId, relyingPartyName, origin,
                        null, optionsId, userVerification, userPresenceRequired);

                return WebauthMapper.mapOptionsResponse(relyingPartyId, timeout, userVerification, optionsId, challenge,
                        savedOptions.createdAt, userPresenceRequired);
            } catch (DuplicateOptionsIdException e) {
                //ignore and retry with different optionsId
            }
        }
    }

    @NotNull
    private static Challenge getChallenge() {
        Challenge challenge = new Challenge() {
            private final byte[] challengeRaw = new byte[32];

            { //initializer block
                new SecureRandom().nextBytes(challengeRaw);
            }

            @NotNull
            @Override
            public byte[] getValue() {
                return challengeRaw;
            }
        };
        return challenge;
    }

    public static WebauthNCredentialResponse registerCredentials(Storage storage, TenantIdentifier tenantIdentifier, String recipeUserId,
                                                                 String optionsId, JsonObject credentialsDataJson)
            throws InvalidWebauthNOptionsException, StorageQueryException, WebauthNVerificationFailedException,
            WebauthNInvalidFormatException, WebauthNOptionsNotExistsException, DuplicateCredentialException,
            UnknownUserIdException, TenantOrAppNotFoundException {

        WebAuthNStorage webAuthNStorage = (WebAuthNStorage) storage;
        WebAuthNOptions generatedOptions = webAuthNStorage.loadOptionsById(tenantIdentifier, optionsId);

        if(generatedOptions == null) {
            throw new WebauthNOptionsNotExistsException();
        }

        CredentialsValidator.validateCredential(credentialsDataJson);
        RegistrationData verifiedRegistrationData = verifyRegistrationData(credentialsDataJson,
                generatedOptions);

        String supertokensUserId = translateToInternalUserId(storage, tenantIdentifier, recipeUserId);
        String credentialId = getAttestedCredentialId(credentialsDataJson, verifiedRegistrationData);
        // Credential ids must be unique within the app, across relying party ids: sign in looks a credential up
        // by id alone. The same id under another relying party id is rejected like at sign-up; a duplicate under
        // the same relying party id keeps its previous status (DuplicateCredentialException), independent of how
        // the storage reports the primary key conflict.
        WebAuthNStoredCredential existing = loadCredentialByIdInApp(storage, tenantIdentifier, credentialId);
        if (existing != null) {
            if (Objects.equals(existing.rpId, generatedOptions.relyingPartyId)) {
                throw new DuplicateCredentialException();
            }
            throw new WebauthNVerificationFailedException("Credential already registered");
        }
        WebAuthNStoredCredential credentialToSave = WebauthMapper.mapRegistrationDataToStoredCredential(
                verifiedRegistrationData,
                supertokensUserId, credentialId, generatedOptions.userEmail, generatedOptions.relyingPartyId,
                tenantIdentifier);

        WebAuthNStoredCredential savedCredential = webAuthNStorage.saveCredentials(tenantIdentifier, credentialToSave);

        WebauthNCredentialResponse response = WebauthMapper.mapStoredCredentialToResponse(savedCredential, generatedOptions.userEmail,
                generatedOptions.relyingPartyName);

        response.recipeUserId = recipeUserId;

        return response;
    }

    @NotNull
    private static RegistrationData verifyRegistrationData(JsonObject registrationResponseJson,
                                                           WebAuthNOptions generatedOptions)
            throws InvalidWebauthNOptionsException, WebauthNVerificationFailedException,
            WebauthNInvalidFormatException {
        long now = System.currentTimeMillis();
        if(generatedOptions.expiresAt < now) {
            throw new InvalidWebauthNOptionsException("Options expired");
        }

        WebAuthnManager nonStrictWebAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
        try {
            RegistrationData registrationData = nonStrictWebAuthnManager.parseRegistrationResponseJSON(
                    new Gson().toJson(registrationResponseJson));
            RegistrationParameters registrationParameters = getRegistrationParameters(generatedOptions);
            return nonStrictWebAuthnManager.verify(registrationData,
                    registrationParameters);
        } catch ( VerificationException e) {
            throw new WebauthNVerificationFailedException(e.getMessage());
        } catch (DataConversionException e) {
            throw new WebauthNInvalidFormatException(e.getMessage());
        }
    }

    private static AuthenticationData verifyAuthenticationData(JsonObject authenticationResponse,
                                                               WebAuthNOptions generatedOptions,
                                                               WebAuthNStoredCredential storedCredential)
            throws InvalidWebauthNOptionsException, WebauthNVerificationFailedException,
            WebauthNInvalidFormatException {
        long now = System.currentTimeMillis();
        if (generatedOptions.expiresAt < now) {
            throw new InvalidWebauthNOptionsException("Options expired");
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
                                                String optionsId, JsonObject credentialDataJson)
            throws InvalidWebauthNOptionsException, DuplicateEmailException, WebauthNVerificationFailedException,
            StorageQueryException, WebauthNOptionsNotExistsException, WebauthNInvalidFormatException{
        // create a new user in the auth recipe storage
        // create new credentials
        // emit the user_creation event
        // all within a single audited transaction, so the event commits atomically with the new user
        try {
            WebAuthNSQLStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
            ActivityLogSQLStorage auditStorage = (ActivityLogSQLStorage) storage;
            AppIdentifier appIdentifier = tenantIdentifier.toAppIdentifier();
            return auditStorage.startAuditedTransaction(appIdentifier, con -> {

                while (true) {
                    try {
                        String recipeUserId = Utils.getUUID();
                        long timeJoined = System.currentTimeMillis();

                        WebAuthNOptions generatedOptions = webAuthNStorage.loadOptionsById_Transaction(tenantIdentifier,
                                con,
                                optionsId);

                        if(generatedOptions == null) {
                            throw new WebauthNOptionsNotExistsException();
                        }

                        CredentialsValidator.validateCredential(credentialDataJson);
                        RegistrationData verifiedRegistrationData = verifyRegistrationData(credentialDataJson,
                                generatedOptions);
                        String credentialId = getAttestedCredentialId(credentialDataJson, verifiedRegistrationData);
                        // Credential ids stay unique within the app, across relying party ids, so one authenticator
                        // credential is bound to one account (sign in itself is rp-scoped).
                        if (webAuthNStorage.loadCredentialById_Transaction(tenantIdentifier, con, credentialId) != null) {
                            throw new WebauthNVerificationFailedException("Credential already registered");
                        }
                        WebAuthNStoredCredential credentialToSave = WebauthMapper.mapRegistrationDataToStoredCredential(
                                verifiedRegistrationData,
                                recipeUserId, credentialId, generatedOptions.userEmail, generatedOptions.relyingPartyId,
                                tenantIdentifier);
                        AuthRecipeUserInfo userInfo = webAuthNStorage.signUpWithCredentialsRegister_Transaction(
                                tenantIdentifier, con, recipeUserId, generatedOptions.userEmail,
                                generatedOptions.relyingPartyId, credentialToSave);
                        userInfo.setExternalUserId(null);

                        AuditLogEvent event = LifecycleAuditEvent.forUserCreation(appIdentifier,
                                userInfo.getSupertokensUserId(), tenantIdentifier.getTenantId(), timeJoined);
                        return new AuditedResult<>(
                                new WebAuthNSignInUpResult(credentialToSave, userInfo, generatedOptions), event);
                    } catch (DuplicateUserIdException duplicateUserIdException) {
                        //ignore and retry
                    } catch (InvalidWebauthNOptionsException | TenantOrAppNotFoundException |
                             DuplicateEmailException | WebauthNVerificationFailedException |
                             WebauthNInvalidFormatException | WebauthNOptionsNotExistsException e) {
                        throw new StorageQueryException(e);
                    }
                }
            });

        } catch (StorageQueryException exception) {
            if (exception.getCause() instanceof InvalidWebauthNOptionsException) {
                throw (InvalidWebauthNOptionsException) exception.getCause();
            } else if (exception.getCause() instanceof DuplicateEmailException) {
                throw (DuplicateEmailException) exception.getCause();
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

    private static String getCredentialId(JsonObject credentialsJson) {
        String credentialId = null;
        if(credentialsJson.has("id")){
            credentialId = credentialsJson.get("id").getAsString();
        }
        return credentialId;
    }

    /**
     * The credential id of a registration is the one in the verified attestation's attested credential data
     * (WebAuthn L3 §7.1). The client-supplied {@code id} and {@code rawId} must match it.
     */
    private static String getAttestedCredentialId(JsonObject credentialsJson, RegistrationData verifiedRegistrationData)
            throws WebauthNInvalidFormatException {
        if (verifiedRegistrationData.getAttestationObject() == null
                || verifiedRegistrationData.getAttestationObject().getAuthenticatorData() == null
                || verifiedRegistrationData.getAttestationObject().getAuthenticatorData()
                .getAttestedCredentialData() == null) {
            throw new WebauthNInvalidFormatException("attested credential data is missing");
        }
        byte[] attestedCredentialId = verifiedRegistrationData.getAttestationObject().getAuthenticatorData()
                .getAttestedCredentialData().getCredentialId();
        String attestedCredentialIdString = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(attestedCredentialId);
        if (!attestedCredentialIdString.equals(getCredentialId(credentialsJson))) {
            throw new WebauthNInvalidFormatException("credential id does not match the attested credential id");
        }
        validateRawIdMatchesId(credentialsJson, attestedCredentialId);
        return attestedCredentialIdString;
    }

    private static void validateRawIdMatchesId(JsonObject credentialsJson, byte[] expectedId)
            throws WebauthNInvalidFormatException {
        if (!credentialsJson.has("rawId") || credentialsJson.get("rawId").isJsonNull()) {
            return;
        }
        byte[] rawId;
        try {
            rawId = Base64.getUrlDecoder().decode(credentialsJson.get("rawId").getAsString());
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            throw new WebauthNInvalidFormatException("credential rawId has to be base64 url encoded");
        }
        if (!java.security.MessageDigest.isEqual(rawId, expectedId)) {
            throw new WebauthNInvalidFormatException("credential rawId does not match the credential id");
        }
    }

    private static void validateRawIdMatchesId(JsonObject credentialsJson) throws WebauthNInvalidFormatException {
        byte[] id;
        try {
            id = Base64.getUrlDecoder().decode(getCredentialId(credentialsJson));
        } catch (IllegalArgumentException e) {
            throw new WebauthNInvalidFormatException("Credential ID has to be base64 url encoded");
        }
        validateRawIdMatchesId(credentialsJson, id);
    }

    private static WebAuthNStoredCredential loadCredentialByIdInApp(Storage storage, TenantIdentifier tenantIdentifier,
                                                                    String credentialId) throws StorageQueryException {
        WebAuthNSQLStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
        try {
            return ((ActivityLogSQLStorage) storage).startAuditedTransaction(tenantIdentifier.toAppIdentifier(),
                    con -> AuditedResult.withoutAudit(
                            webAuthNStorage.loadCredentialById_Transaction(tenantIdentifier, con, credentialId),
                            "Read-only lookup of an existing credential id before registration; writes nothing."));
        } catch (StorageTransactionLogicException e) {
            throw new StorageQueryException(e);
        }
    }

    @TestOnly
    @UnauditedTransaction(justification = "Legacy unaudited transaction (PLAN-012 backlog); pending conversion to startAuditedTransaction or read-only exemption.")
    public static AuthRecipeUserInfo saveUser(Storage storage, TenantIdentifier tenantIdentifier, String email, String userId, String rpId)
            throws StorageQueryException, TenantOrAppNotFoundException, DuplicateEmailException,
            DuplicateUserIdException {
        WebAuthNSQLStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
        try {
            return webAuthNStorage.startTransaction(con -> {
                try {
                    return webAuthNStorage.signUp_Transaction(tenantIdentifier, con, userId, email, rpId);
                } catch (TenantOrAppNotFoundException | DuplicateEmailException | DuplicateUserIdException e) {
                    throw new StorageTransactionLogicException(e);
                }
            });
        } catch (StorageTransactionLogicException  e) {
            if (e.actualException instanceof TenantOrAppNotFoundException) {
                throw (TenantOrAppNotFoundException) e.actualException;
            } else if (e.actualException instanceof DuplicateEmailException) {
                throw (DuplicateEmailException) e.actualException;
            } else if (e.actualException instanceof DuplicateUserIdException) {
                throw (DuplicateUserIdException) e.actualException;
            } else {
                throw new StorageQueryException(e.actualException);
            }
        }
    }

    @UnauditedTransaction(justification = "Legacy unaudited transaction (PLAN-012 backlog); pending conversion to startAuditedTransaction or read-only exemption.")
    public static WebAuthNSignInUpResult signIn(Storage storage, TenantIdentifier tenantIdentifier,
                                              String webauthnGeneratedOptionsId, JsonObject credentialsData,
                                              boolean consumeOptions)
            throws InvalidWebauthNOptionsException, WebauthNVerificationFailedException,
            WebauthNInvalidFormatException, StorageQueryException, WebauthNOptionsNotExistsException,
            WebauthNCredentialNotExistsException, UnknownUserIdException {
        try {
            WebAuthNSQLStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
            return webAuthNStorage.startTransaction(con -> {

                try {
                    WebAuthNOptions generatedOptions = webAuthNStorage.loadOptionsById_Transaction(tenantIdentifier,
                            con,
                            webauthnGeneratedOptionsId);

                    if(generatedOptions == null) {
                        throw new StorageTransactionLogicException(new WebauthNOptionsNotExistsException());
                    }

                    CredentialsValidator.validateCredential(credentialsData);

                    String credentialId = getCredentialId(credentialsData);
                    validateRawIdMatchesId(credentialsData);

                    WebAuthNStoredCredential credential = webAuthNStorage.loadCredentialById_Transaction(tenantIdentifier,
                            con, credentialId);
                    if(credential==null) {
                        throw new StorageTransactionLogicException(new WebauthNCredentialNotExistsException());
                    }
                    // A credential is scoped to the relying party it was registered for. A stored credential
                    // of another relying party cannot be the one this assertion was made with.
                    if (credential.rpId == null || !credential.rpId.equalsIgnoreCase(generatedOptions.relyingPartyId)) {
                        throw new StorageTransactionLogicException(new WebauthNCredentialNotExistsException());
                    }

                    verifyAuthenticationData(credentialsData, generatedOptions, credential);
                    webAuthNStorage.updateCounter_Transaction(tenantIdentifier, con, credentialId, credential.counter); //the verifyAuthenticatorData method's verify step updates the credential on this object. We have to save the updated value!

                    if (consumeOptions) {
                        // challenges are single-use (WebAuthn L3 §13.4.3): consume the options atomically with
                        // the successful verification so the same assertion cannot be replayed. Only done for
                        // CDI >= 5.5 requests — older SDKs verify the same assertion twice per sign in.
                        webAuthNStorage.removeOptions_Transaction(tenantIdentifier, con, webauthnGeneratedOptionsId);
                    }

                    // Resolve the user from the owner of the credential row whose public key verified the
                    // assertion, not from a second lookup by credential id, so the signed-in user is always the
                    // owner of that key.
                    AuthRecipeUserInfo fullyLoadedUserInfo = StorageUtils.getAuthRecipeStorage(storage)
                            .getPrimaryUserById_Transaction(tenantIdentifier.toAppIdentifier(), con, credential.userId);
                    if(fullyLoadedUserInfo == null) {
                        // this shouldn't ever happen!
                        throw new StorageTransactionLogicException(new UnknownUserIdException());
                    }
                    // The webauthn login method that owns the credential must be a member of the requested tenant.
                    LoginMethod credentialOwner = null;
                    for (LoginMethod lm : fullyLoadedUserInfo.loginMethods) {
                        if (lm.recipeId == RECIPE_ID.WEBAUTHN && lm.getSupertokensUserId().equals(credential.userId)) {
                            credentialOwner = lm;
                            break;
                        }
                    }
                    if (credentialOwner == null || credentialOwner.tenantIds == null
                            || !credentialOwner.tenantIds.contains(tenantIdentifier.getTenantId())) {
                        throw new StorageTransactionLogicException(new UnknownUserIdException());
                    }

                    // Defense in depth: the resolved user must be a member of the tenant this sign-in was
                    // requested for (implied by the login method check above).
                    if (fullyLoadedUserInfo.tenantIds == null
                            || !fullyLoadedUserInfo.tenantIds.contains(tenantIdentifier.getTenantId())) {
                        throw new StorageTransactionLogicException(new WebauthNCredentialNotExistsException());
                    }
                    UserIdMapping.populateExternalUserIdForUsers(tenantIdentifier.toAppIdentifier(), storage, new AuthRecipeUserInfo[]{fullyLoadedUserInfo});
                    return new WebAuthNSignInUpResult(credential, fullyLoadedUserInfo, generatedOptions);
                } catch (InvalidWebauthNOptionsException | WebauthNVerificationFailedException |
                         WebauthNInvalidFormatException  e) {
                    throw new StorageTransactionLogicException(e);
                }
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
            } else if (e.getCause() instanceof WebauthNCredentialNotExistsException) {
                throw (WebauthNCredentialNotExistsException) e.getCause();
            } else if (e.getCause() instanceof UnknownUserIdException) {
                throw (UnknownUserIdException) e.getCause();
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
        String supertokensUserId = translateToInternalUserId(storage, tenantIdentifier, recipeUserId);
        WebAuthNStoredCredential loadedCredential = webAuthNStorage.loadCredentialByIdForUser(tenantIdentifier, credentialId, supertokensUserId);
        if(loadedCredential != null) {
            loadedCredential.userId = recipeUserId;
        }
        return loadedCredential;
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
            throws StorageQueryException, DuplicateOptionsIdException, TenantOrAppNotFoundException {
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

    public static String generateRecoverAccountToken(Main main, Storage storage, TenantIdentifier tenantIdentifier, String email, String userIdFromRequest)
            throws NoSuchAlgorithmException, InvalidKeySpecException, TenantOrAppNotFoundException,
            StorageQueryException, UnknownUserIdException {

        String userId = translateToInternalUserId(storage, tenantIdentifier, userIdFromRequest);

        AuthRecipeUserInfo user = AuthRecipe.getUserById(tenantIdentifier.toAppIdentifier(), storage, userId);

        // The user lookup is app-wide; the token is issued for this tenant, so the user must be a member of it.
        if (!isMemberOfTenant(user, tenantIdentifier)) {
            throw new UnknownUserIdException();
        }

        if (!isRecoverAccountTokenEmailStillOnAccount(user, email)) {
            // Kept issuing (no new status for this API), but the token will be rejected when consumed.
            Logging.warn(main, tenantIdentifier, "Recover account token created for an email that does not belong "
                    + "to any login method of the user; it will be rejected when consumed.");
        }

        while (true) {
            // we first generate a password reset token
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
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

    // A recover account token is bound to the email it was sent to: that email must still belong to the account.
    // This is checked against every login method of the account, not only the webauthn login method with the
    // token's user id, because the SDKs also issue tokens for the primary user id with the email of another linked
    // login method, to add a webauthn login method with that email.
    private static boolean isRecoverAccountTokenEmailStillOnAccount(AuthRecipeUserInfo user, String tokenEmail) {
        if (user == null || tokenEmail == null) {
            return false;
        }
        String email = Utils.normaliseEmail(tokenEmail);
        for (LoginMethod lM : user.loginMethods) {
            if (lM.email != null && email.equals(Utils.normaliseEmail(lM.email))) {
                return true;
            }
        }
        return false;
    }

    @UnauditedTransaction(justification = "Legacy unaudited transaction (PLAN-012 backlog); pending conversion to startAuditedTransaction or read-only exemption.")
    public static AccountRecoveryTokenInfo consumeRecoverAccountToken(Main main, TenantIdentifier tenantIdentifier, Storage storage, String token)
            throws StorageQueryException, NoSuchAlgorithmException, InvalidTokenException {
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

                    // The token is spent either way, but it only recovers a user who is a member of this tenant
                    // and whose account still has the email the token was sent to.
                    AuthRecipeUserInfo user = StorageUtils.getAuthRecipeStorage(storage).getPrimaryUserById_Transaction(
                            tenantIdentifier.toAppIdentifier(), con, recoveryTokenInfo.userId);
                    if (!isMemberOfTenant(user, tenantIdentifier)
                            || !isRecoverAccountTokenEmailStillOnAccount(user, recoveryTokenInfo.email)) {
                        return null;
                    }
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

    @UnauditedTransaction(justification = "Legacy unaudited transaction (PLAN-012 backlog); pending conversion to startAuditedTransaction or read-only exemption.")
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

            AuthRecipeUserInfo user = AuthRecipe.getUserById(tenantIdentifier.toAppIdentifier(), storage,
                    tokenInfo.userId);
            if (!isMemberOfTenant(user, tenantIdentifier)) {
                throw new InvalidTokenException();
            }
            return user;
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof StorageQueryException) {
                throw (StorageQueryException) e.actualException;
            }
            throw new IllegalStateException(e.actualException);
        }
    }

    private static boolean isMemberOfTenant(AuthRecipeUserInfo user, TenantIdentifier tenantIdentifier) {
        return user != null && user.tenantIds != null && user.tenantIds.contains(tenantIdentifier.getTenantId());
    }

    private static long getRecoverAccountTokenLifetime(TenantIdentifier tenantIdentifier, Main main)
            throws TenantOrAppNotFoundException {
        return Config.getConfig(tenantIdentifier, main).getWebauthnRecoverAccountTokenLifetime();
    }

    public static void removeCredential(Storage storage, TenantIdentifier tenantIdentifier,
                                        String userId, String credentialId)
            throws StorageQueryException, WebauthNCredentialNotExistsException {
        WebAuthNStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
        String supertokensUserId = translateToInternalUserId(storage, tenantIdentifier, userId);
        webAuthNStorage.removeCredential(tenantIdentifier, supertokensUserId, credentialId);
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
        String supertokensUserId = translateToInternalUserId(storage, tenantIdentifier, userId);
        List<WebAuthNStoredCredential> loadedCredentials = webAuthNStorage.listCredentialsForUser(tenantIdentifier, supertokensUserId);
        for (WebAuthNStoredCredential credential : loadedCredentials) {
            credential.userId = userId; //change back to the external user id
        }
        return loadedCredentials;
    }

    private static String translateToInternalUserId(Storage storage,
                                                    TenantIdentifier tenantIdentifier,
                                                    String userId)
            throws StorageQueryException {
        io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping =
                UserIdMapping.getUserIdMapping(tenantIdentifier.toAppIdentifier(), storage, userId, null);
        return userIdMapping == null ? userId : userIdMapping.superTokensUserId;
    }

    @UnauditedTransaction(justification = "Legacy unaudited transaction (PLAN-012 backlog); pending conversion to startAuditedTransaction or read-only exemption.")
    public static void updateUserEmail(Storage storage, TenantIdentifier tenantIdentifier, String userId, String email)
            throws StorageQueryException, UnknownUserIdException, DuplicateEmailException,
            EmailChangeNotAllowedException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        WebAuthNSQLStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
        AuthRecipeSQLStorage authRecipeStorage = StorageUtils.getAuthRecipeStorage(storage);
        try {
            webAuthNStorage.startTransaction(con -> {

                String userIdFromMapping = translateToInternalUserId(storage, tenantIdentifier, userId);

                AuthRecipeUserInfo user = StorageUtils.getAuthRecipeStorage(storage)
                        .getPrimaryUserById_Transaction(tenantIdentifier.toAppIdentifier(), con, userIdFromMapping);
                if (user == null) {
                    throw new StorageTransactionLogicException(new UnknownUserIdException());
                }
                boolean relevantLoginMethod = false;
                for (LoginMethod lm : user.loginMethods) {
                    if (lm.recipeId == RECIPE_ID.WEBAUTHN && lm.getSupertokensUserId().equals(userIdFromMapping)) {
                        relevantLoginMethod = true;
                        break;
                    }
                }
                if (!relevantLoginMethod) {
                    throw new StorageTransactionLogicException(new UnknownUserIdException());
                }

                if (email != null) {
                    try {
                        webAuthNStorage.updateUserEmail_Transaction(tenantIdentifier, con,
                                userIdFromMapping, email);
                    } catch (DuplicateEmailException | EmailChangeNotAllowedException |
                             UnknownUserIdException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                }
                return null;
            });
        } catch (StorageTransactionLogicException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UnknownUserIdException){
                throw (UnknownUserIdException) cause;
            } else if (cause instanceof DuplicateEmailException) {
                throw (DuplicateEmailException) cause;
            } else if (cause instanceof EmailChangeNotAllowedException) {
                throw (EmailChangeNotAllowedException) cause;
            } else if (cause instanceof TenantOrAppNotFoundException) {
                throw (TenantOrAppNotFoundException) cause;
            }
            throw e;
        }
    }
}
