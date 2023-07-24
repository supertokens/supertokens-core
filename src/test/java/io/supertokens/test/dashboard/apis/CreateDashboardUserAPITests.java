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
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class CreateDashboardUserAPITests {
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
            // calling API with neither email or password
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/user", new JsonObject(), 1000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'email' is invalid in JSON input"));
            }
        }

        {
            // calling API with only email
            JsonObject request = new JsonObject();
            request.addProperty("email", "test@example.com");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/user", request, 1000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'password' is invalid in JSON input"));
            }
        }

        {
            // calling API with email as an invalid type
            JsonObject request = new JsonObject();
            request.addProperty("email", 123);
            request.addProperty("password", "password123");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/user", request, 1000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'email' is invalid in JSON input"));
            }
        }

        {
            // calling API with email as an empty string
            JsonObject request = new JsonObject();
            request.addProperty("email", "  ");
            request.addProperty("password", "password123");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/user", request, 1000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'email' cannot be an empty String"));
            }
        }

        {
            // calling API with only password
            JsonObject request = new JsonObject();
            request.addProperty("password", "testPass123");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/user", request, 1000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'email' is invalid in JSON input"));
            }
        }

        {
            // calling API with password as an invalid type
            JsonObject request = new JsonObject();
            request.addProperty("email", "test@example.com");
            request.addProperty("password", 123);
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/user", request, 1000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'password' is invalid in JSON input"));
            }
        }

        {
            // calling API with password as an empty string
            JsonObject request = new JsonObject();
            request.addProperty("email", "test@example.com");
            request.addProperty("password", "  ");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/user", request, 1000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'password' cannot be an empty String"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSuccessfullyCreatingDashboardUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String email = "test@example.com";
        String password = "testPass123";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email.toUpperCase());
        requestBody.addProperty("password", password);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/dashboard/user", requestBody, 1000, 1000, null,
                SemVer.v2_18.get(), "dashboard");
        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        JsonObject createdUser = response.get("user").getAsJsonObject();
        assertEquals(3, createdUser.entrySet().size());
        assertNotNull(createdUser.get("userId").getAsString());
        assertNotNull(createdUser.get("timeJoined").getAsLong());
        assertEquals(email, createdUser.get("email").getAsString());

        // check that user exists
        DashboardUser[] allDashboardUsers = Dashboard.getAllDashboardUsers(process.getProcess());
        assertEquals(1, allDashboardUsers.length);
        assertEquals(email, allDashboardUsers[0].email);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingTheSameUserTwice() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create the user

        String email = "test@example.com";
        String password = "testPass@123";

        Dashboard.signUpDashboardUser(process.getProcess(), email, password);

        // check that user was successfully created
        {
            // check that user exists
            DashboardUser[] allDashboardUsers = Dashboard.getAllDashboardUsers(process.getProcess());
            assertEquals(1, allDashboardUsers.length);
            assertEquals(email, allDashboardUsers[0].email);
        }

        // try creating the user again

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("password", password);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/dashboard/user", requestBody, 1000, 1000, null,
                SemVer.v2_18.get(), "dashboard");
        assertEquals(1, response.entrySet().size());
        assertEquals("EMAIL_ALREADY_EXISTS_ERROR", response.get("status").getAsString());

        // check that a duplicate user was not created
        DashboardUser[] allDashboardUsers = Dashboard.getAllDashboardUsers(process.getProcess());
        assertEquals(1, allDashboardUsers.length);
        assertEquals(email, allDashboardUsers[0].email);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingAUserWithAnInvalidEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create the user with an invalid email

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", "invalidEmail");
        requestBody.addProperty("password", "testPass123");

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/dashboard/user", requestBody, 1000, 1000, null,
                SemVer.v2_18.get(), "dashboard");
        assertEquals(1, response.entrySet().size());
        assertEquals("INVALID_EMAIL_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingAUserWithAWeakPassword() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            // Password must have at least 8 characters

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            requestBody.addProperty("password", "invalid");

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/dashboard/user", requestBody, 1000, 1000, null,
                    SemVer.v2_18.get(), "dashboard");
            assertEquals(2, response.entrySet().size());
            assertEquals("PASSWORD_WEAK_ERROR", response.get("status").getAsString());
            assertEquals("Password must contain at least 8 characters, including a number",
                    response.get("message").getAsString());
        }

        {
            // password must contain an alphabet
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            requestBody.addProperty("password", "123456789");

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/dashboard/user", requestBody, 1000, 1000, null,
                    SemVer.v2_18.get(), "dashboard");
            assertEquals(2, response.entrySet().size());
            assertEquals("PASSWORD_WEAK_ERROR", response.get("status").getAsString());
            assertEquals("Password must contain at least one alphabet",
                    response.get("message").getAsString());
        }

        {
            // password must contain 1 number
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test@example.com");
            requestBody.addProperty("password", "invalidpassword");

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/dashboard/user", requestBody, 1000, 1000, null,
                    SemVer.v2_18.get(), "dashboard");
            assertEquals(2, response.entrySet().size());
            assertEquals("PASSWORD_WEAK_ERROR", response.get("status").getAsString());
            assertEquals("Password must contain at least one number",
                    response.get("message").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingAUserAfterCrossingTheFreeUserThreshold() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Create users under the max limit
        {
            String password = "testPass@123";

            for (int i = 0; i < Dashboard.MAX_NUMBER_OF_FREE_DASHBOARD_USERS; i++) {
                Dashboard.signUpDashboardUser(process.getProcess(), "test" + i + "@example.com", password);
            }
        }

        // try creating another user when max number of free users is reached 
        {
            String email = "newUser@example.com";
            String password = "testPass123";

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", email);
            requestBody.addProperty("password", password);

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/user", requestBody, 5000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                assert StorageLayer.isInMemDb(process.main);
            } catch (HttpResponseException e) {
                assert !StorageLayer.isInMemDb(process.main);
                assertTrue(e.statusCode == 402 && e.getMessage().equals(
                        "Http error. Status Code: 402. Message:"
                                +
                                " Free user limit reached. Please subscribe to a SuperTokens core license key to " +
                                "allow more users to access the dashboard."));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

}
