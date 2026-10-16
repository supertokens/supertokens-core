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
 * Regression tests for IdP-initiated ACS pinning (GHSA-3r7x-rhvc-fw28 follow-up). On the IdP-initiated
 * path there is no login-time ACS URL to pin against, so Destination/Recipient pinning uses the SAML
 * client's ACS allow-list (`acsURLs`), which IdP-initiated login requires (see SAMLIdpInitiatedAcsURLsTest5_4).
 */
public class SAMLAcsPinningTest5_4 {

    private static final String DEFAULT_REDIRECT_URI = "http://localhost:3000/auth/callback/saml-mock";
    private static final String ACS_URL              = "http://localhost:3000/acs";
    private static final String OTHER_ACS_URL        = "http://localhost:3000/other-acs";
    private static final String IDP_ENTITY_ID        = "https://saml.example.com/entityid";
    private static final String IDP_SSO_URL          = "https://mocksaml.com/api/saml/sso";
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

    private static JsonObject idpInitiatedCallback(TestingProcessManager.TestingProcess process, String samlResponse)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("samlResponse", samlResponse);
        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
    }

    private static JsonArray acsList(String... urls) {
        JsonArray a = new JsonArray();
        for (String u : urls) {
            a.add(u);
        }
        return a;
    }

    /** IdP-initiated, client has a configured ACS matching the response's Destination/Recipient -> accepted. */
    @Test
    public void idpInitiatedWithConfiguredAcsMatchingIsAccepted() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL,
                true /* allowIDPInitiatedLogin */, acsList(ACS_URL));

        String samlResponse = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId, AUDIENCE, ACS_URL, "user@example.com",
                null, null /* no inResponseTo for IdP-initiated */, clientInfo.keyMaterial, 300);

        JsonObject resp = idpInitiatedCallback(process, samlResponse);
        assertEquals("OK", resp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /** IdP-initiated, client has a configured ACS, but the response targets a different ACS -> rejected. */
    @Test
    public void idpInitiatedWithConfiguredAcsMismatchIsRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL,
                true /* allowIDPInitiatedLogin */, acsList(ACS_URL));

        // The IdP mints the assertion for a *different* ACS endpoint (Destination + Recipient = OTHER_ACS_URL),
        // which is not in this client's allow-list.
        String samlResponse = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId, AUDIENCE, OTHER_ACS_URL, "user@example.com",
                null, null, clientInfo.keyMaterial, 300);

        JsonObject resp = idpInitiatedCallback(process, samlResponse);
        assertEquals("SAML_RESPONSE_VERIFICATION_FAILED_ERROR", resp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
