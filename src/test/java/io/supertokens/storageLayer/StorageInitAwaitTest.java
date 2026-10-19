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

package io.supertokens.storageLayer;

import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the two helpers behind {@code StorageLayer.initStoragesInParallel}: the executor it runs on
 * and the bounded wait it uses. Lives in the {@code io.supertokens.storageLayer} package to reach the
 * package-private helpers.
 */
public class StorageInitAwaitTest {
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

    private static List<TenantIdentifier> tenants(String tenantId) {
        List<TenantIdentifier> tenants = new ArrayList<>();
        tenants.add(new TenantIdentifier(null, null, tenantId));
        return tenants;
    }

    /**
     * Runs {@code tasks} blocking tasks on the executor and returns the distinct threads they ran on.
     */
    private static Set<Thread> threadsUsedBy(ExecutorService executor, int tasks) throws InterruptedException {
        Set<Thread> seen = ConcurrentHashMap.newKeySet();
        CountDownLatch done = new CountDownLatch(tasks);
        for (int i = 0; i < tasks; i++) {
            executor.execute(() -> {
                seen.add(Thread.currentThread());
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ignored) {
                }
                done.countDown();
            });
        }
        assertTrue(done.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        return seen;
    }

    @Test
    public void executorIsAPoolOfDaemonPlatformThreadsWithOnePerStorage() throws Exception {
        // a fixed pool spawns one worker per submitted task up to its size, so with one thread per storage every
        // task starts right away and none is still queued when awaitStorageInit gives up
        Set<Thread> threads = threadsUsedBy(StorageLayer.newStorageInitExecutor(69), 69);
        assertEquals(69, threads.size());
        for (Thread t : threads) {
            // platform threads: a virtual thread parked inside a synchronized section (the plugins' pool init)
            // pins its carrier, which is what hung boot with the previous virtual-thread executor
            assertFalse("storage init must not run on virtual threads", t.isVirtual());
            assertTrue("storage init threads must be daemon so a straggler never keeps the JVM alive",
                    t.isDaemon());
            assertTrue(t.getName(), t.getName().startsWith("storage-init-"));
        }

        // the pool is sized to the storages
        assertEquals(3, threadsUsedBy(StorageLayer.newStorageInitExecutor(3), 10).size());
        // degenerate input never yields an unusable executor
        assertEquals(1, threadsUsedBy(StorageLayer.newStorageInitExecutor(0), 2).size());
    }

