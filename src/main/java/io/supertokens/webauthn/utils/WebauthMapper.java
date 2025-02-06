/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webauthn.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.client.challenge.Challenge;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.webauthn.WebAuthNOptions;
import io.supertokens.pluginInterface.webauthn.WebAuthNStoredCredential;
import io.supertokens.webauthn.WebauthNCredentialResponse;
import io.supertokens.webauthn.data.WebauthNCredentialRecord;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class WebauthMapper {
    public static WebAuthNStoredCredential mapRegistrationDataToStoredCredential(
            RegistrationData verifiedRegistrationData,
            String userId, String credentialId, String userEmail,
            String relyingPartyId, TenantIdentifier tenantIdentifier) {
        ObjectConverter objectConverter = new ObjectConverter();
        WebAuthNStoredCredential storedCredential = new WebAuthNStoredCredential();
        storedCredential.id = credentialId;
        storedCredential.appId = tenantIdentifier.getAppId();
        storedCredential.rpId = relyingPartyId;
        storedCredential.userId = userId;
        storedCredential.counter = verifiedRegistrationData.getAttestationObject().getAuthenticatorData()
                .getSignCount();
        AttestedCredentialDataConverter attestedCredentialDataConverter = new AttestedCredentialDataConverter(
                objectConverter);
        storedCredential.publicKey = attestedCredentialDataConverter.convert(
                verifiedRegistrationData.getAttestationObject().getAuthenticatorData()
                        .getAttestedCredentialData());
        storedCredential.transports = objectConverter.getJsonConverter()
                .writeValueAsString(verifiedRegistrationData.getTransports());
        storedCredential.createdAt = System.currentTimeMillis();
        storedCredential.updatedAt = storedCredential.createdAt;

        return storedCredential;
    }

    public static WebauthNCredentialRecord mapStoredCredentialToCredentialRecord(WebAuthNStoredCredential credential){
        ObjectConverter objectConverter = new ObjectConverter();
        AttestedCredentialDataConverter attestedCredentialDataConverter = new AttestedCredentialDataConverter(
                objectConverter);
        AttestedCredentialData attestedCredentialData = attestedCredentialDataConverter.convert(credential.publicKey);
        COSEKey coseKey = attestedCredentialData.getCOSEKey();
        return new WebauthNCredentialRecord(attestedCredentialData.getAaguid(), attestedCredentialData.getCredentialId(), coseKey, credential);
    }

    public static JsonObject createResponseFromOptions(PublicKeyCredentialCreationOptions options, String id,
                                                       long createdAt, long expiresAt, String email) {
        JsonObject response = new JsonObject();
        response.addProperty("webauthnGeneratedOptionsId", id);

        JsonObject rp = new JsonObject();
        rp.addProperty("id", options.getRp().getId());
        rp.addProperty("name", options.getRp().getName());
        response.add("rp", rp);

        JsonObject user = new JsonObject();
        user.addProperty("id", new String(Base64.getUrlEncoder().withoutPadding().encode(options.getUser().getId()), StandardCharsets.UTF_8));
        user.addProperty("name", options.getUser().getName());
        user.addProperty("displayName", options.getUser().getDisplayName());
        response.add("user", user);

        response.addProperty("email", email);

        response.addProperty("timeout", options.getTimeout());
        String encodedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(options.getChallenge().getValue());
        response.addProperty("challenge", encodedChallenge);
        response.addProperty("attestation", options.getAttestation().getValue());

        response.addProperty("createdAt", createdAt);
        response.addProperty("expiresAt", expiresAt);

        JsonArray pubKeyCredParams = new JsonArray();
        for (PublicKeyCredentialParameters params : options.getPubKeyCredParams()) {
            JsonObject pubKeyParam = new JsonObject();
            pubKeyParam.addProperty("alg", params.getAlg().getValue());
            pubKeyParam.addProperty("type", params.getType().getValue()); // should be always public-key
            pubKeyCredParams.add(pubKeyParam);
        }
        response.add("pubKeyCredParams", pubKeyCredParams);

        JsonArray excludeCredentials = new JsonArray();
        if (options.getExcludeCredentials() != null) {
            for (PublicKeyCredentialDescriptor exclude : options.getExcludeCredentials()) {
                JsonObject excl = new JsonObject();
                excl.addProperty("id", new String(exclude.getId()));
                JsonArray transports = new JsonArray();
                for (AuthenticatorTransport transp : exclude.getTransports()) {
                    transports.add(new JsonPrimitive(transp.getValue()));
                }
                excl.add("transport", transports);
                excludeCredentials.add(excl);
            }
        }
        response.add("excludeCredentials", excludeCredentials);

        JsonObject authenticatorSelection = new JsonObject();
        authenticatorSelection.addProperty("requireResidentKey",
                options.getAuthenticatorSelection().isRequireResidentKey() == null ? false :
                        options.getAuthenticatorSelection().isRequireResidentKey());
        authenticatorSelection.addProperty("residentKey",
                options.getAuthenticatorSelection().getResidentKey().getValue());
        authenticatorSelection.addProperty("userVerification",
                options.getAuthenticatorSelection().getUserVerification().getValue());

        response.add("authenticatorSelection", authenticatorSelection);
        return response;
    }

    public static WebauthNCredentialResponse mapStoredCredentialToResponse(WebAuthNStoredCredential credential,
                                                                           String email, String relyingPartyName) {
        WebauthNCredentialResponse response = new WebauthNCredentialResponse();
        response.email = email;
        response.webauthnCredentialId = credential.id;
        response.recipeUserId = credential.userId;
        response.relyingPartyId = credential.rpId;
        response.relyingPartyName = relyingPartyName;
        response.createdAt = credential.createdAt;
        return response;
    }

    public static JsonObject mapStoredCredentialsToResponse(List<WebAuthNStoredCredential> credentials) {
        JsonObject response = new JsonObject();
        JsonArray credentialsArray = new JsonArray();
        if(credentials != null) {
            for(WebAuthNStoredCredential credential : credentials) {
                JsonObject mappedCredential = new JsonObject();
                mappedCredential.addProperty("webauthnCredentialId", credential.id);
                mappedCredential.addProperty("recipeUserId", credential.userId);
                mappedCredential.addProperty("relyingPartyId", credential.rpId);
                mappedCredential.addProperty("createdAt", credential.createdAt);
                credentialsArray.add(mappedCredential);
            }
        }
        response.add("credentials", credentialsArray);
        return response;
    }


    public static JsonObject mapOptionsResponse(String relyingPartyId, Long timeout, String userVerification,
                                                String optionsId, Challenge challenge, Long createdAt) {
        JsonObject response = new JsonObject();
        response.addProperty("webauthnGeneratedOptionsId", optionsId);
        response.addProperty("relyingPartyId", relyingPartyId);
        response.addProperty("challenge", Base64.getUrlEncoder().withoutPadding().encodeToString(challenge.getValue()));
        response.addProperty("timeout", timeout);
        response.addProperty("userVerification", userVerification);
        response.addProperty("createdAt", createdAt);
        response.addProperty("expiresAt", createdAt + timeout);
        return response;
    }

    public static JsonObject mapOptionsResponse(WebAuthNOptions options, JsonObject response) {

        response.addProperty("webauthnGeneratedOptionsId", options.generatedOptionsId);
        response.addProperty("relyingPartyId", options.relyingPartyId);
        response.addProperty("relyingPartyName", options.relyingPartyName);
        response.addProperty("challenge", options.challenge);
        response.addProperty("timeout", options.timeout);
        response.addProperty("origin", options.origin);
        response.addProperty("email", options.userEmail);
        response.addProperty("createdAt", options.createdAt);
        response.addProperty("expiresAt", options.expiresAt);
        response.addProperty("timeout", options.timeout);

        return response;
    }
}