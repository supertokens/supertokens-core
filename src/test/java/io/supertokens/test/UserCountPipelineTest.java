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
import io.supertokens.authRecipe.exception.UsersCountNotReadyException;
import io.supertokens.cronjobs.RetrySoon;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.UsersCountCacheStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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
        assertEquals(1, counts.serveTenant(main, tenant, null, Long.MAX_VALUE).count);
        long primedSnapshot = counts.anchorSnapshotForTesting(tenant, storage);
        KeyValueInfo stored = storage.getKeyValue(tenant, "TENANT_USERS_COUNT");

        for (int i = 0; i < 8; i++) {
            EmailPassword.signUp(tenant, storage, main, "burst" + i + "@example.com", "password");
        }
        assertEquals(9, counts.serveTenant(main, tenant, null, Long.MAX_VALUE).count);
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
                        null), counts.serveTenant(main, tenants[3], null, Long.MAX_VALUE).count);
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

    // A roll-forward for a request with a deadline stops at the deadline (the request gets "try again later"), keeps
    // what it rolled so far, and the retry carries on from there.
    @Test
    public void aRollForwardStopsAtTheDeadlineAndKeepsItsProgress() throws Exception {
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
        Storage slowReads = interceptingStorage(storage, (method, methodArgs) -> {
            if (method.equals("getActivityLogEntriesForApp")) {
                Thread.sleep(100);
            }
        });

        EmailPassword.signUp(tenant, storage, main, "first@example.com", "password");
        assertEquals(1, counts.serve(main, tenant, slowReads).count);
        long primedSnapshot = counts.anchorSnapshotForTesting(tenant, storage);
        ApproximateUserCount.setPageSizeAndRollMarginForTesting(3, 0);
        for (int i = 0; i < 30; i++) {
            EmailPassword.signUp(tenant, storage, main, "burst" + i + "@example.com", "password");
        }

        long start = System.currentTimeMillis();
        try {
            counts.serve(main, tenant, slowReads, 250);
            fail("rolling 10 pages at 100ms a read does not fit 250ms");
        } catch (UsersCountNotReadyException expected) {
        }
        long took = System.currentTimeMillis() - start;
        assertTrue("held on past the deadline: " + took + "ms", took < 800);
        assertTrue("kept what it rolled", counts.anchorSnapshotForTesting(tenant, storage) > primedSnapshot);
        assertEquals(31, counts.serve(main, tenant, slowReads).count);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // An app count across several user pools starts every pool's missing count before waiting for any, so the first
    // "try again later" already has all of them running.
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
        CountDownLatch release = new CountDownLatch(1);
        Set<String> counting = ConcurrentHashMap.newKeySet();
        Storage[] pools = new Storage[3];
        for (int i = 0; i < pools.length; i++) {
            String poolId = "pool-" + i;
            pools[i] = asUserPool(storage, poolId, (method, methodArgs) -> {
                if (method.equals("getUsersCount") || method.equals("getOrComputeAppUsersCount")) {
                    counting.add(poolId);
                    release.await(10, TimeUnit.SECONDS);
                }
            });
        }

        try {
            counts.serveAppForTesting(main, pools, 100);
            fail("no pool's count is ready");
        } catch (UsersCountNotReadyException expected) {
        }
        long deadline = System.currentTimeMillis() + 5000;
        while (counting.size() < pools.length && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(Set.of("pool-0", "pool-1", "pool-2"), counting);

        release.countDown();
        assertEquals(6, counts.serveAppForTesting(main, pools, Long.MAX_VALUE).count);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A /users/count request that cannot wait for a count still being computed gets 503 (with Retry-After); the
    // count keeps running and serves the retry.
    @Test
    public void aRequestThatCannotWaitIsToldToTryAgainLater() throws Exception {
        Utils.setValueInConfig("users_count_request_wait_ms", "0");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Main main = process.getProcess();
        if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        EmailPassword.signUp(main, "a@example.com", "password");
        EmailPassword.signUp(main, "b@example.com", "password");

        try {
            HttpRequestForTesting.sendGETRequest(main, "", "http://localhost:3567/users/count", null, 1000, 1000,
                    null, SemVer.v5_7.get(), "");
            fail("the first request has no count yet and may not wait for one");
        } catch (HttpResponseException e) {
            assertEquals(503, e.statusCode);
        }

        long deadline = System.currentTimeMillis() + 10000;
        while (true) {
            try {
                JsonObject response = HttpRequestForTesting.sendGETRequest(main, "",
                        "http://localhost:3567/users/count", null, 1000, 1000, null, SemVer.v5_7.get(), "");
                assertEquals(2, response.get("count").getAsLong());
                break;
            } catch (HttpResponseException e) {
                assertEquals(503, e.statusCode);
                assertTrue("the count never became ready", System.currentTimeMillis() < deadline);
                Thread.sleep(50);
            }
        }

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

    private static void assertCountsExact(Main main, ApproximateUserCount counts, AppIdentifier app,
                                          TenantIdentifier... tenants) throws Exception {
        Storage[] storages = StorageLayer.getStoragesForApp(main, app);
        assertEquals("app", AuthRecipe.getUsersCountAcrossAllTenants(app, storages, null),
                counts.serveApp(main, null, Long.MAX_VALUE).count);
        for (TenantIdentifier tenant : tenants) {
            assertEquals(tenant.getTenantId(), AuthRecipe.getUsersCountForTenant(tenant,
                            StorageLayer.getStorage(tenant, main), null),
                    counts.serveTenant(main, tenant, null, Long.MAX_VALUE).count);
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
