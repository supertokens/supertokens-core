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

package io.supertokens.test.dashboard.apis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonObject;

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.dashboard.DashboardSessionInfo;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;

public class DeleteDashboardUserSessionAPITest {
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
    public void BadInputTests() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            // calling API without sessionId
            try {
                HttpRequestForTesting.sendJsonDELETERequestWithQueryParams(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/session", new HashMap<>(), 1000, 1000, null,
                        Utils.getCdiVersionStringLatestForTests(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:"
                                + " Field name 'sessionId' is missing in GET request"));
            }

            // calling API with sessionId as an empty string
            HashMap<String, String> requestParams = new HashMap<>();
            requestParams.put("sessionId", "  ");
            try {
                HttpRequestForTesting.sendJsonDELETERequestWithQueryParams(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/session", requestParams, 1000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:"
                                + " Field name 'sessionId' cannot be an empty String"));
            }
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRevokeSessionAPI() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String email = "test@example.com";
        String password = "password123";

        // Create a user and a session
        DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), email, password);
        assertNotNull(user);

        String sessionId = Dashboard.signInDashboardUser(process.getProcess(), email, password);
        assertNotNull(sessionId);

        {
            // check that session exists for user
            DashboardSessionInfo[] sessionInfo = Dashboard.getAllDashboardSessionsForUser(process.getProcess(),
                    user.userId);
            assertEquals(1, sessionInfo.length);
            assertEquals(sessionId, sessionInfo[0].sessionId);
        }

        // revoke session
        HashMap<String, String> requestParams = new HashMap<>();
        requestParams.put("sessionId", sessionId);

        JsonObject response = HttpRequestForTesting.sendJsonDELETERequestWithQueryParams(process.getProcess(), "",
                "http://localhost:3567/recipe/dashboard/session", requestParams, 1000, 1000, null,
                SemVer.v2_18.get(), "dashboard");
        assertEquals(1, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        {
            // check that session exists for user
            DashboardSessionInfo[] sessionInfo = Dashboard.getAllDashboardSessionsForUser(process.getProcess(),
                    user.userId);
            assertEquals(0, sessionInfo.length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
