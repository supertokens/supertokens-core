package io.supertokens.test.dashboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateEmailException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;

public class DashboardTest {
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
    public void testCreatingDashboardUsers() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // check that no Dashboard users exist
        DashboardUser[] dashboardUsers =  Dashboard.getAllDashboardUsers(process.getProcess());
        assertTrue(dashboardUsers.length == 0);

        String email = "test@example.com";

        // create Dashboard user
        Dashboard.signUpDashboardUser(process.getProcess(), email, "testPass123");

        // check that Dashboard user was created 
        dashboardUsers =  Dashboard.getAllDashboardUsers(process.getProcess());
        assertTrue(dashboardUsers.length == 1);
        assertEquals(dashboardUsers[0].email, email);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingDashboardUsersWithDuplicateEmail() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // check that no Dashboard users exist
        DashboardUser[] dashboardUsers =  Dashboard.getAllDashboardUsers(process.getProcess());
        assertTrue(dashboardUsers.length == 0);

        String email = "test@example.com";

        // create Dashboard user
        Dashboard.signUpDashboardUser(process.getProcess(), email, "testPass123");

        // create Dashboard user with duplicate email
        Exception error = null;
        try {
            Dashboard.signUpDashboardUser(process.getProcess(), email, "testPass123");
        } catch (DuplicateEmailException e) {
            error = e;
        }
        assertNotNull(error);
        assertTrue(error instanceof DuplicateEmailException);
        
        // check that no duplicate Dashboard user was created 
        dashboardUsers =  Dashboard.getAllDashboardUsers(process.getProcess());
        assertTrue(dashboardUsers.length == 1);
        assertEquals(dashboardUsers[0].email, email);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    } 
}
