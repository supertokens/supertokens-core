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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class TestPermissionChecks {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testThatOnlyDefaultConnectionURIAppAndTenantIsAllowedToGetAllTenants() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        {
            JsonObject coreConfig = new JsonObject();

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

            TestMultitenancyAPIHelper.createConnectionUriDomain(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "127.0.0.1:3567", true, true, true,
                    coreConfig);
        }

        try {
            TestMultitenancyAPIHelper.listConnectionUriDomains(new TenantIdentifier("127.0.0.1:3567", null, null), process.getProcess());
            fail();
        } catch (HttpResponseException e) {
            assertEquals(
                    "Http error. Status Code: 403. Message: " +
                            "io.supertokens.multitenancy.exception.BadPermissionException: Only the public tenantId," +
                            " public appId and default connectionUriDomain is allowed to list all" +
                            " connectionUriDomains and appIds associated with this core",
                    e.getMessage());
        }

        {
            JsonObject coreConfig = new JsonObject();

            TestMultitenancyAPIHelper.createApp(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "a1", true, true, true,
                    coreConfig);
        }

        try {
            TestMultitenancyAPIHelper.listConnectionUriDomains(new TenantIdentifier(null, "a1", null),
                    process.getProcess());
            fail();
        } catch (HttpResponseException e) {
            assertEquals(
                    "Http error. Status Code: 403. Message: " +
                            "io.supertokens.multitenancy.exception.BadPermissionException: Only the public tenantId," +
                            " public appId and default connectionUriDomain is allowed to list all" +
                            " connectionUriDomains and appIds associated with this core",
                    e.getMessage());
        }

        {
            JsonObject coreConfig = new JsonObject();

            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    coreConfig);
        }

        try {
            TestMultitenancyAPIHelper.listConnectionUriDomains(new TenantIdentifier(null, null, "t1"),
                    process.getProcess());
            fail();
        } catch (HttpResponseException e) {
            assertEquals(
                    "Http error. Status Code: 403. Message: " +
                            "io.supertokens.multitenancy.exception.BadPermissionException: Only the public tenantId," +
                            " public appId and default connectionUriDomain is allowed to list all" +
                            " connectionUriDomains and appIds associated with this core",
                    e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreateConnectionUriDomainFromDifferentCUDAppOrTenantDoesNotWork() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        {
            JsonObject coreConfig = new JsonObject();

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

            TestMultitenancyAPIHelper.createConnectionUriDomain(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "127.0.0.1:3567", true, true, true,
                    coreConfig);
        }

        {
            JsonObject coreConfig = new JsonObject();

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

            TestMultitenancyAPIHelper.createConnectionUriDomain(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "127.0.0.1:3567", true, true, true,
                    coreConfig);
        }

        try {
            TestMultitenancyAPIHelper.createConnectionUriDomain(
                    process.getProcess(),
                    new TenantIdentifier("127.0.0.1:3567", null, null),
                    "localhost.org:3567",
                    true, true, true, new JsonObject()
                    );
            fail();
        } catch (HttpResponseException e) {
            assertEquals(
                    "Http error. Status Code: 403. Message: " +
                            "io.supertokens.multitenancy.exception.BadPermissionException: You must use the base" +
                            " tenant to create/update/delete a connectionUriDomain",
                    e.getMessage());
        }

        {
            JsonObject coreConfig = new JsonObject();

            TestMultitenancyAPIHelper.createApp(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "a1", true, true, true,
                    coreConfig);
        }

        try {
            TestMultitenancyAPIHelper.createConnectionUriDomain(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    "localhost.org:3567",
                    true, true, true, new JsonObject()
            );
            fail();
        } catch (HttpResponseException e) {
            assertEquals(
                    "Http error. Status Code: 403. Message: " +
                            "io.supertokens.multitenancy.exception.BadPermissionException: You must use the base" +
                            " tenant to create/update/delete a connectionUriDomain",
                    e.getMessage());
        }

    }
}
