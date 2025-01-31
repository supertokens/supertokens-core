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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.server.ServerProperty;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.dashboard.exceptions.UserIdNotFoundException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.webauthn.WebAuthNOptions;
import io.supertokens.pluginInterface.webauthn.WebAuthNStorage;
import io.supertokens.pluginInterface.webauthn.WebAuthNStoredCredential;
import io.supertokens.pluginInterface.webauthn.slqStorage.WebAuthNSQLStorage;
import io.supertokens.utils.Utils;
import io.supertokens.webauthn.utils.WebauthMapper;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

public class WebAuthN {

    public static JsonObject generateOptions(TenantIdentifier tenantIdentifier, Storage storage, String email, String displayName, String relyingPartyName, String relyingPartyId,
                                             String origin, Long timeout, String attestation, String residentKey,
                                             String userVerificitaion, JsonArray supportedAlgorithmIds)
            throws StorageQueryException {

        PublicKeyCredentialRpEntity relyingPartyEntity = new PublicKeyCredentialRpEntity(relyingPartyId, relyingPartyName);

        String id = null;
        String optionsId = Utils.getUUID();

        AuthRecipeStorage authStorage = (AuthRecipeStorage) storage;
        AuthRecipeUserInfo[] usersWithEmail = authStorage.listPrimaryUsersByEmail(tenantIdentifier, email);
        if(usersWithEmail.length > 0) {
            id = usersWithEmail[0].getSupertokensUserId();
        } else {
            id = optionsId;
        }

        PublicKeyCredentialUserEntity userEntity = new PublicKeyCredentialUserEntity(id.getBytes(StandardCharsets.UTF_8), email, displayName);

        Challenge challenge = getChallenge();

        List<PublicKeyCredentialParameters> credentialParameters = new ArrayList<>();
        for(int i = 0; i< supportedAlgorithmIds.size(); i++){
            JsonElement supportedAlgoId = supportedAlgorithmIds.get(i);
            COSEAlgorithmIdentifier algorithmIdentifier = COSEAlgorithmIdentifier.create(supportedAlgoId.getAsLong());
            PublicKeyCredentialParameters param = new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, algorithmIdentifier);
            credentialParameters.add(param);
        };

        AuthenticatorSelectionCriteria authenticatorSelectionCriteria = new AuthenticatorSelectionCriteria(null,
                residentKey.equalsIgnoreCase("required"),
                ResidentKeyRequirement.create(residentKey), UserVerificationRequirement.create(userVerificitaion) );

        AttestationConveyancePreference attestationConveyancePreference = AttestationConveyancePreference.create(attestation);

        PublicKeyCredentialCreationOptions options = new PublicKeyCredentialCreationOptions(relyingPartyEntity,
                userEntity, challenge, credentialParameters, timeout, null, authenticatorSelectionCriteria,
                null, attestationConveyancePreference, null);



        WebAuthNOptions savedOptions = saveGeneratedOptions(tenantIdentifier, storage, options.getChallenge(), options.getTimeout(),
                options.getRp().getId(), options.getRp().getName(), origin, email, optionsId);

