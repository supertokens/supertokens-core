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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertNotNull;

/*
 * Session refresh must never hold more than one DB connection at a time. It used to hold two:
 * the legacy Case-B promote recursed into refreshSessionHelper from inside the transaction lambda
 * (second connection while the first was still checked out), and token minting inside the lambda
 * could open a nested transaction for signing-key lookups (always, for static-key sessions). With
 * pool-size concurrent refreshes doing this, no thread could acquire its second connection and the
 * whole pool deadlocked until the connection timeout. These tests only bound the pool when running
 * against a real SQL plugin (the config keys are plugin-scoped); in-memory runs exercise the same
 * code paths without the pool limit.
 */
public class SessionRefreshPoolDeadlockTest {

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

    // Legacy (CDI <= 5.4) rotation: a refresh mints a child token but the DB keeps the parent as
    // current, so the FIRST use of every freshly rotated refresh token takes the Case-B promote
    // path. Returns such a token for a fresh session.
    private String makeCaseBToken(TestingProcessManager.TestingProcess process, String userId, boolean useStaticKey)
            throws Exception {
        SessionInformationHolder session = Session.createNewSession(process.getProcess(), userId,
                new JsonObject(), new JsonObject(), false, AccessToken.getLatestVersion(), useStaticKey);
        SessionInformationHolder refreshed = Session.refreshSession(process.getProcess(),
                session.refreshToken.token, session.antiCsrfToken, false, AccessToken.getLatestVersion());
        return refreshed.refreshToken.token;
    }

    // Production shape: pool of 3, three concurrent Case-B refreshes. Pre-fix each held one
    // connection while acquiring a second, so all three deadlocked and failed at the pool's
    // connection timeout.
    @Test
    public void concurrentCaseBRefreshesAtPoolSizeDoNotDeadlock() throws Exception {
        final int poolSize = 3;
        Utils.setValueInConfig("postgresql_connection_pool_size", String.valueOf(poolSize));

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String[] caseBTokens = new String[poolSize];
        for (int i = 0; i < poolSize; i++) {
            caseBTokens[i] = makeCaseBToken(process, "user" + i, false);
        }

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        try {
            CountDownLatch startSignal = new CountDownLatch(1);
            List<Future<SessionInformationHolder>> results = new ArrayList<>();
            for (String token : caseBTokens) {
                results.add(executor.submit(() -> {
                    startSignal.await();
                    return Session.refreshSession(process.getProcess(), token, null, false,
                            AccessToken.getLatestVersion());
                }));
            }
            startSignal.countDown();
            for (Future<SessionInformationHolder> result : results) {
                assertNotNull(result.get(30, TimeUnit.SECONDS).accessToken);
            }
        } finally {
            executor.shutdown();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // The invariant itself: a refresh holds at most one connection, so every path must succeed with
    // a pool of exactly one. Pre-fix, a single Case-B refresh deadlocked here deterministically, and
    // so did any static-key refresh (static key lookups opened a nested transaction on every mint).
    @Test
    public void refreshSucceedsWithPoolSizeOne() throws Exception {
        Utils.setValueInConfig("postgresql_connection_pool_size", "1");

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // dynamic-key session and static-key session, each through the Case-B promote path
        for (boolean useStaticKey : new boolean[]{false, true}) {
            String caseBToken = makeCaseBToken(process, "user-" + useStaticKey, useStaticKey);
            SessionInformationHolder refreshed = Session.refreshSession(process.getProcess(), caseBToken, null,
                    false, AccessToken.getLatestVersion());
            assertNotNull(refreshed.accessToken);
            assertNotNull(refreshed.refreshToken);
        }

        // a small concurrent burst still gets through the single connection by serializing
        final int concurrency = 4;
        String[] caseBTokens = new String[concurrency];
        for (int i = 0; i < concurrency; i++) {
            caseBTokens[i] = makeCaseBToken(process, "burst-user" + i, i % 2 == 0);
        }
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            CountDownLatch startSignal = new CountDownLatch(1);
            List<Future<SessionInformationHolder>> results = new ArrayList<>();
            for (String token : caseBTokens) {
                results.add(executor.submit(() -> {
                    startSignal.await();
                    return Session.refreshSession(process.getProcess(), token, null, false,
                            AccessToken.getLatestVersion());
                }));
            }
            startSignal.countDown();
            for (Future<SessionInformationHolder> result : results) {
                assertNotNull(result.get(30, TimeUnit.SECONDS).accessToken);
            }
        } finally {
            executor.shutdown();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