    @Test
    public void awaitReturnsTrueOnceEveryStorageFinished() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<List<TenantIdentifier>> tenantsPerFuture = new ArrayList<>();
        futures.add(CompletableFuture.completedFuture(null));
        tenantsPerFuture.add(tenants("t1"));
        CompletableFuture<Void> late = new CompletableFuture<>();
        futures.add(late);
        tenantsPerFuture.add(tenants("t2"));
        // completes well within the first progress interval
        CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS).execute(() -> late.complete(null));

        long start = System.nanoTime();
        assertTrue(StorageLayer.awaitStorageInit(process.getProcess(), futures, tenantsPerFuture, 5, 10));
        assertTrue("must return as soon as the futures finish, not after the progress interval",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 4000);
        assertNull(ProcessState.getInstance(process.getProcess())
                .getLastEventByName(ProcessState.PROCESS_STATE.STORAGE_INIT_TIMED_OUT));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void awaitGivesUpAfterTheTimeoutAndLeavesTheStragglerRunning() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<List<TenantIdentifier>> tenantsPerFuture = new ArrayList<>();
        futures.add(CompletableFuture.completedFuture(null));
        tenantsPerFuture.add(tenants("done"));
        CompletableFuture<Void> straggler = new CompletableFuture<>();
        futures.add(straggler);
        tenantsPerFuture.add(tenants("stuck"));

        // progress report every second, give up after two: two progress ticks, the second past the deadline
        long start = System.nanoTime();
        assertFalse(StorageLayer.awaitStorageInit(process.getProcess(), futures, tenantsPerFuture, 1, 2));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue("gave up too early: " + elapsedMillis + "ms", elapsedMillis >= 2000);
        assertTrue("gave up too late: " + elapsedMillis + "ms", elapsedMillis < 10000);

        // boot continues, the state is recorded for tests/monitoring, and the straggler is left alone: neither
        // cancelled nor completed on its behalf, so a storage that finishes late is still fully usable
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STORAGE_INIT_TIMED_OUT));
        assertFalse(straggler.isDone());
        assertFalse(straggler.isCancelled());

        straggler.complete(null);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void awaitRethrowsATaskFailureAsCompletionExceptionLikeJoinDid() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        IllegalStateException cause = new IllegalStateException("log file setup failed");
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<List<TenantIdentifier>> tenantsPerFuture = new ArrayList<>();
        futures.add(CompletableFuture.failedFuture(cause));
        tenantsPerFuture.add(tenants("t1"));

        try {
            StorageLayer.awaitStorageInit(process.getProcess(), futures, tenantsPerFuture, 5, 10);
            fail("an unexpected task failure must still crash startup");
        } catch (CompletionException e) {
            assertSame(cause, e.getCause());
        }
        assertNull(ProcessState.getInstance(process.getProcess())
                .getLastEventByName(ProcessState.PROCESS_STATE.STORAGE_INIT_TIMED_OUT));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void awaitRethrowsATaskFailureWhileAnotherStorageIsStillInitialising() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        IllegalStateException cause = new IllegalStateException("log file setup failed");
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<List<TenantIdentifier>> tenantsPerFuture = new ArrayList<>();
        futures.add(CompletableFuture.failedFuture(cause));
        tenantsPerFuture.add(tenants("failed"));
        CompletableFuture<Void> straggler = new CompletableFuture<>();
        futures.add(straggler);
        tenantsPerFuture.add(tenants("stuck"));

        // allOf never completes while the straggler runs, so the failure must surface on a progress tick instead
        // of being dropped when the timeout is hit
        try {
            StorageLayer.awaitStorageInit(process.getProcess(), futures, tenantsPerFuture, 1, 2);
            fail("a task failure must still crash startup while another storage is initialising");
        } catch (CompletionException e) {
            assertSame(cause, e.getCause());
        }
        assertNull(ProcessState.getInstance(process.getProcess())
                .getLastEventByName(ProcessState.PROCESS_STATE.STORAGE_INIT_TIMED_OUT));

        straggler.complete(null);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void baseStorageIsInitialisedOnTheCallingThreadAndNotSubjectToTheTimeout() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // the base storage takes longer than the whole timeout; a secondary storage never finishes
        AtomicReference<Thread> baseInitThread = new AtomicReference<>();
        AtomicInteger baseInitsFinished = new AtomicInteger();
        Storage base = mock(Storage.class);
        doAnswer(invocation -> {
            baseInitThread.set(Thread.currentThread());
            Thread.sleep(3000);
            baseInitsFinished.incrementAndGet();
            return null;
        }).when(base).initStorage(anyBoolean(), any());
        CountDownLatch release = new CountDownLatch(1);
        Storage stuck = mock(Storage.class);
        doAnswer(invocation -> {
            release.await();
            return null;
        }).when(stuck).initStorage(anyBoolean(), any());

        Map<Storage, Set<TenantIdentifier>> storagesToInit = new HashMap<>();
        Set<TenantIdentifier> baseTenants = new HashSet<>();
        baseTenants.add(TenantIdentifier.BASE_TENANT);
        baseTenants.add(new TenantIdentifier(null, null, "t1"));
        storagesToInit.put(base, baseTenants);
        storagesToInit.put(stuck, new HashSet<>(tenants("stuck")));

        try {
            StorageLayer.initStoragesInParallel(process.getProcess(), storagesToInit, 1, 1);

            // boot never continues past the timeout without the base storage
            assertSame(Thread.currentThread(), baseInitThread.get());
            assertEquals(1, baseInitsFinished.get());
            // the secondary storage was still given up on
            assertNotNull(ProcessState.getInstance(process.getProcess())
                    .getLastEventByName(ProcessState.PROCESS_STATE.STORAGE_INIT_TIMED_OUT));
        } finally {
            release.countDown();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void everyStorageHasStartedInitialisingWhenTheTimeoutIsHit() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // more storages than the 16 threads the executor used to be capped at, all stuck in init
        int storageCount = 20;
        AtomicInteger started = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        Map<Storage, Set<TenantIdentifier>> storagesToInit = new HashMap<>();
        for (int i = 0; i < storageCount; i++) {
            Storage storage = mock(Storage.class);
            doAnswer(invocation -> {
                started.incrementAndGet();
                release.await();
                return null;
            }).when(storage).initStorage(anyBoolean(), any());
            storagesToInit.put(storage, new HashSet<>(tenants("t" + i)));
        }

        try {
            StorageLayer.initStoragesInParallel(process.getProcess(), storagesToInit, 1, 2);

            // a storage whose task was still queued at the deadline would have no pool at all, and its queries
            // would fail instead of waiting in the plugin's pool-initialisation guard
            assertEquals(storageCount, started.get());
            assertNotNull(ProcessState.getInstance(process.getProcess())
                    .getLastEventByName(ProcessState.PROCESS_STATE.STORAGE_INIT_TIMED_OUT));
        } finally {
            release.countDown();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
