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
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.session.Session;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.session.jwt.JWT;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;

import static org.junit.Assert.*;

public class TestIssueTokens {
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
    public void testAccessToken() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.OAUTH });

        JsonObject client = createClient(process.getProcess());

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password123");
        SessionInformationHolder session = Session.createNewSession(process.getProcess(), user.getSupertokensUserId(),
                new JsonObject(), new JsonObject());

        JsonObject tokenResponse = issueTokens(process.getProcess(), client, user.getSupertokensUserId(),
                user.getSupertokensUserId(), session.session.handle);

        String accessToken = tokenResponse.get("access_token").getAsString();
        JWT.JWTInfo accessTokenInfo = JWT.getPayloadWithoutVerifying(accessToken);
        assertTrue(accessTokenInfo.payload.has("iss"));
        assertEquals("http://localhost:3001/auth", accessTokenInfo.payload.get("iss").getAsString());

        String idToken = tokenResponse.get("id_token").getAsString();
        JWT.JWTInfo idTokenInfo = JWT.getPayloadWithoutVerifying(idToken);
        assertTrue(idTokenInfo.payload.has("iss"));
        assertEquals("http://localhost:3001/auth", idTokenInfo.payload.get("iss").getAsString());

        // test introspect access token
        JsonObject introspectResponse = introspectToken(process.getProcess(),
                tokenResponse.get("access_token").getAsString());
        assertEquals("OK", introspectResponse.get("status").getAsString());
        assertTrue(introspectResponse.get("active").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private JsonObject issueTokens(Main main, JsonObject client, String sub, String rsub, String sessionHandle)
            throws Exception {
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
        acceptLoginRequestBody.addProperty("subject", sub);
        acceptLoginRequestBody.addProperty("remember", true);
        acceptLoginRequestBody.addProperty("rememberFor", 3600);
        acceptLoginRequestBody.addProperty("identityProviderSessionId", sessionHandle);

        JsonObject acceptLoginRequestResponse = OAuthAPIHelper.acceptLoginRequest(main, acceptLoginRequestParams,
                acceptLoginRequestBody);

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
        acceptConsentRequestBody.addProperty("rsub", rsub);
        acceptConsentRequestBody.addProperty("sessionHandle", sessionHandle);
        acceptConsentRequestBody.add("initialAccessTokenPayload", new JsonObject());
        acceptConsentRequestBody.add("initialIdTokenPayload", new JsonObject());
        JsonArray grantScope = new JsonArray();
        grantScope.add(new JsonPrimitive("openid"));
        grantScope.add(new JsonPrimitive("offline_access"));
        acceptConsentRequestBody.add("grantScope", grantScope);
        JsonArray audience = new JsonArray();
        acceptConsentRequestBody.add("grantAccessTokenAudience", audience);
        JsonObject session = new JsonObject();
        session.add("access_token", new JsonObject());
        session.add("id_token", new JsonObject());
        acceptConsentRequestBody.add("session", session);

        queryParams = new HashMap<>();
        queryParams.put("consentChallenge", consentChallenge);

        JsonObject acceptConsentRequestResponse = OAuthAPIHelper.acceptConsentRequest(main, queryParams,
                acceptConsentRequestBody);

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

        JsonObject tokenResponse = OAuthAPIHelper.token(main, tokenRequestBody);
        return tokenResponse;
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

    private JsonObject refreshToken(Main main, JsonObject client, String refreshToken) throws Exception {
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

    private JsonObject introspectToken(Main main, String token) throws Exception {
        JsonObject introspectRequestBody = new JsonObject();
        introspectRequestBody.addProperty("token", token);
        return OAuthAPIHelper.introspect(main, introspectRequestBody);
    }

    private JsonObject createClient(Main main) throws Exception {
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

        JsonObject client = OAuthAPIHelper.createClient(main, clientBody);
        return client;
    }
}
