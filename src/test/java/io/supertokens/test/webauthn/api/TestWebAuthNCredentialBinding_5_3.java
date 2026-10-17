/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.webauthn.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.webauthn4j.converter.AttestationObjectConverter;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.AuthenticatorDataConverter;
import com.webauthn4j.converter.CollectedClientDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.AuthenticatorAttestationResponse;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.client.ClientDataType;
import com.webauthn4j.data.client.CollectedClientData;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionAuthenticatorOutput;
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput;
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput;
import com.webauthn4j.test.EmulatorUtil;
import com.webauthn4j.test.client.ClientPlatform;
import com.webauthn4j.util.Base64UrlUtil;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.webauthn.AccountRecoveryTokenInfo;
import io.supertokens.pluginInterface.webauthn.WebAuthNStorage;
import io.supertokens.pluginInterface.webauthn.WebAuthNStoredCredential;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.multitenant.api.TestMultitenancyAPIHelper;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * WebAuthn must only trust identifiers it verified: the stored credential id comes from the verified attestation,
 * sign in returns the owner of the credential whose key verified the assertion, register options do not depend on
 * whether an account exists, and account recovery only works for members of the requested tenant.
 */
public class TestWebAuthNCredentialBinding_5_3 {

    private static final String RP_ID = "example.com";
    private static final String ORIGIN = "http://example.com";
    private static final String OTHER_RP_ID = "aaa.example.com";
    private static final String OTHER_ORIGIN = "http://aaa.example.com";

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    private TestingProcessManager.TestingProcess startProcess() throws InterruptedException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        return process;
    }

    private static void stop(TestingProcessManager.TestingProcess process) throws InterruptedException {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testIssue18_registrationRejectsMismatchedCredentialId() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        Main main = process.getProcess();
        TenantIdentifier tenant = process.getAppForTesting();

        JsonObject victim = io.supertokens.test.webauthn.Utils.registerUserWithCredentials(main, "victim@example.com");
        assertEquals("OK", victim.get("status").getAsString());
        String victimCredentialId = victim.get("webauthnCredentialId").getAsString();

        // sign up: claim the victim's credential id while presenting an attestation for a different credential
        for (boolean alsoRawId : new boolean[]{false, true}) {
            ClientPlatform authenticator = EmulatorUtil.createClientPlatform(EmulatorUtil.FIDO_U2F_AUTHENTICATOR);
            Map.Entry<String, JsonObject> created = createEmulatedPasskey(main, tenant, "attacker@example.com",
                    authenticator);
            JsonObject credential = created.getValue();
            credential.addProperty("id", victimCredentialId);
            if (alsoRawId) {
                credential.addProperty("rawId", victimCredentialId);
            }
            JsonObject resp = signUp(main, tenant, created.getKey(), credential);
            assertEquals(resp.toString(), "INVALID_CREDENTIALS_ERROR", resp.get("status").getAsString());
        }

        // credential register for an existing user: same check
        JsonObject attacker = io.supertokens.test.webauthn.Utils.registerUserWithCredentials(main,
                "attacker@example.com");
        assertEquals("OK", attacker.get("status").getAsString());
        ClientPlatform authenticator = EmulatorUtil.createClientPlatform(EmulatorUtil.FIDO_U2F_AUTHENTICATOR);
        Map.Entry<String, JsonObject> created = createEmulatedPasskey(main, tenant, "attacker@example.com",
                authenticator);
        JsonObject credential = created.getValue();
        String realId = credential.get("id").getAsString();
        credential.addProperty("id", victimCredentialId);
        JsonObject registerResp = registerCredential(main, tenant, created.getKey(), credential,
                attacker.get("recipeUserId").getAsString());
        assertEquals(registerResp.toString(), "INVALID_CREDENTIALS_ERROR", registerResp.get("status").getAsString());

        // control: the untampered credential registers under its attested id
        credential.addProperty("id", realId);
        registerResp = registerCredential(main, tenant, created.getKey(), credential,
                attacker.get("recipeUserId").getAsString());
        assertEquals(registerResp.toString(), "OK", registerResp.get("status").getAsString());
        assertEquals(realId, registerResp.get("webauthnCredentialId").getAsString());

        stop(process);
    }

    @Test
    public void testIssue18_credentialIdCannotBeReusedAcrossRelyingParties() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        Main main = process.getProcess();
        TenantIdentifier tenant = process.getAppForTesting();

        SoftwareAuthenticator victimKey = new SoftwareAuthenticator(randomCredentialId());
        JsonObject victimOptions = registerOptions(main, tenant, "victim@example.com", RP_ID, ORIGIN);
        JsonObject victim = signUp(main, tenant, victimOptions.get("webauthnGeneratedOptionsId").getAsString(),
                victimKey.register(victimOptions, ORIGIN));
        assertEquals(victim.toString(), "OK", victim.get("status").getAsString());
        String victimCredentialId = victim.get("webauthnCredentialId").getAsString();

        // the attacker's own key, under a second relying party id, claiming the victim's credential id
        SoftwareAuthenticator attackerKey = new SoftwareAuthenticator(Base64UrlUtil.decode(victimCredentialId));
        JsonObject attackerOptions = registerOptions(main, tenant, "attacker@example.com", OTHER_RP_ID, OTHER_ORIGIN);
        JsonObject attackerSignUp = signUp(main, tenant,
                attackerOptions.get("webauthnGeneratedOptionsId").getAsString(),
                attackerKey.register(attackerOptions, OTHER_ORIGIN));
        assertNotEquals(attackerSignUp.toString(), "OK", attackerSignUp.get("status").getAsString());

        // same through credential register for an existing attacker account
        SoftwareAuthenticator attackerOwnKey = new SoftwareAuthenticator(randomCredentialId());
        attackerOptions = registerOptions(main, tenant, "attacker@example.com", OTHER_RP_ID, OTHER_ORIGIN);
        JsonObject attacker = signUp(main, tenant, attackerOptions.get("webauthnGeneratedOptionsId").getAsString(),
                attackerOwnKey.register(attackerOptions, OTHER_ORIGIN));
        assertEquals(attacker.toString(), "OK", attacker.get("status").getAsString());
        attackerOptions = registerOptions(main, tenant, "attacker@example.com", OTHER_RP_ID, OTHER_ORIGIN);
        JsonObject registerResp = registerCredential(main, tenant,
                attackerOptions.get("webauthnGeneratedOptionsId").getAsString(),
                attackerKey.register(attackerOptions, OTHER_ORIGIN), attacker.get("recipeUserId").getAsString());
        assertEquals(registerResp.toString(), "CREDENTIAL_ALREADY_EXISTS_ERROR",
                registerResp.get("status").getAsString());

        // the victim still signs in
        JsonObject victimSignIn = signIn(main, tenant, victimKey, RP_ID, ORIGIN, victimCredentialId);
        assertEquals(victimSignIn.toString(), "OK", victimSignIn.get("status").getAsString());
        assertEquals(victim.get("user").getAsJsonObject().get("id").getAsString(),
                victimSignIn.get("user").getAsJsonObject().get("id").getAsString());

        stop(process);
    }

    @Test
    public void testIssue18_signInReturnsOwnerOfVerifyingCredential() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        Main main = process.getProcess();
        TenantIdentifier tenant = process.getAppForTesting();

        SoftwareAuthenticator victimKey = new SoftwareAuthenticator(randomCredentialId());
        JsonObject victimOptions = registerOptions(main, tenant, "victim@example.com", RP_ID, ORIGIN);
        JsonObject victim = signUp(main, tenant, victimOptions.get("webauthnGeneratedOptionsId").getAsString(),
                victimKey.register(victimOptions, ORIGIN));
        assertEquals(victim.toString(), "OK", victim.get("status").getAsString());
        String victimUserId = victim.get("user").getAsJsonObject().get("id").getAsString();
        String victimCredentialId = victim.get("webauthnCredentialId").getAsString();

        SoftwareAuthenticator attackerKey = new SoftwareAuthenticator(randomCredentialId());
        JsonObject attackerOptions = registerOptions(main, tenant, "attacker@example.com", OTHER_RP_ID, OTHER_ORIGIN);
        JsonObject attacker = signUp(main, tenant, attackerOptions.get("webauthnGeneratedOptionsId").getAsString(),
                attackerKey.register(attackerOptions, OTHER_ORIGIN));
        assertEquals(attacker.toString(), "OK", attacker.get("status").getAsString());
        String attackerUserId = attacker.get("user").getAsJsonObject().get("id").getAsString();

        // A row with the victim's credential id but the attacker's key, under the attacker's relying party id, as
        // registrations accepted before credential ids were bound to the attestation could have stored.
        WebAuthNStoredCredential duplicate = new WebAuthNStoredCredential();
        duplicate.id = victimCredentialId;
        duplicate.appId = tenant.getAppId();
        duplicate.rpId = OTHER_RP_ID;
        duplicate.userId = attackerUserId;
        duplicate.counter = 0;
        duplicate.publicKey = attackerKey.attestedCredentialDataBytes(Base64UrlUtil.decode(victimCredentialId));
        duplicate.transports = "[]";
        duplicate.createdAt = System.currentTimeMillis();
        duplicate.updatedAt = duplicate.createdAt;
        StorageUtils.getWebAuthNStorage(StorageLayer.getStorage(tenant, main)).saveCredentials(tenant, duplicate);

        // The attacker signs with their own key, presenting the victim's credential id. Whatever row the
        // credential lookup returns, the signed-in user must be the owner of the key that verified the assertion.
        // Try with both physical row orders (re-inserting the victim's row moves it behind the attacker's).
        for (int attempt = 0; attempt < 2; attempt++) {
            if (attempt == 1) {
                WebAuthNStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(StorageLayer.getStorage(tenant, main));
                WebAuthNStoredCredential victimRow = webAuthNStorage.loadCredentialByIdForUser(tenant,
                        victimCredentialId, victimUserId);
                assertNotNull(victimRow);
                webAuthNStorage.removeCredential(tenant, victimUserId, victimCredentialId);
                webAuthNStorage.saveCredentials(tenant, victimRow);
            }
            JsonObject signInResp = signIn(main, tenant, attackerKey, OTHER_RP_ID, OTHER_ORIGIN, victimCredentialId);
            if ("OK".equals(signInResp.get("status").getAsString())) {
                assertEquals(signInResp.toString(), attackerUserId,
                        signInResp.get("user").getAsJsonObject().get("id").getAsString());
                assertEquals(signInResp.toString(), attackerUserId, signInResp.get("recipeUserId").getAsString());
            }
            assertFalse(signInResp.toString(), signInResp.toString().contains(victimUserId));
        }

        stop(process);
    }

    @Test
    public void testIssue20_registerOptionsDoesNotRevealUser() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        Main main = process.getProcess();
        TenantIdentifier tenant = process.getAppForTesting();

        AuthRecipeUserInfo existing = EmailPassword.signUp(main, "existing@example.com", "password123");
        JsonObject webauthnUser = io.supertokens.test.webauthn.Utils.registerUserWithCredentials(main,
                "passkey@example.com");
        assertEquals("OK", webauthnUser.get("status").getAsString());

        String[][] cases = {
                {"existing@example.com", existing.getSupertokensUserId()},
                {"passkey@example.com", webauthnUser.get("user").getAsJsonObject().get("id").getAsString()},
                {"nobody@example.com", null}};
        for (String[] c : cases) {
            JsonObject first = registerOptions(main, tenant, c[0], RP_ID, ORIGIN);
            JsonObject second = registerOptions(main, tenant, c[0], RP_ID, ORIGIN);
            for (JsonObject resp : new JsonObject[]{first, second}) {
                assertEquals("OK", resp.get("status").getAsString());
                String handle = new String(
                        Base64.getUrlDecoder().decode(resp.get("user").getAsJsonObject().get("id").getAsString()),
                        StandardCharsets.UTF_8);
                assertNotEquals(resp.get("webauthnGeneratedOptionsId").getAsString(), handle);
                if (c[1] != null) {
                    assertNotEquals(c[1], handle);
                    assertFalse(resp.toString().contains(c[1]));
                    assertFalse(resp.toString().contains(
                            Base64.getUrlEncoder().withoutPadding().encodeToString(
                                    c[1].getBytes(StandardCharsets.UTF_8))));
                }
            }
            // not a stable per-account value either
            assertNotEquals(first.get("user").getAsJsonObject().get("id").getAsString(),
                    second.get("user").getAsJsonObject().get("id").getAsString());
        }

        stop(process);
    }

    @Test
    public void testRecoverAccountTokenRequiresTenantMembership() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        Main main = process.getProcess();
        TenantIdentifier publicTenant = process.getAppForTesting();
        TenantIdentifier t2 = new TenantIdentifier(null, publicTenant.getAppId(), "t2");
        TestMultitenancyAPIHelper.createTenant(main, publicTenant, "t2",
                true, false, false, new JsonObject());

        String email = "member-of-t2@example.com";
        AuthRecipeUserInfo user = EmailPassword.signUp(t2, StorageLayer.getStorage(t2, main), main, email,
                "password123");
        String userId = user.getSupertokensUserId();

        // token generation for a user who is not a member of the requested tenant
        JsonObject crossTenant = generateRecoverToken(main, publicTenant, email, userId);
        assertEquals(crossTenant.toString(), "UNKNOWN_USER_ID_ERROR", crossTenant.get("status").getAsString());

        // a token stored for a tenant the user is not a member of is neither readable nor consumable there
        String token = "token-for-a-non-member-" + java.util.UUID.randomUUID();
        StorageUtils.getWebAuthNStorage(StorageLayer.getStorage(publicTenant, main)).addRecoverAccountToken(
                publicTenant, new AccountRecoveryTokenInfo(userId, email, io.supertokens.utils.Utils.hashSHA256(token),
                        System.currentTimeMillis() + 3600_000L));
        JsonObject userForToken = getUserFromRecoverToken(main, publicTenant, token);
        assertEquals(userForToken.toString(), "RECOVER_ACCOUNT_TOKEN_INVALID_ERROR",
                userForToken.get("status").getAsString());
        JsonObject consumed = consumeRecoverToken(main, publicTenant, token);
        assertEquals(consumed.toString(), "RECOVER_ACCOUNT_TOKEN_INVALID_ERROR", consumed.get("status").getAsString());

        // control: the user's own tenant
        JsonObject sameTenant = generateRecoverToken(main, t2, email, userId);
        assertEquals(sameTenant.toString(), "OK", sameTenant.get("status").getAsString());
        String ownToken = sameTenant.get("token").getAsString();
        userForToken = getUserFromRecoverToken(main, t2, ownToken);
        assertEquals(userForToken.toString(), "OK", userForToken.get("status").getAsString());
        assertEquals(userId, userForToken.get("user").getAsJsonObject().get("id").getAsString());
        consumed = consumeRecoverToken(main, t2, ownToken);
        assertEquals(consumed.toString(), "OK", consumed.get("status").getAsString());
        assertEquals(userId, consumed.get("userId").getAsString());
        assertEquals(email, consumed.get("email").getAsString());

        stop(process);
    }

    // --- a software authenticator with a caller-chosen credential id ("none" attestation, ES256) ---

    private static byte[] randomCredentialId() {
        byte[] id = new byte[32];
        new SecureRandom().nextBytes(id);
        return id;
    }

    private static class SoftwareAuthenticator {
        private final ObjectConverter objectConverter = new ObjectConverter();
        private final KeyPair keyPair;
        private final byte[] credentialId;
        private int counter = 0;

        SoftwareAuthenticator(byte[] credentialId) throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            this.keyPair = generator.generateKeyPair();
            this.credentialId = credentialId;
        }

        private AttestedCredentialData attestedCredentialData(byte[] id) {
            return new AttestedCredentialData(AAGUID.ZERO, id,
                    EC2COSEKey.create((ECPublicKey) keyPair.getPublic(), COSEAlgorithmIdentifier.ES256));
        }

        byte[] attestedCredentialDataBytes(byte[] id) {
            return new AttestedCredentialDataConverter(objectConverter).convert(attestedCredentialData(id));
        }

        JsonObject register(JsonObject registerOptions, String origin) throws Exception {
            String rpId = registerOptions.getAsJsonObject("rp").get("id").getAsString();
            byte flags = (byte) (AuthenticatorData.BIT_UP | AuthenticatorData.BIT_UV | AuthenticatorData.BIT_AT);
            AuthenticatorData<RegistrationExtensionAuthenticatorOutput> authenticatorData = new AuthenticatorData<>(
                    sha256(rpId.getBytes(StandardCharsets.UTF_8)), flags, 0, attestedCredentialData(credentialId));
            byte[] attestationObject = new AttestationObjectConverter(objectConverter).convertToBytes(
                    new AttestationObject(authenticatorData, new NoneAttestationStatement()));
            byte[] clientData = new CollectedClientDataConverter(objectConverter).convertToBytes(
                    new CollectedClientData(ClientDataType.WEBAUTHN_CREATE,
                            new DefaultChallenge(registerOptions.get("challenge").getAsString()), new Origin(origin),
                            null));

            JsonObject response = new JsonObject();
            response.addProperty("attestationObject", Base64UrlUtil.encodeToString(attestationObject));
            response.addProperty("clientDataJSON", Base64UrlUtil.encodeToString(clientData));
            JsonObject credential = new JsonObject();
            credential.addProperty("id", Base64UrlUtil.encodeToString(credentialId));
            credential.addProperty("rawId", Base64UrlUtil.encodeToString(credentialId));
            credential.addProperty("type", "public-key");
            credential.add("response", response);
            return credential;
        }

        JsonObject assertion(JsonObject signInOptions, String origin, String claimedCredentialId) throws Exception {
            String rpId = signInOptions.get("relyingPartyId").getAsString();
            counter++;
            byte flags = (byte) (AuthenticatorData.BIT_UP | AuthenticatorData.BIT_UV);
            byte[] authenticatorData = new AuthenticatorDataConverter(objectConverter).convert(
                    new AuthenticatorData<AuthenticationExtensionAuthenticatorOutput>(
                            sha256(rpId.getBytes(StandardCharsets.UTF_8)), flags, counter));
            byte[] clientData = new CollectedClientDataConverter(objectConverter).convertToBytes(
                    new CollectedClientData(ClientDataType.WEBAUTHN_GET,
                            new DefaultChallenge(signInOptions.get("challenge").getAsString()), new Origin(origin),
                            null));
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(ByteBuffer.allocate(authenticatorData.length + 32).put(authenticatorData)
                    .put(sha256(clientData)).array());

            JsonObject response = new JsonObject();
            response.addProperty("authenticatorData", Base64UrlUtil.encodeToString(authenticatorData));
            response.addProperty("clientDataJSON", Base64UrlUtil.encodeToString(clientData));
            response.addProperty("signature", Base64UrlUtil.encodeToString(signature.sign()));
            JsonObject credential = new JsonObject();
            credential.addProperty("id", claimedCredentialId);
            credential.addProperty("rawId", claimedCredentialId);
            credential.addProperty("type", "public-key");
            credential.add("response", response);
            return credential;
        }

        private static byte[] sha256(byte[] data) throws Exception {
            return MessageDigest.getInstance("SHA-256").digest(data);
        }
    }

    // --- tenant-aware HTTP helpers ---

    private static Map.Entry<String, JsonObject> createEmulatedPasskey(Main main, TenantIdentifier tenant,
                                                                       String email, ClientPlatform authenticator)
            throws HttpResponseException, IOException {
        JsonObject options = registerOptions(main, tenant, email, RP_ID, ORIGIN);
        Map<String, PublicKeyCredentialCreationOptions> creationOptions =
                io.supertokens.test.webauthn.Utils.createPublicKeyCreationOptions(options);
        PublicKeyCredential<AuthenticatorAttestationResponse, RegistrationExtensionClientOutput> passkey =
                io.supertokens.test.webauthn.Utils.createPasskey(authenticator,
                        creationOptions.values().iterator().next());
        JsonObject credential = new Gson().toJsonTree(passkey).getAsJsonObject();
        credential.getAsJsonObject("response").addProperty("attestationObject",
                Base64UrlUtil.encodeToString(passkey.getAuthenticatorResponse().getAttestationObject()));
        credential.getAsJsonObject("response").addProperty("clientDataJSON",
                Base64UrlUtil.encodeToString(passkey.getAuthenticatorResponse().getClientDataJSON()));
        credential.addProperty("type", passkey.getType());
        credential.getAsJsonObject("response").remove("transports");
        credential.remove("clientExtensionResults");
        credential.addProperty("rawId", Base64UrlUtil.encodeToString(passkey.getRawId()));
        return Map.entry(creationOptions.keySet().iterator().next(), credential);
    }

    private static JsonObject registerOptions(Main main, TenantIdentifier tenant, String email, String rpId,
                                              String origin) throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("relyingPartyName", "supertokens.com");
        body.addProperty("relyingPartyId", rpId);
        body.addProperty("origin", origin);
        body.addProperty("timeout", 10000);
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenant, "/recipe/webauthn/options/register"),
                body, 10000, 10000, null, SemVer.v5_3.get(), "webauthn");
    }

    private static JsonObject signUp(Main main, TenantIdentifier tenant, String optionsId, JsonObject credential)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("webauthnGeneratedOptionsId", optionsId);
        body.add("credential", credential);
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenant, "/recipe/webauthn/signup"),
                body, 10000, 10000, null, SemVer.v5_3.get(), "webauthn");
    }

    private static JsonObject registerCredential(Main main, TenantIdentifier tenant, String optionsId,
                                                 JsonObject credential, String recipeUserId)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("webauthnGeneratedOptionsId", optionsId);
        body.add("credential", credential);
        body.addProperty("recipeUserId", recipeUserId);
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenant, "/recipe/webauthn/user/credential/register"),
                body, 10000, 10000, null, SemVer.v5_3.get(), "webauthn");
    }

    private static JsonObject signIn(Main main, TenantIdentifier tenant, SoftwareAuthenticator authenticator,
                                     String rpId, String origin, String claimedCredentialId) throws Exception {
        JsonObject optionsBody = new JsonObject();
        optionsBody.addProperty("relyingPartyName", "supertokens.com");
        optionsBody.addProperty("relyingPartyId", rpId);
        optionsBody.addProperty("origin", origin);
        optionsBody.addProperty("timeout", 10000);
        optionsBody.addProperty("userVerification", "preferred");
        optionsBody.addProperty("userPresence", false);
        JsonObject options = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenant, "/recipe/webauthn/options/signin"),
                optionsBody, 10000, 10000, null, SemVer.v5_3.get(), "webauthn");
        assertEquals(options.toString(), "OK", options.get("status").getAsString());

        JsonObject body = new JsonObject();
        body.addProperty("webauthnGeneratedOptionsId", options.get("webauthnGeneratedOptionsId").getAsString());
        body.add("credential", authenticator.assertion(options, origin, claimedCredentialId));
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenant, "/recipe/webauthn/signin"),
                body, 10000, 10000, null, SemVer.v5_3.get(), "webauthn");
    }

    private static JsonObject generateRecoverToken(Main main, TenantIdentifier tenant, String email, String userId)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("userId", userId);
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenant, "/recipe/webauthn/user/recover/token"),
                body, 10000, 10000, null, SemVer.v5_3.get(), "webauthn");
    }

    private static JsonObject getUserFromRecoverToken(Main main, TenantIdentifier tenant, String token)
            throws HttpResponseException, IOException {
        return HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenant, "/recipe/webauthn/user/recover"),
                Map.of("token", token), 10000, 10000, null, SemVer.v5_3.get(), "webauthn");
    }

    private static JsonObject consumeRecoverToken(Main main, TenantIdentifier tenant, String token)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("token", token);
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenant, "/recipe/webauthn/user/recover/token/consume"),
                body, 10000, 10000, null, SemVer.v5_3.get(), "webauthn");
    }
}
