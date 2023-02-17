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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonObject;

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;

public class DeleteDashboardUserAPITests {
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
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            // calling API with neither email or userId
            try {
                HttpRequestForTesting.sendJsonDELETERequestWithQueryParams(process.getProcess(), "",
                    "http://localhost:3567/recipe/dashboard/user", null, 1000, 1000, null,
                    Utils.getCdiVersion2_18ForTests(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Either field 'email' or 'userId' must be present"));
            }
        }

        {
            // calling API with userId as an empty string
            HashMap<String, String> inputParams = new HashMap<>();
            inputParams.put("userId", "  "); 
            try {
                HttpRequestForTesting.sendJsonDELETERequestWithQueryParams(process.getProcess(), "",
                    "http://localhost:3567/recipe/dashboard/user", inputParams, 1000, 1000, null,
                    Utils.getCdiVersion2_18ForTests(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' cannot be an empty String"));
            }
        }

        // calling API with email as an empty string
        HashMap<String, String> inputParams = new HashMap<>();
        inputParams.put("email", "  "); 
        try {
            HttpRequestForTesting.sendJsonDELETERequestWithQueryParams(process.getProcess(), "",
                "http://localhost:3567/recipe/dashboard/user", inputParams, 1000, 1000, null,
                Utils.getCdiVersion2_18ForTests(), "dashboard");
            throw new Exception("Should never come here");

        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage().equals(
                    "Http error. Status Code: 400. Message:" + " Field name 'email' cannot be an empty String"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSuccessfullyDeletingAUserWithUserId() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a user
        DashboardUser user = Dashboard.signUpDashboardUser(process.getProcess(), "test@example.com", "password123");

        // check that user exists
        assertEquals(1, Dashboard.getAllDashboardUsers(process.getProcess()).length);

        // delete user

        {
            HashMap<String, String> inputParams = new HashMap<>();
            inputParams.put("userId", user.userId);
            JsonObject response = HttpRequestForTesting.sendJsonDELETERequestWithQueryParams(process.getProcess(), "",
                    "http://localhost:3567/recipe/dashboard/user", inputParams, 1000, 1000, null,
                    Utils.getCdiVersion2_18ForTests(), "dashboard");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("didUserExist").getAsBoolean());            
        }

        // try deleting user again
        {
            HashMap<String, String> inputParams = new HashMap<>();
            inputParams.put("userId", user.userId);
            JsonObject response = HttpRequestForTesting.sendJsonDELETERequestWithQueryParams(process.getProcess(), "",
                    "http://localhost:3567/recipe/dashboard/user", inputParams, 1000, 1000, null,
                    Utils.getCdiVersion2_18ForTests(), "dashboard");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("didUserExist").getAsBoolean());  
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
