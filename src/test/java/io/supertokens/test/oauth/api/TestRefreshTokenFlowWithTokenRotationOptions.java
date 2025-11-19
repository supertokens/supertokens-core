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

package io.supertokens.test.oauth.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.totp.TotpLicenseTest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class TestRefreshTokenFlowWithTokenRotationOptions {
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
        OAuthAPIHelper.resetOAuthProvider();
    }

    private static JsonObject createClient(Main main, boolean enableRefreshTokenRotation) throws Exception {
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
        redirectUris.add(new JsonPrimitive("http://localhost.com:3000/auth/callback/supertokens"));
        clientBody.add("redirectUris", redirectUris);
        clientBody.addProperty("scope", "openid email offline_access");
        clientBody.addProperty("tokenEndpointAuthMethod", "client_secret_post");
        clientBody.addProperty("enableRefreshTokenRotation", enableRefreshTokenRotation);

        return OAuthAPIHelper.createClient(main, clientBody);
    }

    private static void updateClient(Main main, JsonObject client, boolean enableRefreshTokenRotation) throws Exception {
        JsonObject updateClientBody = new JsonObject();
        updateClientBody.addProperty("clientId", client.get("clientId").getAsString());
        updateClientBody.addProperty("enableRefreshTokenRotation", enableRefreshTokenRotation);
        OAuthAPIHelper.updateClient(main, updateClientBody);
    }

    private static JsonObject completeFlowAndGetTokens(Main main, JsonObject client) throws Exception {
        List<String> allCookies = new ArrayList<>();
        JsonObject authRequestBody = new JsonObject();
        JsonObject params = new JsonObject();
        params.addProperty("client_id", client.get("clientId").getAsString());
        params.addProperty("redirect_uri", "http://localhost.com:3000/auth/callback/supertokens");
        params.addProperty("response_type", "code");
        params.addProperty("scope", "openid offline_access");
        params.addProperty("state", "test12345678");

        authRequestBody.add("params", params);
        JsonObject authResponse = OAuthAPIHelper.auth(main, authRequestBody);

        allCookies.clear();
        for (JsonElement cookieElem : authResponse.get("cookies").getAsJsonArray()) {
            allCookies.add(cookieElem.getAsString().split(";")[0]);
        }

        String redirectTo = authResponse.get("redirectTo").getAsString();
        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001/auth");

        URL url = new URL(redirectTo);
        Map<String, String> queryParams = splitQuery(url);
        String loginChallenge = queryParams.get("login_challenge");

        Map<String, String> acceptLoginRequestParams = new HashMap<>();
        acceptLoginRequestParams.put("loginChallenge", loginChallenge);

        JsonObject acceptLoginRequestBody = new JsonObject();
        acceptLoginRequestBody.addProperty("subject", "someuserid");
        acceptLoginRequestBody.addProperty("remember", true);
        acceptLoginRequestBody.addProperty("rememberFor", 3600);
        acceptLoginRequestBody.addProperty("identityProviderSessionId", "session-handle");

        JsonObject acceptLoginRequestResponse = OAuthAPIHelper.acceptLoginRequest(main, acceptLoginRequestParams, acceptLoginRequestBody);

        redirectTo = acceptLoginRequestResponse.get("redirectTo").getAsString();
        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001/auth");

        url = new URL(redirectTo);
        queryParams = splitQuery(url);

        params = new JsonObject();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            params.addProperty(entry.getKey(), entry.getValue());
        }
        authRequestBody.add("params", params);
        authRequestBody.addProperty("cookies", String.join("; ", allCookies));

        authResponse = OAuthAPIHelper.auth(main, authRequestBody);

        redirectTo = authResponse.get("redirectTo").getAsString();
        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001/auth");
        allCookies.clear();
        for (JsonElement cookieElem : authResponse.get("cookies").getAsJsonArray()) {
            allCookies.add(cookieElem.getAsString().split(";")[0]);
        }

        url = new URL(redirectTo);
        queryParams = splitQuery(url);

        String consentChallenge = queryParams.get("consent_challenge");

        JsonObject acceptConsentRequestBody = new JsonObject();
        acceptConsentRequestBody.addProperty("iss", "http://localhost:3001/auth");
        acceptConsentRequestBody.addProperty("tId", "public");
        acceptConsentRequestBody.addProperty("rsub", "someuserid");
        acceptConsentRequestBody.addProperty("sessionHandle", "session-handle");
        acceptConsentRequestBody.add("initialAccessTokenPayload", new JsonObject());
        acceptConsentRequestBody.add("initialIdTokenPayload", new JsonObject());
        JsonArray grantScope = new JsonArray();
        grantScope.add(new JsonPrimitive("openid"));
        grantScope.add(new JsonPrimitive("offline_access"));
        acceptConsentRequestBody.add("grantScope", grantScope);
        JsonArray audience = new JsonArray();
        acceptConsentRequestBody.add("grantAccessTokenAudience", audience);
        JsonObject session = new JsonObject();
