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
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.*;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class ApproximateUserCountTest {
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

    // For any sinceMs, anchor(sinceMs) + countTenantUsersJoinedSince(sinceMs) == exact getUsersCount. The
    // subtraction that computes the anchor is exactly what makes this identity hold at every boundary.
    @Test
    public void anchorPlusDeltaEqualsExactAtEveryBoundary() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeSQLStorage storage = (AuthRecipeSQLStorage) StorageLayer.getStorage(process.getProcess());
        TenantIdentifier tenant = process.getAppForTesting();

        // Empty tenant: everything is zero.
        assertEquals(0, storage.getUsersCount(tenant, null));
        assertEquals(0, storage.countTenantUsersJoinedSince(tenant, 0));
        assertEquals(0, storage.computeTenantUserCountAnchor(tenant, 0));

        // Batch A, then a boundary strictly between the batches, then batch B.
        EmailPassword.signUp(process.getProcess(), "a0@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "a1@example.com", "password1");
        Thread.sleep(10);
        long boundary = System.currentTimeMillis();
        Thread.sleep(10);
        EmailPassword.signUp(process.getProcess(), "b0@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "b1@example.com", "password1");
        EmailPassword.signUp(process.getProcess(), "b2@example.com", "password2");

        long exact = storage.getUsersCount(tenant, null);
        assertEquals(5, exact);

        // sinceMs = 0: every user joined after it -> whole count is delta, anchor is empty.
        assertEquals(5, storage.countTenantUsersJoinedSince(tenant, 0));
        assertEquals(0, storage.computeTenantUserCountAnchor(tenant, 0));

        // Far future: nobody joined after it -> delta empty, anchor is the whole count.
        long future = System.currentTimeMillis() + 1_000_000;
        assertEquals(0, storage.countTenantUsersJoinedSince(tenant, future));
        assertEquals(5, storage.computeTenantUserCountAnchor(tenant, future));

        // A boundary between the batches: only batch B is in the delta, batch A is the anchor.
        assertEquals(3, storage.countTenantUsersJoinedSince(tenant, boundary));
        assertEquals(2, storage.computeTenantUserCountAnchor(tenant, boundary));

        // The identity holds at every boundary tried.
        for (long sinceMs : new long[]{0, boundary, future}) {
            assertEquals(exact, storage.computeTenantUserCountAnchor(tenant, sinceMs)
                    + storage.countTenantUsersJoinedSince(tenant, sinceMs));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // allowApproximate on the current CDI version returns the exact count for a freshly primed tenant, plus
    // the approximate/asOf fields.
    @Test
    public void apiApproximateReturnsExactAndFields() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (int i = 0; i < 5; i++) {
            EmailPassword.signUp(process.getProcess(), "user" + i + "@example.com", "password" + i);
        }

        long before = System.currentTimeMillis();
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?allowApproximate=true", null, 1000, 1000, null,
                SemVer.v5_6.get(), "");
        long after = System.currentTimeMillis();

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(5, response.get("count").getAsLong());
        assertTrue(response.has("approximate"));
        assertTrue(response.get("approximate").getAsBoolean());
        assertTrue(response.has("asOf"));
        long asOf = response.get("asOf").getAsLong();
        // asOf is the anchor boundary X = refresh-start - 60s skew margin, so it is in the recent past.
        assertTrue(asOf <= after && asOf >= before - 120_000);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Without the param the response is byte-for-byte unchanged: no approximate/asOf fields.
    @Test
    public void apiWithoutParamHasNoApproximateFields() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        EmailPassword.signUp(process.getProcess(), "user@example.com", "password");

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count", null, 1000, 1000, null, SemVer.v5_6.get(), "");

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(1, response.get("count").getAsLong());
        assertFalse(response.has("approximate"));
        assertFalse(response.has("asOf"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // On a CDI version older than the one that introduces the feature, allowApproximate is ignored: exact
    // count, no new fields.
    @Test
    public void apiApproximateIgnoredOnOlderCDI() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        EmailPassword.signUp(process.getProcess(), "user@example.com", "password");

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?allowApproximate=true", null, 1000, 1000, null,
                SemVer.v5_5.get(), "");

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(1, response.get("count").getAsLong());
        assertFalse(response.has("approximate"));
        assertFalse(response.has("asOf"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A recipe-filtered count has no approximate equivalent, so the server falls back to exact and reports
    // approximate=false (the param was set, so the fields are still present).
    @Test
    public void apiApproximateWithRecipeFilterFallsBackToExact() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        EmailPassword.signUp(process.getProcess(), "user0@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "user1@example.com", "password1");

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?allowApproximate=true&includeRecipeIds=emailpassword", null,
                1000, 1000, null, SemVer.v5_6.get(), "");

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(2, response.get("count").getAsLong());
        assertTrue(response.has("approximate"));
        assertFalse(response.get("approximate").getAsBoolean());
        assertTrue(response.has("asOf"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
