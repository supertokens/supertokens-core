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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.saml.MockSAML;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

import static org.junit.Assert.*;

/**
 * Tests for SAML AuthnRequest signing functionality.
 *
 * When enableRequestSigning=true is set on a SAML client, the generated AuthnRequest
 * should contain a valid XML Signature with non-empty DigestValue and SignatureValue.
 *
 * Bug report: The generated AuthnRequest had empty DigestValue and SignatureValue elements
 * when enableRequestSigning=true was set via the Core API.
 */
public class SAMLRequestSigningTest5_4 {

    private static final String XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#";

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

    /**
     * Test that when enableRequestSigning=true, the generated AuthnRequest contains
     * a valid signature with non-empty DigestValue and SignatureValue.
     *
     * This test verifies the fix for the bug where DigestValue and SignatureValue
     * were empty in the generated AuthnRequest.
     */
    @Test
    public void testAuthnRequestSigningProducesNonEmptySignatureValues() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Create a SAML client with enableRequestSigning=true
        MockSAML.KeyMaterial keyMaterial = MockSAML.generateSelfSignedKeyMaterial();
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, idpSsoUrl, keyMaterial.certificate);
        String metadataXMLBase64 = Base64.getEncoder().encodeToString(metadataXML.getBytes(StandardCharsets.UTF_8));

        JsonObject createClientInput = new JsonObject();
        createClientInput.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml");
        JsonArray redirectURIs = new JsonArray();
        redirectURIs.add("http://localhost:3000/auth/callback/saml");
        createClientInput.add("redirectURIs", redirectURIs);
        createClientInput.addProperty("metadataXML", metadataXMLBase64);
        createClientInput.addProperty("enableRequestSigning", true);  // Enable request signing

        JsonObject createResp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        assertEquals("OK", createResp.get("status").getAsString());
        String clientId = createResp.get("clientId").getAsString();
        assertTrue("enableRequestSigning should be true", createResp.get("enableRequestSigning").getAsBoolean());

        // Create a login request to get the AuthnRequest
        JsonObject loginBody = new JsonObject();
        loginBody.addProperty("clientId", clientId);
        loginBody.addProperty("redirectURI", "http://localhost:3000/auth/callback/saml");
        loginBody.addProperty("acsURL", "http://localhost:3000/acs");
        loginBody.addProperty("state", "test-state-123");

        JsonObject loginResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/login", loginBody, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        assertEquals("OK", loginResp.get("status").getAsString());

        String ssoRedirectURI = loginResp.get("ssoRedirectURI").getAsString();
        assertTrue("Should contain SAMLRequest parameter", ssoRedirectURI.contains("SAMLRequest="));

        // Extract and decode the SAMLRequest
        String samlRequestEncoded = extractSAMLRequest(ssoRedirectURI);
        String samlRequestXML = decodeSAMLRequest(samlRequestEncoded);

        // Parse the XML and verify signature elements
        Document doc = parseXML(samlRequestXML);

        // Verify Signature element exists
        NodeList signatureNodes = doc.getElementsByTagNameNS(XMLDSIG_NS, "Signature");
        assertTrue("AuthnRequest should contain a Signature element when enableRequestSigning=true",
                signatureNodes.getLength() > 0);

        Element signatureElement = (Element) signatureNodes.item(0);

        // Verify DigestValue is NOT empty
        NodeList digestValueNodes = signatureElement.getElementsByTagNameNS(XMLDSIG_NS, "DigestValue");
        assertTrue("Signature should contain DigestValue element", digestValueNodes.getLength() > 0);
        String digestValue = digestValueNodes.item(0).getTextContent();
        assertNotNull("DigestValue should not be null", digestValue);
        assertFalse("DigestValue should not be empty - this indicates signing was not performed",
                digestValue.trim().isEmpty());

        // Verify SignatureValue is NOT empty
        NodeList signatureValueNodes = signatureElement.getElementsByTagNameNS(XMLDSIG_NS, "SignatureValue");
        assertTrue("Signature should contain SignatureValue element", signatureValueNodes.getLength() > 0);
        String signatureValue = signatureValueNodes.item(0).getTextContent();
        assertNotNull("SignatureValue should not be null", signatureValue);
        assertFalse("SignatureValue should not be empty - this indicates signing was not performed",
                signatureValue.trim().isEmpty());

        // Verify KeyInfo contains the certificate
        NodeList keyInfoNodes = signatureElement.getElementsByTagNameNS(XMLDSIG_NS, "KeyInfo");
        assertTrue("Signature should contain KeyInfo element", keyInfoNodes.getLength() > 0);

        NodeList x509CertNodes = signatureElement.getElementsByTagNameNS(XMLDSIG_NS, "X509Certificate");
        assertTrue("KeyInfo should contain X509Certificate element", x509CertNodes.getLength() > 0);
        String certValue = x509CertNodes.item(0).getTextContent();
        assertFalse("X509Certificate should not be empty", certValue.trim().isEmpty());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that when enableRequestSigning=false (or not set), the generated AuthnRequest
     * does NOT contain a Signature element.
     */
    @Test
    public void testAuthnRequestWithoutSigningHasNoSignature() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Create a SAML client with enableRequestSigning=false
        MockSAML.KeyMaterial keyMaterial = MockSAML.generateSelfSignedKeyMaterial();
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, idpSsoUrl, keyMaterial.certificate);
        String metadataXMLBase64 = Base64.getEncoder().encodeToString(metadataXML.getBytes(StandardCharsets.UTF_8));

        JsonObject createClientInput = new JsonObject();
        createClientInput.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml");
        JsonArray redirectURIs = new JsonArray();
        redirectURIs.add("http://localhost:3000/auth/callback/saml");
        createClientInput.add("redirectURIs", redirectURIs);
        createClientInput.addProperty("metadataXML", metadataXMLBase64);
        createClientInput.addProperty("enableRequestSigning", false);  // Disable request signing

        JsonObject createResp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        assertEquals("OK", createResp.get("status").getAsString());
        String clientId = createResp.get("clientId").getAsString();
        assertFalse("enableRequestSigning should be false", createResp.get("enableRequestSigning").getAsBoolean());

        // Create a login request
        JsonObject loginBody = new JsonObject();
        loginBody.addProperty("clientId", clientId);
        loginBody.addProperty("redirectURI", "http://localhost:3000/auth/callback/saml");
        loginBody.addProperty("acsURL", "http://localhost:3000/acs");

        JsonObject loginResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/login", loginBody, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        assertEquals("OK", loginResp.get("status").getAsString());

        String ssoRedirectURI = loginResp.get("ssoRedirectURI").getAsString();
        String samlRequestEncoded = extractSAMLRequest(ssoRedirectURI);
        String samlRequestXML = decodeSAMLRequest(samlRequestEncoded);

        // Parse the XML and verify NO Signature element
        Document doc = parseXML(samlRequestXML);
        NodeList signatureNodes = doc.getElementsByTagNameNS(XMLDSIG_NS, "Signature");
        assertEquals("AuthnRequest should NOT contain a Signature element when enableRequestSigning=false",
                0, signatureNodes.getLength());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that the default value for enableRequestSigning is true, and signing works by default.
     */
    @Test
    public void testDefaultEnableRequestSigningIsTrue() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Create a SAML client WITHOUT specifying enableRequestSigning (should default to true)
        MockSAML.KeyMaterial keyMaterial = MockSAML.generateSelfSignedKeyMaterial();
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId, idpSsoUrl, keyMaterial.certificate);
        String metadataXMLBase64 = Base64.getEncoder().encodeToString(metadataXML.getBytes(StandardCharsets.UTF_8));

        JsonObject createClientInput = new JsonObject();
        createClientInput.addProperty("defaultRedirectURI", "http://localhost:3000/auth/callback/saml");
        JsonArray redirectURIs = new JsonArray();
        redirectURIs.add("http://localhost:3000/auth/callback/saml");
        createClientInput.add("redirectURIs", redirectURIs);
        createClientInput.addProperty("metadataXML", metadataXMLBase64);
        // Note: NOT setting enableRequestSigning - should default to true

        JsonObject createResp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", createClientInput, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        assertEquals("OK", createResp.get("status").getAsString());
        String clientId = createResp.get("clientId").getAsString();
        assertTrue("enableRequestSigning should default to true", createResp.get("enableRequestSigning").getAsBoolean());

        // Create a login request
        JsonObject loginBody = new JsonObject();
        loginBody.addProperty("clientId", clientId);
        loginBody.addProperty("redirectURI", "http://localhost:3000/auth/callback/saml");
        loginBody.addProperty("acsURL", "http://localhost:3000/acs");

        JsonObject loginResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/login", loginBody, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        assertEquals("OK", loginResp.get("status").getAsString());

        String ssoRedirectURI = loginResp.get("ssoRedirectURI").getAsString();
        String samlRequestEncoded = extractSAMLRequest(ssoRedirectURI);
        String samlRequestXML = decodeSAMLRequest(samlRequestEncoded);

        // Parse the XML and verify signature is present and non-empty
        Document doc = parseXML(samlRequestXML);
        NodeList signatureNodes = doc.getElementsByTagNameNS(XMLDSIG_NS, "Signature");
        assertTrue("AuthnRequest should contain a Signature element by default",
                signatureNodes.getLength() > 0);

        Element signatureElement = (Element) signatureNodes.item(0);

        NodeList digestValueNodes = signatureElement.getElementsByTagNameNS(XMLDSIG_NS, "DigestValue");
        String digestValue = digestValueNodes.item(0).getTextContent();
        assertFalse("DigestValue should not be empty by default", digestValue.trim().isEmpty());

        NodeList signatureValueNodes = signatureElement.getElementsByTagNameNS(XMLDSIG_NS, "SignatureValue");
        String signatureValue = signatureValueNodes.item(0).getTextContent();
        assertFalse("SignatureValue should not be empty by default", signatureValue.trim().isEmpty());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // ============ Helper Methods ============

    /**
     * Extract the SAMLRequest parameter value from the SSO redirect URI.
     */
    private String extractSAMLRequest(String ssoRedirectURI) {
        int startIdx = ssoRedirectURI.indexOf("SAMLRequest=");
        if (startIdx == -1) {
            throw new IllegalArgumentException("SAMLRequest not found in URI: " + ssoRedirectURI);
        }
        startIdx += "SAMLRequest=".length();

        int endIdx = ssoRedirectURI.indexOf('&', startIdx);
        if (endIdx == -1) {
            endIdx = ssoRedirectURI.length();
        }

        return ssoRedirectURI.substring(startIdx, endIdx);
    }

    /**
     * Decode a SAMLRequest from the redirect binding format.
     * The SAMLRequest is URL-encoded, Base64-encoded, and DEFLATE-compressed.
     */
    private String decodeSAMLRequest(String encoded) throws Exception {
        // URL decode
        String urlDecoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());

        // Base64 decode
        byte[] base64Decoded = Base64.getDecoder().decode(urlDecoded);

        // DEFLATE decompress (raw deflate, not gzip)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Inflater inflater = new Inflater(true);  // true = raw deflate (no zlib header)
        InflaterOutputStream ios = new InflaterOutputStream(baos, inflater);
        ios.write(base64Decoded);
        ios.close();

        return baos.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * Parse XML string into a Document.
     */
    private Document parseXML(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
