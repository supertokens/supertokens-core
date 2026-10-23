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
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.auditlog.lifecycle.CountDeltaInterpreter;
import io.supertokens.auditlog.lifecycle.GroupPresence;
import io.supertokens.auditlog.lifecycle.LifecycleEventPayload;
import io.supertokens.authRecipe.ApproximateUserCount;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.cronjobs.RetrySoon;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.auditlog.LifecycleEventType;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.UsersCountCacheStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.*;
import org.junit.rules.TestRule;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
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

/**
 * The user-count pipeline end to end: the app-level event rules, the app count across every kind of mutation without
 * a new full count, roll-forward on a burst, the "try again later" reply, the stored-count fallback and the retry of a
 * failed daily job.
 */
public class UserCountPipelineTest {
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

    @After
    public void afterEach() {
        ApproximateUserCount.resetForTesting();
        RetrySoon.enableForTesting(0);
    }

    // The app count is the number of user groups: only creating, deleting, merging or splitting a group changes it.
    @Test
    public void appDeltaOfEachLifecycleEventType() {
        GroupPresence inPublic = new GroupPresence("g1", List.of("public"));
        GroupPresence inNoTenant = new GroupPresence("g2", List.of());
        assertEquals(1, CountDeltaInterpreter.appDeltaOf(LifecycleEventPayload.forUserCreation("public")));
        assertEquals(1, CountDeltaInterpreter.appDeltaOf(LifecycleEventPayload.forUserImport("public")));
        assertEquals(1, CountDeltaInterpreter.appDeltaOf(
                LifecycleEventPayload.forAccountUnlinking(inPublic, inNoTenant)));
        assertEquals(-1, CountDeltaInterpreter.appDeltaOf(LifecycleEventPayload.forUserGroupDeletion(inPublic)));
        assertEquals(-1, CountDeltaInterpreter.appDeltaOf(
                LifecycleEventPayload.forAccountLinking(inPublic, inNoTenant)));
        assertEquals(0, CountDeltaInterpreter.appDeltaOf(LifecycleEventPayload.forUserDeletion(inPublic, inPublic)));
        assertEquals(0, CountDeltaInterpreter.appDeltaOf(
                LifecycleEventPayload.forTenantAssociation(inPublic, "t1")));
        assertEquals(0, CountDeltaInterpreter.appDeltaOf(
                LifecycleEventPayload.forTenantDisassociation(inPublic, inNoTenant, "public")));
    }

    // After one full count, the app and tenant counts follow sign-ups, links, unlinks, tenant changes and deletions
    // from the lifecycle events alone - always equal to the exact counts, with no new full count.
    @Test
    public void countsFollowEveryMutationWithoutANewFullCount() throws Exception {
        TestingProcessManager.TestingProcess process = startWithLinkingAndTenants();
        Main main = process.getProcess();
        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        TenantIdentifier publicTenant = TenantIdentifier.BASE_TENANT;
        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");
        Multitenancy.addNewOrUpdateAppOrTenant(main, publicTenant, new TenantConfig(t1, new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null), new PasswordlessConfig(true), null, null, new JsonObject()));
        Storage storage = StorageLayer.getStorage(main);
        Storage t1Storage = StorageLayer.getStorage(t1, main);
        AppIdentifier app = publicTenant.toAppIdentifier();
        ApproximateUserCount counts = ApproximateUserCount.getInstance(main, app);

        AuthRecipeUserInfo a = EmailPassword.signUp(main, "a@example.com", "password");
        AuthRecipeUserInfo b = EmailPassword.signUp(main, "b@example.com", "password");
        AuthRecipeUserInfo c = EmailPassword.signUp(main, "c@example.com", "password");
        assertCountsExact(main, counts, app, publicTenant, t1); // the full counts
        KeyValueInfo storedApp = storage.getKeyValue(publicTenant, "APP_USERS_COUNT");

