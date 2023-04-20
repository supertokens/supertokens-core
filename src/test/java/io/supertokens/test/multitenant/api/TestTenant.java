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
        newConfig.addProperty("foo", "bar");
        coreConfig.addProperty("foo", "bar");

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
        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);
        coreConfig.addProperty("foo", "bar");

        // Create
        TestMultitenancyAPIHelper.createTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "t1", true, true, true,
                coreConfig);

        JsonObject newConfig = new JsonObject();
        newConfig.add("foo", null);
        coreConfig.remove("foo"); // for verification

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

        JsonObject response = TestMultitenancyAPIHelper.deleteTenant(new TenantIdentifier(null, null, null), process.getProcess(), "t1");
        assertTrue(response.get("didExist").getAsBoolean());

        result = TestMultitenancyAPIHelper.listTenants(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("tenants"));
        assertEquals(1, result.get("tenants").getAsJsonArray().size());

        response = TestMultitenancyAPIHelper.deleteTenant(new TenantIdentifier(null, null, null), process.getProcess(), "t1");
        assertFalse(response.get("didExist").getAsBoolean());
    }
}
