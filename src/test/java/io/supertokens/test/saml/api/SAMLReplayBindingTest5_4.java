/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Regression tests for the SAML callback replay and binding hardening (GHSA-3r7x-rhvc-fw28):
 *  - assertion single-use closes the IdP-initiated replay window (no RelayState to consume there),
 *  - InResponseTo binding ties an SP-initiated response to the AuthnRequest it answers, and rejects a
 *    solicited response smuggled through the unauthenticated IdP-initiated branch,
 *  - Destination / Recipient pinning rejects an assertion minted for a different ACS endpoint.
 */
public class SAMLReplayBindingTest5_4 {

    private static final String DEFAULT_REDIRECT_URI = "http://localhost:3000/auth/callback/saml-mock";
    private static final String ACS_URL              = "http://localhost:3000/acs";
    private static final String IDP_ENTITY_ID        = "https://saml.example.com/entityid";
    private static final String IDP_SSO_URL          = "https://mocksaml.com/api/saml/sso";
    // The default saml_sp_entity_id, which the audience restriction is validated against.
    private static final String AUDIENCE             = "https://saml.supertokens.com";

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    private static TestingProcessManager.TestingProcess startWithSAML() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.SAML});
        return process;
    }

    private static JsonObject callback(TestingProcessManager.TestingProcess process, String samlResponse,
                                       String relayState) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("samlResponse", samlResponse);
        if (relayState != null) {
            body.addProperty("relayState", relayState);
        }
        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
    }

    /**
     * The IdP-initiated path has no RelayState to consume, so before the assertion single-use cache the
     * exact same signed response could be posted repeatedly, each time minting a fresh code. It must now
     * be accepted at most once.
     */
    @Test
    public void idpInitiatedResponseCannotBeReplayed() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL, true /* allowIDPInitiatedLogin */);

        String samlResponse = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId, AUDIENCE, clientInfo.acsURL, "user@example.com",
                null, null /* no inResponseTo for IdP-initiated */, clientInfo.keyMaterial, 300);

        JsonObject first = callback(process, samlResponse, null);
        assertEquals("OK", first.get("status").getAsString());

        JsonObject replay = callback(process, samlResponse, null);
        assertEquals("SAML_RESPONSE_VERIFICATION_FAILED_ERROR", replay.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * An unsolicited (IdP-initiated) response must not carry InResponseTo; a present one indicates a
     * solicited response being replayed through the branch that does no RelayState lookup.
     */
    @Test
    public void idpInitiatedResponseWithInResponseToIsRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL, true /* allowIDPInitiatedLogin */);

        String samlResponse = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId, AUDIENCE, clientInfo.acsURL, "user@example.com",
                null, "_some-authn-request-id" /* InResponseTo present */, clientInfo.keyMaterial, 300);

        JsonObject resp = callback(process, samlResponse, null);
        assertEquals("SAML_RESPONSE_VERIFICATION_FAILED_ERROR", resp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * On the SP-initiated path the response InResponseTo must equal the AuthnRequest ID we issued. A
     * response whose InResponseTo is some other value is not answering this login and is rejected.
     */
    @Test
    public void spInitiatedWrongInResponseToIsRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL, false);
        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process, clientInfo.clientId, clientInfo.defaultRedirectURI, clientInfo.acsURL, "test-state");

        String samlResponse = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId, AUDIENCE, clientInfo.acsURL, "user@example.com",
                null, "_not-the-issued-request-id", clientInfo.keyMaterial, 300);

        JsonObject resp = callback(process, samlResponse, relayState);
        assertEquals("SAML_RESPONSE_VERIFICATION_FAILED_ERROR", resp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * The response Destination and the assertion's SubjectConfirmationData Recipient must match the ACS
     * URL this login was started with. A response addressed to a different ACS is rejected even when its
     * InResponseTo is correct, so a stolen assertion issued for another endpoint cannot be redirected here.
     */
    @Test
    public void spInitiatedWrongDestinationIsRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL, false);
        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process, clientInfo.clientId, clientInfo.defaultRedirectURI, clientInfo.acsURL, "test-state");

        // Destination and Recipient are set from this acsUrl, which differs from the login's ACS URL.
        String samlResponse = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId, AUDIENCE, "http://localhost:3000/a-different-acs", "user@example.com",
                null, SAMLTestUtils.requestIdFor(relayState), clientInfo.keyMaterial, 300);

        JsonObject resp = callback(process, samlResponse, relayState);
        assertEquals("SAML_RESPONSE_VERIFICATION_FAILED_ERROR", resp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * The happy SP-initiated path still works end to end with the new checks in place: correct
     * InResponseTo, matching Destination/Recipient, and a first-time assertion are accepted.
     */
    @Test
    public void spInitiatedValidResponseIsAccepted() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL, false);
        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process, clientInfo.clientId, clientInfo.defaultRedirectURI, clientInfo.acsURL, "test-state");

        String samlResponse = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId, AUDIENCE, clientInfo.acsURL, "user@example.com",
                null, SAMLTestUtils.requestIdFor(relayState), clientInfo.keyMaterial, 300);

        JsonObject resp = callback(process, samlResponse, relayState);
        assertEquals("OK", resp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
