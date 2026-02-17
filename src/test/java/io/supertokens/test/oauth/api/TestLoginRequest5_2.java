package io.supertokens.test.oauth.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;

public class TestLoginRequest5_2 {
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
    public void testLoginRequestCreationAndGet() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.OAUTH });

        JsonObject client = createClient(process.getProcess());
        String clientId = client.get("clientId").getAsString();

        JsonObject authResponse = authRequest(process.getProcess(), clientId);

        assertEquals("OK", authResponse.get("status").getAsString());
        assertEquals(3, authResponse.entrySet().size());
        assertTrue(authResponse.has("redirectTo"));
        assertTrue(authResponse.has("cookies"));

        String cookies = authResponse.get("cookies").getAsJsonArray().get(0).getAsString();
        cookies = cookies.split(";")[0];
        assertTrue(cookies.startsWith("st_oauth_login_csrf_dev_"));

        String redirectTo = authResponse.get("redirectTo").getAsString();
        assertTrue(redirectTo.startsWith("{apiDomain}"));

        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001");
        Map<String, String> queryParams = Utils.splitQueryString(new URL(redirectTo).getQuery());
        assertEquals(1, queryParams.size());

        String loginChallenge = queryParams.get("login_challenge");
        assertNotNull(loginChallenge);

        JsonObject loginRequestResponse = getLoginRequest(process.getProcess(), loginChallenge);

        assertEquals(10, loginRequestResponse.entrySet().size());
        assertEquals("OK", loginRequestResponse.get("status").getAsString());
        assertTrue(loginRequestResponse.has("challenge"));
        assertTrue(loginRequestResponse.has("requestedScope"));
        assertTrue(loginRequestResponse.has("requestedAccessTokenAudience"));
        assertFalse(loginRequestResponse.get("skip").getAsBoolean());
        assertEquals("", loginRequestResponse.get("subject").getAsString());
        assertTrue(loginRequestResponse.has("oidcContext"));
        assertTrue(loginRequestResponse.has("client"));
        assertTrue(loginRequestResponse.has("requestUrl"));
        assertTrue(loginRequestResponse.has("sessionId"));

        JsonArray requestedScope = loginRequestResponse.getAsJsonArray("requestedScope");
        assertEquals(2, requestedScope.size());
        assertTrue(requestedScope.contains(new JsonPrimitive("openid")));
        assertTrue(requestedScope.contains(new JsonPrimitive("offline_access")));

        JsonArray requestedAccessTokenAudience = loginRequestResponse.getAsJsonArray("requestedAccessTokenAudience");
        assertEquals(0, requestedAccessTokenAudience.size());

        JsonObject clientInResponse = loginRequestResponse.getAsJsonObject("client");
        verifyClientStructure(clientInResponse);

        String oauthHost = System.getProperty("ST_OAUTH_PROVIDER_SERVICE_HOST", System.getenv().getOrDefault("TEST_OAUTH_HOST", "localhost"));
        String oauthPort = System.getProperty("ST_OAUTH_PROVIDER_SERVICE_PORT", System.getenv().getOrDefault("TEST_OAUTH_PUBLIC_PORT", "4444"));
        String expectedRequestUrlPrefix = "http://" + oauthHost + ":" + oauthPort + "/oauth2/auth?";
        assertTrue(loginRequestResponse.get("requestUrl").getAsString().startsWith(expectedRequestUrlPrefix));
        assertTrue(loginRequestResponse.has("sessionId"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLoginRequestGetWithDeletedClient() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.OAUTH });

        JsonObject client = createClient(process.getProcess());
        String clientId = client.get("clientId").getAsString();

        JsonObject authResponse = authRequest(process.getProcess(), clientId);

        assertEquals("OK", authResponse.get("status").getAsString());
        assertEquals(3, authResponse.entrySet().size());
        assertTrue(authResponse.has("redirectTo"));
        assertTrue(authResponse.has("cookies"));

        String cookies = authResponse.get("cookies").getAsJsonArray().get(0).getAsString();
        cookies = cookies.split(";")[0];
        assertTrue(cookies.startsWith("st_oauth_login_csrf_dev_"));

        String redirectTo = authResponse.get("redirectTo").getAsString();
        assertTrue(redirectTo.startsWith("{apiDomain}"));

        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001");
        Map<String, String> queryParams = Utils.splitQueryString(new URL(redirectTo).getQuery());
        assertEquals(1, queryParams.size());

        String loginChallenge = queryParams.get("login_challenge");
        assertNotNull(loginChallenge);

        deleteClient(process.getProcess(), clientId);

        JsonObject loginRequestResponse = getLoginRequest(process.getProcess(), loginChallenge);

        assertEquals("CLIENT_NOT_FOUND_ERROR", loginRequestResponse.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAcceptLoginRequest() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.OAUTH });

        JsonObject client = createClient(process.getProcess());
        String clientId = client.get("clientId").getAsString();

        JsonObject authResponse = authRequest(process.getProcess(), clientId);

        assertEquals("OK", authResponse.get("status").getAsString());
        assertEquals(3, authResponse.entrySet().size());
        assertTrue(authResponse.has("redirectTo"));
        assertTrue(authResponse.has("cookies"));

        String cookies = authResponse.get("cookies").getAsJsonArray().get(0).getAsString();
        cookies = cookies.split(";")[0];
        assertTrue(cookies.startsWith("st_oauth_login_csrf_dev_"));

        String redirectTo = authResponse.get("redirectTo").getAsString();
        assertTrue(redirectTo.startsWith("{apiDomain}"));

        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001");
        Map<String, String> queryParams = Utils.splitQueryString(new URL(redirectTo).getQuery());
        assertEquals(1, queryParams.size());

        String loginChallenge = queryParams.get("login_challenge");
        assertNotNull(loginChallenge);

        JsonObject acceptResponse = acceptLoginRequest(process.getProcess(), loginChallenge);

        assertEquals("OK", acceptResponse.get("status").getAsString());
        System.out.println(acceptResponse);

        redirectTo = acceptResponse.get("redirectTo").getAsString();
        assertTrue(redirectTo.startsWith("{apiDomain}"));

        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001");
        queryParams = Utils.splitQueryString(new URL(redirectTo).getQuery());
        assertEquals(6, queryParams.size());

        String loginVerifier = queryParams.get("login_verifier");
        assertNotNull(loginVerifier);

        String expectedRedirectUri = "http://localhost.com:3000/auth/callback/supertokens";
        String expectedResponseType = "code";
        String expectedScope = "openid offline_access";
        String expectedState = "test12345678";

        assertEquals(clientId, queryParams.get("client_id"));
        assertEquals(expectedRedirectUri, queryParams.get("redirect_uri"));
        assertEquals(expectedResponseType, queryParams.get("response_type"));
        assertEquals(expectedScope, queryParams.get("scope"));
        assertEquals(expectedState, queryParams.get("state"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAcceptNonExistantLoginRequest() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.OAUTH });

        JsonObject client = createClient(process.getProcess());
        String clientId = client.get("clientId").getAsString();

        JsonObject authResponse = authRequest(process.getProcess(), clientId);

        assertEquals("OK", authResponse.get("status").getAsString());
        assertEquals(3, authResponse.entrySet().size());
        assertTrue(authResponse.has("redirectTo"));
        assertTrue(authResponse.has("cookies"));

        String cookies = authResponse.get("cookies").getAsJsonArray().get(0).getAsString();
        cookies = cookies.split(";")[0];
        assertTrue(cookies.startsWith("st_oauth_login_csrf_dev_"));

        String redirectTo = authResponse.get("redirectTo").getAsString();
        assertTrue(redirectTo.startsWith("{apiDomain}"));

        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001");
        Map<String, String> queryParams = Utils.splitQueryString(new URL(redirectTo).getQuery());
        assertEquals(1, queryParams.size());

        String loginChallenge = queryParams.get("login_challenge");
        assertNotNull(loginChallenge);

        JsonObject acceptResponse = acceptLoginRequest(process.getProcess(), loginChallenge + "extras");

        assertEquals("OAUTH_ERROR", acceptResponse.get("status").getAsString());
        assertEquals("Not Found", acceptResponse.get("error").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRejectLoginRequest() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[] { EE_FEATURES.OAUTH });

        JsonObject client = createClient(process.getProcess());
        String clientId = client.get("clientId").getAsString();

        JsonObject authResponse = authRequest(process.getProcess(), clientId);

        assertEquals("OK", authResponse.get("status").getAsString());
        assertEquals(3, authResponse.entrySet().size());
        assertTrue(authResponse.has("redirectTo"));
        assertTrue(authResponse.has("cookies"));

        String cookies = authResponse.get("cookies").getAsJsonArray().get(0).getAsString();
        cookies = cookies.split(";")[0];
        assertTrue(cookies.startsWith("st_oauth_login_csrf_dev_"));

        String redirectTo = authResponse.get("redirectTo").getAsString();
        assertTrue(redirectTo.startsWith("{apiDomain}"));

        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001");
        Map<String, String> queryParams = Utils.splitQueryString(new URL(redirectTo).getQuery());
        assertEquals(1, queryParams.size());

        String loginChallenge = queryParams.get("login_challenge");
        assertNotNull(loginChallenge);

        JsonObject rejectResponse = rejectLoginRequest(process.getProcess(), loginChallenge);

        assertEquals("OK", rejectResponse.get("status").getAsString());
        redirectTo = rejectResponse.get("redirectTo").getAsString();
        assertTrue(redirectTo.startsWith("{apiDomain}"));

        redirectTo = redirectTo.replace("{apiDomain}", "http://localhost:3001");
        queryParams = Utils.splitQueryString(new URL(redirectTo).getQuery());
        assertEquals(6, queryParams.size());

        assertTrue(queryParams.containsKey("client_id"));
        assertTrue(queryParams.containsKey("login_verifier"));
        assertTrue(queryParams.containsKey("redirect_uri"));
        assertTrue(queryParams.containsKey("response_type"));
        assertTrue(queryParams.containsKey("scope"));
        assertTrue(queryParams.containsKey("state"));

        assertEquals(clientId, queryParams.get("client_id"));
        assertEquals("http://localhost.com:3000/auth/callback/supertokens", queryParams.get("redirect_uri"));
        assertEquals("code", queryParams.get("response_type"));
        assertEquals("openid offline_access", queryParams.get("scope"));
        assertEquals("test12345678", queryParams.get("state"));

        // Verify login_verifier is present and not empty
        assertNotNull(queryParams.get("login_verifier"));
        assertFalse(queryParams.get("login_verifier").isEmpty());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private JsonObject acceptLoginRequest(Main main, String loginChallenge) throws Exception {
        Map<String, String> acceptLoginRequestParams = new HashMap<>();
        acceptLoginRequestParams.put("loginChallenge", loginChallenge);

        JsonObject acceptLoginRequestBody = new JsonObject();
        acceptLoginRequestBody.addProperty("subject", "someuserid");
        acceptLoginRequestBody.addProperty("remember", true);
        acceptLoginRequestBody.addProperty("rememberFor", 3600);
        acceptLoginRequestBody.addProperty("identityProviderSessionId", "session-handle");

        String url = "http://localhost:3567/recipe/oauth/auth/requests/login/accept?loginChallenge=" + URLEncoder.encode(loginChallenge, StandardCharsets.UTF_8.toString());

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                url, acceptLoginRequestBody, 5000, 5000, null,
                SemVer.v5_2.get(), "");
        return response;
	}

    private JsonObject rejectLoginRequest(Main main, String loginChallenge) throws Exception {
        Map<String, String> rejectLoginRequestParams = new HashMap<>();
        rejectLoginRequestParams.put("loginChallenge", loginChallenge);

        JsonObject acceptLoginRequestBody = new JsonObject();

        String url = "http://localhost:3567/recipe/oauth/auth/requests/login/reject?loginChallenge=" + URLEncoder.encode(loginChallenge, StandardCharsets.UTF_8.toString());

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                url, acceptLoginRequestBody, 5000, 5000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

	private JsonObject createClient(Main main) throws Exception {
        JsonObject createClientBody = new JsonObject();
        JsonArray grantTypes = new JsonArray();
        grantTypes.add(new JsonPrimitive("authorization_code"));
        grantTypes.add(new JsonPrimitive("refresh_token"));
        createClientBody.add("grantTypes", grantTypes);
        JsonArray responseTypes = new JsonArray();
        responseTypes.add(new JsonPrimitive("code"));
        responseTypes.add(new JsonPrimitive("id_token"));
        createClientBody.add("responseTypes", responseTypes);
        JsonArray redirectUris = new JsonArray();
        redirectUris.add(new JsonPrimitive("http://localhost.com:3000/auth/callback/supertokens"));
        createClientBody.add("redirectUris", redirectUris);
        createClientBody.addProperty("scope", "openid email offline_access");
        createClientBody.addProperty("tokenEndpointAuthMethod", "client_secret_post");

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/oauth/clients", createClientBody, 1000, 1000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    private JsonObject authRequest(Main main, String clientId) throws Exception {
        JsonObject authRequestBody = new JsonObject();
        JsonObject params = new JsonObject();
        params.addProperty("client_id", clientId);
        params.addProperty("redirect_uri", "http://localhost.com:3000/auth/callback/supertokens");
        params.addProperty("response_type", "code");
        params.addProperty("scope", "openid offline_access");
        params.addProperty("state", "test12345678");
        authRequestBody.add("params", params);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/oauth/auth", authRequestBody, 5000, 5000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    private JsonObject getLoginRequest(Main main, String loginChallenge) throws Exception {
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("loginChallenge", loginChallenge);
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                "http://localhost:3567/recipe/oauth/auth/requests/login", queryParams, 5000, 5000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    private JsonObject deleteClient(Main main, String clientId) throws Exception {
        JsonObject body = new JsonObject(); 
        body.addProperty("clientId", clientId);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/oauth/clients/remove", body, 1000, 1000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    private void verifyClientStructure(JsonObject response) throws Exception {
        assertEquals(27, response.entrySet().size());
        String[] FIELDS = new String[]{
                "clientId",
                "clientSecret",
                "clientName",
                "scope",
                "redirectUris",
                "enableRefreshTokenRotation",
                "authorizationCodeGrantAccessTokenLifespan",
                "authorizationCodeGrantIdTokenLifespan",
                "authorizationCodeGrantRefreshTokenLifespan",
                "clientCredentialsGrantAccessTokenLifespan",
                "implicitGrantAccessTokenLifespan",
                "implicitGrantIdTokenLifespan",
                "refreshTokenGrantAccessTokenLifespan",
                "refreshTokenGrantIdTokenLifespan",
                "refreshTokenGrantRefreshTokenLifespan",
                "tokenEndpointAuthMethod",
                "clientUri",
                "allowedCorsOrigins",
                "audience",
                "grantTypes",
                "responseTypes",
                "logoUri",
                "policyUri",
                "tosUri",
                "createdAt",
                "updatedAt",
                "metadata"
        };

        for (String field : FIELDS) {
            assertTrue(response.has(field));
        }
    }

}
