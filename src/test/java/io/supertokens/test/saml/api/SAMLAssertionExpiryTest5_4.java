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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.saml.SAMLStorage;
import io.supertokens.storageLayer.StorageLayer;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.Assert.*;

/**
 * The SAML callback accepts an assertion only within a window bounded by signed values: its IssueInstant
 * (plus the clock skew), its Conditions NotOnOrAfter and its bearer SubjectConfirmationData NotOnOrAfter, which
 * must be present. The consumed-assertion record is kept until that same window has passed (plus the clock
 * skew), so it cannot be purged while the assertion would still be accepted. On the SP-initiated path the
 * InResponseTo binding must also be present inside the signed assertion. Updating a client without `acsURLs`
 * keeps its ACS allow-list.
 */
public class SAMLAssertionExpiryTest5_4 {

    private static final String DEFAULT_REDIRECT_URI = "http://localhost:3000/auth/callback/saml-mock";
    private static final String ACS_URL = "http://localhost:3000/acs";
    private static final String IDP_ENTITY_ID = "https://saml.example.com/entityid";
    private static final String IDP_SSO_URL = "https://mocksaml.com/api/saml/sso";
    private static final String AUDIENCE = SAMLTestUtils.TEST_SP_ENTITY_ID;
    private static final String FAILED = "SAML_RESPONSE_VERIFICATION_FAILED_ERROR";

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

