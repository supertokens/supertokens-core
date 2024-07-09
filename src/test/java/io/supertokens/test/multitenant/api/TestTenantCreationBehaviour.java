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

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.WebserverAPI;
import io.supertokens.webserver.api.multitenancy.BaseCreateOrUpdate;
import jakarta.servlet.ServletException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            assertTrue(tenant.get("thirdParty").getAsJsonObject().has("providers"));
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

            assertEquals(4, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertTrue(tenant.has("thirdParty"));
            assertEquals(new JsonArray(), tenant.get("firstFactors").getAsJsonArray());
            // requiredSecondaryFactors should be null
            assertTrue(tenant.get("thirdParty").getAsJsonObject().has("providers"));
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
            assertTrue(tenant.get("thirdParty").getAsJsonObject().has("providers"));
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

            assertEquals(4, tenant.entrySet().size());
            assertEquals("t1", tenant.get("tenantId").getAsString());
            assertTrue(tenant.has("thirdParty"));
            assertEquals(new JsonArray(), tenant.get("firstFactors").getAsJsonArray());
            // requiredSecondaryFactors should be null
            assertTrue(tenant.get("thirdParty").getAsJsonObject().has("providers"));
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
            assertTrue(tenant.get("thirdParty").getAsJsonObject().has("providers"));
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
            assertTrue(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
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
            assertTrue(tenant.get("thirdParty").getAsJsonObject().has("providers"));
        }

        createTenant_5_1(process.getProcess(), new TenantIdentifier(null, "a1", null), "t1", true,
                new String[]{"emailpassword", "otp-phone"}, false, null, new JsonObject());
        TestMultitenancyAPIHelper.addOrUpdateThirdPartyProviderConfig(new TenantIdentifier(null, "a1", "t1"),
                new ThirdPartyConfig.Provider("google", "name", null, null, null, null, null, null, null, null, null,
                        null, null, null), process.getProcess());

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
            assertTrue(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
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
            assertTrue(tenant.get("thirdParty").getAsJsonObject().get("enabled").getAsBoolean());
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
            assertTrue(tenant.get("thirdParty").getAsJsonObject().has("providers"));
        }
    }

    @Test
    public void testCrossVersionCreateAndUpdateCases() throws Exception {
        Gson gson = new Gson();

        String[] testFiles = new String[]{
                "test-data/tenant-cdi-test-cases_1.jsonl",
                "test-data/tenant-cdi-test-cases_2.jsonl",
                "test-data/tenant-cdi-test-cases_3.jsonl",
                "test-data/tenant-cdi-test-cases_4.jsonl",
                "test-data/tenant-cdi-test-cases_5.jsonl",
                "test-data/tenant-cdi-test-cases_6.jsonl",
        };

        int c = 0;
        for (String testFile : testFiles) {
            try (BufferedReader reader = new BufferedReader(new FileReader(testFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    c++;

                    JsonObject jsonObject = gson.fromJson(line, JsonObject.class);

                    TenantIdentifier tenantIdentifier;
                    if (jsonObject.get("tenantId").getAsString().equals("public")) {
                        tenantIdentifier = new TenantIdentifier(null, "a1", null);
                    } else {
                        tenantIdentifier = new TenantIdentifier(null, null, "t1");
                    }

                    SemVer version;
                    boolean isV2;
                    TenantConfig tenantConfig;

                    try {
                        if (jsonObject.get("cv").getAsString().equals("4")) {
                            version = SemVer.v3_0;
                            isV2 = false;
                        } else if (jsonObject.get("cv").getAsString().equals("5")) {
                            version = SemVer.v5_0;
                            isV2 = false;
                        } else if (jsonObject.get("cv").getAsString().equals("v2")) {
                            version = SemVer.v5_1;
                            isV2 = true;
                        } else {
                            throw new Exception("Invalid version");
                        }
                        tenantConfig = BaseCreateOrUpdate.createBaseConfigForVersionForTest(version, tenantIdentifier,
                                isV2);
                        tenantConfig = BaseCreateOrUpdate.applyTenantUpdatesForTest(tenantConfig, version, isV2,
                                jsonObject.get("cbody").getAsJsonObject());

                        if (jsonObject.get("uv").getAsString().equals("4")) {
                            version = SemVer.v3_0;
                            isV2 = false;
                        } else if (jsonObject.get("uv").getAsString().equals("5")) {
                            version = SemVer.v5_0;
                            isV2 = false;
                        } else if (jsonObject.get("uv").getAsString().equals("v2")) {
                            version = SemVer.v5_1;
                            isV2 = true;
                        } else {
                            throw new Exception("Invalid version");
                        }

                        tenantConfig = BaseCreateOrUpdate.applyTenantUpdatesForTest(tenantConfig, version, isV2,
                                jsonObject.get("ubody").getAsJsonObject());

                        Storage storage = StorageLayer.getBaseStorage(process.getProcess());
                        validateState(
                                jsonObject,
                                tenantConfig,
                                tenantConfig.toJsonLesserThanOrEqualTo4_0(false, storage, new String[0]),
                                tenantConfig.toJson5_0(false, storage, new String[0]),
                                tenantConfig.toJson_v2_5_1(false, storage, new String[0])
                        );
                        assertFalse(jsonObject.get("invalidConfig").getAsBoolean());
                    } catch (InvalidConfigException | ServletException e) {
                        if (e instanceof ServletException) {
                            assertTrue(((ServletException) e).getCause() instanceof WebserverAPI.BadRequestException);
                        }
                        if (!jsonObject.get("invalidConfig").getAsBoolean()) {
                            System.out.println(c);
                            System.out.println("InvalidConfig: " + jsonObject.toString());
                            System.out.println(e.toString());
                        }
                        assertTrue(jsonObject.get("invalidConfig").getAsBoolean());
                    } catch (AssertionError e) {
                        System.out.println(c);
                        System.out.println("Mismatch: " + jsonObject.toString());
                        throw e;
                    }
                }
            } catch (Exception e) {
                throw e;
            }
        }

        System.out.println(c);
    }

    private void validateState(JsonObject jsonObject, TenantConfig tenantConfig, JsonObject jsonLesserThanOrEqualTo40,
                               JsonObject json50, JsonObject jsonV251) {
        JsonObject state = jsonObject.get("tenantState").getAsJsonObject();
        JsonObject g4 = jsonObject.get("g4").getAsJsonObject();
        JsonObject g5 = jsonObject.get("g5").getAsJsonObject();
        JsonObject gV2 = jsonObject.get("gv2").getAsJsonObject();

        {
            // validate Tenant state
            assertEquals(state.get("emailPasswordEnabled").getAsBoolean(), tenantConfig.emailPasswordConfig.enabled);
            assertEquals(state.get("thirdPartyEnabled").getAsBoolean(), tenantConfig.thirdPartyConfig.enabled);
            assertEquals(state.get("passwordlessEnabled").getAsBoolean(), tenantConfig.passwordlessConfig.enabled);
            assertEquals(jsonArrayToStringSet(state.get("firstFactors")),
                    stringArrayToStringSet(tenantConfig.firstFactors));
            assertEquals(jsonArrayToStringSet(state.get("requiredSecondaryFactors")),
                    stringArrayToStringSet(tenantConfig.requiredSecondaryFactors));
        }
    }

    private Set<String> jsonArrayToStringSet(JsonElement firstFactors) {
        if (firstFactors == null || !firstFactors.isJsonArray()) {
            return null;
        }
        Set<String> result = new HashSet<>();
        for (JsonElement e : firstFactors.getAsJsonArray()) {
            result.add(e.getAsString());
        }
        return result;
    }

    private Set<String> stringArrayToStringSet(String[] firstFactors) {
        if (firstFactors == null) {
            return null;
        }
        Set<String> result = new HashSet<>();
        for (String e : firstFactors) {
            result.add(e);
        }
        return result;
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

    private static JsonObject createOrUpdateApp_3_0(Main main, TenantIdentifier sourceTenant, String appId,
                                                    JsonObject requestBody) throws HttpResponseException, IOException {
        requestBody.addProperty("appId", appId);

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

    private static JsonObject createOrUpdateApp_5_0(Main main, TenantIdentifier sourceTenant, String appId,
                                                    JsonObject requestBody) throws HttpResponseException, IOException {
        requestBody.addProperty("appId", appId);

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

    private static JsonObject createOrUpdateApp_5_1(Main main, TenantIdentifier sourceTenant, String appId,
                                                    JsonObject requestBody) throws HttpResponseException, IOException {
        requestBody.addProperty("appId", appId);

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

    private static JsonObject deleteTenant(TenantIdentifier sourceTenant, String tenantId, Main main)
            throws HttpResponseException, IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("tenantId", tenantId);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant/remove"),
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

    private static JsonObject createOrUpdateTenant_3_0(Main main, TenantIdentifier sourceTenant, String tenantId,
                                                       JsonObject requestBody)
            throws HttpResponseException, IOException {
        requestBody.addProperty("tenantId", tenantId);

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

    private static JsonObject createOrUpdateTenant_5_0(Main main, TenantIdentifier sourceTenant, String tenantId,
                                                       JsonObject requestBody)
            throws HttpResponseException, IOException {
        requestBody.addProperty("tenantId", tenantId);

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

    private static JsonObject createOrUpdateTenant_5_1(Main main, TenantIdentifier sourceTenant, String tenantId,
                                                       JsonObject requestBody)
            throws HttpResponseException, IOException {
        requestBody.addProperty("tenantId", tenantId);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                HttpRequestForTesting.getMultitenantUrl(sourceTenant, "/recipe/multitenancy/tenant/v2"),
                requestBody, 1000, 2500, null,
                SemVer.v5_1.get(), "multitenancy");

        assertEquals("OK", response.getAsJsonPrimitive("status").getAsString());
        return response;
    }

    private static class CrossVersionTestCase {
        CrossVersionTestCaseStep[] steps;

        public CrossVersionTestCase(CrossVersionTestCaseStep[] steps) {
            this.steps = steps;
        }

        public void perform(Main main) throws HttpResponseException, IOException {
            for (CrossVersionTestCaseStep step : steps) {
                step.perform(main);
            }
        }
    }

    private static class CrossVersionTestCaseStep {
        private static enum OperationType {
            CREATE_APP, CREATE_TENANT, UPDATE_APP, UPDATE_TENANT
        }

        SemVer version;
        OperationType operation;
        JsonObject body;

        public CrossVersionTestCaseStep(SemVer version, OperationType operation, JsonObject body) {
            this.version = version;
            this.operation = operation;
            this.body = body;
        }

        public void perform(Main main) throws HttpResponseException, IOException {
            if (version.equals(SemVer.v3_0)) {
                if (operation == OperationType.CREATE_APP) {
                    deleteApp(TenantIdentifier.BASE_TENANT, "a1", main);
                    createOrUpdateApp_3_0(main, TenantIdentifier.BASE_TENANT, "a1", body);
                } else if (operation == OperationType.CREATE_TENANT) {
                    deleteTenant(TenantIdentifier.BASE_TENANT, "t1", main);
                    createOrUpdateTenant_3_0(main, TenantIdentifier.BASE_TENANT, "t1", body);
                } else if (operation == OperationType.UPDATE_APP) {
                    createOrUpdateApp_3_0(main, TenantIdentifier.BASE_TENANT, "a1", body);
                } else if (operation == OperationType.UPDATE_TENANT) {
                    createOrUpdateTenant_3_0(main, TenantIdentifier.BASE_TENANT, "t1", body);
                } else {
                    throw new RuntimeException("Should never come here");
                }
            }
        }
    }
}
