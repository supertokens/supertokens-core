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
import io.supertokens.pluginInterface.auditlog.LifecycleEventType;
import io.supertokens.authRecipe.ApproximateUserCount;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.*;
import org.junit.rules.TestRule;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    // PLAN-010 unit "default-flip", re-gated to CDI 5.7 (the 5.6 opt-in contract shipped in core 12.2.0 and
    // must not be redefined): on CDI 5.7 the default (param-less) path serves the fast anchor+fold value and
    // carries the approximate/asOf fields, exactly as if allowApproximate had been set. The count is still
    // exact for a freshly primed tenant.
    @Test
    public void apiDefaultPathServesFromFoldWithFieldsOn5_7() throws Exception {
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
                "http://localhost:3567/users/count", null, 1000, 1000, null, SemVer.v5_7.get(), "");
        long after = System.currentTimeMillis();

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(5, response.get("count").getAsLong());
        assertTrue(response.has("approximate"));
        assertTrue(response.get("approximate").getAsBoolean());
        assertTrue(response.has("asOf"));
        long asOf = response.get("asOf").getAsLong();
        assertTrue(asOf <= after && asOf >= before);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // On CDI 5.7 the default (param-less) path serves the ledger fold on its own, so a deletion between two
    // param-less requests - off the same primed anchor, no refresh - is reflected immediately (3 -> 2). On the
    // released 5.6 contract the param-less path is an exact recompute per request and carries no fields (see
    // api5_6ParamLessKeepsReleasedContract).
    @Test
    public void apiDefaultPathReflectsDeletionViaFoldWithoutParamOn5_7() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo doomed = EmailPassword.signUp(process.getProcess(), "del@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "keep0@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "keep1@example.com", "password1");

        // Prime the anchor synchronously with a param-less request: exact count 3, served from the fold.
        JsonObject primed = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count", null, 1000, 1000, null, SemVer.v5_7.get(), "");
        assertEquals(3, primed.get("count").getAsLong());
        assertTrue(primed.get("approximate").getAsBoolean());

        Thread.sleep(5);
        AuthRecipe.deleteUser(process.getProcess(), doomed.getSupertokensUserId());
        Thread.sleep(5);

        // Same anchor (no refresh), param-less again: the fold now carries the -1.
        JsonObject afterDelete = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count", null, 1000, 1000, null, SemVer.v5_7.get(), "");
        assertEquals(2, afterDelete.get("count").getAsLong());
        assertTrue(afterDelete.get("approximate").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Released-contract guard: CDI 5.6 shipped in core 12.2.0 with the ledger value opt-in via
    // allowApproximate=true. A param-less request on 5.6 must therefore keep the pre-flip behaviour - an exact
    // recompute with NO approximate/asOf fields - and must NOT be redefined into the 5.7 default-serve path.
    // (The opt-in path on 5.6 is covered by apiApproximateReturnsExactAndFields; the 5.7 default by
    // apiDefaultPathServesFromFoldWithFieldsOn5_7.)
    @Test
    public void api5_6ParamLessKeepsReleasedContract() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            EmailPassword.signUp(process.getProcess(), "user" + i + "@example.com", "password" + i);
        }

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count", null, 1000, 1000, null, SemVer.v5_6.get(), "");

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(3, response.get("count").getAsLong());
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

    // On CDI 5.7 the additive approximate/asOf fields key purely on the CDI version, no longer on an
    // allowApproximate opt-in. The exact-fallback shapes the ledger fold does not cover - an all-tenants
    // count, or a recipe-filtered count - therefore carry approximate=false + asOf even when the param is
    // absent. This pins that param-less contract for both fallback shapes on 5.7 (the recipe-filter case above
    // still sends the param on 5.6, so it does not).
    @Test
    public void apiFallbackShapesCarryFieldsWithoutParamOn5_7() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        EmailPassword.signUp(process.getProcess(), "user0@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "user1@example.com", "password1");

        // All-tenants shape, no allowApproximate param: exact recompute, but the additive fields still appear.
        JsonObject allTenants = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?includeAllTenants=true", null, 1000, 1000, null,
                SemVer.v5_7.get(), "");

        assertEquals("OK", allTenants.get("status").getAsString());
        assertEquals(2, allTenants.get("count").getAsLong());
        assertTrue(allTenants.has("approximate"));
        assertFalse(allTenants.get("approximate").getAsBoolean());
        assertTrue(allTenants.has("asOf"));

        // Recipe-filtered shape, no allowApproximate param: same param-less fallback contract.
        JsonObject recipeFiltered = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?includeRecipeIds=emailpassword", null, 1000, 1000, null,
                SemVer.v5_7.get(), "");

        assertEquals("OK", recipeFiltered.get("status").getAsString());
        assertEquals(2, recipeFiltered.get("count").getAsLong());
        assertTrue(recipeFiltered.has("approximate"));
        assertFalse(recipeFiltered.get("approximate").getAsBoolean());
        assertTrue(recipeFiltered.has("asOf"));

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

    // Serve-level assertion for the other newly-exact branch: account unlinking (+1). Unlinking a member from a
    // primary group frees it into its own standalone group, so the active-user count goes up by one. Off a
    // single primed anchor with no refresh, serve() reflects that +1 immediately because the fold sees the
    // account_unlinking event. The old PLAN-009 joined-since delta could never see it — unlinking creates no new
    // join row — so this count would have stayed stale until the next anchor refresh. Locks the serve()-path
    // wiring for unlinking against regression (previously covered only via the interpreter/fold unit tests).
    @Test
    public void apiApproximateReflectsUnlinkingViaFoldWithoutARefresh() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});

        // A primary group of two linked accounts (counts as one user) plus one standalone user: exact count 2.
        AuthRecipeUserInfo primary = EmailPassword.signUp(process.getProcess(), "primary@example.com", "password0");
        AuthRecipeUserInfo member = EmailPassword.signUp(process.getProcess(), "member@example.com", "password1");
        EmailPassword.signUp(process.getProcess(), "keep@example.com", "password2");
        AuthRecipe.createPrimaryUser(process.getProcess(), primary.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), member.getSupertokensUserId(),
                primary.getSupertokensUserId());

        // Prime the anchor synchronously: exact count 2 (the linked group + the standalone). The creations and
        // the link are all in the anchor, not the fold window.
        JsonObject primed = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?allowApproximate=true", null, 1000, 1000, null,
                SemVer.v5_6.get(), "");
        assertEquals(2, primed.get("count").getAsLong());

        // Unlink the member after the anchor snapshot: it is freed into its own standalone group (+1). Its
        // account_unlinking event lands in the fold window.
        Thread.sleep(5);
        AuthRecipe.unlinkAccounts(process.getProcess(), member.getSupertokensUserId());
        Thread.sleep(5);

        // Same anchor (no refresh — the entry is not stale), but the fold now carries the +1: exact count served.
        JsonObject afterUnlink = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?allowApproximate=true", null, 1000, 1000, null,
                SemVer.v5_6.get(), "");
        assertEquals(3, afterUnlink.get("count").getAsLong());
        assertTrue(afterUnlink.get("approximate").getAsBoolean());
        // The exact recompute agrees, confirming the fold is exact (not a lagging estimate).
        assertEquals(3, ((AuthRecipeSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getUsersCount(process.getAppForTesting(), null));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Corrupt-ledger safety at the serving path: if a lifecycle-event payload in the fold window fails to parse
    // (InvalidLifecycleEventPayloadException — a ledger-integrity signal), serve() must never return a value
    // derived from a corrupt fold; it re-anchors to a fresh exact count instead. Proven observable by making
    // the fresh exact count differ from the primed anchor: a real deletion moves the exact count to 2, and a
    // corrupt-payload event lands in the same window. The corrupt event poisons the fold, forcing the recompute,
    // so the value served can only be the recomputed 2 — never the primed 3, and never a fold of the valid
    // deletion alone (serve() parses every window event before folding any). Exercises the catch branch that
    // was previously covered only indirectly.
    @Test
    public void apiApproximateReAnchorsOnCorruptLedgerPayload() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo doomed = EmailPassword.signUp(process.getProcess(), "del@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "keep0@example.com", "password1");
        EmailPassword.signUp(process.getProcess(), "keep1@example.com", "password2");

        // Prime the anchor synchronously: exact count 3, snapshotted now.
        JsonObject primed = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?allowApproximate=true", null, 1000, 1000, null,
                SemVer.v5_6.get(), "");
        assertEquals(3, primed.get("count").getAsLong());

        // A real deletion moves the exact count to 2 (its valid user_deletion event lands in the fold window).
        Thread.sleep(5);
        AuthRecipe.deleteUser(process.getProcess(), doomed.getSupertokensUserId());

        // A corrupt-payload lifecycle event lands in the same window: a valid lifecycle event_type (so the
        // window read returns it) with a payload that is well-formed JSON but not parseable against the
        // lifecycle schema (correct schemaVersion + type, but missing the required groupBefore/groupAfter).
        // It must be syntactically-valid JSON: the Postgres payload column is JSONB, which rejects malformed
        // JSON at write time, so a raw-garbage string would fail createActivityLogEntry before serve() ever
        // sees it. Schema drift like this — valid JSON, wrong shape — is exactly what JSONB cannot catch and
        // what serve()'s re-anchor must still defend against on every backend.
        TenantIdentifier tenant = process.getAppForTesting();
        AuditLogEvent corrupt = new AuditLogEvent(tenant.getAppId(), null, "recipe-user", "recipe-user",
                LifecycleEventType.USER_DELETION.getValue(), "success", null, null,
                System.currentTimeMillis(), "{\"schemaVersion\":1,\"type\":\"user_deletion\"}");
        ((ActivityLogStorage) StorageLayer.getStorage(process.getProcess())).createActivityLogEntry(tenant, corrupt);
        Thread.sleep(5);

        // serve() hits InvalidLifecycleEventPayloadException while parsing the window, so it re-anchors to the
        // fresh exact count (2) rather than serving a value derived from the corrupt fold.
        JsonObject afterCorrupt = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?allowApproximate=true", null, 1000, 1000, null,
                SemVer.v5_6.get(), "");
        assertEquals(2, afterCorrupt.get("count").getAsLong());
        assertTrue(afterCorrupt.get("approximate").getAsBoolean());
        assertEquals(2, ((AuthRecipeSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getUsersCount(process.getAppForTesting(), null));

        // The re-anchor re-based past the corrupt event, so a subsequent request folds cleanly off the fresh
        // anchor (empty window) and still serves 2 — the re-anchor is durable, not a one-request fallback.
        JsonObject next = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users/count?allowApproximate=true", null, 1000, 1000, null,
                SemVer.v5_6.get(), "");
        assertEquals(2, next.get("count").getAsLong());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Single-flighted prime: concurrent /users/count requests that find no anchor for a tenant must run one exact
    // count between them, not one each.
    // The storage is wrapped so the first exact count blocks until every caller has had time to reach serve();
    // without the per-tenant lock each of them would run its own count.
    @Test
    public void concurrentFirstRequestsRunOneExactCount() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            EmailPassword.signUp(process.getProcess(), "seed" + i + "@example.com", "password" + i);
        }

        TenantIdentifier tenant = process.getAppForTesting();
        ApproximateUserCount approximateUserCount = ApproximateUserCount.getInstance(process.getProcess(),
                tenant.toAppIdentifier());
        assertFalse(approximateUserCount.hasAnchorForTesting(tenant));

        AtomicInteger exactCounts = new AtomicInteger();
        CountDownLatch firstCountStarted = new CountDownLatch(1);
        CountDownLatch releaseCount = new CountDownLatch(1);
        Storage storage = countingStorage(StorageLayer.getStorage(process.getProcess()), exactCounts,
                firstCountStarted, releaseCount);

        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(
                        () -> approximateUserCount.serve(process.getProcess(), tenant, storage).count));
            }

            assertTrue(firstCountStarted.await(10, TimeUnit.SECONDS));
            // Give the other callers time to reach the prime while the first count is still running.
            Thread.sleep(500);
            releaseCount.countDown();

            for (Future<Long> result : results) {
                assertEquals(3L, (long) result.get(10, TimeUnit.SECONDS));
            }
        } finally {
            releaseCount.countDown();
            pool.shutdownNow();
        }

        assertEquals(1, exactCounts.get());
        assertTrue(approximateUserCount.hasAnchorForTesting(tenant));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A synchronous re-anchor forced by the fold (a corrupt ledger payload here; a burst over the cap takes the
    // same path) is single-flighted too: concurrent callers that hit it run one exact count between them.
    @Test
    public void concurrentReAnchorsRunOneExactCount() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        for (int i = 0; i < 3; i++) {
            EmailPassword.signUp(process.getProcess(), "seed" + i + "@example.com", "password" + i);
        }

        TenantIdentifier tenant = process.getAppForTesting();
        ApproximateUserCount approximateUserCount = ApproximateUserCount.getInstance(process.getProcess(),
                tenant.toAppIdentifier());
        assertEquals(3, approximateUserCount.serve(process.getProcess(), tenant,
                StorageLayer.getStorage(process.getProcess())).count);

        // Valid JSON with the wrong shape, so parsing the fold window fails and serve() re-anchors (see
        // apiApproximateReAnchorsOnCorruptLedgerPayload).
        Thread.sleep(5);
        AuditLogEvent corrupt = new AuditLogEvent(tenant.getAppId(), null, "recipe-user", "recipe-user",
                LifecycleEventType.USER_DELETION.getValue(), "success", null, null,
                System.currentTimeMillis(), "{\"schemaVersion\":1,\"type\":\"user_deletion\"}");
        ((ActivityLogStorage) StorageLayer.getStorage(process.getProcess())).createActivityLogEntry(tenant, corrupt);
        Thread.sleep(5);

        AtomicInteger exactCounts = new AtomicInteger();
        CountDownLatch firstCountStarted = new CountDownLatch(1);
        CountDownLatch releaseCount = new CountDownLatch(1);
        Storage storage = countingStorage(StorageLayer.getStorage(process.getProcess()), exactCounts,
                firstCountStarted, releaseCount);

        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(
                        () -> approximateUserCount.serve(process.getProcess(), tenant, storage).count));
            }

            assertTrue(firstCountStarted.await(10, TimeUnit.SECONDS));
            // Give the other callers time to reach the re-anchor while the first count is still running.
            Thread.sleep(500);
            releaseCount.countDown();

            for (Future<Long> result : results) {
                assertEquals(3L, (long) result.get(10, TimeUnit.SECONDS));
            }
        } finally {
            releaseCount.countDown();
            pool.shutdownNow();
        }

        assertEquals(1, exactCounts.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A failed anchor count is shared too: callers waiting on it get its failure instead of each running the count
    // again in turn (which on a count that outlasts its timeout made the k-th caller fail after k timeouts).
    @Test
    public void failedCountIsSharedWithEveryWaiter() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier tenant = process.getAppForTesting();
        ApproximateUserCount approximateUserCount = ApproximateUserCount.getInstance(process.getProcess(),
                tenant.toAppIdentifier());
        AtomicInteger countRequests = new AtomicInteger();
        CountDownLatch countStarted = new CountDownLatch(1);
        Storage failing = interceptingStorage(StorageLayer.getStorage(process.getProcess()), () -> {
            countRequests.incrementAndGet();
            countStarted.countDown();
            Thread.sleep(500); // the other callers queue up meanwhile
            throw new StorageQueryException(new java.sql.SQLException("simulated count timeout"));
        });

        int callers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> approximateUserCount.serve(process.getProcess(), tenant, failing).count));
            }
            assertTrue(countStarted.await(10, TimeUnit.SECONDS));

            long start = System.currentTimeMillis();
            for (Future<Long> result : results) {
                try {
                    result.get(10, TimeUnit.SECONDS);
                    fail("the count failed, so every caller must fail");
                } catch (java.util.concurrent.ExecutionException e) {
                    assertTrue(e.getCause() instanceof StorageQueryException);
                }
            }
            // all of them fail with the one count, not one after another
            assertTrue(System.currentTimeMillis() - start < 2000);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, countRequests.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // The shadow audit runs only on refreshes that counted anew. A refresh that reuses the stored count (one counted
    // less than 10 minutes ago, possibly by another core) is skipped, so every audit window ends at a count taken
    // for it.
    @Test
    public void shadowAuditRunsOnlyOnNewCounts() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL
                || StorageLayer.isInMemDb(process.getProcess())) { // the in-memory db stores no count, so always counts
            return;
        }

        for (int i = 0; i < 3; i++) {
            EmailPassword.signUp(process.getProcess(), "seed" + i + "@example.com", "password" + i);
        }

        TenantIdentifier tenant = process.getAppForTesting();
        Storage storage = StorageLayer.getStorage(process.getProcess());
        ApproximateUserCount approximateUserCount = ApproximateUserCount.getInstance(process.getProcess(),
                tenant.toAppIdentifier());

        // Prime (counts and stores 3), then two refreshes that both reuse the stored count: if those were audited,
        // the second would compare against the first and report a match.
        assertEquals(3, approximateUserCount.serve(process.getProcess(), tenant, storage).count);
        for (int i = 0; i < 2; i++) {
            refresh(approximateUserCount, process, tenant, storage);
        }
        assertNull(process.checkOrWaitForEvent(
                ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_MATCHED, 100));

        // Two refreshes that count anew (the stored count made too old first), with a ledgered sign-up between
        // them: the first seeds the audit, the second checks the window and matches.
        expireStoredCount(storage, tenant);
        refresh(approximateUserCount, process, tenant, storage);
        EmailPassword.signUp(process.getProcess(), "later@example.com", "password");
        Thread.sleep(5);
        expireStoredCount(storage, tenant);
        refresh(approximateUserCount, process, tenant, storage);
        assertNotNull(process.checkOrWaitForEvent(
                ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_MATCHED, 5000));
        assertNull(process.checkOrWaitForEvent(
                ProcessState.PROCESS_STATE.APPROXIMATE_USER_COUNT_SHADOW_AUDIT_DISCREPANCY, 100));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Anchor counts hold a database connection while they run, so how many run at once is bounded across tenants: a
    // read of many tenants that have no anchor yet runs at most 4 of their counts at a time, not one per tenant.
    @Test
    public void requestCountsAcrossTenantsAreBounded() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<TenantIdentifier> tenants = createTenants(process, "bounded", 6);
        ApproximateUserCount approximateUserCount = ApproximateUserCount.getInstance(process.getProcess(),
                process.getAppForTesting().toAppIdentifier());
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        CountDownLatch releaseCounts = new CountDownLatch(1);
        Storage storage = interceptingStorage(StorageLayer.getStorage(process.getProcess()),
                (CountHook) () -> holdCount(running, maxRunning, releaseCounts));

        ExecutorService pool = Executors.newFixedThreadPool(tenants.size());
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (TenantIdentifier tenant : tenants) {
                results.add(pool.submit(() -> approximateUserCount.serve(process.getProcess(), tenant, storage).count));
            }
            waitFor(() -> running.get() >= 4);
            Thread.sleep(500); // time for any count past the bound to start
            assertEquals(4, maxRunning.get());

            releaseCounts.countDown();
            for (Future<Long> result : results) {
                assertEquals(0L, (long) result.get(10, TimeUnit.SECONDS));
            }
        } finally {
            releaseCounts.countDown();
            pool.shutdownNow();
        }
        assertEquals(4, maxRunning.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Background refreshes run at most 2 at a time, and on their own pool: a backlog of them doesn't hold up the
    // count a request is waiting for.
    @Test
    public void backgroundRefreshesAreBoundedAndDontHoldUpRequestCounts() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<TenantIdentifier> tenants = createTenants(process, "refresh", 5);
        TenantIdentifier unprimed = tenants.remove(tenants.size() - 1);
        ApproximateUserCount approximateUserCount = ApproximateUserCount.getInstance(process.getProcess(),
                process.getAppForTesting().toAppIdentifier());
        Storage realStorage = StorageLayer.getStorage(process.getProcess());
        for (TenantIdentifier tenant : tenants) {
            approximateUserCount.serve(process.getProcess(), tenant, realStorage);
        }

        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        CountDownLatch releaseCounts = new CountDownLatch(1);
        Storage storage = interceptingStorage(realStorage, (TenantCountHook) tenant -> {
            if (!tenant.equals(unprimed)) {
                holdCount(running, maxRunning, releaseCounts);
            }
        });
        try {
            for (TenantIdentifier tenant : tenants) {
                approximateUserCount.expireEntryForTesting(tenant);
                approximateUserCount.serve(process.getProcess(), tenant, storage); // starts the refresh, doesn't wait
            }
            waitFor(() -> running.get() >= 2);
            Thread.sleep(500); // time for any refresh past the bound to start
            assertEquals(2, maxRunning.get());

            // the refresh pool is full and has a backlog; a request's count still runs right away
            long start = System.currentTimeMillis();
            assertEquals(0, approximateUserCount.serve(process.getProcess(), unprimed, storage).count);
            assertTrue(System.currentTimeMillis() - start < 5000);
        } finally {
            releaseCounts.countDown();
        }
        for (TenantIdentifier tenant : tenants) {
            waitFor(() -> !approximateUserCount.isCountingForTesting(tenant));
        }
        assertEquals(2, maxRunning.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Creates tenants prefix0..prefix(n-1) in the test's app.
    private static List<TenantIdentifier> createTenants(TestingProcessManager.TestingProcess process, String prefix,
                                                        int n) throws Exception {
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        TenantIdentifier app = process.getAppForTesting();
        List<TenantIdentifier> tenants = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            TenantIdentifier tenant = new TenantIdentifier(app.getConnectionUriDomain(), app.getAppId(), prefix + i);
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), app.toAppIdentifier()
                    .getAsPublicTenantIdentifier(), new TenantConfig(tenant, new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null), new PasswordlessConfig(true), null, null, new JsonObject()));
            tenants.add(tenant);
        }
        return tenants;
    }

    // A count hook that records how many counts are running at once and holds each until releaseCounts opens.
    private static void holdCount(AtomicInteger running, AtomicInteger maxRunning, CountDownLatch releaseCounts)
            throws InterruptedException {
        maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
        try {
            assertTrue(releaseCounts.await(30, TimeUnit.SECONDS));
        } finally {
            running.decrementAndGet();
        }
    }

    private interface Condition {
        boolean holds() throws Exception;
    }

    private static void waitFor(Condition condition) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        while (!condition.holds()) {
            assertTrue("condition not reached in time", System.currentTimeMillis() < deadline);
            Thread.sleep(10);
        }
    }

    // Makes the cached anchor stale, serves once to start the background refresh, and waits for it (and its audit).
    private static void refresh(ApproximateUserCount approximateUserCount, TestingProcessManager.TestingProcess process,
                                TenantIdentifier tenant, Storage storage) throws Exception {
        Thread.sleep(5);
        approximateUserCount.expireEntryForTesting(tenant);
        approximateUserCount.serve(process.getProcess(), tenant, storage);
        long deadline = System.currentTimeMillis() + 10000;
        while (approximateUserCount.isCountingForTesting(tenant)) {
            assertTrue("background refresh did not finish", System.currentTimeMillis() < deadline);
            Thread.sleep(10);
        }
    }

    private static void expireStoredCount(Storage storage, TenantIdentifier tenant) throws Exception {
        storage.setKeyValue(tenant, "TENANT_USERS_COUNT", new KeyValueInfo("0", 0));
    }

    // Wraps the real storage, counting the tenant count requests (getOrComputeTenantUsersCount, or getUsersCount(tenant)
    // on a storage without the stored-count capability) and holding the first one until releaseCount opens. Every
    // other call goes straight to the real storage.
    private static Storage countingStorage(Storage delegate, AtomicInteger exactCounts,
                                           CountDownLatch firstCountStarted, CountDownLatch releaseCount) {
        return interceptingStorage(delegate, () -> {
            if (exactCounts.incrementAndGet() == 1) {
                firstCountStarted.countDown();
                assertTrue(releaseCount.await(10, TimeUnit.SECONDS));
            }
        });
    }

    private interface CountHook {
        void run() throws Exception;
    }

    private interface TenantCountHook {
        void run(TenantIdentifier tenant) throws Exception;
    }

    private static Storage interceptingStorage(Storage delegate, CountHook onTenantCount) {
        return interceptingStorage(delegate, (TenantCountHook) tenant -> onTenantCount.run());
    }

    private static Storage interceptingStorage(Storage delegate, TenantCountHook onTenantCount) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        for (Class<?> c = delegate.getClass(); c != null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                if (Modifier.isPublic(i.getModifiers())) {
                    interfaces.add(i);
                }
            }
        }
        return (Storage) Proxy.newProxyInstance(delegate.getClass().getClassLoader(),
                interfaces.toArray(new Class<?>[0]), (proxy, method, methodArgs) -> {
                    boolean tenantCount = methodArgs != null && methodArgs[0] instanceof TenantIdentifier
                            && (method.getName().equals("getOrComputeTenantUsersCount")
                            || method.getName().equals("getUsersCount"));
                    if (tenantCount) {
                        onTenantCount.run((TenantIdentifier) methodArgs[0]);
                    }
                    try {
                        return method.invoke(delegate, methodArgs);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
