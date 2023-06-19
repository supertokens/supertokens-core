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

package io.supertokens.test.multitenant;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LoadTest {

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
    public void testCreating100TenantsAndCheckOnlyOneInstanceOfStorageLayerIsCreated()
            throws InterruptedException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TenantConfig[] tenants = new TenantConfig[1000];

        for (int i = 0; i < 100; i++) {
            final int insideLoop = i;
            JsonObject config = new JsonObject();
            tenants[insideLoop] = new TenantConfig(new TenantIdentifier(null, "a" + insideLoop, null),
                    new EmailPasswordConfig(false),
                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
                    new PasswordlessConfig(false),
                    config);
            try {
                Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantIdentifier(null, null, null),
                        tenants[insideLoop]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> map = process.getProcess()
                .getResourceDistributor().getAllResourcesWithResourceKey(StorageLayer.RESOURCE_KEY);
        Set<Storage> uniqueResources = new HashSet<>();
        for (ResourceDistributor.SingletonResource resource : map.values()) {
            StorageLayer storage = (StorageLayer) resource;
            if (uniqueResources.contains(storage.getUnderlyingStorage())) {
                continue;
            }
            uniqueResources.add(storage.getUnderlyingStorage());
        }
        assertEquals(uniqueResources.size(), 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

//    @Test
//    public void testCreating1000TenantsWithOneStorageUsage()
//            throws InterruptedException, InvalidProviderConfigException, DeletionInProgressException,
//            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
//            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException {
//        String[] args = {"../"};
//
//        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
//        FeatureFlagTestContent.getInstance(process.getProcess())
//                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
//        process.startProcess();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
//
//        TenantConfig[] tenants = new TenantConfig[1000];
//
//        for (int i = 0; i < 1000; i++) {
//            System.out.println(i);
//            final int insideLoop = i;
//            JsonObject config = new JsonObject();
//            tenants[insideLoop] = new TenantConfig(new TenantIdentifier(null, "a" + insideLoop, null),
//                    new EmailPasswordConfig(false),
//                    new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]),
//                    new PasswordlessConfig(false),
//                    config);
//            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantIdentifier(null, null, null),
//                    tenants[insideLoop]);
//        }
//
//        Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> map = process.getProcess()
//                .getResourceDistributor().getAllResourcesWithResourceKey(StorageLayer.RESOURCE_KEY);
//        Set<Storage> uniqueResources = new HashSet<>();
//        for (ResourceDistributor.SingletonResource resource : map.values()) {
//            StorageLayer storage = (StorageLayer) resource;
//            if (uniqueResources.contains(storage.getUnderlyingStorage())) {
//                continue;
//            }
//            uniqueResources.add(storage.getUnderlyingStorage());
//        }
//        assertEquals(uniqueResources.size(), 1);
//
//        // TODO: add recipe usage + RAM tests + optimise how these 1000 tenants are created
//
//        process.kill();
//        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
//    }
}
