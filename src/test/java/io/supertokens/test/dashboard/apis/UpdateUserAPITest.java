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
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.dashboard.sqlStorage.DashboardSQLStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;

public class UpdateUserAPITest {

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
            // calling API with neither email or userId
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/user", new JsonObject(), 1000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:"
                                + " Either field 'email' or 'userId' must be present"));
            }
        }

        {
            // calling API with userId as invalid type
            JsonObject requestObject = new JsonObject();
            requestObject.addProperty("userId", 123);
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/user", requestObject, 1000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is invalid in JSON input"));
            }
        }

        {
            // calling API with userId as an empty string
            JsonObject requestObject = new JsonObject();
            requestObject.addProperty("userId", "  ");
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/user", requestObject, 1000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' cannot be an empty String"));
            }
        }

        {
            // calling API with email as invalid type
            JsonObject requestObject = new JsonObject();
            requestObject.addProperty("email", 123);
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/user", requestObject, 1000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'email' is invalid in JSON input"));
            }
        }

        {
            // calling API with userId as an empty string
            JsonObject requestObject = new JsonObject();
            requestObject.addProperty("email", "  ");
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/user", requestObject, 1000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'email' cannot be an empty String"));
            }
        }

        {
            // calling API with newEmail in invalid type
            JsonObject requestObject = new JsonObject();
            requestObject.addProperty("newEmail", 123);
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/dashboard/user", requestObject, 1000, 1000, null,
                        SemVer.v2_18.get(), "dashboard");
                throw new Exception("Should never come here");

            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'newEmail' is invalid in JSON input"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSuccessfullyUpdatingUserDataWithUserId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a user
        String email = "test@example.com";
        String password = "password123";

        Dashboard.signUpDashboardUser(process.getProcess(), email, password);

        DashboardUser user = ((DashboardSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getDashboardUserByEmail(new AppIdentifier(null, null), email);
        assertNotNull(user);

        // update the user's email and password

        String newEmail = "newtest@example.com";
        String newPassword = "newPassword123";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email.toUpperCase());
        requestBody.addProperty("newEmail", newEmail.toUpperCase());
        requestBody.addProperty("newPassword", newPassword);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/dashboard/user", requestBody, 1000, 1000, null,
                SemVer.v2_18.get(), "dashboard");

        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        JsonObject retrievedUser = response.get("user").getAsJsonObject();
        assertEquals(3, retrievedUser.entrySet().size());
        assertEquals(user.userId, retrievedUser.get("userId").getAsString());
        assertEquals(newEmail, retrievedUser.get("email").getAsString());

        // signing in with the old credentials should give invalid credentials
        {
            JsonObject signInResponseObject = new JsonObject();
            signInResponseObject.addProperty("email", email);
            signInResponseObject.addProperty("password", password);

            JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/dashboard/signin", signInResponseObject, 1000, 1000, null,
                    SemVer.v2_18.get(), "dashboard");
            assertEquals(1, signInResponse.entrySet().size());
            assertEquals("INVALID_CREDENTIALS_ERROR", signInResponse.get("status").getAsString());
        }

        // signing in with the new credentials should result in a success
        {
            JsonObject signInResponseObject = new JsonObject();
            signInResponseObject.addProperty("email", newEmail);
            signInResponseObject.addProperty("password", newPassword);

            JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/dashboard/signin", signInResponseObject, 1000, 1000, null,
                    SemVer.v2_18.get(), "dashboard");
            assertEquals(2, signInResponse.entrySet().size());
            assertEquals("OK", signInResponse.get("status").getAsString());
            assertNotNull(signInResponse.get("sessionId").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

}
