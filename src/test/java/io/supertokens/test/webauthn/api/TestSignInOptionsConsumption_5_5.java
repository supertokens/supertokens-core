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
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.extension.client.AuthenticationExtensionClientOutput;
import com.webauthn4j.test.EmulatorUtil;
import com.webauthn4j.test.client.ClientPlatform;
import com.webauthn4j.util.Base64UrlUtil;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * From CDI 5.5, sign-in options are single-use: a successful sign in consumes them
 * atomically (WebAuthn L3 §13.4.3 — challenges exist to prevent replay). Requests on
 * CDI <= 5.4 keep the old behavior because those SDKs verify the same assertion twice
 * per sign in (see supertokens-core#1195) and would break if options were consumed.
 */
public class TestSignInOptionsConsumption_5_5 {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testSignInOnCDI5_5ConsumesOptions() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        Main main = process.getProcess();

        JsonObject user = io.supertokens.test.webauthn.Utils.registerUserWithCredentials(main,
                "consume551@example.com");

        String[] optionsIdHolder = new String[1];
        JsonObject signInBody = createSignInRequestBody(main, user, optionsIdHolder);

        JsonObject signInResp = sendSignIn(main, signInBody, SemVer.v5_5);
        assertEquals("OK", signInResp.get("status").getAsString());

        // the options were consumed by the successful sign in
        assertEquals("OPTIONS_NOT_FOUND_ERROR", getGeneratedOptions(main, optionsIdHolder[0]));

        // replaying the exact same assertion fails: the challenge is gone
        JsonObject replayResp = sendSignIn(main, signInBody, SemVer.v5_5);
        assertEquals("OPTIONS_NOT_FOUND_ERROR", replayResp.get("status").getAsString());

        // a fresh ceremony still works
        JsonObject freshBody = createSignInRequestBody(main, user, new String[1]);
        JsonObject freshResp = sendSignIn(main, freshBody, SemVer.v5_5);
        assertEquals("OK", freshResp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSignInOnOldCDIDoesNotConsumeOptions() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        Main main = process.getProcess();

        JsonObject user = io.supertokens.test.webauthn.Utils.registerUserWithCredentials(main,
                "consume540@example.com");

        String[] optionsIdHolder = new String[1];
        JsonObject signInBody = createSignInRequestBody(main, user, optionsIdHolder);

        JsonObject signInResp = sendSignIn(main, signInBody, SemVer.v5_4);
        assertEquals("OK", signInResp.get("status").getAsString());

        // pre-5.5 behavior, unchanged: the options survive a successful sign in...
        assertEquals("OK", getGeneratedOptions(main, optionsIdHolder[0]));

        // ...so a replay of the same assertion gets past the options lookup and is
        // rejected by the signature-counter clone detection instead (the emulated
        // authenticator increments its counter, so the replayed value is stale).
        JsonObject replayResp = sendSignIn(main, signInBody, SemVer.v5_4);
        assertEquals("INVALID_AUTHENTICATOR_ERROR", replayResp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testFailedSignInDoesNotConsumeOptions() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        Main main = process.getProcess();

        JsonObject user = io.supertokens.test.webauthn.Utils.registerUserWithCredentials(main,
                "consumefail@example.com");

        String[] optionsIdHolder = new String[1];
        JsonObject signInBody = createSignInRequestBody(main, user, optionsIdHolder);

        // corrupt the assertion signature -> verification fails
        JsonObject tamperedBody = new Gson().fromJson(new Gson().toJson(signInBody), JsonObject.class);
        byte[] signature = Base64UrlUtil.decode(
                tamperedBody.getAsJsonObject("credential").getAsJsonObject("response").get("signature").getAsString());
        signature[signature.length - 1] ^= 0x01;
        tamperedBody.getAsJsonObject("credential").getAsJsonObject("response")
                .addProperty("signature", Base64UrlUtil.encodeToString(signature));

        JsonObject failedResp = sendSignIn(main, tamperedBody, SemVer.v5_5);
        assertNotEquals("OK", failedResp.get("status").getAsString());

        // a failed attempt must not consume the options (nothing was proven, and a
        // garbage request must not be able to invalidate an in-flight ceremony)
        assertEquals("OK", getGeneratedOptions(main, optionsIdHolder[0]));

        // the legitimate assertion still works against the same options...
        JsonObject signInResp = sendSignIn(main, signInBody, SemVer.v5_5);
        assertEquals("OK", signInResp.get("status").getAsString());

        // ...and only then are they consumed
        assertEquals("OPTIONS_NOT_FOUND_ERROR", getGeneratedOptions(main, optionsIdHolder[0]));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Builds a sign-in request body for a fresh set of sign-in options, using the shared
    // emulated authenticator that registered the user's credential. The body is a plain
    // JsonObject so tests can replay the exact same serialized assertion.
    private JsonObject createSignInRequestBody(Main main, JsonObject user, String[] optionsIdHolder)
            throws Exception {
        ClientPlatform clientPlatform = EmulatorUtil.createClientPlatform(EmulatorUtil.FIDO_U2F_AUTHENTICATOR);
        JsonObject signInOptionsResponse = io.supertokens.test.webauthn.Utils.signInOptions(main);
        Map<String, PublicKeyCredential<AuthenticatorAssertionResponse, AuthenticationExtensionClientOutput>> pubkeysToOptions =
                io.supertokens.test.webauthn.Utils.createPublicKeyRequestOptions(signInOptionsResponse, clientPlatform,
                        user.get("webauthnCredentialId").getAsString());

        String optionsId = pubkeysToOptions.keySet().stream().findFirst().get();
        PublicKeyCredential<AuthenticatorAssertionResponse, AuthenticationExtensionClientOutput> credential =
                pubkeysToOptions.values().stream().findFirst().get();
        optionsIdHolder[0] = optionsId;

        JsonObject signInRequestBody = new JsonObject();
        signInRequestBody.addProperty("webauthnGeneratedOptionsId", optionsId);
        signInRequestBody.add("credential", new Gson().toJsonTree(credential));
        signInRequestBody.getAsJsonObject("credential").getAsJsonObject("response").addProperty("signature",
                Base64UrlUtil.encodeToString(credential.getResponse().getSignature()));
        signInRequestBody.getAsJsonObject("credential").getAsJsonObject("response").addProperty("clientDataJSON",
                Base64UrlUtil.encodeToString(credential.getAuthenticatorResponse().getClientDataJSON()));
        signInRequestBody.getAsJsonObject("credential").getAsJsonObject("response").addProperty("authenticatorData",
                Base64UrlUtil.encodeToString(credential.getResponse().getAuthenticatorData()));
        signInRequestBody.getAsJsonObject("credential").addProperty("type", credential.getType());
        signInRequestBody.getAsJsonObject("credential").getAsJsonObject("response").remove("transports");
        signInRequestBody.getAsJsonObject("credential").remove("clientExtensionResults");
        signInRequestBody.getAsJsonObject("credential").addProperty("rawId",
                Base64UrlUtil.encodeToString(credential.getRawId()));

        return signInRequestBody;
    }

    private JsonObject sendSignIn(Main main, JsonObject body, SemVer cdiVersion) throws Exception {
        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/webauthn/signin",
                body, 10000, 1000, null, cdiVersion.get(), null);
    }

    private String getGeneratedOptions(Main main, String optionsId) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("webauthnGeneratedOptionsId", optionsId);
        JsonObject resp = HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/recipe/webauthn/options", params, 1000, 1000, null,
                SemVer.v5_3.get(), "webauthn");
        return resp.get("status").getAsString();
    }
}
