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

package io.supertokens.test.multitenant.api;

import com.google.gson.Gson;
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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class TestTenantCreationBehaviour {
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
    public void testDefaultStateWhenNothingIsPassedWhileCreatingAppAndTenant_3_0() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createApp_3_0(process.getProcess(), TenantIdentifier.BASE_TENANT, "a1", null, null, null, new JsonObject());
        {
            // Get using CDI 3.0
            JsonObject tenant = getTenant_3_0(new TenantIdentifier(null, "a1", null), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("public", tenant.get("tenantId").getAsString());
            assertTrue(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
        }
        {
            // Get using CDI 5.0
            JsonObject tenant = getTenant_5_0(new TenantIdentifier(null, "a1", null), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("public", tenant.get("tenantId").getAsString());
            assertTrue(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
            // firstFactors and requiredSecondaryFactors should be null
        }
        {
            // Get using CDI 5.1
            JsonObject tenant = getTenant_5_1(new TenantIdentifier(null, "a1", null), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(3, tenant.entrySet().size());
            assertEquals("public", tenant.get("tenantId").getAsString());
            assertTrue(tenant.has("thirdParty"));
            // firstFactors and requiredSecondaryFactors should be null
            assertFalse(tenant.get("thirdParty").getAsJsonObject().has("providers"));
        }

        createTenant_3_0(process.getProcess(), new TenantIdentifier(null, "a1", null), "t1", null, null, null,
                new JsonObject());

        {
            // Get using CDI 3.0
            JsonObject tenant = getTenant_3_0(new TenantIdentifier(null, "a1", "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertFalse(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
        }
        {
            // Get using CDI 5.0
            JsonObject tenant = getTenant_5_0(new TenantIdentifier(null, "a1", "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertFalse(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
            // firstFactors and requiredSecondaryFactors should be null
        }
        {
            // Get using CDI 5.1
            JsonObject tenant = getTenant_5_1(new TenantIdentifier(null, "a1", "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(3, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertTrue(tenant.has("thirdParty"));
            // firstFactors and requiredSecondaryFactors should be null
            assertFalse(tenant.get("thirdParty").getAsJsonObject().has("providers"));
        }
    }

    @Test
    public void testDefaultStateWhenNothingIsPassedWhileCreatingAppAndTenant_5_0() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createApp_5_0(process.getProcess(), TenantIdentifier.BASE_TENANT, "a1", null, null, null, false, null, false,
                null, new JsonObject());
        {
            // Get using CDI 3.0
            JsonObject tenant = getTenant_3_0(new TenantIdentifier(null, "a1", null), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("public", tenant.get("tenantId").getAsString());
            assertTrue(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
        }
        {
            // Get using CDI 5.0
            JsonObject tenant = getTenant_5_0(new TenantIdentifier(null, "a1", null), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("public", tenant.get("tenantId").getAsString());
            assertTrue(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
            // firstFactors and requiredSecondaryFactors should be null
        }
        {
            // Get using CDI 5.1
            JsonObject tenant = getTenant_5_1(new TenantIdentifier(null, "a1", null), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(3, tenant.entrySet().size());
            assertEquals("public", tenant.get("tenantId").getAsString());
            assertTrue(tenant.has("thirdParty"));
            // firstFactors and requiredSecondaryFactors should be null
            assertFalse(tenant.get("thirdParty").getAsJsonObject().has("providers"));
        }

        createTenant_5_0(process.getProcess(), new TenantIdentifier(null, "a1", null), "t1", null, null, null, false,
                null, false, null, new JsonObject());

        {
            // Get using CDI 3.0
            JsonObject tenant = getTenant_3_0(new TenantIdentifier(null, "a1", "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertFalse(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
        }
        {
            // Get using CDI 5.0
            JsonObject tenant = getTenant_5_0(new TenantIdentifier(null, "a1", "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertFalse(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
            // firstFactors and requiredSecondaryFactors should be null
        }
        {
            // Get using CDI 5.1
            JsonObject tenant = getTenant_5_1(new TenantIdentifier(null, "a1", "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(3, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertTrue(tenant.has("thirdParty"));
            // firstFactors and requiredSecondaryFactors should be null
            assertFalse(tenant.get("thirdParty").getAsJsonObject().has("providers"));
        }
    }

    @Test
    public void testDefaultStateWhenNothingIsPassedWhileCreatingAppAndTenant_5_1() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createApp_5_1(process.getProcess(), TenantIdentifier.BASE_TENANT, "a1", false, null, false, null,
                new JsonObject());
        {
            // Get using CDI 3.0
            JsonObject tenant = getTenant_3_0(new TenantIdentifier(null, "a1", null), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("public", tenant.get("tenantId").getAsString());
            assertTrue(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
        }
        {
            // Get using CDI 5.0
            JsonObject tenant = getTenant_5_0(new TenantIdentifier(null, "a1", null), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("public", tenant.get("tenantId").getAsString());
            assertTrue(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
            // firstFactors and requiredSecondaryFactors should be null
        }
        {
            // Get using CDI 5.1
            JsonObject tenant = getTenant_5_1(new TenantIdentifier(null, "a1", null), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(3, tenant.entrySet().size());
            assertEquals("public", tenant.get("tenantId").getAsString());
            assertTrue(tenant.has("thirdParty"));
            // firstFactors and requiredSecondaryFactors should be null
            assertFalse(tenant.get("thirdParty").getAsJsonObject().has("providers"));
        }

        createTenant_5_1(process.getProcess(), new TenantIdentifier(null, "a1", null), "t1", false, null, false, null,
                new JsonObject());

        {
            // Get using CDI 3.0
            JsonObject tenant = getTenant_3_0(new TenantIdentifier(null, "a1", "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertFalse(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
        }
        {
            // Get using CDI 5.0
            JsonObject tenant = getTenant_5_0(new TenantIdentifier(null, "a1", "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertFalse(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
            // firstFactors and requiredSecondaryFactors should be null
        }
        {
            // Get using CDI 5.1
            JsonObject tenant = getTenant_5_1(new TenantIdentifier(null, "a1", "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(4, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertTrue(tenant.has("thirdParty"));
            assertTrue(tenant.has("firstFactors"));
            assertEquals(0, tenant.get("firstFactors").getAsJsonArray().size());
            // requiredSecondaryFactors should be null
            assertTrue(tenant.get("thirdParty").getAsJsonObject().has("providers"));
        }
    }

    @Test
    public void testCreationOfAppAndTenantUsingFirstFactors() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createApp_5_1(process.getProcess(), TenantIdentifier.BASE_TENANT, "a1", true,
                new String[]{"emailpassword", "otp-phone"}, false, null, new JsonObject());

        {
            // Get using CDI 3.0
            JsonObject tenant = getTenant_3_0(new TenantIdentifier(null, "a1", null), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("public", tenant.get("tenantId").getAsString());
            assertTrue(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
        }

        {
            // Get using CDI 5.0
            JsonObject tenant = getTenant_5_0(new TenantIdentifier(null, "a1", null), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(6, tenant.entrySet().size());
            assertEquals("public", tenant.get("tenantId").getAsString());
            assertTrue(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
            assertEquals(2, tenant.get("firstFactors").getAsJsonArray().size());
            // requiredSecondaryFactors should be null
        }
        {
            // Get using CDI 5.1
            JsonObject tenant = getTenant_5_1(new TenantIdentifier(null, "a1", null), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(4, tenant.entrySet().size());
            assertEquals("public", tenant.get("tenantId").getAsString());
            assertTrue(tenant.has("thirdParty"));
            assertTrue(tenant.has("firstFactors"));
            assertEquals(2, tenant.get("firstFactors").getAsJsonArray().size());
            // requiredSecondaryFactors should be null
            assertFalse(tenant.get("thirdParty").getAsJsonObject().has("providers"));
        }

        createTenant_5_1(process.getProcess(), new TenantIdentifier(null, "a1", null), "t1", true,
                new String[]{"emailpassword", "otp-phone"}, false, null, new JsonObject());

        {
            // Get using CDI 3.0
            JsonObject tenant = getTenant_3_0(new TenantIdentifier(null, "a1", "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertTrue(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
        }
        {
            // Get using CDI 5.0
            JsonObject tenant = getTenant_5_0(new TenantIdentifier(null, "a1", "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(6, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertTrue(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
            assertEquals(2, tenant.get("firstFactors").getAsJsonArray().size());
            // requiredSecondaryFactors should be null
        }
        {
            // Get using CDI 5.1
            JsonObject tenant = getTenant_5_1(new TenantIdentifier(null, "a1", "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(4, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertTrue(tenant.has("thirdParty"));
            assertTrue(tenant.has("firstFactors"));
            assertEquals(2, tenant.get("firstFactors").getAsJsonArray().size());
            // requiredSecondaryFactors should be null
            assertTrue(tenant.get("thirdParty").getAsJsonObject().has("providers"));
        }
    }

    @Test
    public void shouldBeAbleToEnableRecipesForTenantCreatedInOlderCDI() throws Exception {
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // all recipes are disabled by default
        createTenant_3_0(process.getProcess(), TenantIdentifier.BASE_TENANT, "t1", null, null, null, new JsonObject());

        // Enable emailpassword and passwordless using firstFactors input
        createTenant_5_1(process.getProcess(), TenantIdentifier.BASE_TENANT, "t1", true,
                new String[]{"emailpassword", "otp-phone"}, false, null, new JsonObject());

        {
            // Get using CDI 3.0
            JsonObject tenant = getTenant_3_0(new TenantIdentifier(null, null, "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(5, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertTrue(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
        }
        {
            // Get using CDI 5.0
            JsonObject tenant = getTenant_5_0(new TenantIdentifier(null, null, "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(6, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertTrue(tenant.get("emailPassword").getAsJsonObject().get("enabled").getAsBoolean());
            assertFalse(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
            assertTrue(tenant.get("passwordless").getAsJsonObject().get("enabled").getAsBoolean());
            assertEquals(2, tenant.get("firstFactors").getAsJsonArray().size());
            // requiredSecondaryFactors should be null
        }
        {
            // Get using CDI 5.1
            JsonObject tenant = getTenant_5_1(new TenantIdentifier(null, null, "t1"), process.getProcess());
            assertEquals("OK", tenant.getAsJsonPrimitive("status").getAsString());
            tenant.remove("status");

            assertEquals(4, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertTrue(tenant.has("thirdParty"));
            assertTrue(tenant.has("firstFactors"));
            assertEquals(2, tenant.get("firstFactors").getAsJsonArray().size());
            // requiredSecondaryFactors should be null
            assertFalse(tenant.get("thirdParty").getAsJsonObject().has("providers"));
        }
    }

    private static JsonObject createApp_3_0(Main main, TenantIdentifier sourceTenant, String appId,
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

    private static JsonObject createApp_5_0(Main main, TenantIdentifier sourceTenant, String appId,
                                            Boolean emailPasswordEnabled,
                                            Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                            boolean setFirstFactors, String[] firstFactors,
                                            boolean setRequiredSecondaryFactors, String[] requiredSecondaryFactors,
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
        if (setFirstFactors || firstFactors != null) {
            requestBody.add("firstFactors", new Gson().toJsonTree(firstFactors));
        }
        if (setRequiredSecondaryFactors || requiredSecondaryFactors != null) {
            requestBody.add("requiredSecondaryFactors", new Gson().toJsonTree(requiredSecondaryFactors));
        }
        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app"),
                requestBody, 1000, 2500, null,
                SemVer.v5_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createApp_5_1(Main main, TenantIdentifier sourceTenant, String appId,
                                            boolean setFirstFactors, String[] firstFactors,
                                            boolean setRequiredSecondaryFactors, String[] requiredSecondaryFactors,
                                            JsonObject coreConfig) throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("appId", appId);
        if (setFirstFactors || firstFactors != null) {
            requestBody.add("firstFactors", new Gson().toJsonTree(firstFactors));
        }
        if (setRequiredSecondaryFactors || requiredSecondaryFactors != null) {
            requestBody.add("requiredSecondaryFactors", new Gson().toJsonTree(requiredSecondaryFactors));
        }
        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app/v2"),
                requestBody, 1000, 2500, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject getTenant_3_0(TenantIdentifier tenantIdentifier, Main main)
            throws HttpResponseException, IOException {

        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/tenant"),
                null, 1000, 1000, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject getTenant_5_0(TenantIdentifier tenantIdentifier, Main main)
            throws HttpResponseException, IOException {

        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/tenant"),
                null, 1000, 1000, null,
                SemVer.v5_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject getTenant_5_1(TenantIdentifier tenantIdentifier, Main main)
            throws HttpResponseException, IOException {

        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(tenantIdentifier, "/recipe/multitenancy/tenant/v2"),
                null, 1000, 1000, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject listApps(TenantIdentifier sourceTenant, Main main)
            throws HttpResponseException, IOException {
        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/app/list"),
                null, 1000, 1000, null,
                SemVer.v5_1.get(), "multitenancy");

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
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createTenant_3_0(Main main, TenantIdentifier sourceTenant, String tenantId,
                                               Boolean emailPasswordEnabled,
                                               Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                               JsonObject coreConfig) throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("tenantId", tenantId);
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
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant"),
                requestBody, 1000, 2500, null,
                SemVer.v3_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createTenant_5_0(Main main, TenantIdentifier sourceTenant, String tenantId,
                                               Boolean emailPasswordEnabled,
                                               Boolean thirdPartyEnabled, Boolean passwordlessEnabled,
                                               boolean setFirstFactors, String[] firstFactors,
                                               boolean setRequiredSecondaryFactors, String[] requiredSecondaryFactors,
                                               JsonObject coreConfig) throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("tenantId", tenantId);
        if (emailPasswordEnabled != null) {
            requestBody.addProperty("emailPasswordEnabled", emailPasswordEnabled);
        }
        if (thirdPartyEnabled != null) {
            requestBody.addProperty("thirdPartyEnabled", thirdPartyEnabled);
        }
        if (passwordlessEnabled != null) {
            requestBody.addProperty("passwordlessEnabled", passwordlessEnabled);
        }
        if (setFirstFactors || firstFactors != null) {
            requestBody.add("firstFactors", new Gson().toJsonTree(firstFactors));
        }
        if (setRequiredSecondaryFactors || requiredSecondaryFactors != null) {
            requestBody.add("requiredSecondaryFactors", new Gson().toJsonTree(requiredSecondaryFactors));
        }

        requestBody.add("coreConfig", coreConfig);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant"),
                requestBody, 1000, 2500, null,
                SemVer.v5_0.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static JsonObject createTenant_5_1(Main main, TenantIdentifier sourceTenant, String tenantId,
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
}
