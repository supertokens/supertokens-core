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

package io.supertokens.test.accountlinking;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.session.Session;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Regression test for GHSA-2pc4-vw9j-454c: a session created concurrently with unlinkAccounts must not
 * survive the unlink's revocation and stay valid as the victim's primary user.
 *
 * The fix serializes session creation and unlink on the same per-user lock and revokes the user's sessions
 * inside the unlink transaction. This test forces the dangerous interleave deterministically with a hook
 * inside createNewSession that pauses, under the lock, immediately before the session row is inserted:
 *  - session creation reaches the hook (holding the lock, with the fix) and pauses;
 *  - unlinkAccounts is started on another thread;
 *  - with the fix, unlink blocks on the lock and cannot finish until the insert commits (asserted below);
 *  - the insert is released, unlink then proceeds and revokes the just-created session.
 *
 * Without the fix, session creation takes no lock, so unlink runs to completion (its revocation misses the
 * not-yet-inserted row) before the insert, and the created session survives — failing this test.
 */
public class UnlinkSessionRaceTest {

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
    public void sessionCreatedConcurrentlyWithUnlinkDoesNotSurvive() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.ACCOUNT_LINKING});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // This race needs real row-level locking (FOR UPDATE on app_id_to_user_id) to serialize the
        // concurrent session-create and unlink. The in-memory SQLite (shared cache) returns
        // SQLITE_LOCKED instead of blocking, so the latch-based race cannot run there; exercise it only
        // against an external SQL storage (e.g. postgres).
        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL
                || StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        // primary <- linked recipe user; a session for the recipe user resolves to the primary identity.
        AuthRecipeUserInfo primary = EmailPassword.signUp(process.getProcess(), "primary@example.com", "password");
        AuthRecipeUserInfo recipe = EmailPassword.signUp(process.getProcess(), "recipe@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), primary.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), recipe.getSupertokensUserId(), primary.getSupertokensUserId());
        String recipeUserId = recipe.getSupertokensUserId();

        final CountDownLatch atInsert = new CountDownLatch(1);      // session creation is poised to insert, under the lock
        final CountDownLatch releaseInsert = new CountDownLatch(1); // the test lets the insert proceed
        final CountDownLatch unlinkDone = new CountDownLatch(1);    // unlink finished

        Session.setCreateSessionBeforeInsertHook(() -> {
            atInsert.countDown();
            try {
                releaseInsert.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        try {
            final SessionInformationHolder[] created = new SessionInformationHolder[1];
            final Exception[] createErr = new Exception[1];
            Thread creator = new Thread(() -> {
                try {
                    created[0] = Session.createNewSession(process.getProcess(), recipeUserId,
                            new JsonObject(), new JsonObject());
                } catch (Exception e) {
                    createErr[0] = e;
                }
            });
            creator.start();

            // Wait until session creation is holding the lock, right before the insert.
            assertTrue(atInsert.await(30, TimeUnit.SECONDS));

            final Exception[] unlinkErr = new Exception[1];
            Thread unlinker = new Thread(() -> {
                try {
                    AuthRecipe.unlinkAccounts(process.getProcess(), recipeUserId);
                } catch (Exception e) {
                    unlinkErr[0] = e;
                } finally {
                    unlinkDone.countDown();
                }
            });
            unlinker.start();

            // With the fix, unlink blocks on the session-creation lock and cannot finish until the insert
            // commits. Without the fix, unlink runs to completion here (and its revocation misses the
            // not-yet-inserted session).
            boolean unlinkFinishedBeforeInsert = unlinkDone.await(5, TimeUnit.SECONDS);

            releaseInsert.countDown();
            creator.join(30_000);
            unlinker.join(30_000);
            assertTrue(unlinkDone.await(30, TimeUnit.SECONDS));

            assertNull("session creation must not error", createErr[0]);
            assertNull("unlink must not error", unlinkErr[0]);
            assertNotNull("session creation must have produced a session", created[0]);

            // The lock must have forced unlink to wait for the insert; if unlink completed while creation was
            // paused under the lock, the serialization is not happening (the vulnerability is present).
            assertFalse("unlinkAccounts must block on the session-creation lock until the insert commits "
                    + "(GHSA-2pc4-vw9j-454c)", unlinkFinishedBeforeInsert);

            // The session created around the unlink must have been revoked, not left live as the victim primary.
            try {
                Session.getSession(process.getProcess(), created[0].session.handle);
                fail("session created concurrently with unlink survived the unlink's revocation "
                        + "(GHSA-2pc4-vw9j-454c)");
            } catch (UnauthorisedException e) {
                // expected: the concurrently-created session was revoked by the unlink
            }
        } finally {
            Session.setCreateSessionBeforeInsertHook(null);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