        EmailPassword.signUp(main, "d@example.com", "password");                       // +1
        assertCountsExact(main, counts, app, publicTenant, t1);
        AuthRecipe.createPrimaryUser(main, a.getSupertokensUserId());
        AuthRecipe.linkAccounts(main, b.getSupertokensUserId(), a.getSupertokensUserId()); // two groups merge: -1
        assertCountsExact(main, counts, app, publicTenant, t1);
        Multitenancy.addUserIdToTenant(main, t1, t1Storage, c.getSupertokensUserId());  // tenant change: 0 for the app
        assertCountsExact(main, counts, app, publicTenant, t1);
        AuthRecipe.unlinkAccounts(main, b.getSupertokensUserId());                     // split: +1
        assertCountsExact(main, counts, app, publicTenant, t1);
        AuthRecipe.linkAccounts(main, b.getSupertokensUserId(), a.getSupertokensUserId());
        AuthRecipe.deleteUser(main, b.getSupertokensUserId(), false);                  // member deleted: 0
        assertCountsExact(main, counts, app, publicTenant, t1);
        AuthRecipe.deleteUser(main, c.getSupertokensUserId());                         // group deleted: -1
        assertCountsExact(main, counts, app, publicTenant, t1);

        if (storage instanceof UsersCountCacheStorage) {
            // no new full count of the app: its stored count is the first one
            KeyValueInfo after = storage.getKeyValue(publicTenant, "APP_USERS_COUNT");
            assertEquals(storedApp.value, after.value);
            assertEquals(storedApp.createdAtTime, after.createdAtTime);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // More events after the anchors than one read takes (a bulk import): the anchors are rolled forward through them
    // instead of being re-counted.
    @Test
    public void aBurstRollsTheAnchorForwardInsteadOfRecounting() throws Exception {
        ApproximateUserCount.setPageSizeAndRollMarginForTesting(3, 0);
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        TenantIdentifier tenant = process.getAppForTesting();
        Storage storage = StorageLayer.getStorage(tenant, main);
        ApproximateUserCount counts = ApproximateUserCount.getInstance(main, tenant.toAppIdentifier());

        EmailPassword.signUp(tenant, storage, main, "first@example.com", "password");
        assertEquals(1, counts.serveTenant(main, tenant).count);
        long primedSnapshot = counts.anchorSnapshotForTesting(tenant, storage);
        KeyValueInfo stored = storage.getKeyValue(tenant, "TENANT_USERS_COUNT");

        for (int i = 0; i < 8; i++) {
            EmailPassword.signUp(tenant, storage, main, "burst" + i + "@example.com", "password");
        }
        assertEquals(9, counts.serveTenant(main, tenant).count);
        assertEquals(9, AuthRecipe.getUsersCountForTenant(tenant, storage, null));
        assertTrue("the anchor was rolled forward", counts.anchorSnapshotForTesting(tenant, storage) > primedSnapshot);
        if (storage instanceof UsersCountCacheStorage) {
            KeyValueInfo after = storage.getKeyValue(tenant, "TENANT_USERS_COUNT");
            assertEquals("no new full count", stored.createdAtTime, after.createdAtTime);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A burst rolls every tenant's anchor (and the app's) forward to its exact count, each from its own snapshot.
    @Test
    public void aBurstRollsEveryTenantsAnchorToItsExactCount() throws Exception {
        TestingProcessManager.TestingProcess process = startWithLinkingAndTenants();
        Main main = process.getProcess();
        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        TenantIdentifier publicTenant = TenantIdentifier.BASE_TENANT;
        TenantIdentifier[] tenants = {publicTenant, new TenantIdentifier(null, null, "t1"),
                new TenantIdentifier(null, null, "t2"), new TenantIdentifier(null, null, "t3")};
        for (int i = 1; i < tenants.length; i++) {
            Multitenancy.addNewOrUpdateAppOrTenant(main, publicTenant, new TenantConfig(tenants[i],
                    new EmailPasswordConfig(true), new ThirdPartyConfig(true, null), new PasswordlessConfig(true),
                    null, null, new JsonObject()));
        }
        AppIdentifier app = publicTenant.toAppIdentifier();
        ApproximateUserCount counts = ApproximateUserCount.getInstance(main, app);
        for (TenantIdentifier tenant : tenants) {
            EmailPassword.signUp(tenant, StorageLayer.getStorage(tenant, main), main,
                    "first-" + tenant.getTenantId() + "@example.com", "password");
        }
        assertCountsExact(main, counts, app, tenants[0], tenants[1], tenants[2]); // the anchors, all but t3's

        ApproximateUserCount.setPageSizeAndRollMarginForTesting(3, 0);
        for (int i = 0; i < 14; i++) {
            TenantIdentifier tenant = tenants[(i * 7) % tenants.length];
            EmailPassword.signUp(tenant, StorageLayer.getStorage(tenant, main), main,
                    "burst" + i + "@example.com", "password");
            if (i == 6) {
                // t3's first anchor is newer than the others: rolling them must not apply its earlier events to it
                assertEquals(AuthRecipe.getUsersCountForTenant(tenants[3], StorageLayer.getStorage(tenants[3], main),
                        null), counts.serveTenant(main, tenants[3]).count);
            }
        }
        assertCountsExact(main, counts, app, tenants);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A roll-forward reads the burst in slices of about one page each: it never asks the database to sort the rest of
    // the burst again for every page.
    @Test
    public void aRollForwardReadsTheBurstInPageSizedSlices() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        TenantIdentifier tenant = process.getAppForTesting();
        Storage storage = StorageLayer.getStorage(tenant, main);
        ApproximateUserCount counts = ApproximateUserCount.getInstance(main, tenant.toAppIdentifier());
        List<Object[]> reads = Collections.synchronizedList(new ArrayList<>());
        Storage recording = interceptingStorage(storage, (method, methodArgs) -> {
            if (method.equals("getActivityLogEntriesForApp")) {
                reads.add(methodArgs);
            }
        });

        EmailPassword.signUp(tenant, storage, main, "first@example.com", "password");
        assertEquals(1, counts.serve(main, tenant, recording).count);
        ApproximateUserCount.setPageSizeAndRollMarginForTesting(3, 0);
        int burst = 60;
        for (int i = 0; i < burst; i++) {
            EmailPassword.signUp(tenant, storage, main, "burst" + i + "@example.com", "password");
        }
        reads.clear();
        assertEquals(burst + 1, counts.serve(main, tenant, recording).count);

        // the events each read's range holds, which the database sorts to return the first page of them
        long sorted = 0;
        for (Object[] read : reads) {
            sorted += ((ActivityLogStorage) storage).getActivityLogEntriesForApp(tenant.toAppIdentifier(),
                    (Set<String>) read[1], (long) read[2], (long) read[3], Integer.MAX_VALUE).size();
        }
        // the window's reads take the burst twice and the slices about twice more; reading the rest of the burst
        // for every page is about burst^2 / (2 * page) = 600
        assertTrue("events in the ranges read: " + sorted, sorted <= 6L * burst);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // An app count across several user pools starts every pool's missing count before waiting for any, so the pools
    // count in parallel: here every pool's count blocks until all three have started, which they only do if none is
    // awaited before the others are started.
    @Test
    public void anAppCountStartsEveryUserPoolsCountBeforeWaiting() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        TenantIdentifier tenant = process.getAppForTesting();
        Storage storage = StorageLayer.getStorage(tenant, main);
        if (storage.getType() != STORAGE_TYPE.SQL) {
            return;
        }
        EmailPassword.signUp(tenant, storage, main, "a@example.com", "password");
        EmailPassword.signUp(tenant, storage, main, "b@example.com", "password");
        ApproximateUserCount counts = ApproximateUserCount.getInstance(main, tenant.toAppIdentifier());
        Storage[] pools = new Storage[3];
        CountDownLatch allCounting = new CountDownLatch(pools.length);
        for (int i = 0; i < pools.length; i++) {
            pools[i] = asUserPool(storage, "pool-" + i, (method, methodArgs) -> {
                if (method.equals("getUsersCount") || method.equals("getOrComputeAppUsersCount")) {
                    allCounting.countDown();
                    assertTrue("every pool's count was started", allCounting.await(10, TimeUnit.SECONDS));
                }
            });
        }

        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<ApproximateUserCount.ApproximateCountResult> served = caller.submit(
                    () -> counts.serveAppForTesting(main, pools));
            assertEquals(6, served.get(20, TimeUnit.SECONDS).count);
        } finally {
            caller.shutdownNow();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // /users/count keeps its released contract: a request that finds no count yet waits for it and is answered with
    // the count, never with "try again later".
    @Test
    public void aRequestWaitsForACountThatIsStillRunning() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        EmailPassword.signUp(main, "a@example.com", "password");
        EmailPassword.signUp(main, "b@example.com", "password");

        JsonObject response = HttpRequestForTesting.sendGETRequest(main, "", "http://localhost:3567/users/count",
                null, 1000, 10000, null, SemVer.v5_7.get(), "");
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(2, response.get("count").getAsLong());
        assertTrue(response.get("approximate").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // When a new count fails and there is no anchor yet, the last count stored in the database is served, marked
    // stale, instead of an error.
    @Test
    public void aFailedCountFallsBackToTheLastStoredCount() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        Storage storage = StorageLayer.getStorage(main);
        if (storage.getType() != STORAGE_TYPE.SQL || !(storage instanceof UsersCountCacheStorage)) {
            return;
        }
        TenantIdentifier tenant = process.getAppForTesting();
        EmailPassword.signUp(main, "a@example.com", "password");
        EmailPassword.signUp(main, "b@example.com", "password");
        // a count stored by "another core" earlier
        assertEquals(2, ((UsersCountCacheStorage) storage).getOrComputeTenantUsersCount(tenant, null, 600000).count);

        Storage failing = interceptingStorage(storage, method -> {
            if (method.equals("getOrComputeTenantUsersCount")) {
                throw new StorageQueryException(new java.sql.SQLException("simulated count failure"));
            }
        });
        ApproximateUserCount.ApproximateCountResult served = ApproximateUserCount
                .getInstance(main, tenant.toAppIdentifier()).serve(main, tenant, failing);
        assertEquals(2, served.count);
        assertTrue(served.stale);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A failed daily job is retried a few minutes later (here: milliseconds) instead of the next day.
    @Test
    public void aFailedDailyJobIsRetriedSoon() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        RetrySoon.enableForTesting(20);

        AtomicInteger runs = new AtomicInteger();
        RetrySoon.Job job = app -> {
            if (runs.incrementAndGet() < 3) {
                throw new Exception("fails twice");
            }
        };
        try {
            RetrySoon.run(process.getProcess(), "TestJob", new AppIdentifier(null, null), job);
            fail("the first run fails");
        } catch (Exception expected) {
        }
        long deadline = System.currentTimeMillis() + 5000;
        while (runs.get() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(3, runs.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Every unparseable lifecycle event after a scope's anchor forces a new count, not only the pool's earliest one:
    // the shared window starts at the app's (older) anchor, so after a recount past the first corrupt event a later
    // one must still be caught.
    @Test
    public void everyUnparseableEventAfterTheAnchorForcesANewCount() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        TenantIdentifier tenant = process.getAppForTesting();
        Storage storage = StorageLayer.getStorage(tenant, main);
        ApproximateUserCount counts = ApproximateUserCount.getInstance(main, tenant.toAppIdentifier());
        AtomicInteger tenantCounts = new AtomicInteger();
        Storage counting = interceptingStorage(storage, (method, methodArgs) -> {
            if ((method.equals("getOrComputeTenantUsersCount") || method.equals("getUsersCount"))
                    && methodArgs[0] instanceof TenantIdentifier) {
                tenantCounts.incrementAndGet();
            }
        });

        EmailPassword.signUp(tenant, storage, main, "a@example.com", "password");
        counts.serveApp(main); // the app's anchor, older than every corrupt event below
        assertEquals(1, counts.serve(main, tenant, counting).count);
        assertEquals(1, tenantCounts.get());

        insertUnparseableEvent(storage, tenant);
        assertEquals(1, counts.serve(main, tenant, counting).count);
        assertEquals("the first corrupt event forces a new count", 2, tenantCounts.get());

        insertUnparseableEvent(storage, tenant);
        assertEquals(1, counts.serve(main, tenant, counting).count);
        assertEquals("a corrupt event after the new anchor forces another", 3, tenantCounts.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // An unparseable event does not pin the pool's roll-forward: the anchors before it (here the app's, which is not
    // served again for a while) are dropped instead of being kept just before it, so a later burst still rolls the
    // other anchors forward instead of forcing a full count on every call.
    @Test
    public void anUnparseableEventDoesNotPinTheRollForward() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        TenantIdentifier tenant = process.getAppForTesting();
        Storage storage = StorageLayer.getStorage(tenant, main);
        ApproximateUserCount counts = ApproximateUserCount.getInstance(main, tenant.toAppIdentifier());
        AtomicInteger tenantCounts = new AtomicInteger();
        Storage counting = interceptingStorage(storage, (method, methodArgs) -> {
            if ((method.equals("getOrComputeTenantUsersCount") || method.equals("getUsersCount"))
                    && methodArgs[0] instanceof TenantIdentifier) {
                tenantCounts.incrementAndGet();
            }
        });

        EmailPassword.signUp(tenant, storage, main, "a@example.com", "password");
        assertEquals(1, counts.serveApp(main).count); // the app's anchor, before the corrupt event
        insertUnparseableEvent(storage, tenant);
        assertEquals(1, counts.serve(main, tenant, counting).count); // counted after the corrupt event
        int afterCorrupt = tenantCounts.get();

        ApproximateUserCount.setPageSizeAndRollMarginForTesting(3, 0);
        for (int i = 0; i < 8; i++) {
            EmailPassword.signUp(tenant, storage, main, "burst" + i + "@example.com", "password");
        }
        assertEquals(9, counts.serve(main, tenant, counting).count);
        assertEquals(9, counts.serve(main, tenant, counting).count);
        assertEquals("the burst was rolled forward, not counted", afterCorrupt, tenantCounts.get());
        assertEquals("the app's dropped anchor is counted again", 9, counts.serveApp(main).count);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // More than a page of events in one millisecond (a bulk-import partition is stamped with one time) cannot be split
    // into pages: the anchors before it are dropped and their scopes counted once after it, instead of every later
    // call counting again.
    @Test
    public void aMillisecondHoldingMoreThanAPageIsCountedPastOnce() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        TenantIdentifier tenant = process.getAppForTesting();
        Storage storage = StorageLayer.getStorage(tenant, main);
        ApproximateUserCount counts = ApproximateUserCount.getInstance(main, tenant.toAppIdentifier());
        AtomicInteger tenantCounts = new AtomicInteger();
        Storage counting = interceptingStorage(storage, (method, methodArgs) -> {
            if ((method.equals("getOrComputeTenantUsersCount") || method.equals("getUsersCount"))
                    && methodArgs[0] instanceof TenantIdentifier) {
                tenantCounts.incrementAndGet();
            }
        });

        EmailPassword.signUp(tenant, storage, main, "a@example.com", "password");
        assertEquals(1, counts.serveApp(main).count); // the app's anchor: the pool's oldest
        assertEquals(1, counts.serve(main, tenant, counting).count);

        ApproximateUserCount.setPageSizeAndRollMarginForTesting(3, 0);
        Thread.sleep(5);
        long sameMs = System.currentTimeMillis();
        for (int i = 0; i < 4; i++) { // one page + 1, all in one millisecond
            ((ActivityLogStorage) storage).createActivityLogEntry(tenant, new AuditLogEvent(tenant.getAppId(), null,
                    "u" + i, "u" + i, LifecycleEventType.USER_CREATION.getValue(), "success", null, null, sameMs,
                    LifecycleEventPayload.forUserCreation("public").toJson()));
        }
        Thread.sleep(20);
        counts.serve(main, tenant, counting);
        int afterFirst = tenantCounts.get();
        for (int i = 0; i < 2; i++) {
            Thread.sleep(20);
            counts.serve(main, tenant, counting);
        }
        assertEquals("counted past the burst once, not on every call", afterFirst, tenantCounts.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // The license stats' batch runs at most 2 of its counts at a time, so it never fills the request-count pool: a
    // request for a tenant without an anchor still gets its count started right away, instead of queueing behind
    // every tenant of the batch.
    @Test
    public void aBatchOfTenantCountsLeavesRoomForRequestCounts() throws Exception {
        TestingProcessManager.TestingProcess process = startWithLinkingAndTenants();
        Main main = process.getProcess();
        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        TenantIdentifier publicTenant = TenantIdentifier.BASE_TENANT;
        List<TenantIdentifier> batch = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            TenantIdentifier tenant = new TenantIdentifier(null, null, "batch" + i);
            Multitenancy.addNewOrUpdateAppOrTenant(main, publicTenant, new TenantConfig(tenant,
                    new EmailPasswordConfig(true), new ThirdPartyConfig(true, null), new PasswordlessConfig(true),
                    null, null, new JsonObject()));
            batch.add(tenant);
        }
        ApproximateUserCount counts = ApproximateUserCount.getInstance(main, publicTenant.toAppIdentifier());
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        CountDownLatch releaseBatch = new CountDownLatch(1);
        Storage storage = interceptingStorage(StorageLayer.getStorage(main), (method, methodArgs) -> {
            if ((method.equals("getOrComputeTenantUsersCount") || method.equals("getUsersCount"))
                    && batch.contains(methodArgs[0])) {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                try {
                    assertTrue(releaseBatch.await(30, TimeUnit.SECONDS));
                } finally {
                    running.decrementAndGet();
                }
            }
        });

        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<?> batchServed = callers.submit(() -> counts.serveTenantsForTesting(main, batch, storage));
            waitFor(() -> running.get() >= 2);
            Thread.sleep(500); // time for any batch count past the bound to start
            assertEquals(2, maxRunning.get());

            Future<Long> request = callers.submit(() -> counts.serve(main, publicTenant, storage).count);
            assertEquals(AuthRecipe.getUsersCountForTenant(publicTenant, StorageLayer.getStorage(main), null),
                    (long) request.get(10, TimeUnit.SECONDS));

            releaseBatch.countDown();
            batchServed.get(20, TimeUnit.SECONDS);
        } finally {
            releaseBatch.countDown();
            callers.shutdownNow();
        }
        assertEquals(2, maxRunning.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A request doesn't wait behind another call's roll-forward of the shared window, and doesn't start a full count
    // of its own either: it is served its anchor as is, marked stale, with the anchor's snapshot as asOf.
    @Test
    public void aRequestDoesNotWaitBehindAnotherScopesRollForward() throws Exception {
        TestingProcessManager.TestingProcess process = startWithLinkingAndTenants();
        Main main = process.getProcess();
        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        TenantIdentifier publicTenant = TenantIdentifier.BASE_TENANT;
        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");
        Multitenancy.addNewOrUpdateAppOrTenant(main, publicTenant, new TenantConfig(t1,
                new EmailPasswordConfig(true), new ThirdPartyConfig(true, null), new PasswordlessConfig(true),
                null, null, new JsonObject()));
        Storage storage = StorageLayer.getStorage(main);
        ApproximateUserCount counts = ApproximateUserCount.getInstance(main, publicTenant.toAppIdentifier());
        EmailPassword.signUp(publicTenant, storage, main, "p@example.com", "password");
        EmailPassword.signUp(t1, storage, main, "t@example.com", "password");
        assertEquals(1, counts.serve(main, publicTenant, storage).count);
        assertEquals(1, counts.serve(main, t1, storage).count);

        ApproximateUserCount.setPageSizeAndRollMarginForTesting(3, 0);
        for (int i = 0; i < 8; i++) {
            EmailPassword.signUp(publicTenant, storage, main, "burst" + i + "@example.com", "password");
        }
        CountDownLatch rolling = new CountDownLatch(1);
        CountDownLatch releaseRoll = new CountDownLatch(1);
        Storage slowRoll = interceptingStorage(storage, (method, methodArgs) -> {
            if (method.equals("getActivityLogEntriesForApp") && Arrays.stream(Thread.currentThread().getStackTrace())
                    .anyMatch(frame -> frame.getMethodName().equals("rollForward"))) {
                rolling.countDown();
                assertTrue(releaseRoll.await(30, TimeUnit.SECONDS));
            }
        });

        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<Long> roller = callers.submit(() -> counts.serve(main, publicTenant, slowRoll).count);
            assertTrue(rolling.await(10, TimeUnit.SECONDS));

            long t1Snapshot = counts.anchorSnapshotForTesting(t1, storage);
            KeyValueInfo t1Stored = storage.getKeyValue(t1, "TENANT_USERS_COUNT");
            Future<ApproximateUserCount.ApproximateCountResult> request = callers.submit(
                    () -> counts.serve(main, t1, storage));
            ApproximateUserCount.ApproximateCountResult served = request.get(15, TimeUnit.SECONDS);
            assertEquals(1, served.count);
            assertTrue("served as stale", served.stale);
            assertEquals("asOf is the anchor's own snapshot", t1Snapshot, served.asOf);
            if (storage instanceof UsersCountCacheStorage) {
                assertEquals("no new full count", t1Stored.createdAtTime,
                        storage.getKeyValue(t1, "TENANT_USERS_COUNT").createdAtTime);
            }

            releaseRoll.countDown();
            assertEquals(9L, (long) roller.get(20, TimeUnit.SECONDS));
        } finally {
            releaseRoll.countDown();
            callers.shutdownNow();
        }
        assertEquals(1, counts.serve(main, t1, storage).count);
        assertEquals(9, counts.serve(main, publicTenant, storage).count);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A lifecycle event of a valid type whose payload is valid JSON of the wrong shape (so it does not parse).
    private static void insertUnparseableEvent(Storage storage, TenantIdentifier tenant) throws Exception {
        Thread.sleep(5);
        ((ActivityLogStorage) storage).createActivityLogEntry(tenant, new AuditLogEvent(tenant.getAppId(), null,
                "recipe-user", "recipe-user", LifecycleEventType.USER_DELETION.getValue(), "success", null, null,
                System.currentTimeMillis(), "{\"schemaVersion\":1,\"type\":\"user_deletion\"}"));
        Thread.sleep(5);
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

    private static void assertCountsExact(Main main, ApproximateUserCount counts, AppIdentifier app,
                                          TenantIdentifier... tenants) throws Exception {
        Storage[] storages = StorageLayer.getStoragesForApp(main, app);
        assertEquals("app", AuthRecipe.getUsersCountAcrossAllTenants(app, storages, null),
                counts.serveApp(main).count);
        for (TenantIdentifier tenant : tenants) {
            assertEquals(tenant.getTenantId(), AuthRecipe.getUsersCountForTenant(tenant,
                            StorageLayer.getStorage(tenant, main), null),
                    counts.serveTenant(main, tenant).count);
        }
    }

    private static TestingProcessManager.TestingProcess startWithLinkingAndTenants() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess()).setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                new EE_FEATURES[]{EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        return process;
    }

    private interface Hook {
        void before(String method) throws Exception;
    }

    private interface ArgsHook {
        void before(String method, Object[] methodArgs) throws Exception;
    }

    // Wraps the real storage, running the hook before every call; every call then goes to the real storage.
    private static Storage interceptingStorage(Storage delegate, Hook hook) {
        return interceptingStorage(delegate, (method, methodArgs) -> hook.before(method));
    }

    private static Storage interceptingStorage(Storage delegate, ArgsHook hook) {
        return asUserPool(delegate, null, hook);
    }

    // The same, presented as a user pool of its own (poolId, or the real storage's if null).
    private static Storage asUserPool(Storage delegate, String poolId, ArgsHook hook) {
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
                    if (poolId != null && method.getName().equals("getUserPoolId")) {
                        return poolId;
                    }
                    hook.before(method.getName(), methodArgs);
                    try {
                        return method.invoke(delegate, methodArgs);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
