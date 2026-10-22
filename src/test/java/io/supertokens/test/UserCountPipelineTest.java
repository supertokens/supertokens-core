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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    // Wraps the real storage, running the hook before every call; every call then goes to the real storage.
    private static Storage interceptingStorage(Storage delegate, Hook hook) {
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
                    hook.before(method.getName());
                    try {
                        return method.invoke(delegate, methodArgs);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
