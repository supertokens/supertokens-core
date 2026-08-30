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
import io.supertokens.auditlog.lifecycle.GroupPresence;
import io.supertokens.auditlog.lifecycle.LifecycleAuditEvent;
import io.supertokens.authRecipe.ApproximateUserCount;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.*;
import org.junit.rules.TestRule;

import java.util.Collections;

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
        // asOf is the instant the served value is as-of (the fold window's upper bound), so it falls within
        // the request.
        assertTrue(asOf <= after && asOf >= before);

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

    // A stale cache entry triggers a background anchor refresh (stale-while-revalidate): the request keeps
    // serving the correct count (anchor + folded delta), the refresh fires the COMPLETED process state, and a
    // later request advances asOf. Staleness is forced via the test-only seam so we don't wait out the real
    // 10-min TTL.
    @Test
    public void staleEntryTriggersBackgroundRefresh() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            EmailPassword.signUp(process.getProcess(), "seed" + i + "@example.com", "password" + i);
        }

        // Prime the cache (synchronous first request) and capture the initial anchor boundary.
        JsonObject primed = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?allowApproximate=true", null, 1000, 1000, null,
                SemVer.v5_6.get(), "");
        assertEquals(3, primed.get("count").getAsLong());
        long asOfBefore = primed.get("asOf").getAsLong();

        // Two more sign-ups land in the folded delta (their user_creation events are after the anchor
        // snapshot), and age the cached anchor so the next request sees it stale.
        EmailPassword.signUp(process.getProcess(), "extra0@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "extra1@example.com", "password1");
        Thread.sleep(5);
        ApproximateUserCount.getInstance(process.getProcess(), process.getAppForTesting().toAppIdentifier())
                .expireEntryForTesting(process.getAppForTesting());

        // The stale request still serves the correct count (anchor + delta) and kicks off the background refresh.
        JsonObject stale = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?allowApproximate=true", null, 1000, 1000, null,
                SemVer.v5_6.get(), "");
        assertEquals(5, stale.get("count").getAsLong());

        // The background refresh completes and re-dates the anchor.
        assertNotNull(process.checkOrWaitForEvent(
                ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_REFRESH_COMPLETED));

        // A subsequent request still returns the exact count and now serves off the refreshed (later) anchor.
        JsonObject refreshed = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?allowApproximate=true", null, 1000, 1000, null,
                SemVer.v5_6.get(), "");
        assertEquals(5, refreshed.get("count").getAsLong());
        assertTrue(refreshed.get("asOf").getAsLong() > asOfBefore);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // The accuracy upgrade of PLAN-010 unit "ledger-fold": a deletion between two requests is reflected
    // immediately, off the same cached anchor, because the fold sees the deletion's lifecycle event. The old
    // joined-since delta could only ever see creations, so this count would have stayed stale until the next
    // anchor refresh (up to the 10-min TTL). No refresh happens here — the anchor is primed once and reused.
    @Test
    public void apiApproximateReflectsDeletionsViaFoldWithoutARefresh() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo doomed = EmailPassword.signUp(process.getProcess(), "del@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "keep0@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "keep1@example.com", "password1");

        // Prime the anchor synchronously: exact count 3, snapshotted now. The three creations are in the anchor,
        // not the fold window.
        JsonObject primed = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?allowApproximate=true", null, 1000, 1000, null,
                SemVer.v5_6.get(), "");
        assertEquals(3, primed.get("count").getAsLong());

        // Delete a user after the anchor snapshot. Its lifecycle event lands in the fold window.
        Thread.sleep(5);
        AuthRecipe.deleteUser(process.getProcess(), doomed.getSupertokensUserId());
        Thread.sleep(5);

        // Same anchor (no refresh — the entry is not stale), but the fold now carries the -1: exact count served.
        JsonObject afterDelete = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?allowApproximate=true", null, 1000, 1000, null,
                SemVer.v5_6.get(), "");
        assertEquals(2, afterDelete.get("count").getAsLong());
        assertTrue(afterDelete.get("approximate").getAsBoolean());
        // The exact recompute agrees, confirming the fold is exact (not a lagging estimate).
        assertEquals(2, ((AuthRecipeSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getUsersCount(process.getAppForTesting(), null));

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

    // Shadow audit, happy path (PLAN-010 unit 3): a deletion that is recorded in the lifecycle ledger moves the
    // exact count by exactly the folded delta, so the audit at the next refresh matches. Exercises the real
    // storage reads (fresh exact count + app-scoped window read) and the fold end to end.
    @Test
    public void shadowAuditMatchesWhenACountChangeIsLedgered() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo doomed = EmailPassword.signUp(process.getProcess(), "del@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "keep0@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "keep1@example.com", "password1");

        TenantIdentifier tenant = process.getAppForTesting();
        Storage storage = StorageLayer.getStorage(process.getProcess());
        ApproximateUserCount auc = ApproximateUserCount.getInstance(process.getProcess(),
                tenant.toAppIdentifier());

        // Seed the audit snapshot (exact count = 3 as of now); the first pass only seeds, it cannot compare.
        auc.runShadowAuditForTesting(process.getProcess(), tenant, storage);
        assertNull(ProcessState.getInstance(process.getProcess()).getLastEventByName(
                ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_MATCHED));

        // A deletion that IS recorded in the ledger: the exact count (-1) and the folded delta (-1) agree.
        Thread.sleep(5);
        AuthRecipe.deleteUser(process.getProcess(), doomed.getSupertokensUserId());
        Thread.sleep(5);

        ProcessState.getInstance(process.getProcess()).clear();
        auc.runShadowAuditForTesting(process.getProcess(), tenant, storage);

        assertNotNull(ProcessState.getInstance(process.getProcess()).getLastEventByName(
                ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_MATCHED));
        assertNull(ProcessState.getInstance(process.getProcess()).getLastEventByName(
                ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_DISCREPANCY));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Shadow audit, detection path: a lifecycle event that implies a count change the exact count does not
    // reflect (here a spurious group deletion — standing in for any ledger/interpreter bug) is caught. The
    // discrepancy is logged with full context and never served; the state carries the same context for tests.
    @Test
    public void shadowAuditFlagsALedgerCountMismatch() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            EmailPassword.signUp(process.getProcess(), "user" + i + "@example.com", "password" + i);
        }

        TenantIdentifier tenant = process.getAppForTesting();
        Storage storage = StorageLayer.getStorage(process.getProcess());
        ApproximateUserCount auc = ApproximateUserCount.getInstance(process.getProcess(),
                tenant.toAppIdentifier());

        // Seed the audit snapshot (exact count = 3 as of now).
        auc.runShadowAuditForTesting(process.getProcess(), tenant, storage);

        // Inject a group-deletion event that never actually happened, so the ledger implies a -1 the exact
        // count (still 3) does not reflect. A real ledger/interpreter bug looks the same to the audit.
        Thread.sleep(5);
        long createdAt = System.currentTimeMillis();
        GroupPresence phantom = new GroupPresence("phantom-group",
                Collections.singletonList(tenant.getTenantId()));
        AuditLogEvent spurious = LifecycleAuditEvent.forUserGroupDeletion(tenant.toAppIdentifier(),
                "phantom-group", phantom, createdAt);
        ((ActivityLogStorage) storage).createActivityLogEntry(tenant, spurious);
        Thread.sleep(5);

        ProcessState.getInstance(process.getProcess()).clear();
        auc.runShadowAuditForTesting(process.getProcess(), tenant, storage);

        ProcessState.EventAndException event = ProcessState.getInstance(process.getProcess()).getLastEventByName(
                ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_DISCREPANCY);
        assertNotNull(event);
        assertNotNull(event.data);
        assertEquals(3, event.data.get("previousExactCount").getAsLong());
        assertEquals(3, event.data.get("freshExactCount").getAsLong());
        assertEquals(-1, event.data.get("foldedDelta").getAsLong());
        assertEquals(2, event.data.get("expectedCount").getAsLong()); // 3 + (-1), never served
        assertEquals(1, event.data.get("eventCount").getAsInt());
        assertNull(ProcessState.getInstance(process.getProcess()).getLastEventByName(
                ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_MATCHED));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
