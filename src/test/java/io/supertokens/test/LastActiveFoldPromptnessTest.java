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

package io.supertokens.test;

import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.rollupUserLastActive.RollupDirtySignal;
import io.supertokens.cronjobs.rollupUserLastActive.RollupUserLastActive;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Promptness of the last-active fold for a sign-up-only user. A newly created user's fold credit is the
 * {@code user_creation} lifecycle event, which is written on the mutation's own connection via
 * {@code startAuditedTransaction} and so — unlike the {@code updateLastActive} sign-in ping — does not mark
 * the rollup dirty on its own. The sign-up API layer therefore marks it explicitly
 * ({@link ActiveUsers#markLastActiveRollupDirty}); without that, a user who only signs up and produces no
 * other activity would fold into {@code user_last_active} only on the periodic unconditional backstop pass,
 * not on the next tick.
 *
 * <p>This pins that behaviour by folding through a <em>non-forced</em> pass — one that folds only when the
 * dirty flag is set (the case {@code RollupUserLastActiveTest} covers with a manual {@code markDirty}). Here
 * a real HTTP sign-up must be what arms it: after the sign-up a non-forced pass folds the new user. Were the
 * sign-up's dirty signal missing, this pass would skip and the assertion would fail.
 */
public class LastActiveFoldPromptnessTest {

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

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
    public void signUpWakesTheRollupSoANonForcedPassFoldsTheNewUser() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        process.getProcess().setForceInMemoryDB();
        // Neutralise the auto-registered rollup cron so the only passes that run are the ones this test drives.
        // Otherwise its forced first-run pass could fold the sign-up regardless of the dirty signal, masking
        // the behaviour under test.
        CronTaskTest.getInstance(process.getProcess())
                .setInitialWaitTimeInSeconds(RollupUserLastActive.RESOURCE_KEY, 24 * 3600);
        CronTaskTest.getInstance(process.getProcess())
                .setIntervalInSeconds(RollupUserLastActive.RESOURCE_KEY, 24 * 3600);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Main main = process.getProcess();
        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
            process.kill();
            return;
        }

        long base = System.currentTimeMillis();
        AppIdentifier appIdentifier = new AppIdentifier(null, null);
        List<List<TenantIdentifier>> tenantsInfo = StorageLayer.getTenantsWithUniqueUserPoolId(main);
        TenantIdentifier representative = tenantsInfo.get(0).get(0);
        Storage storage = StorageLayer.getStorage(representative, main);
        String userPoolId = storage.getUserPoolId();
        RollupUserLastActive cron = RollupUserLastActive.getNewInstanceForTesting(main, tenantsInfo);

        // Pass 1: this fresh cron instance's first pass is forced, but there is nothing to fold yet.
        cron.runOncePerStorageForTesting(representative, storage);
        assertEquals(0, ActiveUsers.countUsersActiveSince(main, appIdentifier, base - DAY_MS));

        // Clear any stray dirty flag so the only signal the next pass can consume is the one the sign-up sets.
        RollupDirtySignal.getInstance(main).consumeDirty(userPoolId);

        // Sign up a new user over HTTP. Its fold credit is the transactional user_creation event; the rollup is
        // the sole writer of user_last_active, so the projection is still empty immediately after the sign-up.
        signUpEmailPassword(process, "promptness@example.com", "validPass123");
        assertEquals(0, ActiveUsers.countUsersActiveSince(main, appIdentifier, base - DAY_MS));

        // Pass 2 is NOT forced (not the first pass, not a backstop tick): it folds only if the sign-up marked
        // the storage dirty. With the fix it does, so the new user is now folded into user_last_active.
        cron.runOncePerStorageForTesting(representative, storage);
        assertEquals(1, ActiveUsers.countUsersActiveSince(main, appIdentifier, base - DAY_MS));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private void signUpEmailPassword(TestingProcessManager.TestingProcess process, String email, String password)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        JsonObject resp = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signup", body, 1000, 1000, null, SemVer.v5_0.get(), "emailpassword");
        assertEquals("OK", resp.get("status").getAsString());
    }
}
