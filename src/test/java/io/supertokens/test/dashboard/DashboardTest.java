/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.dashboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonArray;

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.dashboard.DashboardSessionInfo;
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
        DashboardUser[] dashboardUsers = Dashboard.getAllDashboardUsers(process.getProcess());
        assertTrue(dashboardUsers.length == 0);

        String email = "test@example.com";

        // create Dashboard user
        Dashboard.signUpDashboardUser(process.getProcess(), email, "testPass123");

        // check that Dashboard user was created
        dashboardUsers = Dashboard.getAllDashboardUsers(process.getProcess());
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
        DashboardUser[] dashboardUsers = Dashboard.getAllDashboardUsers(process.getProcess());
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
        dashboardUsers = Dashboard.getAllDashboardUsers(process.getProcess());
        assertTrue(dashboardUsers.length == 1);
        assertEquals(dashboardUsers[0].email, email);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingAUserAndSessionAndDeletingTheUserDeletesTheSession() throws Exception {

        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String email = "test@example.com";
        String password = "password123";

        // create Dashboard user
        DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), email, password);

        // create dashboard user session
        String sessionId = Dashboard.signInDashboardUser(process.getProcess(), email, password);

        // check that session exists
        {
            DashboardSessionInfo[] sessionInfo =  Dashboard.getAllDashboardSessionsForUser(process.getProcess(), user.userId);
            assertEquals(1, sessionInfo.length);
            assertEquals(sessionId, sessionInfo[0].sessionId);
        }

        // delete user
        assertTrue(Dashboard.deleteUserWithUserId(process.getProcess(), user.userId));

        // check that session no longer exists
        assertFalse(Dashboard.isValidUserSession(process.getProcess(), sessionId));
        assertEquals(0, Dashboard.getAllDashboardSessionsForUser(process.getProcess(), user.userId).length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
