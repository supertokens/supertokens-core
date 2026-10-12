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
import io.supertokens.pluginInterface.saml.SAMLClient;
import io.supertokens.pluginInterface.saml.SAMLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.saml.MockSAML;
import io.supertokens.test.saml.SAMLTestUtils;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * IdP-initiated login has no login-time ACS URL to pin the response to, so a SAML client may only have it
 * enabled together with a non-empty ACS allow-list (`acsURLs`). This is enforced on every create and update
 * of a client, and at callback time for clients stored before the rule existed. (GHSA-wfg2-vqfg-rqh6)
 */
public class SAMLIdpInitiatedAcsURLsTest5_4 {

    private static final String DEFAULT_REDIRECT_URI = "http://localhost:3000/auth/callback/saml-mock";
    private static final String ACS_URL = "http://localhost:3000/auth/public/saml/callback";
    private static final String IDP_ENTITY_ID = "https://saml.example.com/entityid";
    private static final String IDP_SSO_URL = "https://mocksaml.com/api/saml/sso";
    private static final String ACS_REQUIRED_MESSAGE =
            "Http error. Status Code: 400. Message: acsURLs must contain at least one URL when " +
                    "allowIDPInitiatedLogin is true";

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

    private static JsonObject clientInput(String clientId, MockSAML.KeyMaterial keyMaterial,
                                          Boolean allowIDPInitiatedLogin, JsonArray acsURLs) {
        String metadataXML = MockSAML.generateIdpMetadataXML(IDP_ENTITY_ID, IDP_SSO_URL, keyMaterial.certificate);
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
        if (allowIDPInitiatedLogin != null) {
            input.addProperty("allowIDPInitiatedLogin", allowIDPInitiatedLogin);
        }
        if (acsURLs != null) {
            input.add("acsURLs", acsURLs);
        }
        return input;
    }

    private static JsonObject putClient(TestingProcessManager.TestingProcess process, JsonObject input)
            throws Exception {
        return HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", input, 1000, 1000, null, SemVer.v5_4.get(), "saml");
    }

    private static void assertPutRejected(TestingProcessManager.TestingProcess process, JsonObject input,
                                          String expectedMessage) throws Exception {
        try {
            putClient(process, input);
            fail("expected the client write to be rejected");
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals(expectedMessage, e.getMessage());
        }
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

    private static JsonArray acsList(String... urls) {
        JsonArray a = new JsonArray();
        for (String u : urls) {
            a.add(u);
        }
        return a;
    }

    @Test
    public void createWithIdpInitiatedRequiresAcsURLs() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();

        // acsURLs absent
        assertPutRejected(process, clientInput(null, km, true, null), ACS_REQUIRED_MESSAGE);
        // acsURLs empty
        assertPutRejected(process, clientInput(null, km, true, new JsonArray()), ACS_REQUIRED_MESSAGE);
        // acsURLs with only a blank entry
        assertPutRejected(process, clientInput(null, km, true, acsList(" ")),
                "Http error. Status Code: 400. Message: acsURLs must only contain non-empty strings");

        // Nothing was stored by the rejected writes.
        JsonObject listResp = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients/list", null, 1000, 1000, null, SemVer.v5_4.get(),
                "saml");
        assertEquals(0, listResp.get("clients").getAsJsonArray().size());

        // With an allow-list the client is created.
        JsonObject created = putClient(process, clientInput(null, km, true, acsList(ACS_URL)));
        assertEquals("OK", created.get("status").getAsString());
        assertTrue(created.get("allowIDPInitiatedLogin").getAsBoolean());
        assertEquals(acsList(ACS_URL), created.get("acsURLs"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createWithoutIdpInitiatedDoesNotRequireAcsURLs() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();

        JsonObject explicitFalse = putClient(process, clientInput(null, km, false, null));
        assertEquals("OK", explicitFalse.get("status").getAsString());
        assertFalse(explicitFalse.get("allowIDPInitiatedLogin").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void updateCannotEnableIdpInitiatedWithoutAcsURLs() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();

        String clientId = putClient(process, clientInput(null, km, false, null)).get("clientId").getAsString();

        assertPutRejected(process, clientInput(clientId, km, true, null), ACS_REQUIRED_MESSAGE);
        assertPutRejected(process, clientInput(clientId, km, true, new JsonArray()), ACS_REQUIRED_MESSAGE);

        // The stored client is unchanged.
        JsonObject listed = getListedClient(process, clientId);
        assertFalse(listed.get("allowIDPInitiatedLogin").getAsBoolean());
        assertFalse(listed.has("acsURLs"));

        // Enabling it together with an allow-list works.
        JsonObject updated = putClient(process, clientInput(clientId, km, true, acsList(ACS_URL)));
        assertTrue(updated.get("allowIDPInitiatedLogin").getAsBoolean());
        assertEquals(acsList(ACS_URL), updated.get("acsURLs"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void updateCannotDropAcsURLsWhileIdpInitiatedIsEnabled() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();

        String clientId = putClient(process, clientInput(null, km, true, acsList(ACS_URL)))
                .get("clientId").getAsString();

        // An update replaces the whole client, so omitting acsURLs would clear the list.
        assertPutRejected(process, clientInput(clientId, km, true, null), ACS_REQUIRED_MESSAGE);
        assertPutRejected(process, clientInput(clientId, km, true, new JsonArray()), ACS_REQUIRED_MESSAGE);

        JsonObject listed = getListedClient(process, clientId);
        assertTrue(listed.get("allowIDPInitiatedLogin").getAsBoolean());
        assertEquals(acsList(ACS_URL), listed.get("acsURLs"));

        // Disabling IdP-initiated login lets the list go.
        JsonObject updated = putClient(process, clientInput(clientId, km, false, null));
        assertFalse(updated.get("allowIDPInitiatedLogin").getAsBoolean());
        assertFalse(updated.has("acsURLs"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * A client stored before the rule existed (IdP-initiated enabled, no allow-list) cannot be fixed up by
     * the API, so the callback refuses IdP-initiated logins for it until an allow-list is configured.
     */
    @Test
    public void storedClientWithIdpInitiatedButNoAcsURLsIsRefusedAtCallback() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL,
                true /* allowIDPInitiatedLogin */, acsList(ACS_URL));

        // Simulate a pre-existing row by writing the client straight to storage without its allow-list.
        TenantIdentifier tenant = process.getAppForTesting();
        SAMLStorage samlStorage = StorageUtils.getSAMLStorage(StorageLayer.getStorage(tenant, process.getProcess()));
        SAMLClient stored = samlStorage.getSAMLClient(tenant, clientInfo.clientId);
        samlStorage.createOrUpdateSAMLClient(tenant, new SAMLClient(stored.clientId, stored.clientSecret,
                stored.ssoLoginURL, stored.redirectURIs, stored.defaultRedirectURI, stored.idpEntityId,
                stored.idpSigningCertificate, true, stored.enableRequestSigning, null));

        String samlResponse = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId, SAMLTestUtils.TEST_SP_ENTITY_ID, ACS_URL, "user@example.com",
                null, null, clientInfo.keyMaterial, 300);
        JsonObject body = new JsonObject();
        body.addProperty("samlResponse", samlResponse);
        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
        assertEquals("IDP_LOGIN_DISALLOWED_ERROR", resp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
