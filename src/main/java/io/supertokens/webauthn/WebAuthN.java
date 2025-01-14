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
import com.google.gson.JsonPrimitive;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.server.ServerProperty;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.dashboard.exceptions.UserIdNotFoundException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.webauthn.WebAuthNOptions;
import io.supertokens.pluginInterface.webauthn.WebAuthNStorage;
import io.supertokens.pluginInterface.webauthn.WebAuthNStoredCredential;
import io.supertokens.utils.Utils;
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
            throws StorageQueryException, UserIdNotFoundException {

        PublicKeyCredentialRpEntity relyingPartyEntity = new PublicKeyCredentialRpEntity(relyingPartyId, relyingPartyName);

        String id = null;
        AuthRecipeStorage authStorage = (AuthRecipeStorage) storage;
        AuthRecipeUserInfo[] usersWithEmail = authStorage.listPrimaryUsersByEmail(tenantIdentifier, email);
        if(usersWithEmail.length > 0) {
            id = usersWithEmail[0].getSupertokensUserId();
        }

        if(id == null){
            throw new UserIdNotFoundException();
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

        AuthenticatorSelectionCriteria authenticatorSelectionCriteria = new AuthenticatorSelectionCriteria(null, null,
                ResidentKeyRequirement.create(residentKey), UserVerificationRequirement.create(userVerificitaion) );

        AttestationConveyancePreference attestationConveyancePreference = AttestationConveyancePreference.create(attestation);

        PublicKeyCredentialCreationOptions options = new PublicKeyCredentialCreationOptions(relyingPartyEntity,
                userEntity, challenge, credentialParameters, timeout, null, authenticatorSelectionCriteria,
                null, attestationConveyancePreference, null);

        String optionsId = Utils.getUUID();

        saveGeneratedOptions(tenantIdentifier, storage, options.getChallenge(), options.getTimeout(),
                options.getRp().getId(), origin, email, optionsId);

        return createResponseFromOptions(options, optionsId);
    }

    public static JsonObject generateSignInOptions(TenantIdentifier tenantIdentifier, Storage storage,
                                                   String relyingPartyId, String origin, Long timeout,
                                                   String userVerification)
            throws StorageQueryException, UserIdNotFoundException {

        Challenge challenge = getChallenge();

        String optionsId = Utils.getUUID();

        saveGeneratedOptions(tenantIdentifier, storage, challenge, timeout, relyingPartyId, origin,
                null, optionsId); // TODO is it sure that the email should be null? ask Victor

        JsonObject response = new JsonObject();
        response.addProperty("webauthnGeneratedOptionsId", optionsId);
        response.addProperty("rpId", relyingPartyId);
        response.addProperty("challenge", Base64.getEncoder().encodeToString(challenge.getValue()));
        response.addProperty("timeout", timeout);
        response.addProperty("userVerification", userVerification);

        return response;
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

    public static void registerCredentials(Storage storage, TenantIdentifier tenantIdentifier, String optionsId,
                                           String credentialId, String registrationResponseJson)
            throws Exception {

        WebAuthNStorage webAuthNStorage = (WebAuthNStorage) storage;
        WebAuthNOptions generatedOptions = webAuthNStorage.loadOptionsById(tenantIdentifier, optionsId);

        long now = System.currentTimeMillis();
        if(generatedOptions.expiresAt < now) {
            throw new Exception("expired"); // TODO make it some meaningful exception
        }
        if(!generatedOptions.origin.contains(generatedOptions.relyingPartyId)) {
            throw new Exception("not valid origin"); // TODO make it some meaningful exception
        }

        WebAuthnManager nonStrictWebAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
        RegistrationData registrationData = nonStrictWebAuthnManager.parseRegistrationResponseJSON(registrationResponseJson);

        RegistrationParameters registrationParameters = getRegistrationParameters(generatedOptions);

        RegistrationData verifiedRegistrationData = nonStrictWebAuthnManager.verify(registrationData,
                registrationParameters);

        WebAuthNStoredCredential credentialToSave = mapRegistrationDataToStoredCredential(verifiedRegistrationData, credentialId, generatedOptions.userEmail,
                generatedOptions.relyingPartyId, tenantIdentifier);

        webAuthNStorage.saveCredentials(tenantIdentifier, credentialToSave);
        //TODO create recipe user
        //TODO save recipe user related stuff
        //TODO return values!
    }

    private static WebAuthNStoredCredential mapRegistrationDataToStoredCredential(RegistrationData verifiedRegistrationData,
                                                              String credentialId, String userEmail,
                                                              String relyingPartyId, TenantIdentifier tenantIdentifier) {
        ObjectConverter objectConverter = new ObjectConverter();
        WebAuthNStoredCredential storedCredential = new WebAuthNStoredCredential();
        storedCredential.id = credentialId;
        storedCredential.appId = tenantIdentifier.getAppId();
        storedCredential.rpId = relyingPartyId;
        storedCredential.userId = userEmail;
        storedCredential.counter = verifiedRegistrationData.getAttestationObject().getAuthenticatorData().getSignCount();
        AttestedCredentialDataConverter attestedCredentialDataConverter = new AttestedCredentialDataConverter(objectConverter);
        storedCredential.publicKey = attestedCredentialDataConverter.convert(verifiedRegistrationData.getAttestationObject().getAuthenticatorData()
                .getAttestedCredentialData());
        storedCredential.transports = objectConverter.getJsonConverter().writeValueAsString(verifiedRegistrationData.getTransports());
        storedCredential.createdAt = System.currentTimeMillis();
        storedCredential.updatedAt = storedCredential.createdAt;

        return storedCredential;
    }

    @NotNull
    private static RegistrationParameters getRegistrationParameters(WebAuthNOptions generatedOptions) {
        List<PublicKeyCredentialParameters> pubKeyCredParams = null; //Specify the same value as the pubKeyCredParams provided in PublicKeyCredentialCreationOptions
        boolean userVerificationRequired = false;
        boolean userPresenceRequired = true;

        RegistrationParameters registrationParameters = new RegistrationParameters(
                new ServerProperty(new Origin(generatedOptions.origin), generatedOptions.relyingPartyId,
                        new Challenge() {
                            @NotNull
                            @Override
                            public byte[] getValue() {
                                return generatedOptions.challenge.getBytes(StandardCharsets.UTF_8);
                            }
                        }),
                pubKeyCredParams,
                userVerificationRequired,
                userPresenceRequired
        );
        return registrationParameters;
    }

    private static JsonObject createResponseFromOptions(PublicKeyCredentialCreationOptions options, String id) {
        JsonObject response = new JsonObject();
        response.addProperty("webauthGeneratedOptionsId", id);

        JsonObject rp = new JsonObject();
        rp.addProperty("id", options.getRp().getId());
        rp. addProperty("name", options.getRp().getName());
        response.add("rp", rp);

        JsonObject user = new JsonObject();
        user.addProperty("id", new String(options.getUser().getId()));
        user.addProperty("name", options.getUser().getName());
        user.addProperty("displayName", options.getUser().getDisplayName());
        response.add("user", user);

        response.addProperty("timeout", options.getTimeout());
        response.addProperty("challenge", Base64.getEncoder().encodeToString(options.getChallenge().getValue()));
        response.addProperty("attestation", options.getAttestation().getValue());

        JsonArray pubKeyCredParams = new JsonArray();
        for(PublicKeyCredentialParameters params : options.getPubKeyCredParams()){
            JsonObject pubKeyParam = new JsonObject();
            pubKeyParam.addProperty("alg", params.getAlg().getValue());
            pubKeyParam.addProperty("type", params.getType().getValue()); // should be always public-key
            pubKeyCredParams.add(pubKeyParam);
        }
        response.add("pubKeyCredParams",pubKeyCredParams);

        JsonArray excludeCredentials = new JsonArray();
        if(options.getExcludeCredentials() != null){
            for(PublicKeyCredentialDescriptor exclude : options.getExcludeCredentials()){
                JsonObject excl = new JsonObject();
                excl.addProperty("id", new String(exclude.getId()));
                JsonArray transports = new JsonArray();
                for(AuthenticatorTransport transp: exclude.getTransports()){
                    transports.add(new JsonPrimitive(transp.getValue()));
                }
                excl.add("transport", transports);
                excludeCredentials.add(excl);
            }
        }
        response.add("excludeCredentials", excludeCredentials);

        JsonObject authenticatorSelection = new JsonObject();
        authenticatorSelection.addProperty("requireResidentKey", options.getAuthenticatorSelection().isRequireResidentKey() == null ? false : options.getAuthenticatorSelection().isRequireResidentKey());
        authenticatorSelection.addProperty("residentKey", options.getAuthenticatorSelection().getResidentKey().getValue());
        authenticatorSelection.addProperty("userVerification", options.getAuthenticatorSelection().getUserVerification().getValue());

        response.add("authenticatorSelection", authenticatorSelection);
        return response;
    }

    private static void saveGeneratedOptions(TenantIdentifier tenantIdentifier, Storage storage, Challenge challenge,
            Long timeout, String relyinPartyId, String origin, String userEmail, String id)
            throws StorageQueryException {
        WebAuthNStorage webAuthNStorage = (WebAuthNStorage) storage;
        WebAuthNOptions savableOptions = new WebAuthNOptions();
        savableOptions.generatedOptionsId = id;
        savableOptions.challenge = Base64.getEncoder().encodeToString(challenge.getValue());
        savableOptions.origin = origin;
        savableOptions.timeout = timeout;
        savableOptions.createdAt = System.currentTimeMillis();
        savableOptions.expiresAt = savableOptions.createdAt + savableOptions.timeout;
        savableOptions.relyingPartyId = relyinPartyId;
        savableOptions.userEmail = userEmail;
        webAuthNStorage.saveGeneratedOptions(tenantIdentifier, savableOptions);
    }
}
