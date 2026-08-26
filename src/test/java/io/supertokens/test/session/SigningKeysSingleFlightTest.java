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

package io.supertokens.test.session;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.*;

/**
 * Pins the single-flight / stale-while-revalidate behavior of the {@link SigningKeys} cache:
 *
 * <ul>
 * <li>During the rotation overlap window ("refresh due"), readers must keep serving the cached, still-valid
 * dynamic keys while one refresh is in flight - not queue behind the refreshing thread. The overlap config
 * exists precisely so old and new keys are simultaneously acceptable, which is what makes serving stale
 * correct.</li>
 * <li>Callers that cannot answer from a stale cache - a cold start with nothing cached - must wait for the
 * in-flight refresh's result.</li>
 * </ul>
 *
 * <p>The in-flight refresh is simulated by holding {@code SigningKeys.refreshLock} (the lock that grants the
 * right to refresh) from a helper thread, which is exactly the state the cache is in while another request
 * thread is at the database - including when that thread is stuck waiting on the storage's cross-instance
 * rotation lock.
 */
public class SigningKeysSingleFlightTest {

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

    private static ReentrantLock getRefreshLock(SigningKeys keys) throws Exception {
        Field field = SigningKeys.class.getDeclaredField("refreshLock");
        field.setAccessible(true);
        return (ReentrantLock) field.get(keys);
    }

    @SuppressWarnings("unchecked")
    private static List<SigningKeys.KeyInfo> getCachedDynamicKeys(SigningKeys keys) throws Exception {
        Field field = SigningKeys.class.getDeclaredField("dynamicKeys");
        field.setAccessible(true);
        return (List<SigningKeys.KeyInfo>) field.get(keys);
    }

    private static void setCachedDynamicKeys(SigningKeys keys, List<SigningKeys.KeyInfo> value) throws Exception {
        Field field = SigningKeys.class.getDeclaredField("dynamicKeys");
        field.setAccessible(true);
        field.set(keys, value);
    }

    /** Holds the given lock from a dedicated thread until {@code release} is counted down. */
    private static Future<?> holdLock(ExecutorService executor, ReentrantLock lock, CountDownLatch held,
                                      CountDownLatch release) {
        return executor.submit(() -> {
            lock.lock();
            try {
                held.countDown();
                assertTrue(release.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
            return null;
        });
    }

    @Test
    public void rotationWindowReadersServeStaleWhileRefreshIsInFlight() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SigningKeys signingKeys = SigningKeys.getInstance(main);
        // prime the cache, then age the cached key so the next read sees "refresh due" - while keeping it
        // valid for verification (expiryTime untouched), exactly the overlap-window state
        signingKeys.getDynamicKeys();
        List<SigningKeys.KeyInfo> cached = getCachedDynamicKeys(signingKeys);
        String agedKeyId = cached.get(0).id;
        cached.get(0).createdAtTime = System.currentTimeMillis()
                - Config.getConfig(main).getAccessTokenDynamicSigningKeyUpdateIntervalInMillis();

        ReentrantLock refreshLock = getRefreshLock(signingKeys);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            Future<?> holder = holdLock(executor, refreshLock, held, release);
            assertTrue(held.await(5, TimeUnit.SECONDS));

            // 8 concurrent readers in the rotation window: all must return promptly with the stale-but-valid
            // key, none may queue behind the in-flight refresh
            List<Future<List<SigningKeys.KeyInfo>>> readers = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                readers.add(executor.submit(signingKeys::getDynamicKeys));
            }
            for (Future<List<SigningKeys.KeyInfo>> reader : readers) {
                List<SigningKeys.KeyInfo> res = reader.get(2, TimeUnit.SECONDS); // times out if it queued
                assertFalse(res.isEmpty());
                assertEquals("readers must serve the cached key while the refresh is in flight",
                        agedKeyId, res.get(0).id);
            }

            release.countDown();
            holder.get(5, TimeUnit.SECONDS);

            // with the refresh slot free again, the next read refreshes from the DB (where the key's real
            // createdAtTime is current) instead of serving the aged snapshot forever
            List<SigningKeys.KeyInfo> afterRelease = signingKeys.getDynamicKeys();
            assertTrue("the next read after the in-flight refresh finishes must actually refresh",
                    afterRelease.get(0).createdAtTime > cached.get(0).createdAtTime);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void coldStartReadersWaitForTheRefreshResult() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();

        SigningKeys signingKeys = SigningKeys.getInstance(main);
        setCachedDynamicKeys(signingKeys, null); // cold start: nothing to serve stale

        ReentrantLock refreshLock = getRefreshLock(signingKeys);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = holdLock(executor, refreshLock, held, release);
            assertTrue(held.await(5, TimeUnit.SECONDS));

            Future<List<SigningKeys.KeyInfo>> reader = executor.submit(signingKeys::getDynamicKeys);
            Thread.sleep(1000);
            assertFalse("a cold-start reader has nothing to serve stale and must wait for the refresh",
                    reader.isDone());

            release.countDown();
            holder.get(5, TimeUnit.SECONDS);

            List<SigningKeys.KeyInfo> res = reader.get(10, TimeUnit.SECONDS);
            assertFalse("after the in-flight refresh finishes, the waiting reader gets the result",
                    res.isEmpty());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
