/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.oauth.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.totp.TotpLicenseTest;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.Random;

import static org.junit.Assert.*;

public class TestClientCreate5_2 {
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
        OAuthAPIHelper.resetOAuthProvider();
    }

    @Test
    public void testInvalidInputs() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.getProcess())
                .setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_MFA_FEATURE);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});

        {
            // Duplicate client id
            String clientId = "test-client-id-" + new Random().nextInt(100000);
            JsonObject createBody = new JsonObject();
            createBody.addProperty("clientId", clientId);

            JsonObject resp = createClient(process.getProcess(), createBody);
            assertEquals("OK", resp.get("status").getAsString());

            resp = createClient(process.getProcess(), createBody);
            assertEquals("OAUTH_ERROR", resp.get("status").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDefaultClientIdGeneration() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.getProcess())
                .setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_MFA_FEATURE);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});

        JsonObject createBody = new JsonObject();

        JsonObject resp = createClient(process.getProcess(), createBody);
        assertEquals("OK", resp.get("status").getAsString());
        resp.remove("status");
        verifyStructure(resp);

        assertTrue(resp.get("clientId").getAsString().startsWith("stcl_"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAllFields() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlag.getInstance(process.getProcess())
                .setLicenseKeyAndSyncFeatures(TotpLicenseTest.OPAQUE_KEY_WITH_MFA_FEATURE);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.OAUTH});

        String[] FIELDS = new String[]{
                "clientId",
                "clientSecret",
                "clientName",
                "scope",
                "redirectUris",
                "enableRefreshTokenRotation",
                "authorizationCodeGrantAccessTokenLifespan",
                "authorizationCodeGrantIdTokenLifespan",
                "authorizationCodeGrantRefreshTokenLifespan",
                "clientCredentialsGrantAccessTokenLifespan",
                "implicitGrantAccessTokenLifespan",
                "implicitGrantIdTokenLifespan",
                "refreshTokenGrantAccessTokenLifespan",
                "refreshTokenGrantIdTokenLifespan",
                "refreshTokenGrantRefreshTokenLifespan",
                "tokenEndpointAuthMethod",
                "clientUri",
                "allowedCorsOrigins",
                "audience",
                "grantTypes",
                "responseTypes",
                "logoUri",
                "policyUri",
                "tosUri",
                "metadata",
                "postLogoutRedirectUris"
        };

        JsonArray redirectUris = new JsonArray();
        redirectUris.add(new JsonPrimitive("http://localhost:3000/auth"));

        JsonArray allowedCorsOrigins = new JsonArray();
        allowedCorsOrigins.add(new JsonPrimitive("http://localhost:3000"));

        JsonArray audience = new JsonArray();
        audience.add(new JsonPrimitive("https://api.example.com"));

        JsonArray grantTypes = new JsonArray();
        grantTypes.add(new JsonPrimitive("authorization_code"));
        grantTypes.add(new JsonPrimitive("implicit"));
        grantTypes.add(new JsonPrimitive("refresh_token"));
        grantTypes.add(new JsonPrimitive("client_credentials"));

        JsonArray responseTypes = new JsonArray();
        responseTypes.add(new JsonPrimitive("code"));
        responseTypes.add(new JsonPrimitive("token"));
        responseTypes.add(new JsonPrimitive("code token"));
        responseTypes.add(new JsonPrimitive("id_token"));
        responseTypes.add(new JsonPrimitive("code id_token"));
        responseTypes.add(new JsonPrimitive("token id_token"));
        responseTypes.add(new JsonPrimitive("code token id_token"));

        JsonArray postRedirectLogoutUris = new JsonArray();
        postRedirectLogoutUris.add(new JsonPrimitive("http://localhost:3000/logout"));

        JsonObject metadata = new JsonObject();
        metadata.addProperty("sub", "test-client-id");
        metadata.addProperty("iss", "http://localhost:3000");
        metadata.addProperty("aud", "https://api.example.com");
        metadata.addProperty("exp", 1714857600);
        metadata.addProperty("iat", 1714857600);
        metadata.addProperty("jti", "test-jti");
        metadata.addProperty("nonce", "test-nonce");

        JsonElement[] VALUES = new JsonElement[]{
            new JsonPrimitive("test-client-id--" + new Random().nextInt(100000)), // clientId
            new JsonPrimitive("kEdQVPNLsl_FHOFBO_nWnj7P3."), // clientSecret
            new JsonPrimitive("Test Client"), // clientName
            new JsonPrimitive("offline_access offline openid"), // scope
            redirectUris, // redirectUris
            new JsonPrimitive(true), // enableRefreshTokenRotation
            new JsonPrimitive("1h0m0s"), // authorizationCodeGrantAccessTokenLifespan
            new JsonPrimitive("2h0m0s"), // authorizationCodeGrantIdTokenLifespan
            new JsonPrimitive("3h0m0s"), // authorizationCodeGrantRefreshTokenLifespan
            new JsonPrimitive("4h0m0s"), // clientCredentialsGrantAccessTokenLifespan
            new JsonPrimitive("5h0m0s"), // implicitGrantAccessTokenLifespan
            new JsonPrimitive("6h0m0s"), // implicitGrantIdTokenLifespan
            new JsonPrimitive("7h0m0s"), // refreshTokenGrantAccessTokenLifespan
            new JsonPrimitive("8h0m0s"), // refreshTokenGrantIdTokenLifespan
            new JsonPrimitive("9h0m0s"), // refreshTokenGrantRefreshTokenLifespan
            new JsonPrimitive("client_secret_post"), // tokenEndpointAuthMethod
            new JsonPrimitive("http://localhost:3000"), // clientUri
            allowedCorsOrigins, // allowedCorsOrigins
            audience, // audience
            grantTypes, // grantTypes
            responseTypes, // responseTypes
            new JsonPrimitive("http://localhost:3000/logo.png"), // logoUri
            new JsonPrimitive("http://localhost:3000/policy.html"), // policyUri
            new JsonPrimitive("http://localhost:3000/tos.html"), // tosUri
            metadata, // metadata
            postRedirectLogoutUris // postRedirectLogoutUris
        };

        for (int i = 0; i < FIELDS.length; i++) {
            JsonObject createBody = new JsonObject();
            createBody.add(FIELDS[i], VALUES[i]);

            if ("postLogoutRedirectUris".equals(FIELDS[i])) {
                createBody.add("redirectUris", redirectUris);
            }

            JsonObject resp = createClient(process.getProcess(), createBody);
            assertEquals("Unable to create client with field: " + FIELDS[i], "OK", resp.get("status").getAsString());
            assertEquals("Value mismatch for field: " + FIELDS[i], VALUES[i], resp.get(FIELDS[i]));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private JsonObject createClient(Main main, JsonObject createClientBody) throws Exception {
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/oauth/clients", createClientBody, 1000, 1000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    private void verifyStructure(JsonObject response) throws Exception {
        assertEquals(27, response.entrySet().size());
        String[] FIELDS = new String[]{
                "clientId",
                "clientSecret",
                "clientName",
                "scope",
                "redirectUris",
                "enableRefreshTokenRotation",
                "authorizationCodeGrantAccessTokenLifespan",
                "authorizationCodeGrantIdTokenLifespan",
                "authorizationCodeGrantRefreshTokenLifespan",
                "clientCredentialsGrantAccessTokenLifespan",
                "implicitGrantAccessTokenLifespan",
                "implicitGrantIdTokenLifespan",
                "refreshTokenGrantAccessTokenLifespan",
                "refreshTokenGrantIdTokenLifespan",
                "refreshTokenGrantRefreshTokenLifespan",
                "tokenEndpointAuthMethod",
                "clientUri",
                "allowedCorsOrigins",
                "audience",
                "grantTypes",
                "responseTypes",
                "logoUri",
                "policyUri",
                "tosUri",
                "createdAt",
                "updatedAt",
                "metadata"
        };

        for (String field : FIELDS) {
            assertTrue(response.has(field));
        }
    }
}
