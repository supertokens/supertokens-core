/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.multitenant.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.config.CoreConfigTestContent;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.Webserver;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class TestApp3_0 {
    TestingProcessManager.TestingProcess process;

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @After
    public void afterEach() throws InterruptedException {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Before
    public void beforeEach() throws InterruptedException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException {
        Utils.reset();

        String[] args = {"../"};

        this.process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
    }

    @Test
    public void testCreateAppWorks() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        JsonObject result = listApps(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("apps"));

        boolean found = false;

        for (JsonElement app : result.get("apps").getAsJsonArray()) {
            JsonObject appObj = app.getAsJsonObject();

            if (appObj.get("appId").getAsString().equals("a1")) {
                found = true;

                for (JsonElement tenant : appObj.get("tenants").getAsJsonArray()) {
                    JsonObject tenantObj = tenant.getAsJsonObject();
                    assertEquals(5, tenantObj.entrySet().size());
                    assertEquals("public", tenantObj.get("tenantId").getAsString());
                    assertEquals(1, tenantObj.get("emailPassword").getAsJsonObject().entrySet().size());
                    assertTrue(tenantObj.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
                    assertEquals(2, tenantObj.get("thirdParty").getAsJsonObject().entrySet().size());
                    assertTrue(tenantObj.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
                    assertEquals(1, tenantObj.get("passwordless").getAsJsonObject().entrySet().size());
                    assertTrue(tenantObj.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
                    assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
                }
            }
        }

        assertTrue(found);
    }

    @Test
    public void testUpdateAppWorks() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        // Create
        createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        JsonObject newConfig = new JsonObject();
        newConfig.addProperty("email_verification_token_lifetime", 2000);
        coreConfig.addProperty("email_verification_token_lifetime", 2000);

        // Update
        createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                newConfig);

        JsonObject result = listApps(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("apps"));

        boolean found = false;

        for (JsonElement app : result.get("apps").getAsJsonArray()) {
            JsonObject appObj = app.getAsJsonObject();

            if (appObj.get("appId").getAsString().equals("a1")) {
                found = true;

                for (JsonElement tenant : appObj.get("tenants").getAsJsonArray()) {
                    JsonObject tenantObj = tenant.getAsJsonObject();
                    assertEquals(5, tenantObj.entrySet().size());
                    assertEquals("public", tenantObj.get("tenantId").getAsString());
                    assertEquals(1, tenantObj.get("emailPassword").getAsJsonObject().entrySet().size());
                    assertTrue(tenantObj.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
                    assertEquals(2, tenantObj.get("thirdParty").getAsJsonObject().entrySet().size());
                    assertTrue(tenantObj.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
                    assertEquals(1, tenantObj.get("passwordless").getAsJsonObject().entrySet().size());
                    assertTrue(tenantObj.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
                    assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
                }
            }
        }

        assertTrue(found);
    }

    @Test
    public void testUpdateWithNullValueDeletesTheSetting() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);
        coreConfig.addProperty("email_verification_token_lifetime", 2000);

        // Create
        createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        JsonObject newConfig = new JsonObject();
        newConfig.add("email_verification_token_lifetime", null);
        coreConfig.remove("email_verification_token_lifetime"); // for verification

        // Update
        createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                newConfig);

        JsonObject result = listApps(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("apps"));

        boolean found = false;

        for (JsonElement app : result.get("apps").getAsJsonArray()) {
            JsonObject appObj = app.getAsJsonObject();

            if (appObj.get("appId").getAsString().equals("a1")) {
                found = true;

                for (JsonElement tenant : appObj.get("tenants").getAsJsonArray()) {
                    JsonObject tenantObj = tenant.getAsJsonObject();
                    assertTrue(tenantObj.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
                    assertTrue(tenantObj.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
                    assertTrue(tenantObj.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
                    assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
                }
            }
        }

        assertTrue(found);
    }

    @Test
    public void testDeleteAppWorks() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        JsonObject result = listApps(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("apps"));
        assertEquals(2, result.get("apps").getAsJsonArray().size());

        JsonObject response = deleteApp(new TenantIdentifier(null, null, null), "a1",
                process.getProcess());
        assertTrue(response.get("didExist").getAsBoolean());

        result = listApps(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("apps"));
        assertEquals(1, result.get("apps").getAsJsonArray().size());

        response = deleteApp(new TenantIdentifier(null, null, null), "a1",
                process.getProcess());
        assertFalse(response.get("didExist").getAsBoolean());
    }

    @Test
    public void testAddingWithDifferentConnectionURIAddsToNullConnectionURI() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createApp(
                process.getProcess(),
                new TenantIdentifier("localhost", null, null),
                "a1", true, true, true,
                new JsonObject());

        createApp(
                process.getProcess(),
                new TenantIdentifier("127.0.0.1", null, null),
                "a2", true, true, true,
                new JsonObject());

        JsonObject result = listApps(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("apps"));
        assertEquals(3, result.get("apps").getAsJsonArray().size());

        boolean foundA1 = false;
        boolean foundA2 = false;

        for (JsonElement app : result.get("apps").getAsJsonArray()) {
            JsonObject appObj = app.getAsJsonObject();

            if (appObj.get("appId").getAsString().equals("a1")) {
                foundA1 = true;
            } else if (appObj.get("appId").getAsString().equals("a2")) {
                foundA2 = true;
            }
        }

        assertTrue(foundA1);
        assertTrue(foundA2);
    }

    @Test
    public void testDifferentValuesForAppIdThatShouldWork() throws Exception {

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/get-app-id";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException,
                    ServletException {
                try {
                    getTenantStorage(req);
                    super.sendTextResponse(200, this.getTenantIdentifier(req).getAppId(), resp);
                } catch (TenantOrAppNotFoundException e) {
                    throw new ServletException(e);
                }
            }
        });

        String[] valueForCreate = new String[]{"a1", "a-1", "a-B-1", "CAPS1", "MixedCase", "capsinquery",
                "mixedcaseinquery"};
        String[] valueForQuery = new String[]{"a1", "a-1", "A-b-1", "CAPS1", "MixedCase", "CAPSINQUERY",
                "MixedCaseInQuery"};

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (int i = 0; i < valueForCreate.length; i++) {
            createApp(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    valueForCreate[i], true, true, true,
                    new JsonObject());

            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/appid-" + valueForQuery[i] + "/get-app-id", null, 1000, 1000,
                    null, WebserverAPI.getLatestCDIVersion().get(), null);

            assertEquals(valueForCreate[i].toLowerCase(), response);
        }
    }

    @Test
    public void testDifferentValuesForAppIdThatShouldNotWork() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String[] valueForCreate = new String[]{"a_b", "1", "1a", "appid-hello"};
        for (int i = 0; i < valueForCreate.length; i++) {
            try {
                createApp(
                        process.getProcess(),
                        new TenantIdentifier(null, null, null),
                        valueForCreate[i], true, true, true,
                        new JsonObject());
            } catch (HttpResponseException e) {
                assertTrue(e.getMessage().contains("appId can only contain letters, numbers and hyphens") ||
                        e.getMessage().contains("appId must not start with 'appid-'"));
            }
        }
    }

    @Test
    public void testCreationOfAppWithWrongDbSettingsAndLaterUpdateIt() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1000); // This db should not exist

        try {
            createApp(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "a1", true, true, true,
                    coreConfig);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(500, e.statusCode);
        }

        // Ensure storage is created
        Storage errorStorage = StorageLayer.getStorage(new TenantIdentifier(null, "a1", null), process.getProcess());
        assertNotNull(errorStorage);

        {
            JsonObject result = listApps(new TenantIdentifier(null, null, null),
                    process.getProcess());
            assertTrue(result.has("apps"));

            boolean found = false;

            for (JsonElement app : result.get("apps").getAsJsonArray()) {
                JsonObject appObj = app.getAsJsonObject();

                if (appObj.get("appId").getAsString().equals("a1")) {
                    found = true;

                    for (JsonElement tenant : appObj.get("tenants").getAsJsonArray()) {
                        JsonObject tenantObj = tenant.getAsJsonObject();
                        assertTrue(tenantObj.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
                        assertTrue(tenantObj.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
                        assertTrue(tenantObj.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
                        assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
                    }
                }
            }

            assertTrue(found);
        }

        { // test that api fails
            try {
                TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", null),
                        "test@example.com", "password", process.getProcess());
                fail();
            } catch (HttpResponseException e) {
                assertEquals(500, e.statusCode);
            }
        }

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1); // This db should exist

        createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        {
            JsonObject result = listApps(new TenantIdentifier(null, null, null),
                    process.getProcess());
            assertTrue(result.has("apps"));

            boolean found = false;

            for (JsonElement app : result.get("apps").getAsJsonArray()) {
                JsonObject appObj = app.getAsJsonObject();

                if (appObj.get("appId").getAsString().equals("a1")) {
                    found = true;

                    for (JsonElement tenant : appObj.get("tenants").getAsJsonArray()) {
                        JsonObject tenantObj = tenant.getAsJsonObject();
                        assertTrue(tenantObj.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
                        assertTrue(tenantObj.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
                        assertTrue(tenantObj.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
                        assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
                    }
                }
            }

            assertTrue(found);
        }

        Storage workingStorage = StorageLayer.getStorage(new TenantIdentifier(null, "a1", null), process.getProcess());
        assertNotNull(workingStorage);
        assertNotEquals(errorStorage, workingStorage);

        { // test that api passes
            TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier(null, "a1", null),
                    "test@example.com", "password", process.getProcess());
        }
    }

    @Test
    public void testDefaultRecipesEnabledWhileCreatingApp() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        JsonObject response = createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", null, null, null,
                config);

        assertTrue(response.get("createdNew").getAsBoolean());

        JsonObject tenant = getTenant(new TenantIdentifier(null, "a1", null),
                process.getProcess());
        assertTrue(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
        assertTrue(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
        assertTrue(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
    }

    @Test
    public void testInvalidTypedValueInCoreConfigWhileCreatingApp() throws Exception {
        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String[] properties = new String[]{
                "access_token_validity", // long
                "access_token_validity", // long
                "access_token_validity", // long
                "access_token_validity", // long
                "disable_telemetry", // boolean
                "postgresql_connection_pool_size", // int
                "mysql_connection_pool_size", // int
        };
        Object[] values = new Object[]{
                "abcd", // access_token_validity
                "",
                "null",
                null,
                "abcd", // disable_telemetry
                "abcd", // postgresql_connection_pool_size
                "abcd", // mysql_connection_pool_size
        };

        String[] expectedErrorMessages = new String[]{
                "Http error. Status Code: 400. Message: Invalid core config: 'access_token_validity' must be of type " +
                        "long",
                // access_token_validity
                "Http error. Status Code: 400. Message: Invalid core config: 'access_token_validity' must be of type " +
                        "long",
                // access_token_validity
                "Http error. Status Code: 400. Message: Invalid core config: 'access_token_validity' must be of type " +
                        "long",
                // access_token_validity
                null,
                "Http error. Status Code: 400. Message: Invalid core config: 'disable_telemetry' must be of type " +
                        "boolean",
                // disable_telemetry
                "Http error. Status Code: 400. Message: Invalid core config: 'postgresql_connection_pool_size' must " +
                        "be of type int",
                // postgresql_connection_pool_size
                "Http error. Status Code: 400. Message: Invalid core config: 'mysql_connection_pool_size' must be of " +
                        "type int",
                // mysql_connection_pool_size
        };

        for (int i = 0; i < properties.length; i++) {
            try {
                System.out.println("Test case " + i);
                JsonObject config = new JsonObject();
                if (values[i] == null) {
                    config.add(properties[i], null);
                } else if (values[i] instanceof String) {
                    config.addProperty(properties[i], (String) values[i]);
                } else if (values[i] instanceof Boolean) {
                    config.addProperty(properties[i], (Boolean) values[i]);
                } else if (values[i] instanceof Number) {
                    config.addProperty(properties[i], (Number) values[i]);
                } else {
                    throw new RuntimeException("Invalid type");
                }
                StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

                JsonObject response = createApp(
                        process.getProcess(),
                        new TenantIdentifier(null, null, null),
                        "a1", null, null, null,
                        config);
                if (expectedErrorMessages[i] != null) {
                    fail();
                }
            } catch (HttpResponseException e) {
                assertEquals(400, e.statusCode);
                if (!e.getMessage().contains("Invalid config key")) {
                    assertEquals(expectedErrorMessages[i], e.getMessage());
                }
            }
        }
    }

    @Test
    public void testInvalidCoreConfig() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        CoreConfigTestContent.getInstance(process.getProcess()).setKeyValue(CoreConfigTestContent.VALIDITY_TESTING,
                true);

        {
            JsonObject config = new JsonObject();
            config.addProperty("access_token_validity", 3600);
            config.addProperty("refresh_token_validity", 3);
            StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

            try {
                JsonObject response = createApp(
                        process.getProcess(),
                        new TenantIdentifier(null, null, null),
                        "a1", null, null, null,
                        config);
                fail();
            } catch (HttpResponseException e) {
                assertEquals(400, e.statusCode);
                assertEquals(
                        "Http error. Status Code: 400. Message: Invalid core config: 'refresh_token_validity' must be" +
                                " strictly greater than 'access_token_validity'.",
                        e.getMessage());
            }
        }
    }

    private static JsonObject createApp(Main main, TenantIdentifier sourceTenant, String appId,
                                        Boolean emailPasswordEnabled,
                                        Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                        JsonObject coreConfig) throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("appId", appId);
        if (emailPasswordEnabled != null) {
            requestBody.addProperty("emailPasswordEnabled", emailPasswordEnabled);
        }
        if (thirdPartyEnabled != null) {
            requestBody.addProperty("thirdPartyEnabled", thirdPartyEnabled);
        }
        if (passwordlessEnabled != null) {
            requestBody.addProperty("passwordlessEnabled", passwordlessEnabled);
        }
        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject getTenant(TenantIdentifier tenantIdentifier, Main main)
            throws HttpResponseException, IOException {

        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/tenant"),
                null, 1000, 1000, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject listApps(TenantIdentifier sourceTenant, Main main)
            throws HttpResponseException, IOException {
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app/list"),
                null, 1000, 1000, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject deleteApp(TenantIdentifier sourceTenant, String appId, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("appId", appId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app/remove"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }
}