//        JsonObject accessToken = new JsonObject();
//        accessToken.addProperty("gid", "gidForTesting");
        session.add("access_token", new JsonObject());
        session.add("id_token", new JsonObject());
        acceptConsentRequestBody.add("session", session);

        queryParams = new HashMap<>();
        queryParams.put("consentChallenge", consentChallenge);

        JsonObject acceptConsentRequestResponse = OAuthAPIHelper.acceptConsentRequest(main, queryParams, acceptConsentRequestBody);

        redirectTo = acceptConsentRequestResponse.get("redirectTo").getAsString();
        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001/auth");

        url = new URL(redirectTo);
        queryParams = splitQuery(url);

        params = new JsonObject();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            params.addProperty(entry.getKey(), entry.getValue());
        }
        authRequestBody.add("params", params);
        authRequestBody.addProperty("cookies", String.join("; ", allCookies));

        authResponse = OAuthAPIHelper.auth(main, authRequestBody);

        redirectTo = authResponse.get("redirectTo").getAsString();
        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001/auth");

        url = new URL(redirectTo);
        queryParams = splitQuery(url);

        String authorizationCode = queryParams.get("code");

        JsonObject tokenRequestBody = new JsonObject();
        JsonObject inputBody = new JsonObject();
        inputBody.addProperty("grant_type", "authorization_code");
        inputBody.addProperty("code", authorizationCode);
        inputBody.addProperty("redirect_uri", "http://localhost.com:3000/auth/callback/supertokens");
        inputBody.addProperty("client_id", client.get("clientId").getAsString());
        inputBody.addProperty("client_secret", client.get("clientSecret").getAsString());
        tokenRequestBody.add("inputBody", inputBody);
        tokenRequestBody.addProperty("iss", "http://localhost:3001/auth");

        return OAuthAPIHelper.token(main, tokenRequestBody);

    }

    private static JsonObject refreshToken(Main main, JsonObject client, String refreshToken) throws Exception {
        JsonObject tokenRequestBody = new JsonObject();
        JsonObject inputBody = new JsonObject();
        inputBody.addProperty("grant_type", "refresh_token");
        inputBody.addProperty("refresh_token", refreshToken);
        inputBody.addProperty("client_id", client.get("clientId").getAsString());
        inputBody.addProperty("client_secret", client.get("clientSecret").getAsString());

        tokenRequestBody.add("access_token", new JsonObject());
        tokenRequestBody.add("id_token", new JsonObject());
        tokenRequestBody.add("inputBody", inputBody);
        tokenRequestBody.addProperty("iss", "http://localhost:3001/auth");

        return OAuthAPIHelper.token(main, tokenRequestBody);
    }

    @Test
    public void testRefreshTokenWithRotationDisabled() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.getProcess())
                .setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_MFA_FEATURE);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject client = createClient(process.getProcess(), false);
        JsonObject tokens = completeFlowAndGetTokens(process.getProcess(), client);

        String refreshToken = tokens.get("refresh_token").getAsString();
        JsonObject newTokens = refreshToken(process.getProcess(), client, refreshToken);
        assertFalse(newTokens.has("refresh_token"));

        // refresh again with original refresh token
        newTokens = refreshToken(process.getProcess(), client, refreshToken);
        assertFalse(newTokens.has("refresh_token"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRefreshTokenWithRotationEnabled() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.getProcess())
                .setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_MFA_FEATURE);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject client = createClient(process.getProcess(), true);
        JsonObject tokens = completeFlowAndGetTokens(process.getProcess(), client);

        String refreshToken = tokens.get("refresh_token").getAsString();
        JsonObject newTokens = refreshToken(process.getProcess(), client, refreshToken);
        assertTrue(newTokens.has("refresh_token"));

        String newRefreshToken = newTokens.get("refresh_token").getAsString();

        // refresh again with original refresh token
        newTokens = refreshToken(process.getProcess(), client, refreshToken);
        assertEquals("OAUTH_ERROR", newTokens.get("status").getAsString());
        assertEquals("token_inactive", newTokens.get("error").getAsString());

        newTokens = refreshToken(process.getProcess(), client, newRefreshToken);
        assertTrue(newTokens.has("refresh_token"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRefreshTokenWhenRotationIsEnabledAfter() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.getProcess())
                .setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_MFA_FEATURE);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject client = createClient(process.getProcess(), false);
        JsonObject tokens = completeFlowAndGetTokens(process.getProcess(), client);

        String refreshToken = tokens.get("refresh_token").getAsString();
        JsonObject newTokens = refreshToken(process.getProcess(), client, refreshToken);
        assertFalse(newTokens.has("refresh_token"));

        updateClient(process.getProcess(), client, true);

        newTokens = refreshToken(process.getProcess(), client, refreshToken);
        assertTrue(newTokens.has("refresh_token"));

        String newRefreshToken = newTokens.get("refresh_token").getAsString();

        // refresh again with original refresh token
        newTokens = refreshToken(process.getProcess(), client, refreshToken);
        assertEquals("OAUTH_ERROR", newTokens.get("status").getAsString());
        assertEquals("token_inactive", newTokens.get("error").getAsString());


        newTokens = refreshToken(process.getProcess(), client, newRefreshToken);
        assertTrue(newTokens.has("refresh_token"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRefreshTokenWithRotationIsDisabledAfter() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.getProcess())
                .setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_MFA_FEATURE);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject client = createClient(process.getProcess(), true);
        JsonObject tokens = completeFlowAndGetTokens(process.getProcess(), client);

        String refreshToken = tokens.get("refresh_token").getAsString();
        JsonObject newTokens = refreshToken(process.getProcess(), client, refreshToken);
        assertTrue(newTokens.has("refresh_token"));

        String newRefreshToken = newTokens.get("refresh_token").getAsString();

        updateClient(process.getProcess(), client, false);

        newTokens = refreshToken(process.getProcess(), client, newRefreshToken);
        assertFalse(newTokens.has("refresh_token"));

        newTokens = refreshToken(process.getProcess(), client, newRefreshToken);
        assertFalse(newTokens.has("refresh_token"));

        newTokens = refreshToken(process.getProcess(), client, newRefreshToken);
        assertFalse(newTokens.has("refresh_token"));

        newTokens = refreshToken(process.getProcess(), client, newRefreshToken);
        assertFalse(newTokens.has("refresh_token"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testParallelRefreshTokenWithoutRotation() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.getProcess())
                .setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_MFA_FEATURE);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Create client with token rotation disabled
        JsonObject client = createClient(process.getProcess(), false);
        JsonObject tokens = completeFlowAndGetTokens(process.getProcess(), client);

        String refreshToken = tokens.get("refresh_token").getAsString();

        // Setup parallel execution: 16 threads, each making 1000 refresh calls
        int numberOfThreads = 16;
        int refreshCallsPerThread = 25;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // Execute refresh token calls in parallel
        for (int i = 0; i < numberOfThreads; i++) {
            executor.execute(() -> {
                for (int j = 0; j < refreshCallsPerThread; j++) {
                    try {
                        JsonObject refreshResponse = refreshToken(process.getProcess(), client, refreshToken);
                        if ("OK".equals(refreshResponse.get("status").getAsString())) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                            exceptions.add(new RuntimeException("Refresh failed: " + refreshResponse.toString()));
                        }
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                        failureCount.incrementAndGet();
                        exceptions.add(e);
                    }
                }
            });
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(5, TimeUnit.MINUTES);
        assertTrue("Executor did not terminate within timeout", terminated);

        // Verify all refresh calls succeeded
        int totalExpectedCalls = numberOfThreads * refreshCallsPerThread;
        assertEquals("All refresh token calls should succeed", totalExpectedCalls, successCount.get());
        assertEquals("No refresh token calls should fail", 0, failureCount.get());
        assertTrue("No exceptions should occur", exceptions.isEmpty());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static Map<String, String> splitQuery(URL url) throws UnsupportedEncodingException {
        Map<String, String> queryPairs = new LinkedHashMap<>();
        String query = url.getQuery();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            queryPairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                           URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        return queryPairs;
    }
}
