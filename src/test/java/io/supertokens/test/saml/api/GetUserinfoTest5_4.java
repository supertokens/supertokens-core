package io.supertokens.test.saml.api;

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
import io.supertokens.test.saml.MockSAML;
import io.supertokens.test.saml.SAMLTestUtils;
import io.supertokens.utils.SemVer;

public class GetUserinfoTest5_4 {
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
    public void testBadInput() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // Missing accessToken
        {
            JsonObject body = new JsonObject();
            body.addProperty("clientId", "some-client");

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/user", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Field name 'accessToken' is invalid in JSON input", e.getMessage());
            }
        }

        // Missing clientId
        {
            JsonObject body = new JsonObject();
            body.addProperty("accessToken", "some-access-token");

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/user", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Field name 'clientId' is invalid in JSON input", e.getMessage());
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testInvalidAccessToken() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // Test with invalid/fake access token
        {
            JsonObject body = new JsonObject();
            body.addProperty("accessToken", "invalid-access-token-12345");
            body.addProperty("clientId", "test-client-id");

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/user", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");

            assertEquals("INVALID_TOKEN_ERROR", response.get("status").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testValidTokenWithWrongClient() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // Create first client
        String spEntityId1 = "http://example.com/saml";
        String defaultRedirectURI1 = "http://localhost:3000/auth/callback/saml-mock";
        String acsURL1 = "http://localhost:3000/acs";
        String idpEntityId1 = "https://saml.example.com/entityid";
        String idpSsoUrl1 = "https://mocksaml.com/api/saml/sso";

        SAMLTestUtils.CreatedClientInfo clientInfo1 = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                defaultRedirectURI1,
                acsURL1,
                idpEntityId1,
                idpSsoUrl1
        );

        // Create second client
        String spEntityId2 = "http://example2.com/saml";
        String defaultRedirectURI2 = "http://localhost:3001/auth/callback/saml-mock";
        String acsURL2 = "http://localhost:3001/acs";
        String idpEntityId2 = "https://saml2.example.com/entityid";
        String idpSsoUrl2 = "https://mocksaml2.com/api/saml/sso";

        SAMLTestUtils.CreatedClientInfo clientInfo2 = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                defaultRedirectURI2,
                acsURL2,
                idpEntityId2,
                idpSsoUrl2
        );

        // Create a login request for client1 to generate a RelayState
        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process,
                clientInfo1.clientId,
                clientInfo1.defaultRedirectURI,
                clientInfo1.acsURL,
                "test-state"
        );

        // Generate a valid SAML Response for client1
        String samlResponseBase64 = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo1.idpEntityId,
                "https://saml.supertokens.com",
                clientInfo1.acsURL,
                "user@example.com",
                null,
                relayState,
                clientInfo1.keyMaterial,
                300
        );

        // Process the callback for client1 to get a valid access token
        JsonObject callbackBody = new JsonObject();
        callbackBody.addProperty("samlResponse", samlResponseBase64);
        callbackBody.addProperty("relayState", relayState);

        JsonObject callbackResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", callbackBody, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("OK", callbackResp.get("status").getAsString());
        
        // Extract the access token from the redirect URI
        String redirectURI = callbackResp.get("redirectURI").getAsString();
        String accessToken = extractAccessTokenFromRedirectURI(redirectURI);

        // Now try to use the valid access token from client1 with client2's clientId
        JsonObject userInfoBody = new JsonObject();
        userInfoBody.addProperty("accessToken", accessToken);
        userInfoBody.addProperty("clientId", clientInfo2.clientId); // Wrong client ID

        JsonObject userInfoResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/user", userInfoBody, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("INVALID_TOKEN_ERROR", userInfoResp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testValidTokenWithCorrectClient() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

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

        // Generate a valid SAML Response
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

        // Process the callback to get a valid access token
        JsonObject callbackBody = new JsonObject();
        callbackBody.addProperty("samlResponse", samlResponseBase64);
        callbackBody.addProperty("relayState", relayState);

        JsonObject callbackResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", callbackBody, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("OK", callbackResp.get("status").getAsString());
        
        // Extract the access token from the redirect URI
        String redirectURI = callbackResp.get("redirectURI").getAsString();
        String accessToken = extractAccessTokenFromRedirectURI(redirectURI);

        // Use the valid access token with the correct client ID
        JsonObject userInfoBody = new JsonObject();
        userInfoBody.addProperty("accessToken", accessToken);
        userInfoBody.addProperty("clientId", clientInfo.clientId);

        JsonObject userInfoResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/user", userInfoBody, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        // Verify successful response
        assertEquals("OK", userInfoResp.get("status").getAsString());
        assertNotNull(userInfoResp.get("sub"));
        assertEquals("user@example.com", userInfoResp.get("sub").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private String extractAccessTokenFromRedirectURI(String redirectURI) {
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
