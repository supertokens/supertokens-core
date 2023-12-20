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

import com.google.gson.*;
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

public class TestTenant {
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

        TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, true, true,
                coreConfig);

        JsonObject result = TestMultitenancyAPIHelper.listTenants(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("tenants"));

        boolean found = false;

        for (JsonElement tenant : result.get("tenants").getAsJsonArray()) {
            JsonObject tenantObj = tenant.getAsJsonObject();

            if (tenantObj.get("tenantId").getAsString().equals("t1")) {
                found = true;

                assertTrue(tenantObj.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
                assertTrue(tenantObj.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
                assertTrue(tenantObj.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
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
        TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, true, true,
                coreConfig);

        JsonObject newConfig = new JsonObject();
        newConfig.addProperty("email_verification_token_lifetime", 2000);
        coreConfig.addProperty("email_verification_token_lifetime", 2000);

        // Update
        TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, true, true,
                newConfig);

        JsonObject result = TestMultitenancyAPIHelper.listTenants(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("tenants"));

        boolean found = false;

        for (JsonElement tenant : result.get("tenants").getAsJsonArray()) {
            JsonObject tenantObj = tenant.getAsJsonObject();

            if (tenantObj.get("tenantId").getAsString().equals("t1")) {
                found = true;

                assertTrue(tenantObj.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
                assertTrue(tenantObj.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
                assertTrue(tenantObj.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
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
        TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, true, true,
                coreConfig);

        JsonObject newConfig = new JsonObject();
        newConfig.add("email_verification_token_lifetime", null);
        coreConfig.remove("email_verification_token_lifetime"); // for verification

        // Update
        TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, true, true,
                newConfig);

        JsonObject result = TestMultitenancyAPIHelper.listTenants(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("tenants"));

        boolean found = false;

        for (JsonElement tenant : result.get("tenants").getAsJsonArray()) {
            JsonObject tenantObj = tenant.getAsJsonObject();

            if (tenantObj.get("tenantId").getAsString().equals("t1")) {
                found = true;

                assertTrue(tenantObj.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
                assertTrue(tenantObj.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
                assertTrue(tenantObj.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
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

        TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, true, true,
                coreConfig);

        JsonObject result = TestMultitenancyAPIHelper.listTenants(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("tenants"));
        assertEquals(2, result.get("tenants").getAsJsonArray().size());

        JsonObject response = TestMultitenancyAPIHelper.deleteTenant(new TenantIdentifier(null, null, null), "t1",
                process.getProcess());
        assertTrue(response.get("didExist").getAsBoolean());

        result = TestMultitenancyAPIHelper.listTenants(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("tenants"));
        assertEquals(1, result.get("tenants").getAsJsonArray().size());

        response = TestMultitenancyAPIHelper.deleteTenant(new TenantIdentifier(null, null, null), "t1",
                process.getProcess());
        assertFalse(response.get("didExist").getAsBoolean());
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
                    super.sendTextResponse(200, this.getTenantIdentifierWithStorageFromRequest(req).getTenantId(), resp);
                } catch (TenantOrAppNotFoundException e) {
                    throw new ServletException(e);
                }
            }
        });

        String[] valueForCreate = new String[]{"a1", "a-1", "a-B-1", "CAPS1", "MixedCase", "capsinquery", "mixedcaseinquery"};
        String[] valueForQuery  = new String[]{"a1", "a-1", "A-b-1", "CAPS1", "MixedCase", "CAPSINQUERY", "MixedCaseInQuery"};

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (int i = 0; i < valueForCreate.length; i++) {
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    valueForCreate[i], true, true, true,
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

        String[] valueForCreate = new String[]{"a_b", "1", "1a", "appid-hello", "AppId-Hello", "recipe", "reCipe", "CONFIG", "users", "Users"};
        for (int i = 0; i < valueForCreate.length; i++) {
            try {
                TestMultitenancyAPIHelper.createTenant(
                        process.getProcess(),
                        new TenantIdentifier(null, null, null),
                        valueForCreate[i], true, true, true,
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

        JsonObject response = TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", null, null, null,
                config);

        assertTrue(response.get("createdNew").getAsBoolean());

        JsonObject tenant = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess());
        assertFalse(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
        assertFalse(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
        assertFalse(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
    }

    @Test
    public void testFirstFactorsArray() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        JsonObject response = TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", null, null, null,
                config);

        assertTrue(response.get("createdNew").getAsBoolean());

        JsonObject tenant = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess(), SemVer.v5_0);
        assertNull(tenant.get("firstFactors"));

        // builtin firstFactor
        String[] firstFactors = new String[]{"otp-phone"};
        response = TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", null, null, true,
                true, new String[]{"otp-phone"}, false, null,
                config, SemVer.v5_0);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess(), SemVer.v5_0);
        assertTrue(tenant.get("firstFactors").isJsonArray());
        assertEquals(1, tenant.get("firstFactors").getAsJsonArray().size());
        assertEquals(firstFactors, new Gson().fromJson(tenant.get("firstFactors").getAsJsonArray(), String[].class));

        response = TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", null, null, null,
                false, null, false, null,
                config, SemVer.v5_0);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess(), SemVer.v5_0);
        assertTrue(tenant.get("firstFactors").isJsonArray());
        assertEquals(1, tenant.get("firstFactors").getAsJsonArray().size());
        assertEquals(firstFactors, new Gson().fromJson(tenant.get("firstFactors").getAsJsonArray(), String[].class));

        // custom factors
        firstFactors = new String[]{"biometric"};
        response = TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", null, null, null,
                true, firstFactors, false, null,
                config, SemVer.v5_0);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess(), SemVer.v5_0);
        assertTrue(tenant.get("firstFactors").isJsonArray());
        assertEquals(1, tenant.get("firstFactors").getAsJsonArray().size());
        assertEquals(firstFactors, new Gson().fromJson(tenant.get("firstFactors").getAsJsonArray(), String[].class));

        // test both
        firstFactors = new String[]{"otp-phone", "emailpassword", "biometric", "custom"};
        response = TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, null, true,
                true, firstFactors, false, null,
                config, SemVer.v5_0);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess(), SemVer.v5_0);
        assertTrue(tenant.get("firstFactors").isJsonArray());
        assertEquals(4, tenant.get("firstFactors").getAsJsonArray().size());
        assertEquals(Set.of(firstFactors), Set.of(new Gson().fromJson(tenant.get("firstFactors").getAsJsonArray(), String[].class)));

        response = TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", null, null, null,
                true, null, false, null,
                config, SemVer.v5_0);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess(), SemVer.v5_0);
        assertNull(tenant.get("firstFactors"));
    }

    @Test
    public void testRequiredSecondaryFactorsArray() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        JsonObject response = TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", null, null, null,
                config);

        assertTrue(response.get("createdNew").getAsBoolean());

        JsonObject tenant = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess(), SemVer.v5_0);
        assertNull(tenant.get("requiredSecondaryFactors"));

        // builtin firstFactor
        String[] requiredSecondaryFactors = new String[]{"otp-phone"};
        response = TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", null, null, true,
                false, null, true, new String[]{"otp-phone"},
                config, SemVer.v5_0);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess(), SemVer.v5_0);
        assertTrue(tenant.get("requiredSecondaryFactors").isJsonArray());
        assertEquals(1, tenant.get("requiredSecondaryFactors").getAsJsonArray().size());
        assertEquals(requiredSecondaryFactors, new Gson().fromJson(tenant.get("requiredSecondaryFactors").getAsJsonArray(), String[].class));

        response = TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", null, null, null,
                false, null, false, null,
                config, SemVer.v5_0);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess(), SemVer.v5_0);
        assertTrue(tenant.get("requiredSecondaryFactors").isJsonArray());
        assertEquals(1, tenant.get("requiredSecondaryFactors").getAsJsonArray().size());
        assertEquals(requiredSecondaryFactors, new Gson().fromJson(tenant.get("requiredSecondaryFactors").getAsJsonArray(), String[].class));

        // custom factors
        requiredSecondaryFactors = new String[]{"biometric"};
        response = TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", null, null, null,
                false, null, true, requiredSecondaryFactors,
                config, SemVer.v5_0);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess(), SemVer.v5_0);
        assertTrue(tenant.get("requiredSecondaryFactors").isJsonArray());
        assertEquals(1, tenant.get("requiredSecondaryFactors").getAsJsonArray().size());
        assertEquals(requiredSecondaryFactors, new Gson().fromJson(tenant.get("requiredSecondaryFactors").getAsJsonArray(), String[].class));

        // test both
        requiredSecondaryFactors = new String[]{"otp-phone", "emailpassword", "biometric", "custom"};
        response = TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, null, true,
                false, null, true, requiredSecondaryFactors,
                config, SemVer.v5_0);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess(), SemVer.v5_0);
        assertTrue(tenant.get("requiredSecondaryFactors").isJsonArray());
        assertEquals(4, tenant.get("requiredSecondaryFactors").getAsJsonArray().size());
        assertEquals(Set.of(requiredSecondaryFactors), Set.of(new Gson().fromJson(tenant.get("requiredSecondaryFactors").getAsJsonArray(), String[].class)));

        response = TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", null, null, null,
                false, null, true, null,
                config, SemVer.v5_0);
        assertFalse(response.get("createdNew").getAsBoolean());

        tenant = TestMultitenancyAPIHelper.getTenant(new TenantIdentifier(null, null, "t1"),
                process.getProcess(), SemVer.v5_0);
        assertNull(tenant.get("requiredSecondaryFactors"));
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
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", null, null, null,
                    true, factors, false, null,
                    config, SemVer.v5_0);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: firstFactors input should not contain duplicate values", e.getMessage());
        }

        try {
            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", null, null, null,
                    false, null, true, factors,
                    config, SemVer.v5_0);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
            assertEquals("Http error. Status Code: 400. Message: requiredSecondaryFactors input should not contain duplicate values", e.getMessage());
        }

    }
}