        return WebauthMapper.createResponseFromOptions(options, optionsId, savedOptions.createdAt,
                savedOptions.expiresAt, savedOptions.userEmail);
    }

    public static JsonObject generateSignInOptions(TenantIdentifier tenantIdentifier, Storage storage,
                                                   String relyingPartyId, String relyingPartyName, String origin, Long timeout,
                                                   String userVerification)
            throws StorageQueryException, UserIdNotFoundException {

        Challenge challenge = getChallenge();

        String optionsId = Utils.getUUID();

        saveGeneratedOptions(tenantIdentifier, storage, challenge, timeout, relyingPartyId, relyingPartyName, origin,
                null, optionsId);

        return WebauthMapper.mapOptionsResponse(relyingPartyId, timeout, userVerification, optionsId, challenge);
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

    public static WebauthNSaveCredentialResponse registerCredentials(Storage storage, TenantIdentifier tenantIdentifier, String recipeUserId,
                                           String optionsId, String credentialId, String registrationResponseJson)
            throws Exception {

        WebAuthNStorage webAuthNStorage = (WebAuthNStorage) storage;
        WebAuthNOptions generatedOptions = webAuthNStorage.loadOptionsById(tenantIdentifier, optionsId);

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
                                                        WebAuthNOptions generatedOptions) throws Exception {
        long now = System.currentTimeMillis();
        if(generatedOptions.expiresAt < now) {
            throw new Exception("expired"); // TODO make it some meaningful exception
        }
        if(!generatedOptions.origin.contains(generatedOptions.relyingPartyId)) { // This seems to be bullshit. TODO
            throw new Exception("not valid origin"); // TODO make it some meaningful exception
        }

        WebAuthnManager nonStrictWebAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
        RegistrationData registrationData = nonStrictWebAuthnManager.parseRegistrationResponseJSON(
                registrationResponseJson);

        RegistrationParameters registrationParameters = getRegistrationParameters(generatedOptions);

        return nonStrictWebAuthnManager.verify(registrationData,
                registrationParameters);
    }

    public static WebAuthNSignInUpResult signUp(Storage storage, TenantIdentifier tenantIdentifier,
                                                String optionsId, String credentialId, String registrationResponseJson) {
        // create a new user in the auth recipe storage
        // create new credentials
        // all within a transaction
        try {
            WebAuthNSQLStorage webAuthNStorage = (WebAuthNSQLStorage) storage;
            return webAuthNStorage.startTransaction(con -> {

                while (true) {
                    try {
                        String recipeUserId = Utils.getUUID();
                        WebAuthNOptions generatedOptions = webAuthNStorage.loadOptionsById_Transaction(tenantIdentifier,
                                con,
                                optionsId);

                        AuthRecipeUserInfo userInfo = webAuthNStorage.signUp_Transaction(tenantIdentifier, con, recipeUserId, generatedOptions.userEmail,
                                generatedOptions.relyingPartyId);


                        RegistrationData verifiedRegistrationData = getRegistrationData(registrationResponseJson,
                                generatedOptions);
                        WebAuthNStoredCredential credentialToSave = WebauthMapper.mapRegistrationDataToStoredCredential(
                                verifiedRegistrationData,
                                recipeUserId, credentialId, generatedOptions.userEmail, generatedOptions.relyingPartyId,
                                tenantIdentifier);
                        WebAuthNStoredCredential savedCredential = webAuthNStorage.saveCredentials_Transaction(
                                tenantIdentifier,
                                con, credentialToSave);

                        return new WebAuthNSignInUpResult(savedCredential, userInfo, generatedOptions);
                    } catch (DuplicateUserIdException duplicateUserIdException) {
                        //ignore and retry
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e); // TODO! make it more specific
        }
    }

    public static WebAuthNSignInUpResult signIn(Storage storage, TenantIdentifier tenantIdentifier,
                                              String webauthGeneratedOptionsId, String credentialsDataString,
                                              String credentialId) {
        try {
            WebAuthNSQLStorage webAuthNStorage = (WebAuthNSQLStorage) storage;
            webAuthNStorage.startTransaction(con -> {

                WebAuthNOptions generatedOptions = webAuthNStorage.loadOptionsById_Transaction(tenantIdentifier,
                        con,
                        webauthGeneratedOptionsId);

                AuthRecipeUserInfo userInfo = webAuthNStorage.getUserInfoByCredentialId_Transaction(tenantIdentifier, con, credentialId);
                WebAuthNStoredCredential credential = webAuthNStorage.loadCredentialById_Transaction(tenantIdentifier, con, credentialId);

                try {
                    getRegistrationData(credentialsDataString, generatedOptions);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                webAuthNStorage.updateCounter_Transaction(tenantIdentifier, con, credentialId, credential.counter + 1);
                return new WebAuthNSignInUpResult(credential, userInfo, generatedOptions);
            });
        } catch (Exception e) {
            throw new RuntimeException(e); // TODO! make it more specific
        }
        return null;
    }

    public static WebAuthNOptions loadGeneratedOptionsById(Storage storage, TenantIdentifier tenantIdentifier,
                                           String webauthGeneratedOptionsId) throws StorageQueryException {
        WebAuthNSQLStorage webAuthNStorage = (WebAuthNSQLStorage) storage;
        return webAuthNStorage.loadOptionsById(tenantIdentifier, webauthGeneratedOptionsId);
    }

    @NotNull
    private static RegistrationParameters getRegistrationParameters(WebAuthNOptions generatedOptions) {
        List<PublicKeyCredentialParameters> pubKeyCredParams = null; //TODO: Specify the same value as the pubKeyCredParams provided in PublicKeyCredentialCreationOptions
        boolean userVerificationRequired = false;
        boolean userPresenceRequired = true;

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
            Long timeout, String relyinPartyId, String relyingPartyName, String origin, String userEmail, String id)
            throws StorageQueryException {
        WebAuthNStorage webAuthNStorage = (WebAuthNStorage) storage;
        WebAuthNOptions savableOptions = new WebAuthNOptions();
        savableOptions.generatedOptionsId = id;
        savableOptions.challenge = Base64.getUrlEncoder().encodeToString(challenge.getValue());
        savableOptions.origin = origin;
        savableOptions.timeout = timeout;
        savableOptions.createdAt = System.currentTimeMillis();
        savableOptions.expiresAt = savableOptions.createdAt + savableOptions.timeout;
        savableOptions.relyingPartyId = relyinPartyId;
        savableOptions.relyingPartyName = relyingPartyName;
        savableOptions.userEmail = userEmail;
        return webAuthNStorage.saveGeneratedOptions(tenantIdentifier, savableOptions);
    }


}
