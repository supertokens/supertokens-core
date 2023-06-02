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
import io.supertokens.emailpassword.PasswordHashing;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.dashboard.sqlStorage.DashboardSQLStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;

public class SignInAPITest {
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
    public void testSigningInAUserAndVerifyingTheirSession() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create dashboard user, create session and verify session
        String email = "test@example.com";
        String password = "testPass123";

        Dashboard.signUpDashboardUser(process.getProcess(), email, password);

        // signin user
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", "tesT@example.com");
        requestBody.addProperty("password", password);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/dashboard/signin", requestBody, 1000, 1000, null,
                SemVer.v2_18.get(), "dashboard");
        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertNotNull(response.get("sessionId").getAsString());

        // check that session created is a valid session
        String sessionId = response.get("sessionId").getAsString();
        assertTrue(Dashboard.isValidUserSession(process.getProcess(), sessionId));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSigningInASuspendedUser() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // signUp multiple users to the free limit
        {
            for (int i = 0; i < Dashboard.MAX_NUMBER_OF_FREE_DASHBOARD_USERS; i++) {
                Dashboard.signUpDashboardUser(process.getProcess(), "test" + i + "@example.com", "password123");
            }

        }
        // create a user above the free limit
        String email = "suspended@example.com";
        String password = "testPass123";

        DashboardUser user = new DashboardUser(io.supertokens.utils.Utils.getUUID(), email,
                PasswordHashing.getInstance(process.getProcess()).createHashWithSalt(password),
                System.currentTimeMillis());
        ((DashboardSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .createNewDashboardUser(new AppIdentifier(null, null), user);

        // try signing in with the valid user
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "test0@example.com");
            requestBody.addProperty("password", "password123");
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/dashboard/signin", requestBody, 1000, 1000, null,
                    SemVer.v2_18.get(), "dashboard");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertNotNull(response.get("sessionId").getAsString());
        }

        // try signing in with the suspended user
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", email);
            requestBody.addProperty("password", password);
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/dashboard/signin", requestBody, 1000, 1000, null,
                    SemVer.v2_18.get(), "dashboard");
            assertEquals(2, response.entrySet().size());
            assertEquals("USER_SUSPENDED_ERROR", response.get("status").getAsString());
            assertEquals(
                    "User is currently suspended, please sign in with another account, or reactivate the SuperTokens " +
                            "core license key",
                    response.get("message").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

}
