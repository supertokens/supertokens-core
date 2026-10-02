package io.supertokens.test.saml.api;

import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonObject;

import io.supertokens.ProcessState;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.saml.MockSAML;
import io.supertokens.test.saml.SAMLTestUtils;
import io.supertokens.utils.SemVer;

/**
 * Regression test for a missing one-time-use enforcement on the SAML callback: the same
 * signed SAMLResponse, paired with the same RelayState, can be replayed to the callback
 * endpoint repeatedly and each replay mints a fresh, independently redeemable authorization
 * code carrying the IdP-asserted identity. Neither the RelayState row nor the assertion is
 * invalidated after first use, and Response.InResponseTo is never checked, so nothing ties
 * a given signed assertion to a single consumption.
 */
public class SAMLResponseReplayTest5_4 {

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
        // saml_sp_entity_id has no default and is required (GHSA-wfg2); the SAML callback 500s without it.
        SAMLTestUtils.setSAMLSPEntityIdConfig();
    }

    @Test
    public void testSameSignedResponseAndRelayStateCanBeReplayedToMintMultipleCodes() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.SAML});

        String defaultRedirectURI = "http://localhost:3000/auth/callback/saml-mock";
        String acsURL = "http://localhost:3000/acs";
        String idpEntityId = "https://saml.example.com/entityid";
        String idpSsoUrl = "https://mocksaml.com/api/saml/sso";

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, defaultRedirectURI, acsURL, idpEntityId, idpSsoUrl);

        // One SP-initiated login attempt, one RelayState.
        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process, clientInfo.clientId, clientInfo.defaultRedirectURI, clientInfo.acsURL, "test-state");

        // One signed SAMLResponse from the IdP for this flow.
        String samlResponseBase64 = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId,
                "https://saml.supertokens.com",
                clientInfo.acsURL,
                "victim@example.com",
                null,
                null,
                clientInfo.keyMaterial,
                300
        );

        JsonObject body = new JsonObject();
        body.addProperty("samlResponse", samlResponseBase64);
        body.addProperty("relayState", relayState);

        // First submission: this is the legitimate flow, and it must succeed.
        JsonObject firstResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
        assertEquals("OK", firstResp.get("status").getAsString());
        String firstCode = extractCode(firstResp.get("redirectURI").getAsString());
        assertNotNull(firstCode);

        // Replay: the exact same HTTP request body (captured SAMLResponse + RelayState),
        // submitted again with no further IdP interaction. A correct implementation must
        // reject this (RelayState and/or assertion already consumed).
        JsonObject secondResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertNotEquals("A replayed SAMLResponse + RelayState must not mint a second OK, "
                        + "but the callback accepted it again",
                "OK", secondResp.get("status").getAsString());

        if ("OK".equals(secondResp.get("status").getAsString())) {
            String secondCode = extractCode(secondResp.get("redirectURI").getAsString());

            // Both codes are independently valid and redeemable for the victim's identity,
            // proving this is not just a duplicate response but two live, usable sessions
            // minted from a single IdP assertion.
            JsonObject userInfoBody1 = new JsonObject();
            userInfoBody1.addProperty("accessToken", firstCode);
            userInfoBody1.addProperty("clientId", clientInfo.clientId);
            JsonObject userInfo1 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/user", userInfoBody1, 1000, 1000, null, SemVer.v5_4.get(), "saml");
            assertEquals("victim@example.com",
                    userInfo1.get("claims").getAsJsonObject().get("NameID").getAsJsonArray().get(0).getAsString());

            JsonObject userInfoBody2 = new JsonObject();
            userInfoBody2.addProperty("accessToken", secondCode);
            userInfoBody2.addProperty("clientId", clientInfo.clientId);
            JsonObject userInfo2 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/saml/user", userInfoBody2, 1000, 1000, null, SemVer.v5_4.get(), "saml");
            assertEquals("victim@example.com",
                    userInfo2.get("claims").getAsJsonObject().get("NameID").getAsJsonArray().get(0).getAsString());

            assertNotEquals(firstCode, secondCode);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static String extractCode(String redirectURI) {
        int idx = redirectURI.indexOf("code=");
        if (idx == -1) {
            return null;
        }
        String rest = redirectURI.substring(idx + "code=".length());
        int amp = rest.indexOf('&');
        String code = amp == -1 ? rest : rest.substring(0, amp);
        return java.net.URLDecoder.decode(code, java.nio.charset.StandardCharsets.UTF_8);
    }
}
