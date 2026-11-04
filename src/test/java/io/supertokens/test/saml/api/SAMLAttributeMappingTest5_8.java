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
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The per-client SAML `attributeMapping` (CDI 5.8) and the required-once-set rule that covers it and `acsURLs` on
 * `PUT /recipe/saml/clients`.
 */
public class SAMLAttributeMappingTest5_8 {

    private static final String DEFAULT_REDIRECT_URI = "http://localhost:3000/auth/callback/saml-mock";
    private static final String ACS_URL = "http://localhost:3000/acs";
    private static final String IDP_ENTITY_ID = "https://saml.example.com/entityid";
    private static final String IDP_SSO_URL = "https://mocksaml.com/api/saml/sso";

    private static final String OBJECT_ID = "http://schemas.microsoft.com/identity/claims/objectidentifier";
    private static final String NAME_CLAIM = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name";
    private static final String NAME_IDENTIFIER =
            "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier";
    private static final String EMAIL_ADDRESS = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress";

    private static final String BAD_REQUEST = "Http error. Status Code: 400. Message: ";
    private static final String ACS_REQUIRED_MESSAGE = BAD_REQUEST +
            "acsURLs must contain at least one URL when allowIDPInitiatedLogin is true";
    private static final String ACS_URLS_SET_MESSAGE = BAD_REQUEST +
            "acsURLs is set on this client: send its current value to keep it, or null to clear it";
    private static final String MAPPING_SET_MESSAGE = BAD_REQUEST +
            "attributeMapping is set on this client: send its current value to keep it, or null to clear it";

