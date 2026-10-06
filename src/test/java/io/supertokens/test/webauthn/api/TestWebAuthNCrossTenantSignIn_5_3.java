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
import com.webauthn4j.data.AuthenticatorAssertionResponse;
import com.webauthn4j.data.AuthenticatorAttestationResponse;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.extension.client.AuthenticationExtensionClientOutput;
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput;
import com.webauthn4j.test.EmulatorUtil;
import com.webauthn4j.test.client.ClientPlatform;
import com.webauthn4j.util.Base64UrlUtil;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
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
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

/**
 * Regression test for GHSA-6v34-w48w-44rc: WebAuthn sign-in accepted a passkey across tenant boundaries.
 * A passkey registered only in one tenant was accepted at another tenant's sign-in endpoint under the same
 * app, because the sign-in transaction resolved the credential owner app-scoped instead of tenant-scoped.
 *
 * The test registers a passkey in the public tenant, then replays the same authenticator against a second
 * tenant's sign-in (a legitimate second-tenant challenge, signed by the tenant-1 authenticator). Before the
 * fix this returned OK with the tenant-1 user; after the fix the second tenant rejects it. A same-tenant
 * control confirms legitimate sign-in still succeeds.
 */
public class TestWebAuthNCrossTenantSignIn_5_3 {

    private static final String RP_ID = "example.com";
    private static final String ORIGIN = "http://example.com";
    private static final String EMAIL = "attacker@tenant1.example.com";

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

    @Test
    public void passkeyFromOneTenantIsRejectedAtAnotherTenant() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Main main = process.getProcess();
        TenantIdentifier t1 = TenantIdentifier.BASE_TENANT; // public
        TenantIdentifier t2 = new TenantIdentifier(null, null, "t2");

        // A second tenant under the same app; leaving firstFactors unset keeps all first factors
        // (including webauthn) available.
        TestMultitenancyAPIHelper.createTenant(main, TenantIdentifier.BASE_TENANT, "t2",
                true, false, false, new JsonObject());

        // One authenticator, used for both the tenant-1 registration and the tenant-2 sign-in attempt.
        ClientPlatform authenticator = EmulatorUtil.createClientPlatform(EmulatorUtil.FIDO_U2F_AUTHENTICATOR);

        // Register a passkey in tenant 1 (public) only.
        JsonObject registerOptions = registerOptions(main, t1, EMAIL);
        Map<String, PublicKeyCredentialCreationOptions> creationOptions =
                io.supertokens.test.webauthn.Utils.createPublicKeyCreationOptions(registerOptions);
        String registerOptionsId = creationOptions.keySet().iterator().next();
        PublicKeyCredential<AuthenticatorAttestationResponse, RegistrationExtensionClientOutput> passkey =
                io.supertokens.test.webauthn.Utils.createPasskey(authenticator, creationOptions.values().iterator().next());
        JsonObject signUpResp = signUp(main, t1, registerOptionsId, passkey);
        assertEquals("OK", signUpResp.get("status").getAsString());
        String credentialId = signUpResp.get("webauthnCredentialId").getAsString();

        // Attack: obtain a legitimate tenant-2 sign-in challenge, sign it with the tenant-1 authenticator,
        // and submit it to tenant 2. The credential owner is not a member of tenant 2, so it must be rejected.
        JsonObject t2SignInOptions = signInOptions(main, t2);
        Map<String, PublicKeyCredential<AuthenticatorAssertionResponse, AuthenticationExtensionClientOutput>> t2Assertion =
                io.supertokens.test.webauthn.Utils.createPublicKeyRequestOptions(t2SignInOptions, authenticator, credentialId);
        JsonObject crossTenantResp = signIn(main, t2,
                t2Assertion.keySet().iterator().next(), t2Assertion.values().iterator().next());
        assertNotEquals("cross-tenant sign-in must be rejected", "OK", crossTenantResp.get("status").getAsString());
        assertEquals("UNKNOWN_USER_ID_ERROR", crossTenantResp.get("status").getAsString());

