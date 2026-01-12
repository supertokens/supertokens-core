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

package io.supertokens.test.webauthn;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.data.extension.client.AuthenticationExtensionClientOutput;
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput;
import com.webauthn4j.test.EmulatorUtil;
import com.webauthn4j.test.client.ClientPlatform;
import com.webauthn4j.util.Base64UrlUtil;
import io.supertokens.Main;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.pluginInterface.authRecipe.exceptions.AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException;
import io.supertokens.pluginInterface.authRecipe.exceptions.InputUserIdIsNotAPrimaryUserException;
import io.supertokens.pluginInterface.authRecipe.exceptions.CannotLinkSinceRecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException;
import io.supertokens.pluginInterface.authRecipe.exceptions.CannotBecomePrimarySinceRecipeUserIdAlreadyLinkedWithPrimaryUserIdException;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.emailverification.exception.EmailAlreadyVerifiedException;
import io.supertokens.emailverification.exception.EmailVerificationInvalidTokenException;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.authRecipe.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class Utils {

    public static List<JsonObject> registerUsers(Main main, int numberOfUser) throws HttpResponseException, IOException{
        List<JsonObject> users = new ArrayList<>();
        for(int i = 0; i < numberOfUser; i++){
            JsonObject user = registerUserWithCredentials(main, "user" + i + "@example.com");
            users.add(user);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return users;
    }

    public static JsonObject registerUserWithCredentials(Main main, String email)
            throws HttpResponseException, IOException {
        ClientPlatform clientPlatform = EmulatorUtil.createClientPlatform(EmulatorUtil.FIDO_U2F_AUTHENTICATOR);

        JsonObject registerOptionsResponse = registerOptions(main, email);
        Map<String, PublicKeyCredentialCreationOptions> options = createPublicKeyCreationOptions(registerOptionsResponse);
        PublicKeyCredential<AuthenticatorAttestationResponse, RegistrationExtensionClientOutput> credential = createPasskey(clientPlatform, options.values().stream().findFirst().get());

        return signUp(main, options.keySet().stream().findFirst().get(), credential);
    }

    public static JsonObject registerCredentialForUser(Main main, String email, String recipeUserId)
            throws HttpResponseException, IOException {
        ClientPlatform clientPlatform = EmulatorUtil.createClientPlatform(EmulatorUtil.FIDO_U2F_AUTHENTICATOR);

        JsonObject registerOptionsResponse = registerOptions(main, email);
        Map<String, PublicKeyCredentialCreationOptions> options = createPublicKeyCreationOptions(registerOptionsResponse);
        PublicKeyCredential<AuthenticatorAttestationResponse, RegistrationExtensionClientOutput> credential = createPasskey(clientPlatform, options.values().stream().findFirst().get());

        return registerCredentials(main, options.keySet().stream().findFirst().get(), credential, recipeUserId);
    }

    public static JsonObject registerOptions(Main main, String email) throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email",email);
        requestBody.addProperty("relyingPartyName","supertokens.com");
        requestBody.addProperty("relyingPartyId","example.com");
        requestBody.addProperty("origin","http://example.com");
        requestBody.addProperty("timeout",10000);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/webauthn/options/register",
                requestBody, 10000, 10000, null, SemVer.v5_3.get(), null);

        return response;
    }

    public static JsonObject signInOptions(Main main) throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("relyingPartyName","supertokens.com");
        requestBody.addProperty("relyingPartyId","example.com");
        requestBody.addProperty("origin","http://example.com");
        requestBody.addProperty("timeout",10000);
        requestBody.addProperty("userVerification","preferred");
        requestBody.addProperty("userPresence",false);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/webauthn/options/signin",
                requestBody, 10000, 10000, null, SemVer.v5_3.get(), null);

        return response;
    }

    private static JsonObject signIn(Main main, String optionsId, PublicKeyCredential<AuthenticatorAssertionResponse, AuthenticationExtensionClientOutput> credential)
            throws HttpResponseException, IOException {
        JsonObject signInRequestBody = new JsonObject();
        signInRequestBody.addProperty("webauthnGeneratedOptionsId",optionsId);
        String signature = Base64UrlUtil.encodeToString(credential.getResponse().getSignature());
        String clientDataJson = Base64UrlUtil.encodeToString(credential.getAuthenticatorResponse().getClientDataJSON());
        String rawId = Base64UrlUtil.encodeToString(credential.getRawId());
        String authenticatorData = Base64UrlUtil.encodeToString(credential.getResponse().getAuthenticatorData());

        signInRequestBody.add("credential", new Gson().toJsonTree(credential));
        signInRequestBody.getAsJsonObject("credential").getAsJsonObject("response").addProperty("signature", signature);
        signInRequestBody.getAsJsonObject("credential").getAsJsonObject("response").addProperty("clientDataJSON", clientDataJson);
        signInRequestBody.getAsJsonObject("credential").getAsJsonObject("response").addProperty("authenticatorData", authenticatorData);
        signInRequestBody.getAsJsonObject("credential").addProperty("type", credential.getType());
        signInRequestBody.getAsJsonObject("credential").getAsJsonObject("response").remove("transports");
        signInRequestBody.getAsJsonObject("credential").remove("clientExtensionResults");
        signInRequestBody.getAsJsonObject("credential").addProperty("rawId", rawId);

        //System.out.println("Sign in Request body: " + new Gson().toJsonTree(signInRequestBody));

        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/webauthn/signin",
                signInRequestBody, 10000, 1000, null, SemVer.v5_3.get(), null);

        return signInResponse;
    }

    public static JsonObject registerCredentials(Main main, String optionsId,PublicKeyCredential<AuthenticatorAttestationResponse, RegistrationExtensionClientOutput> credential, String recipeUserId)
            throws HttpResponseException, IOException {

        JsonObject registerCredentialRequestBody = new JsonObject();
        registerCredentialRequestBody.addProperty("webauthnGeneratedOptionsId",optionsId);
        String attestationObject = Base64UrlUtil.encodeToString(credential.getAuthenticatorResponse().getAttestationObject());
        String clientDataJson = Base64UrlUtil.encodeToString(credential.getAuthenticatorResponse().getClientDataJSON());
        String rawId = Base64UrlUtil.encodeToString(credential.getRawId());

        registerCredentialRequestBody.add("credential", new Gson().toJsonTree(credential));
        registerCredentialRequestBody.getAsJsonObject("credential").getAsJsonObject("response").addProperty("attestationObject", attestationObject);
        registerCredentialRequestBody.getAsJsonObject("credential").getAsJsonObject("response").addProperty("clientDataJSON", clientDataJson);
        registerCredentialRequestBody.getAsJsonObject("credential").addProperty("type", credential.getType());
        registerCredentialRequestBody.getAsJsonObject("credential").getAsJsonObject("response").remove("transports");
        registerCredentialRequestBody.getAsJsonObject("credential").remove("clientExtensionResults");
        registerCredentialRequestBody.getAsJsonObject("credential").addProperty("rawId", rawId);

        registerCredentialRequestBody.addProperty("recipeUserId", recipeUserId);

        //System.out.println("Sign in Request body: " + new Gson().toJsonTree(signInRequestBody));

        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/webauthn/user/credential/register",
                registerCredentialRequestBody, 10000, 1000, null, SemVer.v5_3.get(), null);

        return signInResponse;
    }

    public static JsonObject signInWithUser(Main main, JsonObject userJson ) throws HttpResponseException, IOException {
        ClientPlatform clientPlatform = EmulatorUtil.createClientPlatform(EmulatorUtil.FIDO_U2F_AUTHENTICATOR);
        JsonObject signInOptionsResponse = signInOptions(main);
        Map<String, PublicKeyCredential<AuthenticatorAssertionResponse, AuthenticationExtensionClientOutput>> pubkeysToOptions = createPublicKeyRequestOptions(
                signInOptionsResponse, clientPlatform, userJson.get("webauthnCredentialId").getAsString());
        return signIn(main, pubkeysToOptions.keySet().stream().findFirst().get(),
                pubkeysToOptions.values().stream().findFirst().get());
    }

    public static Map<String, PublicKeyCredential<AuthenticatorAssertionResponse, AuthenticationExtensionClientOutput>> createPublicKeyRequestOptions(
            JsonObject signInOptionsResponse, ClientPlatform clientPlatform, String credentialId) {

        //System.out.println("Challenge: " + Base64UrlUtil.encodeToString(signInOptionsResponse.get("challenge").getAsString().getBytes(StandardCharsets.UTF_8)));

        PublicKeyCredentialDescriptor credentialDescriptor = new PublicKeyCredentialDescriptor(PublicKeyCredentialType.PUBLIC_KEY,
                Base64UrlUtil.decode(credentialId.getBytes(StandardCharsets.UTF_8)), null);
        Challenge challenge = new DefaultChallenge(signInOptionsResponse.get("challenge").getAsString());
        PublicKeyCredentialRequestOptions requestOptions = new PublicKeyCredentialRequestOptions(challenge,
                10000L,
                signInOptionsResponse.get("relyingPartyId").getAsString(),
                List.of(credentialDescriptor),
                UserVerificationRequirement.create(signInOptionsResponse.get("userVerification").getAsString()),
                null, null );

        PublicKeyCredential<AuthenticatorAssertionResponse,AuthenticationExtensionClientOutput> publicKeyCredential = clientPlatform.get(requestOptions);
        return Map.of(signInOptionsResponse.get("webauthnGeneratedOptionsId").getAsString(), publicKeyCredential);
    }

    private static JsonObject signUp(Main main, String optionsId, PublicKeyCredential<AuthenticatorAttestationResponse, RegistrationExtensionClientOutput> credential)
            throws HttpResponseException, IOException {
        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("webauthnGeneratedOptionsId",optionsId);
        String attestationObject = Base64UrlUtil.encodeToString(credential.getAuthenticatorResponse().getAttestationObject());
        String clientDataJson = Base64UrlUtil.encodeToString(credential.getAuthenticatorResponse().getClientDataJSON());
        String rawId = Base64UrlUtil.encodeToString(credential.getRawId());

        signUpRequestBody.add("credential", new Gson().toJsonTree(credential));
        signUpRequestBody.getAsJsonObject("credential").getAsJsonObject("response").addProperty("attestationObject", attestationObject);
        signUpRequestBody.getAsJsonObject("credential").getAsJsonObject("response").addProperty("clientDataJSON", clientDataJson);
        signUpRequestBody.getAsJsonObject("credential").addProperty("type", credential.getType());
        signUpRequestBody.getAsJsonObject("credential").getAsJsonObject("response").remove("transports");
        signUpRequestBody.getAsJsonObject("credential").remove("clientExtensionResults");
        signUpRequestBody.getAsJsonObject("credential").addProperty("rawId", rawId);

        JsonObject signupResponse = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/webauthn/signup",
                signUpRequestBody, 10000, 1000, null, SemVer.v5_3.get(), null);

        return signupResponse;
    }

    public static Map<String, PublicKeyCredentialCreationOptions> createPublicKeyCreationOptions(JsonObject registerResponse){
        PublicKeyCredentialRpEntity relyingPartyEntity = new PublicKeyCredentialRpEntity(registerResponse.getAsJsonObject("rp").get("id").getAsString(),
                registerResponse.getAsJsonObject("rp").get("name").getAsString());

        JsonObject userJson = registerResponse.getAsJsonObject("user");
        PublicKeyCredentialUserEntity userEntity = new PublicKeyCredentialUserEntity(
                userJson.get("id").getAsString().getBytes(StandardCharsets.UTF_8), userJson.get("name").getAsString(), userJson.get("displayName").getAsString());

        Challenge challenge = new DefaultChallenge(registerResponse.get("challenge").getAsString());

        List<PublicKeyCredentialParameters> credentialParameters = new ArrayList<>();
        for (int i = 0; i < registerResponse.getAsJsonArray("pubKeyCredParams").size(); i++) {
            Long supportedAlgoId = registerResponse.getAsJsonArray("pubKeyCredParams").get(i).getAsJsonObject().get("alg").getAsLong();
            COSEAlgorithmIdentifier algorithmIdentifier = COSEAlgorithmIdentifier.create(
                    supportedAlgoId);
            PublicKeyCredentialParameters param = new PublicKeyCredentialParameters(
                    PublicKeyCredentialType.PUBLIC_KEY, algorithmIdentifier);
            credentialParameters.add(param);
        }

        AuthenticatorSelectionCriteria authenticatorSelectionCriteria = new AuthenticatorSelectionCriteria(null,
                registerResponse.getAsJsonObject("authenticatorSelection").get("residentKey").getAsString().equalsIgnoreCase("required"),
                ResidentKeyRequirement.create(registerResponse.getAsJsonObject("authenticatorSelection").get("residentKey").getAsString()), UserVerificationRequirement.create(registerResponse.getAsJsonObject("authenticatorSelection").get("userVerification").getAsString()));

        AttestationConveyancePreference attestationConveyancePreference = AttestationConveyancePreference.create(
                registerResponse.get("attestation").getAsString());

        PublicKeyCredentialCreationOptions options = new PublicKeyCredentialCreationOptions(relyingPartyEntity,
                userEntity, challenge, credentialParameters, 10000L, null, authenticatorSelectionCriteria,
                null, attestationConveyancePreference, null);

        Map<String, PublicKeyCredentialCreationOptions> optionsMap = Map.of(registerResponse.get("webauthnGeneratedOptionsId").getAsString(), options);
        return optionsMap;
    }

    public static PublicKeyCredential<AuthenticatorAttestationResponse, RegistrationExtensionClientOutput> createPasskey(
            ClientPlatform clientPlatform, PublicKeyCredentialCreationOptions pkCreationOptions) {
        return clientPlatform.create(pkCreationOptions);
    }

    public static JsonObject generateRecoverAccountTokenForEmail(Main main, String email, String userId)
            throws HttpResponseException, IOException {
        JsonObject generateTokenRequestBody = new JsonObject();
        generateTokenRequestBody.addProperty("email", email);
        generateTokenRequestBody.addProperty("userId", userId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/webauthn/user/recover/token",
                generateTokenRequestBody, 10000, 1000, null, SemVer.v5_3.get(), null);

        return response;
    }

    public static JsonObject consumeToken(Main main, String token)
            throws HttpResponseException, IOException {
        JsonObject generateTokenRequestBody = new JsonObject();
        generateTokenRequestBody.addProperty("token", token);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/webauthn/user/recover/token/consume",
                generateTokenRequestBody, 10000, 1000, null, SemVer.v5_3.get(), null);

        return response;
    }

    public static JsonObject getUserFromToken(Main main, String token)
            throws HttpResponseException, IOException {

        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/recipe/webauthn/user/recover",
                Map.of("token", token), 10000, 1000, null, SemVer.v5_3.get(), null);

        return response;
    }

    public static JsonObject updateEmail(Main main, String recipeUserId, String newEmail)
            throws HttpResponseException, IOException {
        JsonObject generateTokenRequestBody = new JsonObject();
        generateTokenRequestBody.addProperty("recipeUserId", recipeUserId);
        generateTokenRequestBody.addProperty("email", newEmail);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                "http://localhost:3567/recipe/webauthn/user/email",
                generateTokenRequestBody, 10000, 1000, null, SemVer.v5_3.get(), null);

        return response;
    }

    public static void createUserIdMapping(Main main, String supertokensId, String externalUserId)
            throws Exception {
        String externalUserIdInfo = "whatever";
        UserIdMapping userIdMapping = new io.supertokens.pluginInterface.useridmapping.UserIdMapping(
                supertokensId,
                externalUserId, externalUserIdInfo);
        createUserIdMappingAndCheckThatItExists(main, userIdMapping);
    }

    private static void createUserIdMappingAndCheckThatItExists(Main main, UserIdMapping userIdMapping)
            throws Exception {
        io.supertokens.useridmapping.UserIdMapping.createUserIdMapping(main, userIdMapping.superTokensUserId,
                userIdMapping.externalUserId, userIdMapping.externalUserIdInfo, false);
        // retrieve mapping and validate
        UserIdMapping retrievedMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(main,
                userIdMapping.superTokensUserId, UserIdType.SUPERTOKENS);
        assertEquals(userIdMapping, retrievedMapping);
    }

    public static List<AuthRecipeUserInfo> createEmailPasswordUsers(TestingProcessManager.TestingProcess process, int noUsers, boolean makePrimary)
            throws DuplicateEmailException, StorageQueryException,
            AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            CannotBecomePrimarySinceRecipeUserIdAlreadyLinkedWithPrimaryUserIdException, FeatureNotEnabledException,
            TenantOrAppNotFoundException, UnknownUserIdException {
        return createEmailPasswordUsers(process, noUsers, makePrimary, 0);
    }

    public static List<AuthRecipeUserInfo> createEmailPasswordUsers(TestingProcessManager.TestingProcess process, int noUsers, boolean makePrimary, int emailIndexStart)
            throws DuplicateEmailException, StorageQueryException,
            AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            CannotBecomePrimarySinceRecipeUserIdAlreadyLinkedWithPrimaryUserIdException, FeatureNotEnabledException,
            TenantOrAppNotFoundException, UnknownUserIdException {

        List<AuthRecipeUserInfo> epUsers = new ArrayList<>();
        for(int i = 0; i < noUsers; i++){
            AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "user" + (emailIndexStart + i) + "@example.com", "password");
            if(makePrimary) {
                makePrimaryUserFrom(process, user.getSupertokensUserId());
            }
            epUsers.add(user);
        }
        return epUsers;
    }

    public static void makePrimaryUserFrom(TestingProcessManager.TestingProcess process, String supertokensUserId)
            throws StorageQueryException, AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException,
            CannotBecomePrimarySinceRecipeUserIdAlreadyLinkedWithPrimaryUserIdException, UnknownUserIdException, TenantOrAppNotFoundException,
            FeatureNotEnabledException {
        AuthRecipe.createPrimaryUser(process.getProcess(),
                process.getAppForTesting().toAppIdentifier(), StorageLayer.getStorage(process.getProcess()), supertokensUserId);
    }

    public static void verifyEmailFor(Main main, String userId, String emailAddress)
            throws EmailAlreadyVerifiedException, StorageQueryException, InvalidKeySpecException,
            NoSuchAlgorithmException, EmailVerificationInvalidTokenException, StorageTransactionLogicException {
        String token = EmailVerification.generateEmailVerificationToken(main, userId, emailAddress);
        EmailVerification.verifyEmail(main, token);
    }

    public static void linkAccounts(Main main, List<String> primaryUserIds, List<String> recipeUserIds)
            throws AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException, InputUserIdIsNotAPrimaryUserException,
            StorageQueryException, CannotLinkSinceRecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException,
            FeatureNotEnabledException, UnknownUserIdException {
        for(int i = 0; i < primaryUserIds.size(); i++){
            AuthRecipe.LinkAccountsResult result = AuthRecipe.linkAccounts(main,
                    recipeUserIds.get(i),
                    primaryUserIds.get(i));

            assertFalse(result.wasAlreadyLinked);
        }
    }

    public static JsonObject listCredentials(Main main, String userId)
            throws HttpResponseException, IOException {
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/recipe/webauthn/user/credential/list",
                Map.of("recipeUserId", userId), 10000, 1000, null, SemVer.v5_3.get(), null);

        return response;
    }

    public static JsonObject removeCredential(Main main, String userId, String credentialId)
            throws HttpResponseException, IOException {
        JsonObject response = HttpRequestForTesting.sendJsonDELETERequestWithQueryParams(main, "",
                "http://localhost:3567/recipe/webauthn/user/credential/remove",
                Map.of("recipeUserId", userId, "webauthnCredentialId", credentialId), 10000, 1000, null, SemVer.v5_3.get(), null);

        return response;
    }

    public static JsonObject getCredential(Main main, String userId, String credentialId)
            throws HttpResponseException, IOException {
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/recipe/webauthn/user/credential/",
                Map.of("recipeUserId", userId, "webauthnCredentialId", credentialId), 10000, 1000, null, SemVer.v5_3.get(), null);

        return response;
    }

}