    // A key-presence marker: the field is left out of the request.
    private static final JsonElement ABSENT = null;

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() throws Exception {
        Utils.reset();
        SAMLTestUtils.setSAMLSPEntityIdConfig();
        Utils.setValueInConfig("saml_legacy_acs_url", "http://localhost:3567/recipe/saml/legacy/callback");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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

    private static JsonObject clientInput(String clientId, MockSAML.KeyMaterial keyMaterial,
                                          boolean allowIDPInitiatedLogin, JsonElement acsURLs,
                                          JsonElement attributeMapping) {
        String metadataXML = MockSAML.generateIdpMetadataXML(idpEntityId(keyMaterial), IDP_SSO_URL,
                keyMaterial.certificate);
        JsonObject input = new JsonObject();
        if (clientId != null) {
            input.addProperty("clientId", clientId);
        }
        input.addProperty("clientSecret", "secret");
        input.addProperty("defaultRedirectURI", DEFAULT_REDIRECT_URI);
        JsonArray redirectURIs = new JsonArray();
        redirectURIs.add(DEFAULT_REDIRECT_URI);
        input.add("redirectURIs", redirectURIs);
        input.addProperty("metadataXML",
                java.util.Base64.getEncoder().encodeToString(metadataXML.getBytes(StandardCharsets.UTF_8)));
        input.addProperty("allowIDPInitiatedLogin", allowIDPInitiatedLogin);
        if (acsURLs != ABSENT) {
            input.add("acsURLs", acsURLs);
        }
        if (attributeMapping != ABSENT) {
            input.add("attributeMapping", attributeMapping);
        }
        return input;
    }

    // A tenant's clients need distinct IdP entity IDs, so each key material (one per client) gets its own.
    private static String idpEntityId(MockSAML.KeyMaterial keyMaterial) {
        return IDP_ENTITY_ID + "/" + Integer.toHexString(System.identityHashCode(keyMaterial));
    }

    private static JsonObject putClient(TestingProcessManager.TestingProcess process, JsonObject input)
            throws Exception {
        return putClient(process, input, SemVer.v5_4.get());
    }

    private static JsonObject putClient(TestingProcessManager.TestingProcess process, JsonObject input,
                                        String cdiVersion) throws Exception {
        return HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients", input, 1000, 1000, null, cdiVersion, "saml");
    }

    private static void assertPutRejected(TestingProcessManager.TestingProcess process, JsonObject input,
                                          String expectedMessage) throws Exception {
        assertPutRejected(process, input, expectedMessage, SemVer.v5_4.get());
    }

    private static void assertPutRejected(TestingProcessManager.TestingProcess process, JsonObject input,
                                          String expectedMessage, String cdiVersion) throws Exception {
        try {
            putClient(process, input, cdiVersion);
            fail("expected the client write to be rejected: " + input);
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

    private static JsonArray list(String... values) {
        JsonArray a = new JsonArray();
        for (String v : values) {
            a.add(v);
        }
        return a;
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static JsonObject polisPreset() {
        return json("{" +
                "\"userId\": [\"id\", \"" + NAME_IDENTIFIER + "\", \"NameID\"]," +
                "\"email\": [\"email\", \"" + EMAIL_ADDRESS + "\", \"NameID\"]," +
                "\"firstName\": [\"firstName\", \"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname\"]," +
                "\"lastName\": [\"lastName\", \"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname\"]" +
                "}");
    }

    private static Map<String, List<String>> attrs(String... keyValues) {
        Map<String, List<String>> m = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put(keyValues[i], List.of(keyValues[i + 1]));
        }
        return m;
    }

    private static class Client {
        final String clientId;
        final MockSAML.KeyMaterial keyMaterial;

        Client(String clientId, MockSAML.KeyMaterial keyMaterial) {
            this.clientId = clientId;
            this.keyMaterial = keyMaterial;
        }
    }

    private static Client createClient(TestingProcessManager.TestingProcess process, JsonElement attributeMapping)
            throws Exception {
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();
        JsonObject created = putClient(process, clientInput(null, km, false, ABSENT, attributeMapping));
        assertEquals("OK", created.get("status").getAsString());
        return new Client(created.get("clientId").getAsString(), km);
    }

    // Runs an SP-initiated login and the callback, and returns the code handed to the app.
    private static String loginAndGetCode(TestingProcessManager.TestingProcess process, Client client,
                                          String nameId, Map<String, List<String>> attributes) throws Exception {
        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process, client.clientId, DEFAULT_REDIRECT_URI, ACS_URL, "test-state");
        String samlResponse = MockSAML.generateSignedSAMLResponseBase64(
                idpEntityId(client.keyMaterial), SAMLTestUtils.TEST_SP_ENTITY_ID, ACS_URL, nameId, attributes,
                SAMLTestUtils.requestIdFor(relayState), client.keyMaterial, 300);

        JsonObject callbackBody = new JsonObject();
        callbackBody.addProperty("samlResponse", samlResponse);
        callbackBody.addProperty("relayState", relayState);
        JsonObject callbackResp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback", callbackBody, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        assertEquals("OK", callbackResp.get("status").getAsString());

        String redirectURI = callbackResp.get("redirectURI").getAsString();
        String code = redirectURI.substring(redirectURI.indexOf("code=") + "code=".length());
        int amp = code.indexOf('&');
        if (amp != -1) {
            code = code.substring(0, amp);
        }
        return URLDecoder.decode(code, StandardCharsets.UTF_8);
    }

    private static JsonObject getUser(TestingProcessManager.TestingProcess process, Client client, String code)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("accessToken", code);
        body.addProperty("clientId", client.clientId);
        return HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/user", body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
    }

    // Stores claims for a code directly, as the callback would, so a test can use claim shapes a parsed assertion
    // cannot produce (OpenSAML trims the NameID on parse, so it never arrives blank or padded from a real one).
    private static JsonObject userInfoForClaims(TestingProcessManager.TestingProcess process, Client client,
                                                JsonObject claims) throws Exception {
        TenantIdentifier tenant = process.getAppForTesting();
        String code = java.util.UUID.randomUUID().toString();
        StorageUtils.getSAMLStorage(StorageLayer.getStorage(tenant, process.getProcess()))
                .saveSAMLClaims(tenant, client.clientId, code, claims, 60000);
        return getUser(process, client, code);
    }

    private static JsonObject claimsWithNameID(String nameId, String... keyValues) {
        JsonObject claims = new JsonObject();
        JsonArray nameIds = new JsonArray();
        nameIds.add(nameId);
        claims.add("NameID", nameIds);
        for (int i = 0; i < keyValues.length; i += 2) {
            claims.add(keyValues[i], list(keyValues[i + 1]));
        }
        return claims;
    }

    private static JsonObject userInfo(TestingProcessManager.TestingProcess process, Client client, String nameId,
                                       Map<String, List<String>> attributes) throws Exception {
        return getUser(process, client, loginAndGetCode(process, client, nameId, attributes));
    }

    private static JsonObject legacyUserInfo(TestingProcessManager.TestingProcess process, Client client,
                                             String nameId, Map<String, List<String>> attributes) throws Exception {
        String code = loginAndGetCode(process, client, nameId, attributes);
        JsonObject tokenForm = new JsonObject();
        tokenForm.addProperty("client_id", client.clientId);
        tokenForm.addProperty("client_secret", "secret");
        tokenForm.addProperty("code", code);
        JsonObject tokenResp = HttpRequestForTesting.sendFormDataPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/legacy/token", tokenForm, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        assertEquals("OK", tokenResp.get("status").getAsString());

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + tokenResp.get("access_token").getAsString());
        return HttpRequestForTesting.sendGETRequestWithHeaders(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/legacy/userinfo", null, headers, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
    }

    private static String stringOrNull(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    // ── API: create, echo, list, CDI ─────────────────────────────────────────

    @Test
    public void mappingIsStoredEchoedAndListed() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();

        JsonObject mapping = json("{\"userId\": [\"id\", \"NameID\"], \"email\": [\"mail\"]}");
        JsonObject created = putClient(process, clientInput(null, km, false, list(ACS_URL), mapping),
                SemVer.v5_8.get());
        assertEquals("OK", created.get("status").getAsString());
        assertEquals(mapping, created.get("attributeMapping"));
        assertEquals(list(ACS_URL), created.get("acsURLs"));

        JsonObject listed = getListedClient(process, created.get("clientId").getAsString());
        assertEquals(mapping, listed.get("attributeMapping"));
        assertEquals(list(ACS_URL), listed.get("acsURLs"));

        // A client without either field leaves both out.
        Client plain = createClient(process, ABSENT);
        JsonObject listedPlain = getListedClient(process, plain.clientId);
        assertFalse(listedPlain.has("attributeMapping"));
        assertFalse(listedPlain.has("acsURLs"));

        stop(process);
    }

    @Test
    public void cdi58IsAdvertisedAndAcceptedOnClientWrites() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();

        JsonObject apiVersion = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/apiversion", null, 1000, 1000, null, null, "");
        assertTrue(apiVersion.get("versions").getAsJsonArray().contains(new com.google.gson.JsonPrimitive("5.8")));

        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();
        JsonObject created = putClient(process,
                clientInput(null, km, false, ABSENT, json("{\"userId\": [\"id\"]}")), "5.8");
        assertEquals("OK", created.get("status").getAsString());
        assertEquals(json("{\"userId\": [\"id\"]}"), created.get("attributeMapping"));

        stop(process);
    }

