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
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.signingkeys.AccessTokenSigningKey;
import io.supertokens.signingkeys.SigningKeys.KeyInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for the duplicate dynamic access token signing key race.
 *
 * <p>When several cores sit behind a load balancer and the dynamic access token signing key comes
 * up for rotation, each core independently runs
 * {@link AccessTokenSigningKey#getOrCreateAndGetSigningKeys()}. That method does a check-then-act
 * ({@code SELECT ... FOR UPDATE}, then {@code INSERT} when no key is fresh enough to sign with) at
 * {@code READ COMMITTED}. {@code FOR UPDATE} only locks rows that already exist, and under
 * {@code READ COMMITTED} a blocked {@code SELECT} re-reads only the rows it was blocked on — not
 * rows another transaction inserted meanwhile. So every core that piles up on the lock still
 * concludes that no fresh key exists and inserts one of its own. The primary key contains
 * {@code created_at_time}, so the inserts land on different milliseconds and all commit silently:
 * no deadlock, no constraint violation, nothing for the {@code startTransaction} retry wrapper to
 * catch.</p>
 *
 * <p>Each core then signs access tokens with its own key while serving a JWKS that only advertises
 * that key, so a consumer validating against the JWKS of one core rejects tokens minted by another
 * (Envoy reports {@code Jwks_doesn't_have_key_to_match_kid_or_alg_from_Jwt}) until the caches
 * happen to reconverge.</p>
 *
 * <p>This test is deliberately NOT wrapped in {@link Utils#retryFlakyTest()}: it must FAIL while
 * the bug is present and pass only once key creation is serialised per app.</p>
 */
public class AccessTokenSigningKeyRaceConditionTest {

    /** Number of cores that rotate the key at the same moment. */
    private static final int CORE_INSTANCES = 3;

    /** How many times the rotation is raced. Each round re-seeds a single stale key. */
    private static final int ROUNDS = 3;

    /** Print on failure but do NOT retry — retrying would hide the bug. */
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
    public void testConcurrentRotationCreatesExactlyOneNewDynamicSigningKey() throws Exception {
        // ~1 second rotation interval, so the key seeded before each round is always due for rotation.
        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "0.00027");

        String[] args = {"../"};

        List<TestingProcess> cores = new ArrayList<>();
        TestingProcess firstCore = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(firstCore.checkOrWaitForEvent(PROCESS_STATE.STARTED));
        cores.add(firstCore);

        if (StorageLayer.getStorage(firstCore.getProcess()).getType() != STORAGE_TYPE.SQL
                // the in-memory (SQLite) storage serialises writes per app on a single connection, so
                // multiple cores cannot interleave the way they do against a row-locking database
                || StorageLayer.isInMemDb(firstCore.getProcess())) {
            firstCore.kill();
            return;
        }

        // every core points at the same database, exactly like several cores behind a load balancer
        for (int i = 1; i < CORE_INSTANCES; i++) {
            TestingProcess core = TestingProcessManager.startIsolatedProcess(args);
            assertNotNull(core.checkOrWaitForEvent(PROCESS_STATE.STARTED));
            cores.add(core);
        }

        AppIdentifier app = firstCore.getAppForTesting().toAppIdentifier();
        SessionSQLStorage storage = (SessionSQLStorage) StorageLayer.getStorage(firstCore.getProcess());

        for (int round = 1; round <= ROUNDS; round++) {
            seedSingleStaleSigningKey(storage, app);

            List<String> errors = Collections.synchronizedList(new ArrayList<>());
            List<String> latestKeyIdPerCore = Collections.synchronizedList(new ArrayList<>());

            ExecutorService executor = Executors.newFixedThreadPool(cores.size());
            CountDownLatch ready = new CountDownLatch(cores.size()); // cores signal when they are poised
            CountDownLatch start = new CountDownLatch(1);            // the test fires the starting gun

            for (TestingProcess core : cores) {
                Main main = core.getProcess();
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        List<KeyInfo> keys = AccessTokenSigningKey.getInstance(main).getOrCreateAndGetSigningKeys();
                        // keys are sorted newest first, so this is the key the core will sign with
                        latestKeyIdPerCore.add(keys.get(0).id);
                    } catch (Exception e) {
                        errors.add(e.toString());
                    }
                });
            }

            ready.await();
            start.countDown();

            executor.shutdown();
            assertTrue("Round " + round + ": timed out waiting for the concurrent rotation",
                    executor.awaitTermination(2, TimeUnit.MINUTES));
            assertEquals("Round " + round + ": rotating the signing key must not throw: " + errors,
                    0, errors.size());

            KeyValueInfo[] keysInDb = readSigningKeys(storage, app);
            assertEquals("Round " + round + ": " + cores.size() + " cores rotated the dynamic access token signing"
                            + " key at the same time and each created a key of its own. Expected the seeded stale key"
                            + " plus exactly one new key, but the table holds " + keysInDb.length + " keys created at "
                            + createdAtTimes(keysInDb),
                    2, keysInDb.length);

            assertEquals("Round " + round + ": every core must sign with the same key after a rotation, but they"
                            + " ended up on different ones: " + latestKeyIdPerCore,
                    1, new HashSet<>(latestKeyIdPerCore).size());
        }

        for (TestingProcess core : cores) {
            core.kill();
            assertNotNull(core.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    /**
     * Leaves the app with exactly one signing key, old enough that it can still verify tokens but no
     * longer sign them — i.e. the state every core sees when a rotation is due.
     */
    private static void seedSingleStaleSigningKey(SessionSQLStorage storage, AppIdentifier app) throws Exception {
        storage.removeAccessTokenSigningKeysBefore(app, System.currentTimeMillis() + 1000);

        String staleKeyValue = io.supertokens.utils.Utils.generateNewPubPriKey().toString();
        long staleKeyCreatedAt = System.currentTimeMillis() - 5000;

        storage.startTransaction(con -> {
            storage.addAccessTokenSigningKey_Transaction(app, con,
                    new KeyValueInfo(staleKeyValue, staleKeyCreatedAt));
            storage.commitTransaction(con);
            return null;
        });
    }

    private static KeyValueInfo[] readSigningKeys(SessionSQLStorage storage, AppIdentifier app) throws Exception {
        return storage.startTransaction(con -> storage.getAccessTokenSigningKeys_Transaction(app, con));
    }

    private static String createdAtTimes(KeyValueInfo[] keys) {
        List<Long> times = new ArrayList<>();
        for (KeyValueInfo key : keys) {
            times.add(key.createdAtTime);
        }
        return times.stream().sorted().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
