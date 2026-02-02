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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.saml.MockSAML;
import io.supertokens.test.saml.SAMLTestUtils;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/**
 * Tests for SAML configuration options:
 * - saml_sp_entity_id: Custom SP Entity ID
 * - saml_claims_validity: How long claims/tokens are valid
 * - saml_relay_state_validity: How long relay state is valid
 */
public class SAMLConfigTest5_4 {

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
     * Test that relay state expires after the configured validity period.
     * Default is 300000ms (5 minutes), we test with a short 1 second validity.
     */
    @Test
    public void testRelayStateExpiry() throws Exception {
        // Start with a very short relay state validity (1 second)
        Utils.setValueInConfig("saml_relay_state_validity", "1000");

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Create SAML client
        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                "http://localhost:3000/auth/callback/saml",
                "http://localhost:3000/acs",
                "https://saml.example.com/entityid-relay-test",
                "https://mocksaml.com/api/saml/sso"
        );

        // Create a login request to generate a RelayState
        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process,
                clientInfo.clientId,
                clientInfo.defaultRedirectURI,
                clientInfo.acsURL,
                "test-state"
        );

        // Wait for relay state to expire (1.5 seconds to be safe)
        Thread.sleep(1500);

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

        // Try to process the callback - should fail because relay state expired
        JsonObject callbackBody = new JsonObject();
        callbackBody.addProperty("samlResponse", samlResponseBase64);
        callbackBody.addProperty("relayState", relayState);

        JsonObject callbackResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", callbackBody, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("INVALID_RELAY_STATE_ERROR", callbackResp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that claims/access tokens expire after the configured validity period.
     * Default is 300000ms (5 minutes), we test with a short 1 second validity.
     */
    @Test
    public void testClaimsExpiry() throws Exception {
        // Start with a very short claims validity (1 second)
        Utils.setValueInConfig("saml_claims_validity", "1000");

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Create SAML client
        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                "http://localhost:3000/auth/callback/saml",
                "http://localhost:3000/acs",
                "https://saml.example.com/entityid-claims-test",
                "https://mocksaml.com/api/saml/sso"
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

        // Process the callback to get an access token
        JsonObject callbackBody = new JsonObject();
        callbackBody.addProperty("samlResponse", samlResponseBase64);
        callbackBody.addProperty("relayState", relayState);

        JsonObject callbackResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", callbackBody, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("OK", callbackResp.get("status").getAsString());

        // Extract the access token from the redirect URI
        String redirectURI = callbackResp.get("redirectURI").getAsString();
        String accessToken = extractCodeFromRedirectURI(redirectURI);

        // Wait for claims to expire (1.5 seconds to be safe)
        Thread.sleep(1500);

        // Try to get user info - should fail because claims expired
        JsonObject userInfoBody = new JsonObject();
        userInfoBody.addProperty("accessToken", accessToken);
        userInfoBody.addProperty("clientId", clientInfo.clientId);

        JsonObject userInfoResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/user", userInfoBody, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("INVALID_TOKEN_ERROR", userInfoResp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that custom saml_sp_entity_id is used in AuthnRequest and metadata.
     */
    @Test
    public void testCustomSpEntityId() throws Exception {
        String customSpEntityId = "https://custom-sp.example.com/saml";
        Utils.setValueInConfig("saml_sp_entity_id", "\"" + customSpEntityId + "\"");

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Create SAML client
        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                "http://localhost:3000/auth/callback/saml",
                "http://localhost:3000/acs",
                "https://saml.example.com/entityid-sp-test",
                "https://mocksaml.com/api/saml/sso"
        );

        // Get SP metadata and verify it contains the custom entity ID
        // The endpoint is /.well-known/sp-metadata and returns XML directly
        String metadataXML = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/.well-known/sp-metadata", null, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertTrue("SP metadata should contain custom entity ID",
                metadataXML.contains("entityID=\"" + customSpEntityId + "\""));

        // Create a login request and verify the AuthnRequest uses the custom SP entity ID
        JsonObject loginBody = new JsonObject();
        loginBody.addProperty("clientId", clientInfo.clientId);
        loginBody.addProperty("redirectURI", clientInfo.defaultRedirectURI);
        loginBody.addProperty("acsURL", clientInfo.acsURL);

        JsonObject loginResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/login", loginBody, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("OK", loginResp.get("status").getAsString());

        // Decode the SAMLRequest and verify it contains the custom SP entity ID as Issuer
        String ssoRedirectURI = loginResp.get("ssoRedirectURI").getAsString();
        String samlRequestEncoded = extractSAMLRequest(ssoRedirectURI);
        String samlRequestXML = decodeSAMLRequest(samlRequestEncoded);

        assertTrue("AuthnRequest should contain custom SP entity ID as Issuer",
                samlRequestXML.contains(customSpEntityId));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that the default SP entity ID is used when not configured.
     */
    @Test
    public void testDefaultSpEntityId() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Get SP metadata and verify it contains the default entity ID
        String metadataXML = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/.well-known/sp-metadata", null, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertTrue("SP metadata should contain default entity ID",
                metadataXML.contains("entityID=\"https://saml.supertokens.com\""));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Test that SAML callback validates audience against the configured SP entity ID.
     */
    @Test
    public void testAudienceValidationWithCustomSpEntityId() throws Exception {
        String customSpEntityId = "https://custom-sp-audience.example.com/saml";
        Utils.setValueInConfig("saml_sp_entity_id", "\"" + customSpEntityId + "\"");

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        // Create SAML client
        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process,
                "http://localhost:3000/auth/callback/saml",
                "http://localhost:3000/acs",
                "https://saml.example.com/entityid-audience-test",
                "https://mocksaml.com/api/saml/sso"
        );

        // Create a login request to generate a RelayState
        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process,
                clientInfo.clientId,
                clientInfo.defaultRedirectURI,
                clientInfo.acsURL,
                "test-state"
        );

        // Generate a SAML Response with WRONG audience (default instead of custom)
        String samlResponseWrongAudience = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId,
                "https://saml.supertokens.com",  // Wrong! Should be customSpEntityId
                clientInfo.acsURL,
                "user@example.com",
                null,
                relayState,
                clientInfo.keyMaterial,
                300
        );

        // Try to process the callback - should fail because audience doesn't match
        JsonObject callbackBody = new JsonObject();
        callbackBody.addProperty("samlResponse", samlResponseWrongAudience);
        callbackBody.addProperty("relayState", relayState);

        JsonObject callbackResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", callbackBody, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("SAML_RESPONSE_VERIFICATION_FAILED_ERROR", callbackResp.get("status").getAsString());

        // Now create another login request and use the correct audience
        String relayState2 = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process,
                clientInfo.clientId,
                clientInfo.defaultRedirectURI,
                clientInfo.acsURL,
                "test-state-2"
        );

        String samlResponseCorrectAudience = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId,
                customSpEntityId,  // Correct audience
                clientInfo.acsURL,
                "user@example.com",
                null,
                relayState2,
                clientInfo.keyMaterial,
                300
        );

        JsonObject callbackBody2 = new JsonObject();
        callbackBody2.addProperty("samlResponse", samlResponseCorrectAudience);
        callbackBody2.addProperty("relayState", relayState2);

        JsonObject callbackResp2 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", callbackBody2, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("OK", callbackResp2.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // ============ Helper Methods ============

    private String extractCodeFromRedirectURI(String redirectURI) {
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

    private String decodeSAMLRequest(String encoded) throws Exception {
        // URL decode
        String urlDecoded = java.net.URLDecoder.decode(encoded, java.nio.charset.StandardCharsets.UTF_8.name());

        // Base64 decode
        byte[] base64Decoded = java.util.Base64.getDecoder().decode(urlDecoded);

        // DEFLATE decompress (raw deflate, not gzip)
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.util.zip.Inflater inflater = new java.util.zip.Inflater(true);
        java.util.zip.InflaterOutputStream ios = new java.util.zip.InflaterOutputStream(baos, inflater);
        ios.write(base64Decoded);
        ios.close();

        return baos.toString(java.nio.charset.StandardCharsets.UTF_8.name());
    }
}
