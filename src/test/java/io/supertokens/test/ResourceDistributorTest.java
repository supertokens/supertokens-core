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

package io.supertokens.test;

import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class ResourceDistributorTest {

    private static class ResourceA extends ResourceDistributor.SingletonResource {
        private static final String RESOURCE_ID = "io.supertokens.test.ResourceDistributorTest.ResourceA";

        public ResourceA() {
        }
    }

    private static class ResourceB extends ResourceDistributor.SingletonResource {
        private static final String RESOURCE_ID = "io.supertokens.test.ResourceDistributorTest.ResourceB";

        public ResourceB() {
        }
    }

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
    public void testClearAllResourcesWithKeyWorksCorrectly() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        AppIdentifier a1 = new AppIdentifier(null, "a1");
        TenantIdentifier t1 = new TenantIdentifier(null, "a1", "t1");

        process.getProcess().getResourceDistributor().setResource(a1, ResourceA.RESOURCE_ID, new ResourceA());
        process.getProcess().getResourceDistributor().setResource(t1, ResourceA.RESOURCE_ID, new ResourceA());

        process.getProcess().getResourceDistributor().setResource(a1, ResourceB.RESOURCE_ID, new ResourceB());
        process.getProcess().getResourceDistributor().setResource(t1, ResourceB.RESOURCE_ID, new ResourceB());

        assertTrue(process.getProcess().getResourceDistributor()
                .getResource(a1, ResourceA.RESOURCE_ID) instanceof ResourceA);
        assertTrue(process.getProcess().getResourceDistributor()
                .getResource(t1, ResourceA.RESOURCE_ID) instanceof ResourceA);

        process.getProcess().getResourceDistributor().clearAllResourcesWithResourceKey(ResourceA.RESOURCE_ID);

        try {
            process.getProcess().getResourceDistributor().getResource(a1, ResourceA.RESOURCE_ID);
            fail();
        } catch (TenantOrAppNotFoundException e) {
            // ignored
        }
        try {
            process.getProcess().getResourceDistributor().getResource(t1, ResourceA.RESOURCE_ID);
            fail();
        } catch (TenantOrAppNotFoundException e) {
            // ignored
        }

        assertTrue(process.getProcess().getResourceDistributor()
                .getResource(a1, ResourceB.RESOURCE_ID) instanceof ResourceB);
        assertTrue(process.getProcess().getResourceDistributor()
                .getResource(t1, ResourceB.RESOURCE_ID) instanceof ResourceB);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }
}
