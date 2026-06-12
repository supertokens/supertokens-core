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
 * Regression test for the SAML XML Signature Wrapping (XSW) authentication bypass.
 *
 * Root cause (SAML.java):
 *   - verifySamlResponseSignature() iterates all assertions, validates any that carry
 *     a signature, and sets foundSignedAssertion=true — but does NOT record WHICH
 *     assertion was validated.
 *   - extractAllClaims() then iterates ALL assertions unconditionally. Gson's
 *     JsonObject.add() is last-writer-wins, so a forged unsigned assertion placed after
 *     the legitimate signed assertion overwrites NameID with the victim identity.
 *   - The timestamp and audience validators skip assertions with null Conditions, so a
 *     forged assertion with no Conditions passes every pre-claim check unverified.
 *
 * Attack:
 *   Signed assertion  (attacker@evil.com) + unsigned assertion (admin@victim.com)
 *   → signature check passes, forged NameID wins in extractAllClaims().
 *
 * This test FAILS without the fix and PASSES once it is applied.
 * The fix must reject any SAML Response that, when assertion-level signing is used,
 * contains at least one assertion without a valid signature.
 */
public class SAMLXSWTest5_4 {

    private static final String DEFAULT_REDIRECT_URI = "http://localhost:3000/auth/callback/saml-mock";
    private static final String ACS_URL              = "http://localhost:3000/acs";
    private static final String IDP_ENTITY_ID        = "https://saml.example.com/entityid";
    private static final String IDP_SSO_URL          = "https://mocksaml.com/api/saml/sso";

    // SP entity ID that SuperTokens core uses as the expected SAML audience.
    private static final String SP_ENTITY_ID         = "https://saml.supertokens.com";

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
    }

    /**
     * Submits an XSW-crafted SAMLResponse and asserts that the callback rejects it.
     *
     * Crafted response contains:
     *   - Assertion 1 (signed):   NameID = attacker@evil.com  (legitimate IdP-signed)
     *   - Assertion 2 (unsigned): NameID = admin@victim.com   (forged, no Conditions)
     *
     * Without the fix: callback returns OK and authenticates admin@victim.com — FAIL.
     * With    the fix: callback returns SAML_RESPONSE_VERIFICATION_FAILED_ERROR — PASS.
     */
    @Test
    public void xswAttack_responseWithUnsignedSiblingAssertionMustBeRejected() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.SAML});

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL);

        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process, clientInfo.clientId, clientInfo.defaultRedirectURI,
                clientInfo.acsURL, "test-state");

        // Craft the XSW response: signed assertion for attacker, unsigned forged assertion for victim.
        String xswResponseBase64 = MockSAML.generateXSWWrappedSAMLResponseBase64(
                clientInfo.idpEntityId,
                SP_ENTITY_ID,
                clientInfo.acsURL,
                ATTACKER_EMAIL,
                VICTIM_EMAIL,
                relayState,
                clientInfo.keyMaterial,
                300);

        JsonObject callbackBody = new JsonObject();
        callbackBody.addProperty("samlResponse", xswResponseBase64);
        callbackBody.addProperty("relayState", relayState);

        JsonObject callbackResp = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback",
                callbackBody, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        // A response that mixes signed and unsigned assertions at the assertion level must be
        // rejected: only the signed assertion was verified by the IdP, so the unsigned sibling
        // must not be consumed.
        assertEquals(
                "XSW attack was not rejected: a SAMLResponse containing an unsigned sibling " +
                "assertion alongside a signed one must be refused",
                "SAML_RESPONSE_VERIFICATION_FAILED_ERROR", callbackResp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
