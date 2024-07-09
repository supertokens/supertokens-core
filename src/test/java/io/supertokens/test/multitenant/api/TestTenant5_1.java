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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
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
import java.util.Set;

import static org.junit.Assert.*;

public class TestTenant5_1 {
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
    public void testCreateTenantWorks() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                coreConfig);

        JsonObject result = listTenants(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("tenants"));

        boolean found = false;

        for (JsonElement tenant : result.get("tenants").getAsJsonArray()) {
            JsonObject tenantObj = tenant.getAsJsonObject();

            if (tenantObj.get("tenantId").getAsString().equals("t1")) {
                found = true;

                assertEquals(4, tenantObj.entrySet().size());
                assertEquals("t1", tenantObj.get("tenantId").getAsString());
                assertEquals(1, tenantObj.get("thirdParty").getAsJsonObject().entrySet().size());
                assertEquals(0, tenantObj.get("thirdParty").getAsJsonObject().get("providers").getAsJsonArray().size());
                assertEquals(3, tenantObj.get("firstFactors").getAsJsonArray().size());
                assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
            }
        }

        assertTrue(found);
    }

    @Test
    public void testUpdateTenantWorks() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        // Create
        createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                coreConfig);

        JsonObject newConfig = new JsonObject();
        newConfig.addProperty("email_verification_token_lifetime", 2000);
        coreConfig.addProperty("email_verification_token_lifetime", 2000);

        // Update
        createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, new String[]{"emailpassword", "thirdparty"}, false, null,
                newConfig);

        JsonObject result = listTenants(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("tenants"));

        boolean found = false;

        for (JsonElement tenant : result.get("tenants").getAsJsonArray()) {
            JsonObject tenantObj = tenant.getAsJsonObject();

            if (tenantObj.get("tenantId").getAsString().equals("t1")) {
                found = true;

                assertEquals(4, tenantObj.entrySet().size());
                assertEquals("t1", tenantObj.get("tenantId").getAsString());
                assertEquals(1, tenantObj.get("thirdParty").getAsJsonObject().entrySet().size());
                assertEquals(0, tenantObj.get("thirdParty").getAsJsonObject().get("providers").getAsJsonArray().size());
                assertEquals(2, tenantObj.get("firstFactors").getAsJsonArray().size());
                assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
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
        createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                coreConfig);

        JsonObject newConfig = new JsonObject();
        newConfig.add("email_verification_token_lifetime", null);
        coreConfig.remove("email_verification_token_lifetime"); // for verification

        // Update
        createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                newConfig);

        JsonObject result = listTenants(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("tenants"));

        boolean found = false;

        for (JsonElement tenant : result.get("tenants").getAsJsonArray()) {
            JsonObject tenantObj = tenant.getAsJsonObject();

            if (tenantObj.get("tenantId").getAsString().equals("t1")) {
                found = true;

                assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
            }
        }

        assertTrue(found);
    }

    @Test
    public void testDeleteTenantWorks() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                coreConfig);

        JsonObject result = listTenants(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("tenants"));
        assertEquals(2, result.get("tenants").getAsJsonArray().size());

        JsonObject response = TestMultitenancyAPIHelper.deleteTenant(new TenantIdentifier(null, null, null), "t1",
                process.getProcess());
        assertTrue(response.get("didExist").getAsBoolean());

        result = listTenants(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("tenants"));
        assertEquals(1, result.get("tenants").getAsJsonArray().size());

        response = TestMultitenancyAPIHelper.deleteTenant(new TenantIdentifier(null, null, null), "t1",
                process.getProcess());
        assertFalse(response.get("didExist").getAsBoolean());
    }

    @Test
    public void testUnknownTenantError() throws Exception {
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                HttpRequestForTesting.getMultitenantUrl(new TenantIdentifier(null, null, "unknown"), "/recipe" +
                        "/multitenancy" +
                        "/tenant/v2"),
                null, 1000, 1000, null,
                SemVer.v5_1.get(), "multitenancy");
        assertEquals("TENANT_NOT_FOUND_ERROR", response.get("status").getAsString());
    }

    @Test
    public void testDifferentValuesForTenantIdThatShouldWork() throws Exception {

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/get-tenant-id";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException,
                    ServletException {
                try {
                    getTenantStorage(req);
                    super.sendTextResponse(200, this.getTenantIdentifier(req).getTenantId(), resp);
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
            createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    valueForCreate[i], true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,
                    new JsonObject());

            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/" + valueForQuery[i] + "/get-tenant-id", null, 1000, 1000,
                    null, WebserverAPI.getLatestCDIVersion().get(), null);

            assertEquals(valueForCreate[i].toLowerCase(), response);
        }
    }

    @Test
    public void testDifferentValuesForTenantIdThatShouldNotWork() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String[] valueForCreate = new String[]{"a_b", "1", "1a", "appid-hello", "AppId-Hello", "recipe", "reCipe",
                "CONFIG", "users", "Users"};
        for (int i = 0; i < valueForCreate.length; i++) {
            try {
                createTenant(
                        process.getProcess(),
                        new TenantIdentifier(null, null, null),
                        valueForCreate[i], true, new String[]{"emailpassword", "thirdparty", "otp-email"}, false, null,

                        new JsonObject());
            } catch (HttpResponseException e) {
                assertTrue(e.getMessage().contains("tenantId can only contain letters, numbers and hyphens")
                        || e.getMessage().contains("tenantId must not start with 'appid-'")
                        || e.getMessage().contains("Cannot use"));
            }
        }
    }

    @Test
    public void testDefaultRecipesEnabledWhileCreatingTenant() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        JsonObject response = createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", false, null, false, null,
                config);

        assertTrue(response.get("createdNew").getAsBoolean());

        JsonObject tenantObj = getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess());
        assertEquals("OK", tenantObj.get("status").getAsString());
        tenantObj.remove("status");

        assertEquals(4, tenantObj.entrySet().size());
        assertEquals("t1", tenantObj.get("tenantId").getAsString());
        assertEquals(1, tenantObj.get("thirdParty").getAsJsonObject().entrySet().size());
        assertEquals(0, tenantObj.get("thirdParty").getAsJsonObject().get("providers").getAsJsonArray().size());
        assertEquals(0, tenantObj.get("firstFactors").getAsJsonArray().size()); // no recipes are enabled
        assertFalse(tenantObj.has("requiredSecondaryFactors"));
    }

    @Test
    public void testFirstFactorsArray() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        JsonObject response = createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", false, null, false, null,
                config);

        assertTrue(response.get("createdNew").getAsBoolean());

        JsonObject tenant = getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertEquals(0, tenant.get("firstFactors").getAsJsonArray().size());

        // builtin firstFactor
        String[] firstFactors = new String[]{"otp-phone"};
        response = createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, new String[]{"otp-phone"}, false, null,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertTrue(tenant.get("firstFactors").isJsonArray());
        assertEquals(1, tenant.get("firstFactors").getAsJsonArray().size());
        assertEquals(firstFactors, new Gson().fromJson(tenant.get("firstFactors").getAsJsonArray(), String[].class));

        response = createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", false, null, false, null,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertTrue(tenant.get("firstFactors").isJsonArray());
        assertEquals(1, tenant.get("firstFactors").getAsJsonArray().size());
        assertEquals(firstFactors, new Gson().fromJson(tenant.get("firstFactors").getAsJsonArray(), String[].class));

        // custom factors
        firstFactors = new String[]{"biometric"};
        response = createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, firstFactors, false, null,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertTrue(tenant.get("firstFactors").isJsonArray());
        assertEquals(1, tenant.get("firstFactors").getAsJsonArray().size());
        assertEquals(firstFactors, new Gson().fromJson(tenant.get("firstFactors").getAsJsonArray(), String[].class));

        // test both
        firstFactors = new String[]{"otp-phone", "emailpassword", "biometric", "custom"};
        response = createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, firstFactors, false, null,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertTrue(tenant.get("firstFactors").isJsonArray());
        assertEquals(4, tenant.get("firstFactors").getAsJsonArray().size());
        assertEquals(Set.of(firstFactors),
                Set.of(new Gson().fromJson(tenant.get("firstFactors").getAsJsonArray(), String[].class)));

        response = createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, null, false, null,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess());
        assertEquals(4, tenant.entrySet().size());
        assertFalse(tenant.has("firstFactors"));
    }

    @Test
    public void testRequiredSecondaryFactorsArray() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        JsonObject response = createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", false, null, false, null, config);

        assertTrue(response.get("createdNew").getAsBoolean());

        JsonObject tenant = getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertFalse(tenant.has("requiredSecondaryFactors"));

        // builtin firstFactor
        String[] requiredSecondaryFactors = new String[]{"otp-phone"};
        response = createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", false, null, true, new String[]{"otp-phone"},
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess());
        assertEquals(6, tenant.entrySet().size());
        assertTrue(tenant.get("requiredSecondaryFactors").isJsonArray());
        assertEquals(1, tenant.get("requiredSecondaryFactors").getAsJsonArray().size());
        assertEquals(requiredSecondaryFactors,
                new Gson().fromJson(tenant.get("requiredSecondaryFactors").getAsJsonArray(), String[].class));

        response = createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", false, null, false, null,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess());
        assertEquals(6, tenant.entrySet().size());
        assertTrue(tenant.get("requiredSecondaryFactors").isJsonArray());
        assertEquals(1, tenant.get("requiredSecondaryFactors").getAsJsonArray().size());
        assertEquals(requiredSecondaryFactors,
                new Gson().fromJson(tenant.get("requiredSecondaryFactors").getAsJsonArray(), String[].class));

        // custom factors
        requiredSecondaryFactors = new String[]{"biometric"};
        response = createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", false, null, true, requiredSecondaryFactors,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess());
        assertEquals(6, tenant.entrySet().size());
        assertTrue(tenant.get("requiredSecondaryFactors").isJsonArray());
        assertEquals(1, tenant.get("requiredSecondaryFactors").getAsJsonArray().size());
        assertEquals(requiredSecondaryFactors,
                new Gson().fromJson(tenant.get("requiredSecondaryFactors").getAsJsonArray(), String[].class));

        // test both
        requiredSecondaryFactors = new String[]{"otp-phone", "emailpassword", "biometric", "custom"};
        response = createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", false, null, true, requiredSecondaryFactors,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess());
        assertEquals(6, tenant.entrySet().size());
        assertTrue(tenant.get("requiredSecondaryFactors").isJsonArray());
        assertEquals(4, tenant.get("requiredSecondaryFactors").getAsJsonArray().size());
        assertEquals(Set.of(requiredSecondaryFactors),
                Set.of(new Gson().fromJson(tenant.get("requiredSecondaryFactors").getAsJsonArray(), String[].class)));

        response = createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", false, null, true, null,
                config);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess());
        assertEquals(5, tenant.entrySet().size());
        assertFalse(tenant.has("requiredSecondaryFactors"));
    }

    @Test
    public void testDuplicateValuesInFirstFactorsAndRequiredSecondaryFactors() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        String[] factors = new String[]{"duplicate", "emailpassword", "duplicate", "custom"};
        try {
            createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, factors, false, null,
                    config);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: firstFactors input should not contain duplicate values",
                    e.getMessage());
        }

        try {
            createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", false, null, true, factors,
                    config);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: requiredSecondaryFactors input should not contain " +
                            "duplicate values",
                    e.getMessage());
        }
    }

    private static JsonObject createTenant(Main main, TenantIdentifier sourceTenant, String tenantId,
                                           boolean setFirstFactors, String[] firstFactors,
                                           boolean setRequiredSecondaryFactors, String[] requiredSecondaryFactors,
                                           JsonObject coreConfig) throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("tenantId", tenantId);
        if (setFirstFactors || firstFactors != null) {
            requestBody.add("firstFactors", new Gson().toJsonTree(firstFactors));
        }
        if (setRequiredSecondaryFactors || requiredSecondaryFactors != null) {
            requestBody.add("requiredSecondaryFactors", new Gson().toJsonTree(requiredSecondaryFactors));
        }

        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant/v2"),
                requestBody, 1000, 2500, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject listTenants(TenantIdentifier sourceTenant, Main main)
            throws HttpResponseException, IOException {
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant/list/v2"),
                null, 1000, 1000, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject getTenant(TenantIdentifier tenantIdentifier, Main main)
            throws HttpResponseException, IOException {

        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/tenant/v2"),
                null, 1000, 1000, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }
}
