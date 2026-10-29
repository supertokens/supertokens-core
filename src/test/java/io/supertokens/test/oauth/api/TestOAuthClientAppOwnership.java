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

package io.supertokens.test.oauth.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sun.net.httpserver.HttpServer;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.config.Config;
import io.supertokens.oauth.HttpRequestForOAuthProvider;
import io.supertokens.oauth.OAuth;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Several apps of one core may share the same OAuth provider. These tests make sure that a caller of one app
 * cannot act on OAuth clients, challenges or tokens of another app through the OAuth provider proxy APIs.
 */
public class TestOAuthClientAppOwnership {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    private static final String OTHER_APP_BASE = "http://localhost:3567/appid-otherapp";
    private static final String PUBLIC_APP_BASE = "http://localhost:3567";
    private static final String REDIRECT_URI = "http://localhost.com:3000/auth/callback/supertokens";

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
        OAuthAPIHelper.resetOAuthProvider();
    }

    @Test
    public void testAcceptLoginChallengeOfOtherAppRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        JsonObject client = createAuthCodeClient(main, PUBLIC_APP_BASE);
        FlowState flow = startAuthFlow(main, client);

        JsonObject response = acceptLoginRequest(main, OTHER_APP_BASE, flow.loginChallenge, "attacker-chosen-user");
        assertChallengeNotFound(response);
        assertNull(response.get("redirectTo"));

        // the response is the same as for a challenge that does not exist
        assertEquals(acceptLoginRequest(main, OTHER_APP_BASE, flow.loginChallenge + "extras", "someuserid"),
                response);

        // the challenge is untouched and can still be completed by the app that owns the client
        response = acceptLoginRequest(main, PUBLIC_APP_BASE, flow.loginChallenge, "someuserid");
        assertEquals("OK", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRejectLoginChallengeOfOtherAppRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        JsonObject client = createAuthCodeClient(main, PUBLIC_APP_BASE);
        FlowState flow = startAuthFlow(main, client);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/auth/requests/login/reject?loginChallenge="
                        + URLEncoder.encode(flow.loginChallenge, StandardCharsets.UTF_8),
                new JsonObject(), 5000, 20000, null, SemVer.v5_2.get(), "");
        assertChallengeNotFound(response);

        // the challenge was not rejected
        response = acceptLoginRequest(main, PUBLIC_APP_BASE, flow.loginChallenge, "someuserid");
        assertEquals("OK", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAcceptConsentChallengeOfOtherAppRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        JsonObject client = createAuthCodeClient(main, PUBLIC_APP_BASE);
        FlowState flow = startAuthFlow(main, client);
        continueToConsent(main, flow);

        JsonObject response = acceptConsentRequest(main, OTHER_APP_BASE, flow.consentChallenge,
                "attacker-chosen-user");
        assertChallengeNotFound(response);
        assertNull(response.get("redirectTo"));

        response = acceptConsentRequest(main, PUBLIC_APP_BASE, flow.consentChallenge, "someuserid");
        assertEquals("OK", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRejectConsentChallengeOfOtherAppRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        JsonObject client = createAuthCodeClient(main, PUBLIC_APP_BASE);
        FlowState flow = startAuthFlow(main, client);
        continueToConsent(main, flow);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/auth/requests/consent/reject?consentChallenge="
                        + URLEncoder.encode(flow.consentChallenge, StandardCharsets.UTF_8),
                new JsonObject(), 5000, 20000, null, SemVer.v5_2.get(), "");
        assertChallengeNotFound(response);

        // the challenge was not rejected
        response = acceptConsentRequest(main, PUBLIC_APP_BASE, flow.consentChallenge, "someuserid");
        assertEquals("OK", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetConsentRequestOfOtherAppRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        JsonObject client = createAuthCodeClient(main, PUBLIC_APP_BASE);
        FlowState flow = startAuthFlow(main, client);
        continueToConsent(main, flow);

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("consentChallenge", flow.consentChallenge);

        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/auth/requests/consent", queryParams, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertChallengeNotFound(response);
        assertNull(response.get("subject"));

        // the response is the same as for a challenge that does not exist
        Map<String, String> unknownChallengeParams = new HashMap<>();
        unknownChallengeParams.put("consentChallenge", flow.consentChallenge + "extras");
        assertEquals(HttpRequestForTesting.sendGETRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/auth/requests/consent", unknownChallengeParams, 5000, 20000, null,
                SemVer.v5_2.get(), ""), response);

        response = HttpRequestForTesting.sendGETRequest(main, "",
                PUBLIC_APP_BASE + "/recipe/oauth/auth/requests/consent", queryParams, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertEquals("OK", response.get("status").getAsString());
        assertEquals("someuserid", response.get("subject").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRevokeTokensOfOtherAppsClientRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        JsonObject client = createAuthCodeClient(main, PUBLIC_APP_BASE);
        FlowState flow = startAuthFlow(main, client);
        continueToConsent(main, flow);
        JsonObject tokens = finishFlowAndGetTokens(main, client, flow);
        assertNotNull(tokens.get("refresh_token"));

        JsonObject revokeBody = new JsonObject();
        revokeBody.addProperty("client_id", client.get("clientId").getAsString());
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/tokens/revoke", revokeBody, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        // reported as OK: this app has no such client, so it has no tokens of it to revoke
        assertEquals("OK", response.get("status").getAsString());

        Thread.sleep(1000);

        // the tokens of the client are still usable in the app that owns the client
        JsonObject refreshResponse = refreshTokens(main, client, tokens.get("refresh_token").getAsString());
        assertEquals("OK", refreshResponse.get("status").getAsString());

        // revoking the tokens from the owning app still works
        response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                PUBLIC_APP_BASE + "/recipe/oauth/tokens/revoke", revokeBody, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertEquals("OK", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRevokeTokenWithClientOfOtherAppRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        JsonObject client = createAuthCodeClient(main, PUBLIC_APP_BASE);
        FlowState flow = startAuthFlow(main, client);
        continueToConsent(main, flow);
        JsonObject tokens = finishFlowAndGetTokens(main, client, flow);
        String refreshToken = tokens.get("refresh_token").getAsString();

        JsonObject revokeBody = new JsonObject();
        revokeBody.addProperty("token", refreshToken);
        revokeBody.addProperty("client_id", client.get("clientId").getAsString());
        revokeBody.addProperty("client_secret", client.get("clientSecret").getAsString());
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/token/revoke", revokeBody, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        // reported like an unknown client
        assertEquals("OAUTH_ERROR", response.get("status").getAsString());
        assertEquals("invalid_client", response.get("error").getAsString());
        assertEquals(401, response.get("statusCode").getAsInt());

        Thread.sleep(1000);

        // the refresh token was not revoked
        JsonObject refreshResponse = refreshTokens(main, client, refreshToken);
        assertEquals("OK", refreshResponse.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetClientOfOtherAppIsNotLookedUpInTheProvider() throws Exception {
        TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        JsonObject client = createAuthCodeClient(main, PUBLIC_APP_BASE);

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("clientId", client.get("clientId").getAsString());
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/clients", queryParams, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertEquals("CLIENT_NOT_FOUND_ERROR", response.get("status").getAsString());

        // a client that exists nowhere gets the same answer as a client of another app
        queryParams.put("clientId", "stcl_does-not-exist");
        response = HttpRequestForTesting.sendGETRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/clients", queryParams, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertEquals("CLIENT_NOT_FOUND_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testIssue225_parameterNamesAreUrlEncoded() throws Exception {
        List<String> received = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String body;
            try (InputStream is = exchange.getRequestBody()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            received.add(exchange.getRequestMethod().equals("POST") ? body : query);
            byte[] resp = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/test";

            Map<String, String> params = new LinkedHashMap<>();
            params.put("client_id", "client-of-this-app");
            params.put("x&client_id", "client-of-another-app");
            params.put("a=b", "c");

            HttpRequestForOAuthProvider.doGet(url, new HashMap<>(), params);
            HttpRequestForOAuthProvider.doFormPost(url, new HashMap<>(), params);
            HttpRequestForOAuthProvider.doJsonPut(url, params, new HashMap<>(), new JsonObject());
            HttpRequestForOAuthProvider.doJsonDelete(url, new HashMap<>(), params, new JsonObject());

            assertEquals(4, received.size());
            for (String serialised : received) {
                Map<String, List<String>> parsed = parseFormEncoded(serialised);
                assertEquals(serialised, 3, parsed.size());
                assertEquals(List.of("client-of-this-app"), parsed.get("client_id"));
                assertEquals(List.of("client-of-another-app"), parsed.get("x&client_id"));
                assertEquals(List.of("c"), parsed.get("a=b"));
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testIssue225_basicAuthClientIdIsDecodedBeforeOwnershipCheck() throws Exception {
        TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        JsonObject publicAppClient = createClientCredentialsClient(main, PUBLIC_APP_BASE, null);
        String publicAppClientId = publicAppClient.get("clientId").getAsString();
        assertTrue(publicAppClientId.startsWith("stcl_"));

        // the other app has a client whose id is the URL-encoded form of the public app's client id (such ids are
        // rejected at creation now, so it is registered the way an older core would have done it)
        String encodedClientId = "stcl%5F" + publicAppClientId.substring("stcl_".length());
        registerLegacyClientInOtherApp(main, encodedClientId);

        // the OAuth provider decodes the Basic auth client id, so this authenticates the public app's client
        String authorizationHeader = "Basic " + Base64.getEncoder().encodeToString(
                (encodedClientId + ":" + publicAppClient.get("clientSecret").getAsString())
                        .getBytes(StandardCharsets.UTF_8));
        JsonObject inputBody = new JsonObject();
        inputBody.addProperty("grant_type", "client_credentials");
        JsonObject tokenBody = new JsonObject();
        tokenBody.add("inputBody", inputBody);
        tokenBody.addProperty("authorizationHeader", authorizationHeader);
        tokenBody.addProperty("iss", "http://localhost:3001/auth");
        tokenBody.add("access_token", new JsonObject());
        tokenBody.add("id_token", new JsonObject());

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/token", tokenBody, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertEquals("CLIENT_NOT_FOUND_ERROR", response.get("status").getAsString());
        assertNull(response.get("access_token"));

        // the same request is fine in the app that owns the client
        response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                PUBLIC_APP_BASE + "/recipe/oauth/token", tokenBody, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertEquals("OK", response.get("status").getAsString());
        assertNotNull(response.get("access_token"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testClientIdThatNeedsEncodingIsRejectedAtCreation() throws Exception {
        TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        JsonObject publicAppClient = createClientCredentialsClient(main, PUBLIC_APP_BASE, null);
        String alias = "stcl%5F" + publicAppClient.get("clientId").getAsString().substring("stcl_".length());

        for (String clientId : new String[]{alias, "a/b", "a?b", "a#b", "a b", "a+b", "a:b", "a%2Fb", "a&b", ".",
                ".."}) {
            JsonObject response = createClientCredentialsClient(main, OTHER_APP_BASE, clientId);
            assertInvalidClientMetadata(response);
            assertEquals("clientId can only contain letters, digits and the characters . _ ~ -",
                    response.get("errorDescription").getAsString());
        }

        // random suffix and removal at the end: the OAuth provider can be shared between test runs
        String safeClientId = "custom-client_1.0~" + java.util.UUID.randomUUID();
        JsonObject client = createClientCredentialsClient(main, OTHER_APP_BASE, safeClientId);
        assertEquals("OK", client.get("status").getAsString());
        assertEquals(safeClientId, client.get("clientId").getAsString());

        JsonObject removeBody = new JsonObject();
        removeBody.addProperty("clientId", safeClientId);
        JsonObject removeResponse = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/clients/remove", removeBody, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertEquals("OK", removeResponse.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testClientIdUnderAnotherKeySpellingIsRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        JsonObject publicAppClient = createClientCredentialsClient(main, PUBLIC_APP_BASE, null);
        String publicAppClientId = publicAppClient.get("clientId").getAsString();
        String alias = "stcl%5F" + publicAppClientId.substring("stcl_".length());

        // every one of these keys would be sent to the OAuth provider as client_id
        String[][] bodies = new String[][]{
                {"clientId", "safe-client-id", "client_id", alias},
                {"clientId", "safe-client-id", "clientID", "a/b"},
                {"client_id", alias},
                {"clientID", alias},
                {"CLIENT_ID", alias},
                {"ClientId", alias},
                // two spellings of another field
                {"clientName", "a", "client_name", "b"},
        };
        for (String[] fields : bodies) {
            JsonObject body = new JsonObject();
            for (int i = 0; i < fields.length; i += 2) {
                body.addProperty(fields[i], fields[i + 1]);
            }
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                    OTHER_APP_BASE + "/recipe/oauth/clients", body, 5000, 20000, null,
                    SemVer.v5_2.get(), "");
            assertInvalidClientMetadata(response);
        }

        // the same for updates
        JsonObject otherAppClient = createClientCredentialsClient(main, OTHER_APP_BASE, null);
        JsonObject updateBody = new JsonObject();
        updateBody.addProperty("clientId", otherAppClient.get("clientId").getAsString());
        updateBody.addProperty("client_id", publicAppClientId);
        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/clients", updateBody, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertInvalidClientMetadata(response);

        // no client with the alias was created in the other app
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("clientId", alias);
        response = HttpRequestForTesting.sendGETRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/clients", queryParams, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertEquals("CLIENT_NOT_FOUND_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testEncodedClientIdAliasCannotReachOtherAppsClient() throws Exception {
        TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        JsonObject victimBody = new JsonObject();
        victimBody.addProperty("clientName", "victim");
        JsonObject victim = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                PUBLIC_APP_BASE + "/recipe/oauth/clients", victimBody, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertEquals("OK", victim.get("status").getAsString());
        String victimClientId = victim.get("clientId").getAsString();

        // a client of the other app whose id URL-decodes to the public app's client id
        String alias = "stcl%5F" + victimClientId.substring("stcl_".length());
        registerLegacyClientInOtherApp(main, alias);

        // GET only reaches the other app's own client
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("clientId", alias);
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/clients", queryParams, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(alias, response.get("clientId").getAsString());
        assertFalse("victim".equals(response.has("clientName") ? response.get("clientName").getAsString() : null));

        // PUT only updates the other app's own client
        JsonObject updateBody = new JsonObject();
        updateBody.addProperty("clientId", alias);
        updateBody.addProperty("clientName", "changed-by-other-app");
        response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/clients", updateBody, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(alias, response.get("clientId").getAsString());

        // remove only deletes the other app's own client
        JsonObject removeBody = new JsonObject();
        removeBody.addProperty("clientId", alias);
        response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                OTHER_APP_BASE + "/recipe/oauth/clients/remove", removeBody, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertEquals("OK", response.get("status").getAsString());

        // the public app's client is unchanged and still exists
        queryParams.put("clientId", victimClientId);
        response = HttpRequestForTesting.sendGETRequest(main, "",
                PUBLIC_APP_BASE + "/recipe/oauth/clients", queryParams, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(victimClientId, response.get("clientId").getAsString());
        assertEquals("victim", response.get("clientName").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testIssue225_basicAuthAndBodyClientIdMustMatch() throws Exception {
        TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        JsonObject client = createClientCredentialsClient(main, PUBLIC_APP_BASE, null);
        String authorizationHeader = "Basic " + Base64.getEncoder().encodeToString(
                (client.get("clientId").getAsString() + ":" + client.get("clientSecret").getAsString())
                        .getBytes(StandardCharsets.UTF_8));

        JsonObject inputBody = new JsonObject();
        inputBody.addProperty("grant_type", "client_credentials");
        inputBody.addProperty("client_id", "stcl_some-other-client");
        JsonObject tokenBody = new JsonObject();
        tokenBody.add("inputBody", inputBody);
        tokenBody.addProperty("authorizationHeader", authorizationHeader);
        tokenBody.addProperty("iss", "http://localhost:3001/auth");
        tokenBody.add("access_token", new JsonObject());
        tokenBody.add("id_token", new JsonObject());

        JsonObject response = OAuthAPIHelper.token(main, tokenBody);
        assertEquals("OAUTH_ERROR", response.get("status").getAsString());
        assertEquals("invalid_request", response.get("error").getAsString());
        assertEquals("The client_id in the request body does not match the client in the Authorization header.",
                response.get("errorDescription").getAsString());
        assertEquals(400, response.get("statusCode").getAsInt());

        // a matching client_id in the body is accepted
        inputBody.addProperty("client_id", client.get("clientId").getAsString());
        response = OAuthAPIHelper.token(main, tokenBody);
        assertEquals("OK", response.get("status").getAsString());
        assertNotNull(response.get("access_token"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testIssue225_authRequestWithoutClientIdRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
        if (process == null) {
            return;
        }
        Main main = process.getProcess();

        JsonObject authRequestBody = new JsonObject();
        JsonObject params = new JsonObject();
        params.addProperty("redirect_uri", REDIRECT_URI);
        params.addProperty("response_type", "code");
        params.addProperty("scope", "openid offline_access");
        params.addProperty("state", "test12345678");
        authRequestBody.add("params", params);

        JsonObject response = OAuthAPIHelper.auth(main, authRequestBody);
        assertEquals("CLIENT_NOT_FOUND_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // ---------------------------------------------------------------------------------------------------------

    private static class FlowState {
        String loginChallenge;
        String consentChallenge;
        JsonObject authRequestBody;
        List<String> cookies = new ArrayList<>();
    }

    @Test
    public void testConsentRequestWithoutClientIsReportedAsNotFound() throws Exception {
        List<String> received = new ArrayList<>();
        HttpServer provider = startMockProvider(received, "{\"challenge\":\"some-challenge\"}");
        try {
            TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
            if (process == null) {
                return;
            }
            Main main = process.getProcess();
            addAppUsingProvider(main, "mockapp", provider);

            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("consentChallenge", "some-challenge");
            JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                    "http://localhost:3567/appid-mockapp/recipe/oauth/auth/requests/consent", queryParams, 5000,
                    20000, null, SemVer.v5_2.get(), "");
            assertChallengeNotFound(response);
            assertEquals(1, received.size());

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        } finally {
            provider.stop(0);
        }
    }

    @Test
    public void testRevokeTokensOfClientNotInTheAppIsNotForwarded() throws Exception {
        List<String> received = new ArrayList<>();
        HttpServer provider = startMockProvider(received, "{}");
        try {
            TestingProcessManager.TestingProcess process = startProcessWithTwoApps();
            if (process == null) {
                return;
            }
            Main main = process.getProcess();
            addAppUsingProvider(main, "mockapp", provider);

            JsonObject revokeBody = new JsonObject();
            revokeBody.addProperty("client_id", "client-of-another-app");
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                    "http://localhost:3567/appid-mockapp/recipe/oauth/tokens/revoke", revokeBody, 5000, 20000, null,
                    SemVer.v5_2.get(), "");
            assertEquals("OK", response.get("status").getAsString());
            // the OAuth provider is never asked to revoke the tokens of a client the app does not have
            assertEquals(received.toString(), 0, received.size());

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        } finally {
            provider.stop(0);
        }
    }

    // An OAuth provider stand-in that records "METHOD path" of every request and answers 200 with the given body
    private static HttpServer startMockProvider(List<String> received, String responseBody) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            synchronized (received) {
                received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            }
            byte[] resp = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        return server;
    }

    private static void addAppUsingProvider(Main main, String appId, HttpServer provider) throws Exception {
        String url = "http://127.0.0.1:" + provider.getAddress().getPort();
        JsonObject coreConfig = new JsonObject();
        coreConfig.addProperty("oauth_provider_public_service_url", url);
        coreConfig.addProperty("oauth_provider_admin_service_url", url);
        coreConfig.addProperty("oauth_provider_consent_login_base_url", "http://localhost:3001/auth");
        Multitenancy.addNewOrUpdateAppOrTenant(main, new TenantConfig(
                new TenantIdentifier(null, appId, null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, coreConfig
        ), false);
    }

    private TestingProcessManager.TestingProcess startProcessWithTwoApps() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
            return null;
        }

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                        new EE_FEATURES[]{EE_FEATURES.OAUTH, EE_FEATURES.MULTI_TENANCY});

        // both apps use the same OAuth provider (the default config)
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "otherapp", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null, new JsonObject()
        ), false);

        return process;
    }

    // Registers a client in the other app the way an older core did: without validating the (caller chosen) id.
    // The client is created in the OAuth provider under exactly this (literal) id and recorded in the other app.
    private void registerLegacyClientInOtherApp(Main main, String clientId) throws Exception {
        String adminUrl = Config.getConfig(new TenantIdentifier(null, null, null), main)
                .getOAuthProviderAdminServiceUrl();
        JsonObject body = new JsonObject();
        body.addProperty("client_id", clientId);
        body.addProperty("client_secret", "legacy-client-secret");
        JsonArray grantTypes = new JsonArray();
        grantTypes.add(new JsonPrimitive("client_credentials"));
        body.add("grant_types", grantTypes);
        body.addProperty("token_endpoint_auth_method", "client_secret_basic");
        body.addProperty("access_token_strategy", "jwt");
        body.addProperty("owner", "_otherapp");
        HttpRequestForOAuthProvider.Response response = HttpRequestForOAuthProvider.doJsonPost(
                adminUrl + "/admin/clients", new HashMap<>(), body);
        assertEquals(response.rawResponse, 201, response.statusCode);

        TenantIdentifier otherApp = new TenantIdentifier(null, "otherapp", null);
        OAuth.addOrUpdateClient(main, otherApp.toAppIdentifier(), StorageLayer.getStorage(otherApp, main),
                clientId, "legacy-client-secret", true, false);
    }

    private JsonObject createAuthCodeClient(Main main, String appBase) throws Exception {
        JsonObject clientBody = new JsonObject();
        JsonArray grantTypes = new JsonArray();
        grantTypes.add(new JsonPrimitive("authorization_code"));
        grantTypes.add(new JsonPrimitive("refresh_token"));
        clientBody.add("grantTypes", grantTypes);
        JsonArray responseTypes = new JsonArray();
        responseTypes.add(new JsonPrimitive("code"));
        responseTypes.add(new JsonPrimitive("id_token"));
        clientBody.add("responseTypes", responseTypes);
        JsonArray redirectUris = new JsonArray();
        redirectUris.add(new JsonPrimitive(REDIRECT_URI));
        clientBody.add("redirectUris", redirectUris);
        clientBody.addProperty("scope", "openid email offline_access");
        clientBody.addProperty("tokenEndpointAuthMethod", "client_secret_post");

        JsonObject client = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                appBase + "/recipe/oauth/clients", clientBody, 5000, 20000, null,
                SemVer.v5_2.get(), "");
        assertEquals("OK", client.get("status").getAsString());
        return client;
    }

    private JsonObject createClientCredentialsClient(Main main, String appBase, String clientId) throws Exception {
        JsonObject clientBody = new JsonObject();
        if (clientId != null) {
            clientBody.addProperty("clientId", clientId);
        }
        JsonArray grantTypes = new JsonArray();
        grantTypes.add(new JsonPrimitive("client_credentials"));
        clientBody.add("grantTypes", grantTypes);
        clientBody.addProperty("scope", "profile");
        clientBody.addProperty("tokenEndpointAuthMethod", "client_secret_basic");

        return HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                appBase + "/recipe/oauth/clients", clientBody, 5000, 20000, null,
                SemVer.v5_2.get(), "");
    }

    private FlowState startAuthFlow(Main main, JsonObject client) throws Exception {
        FlowState flow = new FlowState();
        flow.authRequestBody = new JsonObject();
        JsonObject params = new JsonObject();
        params.addProperty("client_id", client.get("clientId").getAsString());
        params.addProperty("redirect_uri", REDIRECT_URI);
        params.addProperty("response_type", "code");
        params.addProperty("scope", "openid offline_access");
        params.addProperty("state", "test12345678");
        flow.authRequestBody.add("params", params);

        JsonObject authResponse = OAuthAPIHelper.auth(main, flow.authRequestBody);
        assertEquals("OK", authResponse.get("status").getAsString());
        for (JsonElement cookieElem : authResponse.get("cookies").getAsJsonArray()) {
            flow.cookies.add(cookieElem.getAsString().split(";")[0]);
        }

        Map<String, String> queryParams = queryOfRedirect(authResponse.get("redirectTo").getAsString());
        flow.loginChallenge = queryParams.get("login_challenge");
        assertNotNull(flow.loginChallenge);
        return flow;
    }

    private void continueToConsent(Main main, FlowState flow) throws Exception {
        JsonObject response = acceptLoginRequest(main, PUBLIC_APP_BASE, flow.loginChallenge, "someuserid");
        assertEquals("OK", response.get("status").getAsString());

        JsonObject authResponse = followRedirect(main, flow, response.get("redirectTo").getAsString());
        flow.cookies.clear();
        for (JsonElement cookieElem : authResponse.get("cookies").getAsJsonArray()) {
            flow.cookies.add(cookieElem.getAsString().split(";")[0]);
        }
        flow.consentChallenge = queryOfRedirect(authResponse.get("redirectTo").getAsString())
                .get("consent_challenge");
        assertNotNull(flow.consentChallenge);
    }

    private JsonObject finishFlowAndGetTokens(Main main, JsonObject client, FlowState flow) throws Exception {
        JsonObject response = acceptConsentRequest(main, PUBLIC_APP_BASE, flow.consentChallenge, "someuserid");
        assertEquals("OK", response.get("status").getAsString());

        JsonObject authResponse = followRedirect(main, flow, response.get("redirectTo").getAsString());
        String code = queryOfRedirect(authResponse.get("redirectTo").getAsString()).get("code");
        assertNotNull(code);

        JsonObject inputBody = new JsonObject();
        inputBody.addProperty("grant_type", "authorization_code");
        inputBody.addProperty("code", code);
        inputBody.addProperty("redirect_uri", REDIRECT_URI);
        inputBody.addProperty("client_id", client.get("clientId").getAsString());
        inputBody.addProperty("client_secret", client.get("clientSecret").getAsString());
        JsonObject tokenRequestBody = new JsonObject();
        tokenRequestBody.add("inputBody", inputBody);
        tokenRequestBody.addProperty("iss", "http://localhost:3001/auth");
        tokenRequestBody.add("access_token", new JsonObject());
        tokenRequestBody.add("id_token", new JsonObject());

        JsonObject tokenResponse = OAuthAPIHelper.token(main, tokenRequestBody);
        assertEquals("OK", tokenResponse.get("status").getAsString());
        return tokenResponse;
    }

    private JsonObject refreshTokens(Main main, JsonObject client, String refreshToken) throws Exception {
        JsonObject inputBody = new JsonObject();
        inputBody.addProperty("grant_type", "refresh_token");
        inputBody.addProperty("refresh_token", refreshToken);
        inputBody.addProperty("client_id", client.get("clientId").getAsString());
        inputBody.addProperty("client_secret", client.get("clientSecret").getAsString());

        JsonObject tokenBody = new JsonObject();
        tokenBody.add("inputBody", inputBody);
        tokenBody.addProperty("iss", "http://localhost:3001/auth");
        tokenBody.add("access_token", new JsonObject());
        tokenBody.add("id_token", new JsonObject());
        return OAuthAPIHelper.token(main, tokenBody);
    }

    private JsonObject followRedirect(Main main, FlowState flow, String redirectTo) throws Exception {
        JsonObject params = new JsonObject();
        for (Map.Entry<String, String> entry : queryOfRedirect(redirectTo).entrySet()) {
            params.addProperty(entry.getKey(), entry.getValue());
        }
        flow.authRequestBody.add("params", params);
        flow.authRequestBody.addProperty("cookies", String.join("; ", flow.cookies));
        JsonObject authResponse = OAuthAPIHelper.auth(main, flow.authRequestBody);
        assertEquals("OK", authResponse.get("status").getAsString());
        return authResponse;
    }

    private JsonObject acceptLoginRequest(Main main, String appBase, String loginChallenge, String subject)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("subject", subject);
        body.addProperty("remember", true);
        body.addProperty("rememberFor", 3600);
        body.addProperty("identityProviderSessionId", "session-handle");

        return HttpRequestForTesting.sendJsonPUTRequest(main, "",
                appBase + "/recipe/oauth/auth/requests/login/accept?loginChallenge="
                        + URLEncoder.encode(loginChallenge, StandardCharsets.UTF_8),
                body, 5000, 20000, null, SemVer.v5_2.get(), "");
    }

    private JsonObject acceptConsentRequest(Main main, String appBase, String consentChallenge, String rsub)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("iss", "http://localhost:3001/auth");
        body.addProperty("tId", "public");
        body.addProperty("rsub", rsub);
        body.addProperty("sessionHandle", "session-handle");
        body.add("initialAccessTokenPayload", new JsonObject());
        body.add("initialIdTokenPayload", new JsonObject());
        JsonArray grantScope = new JsonArray();
        grantScope.add(new JsonPrimitive("openid"));
        grantScope.add(new JsonPrimitive("offline_access"));
        body.add("grantScope", grantScope);
        body.add("grantAccessTokenAudience", new JsonArray());

        return HttpRequestForTesting.sendJsonPUTRequest(main, "",
                appBase + "/recipe/oauth/auth/requests/consent/accept?consentChallenge="
                        + URLEncoder.encode(consentChallenge, StandardCharsets.UTF_8),
                body, 5000, 20000, null, SemVer.v5_2.get(), "");
    }

    private static void assertInvalidClientMetadata(JsonObject response) {
        assertEquals(response.toString(), "OAUTH_ERROR", response.get("status").getAsString());
        assertEquals("invalid_client_metadata", response.get("error").getAsString());
        assertEquals(400, response.get("statusCode").getAsInt());
    }

    // the OAUTH_ERROR the OAuth provider returns for an unknown login/consent challenge
    private static void assertChallengeNotFound(JsonObject response) {
        assertEquals("OAUTH_ERROR", response.get("status").getAsString());
        assertEquals("Not Found", response.get("error").getAsString());
        assertEquals("Unable to locate the requested resource", response.get("errorDescription").getAsString());
        assertEquals(404, response.get("statusCode").getAsInt());
    }

    private static Map<String, String> queryOfRedirect(String redirectTo) throws Exception {
        URL url = new URL(redirectTo.replace("{apiDomain}", "http://localhost:3001/auth"));
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : parseFormEncoded(url.toURI().getRawQuery()).entrySet()) {
            result.put(entry.getKey(), entry.getValue().get(0));
        }
        return result;
    }

    private static Map<String, List<String>> parseFormEncoded(String encoded) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return result;
        }
        for (String pair : encoded.split("&")) {
            int idx = pair.indexOf('=');
            String key = URLDecoder.decode(idx < 0 ? pair : pair.substring(0, idx), StandardCharsets.UTF_8);
            String value = idx < 0 ? "" : URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return result;
    }
}
