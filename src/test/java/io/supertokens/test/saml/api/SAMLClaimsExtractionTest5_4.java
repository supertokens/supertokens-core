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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Regression tests for two bugs fixed in SAML.java:
 *
 * Bug 1 — XSString attributes silently dropped:
 *   OpenSAML 4 materialises xsi:type="xsd:string" AttributeValue nodes as XSStringImpl,
 *   which does NOT implement the AttributeValue marker interface. The old code's instanceof
 *   check skipped every such value, so claims sent by AD FS, Azure AD, and most enterprise
 *   IdPs never reached the application. MockSAML already builds attributes via XSStringBuilder,
 *   making these tests a direct regression suite for the fix.
 *
 * Bug 2 — Email only read from WS-Federation URI claim:
 *   Many IdPs send the email address under the LDAP friendly name "mail" or the OIDC-style
 *   "email" attribute, not the WS-Fed URI. Also added a NameID-as-email fallback when the
 *   NameID contains "@" (emailAddress NameIDFormat).
 */
public class SAMLClaimsExtractionTest5_4 {

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
        SAMLTestUtils.setSAMLSPEntityIdConfig();
    }

    // ── shared constants ──────────────────────────────────────────────────────

    private static final String DEFAULT_REDIRECT_URI = "http://localhost:3000/auth/callback/saml-mock";
    private static final String ACS_URL              = "http://localhost:3000/acs";
    private static final String IDP_ENTITY_ID        = "https://saml.example.com/entityid";
    private static final String IDP_SSO_URL          = "https://mocksaml.com/api/saml/sso";

    // ── test helpers ─────────────────────────────────────────────────────────

    private String doFullSAMLFlow(
            TestingProcessManager.TestingProcess process,
            SAMLTestUtils.CreatedClientInfo clientInfo,
            Map<String, List<String>> attributes,
            String nameId) throws Exception {

        String relayState = SAMLTestUtils.createLoginRequestAndGetRelayState(
                process, clientInfo.clientId, clientInfo.defaultRedirectURI,
                clientInfo.acsURL, "test-state");

        String samlResponseBase64 = MockSAML.generateSignedSAMLResponseBase64(
                clientInfo.idpEntityId,
                "https://saml.supertokens.com",
                clientInfo.acsURL,
                nameId,
                attributes,
                SAMLTestUtils.requestIdFor(relayState),
                clientInfo.keyMaterial,
                300);

        JsonObject callbackBody = new JsonObject();
        callbackBody.addProperty("samlResponse", samlResponseBase64);
        callbackBody.addProperty("relayState", relayState);

        JsonObject callbackResp = HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(), "",
                "http://localhost:3567/recipe/saml/callback",
                callbackBody, 1000, 1000, null, SemVer.v5_4.get(), "saml");

        assertEquals("OK", callbackResp.get("status").getAsString());

        String redirectURI = callbackResp.get("redirectURI").getAsString();
        int codeIdx = redirectURI.indexOf("code=");
        String code = redirectURI.substring(codeIdx + "code=".length());
        int amp = code.indexOf('&');
        if (amp != -1) {
            code = code.substring(0, amp);
        }
        return URLDecoder.decode(code, StandardCharsets.UTF_8);
    }

    private JsonObject getUserInfo(
            TestingProcessManager.TestingProcess process,
            String accessToken,
            String clientId) throws Exception {

        JsonObject body = new JsonObject();
        body.addProperty("accessToken", accessToken);
        body.addProperty("clientId", clientId);
        return HttpRequestForTesting.sendJsonPOSTRequest(
                process.getProcess(), "",
                "http://localhost:3567/recipe/saml/user",
                body, 1000, 1000, null, SemVer.v5_4.get(), "saml");
    }

    // ── Bug 1: XSString attribute extraction ─────────────────────────────────

    /**
     * MockSAML builds AttributeValue nodes using XSStringBuilder (xsi:type="xsd:string"),
     * which produces XSStringImpl — the exact type that the old instanceof-AttributeValue
     * check silently discarded. Verifies that string-typed attributes now appear in claims.
     */
    @Test
    public void testXSStringAttributesAreExtractedIntoClaims() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                        new EE_FEATURES[]{EE_FEATURES.SAML});

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL);

        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("department", List.of("engineering"));
        attrs.put("role", List.of("developer"));

        String accessToken = doFullSAMLFlow(process, clientInfo, attrs, "user-id-001");
        JsonObject info = getUserInfo(process, accessToken, clientInfo.clientId);

        assertEquals("OK", info.get("status").getAsString());
        JsonObject claims = info.getAsJsonObject("claims");

        assertTrue("claims must contain 'department'", claims.has("department"));
        assertEquals("engineering", claims.getAsJsonArray("department").get(0).getAsString());

        assertTrue("claims must contain 'role'", claims.has("role"));
        assertEquals("developer", claims.getAsJsonArray("role").get(0).getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * A multi-valued XSString attribute (e.g. group memberships) must be fully preserved —
     * all values should appear in the claims array, not just the first one.
     */
    @Test
    public void testMultiValuedXSStringAttributeIsFullyExtracted() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                        new EE_FEATURES[]{EE_FEATURES.SAML});

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL);

        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("groups", List.of("admins", "developers", "qa"));

        String accessToken = doFullSAMLFlow(process, clientInfo, attrs, "user-id-002");
        JsonObject info = getUserInfo(process, accessToken, clientInfo.clientId);

        assertEquals("OK", info.get("status").getAsString());
        JsonObject claims = info.getAsJsonObject("claims");

        assertTrue("claims must contain 'groups'", claims.has("groups"));
        assertEquals("all three group values must be present",
                3, claims.getAsJsonArray("groups").size());
        assertEquals("admins",     claims.getAsJsonArray("groups").get(0).getAsString());
        assertEquals("developers", claims.getAsJsonArray("groups").get(1).getAsString());
        assertEquals("qa",         claims.getAsJsonArray("groups").get(2).getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // ── Bug 2: email attribute fallback chain ─────────────────────────────────

    /**
     * Regression: email sent as the WS-Federation URI claim (the only case that worked before)
     * must still be picked up correctly.
     */
    @Test
    public void testEmailExtractedViaWsFedUriClaim() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                        new EE_FEATURES[]{EE_FEATURES.SAML});

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL);

        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress",
                List.of("user@wsfed.com"));

        String accessToken = doFullSAMLFlow(process, clientInfo, attrs, "some-user-id");
        JsonObject info = getUserInfo(process, accessToken, clientInfo.clientId);

        assertEquals("OK", info.get("status").getAsString());
        assertEquals("user@wsfed.com", info.get("email").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * AD FS and Active Directory send email as the LDAP friendly name "mail". This was
     * silently ignored before the fix, leaving the email field null.
     */
    @Test
    public void testEmailExtractedViaMailAttribute() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                        new EE_FEATURES[]{EE_FEATURES.SAML});

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL);

        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("mail", List.of("user@adfs-company.com"));

        String accessToken = doFullSAMLFlow(process, clientInfo, attrs, "some-user-id");
        JsonObject info = getUserInfo(process, accessToken, clientInfo.clientId);

        assertEquals("OK", info.get("status").getAsString());
        assertEquals("user@adfs-company.com", info.get("email").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * OIDC-bridge and SCIM-style IdPs use the plain "email" attribute name.
     */
    @Test
    public void testEmailExtractedViaEmailAttribute() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                        new EE_FEATURES[]{EE_FEATURES.SAML});

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL);

        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("email", List.of("user@oidc-idp.com"));

        String accessToken = doFullSAMLFlow(process, clientInfo, attrs, "some-user-id");
        JsonObject info = getUserInfo(process, accessToken, clientInfo.clientId);

        assertEquals("OK", info.get("status").getAsString());
        assertEquals("user@oidc-idp.com", info.get("email").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * When no email attribute is sent at all but the NameID looks like an email address
     * (it contains "@"), it should be used as the email fallback of last resort.
     */
    @Test
    public void testEmailExtractedViaNameIdWhenItContainsAtSign() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                        new EE_FEATURES[]{EE_FEATURES.SAML});

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL);

        // No email attributes — NameID is the email address (emailAddress NameIDFormat)
        String accessToken = doFullSAMLFlow(process, clientInfo, null, "user@nameid-email.com");
        JsonObject info = getUserInfo(process, accessToken, clientInfo.clientId);

        assertEquals("OK", info.get("status").getAsString());
        assertEquals("user@nameid-email.com", info.get("email").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * When the NameID is an opaque identifier (no "@") and no email attribute is present,
     * the email field must remain null rather than using the NameID as email.
     */
    @Test
    public void testEmailRemainsNullWhenNameIdIsNotAnEmailAddress() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                        new EE_FEATURES[]{EE_FEATURES.SAML});

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL);

        // Opaque NameID (transient / persistent format) — must not be misused as email
        String accessToken = doFullSAMLFlow(process, clientInfo, null, "opaque-user-id-12345");
        JsonObject info = getUserInfo(process, accessToken, clientInfo.clientId);

        assertEquals("OK", info.get("status").getAsString());
        assertTrue("email must be null when NameID contains no '@'",
                info.get("email").isJsonNull());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Priority: WS-Fed URI > mail > email > NameID.
     * When all are present, the WS-Fed URI claim must win.
     */
    @Test
    public void testEmailPriorityWsFedUriOverMailAndEmail() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                        new EE_FEATURES[]{EE_FEATURES.SAML});

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL);

        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress",
                List.of("primary@wsfed.com"));
        attrs.put("mail",  List.of("secondary@mail.com"));
        attrs.put("email", List.of("tertiary@email.com"));

        String accessToken = doFullSAMLFlow(process, clientInfo, attrs, "user@nameid.com");
        JsonObject info = getUserInfo(process, accessToken, clientInfo.clientId);

        assertEquals("OK", info.get("status").getAsString());
        assertEquals("WS-Fed URI claim must take precedence",
                "primary@wsfed.com", info.get("email").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Priority: mail > email > NameID.
     * When "mail" and "email" are both present (but no WS-Fed URI), "mail" must win.
     */
    @Test
    public void testEmailPriorityMailOverEmailAndNameId() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                        new EE_FEATURES[]{EE_FEATURES.SAML});

        SAMLTestUtils.CreatedClientInfo clientInfo = SAMLTestUtils.createClientWithGeneratedMetadata(
                process, DEFAULT_REDIRECT_URI, ACS_URL, IDP_ENTITY_ID, IDP_SSO_URL);

        Map<String, List<String>> attrs = new HashMap<>();
        attrs.put("mail",  List.of("user@ldap.com"));
        attrs.put("email", List.of("user@oidc.com"));

        String accessToken = doFullSAMLFlow(process, clientInfo, attrs, "user@nameid.com");
        JsonObject info = getUserInfo(process, accessToken, clientInfo.clientId);

        assertEquals("OK", info.get("status").getAsString());
        assertEquals("mail claim must take precedence over email and NameID",
                "user@ldap.com", info.get("email").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
