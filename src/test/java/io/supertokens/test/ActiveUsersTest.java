package io.supertokens.test;

import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor;
import com.google.gson.JsonObject;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.ProcessState;

import java.util.HashMap;

public class ActiveUsersTest {

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
    public void updateAndCountUserLastActiveTest() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            assert (false);
        }

        Main main = process.getProcess();
        long now = System.currentTimeMillis();

        assert ActiveUsers.countUsersActiveSince(main, now) == 0;

        ActiveUsers.updateLastActive(main, "user1");
        ActiveUsers.updateLastActive(main, "user2");

        assert ActiveUsers.countUsersActiveSince(main, now) == 2;

        long now2 = System.currentTimeMillis();

        ActiveUsers.updateLastActive(main, "user1");

        assert ActiveUsers.countUsersActiveSince(main, now2) == 1; // only user1 is counted
        assert ActiveUsers.countUsersActiveSince(main, now) == 2; // user1 and user2 are counted
    }

    @Test
    public void activeUserCountAPITest() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            assert (false);
        }

        Main main = process.getProcess();
        long now = System.currentTimeMillis();

        HashMap<String, String> params = new HashMap<>();
        params.put("since", Long.toString(now));

        JsonObject res = HttpRequestForTesting.sendGETRequest(
                process.getProcess(),
                "",
                "http://localhost:3567/users/count/active",
                params,
                1000,
                1000,
                null,
                Utils.getCdiVersionLatestForTests(),
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
                Utils.getCdiVersionLatestForTests(),
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
                Utils.getCdiVersionLatestForTests(),
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
                Utils.getCdiVersionLatestForTests(),
                "");

        assert res.get("status").getAsString().equals("OK");
        assert res.get("count").getAsInt() == 2;
    }

}