        // Control: signing in at tenant 1 with the same passkey still succeeds.
        JsonObject t1SignInOptions = signInOptions(main, t1);
        Map<String, PublicKeyCredential<AuthenticatorAssertionResponse, AuthenticationExtensionClientOutput>> t1Assertion =
                io.supertokens.test.webauthn.Utils.createPublicKeyRequestOptions(t1SignInOptions, authenticator, credentialId);
        JsonObject sameTenantResp = signIn(main, t1,
                t1Assertion.keySet().iterator().next(), t1Assertion.values().iterator().next());
        assertEquals("OK", sameTenantResp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // --- tenant-aware HTTP helpers (the shared webauthn test Utils hit the base endpoint only) ---

    private static JsonObject registerOptions(Main main, TenantIdentifier tenant, String email)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("relyingPartyName", "supertokens.com");
        body.addProperty("relyingPartyId", RP_ID);
        body.addProperty("origin", ORIGIN);
        body.addProperty("timeout", 10000);
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenant, "/recipe/webauthn/options/register"),
                body, 10000, 10000, null, SemVer.v5_3.get(), "webauthn");
    }

    private static JsonObject signInOptions(Main main, TenantIdentifier tenant)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("relyingPartyName", "supertokens.com");
        body.addProperty("relyingPartyId", RP_ID);
        body.addProperty("origin", ORIGIN);
        body.addProperty("timeout", 10000);
        body.addProperty("userVerification", "preferred");
        body.addProperty("userPresence", false);
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenant, "/recipe/webauthn/options/signin"),
                body, 10000, 10000, null, SemVer.v5_3.get(), "webauthn");
    }

    private static JsonObject signUp(Main main, TenantIdentifier tenant, String optionsId,
                                     PublicKeyCredential<AuthenticatorAttestationResponse, RegistrationExtensionClientOutput> credential)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("webauthnGeneratedOptionsId", optionsId);
        body.add("credential", new Gson().toJsonTree(credential));
        body.getAsJsonObject("credential").getAsJsonObject("response").addProperty("attestationObject",
                Base64UrlUtil.encodeToString(credential.getAuthenticatorResponse().getAttestationObject()));
        body.getAsJsonObject("credential").getAsJsonObject("response").addProperty("clientDataJSON",
                Base64UrlUtil.encodeToString(credential.getAuthenticatorResponse().getClientDataJSON()));
        body.getAsJsonObject("credential").addProperty("type", credential.getType());
        body.getAsJsonObject("credential").getAsJsonObject("response").remove("transports");
        body.getAsJsonObject("credential").remove("clientExtensionResults");
        body.getAsJsonObject("credential").addProperty("rawId", Base64UrlUtil.encodeToString(credential.getRawId()));
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenant, "/recipe/webauthn/signup"),
                body, 10000, 10000, null, SemVer.v5_3.get(), "webauthn");
    }

    private static JsonObject signIn(Main main, TenantIdentifier tenant, String optionsId,
                                     PublicKeyCredential<AuthenticatorAssertionResponse, AuthenticationExtensionClientOutput> credential)
            throws HttpResponseException, IOException {
        JsonObject body = new JsonObject();
        body.addProperty("webauthnGeneratedOptionsId", optionsId);
        body.add("credential", new Gson().toJsonTree(credential));
        body.getAsJsonObject("credential").getAsJsonObject("response").addProperty("signature",
                Base64UrlUtil.encodeToString(credential.getResponse().getSignature()));
        body.getAsJsonObject("credential").getAsJsonObject("response").addProperty("clientDataJSON",
                Base64UrlUtil.encodeToString(credential.getAuthenticatorResponse().getClientDataJSON()));
        body.getAsJsonObject("credential").getAsJsonObject("response").addProperty("authenticatorData",
                Base64UrlUtil.encodeToString(credential.getResponse().getAuthenticatorData()));
        body.getAsJsonObject("credential").addProperty("type", credential.getType());
        body.getAsJsonObject("credential").getAsJsonObject("response").remove("transports");
        body.getAsJsonObject("credential").remove("clientExtensionResults");
        body.getAsJsonObject("credential").addProperty("rawId", Base64UrlUtil.encodeToString(credential.getRawId()));
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenant, "/recipe/webauthn/signin"),
                body, 10000, 10000, null, SemVer.v5_3.get(), "webauthn");
    }
}
