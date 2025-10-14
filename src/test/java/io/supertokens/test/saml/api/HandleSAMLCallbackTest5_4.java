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

public class HandleSAMLCallbackTest5_4 {

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

        // Missing SAMLResponse
        {
            JsonObject body = new JsonObject();
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/callback", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Field name 'samlResponse' is invalid in JSON input", e.getMessage());
            }
        }

        // Empty SAMLResponse (base64 of empty string is empty)
        {
            JsonObject body = new JsonObject();
            body.addProperty("samlResponse", "");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/callback", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Invalid or malformed SAML response input", e.getMessage());
            }
        }

        // Non-XML SAMLResponse (base64 of 'hello')
        {
            String nonXmlBase64 = java.util.Base64.getEncoder().encodeToString("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            JsonObject body = new JsonObject();
            body.addProperty("samlResponse", nonXmlBase64);
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/callback", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Invalid or malformed SAML response input", e.getMessage());
            }
        }

        // Arbitrary XML as SAMLResponse (not a SAML Response element)
        {
            String xml = "<root></root>";
            String xmlBase64 = java.util.Base64.getEncoder().encodeToString(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            JsonObject body = new JsonObject();
            body.addProperty("samlResponse", xmlBase64);
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/callback", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Invalid or malformed SAML response input", e.getMessage());
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testNonExistingRelayState() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String spEntityId = "http://example.com/saml";
        String defaultRedirectURI = "http://localhost:3000/auth/callback/saml-mock";
        String acsURL = "http://localhost:3000/acs";
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                spEntityId,
                defaultRedirectURI,
                acsURL,
                idpEntityId,
                idpSsoUrl
        );

        String samlResponseBase64 = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId,
                clientInfo.spEntityId,
                clientInfo.acsURL,
                "user@example.com",
                null,
                null,
                clientInfo.keyMaterial,
                300
        );

        JsonObject body = new JsonObject();
        body.addProperty("samlResponse", samlResponseBase64);
        body.addProperty("relayState", "this-does-not-exist");

        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("INVALID_RELAY_STATE_ERROR", resp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
