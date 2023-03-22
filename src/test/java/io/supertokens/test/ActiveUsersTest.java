package io.supertokens.test;

import static org.junit.Assert.assertNotNull;

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

        assert ActiveUsers.countUsersActiveSince(main, now) == 2; // user1 just got updated
        assert ActiveUsers.countUsersActiveSince(main, now2) == 1; // only user1 is counted
    }

}
