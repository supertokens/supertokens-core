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
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateUserIdException;
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

}
