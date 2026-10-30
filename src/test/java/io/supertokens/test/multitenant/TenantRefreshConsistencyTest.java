/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.multitenancy.MultitenancyHelper;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * The tenant refresh must publish the CUD set atomically. (This line has no fast-path refresh after a known tenant
 * change: every refresh reloads the full tenant list from the DB, so the refresh used here is the full one.)
 */
public class TenantRefreshConsistencyTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    private static TenantConfig tenantConfig(TenantIdentifier tenantIdentifier, JsonObject coreConfig) {
        return new TenantConfig(tenantIdentifier, new EmailPasswordConfig(true), new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true), null, null, coreConfig);
    }

    private TestingProcessManager.TestingProcess startProcess() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        return process;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> getPublishedCUDSet(MultitenancyHelper helper) throws Exception {
        Field field = MultitenancyHelper.class.getDeclaredField("dangerous_allCUDsFromDb");
        field.setAccessible(true);
        return (Set<String>) field.get(helper);
    }

    @Test
    public void testIssue10_cudSetSwappedAtomically() throws Exception {
        TestingProcessManager.TestingProcess process = startProcess();
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject cudConfig = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(cudConfig, 2);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(),
                tenantConfig(new TenantIdentifier("127.0.0.1", null, null), cudConfig), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(),
                tenantConfig(new TenantIdentifier("127.0.0.1", "a1", null), new JsonObject()), false);
        for (int i = 0; i < 5; i++) {
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(),
                    tenantConfig(new TenantIdentifier(null, null, "t" + i), new JsonObject()), false);
        }

        MultitenancyHelper helper = MultitenancyHelper.getInstance(process.getProcess());
        assertTrue(helper.isConnectionUriDomainPresentInDb("127.0.0.1"));

        // A published set must never be modified in place: a refresh publishes a new one.
        Set<String> before = getPublishedCUDSet(helper);
        Set<String> beforeContents = new HashSet<>(before);
        helper.refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(true);
        Set<String> after = getPublishedCUDSet(helper);
        assertNotSame(before, after);
        assertEquals(beforeContents, before);
        assertEquals(beforeContents, after);

        // A concurrent reader never sees the CUD missing while refreshes run.
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicLong misses = new AtomicLong(0);
        AtomicLong reads = new AtomicLong(0);
        Thread reader = new Thread(() -> {
            while (!stop.get()) {
                if (!helper.isConnectionUriDomainPresentInDb("127.0.0.1")) {
                    misses.incrementAndGet();
                }
                reads.incrementAndGet();
            }
        });
        reader.start();
        try {
            for (int i = 0; i < 300; i++) {
                helper.refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(true);
            }
        } finally {
            stop.set(true);
            reader.join(10000);
        }
        assertTrue(reads.get() > 0);
        assertEquals("the CUD was missing from the set for " + misses.get() + " of " + reads.get() + " reads", 0,
                misses.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
