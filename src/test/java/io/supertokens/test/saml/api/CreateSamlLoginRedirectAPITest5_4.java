package io.supertokens.test.saml.api;

import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
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
import io.supertokens.test.saml.MockSAML;
import io.supertokens.utils.SemVer;

public class CreateSamlLoginRedirectAPITest5_4 {

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

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // missing clientId
        {
            JsonObject body = new JsonObject();
            body.addProperty("redirectURI", "http://localhost:3000/auth/callback/saml-mock");
            body.addProperty("acsURL", "http://localhost:3000/acs");

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/login", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Field name 'clientId' is invalid in JSON input", e.getMessage());
            }
        }

        // missing redirectURI
        {
            JsonObject body = new JsonObject();
            body.addProperty("clientId", "some-client");
            body.addProperty("acsURL", "http://localhost:3000/acs");

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/login", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Field name 'redirectURI' is invalid in JSON input", e.getMessage());
            }
        }

        // missing acsURL
        {
            JsonObject body = new JsonObject();
            body.addProperty("clientId", "some-client");
            body.addProperty("redirectURI", "http://localhost:3000/auth/callback/saml-mock");

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/saml/login", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
                fail();
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals("Http error. Status Code: 400. Message: Field name 'acsURL' is invalid in JSON input", e.getMessage());
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testInvalidClientId() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        JsonObject body = new JsonObject();
        body.addProperty("clientId", "non-existent-client");
        body.addProperty("redirectURI", "http://localhost:3000/auth/callback/saml-mock");
        body.addProperty("acsURL", "http://localhost:3000/acs");

        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/login", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
        assertEquals("INVALID_CLIENT_ERROR", resp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testInvalidRedirectURI() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        JsonObject createClientInput = new JsonObject();
        createClientInput.addProperty("spEntityId", "http://example.com/saml");
        createClientInput.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml-mock");
        createClientInput.add("redirectURIs", new JsonArray());
        createClientInput.get("redirectURIs").getAsJsonArray().add("http://localhost:3000/auth/callback/saml-mock");
        
        // Generate IdP metadata using MockSAML
        MockSAML.KeyMaterial keyMaterial = MockSAML.generateSelfSignedKeyMaterial();
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, idpSsoUrl, keyMaterial.certificate);
        String metadataXMLBase64 = java.util.Base64.getEncoder().encodeToString(metadataXML.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        createClientInput.addProperty("metadataXML", metadataXMLBase64);

        JsonObject createResp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null, SemVer.v5_4.get(), "saml");
        assertEquals("OK", createResp.get("status").getAsString());
        String clientId = createResp.get("clientId").getAsString();

        JsonObject body = new JsonObject();
        body.addProperty("clientId", clientId);
        body.addProperty("redirectURI", "http://localhost:3000/another/callback");
        body.addProperty("acsURL", "http://localhost:3000/acs");

        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/login", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
        assertEquals("INVALID_CLIENT_ERROR", resp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testValidLoginRedirect() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Prepare IdP metadata using MockSAML self-signed certificate
        MockSAML.KeyMaterial keyMaterial = MockSAML.generateSelfSignedKeyMaterial();
        java.security.cert.X509Certificate cert = keyMaterial.certificate;
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, idpSsoUrl, cert);
        String metadataXMLBase64 = java.util.Base64.getEncoder().encodeToString(metadataXML.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Create client using metadataXML
        JsonObject createClientInput = new JsonObject();
        createClientInput.addProperty("spEntityId", "http://example.com/saml");
        createClientInput.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml-mock");
        createClientInput.add("redirectURIs", new JsonArray());
        createClientInput.get("redirectURIs").getAsJsonArray().add("http://localhost:3000/auth/callback/saml-mock");
        createClientInput.addProperty("metadataXML", metadataXMLBase64);

        JsonObject createResp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null, SemVer.v5_4.get(), "saml");
        assertEquals("OK", createResp.get("status").getAsString());
        String clientId = createResp.get("clientId").getAsString();

        // Create login request with valid redirect URI
        JsonObject body = new JsonObject();
        body.addProperty("clientId", clientId);
        body.addProperty("redirectURI", "http://localhost:3000/auth/callback/saml-mock");
        body.addProperty("acsURL", "http://localhost:3000/acs");
        body.addProperty("state", "abc123");

        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/login", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        // Verify response structure
        assertEquals("OK", resp.get("status").getAsString());
        assertTrue(resp.has("ssoRedirectURI"));
        String ssoRedirectURI = resp.get("ssoRedirectURI").getAsString();
        assertTrue(ssoRedirectURI.startsWith(idpSsoUrl + "?"));
        assertTrue(ssoRedirectURI.contains("SAMLRequest="));
        assertTrue(ssoRedirectURI.contains("RelayState="));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
