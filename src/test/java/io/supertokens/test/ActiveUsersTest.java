package io.supertokens.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.cronjobs.rollupUserLastActive.RollupUserLastActive;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.ActiveUsersStorage;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.test.multitenant.api.TestMultitenancyAPIHelper;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class ActiveUsersTest {

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
        ActiveUsers.clearCacheForTesting();
    }

    @Test
    public void updateAndCountUserLastActiveTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Main main = process.getProcess();
        long now = System.currentTimeMillis();

        assert ActiveUsers.countUsersActiveSince(main, now) == 0;

        // Real users so their auth records live on this storage — the fold credits only users whose
        // app_id_to_user_id mapping is present. Signup itself emits user_creation (a folded activity event).
        String user1 = signUpUserOnPublicTenant(process, "user1@example.com");
        String user2 = signUpUserOnPublicTenant(process, "user2@example.com");
        ActiveUsers.updateLastActive(main, user1);
        ActiveUsers.updateLastActive(main, user2);

        // The rollup cron is the sole writer of user_last_active (PLAN-011 cutover): activity reaches the
        // projection only through a fold, so force one before reading the count.
        RollupUserLastActive.runOnceForAllStoragesForTesting(main);
        assert ActiveUsers.countUsersActiveSince(main, now) == 2;

        Thread.sleep(1);

        long now2 = System.currentTimeMillis();

        // Throttle would otherwise skip this update since user1 was just touched above; clear so
        // the test exercises a fresh activity-log emit at now2.
        ActiveUsers.clearCacheForTesting();
        ActiveUsers.updateLastActive(main, user1);

        RollupUserLastActive.runOnceForAllStoragesForTesting(main);
        assert ActiveUsers.countUsersActiveSince(main, now2) == 1; // only user1 is counted
        assert ActiveUsers.countUsersActiveSince(main, now) == 2; // user1 and user2 are counted
    }

    @Test
    public void activeUserCountAPITest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Main main = process.getProcess();
        long now = System.currentTimeMillis();

        HashMap<String, String> params = new HashMap<>();

        HttpResponseException e =
                assertThrows(
                        HttpResponseException.class,
                        () -> {
                            HttpRequestForTesting.sendGETRequest(
                                    process.getProcess(),
                                    "",
                                    "http://localhost:3567/users/count/active",
                                    params,
                                    1000,
                                    1000,
                                    null,
                                    Utils.getCdiVersionStringLatestForTests(),
                                    "");
                        }
                );

        assert e.statusCode == 400;
        assert e.getMessage().contains("Field name 'since' is missing in GET request");

        params.put("since", "not a number");
        e =
                assertThrows(
                        HttpResponseException.class,
                        () -> {
                            HttpRequestForTesting.sendGETRequest(
                                    process.getProcess(),
                                    "",
                                    "http://localhost:3567/users/count/active",
                                    params,
                                    1000,
                                    1000,
                                    null,
                                    Utils.getCdiVersionStringLatestForTests(),
                                    "");
                        }
                );

        assert e.statusCode == 400;
        assert e.getMessage().contains("Field name 'since' must be a long in the GET request");

        params.put("since", "-1");
        e =
                assertThrows(
                        HttpResponseException.class,
                        () -> {
                            HttpRequestForTesting.sendGETRequest(
                                    process.getProcess(),
                                    "",
                                    "http://localhost:3567/users/count/active",
                                    params,
                                    1000,
                                    1000,
                                    null,
                                    Utils.getCdiVersionStringLatestForTests(),
                                    "");
                        }
                );

        assert e.statusCode == 400;
        assert e.getMessage().contains("'since' query parameter must be >= 0");


        params.put("since", Long.toString(now));

        JsonObject res = HttpRequestForTesting.sendGETRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/users/count/active",
                params,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "");

        assert res.get("status").getAsString().equals("OK");
        assert res.get("count").getAsInt() == 0;

        // Real users so their auth records live on this storage — the fold credits only users whose
        // app_id_to_user_id mapping is present. Signup itself emits user_creation (a folded activity event).
        String user1 = signUpUserOnPublicTenant(process, "user1@example.com");
        String user2 = signUpUserOnPublicTenant(process, "user2@example.com");
        ActiveUsers.updateLastActive(main, user1);
        ActiveUsers.updateLastActive(main, user2);

        // Sole-writer cutover: fold the emitted activity into the projection before the API reads it.
        RollupUserLastActive.runOnceForAllStoragesForTesting(main);

        res = HttpRequestForTesting.sendGETRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/users/count/active",
                params,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "");

        assert res.get("status").getAsString().equals("OK");
        assert res.get("count").getAsInt() == 2;

        long now2 = System.currentTimeMillis();

        // See clearCacheForTesting above — throttle would skip the second update otherwise.
        ActiveUsers.clearCacheForTesting();
        ActiveUsers.updateLastActive(main, user1);

        RollupUserLastActive.runOnceForAllStoragesForTesting(main);

        params.put("since", Long.toString(now2));
        res = HttpRequestForTesting.sendGETRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/users/count/active",
                params,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "");

        assert res.get("status").getAsString().equals("OK");
        assert res.get("count").getAsInt() == 1;

        params.put("since", Long.toString(now));
        res = HttpRequestForTesting.sendGETRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/users/count/active",
                params,
                1000,
                1000,
                null,
                Utils.getCdiVersionStringLatestForTests(),
                "");

        assert res.get("status").getAsString().equals("OK");
        assert res.get("count").getAsInt() == 2;
    }

    @Test
    public void testMauSeriesWithActivityAcrossDayBuckets() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Main main = process.getProcess();
        final long day = 24 * 60 * 60 * 1000L;
        final long halfDay = 12 * 60 * 60 * 1000L;
        long now = System.currentTimeMillis();

        AppIdentifier appIdentifier = ResourceDistributor.getAppForTesting().toAppIdentifier();
        Storage storage = StorageLayer.getStorage(main);

        // Both the in-memory storage and the postgresql plugin expose a @TestOnly
        // updateLastActive(AppIdentifier, String, long) overload for backdating. The plugin class
        // is not on the compile-time classpath, so look the method up reflectively.
        Method backdate = storage.getClass().getMethod("updateLastActive", AppIdentifier.class, String.class,
                long.class);

        // existing MAU tests only create activity at "now" (bucket 0), so the summation across
        // buckets was never exercised. Backdate activity into several distinct day buckets,
        // leave a middle bucket empty, and add activity beyond the 31 day window.
        backdate.invoke(storage, appIdentifier, "bucket0-user", now - halfDay);
        backdate.invoke(storage, appIdentifier, "bucket1-user-a", now - day - halfDay);
        backdate.invoke(storage, appIdentifier, "bucket1-user-b", now - day - halfDay);
        // bucket 2 is intentionally left empty
        backdate.invoke(storage, appIdentifier, "bucket3-user", now - 3 * day - halfDay);
        backdate.invoke(storage, appIdentifier, "bucket30-user", now - 30 * day - halfDay);
        backdate.invoke(storage, appIdentifier, "outside-window-user", now - 32 * day);

        Map<Integer, Integer> buckets = ((ActiveUsersStorage) storage)
                .countUsersActiveSinceGroupedByDay(appIdentifier, now - 31 * day, now);

        assertEquals(1, buckets.getOrDefault(0, 0).intValue());
        assertEquals(2, buckets.getOrDefault(1, 0).intValue());
        assertEquals(0, buckets.getOrDefault(2, 0).intValue());
        assertEquals(1, buckets.getOrDefault(3, 0).intValue());
        assertEquals(1, buckets.getOrDefault(30, 0).intValue());

        // the running total of buckets 0..i must equal the cumulative count for the same threshold
        int runningTotal = 0;
        for (int i = 0; i <= 30; i++) {
            runningTotal += buckets.getOrDefault(i, 0);
            assertEquals("running total of buckets 0.." + i + " should match countUsersActiveSince",
                    ActiveUsers.countUsersActiveSince(main, now - (i + 1) * day), runningTotal);
        }
        assertEquals(5, runningTotal); // outside-window-user is not part of the series

        // the maus series is built from the bucketed query and must reflect the same numbers
        JsonArray maus = FeatureFlag.getInstance(main).getPaidFeatureStats().get("maus").getAsJsonArray();
        assertEquals(31, maus.size());
        assertEquals(1, maus.get(0).getAsInt());
        assertEquals(3, maus.get(1).getAsInt());
        assertEquals(3, maus.get(2).getAsInt()); // empty bucket keeps the previous total
        assertEquals(4, maus.get(3).getAsInt());
        assertEquals(4, maus.get(29).getAsInt());
        assertEquals(5, maus.get(30).getAsInt()); // bucket 30 only shows up in the last value

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatActiveUserDataIsSavedInPublicTenantStorage() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        { // Create a tenant
            JsonObject coreConfig = new JsonObject();

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

            TestMultitenancyAPIHelper.createTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    "t1", true, true, true,
                    coreConfig);
        }

        { // no active users yet
            HashMap<String, String> params = new HashMap<>();
            params.put("since", "0");
            JsonObject res = HttpRequestForTesting.sendGETRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/users/count/active",
                    params,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "");

            assert res.get("status").getAsString().equals("OK");
            assert res.get("count").getAsInt() == 0;
        }

        { // Sign up, which updates active users
            JsonObject responseBody = new JsonObject();
            responseBody.addProperty("email", "random@gmail.com");
            responseBody.addProperty("password", "validPass123");

            JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/t1/recipe/signup", responseBody, 1000, 1000, null, SemVer.v4_0.get(),
                    "emailpassword");
        }

        // Sole-writer cutover: the sign-up's last-active activity reaches the projection only via a fold.
        RollupUserLastActive.runOnceForAllStoragesForTesting(process.getProcess());

        { // 1 active user in the public tenant
            HashMap<String, String> params = new HashMap<>();
            params.put("since", "0");
            JsonObject res = HttpRequestForTesting.sendGETRequest(
                    process.getProcess(),
                    "",
                    "http://localhost:3567/users/count/active",
                    params,
                    1000,
                    1000,
                    null,
                    Utils.getCdiVersionStringLatestForTests(),
                    "");

            assert res.get("status").getAsString().equals("OK");
            assert res.get("count").getAsInt() == 1;
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * Finding #3 regression pin (per-storage projection). A user who signs up on a tenant with its own
     * database must still be counted by the app-level active-user count. Their {@code user_creation} folds
     * into the tenant's own storage; {@link ActiveUsers#countUsersActiveSince} sums over every storage backing
     * the app, so it must see them. Before the rework the count read only the app-public storage and returned
     * 0 for a separate-database tenant's sign-up-only user — this test failed on that code and passes now.
     * (On in-memory storage the "separate" pool collapses onto the one shared database, so the sum trivially
     * holds; the separate-storage path is exercised on the SQL plugins in CI.)
     */
    @Test
    public void activeUserCountSumsAcrossSeparateTenantStorages() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Main main = process.getProcess();
        AppIdentifier app = new TenantIdentifier(null, null, null).toAppIdentifier();

        { // a tenant t1 with its own user pool (a separate database)
            JsonObject coreConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), main)
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);
            TestMultitenancyAPIHelper.createTenant(main, new TenantIdentifier(null, null, null),
                    "t1", true, true, true, coreConfig);
        }

        assertEquals(0, ActiveUsers.countUsersActiveSince(main, app, 0));

        { // sign up on the separate-database tenant — the only credit is its user_creation on t1's storage
            JsonObject body = new JsonObject();
            body.addProperty("email", "separatedb@example.com");
            body.addProperty("password", "validPass123");
            HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                    "http://localhost:3567/t1/recipe/signup", body, 1000, 1000, null, SemVer.v4_0.get(),
                    "emailpassword");
        }

        RollupUserLastActive.runOnceForAllStoragesForTesting(main);

        assertEquals(1, ActiveUsers.countUsersActiveSince(main, app, 0));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Pins that the post-link reconcile (updateLastActiveAfterLinking) routes to the storage backing the linked
    // users, not the app's public-tenant storage. For a separate-database tenant the linked users live on the
    // tenant's own storage, so the stale-row delete must target that storage; routing to public-tenant storage
    // (pre-fix) makes the delete a silent no-op and the recipe user keeps counting. Failing-first on the SQL
    // plugins in CI, where the tenant genuinely has its own database — in-memory collapses the two pools onto one
    // DB, so the misroute is masked there (same masking as activeUserCountSumsAcrossSeparateTenantStorages).
    @Test
    public void activeUserReconcileAfterLinkingRoutesToTenantStorage() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES,
                        new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY, EE_FEATURES.ACCOUNT_LINKING});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Main main = process.getProcess();
        AppIdentifier app = new TenantIdentifier(null, null, null).toAppIdentifier();

        { // a tenant t1 with its own user pool (a separate database)
            JsonObject coreConfig = new JsonObject();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), main)
                    .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);
            TestMultitenancyAPIHelper.createTenant(main, new TenantIdentifier(null, null, null),
                    "t1", true, true, true, coreConfig);
        }

        TenantIdentifier t1 = new TenantIdentifier(null, null, "t1");
        Storage t1Storage = StorageLayer.getStorage(t1, main);

        // Two users signed up on the separate-database tenant; their auth records and activity live on t1's
        // storage. A rollup folds both user_creation events into t1's projection, so both count as active.
        AuthRecipeUserInfo recipeUser =
                EmailPassword.signUp(t1, t1Storage, main, "recipe@example.com", "validPass123");
        AuthRecipeUserInfo primaryUser =
                EmailPassword.signUp(t1, t1Storage, main, "primary@example.com", "validPass123");
        RollupUserLastActive.runOnceForAllStoragesForTesting(main);
        assertEquals(2, ActiveUsers.countUsersActiveSince(main, app, 0));

        // Link the recipe user into the primary on t1's storage — the account_linking event is emitted on t1 —
        // then run the post-link reconcile. Its stale-row delete of the recipe user must land on t1's storage;
        // asserted BEFORE any further rollup so we measure the direct delete (the latency optimization), not the
        // eventual rollup reconcile that would merge the two regardless.
        AuthRecipe.createPrimaryUser(main, app, t1Storage, primaryUser.getSupertokensUserId());
        AuthRecipe.linkAccounts(main, app, t1Storage, recipeUser.getSupertokensUserId(),
                primaryUser.getSupertokensUserId());
        ActiveUsers.updateLastActiveAfterLinking(main, app, t1Storage, primaryUser.getSupertokensUserId(),
                recipeUser.getSupertokensUserId());

        // The recipe user's projection row is gone from t1, so only the primary remains active. With the pre-fix
        // public-tenant routing the delete would have no-opped on a separate database and this would read 2.
        assertEquals(1, ActiveUsers.countUsersActiveSince(main, app, 0));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Signs a user up on the app's public tenant and returns its user id. Real users are needed because the
    // fold's app_id_to_user_id residency guard credits only users whose auth record lives on the storage.
    private String signUpUserOnPublicTenant(TestingProcessManager.TestingProcess process, String email)
            throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", "validPass123");
        JsonObject res = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signup", body, 1000, 1000, null, SemVer.v4_0.get(), "emailpassword");
        assertEquals("OK", res.get("status").getAsString());
        return res.get("user").getAsJsonObject().get("id").getAsString();
    }
}
