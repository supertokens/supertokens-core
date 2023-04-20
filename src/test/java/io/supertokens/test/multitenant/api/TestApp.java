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
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class TestApp {
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
        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        TestMultitenancyAPIHelper.createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        JsonObject result = TestMultitenancyAPIHelper.listApps(new TenantIdentifier(null, null, null), process.getProcess());
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
    public void testUpdateAppWorks() throws Exception {
        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        // Create
        TestMultitenancyAPIHelper.createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        JsonObject newConfig = new JsonObject();
        newConfig.addProperty("foo", "bar");
        coreConfig.addProperty("foo", "bar");

        // Update
        TestMultitenancyAPIHelper.createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                newConfig);

        JsonObject result = TestMultitenancyAPIHelper.listApps(new TenantIdentifier(null, null, null), process.getProcess());
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
    public void testUpdateWithNullValueDeletesTheSetting() throws Exception {
        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);
        coreConfig.addProperty("foo", "bar");

        // Create
        TestMultitenancyAPIHelper.createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        JsonObject newConfig = new JsonObject();
        newConfig.add("foo", null);
        coreConfig.remove("foo"); // for verification

        // Update
        TestMultitenancyAPIHelper.createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                newConfig);

        JsonObject result = TestMultitenancyAPIHelper.listApps(new TenantIdentifier(null, null, null), process.getProcess());
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
        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        TestMultitenancyAPIHelper.createApp(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "a1", true, true, true,
                coreConfig);

        JsonObject result = TestMultitenancyAPIHelper.listApps(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("apps"));
        assertEquals(2, result.get("apps").getAsJsonArray().size());

        JsonObject response = TestMultitenancyAPIHelper.deleteApp(new TenantIdentifier(null, null, null), process.getProcess(), "a1");
        assertTrue(response.get("didExist").getAsBoolean());

        result = TestMultitenancyAPIHelper.listApps(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("apps"));
        assertEquals(1, result.get("apps").getAsJsonArray().size());

        response = TestMultitenancyAPIHelper.deleteApp(new TenantIdentifier(null, null, null), process.getProcess(), "a1");
        assertFalse(response.get("didExist").getAsBoolean());
    }
}
