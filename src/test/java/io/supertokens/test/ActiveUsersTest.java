package io.supertokens.test;

import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
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

import java.util.HashMap;

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

        ActiveUsers.updateLastActive(main, "user1");
        ActiveUsers.updateLastActive(main, "user2");

        assert ActiveUsers.countUsersActiveSince(main, now) == 2;

        Thread.sleep(1);

        long now2 = System.currentTimeMillis();

        ActiveUsers.updateLastActive(main, "user1");

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

        ActiveUsers.updateLastActive(main, "user1");
        ActiveUsers.updateLastActive(main, "user2");

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

        ActiveUsers.updateLastActive(main, "user1");

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
}
