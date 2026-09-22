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

import static org.junit.Assert.*;

/**
 * GHSA-wfg2-vqfg-rqh6: the audience restriction was validated per response (first matching assertion)
 * while identity was extracted from every assertion (last NameID wins), so an assertion issued to a
 * different audience could supply the identity while another assertion satisfied the audience. The fix
 * takes identity from a single assertion and validates that same assertion's audience on it.
 */
public class SAMLMultiAssertionAudienceTest5_4 {

    private static final String DEFAULT_REDIRECT_URI = "http://localhost:3000/auth/callback/saml-mock";
    private static final String ACS_URL              = "http://localhost:3000/acs";
    private static final String IDP_ENTITY_ID        = "https://saml.example.com/entityid";
    private static final String IDP_SSO_URL          = "https://mocksaml.com/api/saml/sso";

    // Matches SAMLTestUtils.TEST_SP_ENTITY_ID set in @Before via setSAMLSPEntityIdConfig().
    private static final String SP_ENTITY_ID         = "https://saml.supertokens.com";
    // An audience the victim's assertion was issued to (a different Service Provider).
    private static final String OTHER_AUDIENCE       = "https://other-sp.example/entityid";

    private static final String ATTACKER_EMAIL       = "attacker@evil.com";
    private static final String VICTIM_EMAIL         = "admin@victim.com";

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
        SAMLTestUtils.setSAMLSPEntityIdConfig();
    }

    private static String extractCode(String redirectURI) {
        int idx = redirectURI.indexOf("code=");
        assertTrue("redirectURI has no code: " + redirectURI, idx != -1);
        String rest = redirectURI.substring(idx + "code=".length());
        int amp = rest.indexOf('&');
        return amp == -1 ? rest : rest.substring(0, amp);
    }

    /**
     * The attacker's own assertion (correct audience) comes first, the victim's assertion (a different
     * audience) is appended last. Before the fix, extractAllClaims was last-writer-wins, so the response
     * authenticated the victim. After the fix, identity comes from the single validated assertion, so the
     * subject is the attacker and never the victim.
     */
    @Test
    public void attackerFirstVictimLast_authenticatesAttackerNeverVictim() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.SAML});

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL);
        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process, clientInfo.clientId, clientInfo.defaultRedirectURI, clientInfo.acsURL, "test-state");

        String responseBase64 = MockSAML.generateTwoAssertionResponseBase64(
                clientInfo.idpEntityId, clientInfo.acsURL,
                ATTACKER_EMAIL, SP_ENTITY_ID,
                VICTIM_EMAIL, OTHER_AUDIENCE,
                relayState, clientInfo.keyMaterial, 300);

        JsonObject callbackBody = new JsonObject();
        callbackBody.addProperty("samlResponse", responseBase64);
        callbackBody.addProperty("relayState", relayState);
        JsonObject callbackResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", callbackBody, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("OK", callbackResp.get("status").getAsString());
        String code = extractCode(callbackResp.get("redirectURI").getAsString());

        JsonObject userInfoBody = new JsonObject();
        userInfoBody.addProperty("accessToken", code);
        userInfoBody.addProperty("clientId", clientInfo.clientId);
        JsonObject userInfoResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/user", userInfoBody, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("OK", userInfoResp.get("status").getAsString());
        String sub = userInfoResp.get("sub").getAsString();
        assertEquals("identity must come from the audience-validated assertion, not the last one", ATTACKER_EMAIL, sub);
        assertNotEquals("the victim identity must never be returned", VICTIM_EMAIL, sub);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * The victim's assertion (a different audience) is first, the attacker's assertion (correct audience)
     * is second. The authenticating assertion is the first one, its audience does not match, so the whole
     * response is rejected.
     */
    @Test
    public void victimFirstWithWrongAudience_isRejected() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.SAML});

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL);
        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process, clientInfo.clientId, clientInfo.defaultRedirectURI, clientInfo.acsURL, "test-state");

        String responseBase64 = MockSAML.generateTwoAssertionResponseBase64(
                clientInfo.idpEntityId, clientInfo.acsURL,
                VICTIM_EMAIL, OTHER_AUDIENCE,
                ATTACKER_EMAIL, SP_ENTITY_ID,
                relayState, clientInfo.keyMaterial, 300);

        JsonObject callbackBody = new JsonObject();
        callbackBody.addProperty("samlResponse", responseBase64);
        callbackBody.addProperty("relayState", relayState);
        JsonObject callbackResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", callbackBody, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("SAML_RESPONSE_VERIFICATION_FAILED_ERROR", callbackResp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
