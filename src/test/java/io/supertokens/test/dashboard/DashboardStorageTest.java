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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.dashboard.DashboardSessionInfo;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.dashboard.exceptions.UserIdNotFoundException;
import io.supertokens.pluginInterface.dashboard.sqlStorage.DashboardSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;

public class DashboardStorageTest {
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
    public void testCreateNewDashboardUser() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a dashboard user
        String userId = "testUserId";
        String email = "test@example.com";
        String passwordHash = "testPasswordHash";

        DashboardSQLStorage dashboardSQLStorage = StorageLayer.getDashboardStorage(process.getProcess());

        {
            DashboardUser user = new DashboardUser(userId, email, passwordHash, System.currentTimeMillis());
            dashboardSQLStorage.createNewDashboardUser(user);

            // get dashboard users to verify that user was created
            DashboardUser[] users = dashboardSQLStorage.getAllDashboardUsers();
            assertEquals(1, users.length);
            assertEquals(user, users[0]);
        }

        {
            // test creating a user with a duplicate userId
            DashboardUser user = new DashboardUser(userId, "test2@example.com", passwordHash,
                    System.currentTimeMillis());
            Exception error = null;
            try {
                dashboardSQLStorage.createNewDashboardUser(user);
                throw new Exception("Should never come here");
            } catch (DuplicateUserIdException e) {
                error = e;
            }

            assertNotNull(error);
            assertTrue(error instanceof DuplicateUserIdException);
        }

        {
            // test creating a user with a duplicate email
            DashboardUser user = new DashboardUser("newUserId", "test@example.com", passwordHash,
                    System.currentTimeMillis());
            Exception error = null;
            try {
                dashboardSQLStorage.createNewDashboardUser(user);
                throw new Exception("Should never come here");
            } catch (DuplicateEmailException e) {
                error = e;
            }

            assertNotNull(error);
            assertTrue(error instanceof DuplicateEmailException);

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetDashboardUserFunctions() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a dashboard user
        String userId = "testUserId";
        String email = "test@example.com";
        String passwordHash = "testPasswordHash";

        DashboardSQLStorage dashboardSQLStorage = StorageLayer.getDashboardStorage(process.getProcess());

        // create a dashboardUser
        DashboardUser user = new DashboardUser(userId, email, passwordHash, System.currentTimeMillis());
        dashboardSQLStorage.createNewDashboardUser(user);

        {
            // retrieve user with email
            DashboardUser retrievedUser = dashboardSQLStorage.getDashboardUserByEmail(email);
            assertEquals(user, retrievedUser);
        }

        {
            // retrieve user with userId
            DashboardUser retrievedUser = dashboardSQLStorage.getDashboardUserByUserId(userId);
            assertEquals(user, retrievedUser);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetAllDashboardUsers() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create 10 dashboard users and make sure they are returned in order of them
        // being created
        for (int i = 0; i < 10; i++) {

            DashboardUser user = new DashboardUser(io.supertokens.utils.Utils.getUUID(), "test" + i + "@example.com",
                    "testPasswordHash", System.currentTimeMillis());
            StorageLayer.getDashboardStorage(process.getProcess()).createNewDashboardUser(user);
        }

        // retrieve all dashboard users, check that correctly created and returned in
        // the correct order.

        DashboardUser[] users = StorageLayer.getDashboardStorage(process.getProcess()).getAllDashboardUsers();
        assertEquals(10, users.length);

        for (int i = 0; i < 10; i++) {
            assertEquals("test" + i + "@example.com", users[i].email);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // test the deleteDashboardUserWithUserId function
    @Test
    public void testTheDeleteDashboardUserWithUserIdFunction() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        DashboardSQLStorage dashboardSQLStorage = StorageLayer.getDashboardStorage(process.getProcess());

        // check no user exists
        assertEquals(0, dashboardSQLStorage.getAllDashboardUsers().length);

        // create a user
        DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), "test@example.com", "testPass123");
        assertNotNull(user);

        // check that user was created
        assertEquals(1, dashboardSQLStorage.getAllDashboardUsers().length);

        // delete dashboard user
        assertTrue(dashboardSQLStorage.deleteDashboardUserWithUserId(user.userId));

        // check that no users exist
        assertEquals(0, dashboardSQLStorage.getAllDashboardUsers().length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testTheCreateNewDashboardUserSession() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        DashboardSQLStorage dashboardSQLStorage = StorageLayer.getDashboardStorage(process.getProcess());

        // create a dashboard session for a user that does not exist
        Exception error = null;
        try {
            dashboardSQLStorage.createNewDashboardUserSession("unknownUserId", "testSessionId", 0, 0);
            throw new Exception("Should never come here");
        } catch (UserIdNotFoundException e) {
            error = e;
        }

        assertNotNull(error);
        assertTrue(error instanceof UserIdNotFoundException);

        // create a user and create a session for the user
        DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), "test@example.com", "password123");

        dashboardSQLStorage.createNewDashboardUserSession(user.userId, io.supertokens.utils.Utils.getUUID(), 0, 0);

        // check that the session was successfully created
        DashboardSessionInfo[] sessionInfo = dashboardSQLStorage.getAllSessionsForUserId(user.userId);
        assertEquals(1, sessionInfo.length);
        assertEquals(user.userId, sessionInfo[0].userId);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingMultipleSessionsForAUser() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        DashboardSQLStorage dashboardSQLStorage = StorageLayer.getDashboardStorage(process.getProcess());

        // create a user
        DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), "test@example.com", "password123");
        ArrayList<String> sessionIds = new ArrayList<>();

        // create 5 sessions for the user
        for (int i = 0; i < 5; i++) {
            String sessionId = io.supertokens.utils.Utils.getUUID();
            sessionIds.add(sessionId);
            dashboardSQLStorage.createNewDashboardUserSession(user.userId, sessionId, 0, 0);
        }

        // get all sessions for userId
        DashboardSessionInfo[] sessionInfoArray = dashboardSQLStorage.getAllSessionsForUserId(user.userId);
        assertEquals(5, sessionInfoArray.length);

        // test retrieving sessions
        for(int i = 0; i < 2; i++){
            DashboardSessionInfo sessionInfo =  dashboardSQLStorage.getSessionInfoWithSessionId(sessionIds.get(i));
            assertNotNull(sessionInfo);
            assertEquals(user.userId, sessionInfo.userId);
        }
        
        // delete some user sessions
        dashboardSQLStorage.revokeSessionWithSessionId(sessionIds.get(0));
        dashboardSQLStorage.revokeSessionWithSessionId(sessionIds.get(1));

        // retrieve all sessions
        DashboardSessionInfo[] dashboardSessionInfo = dashboardSQLStorage.getAllSessionsForUserId(user.userId);
        assertEquals(3, dashboardSessionInfo.length); 
        
        // check that two sessions were deleted
        for(int i = 0; i < 2; i++){
            assertNull(dashboardSQLStorage.getSessionInfoWithSessionId(sessionIds.get(i)));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

}