    @Test
    public void invalidMappingsAreRejectedNamingTheKey() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();

        String tooLong = "a".repeat(513);
        String[][] cases = {
                {"\"x\"", "attributeMapping must be an object or null"},
                {"[]", "attributeMapping must be an object or null"},
                {"5", "attributeMapping must be an object or null"},
                {"{\"uid\": [\"id\"]}", "attributeMapping has an unknown key: uid"},
                {"{\"userId\": \"id\"}", "attributeMapping.userId must be an array of 1 to 10 claim names"},
                {"{\"userId\": null}", "attributeMapping.userId must be an array of 1 to 10 claim names"},
                {"{\"email\": []}", "attributeMapping.email must be an array of 1 to 10 claim names"},
                {"{\"firstName\": [\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\",\"10\",\"11\"]}",
                        "attributeMapping.firstName must be an array of 1 to 10 claim names"},
                {"{\"lastName\": [1]}", "attributeMapping.lastName must only contain strings"},
                {"{\"lastName\": [null]}", "attributeMapping.lastName must only contain strings"},
                {"{\"lastName\": [{}]}", "attributeMapping.lastName must only contain strings"},
                {"{\"userId\": [\"\"]}", "attributeMapping.userId must not contain empty claim names"},
                {"{\"userId\": [\"id\", \"  \"]}", "attributeMapping.userId must not contain empty claim names"},
                {"{\"userId\": [\" NameID\"]}",
                        "attributeMapping.userId must not contain claim names with leading or trailing whitespace"},
                {"{\"email\": [\"mail\", \"" + EMAIL_ADDRESS + "\\n\"]}",
                        "attributeMapping.email must not contain claim names with leading or trailing whitespace"},
                {"{\"firstName\": [\"\\tgivenName\"]}",
                        "attributeMapping.firstName must not contain claim names with leading or trailing " +
                                "whitespace"},
                {"{\"email\": [\"" + tooLong + "\"]}",
                        "attributeMapping.email must not contain claim names longer than 512 characters"},
        };
        for (String[] c : cases) {
            assertPutRejected(process, clientInput(null, km, false, ABSENT, JsonParser.parseString(c[0])),
                    BAD_REQUEST + c[1]);
        }

