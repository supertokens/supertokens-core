package io.supertokens.test.saml.api;

import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.FeatureFlagTestContent;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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
import io.supertokens.utils.SemVer;

public class RemoveSAMLClientTest5_4 {

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
    public void testDeleteNonExistingClientReturnsFalse() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        JsonObject body = new JsonObject();
        body.addProperty("clientId", "st_saml_does_not_exist");

        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients/remove", body, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");

        assertEquals("OK", resp.get("status").getAsString());
        assertFalse(resp.get("didExist").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testBadInputMissingClientId() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        JsonObject body = new JsonObject();
        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/clients/remove", body, 1000, 1000, null,
                    SemVer.v5_4.get(), "saml");
            // should not reach here
            org.junit.Assert.fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Field name 'clientId' is invalid in JSON input", e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateThenDeleteClient() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // create a client first
        JsonObject create = new JsonObject();
        create.addProperty("spEntityId", "http://example.com/saml");
        create.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml-mock");
        create.add("redirectURIs", new JsonArray());
        create.get("redirectURIs").getAsJsonArray().add("http://localhost:3000/auth/callback/saml-mock");
        
        // Generate IdP metadata using MockSAML
        MockSAML.KeyMaterial keyMaterial = MockSAML.generateSelfSignedKeyMaterial();
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, idpSsoUrl, keyMaterial.certificate);
        String metadataXMLBase64 = java.util.Base64.getEncoder().encodeToString(metadataXML.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        create.addProperty("metadataXML", metadataXMLBase64);

        JsonObject createResp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", create, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");

        String clientId = createResp.get("clientId").getAsString();
        assertTrue(clientId.startsWith("st_saml_"));

        // delete it
        JsonObject body = new JsonObject();
        body.addProperty("clientId", clientId);

        JsonObject deleteResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients/remove", body, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");

        assertEquals("OK", deleteResp.get("status").getAsString());
        assertTrue(deleteResp.get("didExist").getAsBoolean());

        // verify listing is empty after deletion
        JsonObject listResp = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients/list", null, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        assertEquals("OK", listResp.get("status").getAsString());
        assertTrue(listResp.get("clients").isJsonArray());
        assertEquals(0, listResp.get("clients").getAsJsonArray().size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeleteTwiceSecondTimeFalse() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // create
        JsonObject create = new JsonObject();
        create.addProperty("spEntityId", "http://example.com/saml");
        create.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml-mock");
        create.add("redirectURIs", new JsonArray());
        create.get("redirectURIs").getAsJsonArray().add("http://localhost:3000/auth/callback/saml-mock");
        
        // Generate IdP metadata using MockSAML
        MockSAML.KeyMaterial keyMaterial = MockSAML.generateSelfSignedKeyMaterial();
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, idpSsoUrl, keyMaterial.certificate);
        String metadataXMLBase64 = java.util.Base64.getEncoder().encodeToString(metadataXML.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        create.addProperty("metadataXML", metadataXMLBase64);

        JsonObject createResp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", create, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");

        String clientId = createResp.get("clientId").getAsString();

        JsonObject body = new JsonObject();
        body.addProperty("clientId", clientId);

        JsonObject deleteResp1 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients/remove", body, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        assertEquals("OK", deleteResp1.get("status").getAsString());
        assertTrue(deleteResp1.get("didExist").getAsBoolean());

        JsonObject deleteResp2 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients/remove", body, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        assertEquals("OK", deleteResp2.get("status").getAsString());
        assertFalse(deleteResp2.get("didExist").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
