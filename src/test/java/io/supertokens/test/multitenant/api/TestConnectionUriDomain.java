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
import io.supertokens.multitenancy.exception.DeletionInProgressException;
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

public class TestConnectionUriDomain extends TestMultitenancyAPIHelper {
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
    public void beforeEach() throws InterruptedException, InvalidProviderConfigException, DeletionInProgressException,
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
    public void testCreateConnectionUriDomainWorks() throws Exception {
        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        this.createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", true, true, true,
                coreConfig);

        JsonObject result = this.listConnectionUriDomains(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("connectionUriDomains"));

        boolean found = false;
        for (JsonElement cud : result.get("connectionUriDomains").getAsJsonArray()) {
            JsonObject cudObj = cud.getAsJsonObject();
            if (cudObj.get("connectionUriDomain").getAsString().equals("127.0.0.1:3567")) {
                found = true;

                for (JsonElement app : cudObj.get("apps").getAsJsonArray()) {
                    JsonObject appObj = app.getAsJsonObject();

                    for (JsonElement tenant : appObj.get("tenants").getAsJsonArray()) {
                        JsonObject tenantObj = tenant.getAsJsonObject();
                        if (tenantObj.get("tenantId").getAsString().equals("1")) {
                            assertTrue(tenantObj.get("isEmailPasswordEnabled").getAsBoolean());
                            assertTrue(tenantObj.get("isThirdPartyEnabled").getAsBoolean());
                            assertTrue(tenantObj.get("isSessionEnabled").getAsBoolean());
                            assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
                        }
                    }
                }
            }
        }
        assertTrue(found);
    }

    @Test
    public void testUpdateConnectionUriDomainWorks() throws Exception {
        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        // Create
        this.createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", true, true, true,
                coreConfig);

        JsonObject newConfig = new JsonObject();
        newConfig.addProperty("foo", "bar");
        coreConfig.addProperty("foo", "bar");

        // Update
        this.createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", true, true, true,
                newConfig);

        JsonObject result = this.listConnectionUriDomains(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("connectionUriDomains"));

        boolean found = false;
        for (JsonElement cud : result.get("connectionUriDomains").getAsJsonArray()) {
            JsonObject cudObj = cud.getAsJsonObject();
            if (cudObj.get("connectionUriDomain").getAsString().equals("127.0.0.1:3567")) {
                found = true;

                for (JsonElement app : cudObj.get("apps").getAsJsonArray()) {
                    JsonObject appObj = app.getAsJsonObject();

                    for (JsonElement tenant : appObj.get("tenants").getAsJsonArray()) {
                        JsonObject tenantObj = tenant.getAsJsonObject();
                        if (tenantObj.get("tenantId").getAsString().equals("1")) {
                            assertTrue(tenantObj.get("isEmailPasswordEnabled").getAsBoolean());
                            assertTrue(tenantObj.get("isThirdPartyEnabled").getAsBoolean());
                            assertTrue(tenantObj.get("isSessionEnabled").getAsBoolean());
                            assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
                        }
                    }
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
        this.createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", true, true, true,
                coreConfig);

        JsonObject newConfig = new JsonObject();
        newConfig.add("foo", null);
        coreConfig.remove("foo"); // for verification

        // Update
        this.createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", true, true, true,
                newConfig);

        JsonObject result = this.listConnectionUriDomains(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("connectionUriDomains"));

        boolean found = false;
        for (JsonElement cud : result.get("connectionUriDomains").getAsJsonArray()) {
            JsonObject cudObj = cud.getAsJsonObject();
            if (cudObj.get("connectionUriDomain").getAsString().equals("127.0.0.1:3567")) {
                found = true;

                for (JsonElement app : cudObj.get("apps").getAsJsonArray()) {
                    JsonObject appObj = app.getAsJsonObject();

                    for (JsonElement tenant : appObj.get("tenants").getAsJsonArray()) {
                        JsonObject tenantObj = tenant.getAsJsonObject();
                        if (tenantObj.get("tenantId").getAsString().equals("1")) {
                            assertTrue(tenantObj.get("isEmailPasswordEnabled").getAsBoolean());
                            assertTrue(tenantObj.get("isThirdPartyEnabled").getAsBoolean());
                            assertTrue(tenantObj.get("isSessionEnabled").getAsBoolean());
                            assertEquals(coreConfig, tenantObj.get("coreConfig").getAsJsonObject());
                        }
                    }
                }
            }
        }
        assertTrue(found);
    }

    @Test
    public void testDeleteConnectionUriDomainWorks() throws Exception {
        JsonObject coreConfig = new JsonObject();

        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        this.createConnectionUriDomain(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                "127.0.0.1:3567", true, true, true,
                coreConfig);

        JsonObject result = this.listConnectionUriDomains(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("connectionUriDomains"));
        assertEquals(2, result.get("connectionUriDomains").getAsJsonArray().size());

        JsonObject response = this.deleteConnectionUriDomain(new TenantIdentifier(null, null, null), process.getProcess(), "127.0.0.1:3567");
        assertTrue(response.get("didExist").getAsBoolean());

        result = this.listConnectionUriDomains(new TenantIdentifier(null, null, null), process.getProcess());
        assertTrue(result.has("connectionUriDomains"));
        assertEquals(1, result.get("connectionUriDomains").getAsJsonArray().size());

        response = this.deleteConnectionUriDomain(new TenantIdentifier(null, null, null), process.getProcess(), "127.0.0.1:3567");
        assertFalse(response.get("didExist").getAsBoolean());
    }
}
