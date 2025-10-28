package io.supertokens.test.saml.api;

import io.supertokens.featureflag.EE_FEATURES;
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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.supertokens.ProcessState;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.saml.MockSAML;
import io.supertokens.utils.SemVer;

public class ListSAMLClientsTest5_4 {

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
    public void testEmptyList() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        JsonObject listResp = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients/list", null, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");

        assertEquals("OK", listResp.get("status").getAsString());
        assertTrue(listResp.has("clients"));
        assertTrue(listResp.get("clients").isJsonArray());
        assertEquals(0, listResp.get("clients").getAsJsonArray().size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListAfterCreatingClientViaXML() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Generate IdP metadata using MockSAML
        MockSAML.KeyMaterial keyMaterial = MockSAML.generateSelfSignedKeyMaterial();
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, idpSsoUrl, keyMaterial.certificate);
        String metadataXMLBase64 = java.util.Base64.getEncoder().encodeToString(metadataXML.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        JsonObject createClientInput = new JsonObject();
        createClientInput.addProperty("spEntityId", "http://example.com/saml");
        createClientInput.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml-mock");
        createClientInput.add("redirectURIs", new JsonArray());
        createClientInput.get("redirectURIs").getAsJsonArray().add("http://localhost:3000/auth/callback/saml-mock");
        createClientInput.addProperty("metadataXML", metadataXMLBase64);

        JsonObject createResp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");

        assertEquals("OK", createResp.get("status").getAsString());
        String clientId = createResp.get("clientId").getAsString();

        JsonObject listResp = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients/list", null, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");

        assertEquals("OK", listResp.get("status").getAsString());
        assertTrue(listResp.get("clients").isJsonArray());
        JsonArray clients = listResp.get("clients").getAsJsonArray();
        assertEquals(1, clients.size());

        JsonObject listed = findByClientId(clients, clientId);
        assertNotNull(listed);

        // should not include clientSecret since we didn't set it
        assertFalse(listed.has("clientSecret"));

        assertEquals("http://localhost:3000/auth/callback/saml-mock", listed.get("defaultRedirectURI").getAsString());
        assertTrue(listed.get("redirectURIs").isJsonArray());
        assertEquals(1, listed.get("redirectURIs").getAsJsonArray().size());
        assertEquals("http://localhost:3000/auth/callback/saml-mock",
                listed.get("redirectURIs").getAsJsonArray().get(0).getAsString());

        assertEquals(idpEntityId, listed.get("idpEntityId").getAsString());
        assertTrue(listed.has("idpSigningCertificate"));
        assertFalse(listed.get("idpSigningCertificate").getAsString().isEmpty());
        assertFalse(listed.get("allowIDPInitiatedLogin").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListIncludesClientSecretWhenProvided() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Generate IdP metadata using MockSAML
        MockSAML.KeyMaterial keyMaterial = MockSAML.generateSelfSignedKeyMaterial();
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, idpSsoUrl, keyMaterial.certificate);
        String metadataXMLBase64 = java.util.Base64.getEncoder().encodeToString(metadataXML.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        JsonObject createClientInput = new JsonObject();
        createClientInput.addProperty("spEntityId", "http://example.com/saml");
        createClientInput.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml-mock");
        createClientInput.add("redirectURIs", new JsonArray());
        createClientInput.get("redirectURIs").getAsJsonArray().add("http://localhost:3000/auth/callback/saml-mock");
        createClientInput.addProperty("metadataXML", metadataXMLBase64);

        String clientSecret = "my-secret-xyz";
        createClientInput.addProperty("clientSecret", clientSecret);

        JsonObject createResp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");

        assertEquals("OK", createResp.get("status").getAsString());
        String clientId = createResp.get("clientId").getAsString();

        JsonObject listResp = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients/list", null, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");

        assertEquals("OK", listResp.get("status").getAsString());
        JsonArray clients = listResp.get("clients").getAsJsonArray();
        JsonObject listed = findByClientId(clients, clientId);
        assertNotNull(listed);
        assertTrue(listed.has("clientSecret"));
        assertEquals(clientSecret, listed.get("clientSecret").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static JsonObject findByClientId(JsonArray clients, String clientId) {
        for (JsonElement el : clients) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("clientId") && obj.get("clientId").getAsString().equals(clientId)) {
                return obj;
            }
        }
        return null;
    }
}
