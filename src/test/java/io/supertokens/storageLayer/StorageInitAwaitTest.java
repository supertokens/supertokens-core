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
import io.supertokens.config.Config;
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
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
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
 * Unit tests for {@code StorageLayer.initStoragesInParallel} and the helpers behind it: the executor it runs on
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

    /** Start times for {@code count} futures whose tasks are all running already. */
    private static AtomicLongArray startedNow(int count) {
        AtomicLongArray startedAtNanos = new AtomicLongArray(count);
        for (int i = 0; i < count; i++) {
            startedAtNanos.set(i, System.nanoTime());
        }
        return startedAtNanos;
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
    public void executorIsABoundedPoolOfDaemonPlatformThreads() throws Exception {
        // a fixed pool spawns one worker per submitted task up to its size, so 69 tasks on a pool for 69 storages
        // show its bound
        Set<Thread> threads = threadsUsedBy(StorageLayer.newStorageInitExecutor(69, 16), 69);
        assertEquals(16, threads.size());
        for (Thread t : threads) {
            // platform threads: a virtual thread parked inside a synchronized section (the plugins' pool init)
            // pins its carrier, which is what hung boot with the previous virtual-thread executor
            assertFalse("storage init must not run on virtual threads", t.isVirtual());
            assertTrue("storage init threads must be daemon so a straggler never keeps the JVM alive",
                    t.isDaemon());
            assertTrue(t.getName(), t.getName().startsWith("storage-init-"));
        }

        // fewer storages than the bound: no idle threads
        assertEquals(3, threadsUsedBy(StorageLayer.newStorageInitExecutor(3, 16), 10).size());
        // degenerate input never yields an unusable executor
        assertEquals(1, threadsUsedBy(StorageLayer.newStorageInitExecutor(0, 16), 2).size());
    }

    @Test
    public void addingAThreadStartsTheNextQueuedTask() throws Exception {
        ThreadPoolExecutor executor = StorageLayer.newStorageInitExecutor(2, 16);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger started = new AtomicInteger();
        CountDownLatch thirdStarted = new CountDownLatch(1);
        try {
            for (int i = 0; i < 3; i++) {
                executor.execute(() -> {
                    if (started.incrementAndGet() == 3) {
                        thirdStarted.countDown();
                    }
                    try {
                        release.await();
                    } catch (InterruptedException ignored) {
                    }
                });
            }
            // both threads are taken by blocked tasks, so the third stays queued
            assertFalse(thirdStarted.await(500, TimeUnit.MILLISECONDS));

            StorageLayer.addStorageInitThread(executor);
            assertTrue(thirdStarted.await(10, TimeUnit.SECONDS));
            assertEquals(3, executor.getMaximumPoolSize());
        } finally {
            release.countDown();
            executor.shutdown();
        }
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
        assertTrue(StorageLayer.awaitStorageInit(process.getProcess(), futures, tenantsPerFuture,
                startedNow(futures.size()), () -> {}, 5, 10));
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

        // progress report every second, give up after two: two progress ticks, the second past the straggler's
        // own time limit
        AtomicInteger stragglersReported = new AtomicInteger();
        long start = System.nanoTime();
        assertFalse(StorageLayer.awaitStorageInit(process.getProcess(), futures, tenantsPerFuture,
                startedNow(futures.size()), stragglersReported::incrementAndGet, 1, 2));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue("gave up too early: " + elapsedMillis + "ms", elapsedMillis >= 2000);
        assertTrue("gave up too late: " + elapsedMillis + "ms", elapsedMillis < 10000);

        // boot continues, the state is recorded for tests/monitoring, and the straggler is left alone: neither
        // cancelled nor completed on its behalf, so a storage that finishes late is still fully usable
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STORAGE_INIT_TIMED_OUT));
        assertFalse(straggler.isDone());
        assertFalse(straggler.isCancelled());
        // reported once, so the caller adds exactly one thread for it
        assertEquals(1, stragglersReported.get());

        straggler.complete(null);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void awaitKeepsWaitingWhileAStorageHasNotStartedAndTimesEachStorageFromItsOwnStart() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<List<TenantIdentifier>> tenantsPerFuture = new ArrayList<>();
        CompletableFuture<Void> stuck = new CompletableFuture<>();
        futures.add(stuck);
        tenantsPerFuture.add(tenants("stuck"));
        CompletableFuture<Void> queued = new CompletableFuture<>();
        futures.add(queued);
        tenantsPerFuture.add(tenants("queued"));
        AtomicLongArray startedAtNanos = startedNow(2);
        startedAtNanos.set(1, StorageLayer.STORAGE_INIT_NOT_STARTED);

        // the stuck storage passes its 1s limit on the first ticks, but the queued one has not started, so the
        // wait goes on
        AtomicInteger stragglersReported = new AtomicInteger();
        long start = System.nanoTime();
        CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(
                () -> StorageLayer.awaitStorageInit(process.getProcess(), futures, tenantsPerFuture, startedAtNanos,
                        stragglersReported::incrementAndGet, 1, 1));
        Thread.sleep(3500);
        assertFalse("must not continue boot while a storage has not started", result.isDone());
        assertEquals(1, stragglersReported.get());

        // the queued storage starts 3.5s in and gets its own 1s from there (a single deadline for all storages
        // would have given up on it before it started); it finishes within it
        startedAtNanos.set(1, System.nanoTime());
        Thread.sleep(500);
        queued.complete(null);
        assertFalse(result.get(10, TimeUnit.SECONDS));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 4000);
        assertEquals("only the stuck storage is a straggler", 1, stragglersReported.get());
        assertNotNull(ProcessState.getInstance(process.getProcess())
                .getLastEventByName(ProcessState.PROCESS_STATE.STORAGE_INIT_TIMED_OUT));
        assertFalse(stuck.isDone());

        stuck.complete(null);
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
            StorageLayer.awaitStorageInit(process.getProcess(), futures, tenantsPerFuture,
                    startedNow(futures.size()), () -> {}, 5, 10);
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
            StorageLayer.awaitStorageInit(process.getProcess(), futures, tenantsPerFuture,
                    startedNow(futures.size()), () -> {}, 1, 2);
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
    public void queuedStoragesAreNotGivenUpOnBeforeTheyStart() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // one storage more than the executor has threads. Each takes 1.5s against a 2s limit, so the last one
        // only starts 1.5s in and finishes 3s in, past a single 2s deadline for all storages.
        int storageCount = Config.getBaseConfig(process.getProcess()).getStorageInitParallelism() + 1;
        AtomicInteger finished = new AtomicInteger();
        Map<Storage, Set<TenantIdentifier>> storagesToInit = new HashMap<>();
        for (int i = 0; i < storageCount; i++) {
            Storage storage = mock(Storage.class);
            doAnswer(invocation -> {
                Thread.sleep(1500);
                finished.incrementAndGet();
                return null;
            }).when(storage).initStorage(anyBoolean(), any());
            storagesToInit.put(storage, new HashSet<>(tenants("t" + i)));
        }

        StorageLayer.initStoragesInParallel(process.getProcess(), storagesToInit, 1, 2);

        assertEquals(storageCount, finished.get());
        assertNull(ProcessState.getInstance(process.getProcess())
                .getLastEventByName(ProcessState.PROCESS_STATE.STORAGE_INIT_TIMED_OUT));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void wedgedStoragesDoNotKeepQueuedStoragesFromStarting() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // more storages than the executor has threads, all wedged in init: the first ones take every thread
        int storageCount = Config.getBaseConfig(process.getProcess()).getStorageInitParallelism() + 4;
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
            // each straggler reported frees room for a queued storage, so boot still continues, once every
            // storage has started and run out its own limit
            Future<?> boot = CompletableFuture.runAsync(
                    () -> StorageLayer.initStoragesInParallel(process.getProcess(), storagesToInit, 1, 1));
            boot.get(30, TimeUnit.SECONDS);

            assertEquals(storageCount, started.get());
            assertNotNull(ProcessState.getInstance(process.getProcess())
                    .getLastEventByName(ProcessState.PROCESS_STATE.STORAGE_INIT_TIMED_OUT));
        } finally {
            release.countDown();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void awaitReportsAStorageAtItsLimitEvenWhenTheProgressIntervalIsLonger() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<List<TenantIdentifier>> tenantsPerFuture = new ArrayList<>();
        CompletableFuture<Void> straggler = new CompletableFuture<>();
        futures.add(straggler);
        tenantsPerFuture.add(tenants("stuck"));

        // a 1s limit with the progress log only every 60s: the storage is given up on at its limit, not at the
        // first progress log
        long start = System.nanoTime();
        assertFalse(StorageLayer.awaitStorageInit(process.getProcess(), futures, tenantsPerFuture,
                startedNow(futures.size()), () -> {}, 60, 1));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue("gave up too early: " + elapsedMillis + "ms", elapsedMillis >= 1000);
        assertTrue("waited for the progress interval instead of the limit: " + elapsedMillis + "ms",
                elapsedMillis < 10000);
        assertNotNull(ProcessState.getInstance(process.getProcess())
                .getLastEventByName(ProcessState.PROCESS_STATE.STORAGE_INIT_TIMED_OUT));

        straggler.complete(null);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void timeoutIsReadFromStorageInitTimeoutSeconds() throws Exception {
        Utils.setValueInConfig("storage_init_timeout_seconds", "1");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        assertEquals(1, Config.getBaseConfig(process.getProcess()).getStorageInitTimeoutSeconds());

        CountDownLatch release = new CountDownLatch(1);
        Storage stuck = mock(Storage.class);
        doAnswer(invocation -> {
            release.await();
            return null;
        }).when(stuck).initStorage(anyBoolean(), any());
        Map<Storage, Set<TenantIdentifier>> storagesToInit = new HashMap<>();
        storagesToInit.put(stuck, new HashSet<>(tenants("stuck")));

        try {
            // the overload boot uses: the configured 1s limit, with the real 60s progress interval
            Future<?> boot = CompletableFuture.runAsync(
                    () -> StorageLayer.initStoragesInParallel(process.getProcess(), storagesToInit));
            boot.get(10, TimeUnit.SECONDS);
            assertNotNull(ProcessState.getInstance(process.getProcess())
                    .getLastEventByName(ProcessState.PROCESS_STATE.STORAGE_INIT_TIMED_OUT));
        } finally {
            release.countDown();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void storageInitTimeoutSecondsDefaultsTo120AndMustBePositive() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        assertEquals(120, Config.getBaseConfig(process.getProcess()).getStorageInitTimeoutSeconds());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        Utils.setValueInConfig("storage_init_timeout_seconds", "0");
        process = TestingProcessManager.startIsolatedProcess(args);
        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("Provided storage_init_timeout_seconds must be >= 1", e.exception.getCause().getMessage());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void parallelismIsReadFromStorageInitParallelism() throws Exception {
        Utils.setValueInConfig("storage_init_parallelism", "2");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        assertEquals(2, Config.getBaseConfig(process.getProcess()).getStorageInitParallelism());

        AtomicInteger started = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        Map<Storage, Set<TenantIdentifier>> storagesToInit = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            Storage storage = mock(Storage.class);
            doAnswer(invocation -> {
                started.incrementAndGet();
                release.await();
                return null;
            }).when(storage).initStorage(anyBoolean(), any());
            storagesToInit.put(storage, new HashSet<>(tenants("t" + i)));
        }

        try {
            // a limit far beyond the test, so no straggler adds a thread: only the configured 2 run
            Future<?> boot = CompletableFuture.runAsync(
                    () -> StorageLayer.initStoragesInParallel(process.getProcess(), storagesToInit, 1, 60));
            Thread.sleep(1000);
            assertEquals(2, started.get());

            release.countDown();
            boot.get(10, TimeUnit.SECONDS);
            assertEquals(3, started.get());
        } finally {
            release.countDown();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void storageInitParallelismDefaultsTo16AndMustBePositive() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        assertEquals(16, Config.getBaseConfig(process.getProcess()).getStorageInitParallelism());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        Utils.setValueInConfig("storage_init_parallelism", "0");
        process = TestingProcessManager.startIsolatedProcess(args);
        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("Provided storage_init_parallelism must be >= 1", e.exception.getCause().getMessage());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
