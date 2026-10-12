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

import com.google.gson.JsonArray;
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
 * Regression tests for GHSA-wfg2-vqfg-rqh6, Attack 2: with saml_sp_entity_id left at its default, every
 * SuperTokens deployment has the same audience, so an assertion the IdP issued to another deployment passes
 * the audience check here. It must still be rejected because its signed Recipient names the other
 * deployment's ACS URL, and an assertion without a Recipient must be rejected outright.
 *
 * No saml_sp_entity_id is configured in these tests, so the core runs with the shared default.
 */
public class SAMLDefaultSpEntityIdCrossDeploymentTest5_4 {

    private static final String DEFAULT_REDIRECT_URI = "http://localhost:3000/auth/callback/saml-mock";
    private static final String THIS_ACS_URL = "http://localhost:3000/auth/public/saml/callback";
    private static final String OTHER_DEPLOYMENT_ACS_URL =
            "https://other-deployment.example.com/auth/public/saml/callback";
    private static final String IDP_ENTITY_ID = "https://saml.example.com/entityid";
    private static final String IDP_SSO_URL = "https://mocksaml.com/api/saml/sso";
    private static final String DEFAULT_SP_ENTITY_ID = "https://saml.supertokens.com";

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

    private static SAMLTestUtils.CreatedClientInfo createClient(TestingProcessManager.TestingProcess process)
            throws Exception {
        JsonArray acsURLs = new JsonArray();
        acsURLs.add(THIS_ACS_URL);
        return SAMLTestUtils.createClientWithGeneratedMetadata(process, DEFAULT_REDIRECT_URI, THIS_ACS_URL,
                IDP_ENTITY_ID, IDP_SSO_URL, true /* allowIDPInitiatedLogin */, acsURLs);
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

    /** Control: the default entity ID is a working audience for an assertion issued to this deployment. */
    @Test
    public void assertionIssuedToThisDeploymentIsAccepted() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        SAMLTestUtils.CreatedClientInfo client = createClient(process);

        String idpInitiated = MockSAML.generateSignedSAMLResponseBase64(client.idpEntityId, DEFAULT_SP_ENTITY_ID,
                THIS_ACS_URL, "idp-initiated@example.com", null, null, client.keyMaterial, 300);
        assertEquals("OK", callback(process, idpInitiated, null).get("status").getAsString());

        SAMLTestUtils.LoginInfo login = SAMLTestUtils.createLoginRequestAndGetLoginInfo(process, client.clientId,
                DEFAULT_REDIRECT_URI, THIS_ACS_URL, "state");
        String spInitiated = MockSAML.generateSignedSAMLResponseBase64(client.idpEntityId, DEFAULT_SP_ENTITY_ID,
                THIS_ACS_URL, "sp-initiated@example.com", null, login.requestId, client.keyMaterial, 300);
        assertEquals("OK", callback(process, spInitiated, login.relayState).get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void idpInitiatedAssertionIssuedToAnotherDeploymentIsRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        SAMLTestUtils.CreatedClientInfo client = createClient(process);

        // Same IdP key, same (default) audience, but issued to the other deployment's ACS URL.
        String samlResponse = MockSAML.generateSignedSAMLResponseBase64(client.idpEntityId, DEFAULT_SP_ENTITY_ID,
                OTHER_DEPLOYMENT_ACS_URL, "victim@example.com", null, null, client.keyMaterial, 300);

        assertEquals("SAML_RESPONSE_VERIFICATION_FAILED_ERROR",
                callback(process, samlResponse, null).get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void idpInitiatedAssertionWithoutRecipientIsRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        SAMLTestUtils.CreatedClientInfo client = createClient(process);

        // The unsigned wrapper names this deployment, but the signed assertion carries no Recipient.
        String samlResponse = MockSAML.generateSignedSAMLResponseWithoutRecipientBase64(client.idpEntityId,
                DEFAULT_SP_ENTITY_ID, THIS_ACS_URL, "victim@example.com", null, client.keyMaterial, 300);

        assertEquals("SAML_RESPONSE_VERIFICATION_FAILED_ERROR",
                callback(process, samlResponse, null).get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void spInitiatedAssertionIssuedToAnotherDeploymentIsRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        SAMLTestUtils.CreatedClientInfo client = createClient(process);

        SAMLTestUtils.LoginInfo login = SAMLTestUtils.createLoginRequestAndGetLoginInfo(process, client.clientId,
                DEFAULT_REDIRECT_URI, THIS_ACS_URL, "state");
        // InResponseTo matches this login, so only the Recipient ties the assertion to the other deployment.
        String samlResponse = MockSAML.generateSignedSAMLResponseBase64(client.idpEntityId, DEFAULT_SP_ENTITY_ID,
                OTHER_DEPLOYMENT_ACS_URL, "victim@example.com", null, login.requestId, client.keyMaterial, 300);

        assertEquals("SAML_RESPONSE_VERIFICATION_FAILED_ERROR",
                callback(process, samlResponse, login.relayState).get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void spInitiatedAssertionWithoutRecipientIsRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        SAMLTestUtils.CreatedClientInfo client = createClient(process);

        SAMLTestUtils.LoginInfo login = SAMLTestUtils.createLoginRequestAndGetLoginInfo(process, client.clientId,
                DEFAULT_REDIRECT_URI, THIS_ACS_URL, "state");
        String samlResponse = MockSAML.generateSignedSAMLResponseWithoutRecipientBase64(client.idpEntityId,
                DEFAULT_SP_ENTITY_ID, THIS_ACS_URL, "victim@example.com", login.requestId, client.keyMaterial, 300);

        assertEquals("SAML_RESPONSE_VERIFICATION_FAILED_ERROR",
                callback(process, samlResponse, login.relayState).get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
