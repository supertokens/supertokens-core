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

package io.supertokens.test.multitenant;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.multitenant.api.TestMultitenancyAPIHelper;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class LoadOnlyCUDTest {
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
    public void testAPIChecksForLoadOnlyCUD() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject coreConfig = new JsonObject();
        StorageLayer.getStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        TestMultitenancyAPIHelper.createConnectionUriDomain(process.getProcess(), TenantIdentifier.BASE_TENANT,
                "127.0.0.1", true, true, true, coreConfig);
        StorageLayer.getStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(coreConfig, 2);

        TestMultitenancyAPIHelper.createConnectionUriDomain(process.getProcess(), TenantIdentifier.BASE_TENANT,
                "localhost", true, true, true, coreConfig);

        process.kill(false);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        Utils.setValueInConfig("supertokens_saas_load_only_cud", "127.0.0.1:3567");
        process = TestingProcessManager.start(args, false);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier("localhost", null, null), "test@example.com",
                    "password123", process.getProcess());
            fail();
        } catch (HttpResponseException e) {
            assertEquals(403, e.statusCode);
        }

        // check that it's allowed with 127.0.0.1
        TestMultitenancyAPIHelper.epSignUp(new TenantIdentifier("127.0.0.1", null, null), "test@example.com",
                "password123", process.getProcess());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreationOfCUDWithLoadOnlyCUD() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("supertokens_saas_load_only_cud", "127.0.0.1:3567");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            TestMultitenancyAPIHelper.createConnectionUriDomain(process.getProcess(), TenantIdentifier.BASE_TENANT,
                    "localhost:3567", true, true, true, new JsonObject());
            fail();
        } catch (HttpResponseException e) {
            assertEquals(400, e.statusCode);
        }

        JsonObject coreConfig = new JsonObject();
        StorageLayer.getStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        // This should pass
        TestMultitenancyAPIHelper.createConnectionUriDomain(process.getProcess(), TenantIdentifier.BASE_TENANT,
                "127.0.0.1:3567", true, true, true, coreConfig);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatResourcesAreNotLoadedWithLoadOnlyCUD() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject coreConfig = new JsonObject();
        StorageLayer.getStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        TestMultitenancyAPIHelper.createConnectionUriDomain(process.getProcess(), TenantIdentifier.BASE_TENANT,
                "127.0.0.1", true, true, true, coreConfig);
        StorageLayer.getStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(coreConfig, 2);

        TestMultitenancyAPIHelper.createConnectionUriDomain(process.getProcess(), TenantIdentifier.BASE_TENANT,
                "localhost.org", true, true, true, coreConfig);

        process.kill(false);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        Utils.setValueInConfig("supertokens_saas_load_only_cud", "127.0.0.1:3567");
        process = TestingProcessManager.start(args, false);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject result = TestMultitenancyAPIHelper.listConnectionUriDomains(TenantIdentifier.BASE_TENANT,
                process.getProcess());
        System.out.println(result);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}