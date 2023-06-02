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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.deleteExpiredDashboardSessions.DeleteExpiredDashboardSessions;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.dashboard.DashboardSessionInfo;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.dashboard.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.dashboard.sqlStorage.DashboardSQLStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.webserver.WebserverAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;

import static org.junit.Assert.*;

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

    private final String OPAQUE_KEY_WITH_DASHBOARD_FEATURE =
            "EBy9Z4IRJ7BYyLP8AXxjq997o3RPaDekAE4CMGxduglUaEH9hugXzIduxvHIjpkFccVCZaHJIacMi8NJJg4I" +
                    "=ruc3bZbT43QOLJbGu01cgACmVu2VOjQzFbT3lXiAKOR";

    @Test
    public void testCreatingDashboardUsers() throws Exception {
        String[] args = {"../"};

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
        String[] args = {"../"};

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

        String[] args = {"../"};

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
            DashboardSessionInfo[] sessionInfo = Dashboard.getAllDashboardSessionsForUser(process.getProcess(),
                    user.userId);
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

    @Test
    public void testDashboardCronjob() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        CronTaskTest.getInstance(process.getProcess()).setIntervalInSeconds(DeleteExpiredDashboardSessions.RESOURCE_KEY,
                1);
        process.startProcess();

        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String email = "test@example.com";
        String password = "password123";

        // create Dashboard user
        DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), email, password);
        assertNotNull(user);

        String sessionId = "test";

        // create a session with low expiry
        ((DashboardSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .createNewDashboardUserSession(new AppIdentifier(null, null), user.userId, sessionId,
                        System.currentTimeMillis(), 0);

        // check that session exists
        assertEquals(1, Dashboard.getAllDashboardSessionsForUser(process.getProcess(), user.userId).length);

        // wait for cronjob to run
        Thread.sleep(3000);

        // check that session does not exist
        assertEquals(0, Dashboard.getAllDashboardSessionsForUser(process.getProcess(), user.userId).length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingDashboardUsersCredentialsShouldRevokeSessions() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create dashboard user
        String email = "test@example.com";
        String password = "password123";
        DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), email, password);

        // create multiple sessions
        ArrayList<String> sessionIds = new ArrayList<>();

        // create 3 user sessions
        for (int i = 0; i < 3; i++) {
            String sessionId = Dashboard.signInDashboardUser(process.getProcess(), email, password);
            assertNotNull(sessionId);
            sessionIds.add(sessionId);
        }
        assertEquals(3, sessionIds.size());

        // check that sessions are valid

        for (int i = 0; i < sessionIds.size(); i++) {
            assertTrue(Dashboard.isValidUserSession(process.getProcess(), sessionIds.get(i)));
        }

        // update the users email and check that the sessions are invalidated
        Dashboard.updateUsersCredentialsWithUserId(process.getProcess(), user.userId, null, "newPassword123");

        // check that no sessions exist for the user
        assertEquals(0, Dashboard.getAllDashboardSessionsForUser(process.getProcess(), user.userId).length);

        // check that session handles are not valid

        for (int i = 0; i < sessionIds.size(); i++) {
            assertFalse(Dashboard.isValidUserSession(process.getProcess(), sessionIds.get(i)));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDashboardUsageStats() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // test calling usage stats api without any users and feature disabled
        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/ee/featureflag",
                    null, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(0, response.get("features").getAsJsonArray().size());
            JsonObject usageStats = response.get("usageStats").getAsJsonObject();
            JsonArray mauArr = usageStats.get("maus").getAsJsonArray();
            assertEquals(1, usageStats.entrySet().size());
            assertEquals(30, mauArr.size());
            assertEquals(0, mauArr.get(0).getAsInt());
            assertEquals(0, mauArr.get(29).getAsInt());
        }

        // create a dashboard user
        Dashboard.signUpDashboardUser(process.getProcess(), "test@example.com", "password123");

        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/ee/featureflag",
                    null, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(0, response.get("features").getAsJsonArray().size());
            JsonObject usageStats = response.get("usageStats").getAsJsonObject();
            JsonArray mauArr = usageStats.get("maus").getAsJsonArray();
            assertEquals(1, usageStats.entrySet().size());
            assertEquals(30, mauArr.size());
            assertEquals(0, mauArr.get(0).getAsInt());
            assertEquals(0, mauArr.get(29).getAsInt());
        }

        // enable the dashboard feature
        FeatureFlag.getInstance(process.main).setLicenseKeyAndSyncFeatures(OPAQUE_KEY_WITH_DASHBOARD_FEATURE);

        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/ee/featureflag",
                    null, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(1, response.get("features").getAsJsonArray().size());
            JsonArray featuresArray = response.get("features").getAsJsonArray();
            assertEquals(1, featuresArray.size());
            assertEquals(EE_FEATURES.DASHBOARD_LOGIN.toString(), featuresArray.get(0).getAsString());
            JsonObject usageStats = response.get("usageStats").getAsJsonObject();
            JsonObject dashboardLoginObject = usageStats.get("dashboard_login").getAsJsonObject();
            assertEquals(2, usageStats.entrySet().size());
            assertEquals(30, usageStats.get("maus").getAsJsonArray().size());
            assertEquals(1, dashboardLoginObject.entrySet().size());
            assertEquals(1, dashboardLoginObject.get("user_count").getAsInt());
        }

        // create 3 more users

        Dashboard.signUpDashboardUser(process.getProcess(), "test+1@example.com", "password123");
        Dashboard.signUpDashboardUser(process.getProcess(), "test+2@example.com", "password123");
        Dashboard.signUpDashboardUser(process.getProcess(), "test+3@example.com", "password123");
        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/ee/featureflag",
                    null, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(1, response.get("features").getAsJsonArray().size());
            JsonArray featuresArray = response.get("features").getAsJsonArray();
            assertEquals(1, featuresArray.size());
            assertEquals(EE_FEATURES.DASHBOARD_LOGIN.toString(), featuresArray.get(0).getAsString());
            JsonObject usageStats = response.get("usageStats").getAsJsonObject();
            JsonObject dashboardLoginObject = usageStats.get("dashboard_login").getAsJsonObject();
            assertEquals(2, usageStats.entrySet().size());
            assertEquals(30, usageStats.get("maus").getAsJsonArray().size());
            assertEquals(1, dashboardLoginObject.entrySet().size());
            assertEquals(4, dashboardLoginObject.get("user_count").getAsInt());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

}