    private static TestingProcessManager.TestingProcess startWithSAML() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.SAML});
        return process;
    }

    private static void stop(TestingProcessManager.TestingProcess process) throws Exception {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static String callbackStatus(TestingProcessManager.TestingProcess process, String samlResponse,
                                         String relayState) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("samlResponse", samlResponse);
        if (relayState != null) {
            body.addProperty("relayState", relayState);
        }
        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
        return resp.get("status").getAsString();
    }

    private static SAMLTestUtils.CreatedClientInfo idpInitiatedClient(TestingProcessManager.TestingProcess process)
            throws Exception {
        return SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL, true /* allowIDPInitiatedLogin */);
    }

    private static MockSAML.AssertionSpec spec(SAMLTestUtils.CreatedClientInfo clientInfo) {
        return new MockSAML.AssertionSpec(clientInfo.idpEntityId, AUDIENCE, clientInfo.acsURL);
    }

    /**
     * A bearer SubjectConfirmationData NotOnOrAfter that has passed ends the assertion's acceptance, even when
     * the Conditions NotOnOrAfter is still in the future.
     */
    @Test
    public void testIssue224_expiredSubjectConfirmationRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        SAMLTestUtils.CreatedClientInfo clientInfo = idpInitiatedClient(process);

        MockSAML.AssertionSpec s = spec(clientInfo);
        s.conditionsNotOnOrAfter = Instant.now().plusSeconds(3600);
        s.scdNotOnOrAfter = Instant.now().minusSeconds(5);
        assertEquals(FAILED, callbackStatus(process, MockSAML.generateSignedSAMLResponseBase64(s,
                clientInfo.keyMaterial), null));

        stop(process);
    }

    /**
     * The consumed-assertion record outlives the assertion's acceptance window: after the bearer
     * SubjectConfirmationData NotOnOrAfter has passed and the expired SAML data has been purged, the record is
     * still there and the replayed response is rejected.
     */
    @Test
    public void testIssue224_consumedRecordOutlivesAcceptanceWindow() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        SAMLTestUtils.CreatedClientInfo clientInfo = idpInitiatedClient(process);

        // No Conditions NotOnOrAfter; the bearer confirmation is the only explicit bound.
        MockSAML.AssertionSpec s = spec(clientInfo);
        s.conditionsNotOnOrAfter = null;
        s.scdNotOnOrAfter = Instant.now().plusSeconds(3);
        String samlResponse = MockSAML.generateSignedSAMLResponseBase64(s, clientInfo.keyMaterial);

        assertEquals("OK", callbackStatus(process, samlResponse, null));

        Thread.sleep(4000);
        TenantIdentifier tenant = process.getAppForTesting();
        SAMLStorage samlStorage = StorageUtils.getSAMLStorage(StorageLayer.getStorage(tenant, process.getProcess()));
        samlStorage.removeExpiredSAMLCodesAndRelayStates();

        assertEquals(FAILED, callbackStatus(process, samlResponse, null));
        // The record of the consumed assertion was not purged.
        assertFalse(samlStorage.markSAMLAssertionConsumed(tenant, s.assertionId,
                System.currentTimeMillis() + 60000));

        stop(process);
    }

    /**
     * An assertion whose bearer SubjectConfirmationData has no NotOnOrAfter is rejected, whether or not it
     * carries a Conditions NotOnOrAfter.
     */
    @Test
    public void testAssertionWithoutSubjectConfirmationNotOnOrAfterRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        SAMLTestUtils.CreatedClientInfo clientInfo = idpInitiatedClient(process);

        MockSAML.AssertionSpec noExpiry = spec(clientInfo);
        noExpiry.conditionsNotOnOrAfter = null;
        noExpiry.scdNotOnOrAfter = null;
        assertEquals(FAILED, callbackStatus(process, MockSAML.generateSignedSAMLResponseBase64(noExpiry,
                clientInfo.keyMaterial), null));

        MockSAML.AssertionSpec conditionsOnly = spec(clientInfo);
        conditionsOnly.scdNotOnOrAfter = null;
        assertEquals(FAILED, callbackStatus(process, MockSAML.generateSignedSAMLResponseBase64(conditionsOnly,
                clientInfo.keyMaterial), null));

        stop(process);
    }

    /**
     * The signed Assertion IssueInstant bounds the assertion's age, even when it is wrapped in a Response with a
     * current IssueInstant and its NotOnOrAfter values are far in the future. A missing or future IssueInstant
     * is rejected too.
     */
    @Test
    public void testStaleAssertionIssueInstantRejected() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        SAMLTestUtils.CreatedClientInfo clientInfo = idpInitiatedClient(process);

        MockSAML.AssertionSpec stale = spec(clientInfo);
        stale.assertionIssueInstant = Instant.now().minusSeconds(3600);
        stale.conditionsNotBefore = Instant.now().minusSeconds(3600);
        stale.conditionsNotOnOrAfter = null;
        stale.scdNotOnOrAfter = Instant.now().plusSeconds(3600);
        assertEquals(FAILED, callbackStatus(process, MockSAML.generateSignedSAMLResponseBase64(stale,
                clientInfo.keyMaterial), null));

        MockSAML.AssertionSpec missing = spec(clientInfo);
        missing.assertionIssueInstant = null;
        assertEquals(FAILED, callbackStatus(process, MockSAML.generateSignedSAMLResponseBase64(missing,
                clientInfo.keyMaterial), null));

        MockSAML.AssertionSpec future = spec(clientInfo);
        future.assertionIssueInstant = Instant.now().plusSeconds(3600);
        assertEquals(FAILED, callbackStatus(process, MockSAML.generateSignedSAMLResponseBase64(future,
                clientInfo.keyMaterial), null));

        stop(process);
    }

    /**
     * A SubjectConfirmationData NotBefore in the future is enforced.
     */
    @Test
    public void testSubjectConfirmationNotBeforeEnforced() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        SAMLTestUtils.CreatedClientInfo clientInfo = idpInitiatedClient(process);

        MockSAML.AssertionSpec s = spec(clientInfo);
        s.scdNotBefore = Instant.now().plusSeconds(3600);
        s.scdNotOnOrAfter = Instant.now().plusSeconds(7200);
        assertEquals(FAILED, callbackStatus(process, MockSAML.generateSignedSAMLResponseBase64(s,
                clientInfo.keyMaterial), null));

        stop(process);
    }

    /**
     * An assertion bounded only by its bearer SubjectConfirmationData NotOnOrAfter (no Conditions NotOnOrAfter,
     * as the SAML Web Browser SSO profile allows) is still accepted while that bound has not passed.
     */
    @Test
    public void testAssertionBoundOnlyBySubjectConfirmationIsAccepted() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        SAMLTestUtils.CreatedClientInfo clientInfo = idpInitiatedClient(process);

        MockSAML.AssertionSpec s = spec(clientInfo);
        s.conditionsNotBefore = null;
        s.conditionsNotOnOrAfter = null;
        assertEquals("OK", callbackStatus(process, MockSAML.generateSignedSAMLResponseBase64(s,
                clientInfo.keyMaterial), null));

        stop(process);
    }

    /**
     * On the SP-initiated path the AuthnRequest ID must be echoed inside the signed assertion (bearer
     * SubjectConfirmationData InResponseTo), not only on the Response wrapper, which is unsigned when only the
     * assertion is signed.
     */
    @Test
    public void testInResponseToTakenFromSignedAssertion() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL, false);

        SAMLTestUtils.LoginInfo login = SAMLTestUtils.createLoginRequestAndGetLoginInfo(
                process, clientInfo.clientId, clientInfo.defaultRedirectURI, clientInfo.acsURL, "state");
        MockSAML.AssertionSpec unbound = spec(clientInfo);
        unbound.responseInResponseTo = login.requestId;
        unbound.scdInResponseTo = null;
        assertEquals(FAILED, callbackStatus(process, MockSAML.generateSignedSAMLResponseBase64(unbound,
                clientInfo.keyMaterial), login.relayState));

        SAMLTestUtils.LoginInfo login2 = SAMLTestUtils.createLoginRequestAndGetLoginInfo(
                process, clientInfo.clientId, clientInfo.defaultRedirectURI, clientInfo.acsURL, "state");
        MockSAML.AssertionSpec bound = spec(clientInfo);
        bound.responseInResponseTo = login2.requestId;
        bound.scdInResponseTo = login2.requestId;
        assertEquals("OK", callbackStatus(process, MockSAML.generateSignedSAMLResponseBase64(bound,
                clientInfo.keyMaterial), login2.relayState));

        stop(process);
    }

    private static JsonObject putClient(TestingProcessManager.TestingProcess process, String clientId,
                                        MockSAML.KeyMaterial km, JsonArray acsURLs) throws Exception {
        String metadataXML = MockSAML.generateIdpMetadataXML(IDP_ENTITY_ID, IDP_SSO_URL, km.certificate);
        JsonObject input = new JsonObject();
        if (clientId != null) {
            input.addProperty("clientId", clientId);
        }
        input.addProperty("defaultRedirectURI", DEFAULT_REDIRECT_URI);
        JsonArray redirectURIs = new JsonArray();
        redirectURIs.add(DEFAULT_REDIRECT_URI);
        input.add("redirectURIs", redirectURIs);
        input.addProperty("metadataXML",
                java.util.Base64.getEncoder().encodeToString(metadataXML.getBytes(StandardCharsets.UTF_8)));
        if (acsURLs != null) {
            input.add("acsURLs", acsURLs);
        }
        return HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", input, 1000, 1000, null, SemVer.v5_4.get(), "saml");
    }

    private static JsonObject getListedClient(TestingProcessManager.TestingProcess process, String clientId)
            throws Exception {
        JsonObject listResp = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients/list", null, 1000, 1000, null, SemVer.v5_4.get(),
                "saml");
        for (JsonElement c : listResp.get("clients").getAsJsonArray()) {
            if (c.getAsJsonObject().get("clientId").getAsString().equals(clientId)) {
                return c.getAsJsonObject();
            }
        }
        fail("client " + clientId + " not listed");
        return null;
    }

    /**
     * Re-saving a client without the `acsURLs` field keeps its ACS allow-list, so the SP-initiated ACS check
     * is not dropped by an update that does not mention it. An empty list clears it.
     */
    @Test
    public void testUpdateClientWithoutAcsUrlsKeepsAllowList() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();
        JsonArray acs = new JsonArray();
        acs.add(ACS_URL);

        String clientId = putClient(process, null, km, acs).get("clientId").getAsString();

        JsonObject updated = putClient(process, clientId, km, null);
        assertEquals("OK", updated.get("status").getAsString());
        assertEquals(acs, updated.get("acsURLs"));
        assertEquals(acs, getListedClient(process, clientId).get("acsURLs"));

        // A login for an ACS URL outside the kept allow-list is still refused.
        JsonObject body = new JsonObject();
        body.addProperty("clientId", clientId);
        body.addProperty("redirectURI", DEFAULT_REDIRECT_URI);
        body.addProperty("acsURL", "http://localhost:3000/another-acs");
        JsonObject loginResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/login", body, 1000, SAMLTestUtils.SP_KEY_READ_TIMEOUT_MS, null, SemVer.v5_4.get(), "saml");
        assertEquals("INVALID_CLIENT_ERROR", loginResp.get("status").getAsString());

        // An explicit empty list clears it.
        JsonObject cleared = putClient(process, clientId, km, new JsonArray());
        assertFalse(cleared.has("acsURLs"));
        assertFalse(getListedClient(process, clientId).has("acsURLs"));

        stop(process);
    }
}
