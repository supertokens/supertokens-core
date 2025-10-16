/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.saml.api;

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
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.saml.MockSAML;
import io.supertokens.utils.SemVer;

public class CreateOrUpdateSAMLClientTest5_4 {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Test
    public void testCreationWithClientSecret() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject createClientInput = new JsonObject();
        createClientInput.addProperty("spEntityId", "http://example.com/saml");
        createClientInput.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml-mock");
        createClientInput.add("redirectURIs", new JsonArray());
        createClientInput.get("redirectURIs").getAsJsonArray().add("http://localhost:3000/auth/callback/saml-mock");

		// Generate IdP metadata using MockSAML
		MockSAML.KeyMaterial keyMaterial = MockSAML.generateSelfSignedKeyMaterial();
		String idpEntityId = "https://saml.example.com/entityid";
		String idpSsoUrl = "https://mocksaml.com/api/saml/sso";
		String generatedMetadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, idpSsoUrl, keyMaterial.certificate);
		String metadataXMLBase64 = java.util.Base64.getEncoder().encodeToString(generatedMetadataXML.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		createClientInput.addProperty("metadataXML", metadataXMLBase64);

        String clientSecret = "my-secret-abc-123";
        createClientInput.addProperty("clientSecret", clientSecret);

        JsonObject resp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");

        // Ensure structure contains clientSecret and matches provided value
        assertEquals("OK", resp.get("status").getAsString());
        assertTrue(resp.has("clientSecret"));
        assertEquals(clientSecret, resp.get("clientSecret").getAsString());
        assertTrue(resp.get("clientId").getAsString().startsWith("st_saml_"));
        assertEquals("http://localhost:3000/auth/callback/saml-mock", resp.get("defaultRedirectURI").getAsString());
        assertEquals("http://example.com/saml", resp.get("spEntityId").getAsString());
        assertTrue(resp.get("redirectURIs").isJsonArray());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreationWithPredefinedClientId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject createClientInput = new JsonObject();
        String customClientId = "st_saml_custom_12345";
        createClientInput.addProperty("clientId", customClientId);
        createClientInput.addProperty("spEntityId", "http://example.com/saml");
        createClientInput.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml-mock");
        createClientInput.add("redirectURIs", new JsonArray());
        createClientInput.get("redirectURIs").getAsJsonArray().add("http://localhost:3000/auth/callback/saml-mock");

        // Generate IdP metadata using MockSAML
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, idpSsoUrl, km.certificate);
        String metadataXMLBase64 = java.util.Base64.getEncoder().encodeToString(metadataXML.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        createClientInput.addProperty("metadataXML", metadataXMLBase64);

        JsonObject resp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");

        // Ensure custom clientId is respected and standard fields present
        verifyClientStructureWithoutClientSecret(resp, false);
        assertEquals("OK", resp.get("status").getAsString());
        assertEquals(customClientId, resp.get("clientId").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
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

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject createClientInput = new JsonObject();
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                    SemVer.v5_4.get(), "saml");
            fail();

        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Field name 'spEntityId' is invalid in JSON input", e.getMessage());
        }
        createClientInput.addProperty("spEntityId", "http://example.com/saml");
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                    SemVer.v5_4.get(), "saml");
            fail();

        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Field name 'defaultRedirectURI' is invalid in JSON input", e.getMessage());
        }

        createClientInput.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml-azure");
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                    SemVer.v5_4.get(), "saml");
            fail();

        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Field name 'redirectURIs' is invalid in JSON input", e.getMessage());
        }

        createClientInput.add("redirectURIs", new JsonArray());
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                    SemVer.v5_4.get(), "saml");
            fail();

        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: redirectURIs is required in the input", e.getMessage());
       }

        createClientInput.get("redirectURIs").getAsJsonArray().add("http://localhost:3000/auth/callback/saml-azure");
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                    SemVer.v5_4.get(), "saml");
            fail();

        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: Field name 'metadataXML' is invalid in JSON input", e.getMessage());
        }

        createClientInput.addProperty("metadataXML", "");
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                    SemVer.v5_4.get(), "saml");
            fail();

        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: metadataXML does not have a valid SAML metadata", e.getMessage());
        }

        String helloXml = "<hello>world</hello>";
        String helloXmlBase64 = java.util.Base64.getEncoder().encodeToString(helloXml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        createClientInput.addProperty("metadataXML", helloXmlBase64);
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                    SemVer.v5_4.get(), "saml");
            fail();

        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: metadataXML does not have a valid SAML metadata", e.getMessage());
        }

        // has an invalid certificate
        String metadataXML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<md:EntityDescriptor xmlns:md=\"urn:oasis:names:tc:SAML:2.0:metadata\" entityID=\"https://saml.example.com/entityid\" validUntil=\"2035-10-13T09:51:02.835Z\">\n" +
                "  <md:IDPSSODescriptor WantAuthnRequestsSigned=\"true\" protocolSupportEnumeration=\"urn:oasis:names:tc:SAML:2.0:protocol\">\n" +
                "    <md:KeyDescriptor use=\"signing\">\n" +
                "      <ds:KeyInfo xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">\n" +
                "        <ds:X509Data>\n" +
                "          <ds:X509Certificate>MIIC4jCCAcoCCQC33wnybT5QZDANBgkqhkiG9w0BAQsFADAyMQswCQYDVQQGEwJV\n" +
                "SzEPMA0GA1UECgwGQm94eUhRMRIwEAYDVQQDDAlNb2NrIFNBTUwwIBcNMjIwMjI4\n" +
                "MjE0NjM4WhgPMzAyMTA3MDEyMTQ2MzhaMDIxCzAJBgNVBAYTAlVLMQ8wDQYDVQQK\n" +
                "DAZCb3h5SFExEjAQBgNVBAMMCU1vY2sgU0FNTDCCASIwDQYJKoZIhvcNAQEBBQAD\n" +
                "ggEPADCCAQoCggEBALGfYettMsct1T6tVUwTudNJH5Pnb9GGnkXi9Zw/e6x45DD0\n" +
                "RuRONbFlJ2T4RjAE/uG+AjXxXQ8o2SZfb9+GgmCHuTJFNgHoZ1nFVXCmb/Hg8Hpd\n" +
                "4vOAGXndixaReOiq3EH5XvpMjMkJ3+8+9VYMzMZOjkgQtAqO36eAFFfNKX7dTj3V\n" +
                "2/W5sGHRv+9AarggJkF+ptUkXoLtVA51wcfYm6hILptpde5FQC8RWY1YrswBWAEZ\n" +
                "NfyrR4JeSweElNHg4NVOs4TwGjOPwWGqzTfgTlECAwEAATANBgkqhkiG9w0BAQsF\n" +
                "AAOCAQEAAYRlYflSXAWoZpFfwNiCQVE5d9zZ0DPzNdWhAybXcTyMf0z5mDf6FWBW\n" +
                "5Gyoi9u3EMEDnzLcJNkwJAAc39Apa4I2/tml+Jy29dk8bTyX6m93ngmCgdLh5Za4\n" +
                "khuU3AM3L63g7VexCuO7kwkjh/+LqdcIXsVGO6XDfu2QOs1Xpe9zIzLpwm/RNYeX\n" +
                "UjbSj5ce/jekpAw7qyVVL4xOyh8AtUW1ek3wIw1MJvEgEPt0d16oshWJpoS1OT8L\n" +
                "r/22SvYEo3EmSGdTVGgk3x3s+A0qWAqTcyjr7Q4s/GKYRFfomGwz0TZ4Iw1ZN99M\n" +
                "m0eo2USlSRTVl7QHRTuiuSThHpLKQQ==</ds:X509Certificate>\n" +
                "        </ds:X509Data>\n" +
                "      </ds:KeyInfo>\n" +
                "    </md:KeyDescriptor>\n" +
                "    <md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</md:NameIDFormat>\n" +
                "    <md:SingleSignOnService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect\" Location=\"https://mocksaml.com/api/saml/sso\"/>\n" +
                "    <md:SingleSignOnService Binding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" Location=\"https://mocksaml.com/api/saml/sso\"/>\n" +
                "  </md:IDPSSODescriptor>\n" +
                "</md:EntityDescriptor>";

        metadataXML = java.util.Base64.getEncoder().encodeToString(metadataXML.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        createClientInput.addProperty("metadataXML", metadataXML);
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                    SemVer.v5_4.get(), "saml");
            fail();

        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: metadataXML does not have a valid SAML metadata", e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreationUsingXML() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject createClientInput = new JsonObject();
        createClientInput.addProperty("spEntityId", "http://example.com/saml");
        createClientInput.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml-mock");
        createClientInput.add("redirectURIs", new JsonArray());
        createClientInput.get("redirectURIs").getAsJsonArray().add("http://localhost:3000/auth/callback/saml-mock");

        // Generate IdP metadata using MockSAML
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, idpSsoUrl, km.certificate);
        String metadataXMLBase64 = java.util.Base64.getEncoder().encodeToString(metadataXML.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        createClientInput.addProperty("metadataXML", metadataXMLBase64);

        JsonObject resp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        verifyClientStructureWithoutClientSecret(resp, true);

        assertEquals("OK", resp.get("status").getAsString());
        // Check the actual returned values for each field
        assertTrue(resp.get("clientId").getAsString().startsWith("st_saml_"));

        assertEquals("http://localhost:3000/auth/callback/saml-mock", resp.get("defaultRedirectURI").getAsString());

        assertTrue(resp.get("redirectURIs").isJsonArray());
        assertEquals(1, resp.get("redirectURIs").getAsJsonArray().size());
        assertEquals("http://localhost:3000/auth/callback/saml-mock", resp.get("redirectURIs").getAsJsonArray().get(0).getAsString());

        assertEquals("http://example.com/saml", resp.get("spEntityId").getAsString());

        assertEquals(idpEntityId, resp.get("idpEntityId").getAsString());

        String expectedCertBase64 = java.util.Base64.getEncoder().encodeToString(km.certificate.getEncoded());
        assertEquals(expectedCertBase64, resp.get("idpSigningCertificate").getAsString());

        assertFalse(resp.get("allowIDPInitiatedLogin").getAsBoolean());

        assertEquals("OK", resp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdateClient() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // Create a client first
        JsonObject createClientInput = new JsonObject();
        createClientInput.addProperty("spEntityId", "http://example.com/saml");
        createClientInput.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml-mock");
        createClientInput.add("redirectURIs", new JsonArray());
        createClientInput.get("redirectURIs").getAsJsonArray().add("http://localhost:3000/auth/callback/saml-mock");

        // Generate IdP metadata using MockSAML
        MockSAML.KeyMaterial km2 = MockSAML.generateSelfSignedKeyMaterial();
        String idpEntityId2 = "https://saml.example.com/entityid";
        String idpSsoUrl2 = "https://mocksaml.com/api/saml/sso";
        String metadataXML2 = MockSAML.generateIdpMetadataXML(idpEntityId2, idpSsoUrl2, km2.certificate);
        String metadataXMLBase64_2 = java.util.Base64.getEncoder().encodeToString(metadataXML2.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        createClientInput.addProperty("metadataXML", metadataXMLBase64_2);

        JsonObject createResp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        verifyClientStructureWithoutClientSecret(createResp, true);

        String clientId = createResp.get("clientId").getAsString();

        // Update fields
        JsonObject updateInput = new JsonObject();
        updateInput.addProperty("clientId", clientId);
        updateInput.addProperty("spEntityId", "http://example.com/saml-updated");
        updateInput.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml-mock-2");
        JsonArray updatedRedirectURIs = new JsonArray();
        updatedRedirectURIs.add("http://localhost:3000/auth/callback/saml-mock-2");
        updatedRedirectURIs.add("http://localhost:3000/auth/callback/saml-mock-3");
        updateInput.add("redirectURIs", updatedRedirectURIs);
        updateInput.addProperty("allowIDPInitiatedLogin", true);
        // metadata is required by the API even on update
        updateInput.addProperty("metadataXML", metadataXMLBase64_2);

        JsonObject updateResp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", updateInput, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        verifyClientStructureWithoutClientSecret(updateResp, false);

        assertEquals("OK", updateResp.get("status").getAsString());
        assertEquals(clientId, updateResp.get("clientId").getAsString());
        assertEquals("http://localhost:3000/auth/callback/saml-mock-2", updateResp.get("defaultRedirectURI").getAsString());
        assertTrue(updateResp.get("redirectURIs").isJsonArray());
        assertEquals(2, updateResp.get("redirectURIs").getAsJsonArray().size());
        assertEquals("http://localhost:3000/auth/callback/saml-mock-2", updateResp.get("redirectURIs").getAsJsonArray().get(0).getAsString());
        assertEquals("http://localhost:3000/auth/callback/saml-mock-3", updateResp.get("redirectURIs").getAsJsonArray().get(1).getAsString());
        assertEquals("http://example.com/saml-updated", updateResp.get("spEntityId").getAsString());
        assertTrue(updateResp.get("allowIDPInitiatedLogin").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static void verifyClientStructureWithoutClientSecret(JsonObject client, boolean generatedClientId) throws Exception {
        assertEquals(9, client.size());

        String[] FIELDS = new String[]{
                "clientId",
                "defaultRedirectURI",
                "redirectURIs",
                "spEntityId",
                "idpEntityId",
                "idpSigningCertificate",
                "allowIDPInitiatedLogin",
                "enableRequestSigning",
                "status"
        };

        for (String field : FIELDS) {
            assertTrue(client.has(field));
        }

        if (generatedClientId) {
            assertTrue(client.get("clientId").getAsString().startsWith("st_saml_"));
        }

        assertTrue(client.get("defaultRedirectURI").isJsonPrimitive());

        assertTrue(client.get("redirectURIs").isJsonArray());
        assertTrue(client.get("redirectURIs").getAsJsonArray().size() > 0);

        assertTrue(client.get("spEntityId").isJsonPrimitive());
        assertTrue(client.get("idpEntityId").isJsonPrimitive());
        assertTrue(client.get("idpSigningCertificate").isJsonPrimitive());
        assertTrue(client.get("enableRequestSigning").isJsonPrimitive());

        assertEquals("OK", client.get("status").getAsString());
    }
}
