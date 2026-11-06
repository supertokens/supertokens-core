package io.supertokens.test.saml.api;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.ProcessState;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.saml.MockSAML;
import io.supertokens.test.saml.SAMLTestUtils;
import io.supertokens.utils.SemVer;
import org.apache.catalina.Valve;
import org.apache.catalina.valves.ErrorReportValve;
import io.supertokens.webserver.Webserver;

public class LegacyTest5_4 {

    private static final String TEST_REDIRECT_URI = "http://localhost:3000/auth/callback/saml-mock";

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() throws IOException {
        Utils.reset();
        SAMLTestUtils.setSAMLSPEntityIdConfig();
        // Set the legacy ACS URL for testing
        Utils.setValueInConfig("saml_legacy_acs_url", "http://localhost:3567/recipe/saml/legacy/callback");
    }

    // ========== LegacyAuthorizeAPI Tests ==========

    @Test
    public void testLegacyAuthorizeBadInput() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Missing client_id
        {
            Map<String, String> params = new HashMap<>();
            params.put("redirect_uri", TEST_REDIRECT_URI);
            params.put("state", "test-state");
            
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/legacy/authorize", params, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Field name 'client_id' is missing in GET request", e.getMessage());
            }
        }

        // Missing redirect_uri
        {
            Map<String, String> params = new HashMap<>();
            params.put("client_id", "test-client");
            params.put("state", "test-state");
            
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/legacy/authorize", params, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Field name 'redirect_uri' is missing in GET request", e.getMessage());
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLegacyAuthorizeInvalidClient() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Test with non-existent client_id
        Map<String, String> params = new HashMap<>();
        params.put("client_id", "non-existent-client");
        params.put("redirect_uri", TEST_REDIRECT_URI);
        params.put("state", "test-state");
        
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/legacy/authorize", params, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("INVALID_CLIENT_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLegacyAuthorizeValidClient() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Create SAML client
        String spEntityId = "http://example.com/saml";
        String defaultRedirectURI = "http://localhost:3000/auth/callback/saml-mock";
        String acsURL = "http://localhost:3000/acs";
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                defaultRedirectURI,
                acsURL,
                idpEntityId,
                idpSsoUrl
        );

        // Test valid authorization request
        String redirectURI = TEST_REDIRECT_URI; // Use the same redirect URI as configured in the client
        String state = "test-state-123";

        // Create query parameters map
        Map<String, String> params = new HashMap<>();
        params.put("client_id", clientInfo.clientId);
        params.put("redirect_uri", redirectURI);
        params.put("state", state);

        // This should redirect to SSO URL, so we expect a 307 redirect
        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/legacy/authorize", params, 1000, 1000, null, SemVer.v5_4.get(), "saml");
            fail("Expected redirect response");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(307, e.statusCode);
            // Verify the redirect URL contains expected parameters
            String location = e.getMessage();
            assertNotNull(location);
            assertNotNull("Location header should contain SSO URL", location);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // ========== LegacyCallbackAPI Tests ==========

    @Test
    public void testLegacyCallbackBadInput() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Missing SAMLResponse
        {
            try {
                HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/legacy/callback", new JsonObject(), 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Missing form field: SAMLResponse", e.getMessage());
            }
        }

        // Empty SAMLResponse
        {
            JsonObject formData = new JsonObject();
            formData.addProperty("SAMLResponse", "");
            try {
                HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/legacy/callback", formData, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Missing form field: SAMLResponse", e.getMessage());
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLegacyCallbackInvalidRelayState() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        String spEntityId = "http://example.com/saml";
        String defaultRedirectURI = "http://localhost:3000/auth/callback/saml-mock";
        String acsURL = "http://localhost:3000/acs";
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                defaultRedirectURI,
                acsURL,
                idpEntityId,
                idpSsoUrl
        );

        String samlResponseBase64 = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId,
                "https://saml.supertokens.com",
                clientInfo.acsURL,
                "user@example.com",
                null,
                null,
                clientInfo.keyMaterial,
                300
        );

        JsonObject formData = new JsonObject();
        formData.addProperty("SAMLResponse", samlResponseBase64);
        formData.addProperty("RelayState", "invalid-relay-state");

        try {
            String response = HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/legacy/callback", formData, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: INVALID_RELAY_STATE_ERROR", e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLegacyCallbackValidResponse() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        String spEntityId = "http://example.com/saml";
        String defaultRedirectURI = "http://localhost:3000/auth/callback/saml-mock";
        String acsURL = "http://localhost:3000/acs";
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                defaultRedirectURI,
                acsURL,
                idpEntityId,
                idpSsoUrl
        );

        // Create a login request to generate a RelayState
        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process,
                clientInfo.clientId,
                clientInfo.defaultRedirectURI,
                clientInfo.acsURL,
                "test-state"
        );

        String samlResponseBase64 = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId,
                "https://saml.supertokens.com",
                clientInfo.acsURL,
                "user@example.com",
                null,
                SAMLTestUtils.requestIdFor(relayState),
                clientInfo.keyMaterial,
                300
        );

        JsonObject formData = new JsonObject();
        formData.addProperty("SAMLResponse", samlResponseBase64);
        formData.addProperty("RelayState", relayState);

        // This should redirect to the callback URL with authorization code
        try {
            HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/legacy/callback", formData, 1000, 1000, null, SemVer.v5_4.get(), "saml", false);
            fail("Expected redirect response");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(302, e.statusCode);
            String location = e.getMessage();
            assertNotNull(location);
            assertNotNull("Location header should contain redirect URI", location);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // ========== LegacyTokenAPI Tests ==========

    @Test
    public void testLegacyTokenBadInput() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Create SAML client
        String spEntityId = "http://example.com/saml";
        String defaultRedirectURI = "http://localhost:3000/auth/callback/saml-mock";
        String acsURL = "http://localhost:3000/acs";
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                defaultRedirectURI,
                acsURL,
                idpEntityId,
                idpSsoUrl
        );

        // Missing client_id
        {
            JsonObject formData = new JsonObject();
            formData.addProperty("client_secret", clientInfo.clientId); // In legacy API, client_secret is same as client_id
            formData.addProperty("code", "test-code");
            try {
                HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/legacy/token", formData, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Missing form field: client_id", e.getMessage());
            }
        }

        // Missing client_secret
        {
            JsonObject formData = new JsonObject();
            formData.addProperty("client_id", clientInfo.clientId);
            formData.addProperty("code", "test-code");
            try {
                HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/legacy/token", formData, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Missing form field: client_secret", e.getMessage());
            }
        }

        // Missing code
        {
            JsonObject formData = new JsonObject();
            formData.addProperty("client_id", clientInfo.clientId);
            formData.addProperty("client_secret", clientInfo.clientId); // In legacy API, client_secret is same as client_id
            try {
                HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/legacy/token", formData, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Missing form field: code", e.getMessage());
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLegacyTokenInvalidClient() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        JsonObject formData = new JsonObject();
        formData.addProperty("client_id", "non-existent-client");
        formData.addProperty("client_secret", "test-secret");
        formData.addProperty("code", "test-code");

        try {
            HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/legacy/token", formData, 1000, 1000, null, SemVer.v5_4.get(), "saml");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Invalid client_id", e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLegacyTokenInvalidSecret() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Create SAML client
        String spEntityId = "http://example.com/saml";
        String defaultRedirectURI = "http://localhost:3000/auth/callback/saml-mock";
        String acsURL = "http://localhost:3000/acs";
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                defaultRedirectURI,
                acsURL,
                idpEntityId,
                idpSsoUrl
        );

        JsonObject formData = new JsonObject();
        formData.addProperty("client_id", clientInfo.clientId);
        formData.addProperty("client_secret", "wrong-secret");
        formData.addProperty("code", "test-code");

        try {
            HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/legacy/token", formData, 1000, 1000, null, SemVer.v5_4.get(), "saml");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Invalid client_secret", e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLegacyTokenValidRequest() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Create SAML client
        String spEntityId = "http://example.com/saml";
        String defaultRedirectURI = "http://localhost:3000/auth/callback/saml-mock";
        String acsURL = "http://localhost:3000/acs";
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                defaultRedirectURI,
                acsURL,
                idpEntityId,
                idpSsoUrl
        );

        // Create a login request to generate a RelayState
        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process,
                clientInfo.clientId,
                clientInfo.defaultRedirectURI,
                clientInfo.acsURL,
                "test-state"
        );

        String samlResponseBase64 = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId,
                "https://saml.supertokens.com",
                clientInfo.acsURL,
                "user@example.com",
                null,
                SAMLTestUtils.requestIdFor(relayState),
                clientInfo.keyMaterial,
                300
        );

        // Process callback to get authorization code
        JsonObject callbackFormData = new JsonObject();
        callbackFormData.addProperty("SAMLResponse", samlResponseBase64);
        callbackFormData.addProperty("RelayState", relayState);

        String redirectURI = null;
        try {
            HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/legacy/callback", callbackFormData, 1000, 1000, null, SemVer.v5_4.get(), "saml", false);
            fail("Expected redirect response");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(302, e.statusCode);
            redirectURI = e.getMessage();
        }

        // Extract authorization code from redirect URI
        String authCode = extractAuthCodeFromRedirectURI(redirectURI);

        // Now test token exchange
        JsonObject tokenFormData = new JsonObject();
        tokenFormData.addProperty("client_id", clientInfo.clientId);
        tokenFormData.addProperty("client_secret", "secret");
        tokenFormData.addProperty("code", authCode);

        JsonObject tokenResponse = HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/legacy/token", tokenFormData, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("OK", tokenResponse.get("status").getAsString());
        assertNotNull(tokenResponse.get("access_token"));
        String accessToken = tokenResponse.get("access_token").getAsString();
        // The access token is a fresh random value, not derived from the code or the client id
        assertFalse(accessToken.contains(authCode));
        assertFalse(accessToken.contains(clientInfo.clientId));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // ========== LegacyUserinfoAPI Tests ==========

    @Test
    public void testLegacyUserinfoBadInput() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Missing Authorization header
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/legacy/userinfo", null, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Authorization header is required", e.getMessage());
            }
        }

        // Invalid Authorization header format
        {
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/legacy/userinfo", null, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Authorization header is required", e.getMessage());
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLegacyUserinfoInvalidToken() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer invalid-token");
            JsonObject response = HttpRequestForTesting.sendGETRequestWithHeaders(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/legacy/userinfo", null, headers, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: INVALID_TOKEN_ERROR", e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLegacyUserinfoValidToken() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Create SAML client
        String spEntityId = "http://example.com/saml";
        String defaultRedirectURI = "http://localhost:3000/auth/callback/saml-mock";
        String acsURL = "http://localhost:3000/acs";
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                defaultRedirectURI,
                acsURL,
                idpEntityId,
                idpSsoUrl
        );

        // Create a login request to generate a RelayState
        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process,
                clientInfo.clientId,
                clientInfo.defaultRedirectURI,
                clientInfo.acsURL,
                "test-state"
        );

        String samlResponseBase64 = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId,
                "https://saml.supertokens.com",
                clientInfo.acsURL,
                "user@example.com",
                null,
                SAMLTestUtils.requestIdFor(relayState),
                clientInfo.keyMaterial,
                300
        );

        // Process callback to get authorization code
        JsonObject callbackFormData = new JsonObject();
        callbackFormData.addProperty("SAMLResponse", samlResponseBase64);
        callbackFormData.addProperty("RelayState", relayState);

        String redirectURI = null;
        try {
            HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/legacy/callback", callbackFormData, 1000, 1000, null, SemVer.v5_4.get(), "saml", false);
            fail("Expected redirect response");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(302, e.statusCode);
            redirectURI = e.getMessage();
        }

        // Extract authorization code from redirect URI
        String authCode = extractAuthCodeFromRedirectURI(redirectURI);

        // Exchange code for access token
        JsonObject tokenFormData = new JsonObject();
        tokenFormData.addProperty("client_id", clientInfo.clientId);
        tokenFormData.addProperty("client_secret", "secret");
        tokenFormData.addProperty("code", authCode);

        JsonObject tokenResponse = HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/legacy/token", tokenFormData, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("OK", tokenResponse.get("status").getAsString());

        String accessToken = tokenResponse.get("access_token").getAsString();

        // Now test userinfo with valid access token
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + accessToken);
        JsonObject userInfoResponse = HttpRequestForTesting.sendGETRequestWithHeaders(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/legacy/userinfo", null, headers, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertNotNull(userInfoResponse.get("id"));
        assertEquals("user@example.com", userInfoResponse.get("id").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // ========== Core with API keys configured ==========

    // The legacy flow is an OAuth provider facade: authorize and callback are opened by the end user's browser
    // and token / userinfo are called by the relying party's OAuth client. None of them holds the core API key,
    // so the whole flow must work without it on a core that has api_keys configured. The non-legacy SAML APIs,
    // called by the backend SDK, still require the key.
    @Test
    public void testLegacyFlowWorksWithoutApiKeyWhenApiKeysAreConfigured() throws Exception {
        String apiKey = "legacy-saml-test-api-key-0123456789";
        Utils.setValueInConfig("api_keys", apiKey);
        TestingProcessManager.TestingProcess process = startWithSAML();

        String legacyACSURL = "http://localhost:3567/recipe/saml/legacy/callback";
        String idpEntityId = "https://saml.example.com/entityid";
        MockSAML.KeyMaterial keyMaterial = MockSAML.generateSelfSignedKeyMaterial();
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, "https://mocksaml.com/api/saml/sso",
                keyMaterial.certificate);

        JsonObject createClientInput = new JsonObject();
        createClientInput.addProperty("clientSecret", "secret");
        createClientInput.addProperty("defaultRedirectURI", TEST_REDIRECT_URI);
        JsonArray redirectURIs = new JsonArray();
        redirectURIs.add(TEST_REDIRECT_URI);
        createClientInput.add("redirectURIs", redirectURIs);
        createClientInput.addProperty("metadataXML", java.util.Base64.getEncoder()
                .encodeToString(metadataXML.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        createClientInput.addProperty("allowIDPInitiatedLogin", false);

        // The client API is called by the backend SDK and still requires the key.
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                    SemVer.v5_4.get(), "saml");
            fail("Expected the client API to require the API key");
        } catch (HttpResponseException e) {
            assertEquals(401, e.statusCode);
        }
        JsonObject createResp = HttpRequestForTesting.sendJsonRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                SemVer.v5_4.get(), "PUT", apiKey, "saml");
        String clientId = createResp.get("clientId").getAsString();

        // authorize, opened by the browser without the key: redirects to the IdP.
        Map<String, String> params = new HashMap<>();
        params.put("client_id", clientId);
        params.put("redirect_uri", TEST_REDIRECT_URI);
        params.put("state", "test-state");
        String ssoRedirectURI = null;
        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/legacy/authorize", params, 1000, 1000, null,
                    SemVer.v5_4.get(), "saml");
            fail("Expected redirect response");
        } catch (HttpResponseException e) {
            assertEquals(307, e.statusCode);
            ssoRedirectURI = e.getMessage();
        }
        SAMLTestUtils.LoginInfo loginInfo = SAMLTestUtils.loginInfoFromSSORedirect(ssoRedirectURI);

        // callback, the IdP's form POST through the browser without the key: redirects with a code.
        String samlResponseBase64 = MockSAML.generateSignedSAMLResponseBase64(
                idpEntityId,
                SAMLTestUtils.TEST_SP_ENTITY_ID,
                legacyACSURL,
                "user@example.com",
                null,
                loginInfo.requestId,
                keyMaterial,
                300
        );
        JsonObject callbackFormData = new JsonObject();
        callbackFormData.addProperty("SAMLResponse", samlResponseBase64);
        callbackFormData.addProperty("RelayState", loginInfo.relayState);
        String code = null;
        try {
            HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                    legacyACSURL, callbackFormData, 1000, 1000, null, SemVer.v5_4.get(), "saml", false);
            fail("Expected redirect response");
        } catch (HttpResponseException e) {
            assertEquals(302, e.statusCode);
            assertTrue(e.getMessage(), e.getMessage().contains(TEST_REDIRECT_URI + "?code="));
            code = extractAuthCodeFromRedirectURI(e.getMessage());
        }

        // token and userinfo, called by the relying party's OAuth client without the key.
        JsonObject tokenResponse = exchangeCode(process, clientId, "secret", code);
        assertEquals("OK", tokenResponse.get("status").getAsString());
        JsonObject userInfo = callUserinfo(process, "Bearer " + tokenResponse.get("access_token").getAsString());
        assertEquals("user@example.com", userInfo.get("id").getAsString());

        // The non-legacy login API, called by the backend SDK, still requires the key.
        JsonObject loginInput = new JsonObject();
        loginInput.addProperty("clientId", clientId);
        loginInput.addProperty("redirectURI", TEST_REDIRECT_URI);
        loginInput.addProperty("acsURL", legacyACSURL);
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/login", loginInput, 1000, 1000, null,
                    SemVer.v5_4.get(), "saml");
            fail("Expected the login API to require the API key");
        } catch (HttpResponseException e) {
            assertEquals(401, e.statusCode);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // ========== Code / token exchange hardening ==========

    private static final String TOKEN_URL = "http://localhost:3567/recipe/saml/legacy/token";
    private static final String USERINFO_URL = "http://localhost:3567/recipe/saml/legacy/userinfo";

    private TestingProcessManager.TestingProcess startWithSAML() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});
        return process;
    }

    private SAMLTestUtils.CreatedClientInfo createLegacyClient(TestingProcessManager.TestingProcess process,
                                                               String idpEntityId) throws Exception {
        return SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                TEST_REDIRECT_URI,
                "http://localhost:3000/acs",
                idpEntityId,
                "https://mocksaml.com/api/saml/sso"
        );
    }

    // Runs a legacy SP-initiated login through the callback and returns the code from the redirect.
    private String loginAndGetCode(TestingProcessManager.TestingProcess process,
                                   SAMLTestUtils.CreatedClientInfo clientInfo, String email) throws Exception {
        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process, clientInfo.clientId, clientInfo.defaultRedirectURI, clientInfo.acsURL, "test-state");
        String samlResponseBase64 = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId,
                "https://saml.supertokens.com",
                clientInfo.acsURL,
                email,
                null,
                SAMLTestUtils.requestIdFor(relayState),
                clientInfo.keyMaterial,
                300
        );
        JsonObject callbackFormData = new JsonObject();
        callbackFormData.addProperty("SAMLResponse", samlResponseBase64);
        callbackFormData.addProperty("RelayState", relayState);
        try {
            HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/legacy/callback", callbackFormData, 1000, 1000, null,
                    SemVer.v5_4.get(), "saml", false);
            fail("Expected redirect response");
            return null;
        } catch (HttpResponseException e) {
            assertEquals(302, e.statusCode);
            return extractAuthCodeFromRedirectURI(e.getMessage());
        }
    }

    private JsonObject exchangeCode(TestingProcessManager.TestingProcess process, String clientId,
                                    String clientSecret, String code) throws Exception {
        JsonObject tokenFormData = new JsonObject();
        tokenFormData.addProperty("client_id", clientId);
        tokenFormData.addProperty("client_secret", clientSecret);
        tokenFormData.addProperty("code", code);
        return HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                TOKEN_URL, tokenFormData, 5000, 5000, null, SemVer.v5_4.get(), "saml");
    }

    private void assertExchangeFails(TestingProcessManager.TestingProcess process, String clientId,
                                     String clientSecret, String code, String expectedMessage) throws Exception {
        try {
            exchangeCode(process, clientId, clientSecret, code);
            fail("Expected the code exchange to fail");
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: " + expectedMessage, e.getMessage());
        }
    }

    private JsonObject callUserinfo(TestingProcessManager.TestingProcess process, String authorizationHeader)
            throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", authorizationHeader);
        return HttpRequestForTesting.sendGETRequestWithHeaders(process.getProcess(), "",
                USERINFO_URL, null, headers, 1000, 1000, null, SemVer.v5_4.get(), "saml");
    }

    private void assertUserinfoRejected(TestingProcessManager.TestingProcess process, String authorizationHeader)
            throws Exception {
        try {
            callUserinfo(process, authorizationHeader);
            fail("Expected userinfo to reject: " + authorizationHeader);
        } catch (HttpResponseException e) {
            assertEquals("for " + authorizationHeader, 400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: INVALID_TOKEN_ERROR", e.getMessage());
        }
    }

    // Regression: a malformed bearer token must be rejected with a clean 400 INVALID_TOKEN_ERROR
    // from an unauthenticated caller and must NOT surface an unhandled 500 (previously an
    // ArrayIndexOutOfBoundsException from split()[1]). Covers the token shapes that fail the
    // "<code>.<clientId>" guard: no dot at all ("no-dot-token"), an empty code (a leading "."
    // or a lone "."), and an empty clientId (a trailing "abc."). "abc.." now parses to
    // code="abc"/clientId="." and is rejected downstream as an invalid code — still a 400, never a 500.
    @Test
    public void testLegacyUserinfoMalformedTokenIsRejectedWithout500() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        for (String malformed : new String[]{"abc.", ".", ".abc", "abc..", "no-dot-token"}) {
            try {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + malformed);
                HttpRequestForTesting.sendGETRequestWithHeaders(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/legacy/userinfo", null, headers, 1000, 1000, null,
                        SemVer.v5_4.get(), "saml");
                fail("Expected a 400 for malformed token: \"" + malformed + "\"");
            } catch (HttpResponseException e) {
                assertEquals("token \"" + malformed + "\" must not cause a 500", 400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: INVALID_TOKEN_ERROR", e.getMessage());
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A caller-supplied clientId may contain dots. The legacy access token is opaque (it no longer embeds the
    // clientId), so a dotted clientId must still complete the code exchange and the userinfo call.
    @Test
    public void testLegacyUserinfoPreservesDottedClientId() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        String dottedClientId = "st_saml_my.dotted.client";
        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                TEST_REDIRECT_URI,
                "http://localhost:3000/acs",
                "https://saml.example.com/entityid",
                "https://mocksaml.com/api/saml/sso",
                false,
                dottedClientId
        );
        assertEquals(dottedClientId, clientInfo.clientId);

        String code = loginAndGetCode(process, clientInfo, "user@example.com");
        JsonObject tokenResponse = exchangeCode(process, clientInfo.clientId, "secret", code);
        assertEquals("OK", tokenResponse.get("status").getAsString());

        String accessToken = tokenResponse.get("access_token").getAsString();
        assertFalse("the token must not embed the clientId", accessToken.contains(dottedClientId));

        JsonObject userInfoResponse = callUserinfo(process, "Bearer " + accessToken);
        assertEquals("user@example.com", userInfoResponse.get("id").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Regression for the Tomcat error-response hardening: the embedded Tomcat must register an
    // ErrorReportValve on the host pipeline with both the server build string and the exception
    // report turned off, so connector-level / pre-dispatch errors (which bypass the servlet's own
    // catch-all and are rendered by this valve) cannot disclose the "Apache Tomcat/<version>" build
    // string or stack frames to unauthenticated callers. Those pre-dispatch errors are awkward to
    // trigger deterministically over the wire from this harness, so we lock the hardening in at the
    // configuration level: a future refactor that drops the valve or flips either flag fails here.
    @Test
    public void testTomcatErrorReportValveHidesServerInfoAndReport() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        var tomcat = Webserver.getInstance(process.getProcess()).getTomcatReference().getTomcatForTest();

        ErrorReportValve hardenedValve = null;
        for (Valve valve : tomcat.getHost().getPipeline().getValves()) {
            if (valve instanceof ErrorReportValve) {
                hardenedValve = (ErrorReportValve) valve;
            }
        }

        assertNotNull("an ErrorReportValve must be registered on the host pipeline", hardenedValve);

        // ErrorReportValve exposes setters but not getters for these flags in this Tomcat version,
        // so read the protected fields directly to assert the hardening is in effect.
        java.lang.reflect.Field showServerInfoField = ErrorReportValve.class.getDeclaredField("showServerInfo");
        showServerInfoField.setAccessible(true);
        assertEquals(false, showServerInfoField.getBoolean(hardenedValve));

        java.lang.reflect.Field showReportField = ErrorReportValve.class.getDeclaredField("showReport");
        showReportField.setAccessible(true);
        assertEquals(false, showReportField.getBoolean(hardenedValve));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testIssue3_legacyTokenRequiresValidCode() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        SAMLTestUtils.CreatedClientInfo clientA = createLegacyClient(process, "https://idp-a.example.com/entityid");
        SAMLTestUtils.CreatedClientInfo clientB = createLegacyClient(process, "https://idp-b.example.com/entityid");

        // A code that was never issued is not exchanged, even with valid client credentials
        assertExchangeFails(process, clientA.clientId, "secret", java.util.UUID.randomUUID().toString(),
                "Invalid code");
        assertExchangeFails(process, clientA.clientId, "secret", "anything", "Invalid code");

        // A code issued to client A cannot be exchanged by client B (and is burnt by the attempt)
        String codeForA = loginAndGetCode(process, clientA, "a@example.com");
        assertExchangeFails(process, clientB.clientId, "secret", codeForA, "Invalid code");
        assertExchangeFails(process, clientA.clientId, "secret", codeForA, "Invalid code");

        // A wrong client secret is rejected before the code is redeemed, so the code survives
        String code = loginAndGetCode(process, clientA, "a@example.com");
        assertExchangeFails(process, clientA.clientId, "wrong-secret", code, "Invalid client_secret");
        assertExchangeFails(process, clientA.clientId, "secre", code, "Invalid client_secret");

        // The right client gets a fresh access token, exactly once
        JsonObject tokenResponse = exchangeCode(process, clientA.clientId, "secret", code);
        assertEquals("OK", tokenResponse.get("status").getAsString());
        String accessToken = tokenResponse.get("access_token").getAsString();
        assertFalse(accessToken.contains(code));
        assertFalse(accessToken.equals(code + "." + clientA.clientId));
        assertExchangeFails(process, clientA.clientId, "secret", code, "Invalid code");

        // An access token cannot be exchanged as a code
        assertExchangeFails(process, clientA.clientId, "secret", accessToken, "Invalid code");

        JsonObject userInfo = callUserinfo(process, "Bearer " + accessToken);
        assertEquals("a@example.com", userInfo.get("id").getAsString());
        assertEquals(clientA.clientId, userInfo.get("aud").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testIssue22_userinfoRejectsRawCode() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        SAMLTestUtils.CreatedClientInfo clientInfo = createLegacyClient(process, "https://saml.example.com/entityid");
        String code = loginAndGetCode(process, clientInfo, "user@example.com");

        // Neither the code nor a value built from the code and the public client id is an access token
        assertUserinfoRejected(process, "Bearer " + code + "." + clientInfo.clientId);
        assertUserinfoRejected(process, "Bearer " + code);

        // Those attempts did not consume the code: the client can still exchange it
        String accessToken = exchangeCode(process, clientInfo.clientId, "secret", code)
                .get("access_token").getAsString();

        // A modified access token is not accepted
        assertUserinfoRejected(process, "Bearer " + accessToken + "x");

        JsonObject userInfo = callUserinfo(process, "Bearer " + accessToken);
        assertEquals("user@example.com", userInfo.get("id").getAsString());

        // The access token is single use
        assertUserinfoRejected(process, "Bearer " + accessToken);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLegacyAccessTokenNotAcceptedAsCodeByNonLegacyUserinfo() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        SAMLTestUtils.CreatedClientInfo clientInfo = createLegacyClient(process, "https://saml.example.com/entityid");
        String code = loginAndGetCode(process, clientInfo, "user@example.com");
        String accessToken = exchangeCode(process, clientInfo.clientId, "secret", code)
                .get("access_token").getAsString();

        // The non-legacy userinfo API redeems codes, never legacy access tokens (with or without the key prefix)
        for (String candidate : new String[]{accessToken, "legacy_access_token:" + accessToken}) {
            JsonObject body = new JsonObject();
            body.addProperty("accessToken", candidate);
            body.addProperty("clientId", clientInfo.clientId);
            JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/user", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
            assertEquals("INVALID_TOKEN_ERROR", resp.get("status").getAsString());
        }

        // ... so the access token is still valid at the legacy userinfo endpoint
        JsonObject userInfo = callUserinfo(process, "Bearer " + accessToken);
        assertEquals("user@example.com", userInfo.get("id").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLegacyCodeExchangedOnceUnderConcurrency() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        SAMLTestUtils.CreatedClientInfo clientInfo = createLegacyClient(process, "https://saml.example.com/entityid");

        int threads = 8;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            for (int trial = 0; trial < 5; trial++) {
                String code = loginAndGetCode(process, clientInfo, "user@example.com");
                java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(threads);
                java.util.List<java.util.concurrent.Future<Boolean>> futures = new java.util.ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    futures.add(executor.submit(() -> {
                        barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                        try {
                            exchangeCode(process, clientInfo.clientId, "secret", code);
                            return true;
                        } catch (HttpResponseException e) {
                            assertEquals(400, e.statusCode);
                            return false;
                        }
                    }));
                }
                int succeeded = 0;
                for (java.util.concurrent.Future<Boolean> f : futures) {
                    if (f.get(30, java.util.concurrent.TimeUnit.SECONDS)) {
                        succeeded++;
                    }
                }
                assertEquals("a code must be exchanged exactly once", 1, succeeded);
            }
        } finally {
            executor.shutdownNow();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testMatthewM3_userinfoMalformedTokenIsNot500() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        String[] malformed = {
                "Bearer .",
                "Bearer abc.",
                "Bearer .abc",
                "Bearer a.b.c",
                "Bearer ..",
                "Bearer  ",
                "Bearer a b",
                "Bearer %zz",
                "Bearer " + "a".repeat(5000),
        };
        for (String header : malformed) {
            try {
                callUserinfo(process, header);
                fail("Expected userinfo to reject: " + header);
            } catch (HttpResponseException e) {
                // A client error, never an internal server error
                assertEquals("for " + header, 400, e.statusCode);
                String message = e.getMessage();
                assertTrue(message, message.equals("Http error. Status Code: 400. Message: INVALID_TOKEN_ERROR")
                        || message.equals("Http error. Status Code: 400. Message: Authorization header is required"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Helper method to extract authorization code from redirect URI
    private String extractAuthCodeFromRedirectURI(String redirectURI) {
        // Extract the 'code' parameter from the redirect URI
        // Format: http://localhost:3000/auth/callback/saml-mock?code=some-uuid&state=test-state
        int codeIndex = redirectURI.indexOf("code=");
        if (codeIndex == -1) {
            throw new IllegalStateException("Code parameter not found in redirect URI: " + redirectURI);
        }
        
        String codePart = redirectURI.substring(codeIndex + "code=".length());
        int ampIndex = codePart.indexOf('&');
        if (ampIndex != -1) {
            codePart = codePart.substring(0, ampIndex);
        }
        
        return java.net.URLDecoder.decode(codePart, java.nio.charset.StandardCharsets.UTF_8);
    }
}
