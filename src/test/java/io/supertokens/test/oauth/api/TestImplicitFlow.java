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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import static org.junit.Assert.assertNotNull;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestImplicitFlow {
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

    @Test
    public void testImplicitGrantFlow() throws Exception {
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

        JsonObject clientBody = new JsonObject();
        JsonArray grantTypes = new JsonArray();
        grantTypes.add(new JsonPrimitive("implicit"));
        clientBody.add("grantTypes", grantTypes);
        JsonArray responseTypes = new JsonArray();
        responseTypes.add(new JsonPrimitive("token"));
        responseTypes.add(new JsonPrimitive("id_token"));
        clientBody.add("responseTypes", responseTypes);
        JsonArray redirectUris = new JsonArray();
        redirectUris.add(new JsonPrimitive("http://localhost.com:3000/auth/callback/supertokens"));
        clientBody.add("redirectUris", redirectUris);
        clientBody.addProperty("scope", "openid profile email");

        JsonObject client = OAuthAPIHelper.createClient(process.getProcess(), clientBody);

        JsonObject authRequestBody = new JsonObject();
        JsonObject params = new JsonObject();
        params.addProperty("client_id", client.get("clientId").getAsString());
        params.addProperty("redirect_uri", "http://localhost.com:3000/auth/callback/supertokens");
        params.addProperty("response_type", "token");
        params.addProperty("scope", "openid profile email");
        params.addProperty("state", "test12345678");

        authRequestBody.add("params", params);

        JsonObject authResponse = OAuthAPIHelper.auth(process.getProcess(), authRequestBody);
        String cookies = authResponse.get("cookies").getAsJsonArray().get(0).getAsString();
        cookies = cookies.split(";")[0];

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

        JsonObject acceptLoginRequestResponse = OAuthAPIHelper.acceptLoginRequest(process.getProcess(), acceptLoginRequestParams, acceptLoginRequestBody);

        redirectTo = acceptLoginRequestResponse.get("redirectTo").getAsString();
        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001/auth");

        url = new URL(redirectTo);
        queryParams = splitQuery(url);

        params = new JsonObject();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            params.addProperty(entry.getKey(), entry.getValue());
        }
        authRequestBody.add("params", params);
        authRequestBody.addProperty("cookies", cookies);

        authResponse = OAuthAPIHelper.auth(process.getProcess(), authRequestBody);

        redirectTo = authResponse.get("redirectTo").getAsString();
        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001/auth");
        cookies = authResponse.get("cookies").getAsJsonArray().get(0).getAsString();
        cookies = cookies.split(";")[0];

        url = new URL(redirectTo);
        queryParams = splitQuery(url);

        String consentChallenge = queryParams.get("consent_challenge");

        JsonObject acceptConsentRequestBody = new JsonObject();
        acceptConsentRequestBody.addProperty("remember", true);
        acceptConsentRequestBody.addProperty("rememberFor", 3600);
        acceptConsentRequestBody.addProperty("iss", "http://localhost:3001/auth");
        acceptConsentRequestBody.addProperty("tId", "public");
        acceptConsentRequestBody.addProperty("rsub", "someuser");
        acceptConsentRequestBody.addProperty("sessionHandle", "session-handle");
        acceptConsentRequestBody.add("initialAccessTokenPayload", new JsonObject());
        acceptConsentRequestBody.add("initialIdTokenPayload", new JsonObject());

        queryParams = new HashMap<>();
        queryParams.put("consentChallenge", consentChallenge);

        JsonObject acceptConsentRequestResponse = OAuthAPIHelper.acceptConsentRequest(process.getProcess(), queryParams, acceptConsentRequestBody);

        redirectTo = acceptConsentRequestResponse.get("redirectTo").getAsString();
        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001/auth");

        url = new URL(redirectTo);
        queryParams = splitQuery(url);

        params = new JsonObject();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            params.addProperty(entry.getKey(), entry.getValue());
        }
        authRequestBody.add("params", params);
        authRequestBody.addProperty("cookies", cookies);

        authResponse = OAuthAPIHelper.auth(process.getProcess(), authRequestBody);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Helper method to split query parameters
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
