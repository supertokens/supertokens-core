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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    public void testClearAllResourcesWithKeyWorksCorrectly() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
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

    /**
     * Concurrent reads (getResource iterates keySet) and writes (clearAllResourcesWithResourceKey)
     * must not throw ConcurrentModificationException. This would have failed reliably with the
     * original HashMap — the fix is switching to ConcurrentHashMap.
     */
    @Test
    public void testConcurrentReadWriteNoConcurrentModificationException() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        ResourceDistributor rd = process.getProcess().getResourceDistributor();
        int tenantCount = 50;

        for (int i = 0; i < tenantCount; i++) {
            rd.setResource(new TenantIdentifier(null, null, "t" + i), ResourceA.RESOURCE_ID, new ResourceA());
        }

        AtomicBoolean failed = new AtomicBoolean(false);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean running = new AtomicBoolean(true);

        // Reader threads continuously walk keySet() via getResource
        int readerCount = 10;
        ExecutorService readers = Executors.newFixedThreadPool(readerCount);
        for (int i = 0; i < readerCount; i++) {
            final int idx = i;
            readers.submit(() -> {
                while (running.get()) {
                    try {
                        rd.getResource(new TenantIdentifier(null, null, "t" + (idx % tenantCount)),
                                ResourceA.RESOURCE_ID);
                    } catch (TenantOrAppNotFoundException ignored) {
                        // acceptable — resource may be transiently absent during clear
                    } catch (Throwable t) {
                        error.set(t);
                        failed.set(true);
                        running.set(false);
                    }
                }
            });
        }

        // Writer repeatedly clears and repopulates
        for (int round = 0; round < 200 && !failed.get(); round++) {
            rd.clearAllResourcesWithResourceKey(ResourceA.RESOURCE_ID);
            for (int i = 0; i < tenantCount; i++) {
                rd.setResource(new TenantIdentifier(null, null, "t" + i), ResourceA.RESOURCE_ID, new ResourceA());
            }
        }

        running.set(false);
        readers.shutdown();
        assertTrue(readers.awaitTermination(30, TimeUnit.SECONDS));

        if (failed.get()) {
            throw new AssertionError("Unexpected exception in reader thread", error.get());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * When many threads race to setResource for the same key, exactly one instance wins and
     * every caller — including the losers — gets that same winning instance back.
     * This would have been a check-then-act race with the original get+put; the fix is putIfAbsent.
     */
    @Test
    public void testSetResourceIsAtomicUnderConcurrency() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        ResourceDistributor rd = process.getProcess().getResourceDistributor();
        TenantIdentifier tenant = new TenantIdentifier(null, null, null);

        int threadCount = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        // Collect the instance each thread got back
        ConcurrentLinkedQueue<ResourceDistributor.SingletonResource> results = new ConcurrentLinkedQueue<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    results.add(rd.setResource(tenant, ResourceA.RESOURCE_ID, new ResourceA()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startLatch.countDown(); // release all threads simultaneously
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(threadCount, results.size());
        // All threads must have received the identical winning instance
        ResourceDistributor.SingletonResource first = results.peek();
        for (ResourceDistributor.SingletonResource r : results) {
            assertSame(first, r);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Tenants that are present before and after a replaceResourcesWithResourceKey call must
     * never be invisible to a concurrent reader. The old clear-then-repopulate pattern had a
     * window where the map was empty; replaceResourcesWithResourceKey eliminates it by writing
     * new entries before removing stale ones.
     */
    @Test
    public void testReplaceResourcesWithResourceKeyHasNoReaderGap() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        ResourceDistributor rd = process.getProcess().getResourceDistributor();
        int tenantCount = 20;

        for (int i = 0; i < tenantCount; i++) {
            rd.setResource(new TenantIdentifier(null, null, "t" + i), ResourceA.RESOURCE_ID, new ResourceA());
        }

        AtomicBoolean gapDetected = new AtomicBoolean(false);
        AtomicBoolean running = new AtomicBoolean(true);

        // Readers must never get TenantOrAppNotFoundException — the tenants are always in the new set
        int readerCount = 8;
        ExecutorService readers = Executors.newFixedThreadPool(readerCount);
        for (int i = 0; i < readerCount; i++) {
            final int tenantIdx = i % tenantCount;
            readers.submit(() -> {
                while (running.get()) {
                    try {
                        rd.getResource(new TenantIdentifier(null, null, "t" + tenantIdx), ResourceA.RESOURCE_ID);
                    } catch (TenantOrAppNotFoundException e) {
                        gapDetected.set(true);
                        running.set(false);
                    }
                }
            });
        }

        // Writer repeatedly replaces with the same set of tenants (new instances, same keys)
        for (int round = 0; round < 500 && !gapDetected.get(); round++) {
            Map<ResourceDistributor.KeyClass, ResourceDistributor.SingletonResource> newResources = new HashMap<>();
            for (int i = 0; i < tenantCount; i++) {
                TenantIdentifier t = new TenantIdentifier(null, null, "t" + i);
                newResources.put(new ResourceDistributor.KeyClass(t, ResourceA.RESOURCE_ID), new ResourceA());
            }
            rd.replaceResourcesWithResourceKey(ResourceA.RESOURCE_ID, newResources);
        }

        running.set(false);
        readers.shutdown();
        assertTrue(readers.awaitTermination(30, TimeUnit.SECONDS));

        assertFalse("Reader observed a gap (TenantOrAppNotFoundException) during replaceResourcesWithResourceKey",
                gapDetected.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
