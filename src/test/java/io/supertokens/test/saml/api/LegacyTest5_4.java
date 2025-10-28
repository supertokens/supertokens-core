package io.supertokens.test.saml.api;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonObject;

import io.supertokens.ProcessState;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.saml.MockSAML;
import io.supertokens.test.saml.SAMLTestUtils;
import io.supertokens.utils.SemVer;

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
                relayState,
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
                relayState,
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
        assertEquals(authCode + "." + clientInfo.clientId, accessToken);

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
                relayState,
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