        // Nothing was stored by the rejected writes.
        JsonObject listResp = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients/list", null, 1000, 1000, null, SemVer.v5_4.get(),
                "saml");
        assertEquals(0, listResp.get("clients").getAsJsonArray().size());

        // The limits themselves are accepted.
        JsonObject atLimits = json("{\"userId\": [\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\",\"10\"]," +
                "\"email\": [\"" + "a".repeat(512) + "\"], \"firstName\": [\"given name\"]}");
        JsonObject created = putClient(process, clientInput(null, km, false, ABSENT, atLimits));
        assertEquals(atLimits, created.get("attributeMapping"));

        stop(process);
    }

    // ── required once set ───────────────────────────────────────────────────
    // From CDI 5.8 a set field must be sent on every update; below 5.8 leaving it out keeps the stored value.
    // The other cells of the table are the same on every version.

    @Test
    public void attributeMappingIsRequiredOnceSetFromCdi5_8() throws Exception {
        attributeMappingRequiredOnceSetTable(SemVer.v5_8.get());
    }

    @Test
    public void attributeMappingIsKeptWhenLeftOutBelowCdi5_8() throws Exception {
        attributeMappingRequiredOnceSetTable(SemVer.v5_4.get());
    }

    private void attributeMappingRequiredOnceSetTable(String cdi) throws Exception {
        boolean strict = new SemVer(cdi).greaterThanOrEqualTo(SemVer.v5_8);
        TestingProcessManager.TestingProcess process = startWithSAML();
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();
        JsonObject m1 = json("{\"userId\": [\"id\"]}");
        JsonObject m2 = json("{\"email\": [\"mail\", \"NameID\"]}");

        // Stored value: none (new client) — absent, null and {} all leave it unset; a value sets it.
        String clientId = putClient(process, clientInput(null, km, false, ABSENT, ABSENT), cdi)
                .get("clientId").getAsString();
        assertFalse(getListedClient(process, clientId).has("attributeMapping"));
        // Stored value: none (never set) — absent / null / {} stay unset.
        for (JsonElement unset : new JsonElement[]{ABSENT, JsonNull.INSTANCE, new JsonObject()}) {
            JsonObject resp = putClient(process, clientInput(clientId, km, false, ABSENT, unset), cdi);
            assertFalse(resp.has("attributeMapping"));
            assertFalse(getListedClient(process, clientId).has("attributeMapping"));
        }
        JsonObject newWithNull = putClient(process, clientInput(null,
                MockSAML.generateSelfSignedKeyMaterial(), false, ABSENT, JsonNull.INSTANCE), cdi);
        assertEquals("OK", newWithNull.get("status").getAsString());
        assertFalse(newWithNull.has("attributeMapping"));

        // Stored value: none — a value sets it.
        assertEquals(m1,
                putClient(process, clientInput(clientId, km, false, ABSENT, m1), cdi).get("attributeMapping"));

        // Stored value: set — absent is a 400 from 5.8 and keeps it below; either way the stored value stays.
        if (strict) {
            assertPutRejected(process, clientInput(clientId, km, false, ABSENT, ABSENT), MAPPING_SET_MESSAGE, cdi);
        } else {
            assertEquals(m1, putClient(process, clientInput(clientId, km, false, ABSENT, ABSENT), cdi)
                    .get("attributeMapping"));
        }
        assertEquals(m1, getListedClient(process, clientId).get("attributeMapping"));

        // Stored value: set — a value replaces it (as a whole).
        assertEquals(m2,
                putClient(process, clientInput(clientId, km, false, ABSENT, m2), cdi).get("attributeMapping"));
        assertEquals(m2, getListedClient(process, clientId).get("attributeMapping"));

        // Stored value: set — null clears it, and afterwards absent is fine again.
        assertFalse(putClient(process, clientInput(clientId, km, false, ABSENT, JsonNull.INSTANCE), cdi)
                .has("attributeMapping"));
        assertFalse(getListedClient(process, clientId).has("attributeMapping"));
        assertFalse(putClient(process, clientInput(clientId, km, false, ABSENT, ABSENT), cdi)
                .has("attributeMapping"));

        // Stored value: set — {} clears it too.
        putClient(process, clientInput(clientId, km, false, ABSENT, m1), cdi);
        assertFalse(putClient(process, clientInput(clientId, km, false, ABSENT, new JsonObject()), cdi)
                .has("attributeMapping"));
        assertFalse(getListedClient(process, clientId).has("attributeMapping"));

        stop(process);
    }

    @Test
    public void acsURLsIsRequiredOnceSetFromCdi5_8() throws Exception {
        acsURLsRequiredOnceSetTable(SemVer.v5_8.get());
    }

    @Test
    public void acsURLsIsKeptWhenLeftOutBelowCdi5_8() throws Exception {
        acsURLsRequiredOnceSetTable(SemVer.v5_4.get());
    }

    private void acsURLsRequiredOnceSetTable(String cdi) throws Exception {
        boolean strict = new SemVer(cdi).greaterThanOrEqualTo(SemVer.v5_8);
        TestingProcessManager.TestingProcess process = startWithSAML();
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();

        // Stored value: none — absent, null and [] all leave it unset.
        String clientId = putClient(process, clientInput(null, km, false, ABSENT, ABSENT), cdi)
                .get("clientId").getAsString();
        for (JsonElement unset : new JsonElement[]{ABSENT, JsonNull.INSTANCE, new JsonArray()}) {
            JsonObject resp = putClient(process, clientInput(clientId, km, false, unset, ABSENT), cdi);
            assertFalse(resp.has("acsURLs"));
            assertFalse(getListedClient(process, clientId).has("acsURLs"));
        }
        JsonObject newWithNull = putClient(process, clientInput(null,
                MockSAML.generateSelfSignedKeyMaterial(), false, JsonNull.INSTANCE, ABSENT), cdi);
        assertEquals("OK", newWithNull.get("status").getAsString());
        assertFalse(newWithNull.has("acsURLs"));

        // Stored value: none — a value sets it.
        assertEquals(list(ACS_URL),
                putClient(process, clientInput(clientId, km, false, list(ACS_URL), ABSENT), cdi).get("acsURLs"));

        // Stored value: set — absent used to clear the list. It is now a 400 from 5.8 and keeps the list below.
        if (strict) {
            assertPutRejected(process, clientInput(clientId, km, false, ABSENT, ABSENT), ACS_URLS_SET_MESSAGE, cdi);
        } else {
            assertEquals(list(ACS_URL),
                    putClient(process, clientInput(clientId, km, false, ABSENT, ABSENT), cdi).get("acsURLs"));
        }
        assertEquals(list(ACS_URL), getListedClient(process, clientId).get("acsURLs"));

        // Stored value: set — a value replaces it.
        String other = "http://localhost:3000/other-acs";
        assertEquals(list(other),
                putClient(process, clientInput(clientId, km, false, list(other), ABSENT), cdi).get("acsURLs"));

        // Stored value: set — null clears it.
        assertFalse(putClient(process, clientInput(clientId, km, false, JsonNull.INSTANCE, ABSENT), cdi)
                .has("acsURLs"));
        assertFalse(getListedClient(process, clientId).has("acsURLs"));

        // Stored value: set — [] clears it.
        putClient(process, clientInput(clientId, km, false, list(ACS_URL), ABSENT), cdi);
        assertFalse(putClient(process, clientInput(clientId, km, false, new JsonArray(), ABSENT), cdi)
                .has("acsURLs"));
        assertFalse(getListedClient(process, clientId).has("acsURLs"));

        stop(process);
    }

    @Test
    public void bothFieldsAreCheckedIndependentlyFromCdi5_8() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();
        JsonObject mapping = json("{\"userId\": [\"id\"]}");
        String cdi = SemVer.v5_8.get();

        String clientId = putClient(process, clientInput(null, km, false, list(ACS_URL), mapping), cdi)
                .get("clientId").getAsString();

        assertPutRejected(process, clientInput(clientId, km, false, list(ACS_URL), ABSENT), MAPPING_SET_MESSAGE,
                cdi);
        assertPutRejected(process, clientInput(clientId, km, false, ABSENT, mapping), ACS_URLS_SET_MESSAGE, cdi);

        JsonObject resent = putClient(process, clientInput(clientId, km, false, list(ACS_URL), mapping), cdi);
        assertEquals(mapping, resent.get("attributeMapping"));
        assertEquals(list(ACS_URL), resent.get("acsURLs"));

        stop(process);
    }

    /**
     * What an SDK on CDI 5.4 sends: neither key. Both stored values are kept, and the rest of the client is
     * updated.
     */
    @Test
    public void bothFieldsAreKeptWhenLeftOutBelowCdi5_8() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();
        JsonObject mapping = json("{\"userId\": [\"id\"]}");
        String cdi = SemVer.v5_4.get();

        String clientId = putClient(process, clientInput(null, km, false, list(ACS_URL), mapping), cdi)
                .get("clientId").getAsString();

        JsonObject sdkUpdate = clientInput(clientId, km, false, ABSENT, ABSENT);
        sdkUpdate.addProperty("enableRequestSigning", false);
        JsonObject updated = putClient(process, sdkUpdate, cdi);
        assertEquals(mapping, updated.get("attributeMapping"));
        assertEquals(list(ACS_URL), updated.get("acsURLs"));
        assertFalse(updated.get("enableRequestSigning").getAsBoolean());

        JsonObject listed = getListedClient(process, clientId);
        assertEquals(mapping, listed.get("attributeMapping"));
        assertEquals(list(ACS_URL), listed.get("acsURLs"));
        assertFalse(listed.get("enableRequestSigning").getAsBoolean());

        stop(process);
    }

    @Test
    public void requiredOnceSetRunsBeforeTheIdpInitiatedAcsCheckFromCdi5_8() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();
        String cdi = SemVer.v5_8.get();

        String clientId = putClient(process, clientInput(null, km, true, list(ACS_URL), ABSENT), cdi)
                .get("clientId").getAsString();

        // Leaving out a set acsURLs gets the required-once-set message ...
        assertPutRejected(process, clientInput(clientId, km, true, ABSENT, ABSENT), ACS_URLS_SET_MESSAGE, cdi);
        // ... while sending [] or null with IdP-initiated login on keeps today's message.
        assertPutRejected(process, clientInput(clientId, km, true, new JsonArray(), ABSENT), ACS_REQUIRED_MESSAGE,
                cdi);
        assertPutRejected(process, clientInput(clientId, km, true, JsonNull.INSTANCE, ABSENT), ACS_REQUIRED_MESSAGE,
                cdi);

        JsonObject listed = getListedClient(process, clientId);
        assertTrue(listed.get("allowIDPInitiatedLogin").getAsBoolean());
        assertEquals(list(ACS_URL), listed.get("acsURLs"));

        stop(process);
    }

    /**
     * Below 5.8 the stored acsURLs is carried forward before the IdP-initiated check, so an update that leaves it
     * out passes that check (it failed with ACS_REQUIRED from 12.3.1 to 12.4).
     */
    @Test
    public void leftOutAcsURLsIsKeptBeforeTheIdpInitiatedAcsCheckBelowCdi5_8() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();
        String cdi = SemVer.v5_4.get();

        String clientId = putClient(process, clientInput(null, km, true, list(ACS_URL), ABSENT), cdi)
                .get("clientId").getAsString();

        JsonObject updated = putClient(process, clientInput(clientId, km, true, ABSENT, ABSENT), cdi);
        assertTrue(updated.get("allowIDPInitiatedLogin").getAsBoolean());
        assertEquals(list(ACS_URL), updated.get("acsURLs"));

        // Sending [] or null with IdP-initiated login on still clears the list, so it is still refused.
        assertPutRejected(process, clientInput(clientId, km, true, new JsonArray(), ABSENT), ACS_REQUIRED_MESSAGE,
                cdi);
        assertPutRejected(process, clientInput(clientId, km, true, JsonNull.INSTANCE, ABSENT), ACS_REQUIRED_MESSAGE,
                cdi);

        JsonObject listed = getListedClient(process, clientId);
        assertTrue(listed.get("allowIDPInitiatedLogin").getAsBoolean());
        assertEquals(list(ACS_URL), listed.get("acsURLs"));

        stop(process);
    }

    /**
     * A 12.3.1+ core stored `acsURLs: []` as `[]`. Such a row counts as unset: it is left out of responses, an
     * update may leave the key out, and SP-initiated logins (which failed with INVALID_CLIENT against the empty
     * allow-list) now work.
     */
    @Test
    public void storedEmptyAcsURLsCountsAsUnset() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        MockSAML.KeyMaterial km = MockSAML.generateSelfSignedKeyMaterial();

        String clientId = putClient(process, clientInput(null, km, false, ABSENT, ABSENT))
                .get("clientId").getAsString();

        // Write the row as an earlier core would have.
        TenantIdentifier tenant = process.getAppForTesting();
        SQLStorage storage = (SQLStorage) StorageLayer.getStorage(tenant, process.getProcess());
        storage.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try (PreparedStatement pst = sqlCon.prepareStatement(
                    "UPDATE saml_clients SET acs_urls = '[]' WHERE app_id = ? AND tenant_id = ? AND client_id = ?")) {
                pst.setString(1, tenant.getAppId());
                pst.setString(2, tenant.getTenantId());
                pst.setString(3, clientId);
                assertEquals(1, pst.executeUpdate());
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            }
            storage.commitTransaction(con);
            return null;
        });

        assertFalse(getListedClient(process, clientId).has("acsURLs"));

        // An SP-initiated authorize for it succeeds.
        SAMLTestUtils.LoginInfo login = SAMLTestUtils.createLoginRequestAndGetLoginInfo(
                process, clientId, DEFAULT_REDIRECT_URI, ACS_URL, "state");
        assertNotNull(login.relayState);

        // An update may leave acsURLs out.
        JsonObject updated = putClient(process, clientInput(clientId, km, false, ABSENT, ABSENT));
        assertEquals("OK", updated.get("status").getAsString());
        assertFalse(updated.has("acsURLs"));

        stop(process);
    }

    // ── userinfo resolution ─────────────────────────────────────────────────

    @Test
    public void mappedFieldsAreUsedBySubAndLegacyId() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        Client client = createClient(process,
                json("{\"userId\": [\"employeeId\"], \"email\": [\"workEmail\", \"NameID\"]}"));
        Map<String, List<String>> a = attrs("employeeId", "E-42", "workEmail", "work@example.com",
                EMAIL_ADDRESS, "default@example.com");

        JsonObject user = userInfo(process, client, "nameid@example.com", a);
        assertEquals("E-42", user.get("sub").getAsString());
        assertEquals("work@example.com", user.get("email").getAsString());
        assertFalse(user.has("id"));

        JsonObject legacy = legacyUserInfo(process, client, "nameid@example.com", a);
        assertEquals("E-42", legacy.get("id").getAsString());
        assertEquals("work@example.com", legacy.get("email").getAsString());
        assertFalse(legacy.has("sub"));

        // Fields the mapping leaves out use the defaults; an unresolved field is null as today.
        Client userIdOnly = createClient(process, json("{\"userId\": [\"employeeId\"]}"));
        JsonObject u2 = userInfo(process, userIdOnly, "nameid@example.com", attrs("employeeId", "E-7"));
        assertEquals("E-7", u2.get("sub").getAsString());
        assertEquals("nameid@example.com", u2.get("email").getAsString());

        JsonObject u3 = userInfo(process, userIdOnly, "nameid@example.com", attrs("other", "x"));
        assertTrue(u3.get("sub").isJsonNull());

        stop(process);
    }

    @Test
    public void namesAreReturnedOnlyWhenMappedAndFound() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        Map<String, List<String>> a = attrs("givenName", "Ada", "sn", "Lovelace", "firstName", "A",
                "lastName", "L");

        Client firstOnly = createClient(process, json("{\"firstName\": [\"givenName\"]}"));
        JsonObject u1 = userInfo(process, firstOnly, "user@example.com", a);
        assertEquals("Ada", u1.get("firstName").getAsString());
        assertFalse(u1.has("lastName"));

        Client both = createClient(process, json("{\"firstName\": [\"givenName\"], \"lastName\": [\"missing\"]}"));
        JsonObject u2 = userInfo(process, both, "user@example.com", a);
        assertEquals("Ada", u2.get("firstName").getAsString());
        assertFalse("a mapped but missing name is left out", u2.has("lastName"));

        Client noMapping = createClient(process, ABSENT);
        JsonObject u3 = userInfo(process, noMapping, "user@example.com", a);
        assertFalse(u3.has("firstName"));
        assertFalse(u3.has("lastName"));

        stop(process);
    }

    @Test
    public void polisPresetMatchesPolisResolution() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        Client client = createClient(process, polisPreset());

        // An `id` attribute plus an email NameID gives the `id` value.
        JsonObject u1 = userInfo(process, client, "okta.user@example.com", attrs("id", "00u1abcd"));
        assertEquals("00u1abcd", u1.get("sub").getAsString());
        assertEquals("okta.user@example.com", u1.get("email").getAsString());

        // Only a nameidentifier attribute gives that.
        JsonObject u2 = userInfo(process, client, "   ", attrs(NAME_IDENTIFIER, "nid-123"));
        assertEquals("nid-123", u2.get("sub").getAsString());

        // Only a NameID gives the NameID.
        JsonObject u3 = userInfo(process, client, "persistent-id-9", null);
        assertEquals("persistent-id-9", u3.get("sub").getAsString());
        assertTrue("a NameID without @ is not an email", u3.get("email").isJsonNull());

        // `email` and `emailaddress` together give `email`; names come from the preset.
        JsonObject u4 = userInfo(process, client, "x", attrs("email", "first@example.com",
                EMAIL_ADDRESS, "second@example.com", "firstName", "Grace",
                "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname", "Hopper"));
        assertEquals("first@example.com", u4.get("email").getAsString());
        assertEquals("Grace", u4.get("firstName").getAsString());
        assertEquals("Hopper", u4.get("lastName").getAsString());

        stop(process);
    }

    @Test
    public void blankNameIDFallsThroughForClientsWithAMapping() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        Client client = createClient(process, json("{\"userId\": [\"NameID\", \"uid\"], " +
                "\"email\": [\"NameID\", \"mail\"]}"));

        // Empty, whitespace-only and null NameID fall through to the next candidate.
        for (String blank : new String[]{"", "   ", null}) {
            JsonObject u = userInfoForClaims(process, client, claimsWithNameID(blank, "uid", "u-1",
                    "mail", "mail@example.com"));
            String label = "NameID " + (blank == null ? "null" : "'" + blank + "'");
            assertEquals(label, "u-1", u.get("sub").getAsString());
            assertEquals(label, "mail@example.com", u.get("email").getAsString());
        }

        // A NameID with surrounding spaces is returned untrimmed.
        JsonObject padded = userInfoForClaims(process, client, claimsWithNameID("  padded@example.com  ",
                "uid", "u-1", "mail", "mail@example.com"));
        assertEquals("  padded@example.com  ", padded.get("sub").getAsString());
        assertEquals("  padded@example.com  ", padded.get("email").getAsString());

        // Through a real assertion: a NameID without a value (which fails today's built-in chain) falls through.
        JsonObject parsed = userInfo(process, client, null, attrs("uid", "u-2", "mail", "m2@example.com"));
        assertEquals("u-2", parsed.get("sub").getAsString());
        assertEquals("m2@example.com", parsed.get("email").getAsString());

        stop(process);
    }

    @Test
    public void clientWithoutMappingKeepsTheBuiltInOutput() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        Client client = createClient(process, ABSENT);

        // Only the objectidentifier claim (no NameID at all).
        JsonObject u1 = userInfo(process, client, MockSAML.NO_NAME_ID, attrs(OBJECT_ID, "oid-1"));
        // Only the name claim.
        JsonObject u2 = userInfo(process, client, MockSAML.NO_NAME_ID, attrs(NAME_CLAIM, "name-1"));
        // NameID plus the default email claims.
        JsonObject u3 = userInfo(process, client, "nid@example.com", attrs("mail", "m@example.com",
                "email", "e@example.com"));
        JsonObject u4 = userInfo(process, client, "nid@example.com", null);
        // Blank and padded NameIDs (stored claims): today's chain returns them as they are.
        JsonObject u5 = userInfoForClaims(process, client, claimsWithNameID("", OBJECT_ID, "oid-2"));
        JsonObject u6 = userInfoForClaims(process, client, claimsWithNameID("   ", OBJECT_ID, "oid-2",
                "mail", "m@example.com"));
        JsonObject u7 = userInfoForClaims(process, client, claimsWithNameID(" padded@example.com "));

        for (JsonObject u : new JsonObject[]{u1, u2, u3, u4, u5, u6, u7}) {
            assertEquals(builtInExpected(u.getAsJsonObject("claims"), client.clientId), u);
        }
        assertEquals("oid-1", u1.get("sub").getAsString());
        assertEquals("name-1", u2.get("sub").getAsString());
        assertEquals("nid@example.com", u3.get("sub").getAsString());
        assertEquals("m@example.com", u3.get("email").getAsString());
        assertEquals("", u5.get("sub").getAsString());
        assertEquals("   ", u6.get("sub").getAsString());
        assertEquals(" padded@example.com ", u7.get("sub").getAsString());
        assertEquals(" padded@example.com ", u7.get("email").getAsString());

        stop(process);
    }

    @Test
    public void clientWithoutMappingAndNoNameIDValueStillFails() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        Client client = createClient(process, ABSENT);

        // A NameID element without a value: today's built-in chain fails the request, and it still does. The
        // comparison logs it as blank-nameid.
        String code = loginAndGetCode(process, client, null, attrs(OBJECT_ID, "oid-3"));
        ProcessState.getInstance(process.getProcess()).clear();
        try {
            getUser(process, client, code);
            fail("expected today's 500 for a NameID without a value");
        } catch (HttpResponseException e) {
            assertEquals(500, e.statusCode);
        }
        List<JsonObject> logged = mismatchEvents(process);
        assertEquals(1, logged.size());
        assertEquals("userId", logged.get(0).get("field").getAsString());
        assertEquals("blank-nameid", logged.get(0).get("kind").getAsString());
        assertFalse(logged.get(0).get("message").getAsString().contains("oid-3"));

        // The same assertion for a client with a mapping falls through to the next candidate.
        Client mapped = createClient(process, json("{\"email\": [\"mail\"]}"));
        JsonObject u = userInfo(process, mapped, null, attrs(OBJECT_ID, "oid-3"));
        assertEquals("oid-3", u.get("sub").getAsString());

        stop(process);
    }

    @Test
    public void mappingIsReadWhenUserinfoIsBuilt() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        Client client = createClient(process, ABSENT);
        Map<String, List<String>> a = attrs("id", "polis-id");

        String code = loginAndGetCode(process, client, "nid@example.com", a);
        putClient(process, clientInput(client.clientId, client.keyMaterial, false, ABSENT,
                json("{\"userId\": [\"id\"]}")));
        assertEquals("polis-id", getUser(process, client, code).get("sub").getAsString());

        stop(process);
    }

    @Test
    public void clientRemovedBeforeUserinfoGetsTheBuiltInOutput() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        Client client = createClient(process, json("{\"userId\": [\"id\"]}"));

        String code = loginAndGetCode(process, client, "nid@example.com", attrs("id", "polis-id"));

        JsonObject removeBody = new JsonObject();
        removeBody.addProperty("clientId", client.clientId);
        JsonObject removed = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/saml/clients/remove", removeBody, 1000, 1000, null,
                SemVer.v5_4.get(), "saml");
        assertTrue(removed.get("didExist").getAsBoolean());

        JsonObject u = getUser(process, client, code);
        assertEquals(builtInExpected(u.getAsJsonObject("claims"), client.clientId), u);
        assertEquals("nid@example.com", u.get("sub").getAsString());

        stop(process);
    }

    // ── the comparison log for clients without a mapping ────────────────────

    @Test
    public void comparisonLogsBlankNameIDDifferenceWithoutClaimValues() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        Client client = createClient(process, ABSENT);

        ProcessState.getInstance(process.getProcess()).clear();
        JsonObject u = userInfoForClaims(process, client, claimsWithNameID("   ", OBJECT_ID, "secret-oid",
                "mail", "secret@example.com"));
        // The response is still the built-in chain's.
        assertEquals("   ", u.get("sub").getAsString());
        assertEquals("secret@example.com", u.get("email").getAsString());

        List<JsonObject> logged = mismatchEvents(process);
        assertEquals(1, logged.size());
        JsonObject line = logged.get(0);
        assertEquals("userId", line.get("field").getAsString());
        assertEquals("blank-nameid", line.get("kind").getAsString());
        String message = line.get("message").getAsString();
        assertTrue(message, message.contains(client.clientId));
        assertTrue(message, message.contains("appId=" + process.getAppForTesting().getAppId()));
        assertTrue(message, message.contains("tenantId=" + process.getAppForTesting().getTenantId()));
        assertTrue(message, message.contains("field=userId"));
        assertTrue(message, message.contains("kind=blank-nameid"));
        assertFalse(message, message.contains("secret"));

        stop(process);
    }

    @Test
    public void comparisonLogsNothingWhenOutputsAgree() throws Exception {
        TestingProcessManager.TestingProcess process = startWithSAML();
        Client client = createClient(process, ABSENT);

        ProcessState.getInstance(process.getProcess()).clear();
        userInfo(process, client, "user@example.com", attrs(OBJECT_ID, "oid"));
        userInfo(process, client, MockSAML.NO_NAME_ID, attrs(NAME_CLAIM, "n", EMAIL_ADDRESS, "e@example.com"));
        assertEquals(0, mismatchEvents(process).size());

        // A client with a mapping is never compared.
        Client mapped = createClient(process, json("{\"userId\": [\"NameID\", \"uid\"]}"));
        userInfoForClaims(process, mapped, claimsWithNameID("   ", "uid", "u"));
        assertEquals(0, mismatchEvents(process).size());

        stop(process);
    }

    private static List<JsonObject> mismatchEvents(TestingProcessManager.TestingProcess process) {
        List<JsonObject> out = new java.util.ArrayList<>();
        for (ProcessState.EventAndException e : ProcessState.getInstance(process.getProcess())
                .getEventsByName(ProcessState.PROCESS_STATE.SAML_ATTRIBUTE_MAPPING_COMPARISON_MISMATCH)) {
            out.add(e.data);
        }
        return out;
    }

    // Today's built-in rules, written out independently of the core's implementation.
    private static JsonObject builtInExpected(JsonObject claims, String clientId) {
        String sub = null;
        if (claims.has("NameID")) {
            sub = claims.getAsJsonArray("NameID").get(0).getAsString();
        } else if (claims.has(OBJECT_ID)) {
            sub = claims.getAsJsonArray(OBJECT_ID).get(0).getAsString();
        } else if (claims.has(NAME_CLAIM)) {
            sub = claims.getAsJsonArray(NAME_CLAIM).get(0).getAsString();
        }
        String email = null;
        if (claims.has(EMAIL_ADDRESS)) {
            email = claims.getAsJsonArray(EMAIL_ADDRESS).get(0).getAsString();
        } else if (claims.has("mail")) {
            email = claims.getAsJsonArray("mail").get(0).getAsString();
        } else if (claims.has("email")) {
            email = claims.getAsJsonArray("email").get(0).getAsString();
        } else if (claims.has("NameID") && claims.getAsJsonArray("NameID").get(0).getAsString().contains("@")) {
            email = claims.getAsJsonArray("NameID").get(0).getAsString();
        }
        JsonObject expected = new JsonObject();
        expected.addProperty("status", "OK");
        expected.add("claims", claims);
        expected.addProperty("sub", sub);
        expected.addProperty("email", email);
        expected.addProperty("aud", clientId);
        return expected;
    }
}
