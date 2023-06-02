/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.userRoles.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.userroles.sqlStorage.UserRolesSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.userroles.UserRoles;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class AddUserRoleAPITest {
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
    public void badInputTest() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // dont pass either roles or userId
        {
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role", new JsonObject(), 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is invalid in JSON input"));
            }
        }

        // dont pass userId
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("role", "role");
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is invalid in JSON input"));
            }

        }
        // userId as a number
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", 1);
            requestBody.addProperty("role", "role");
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is invalid in JSON input"));
            }
        }
        // dont pass role
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", "userId");
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' is invalid in JSON input"));
            }
        }
        // role as a number
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", "userId");
            requestBody.addProperty("role", 1);
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' is invalid in JSON input"));
            }
        }
        // role as an empty string
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", "userId");
            requestBody.addProperty("role", "  ");
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' cannot be an empty String"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAddingARoleToAUserTwice() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // create a role
        String[] role = new String[]{"role"};
        String userId = "userId";
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role[0], null);

        {
            // add the role to a user
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("role", role[0]);
            requestBody.addProperty("userId", userId);

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                    SemVer.v2_14.get(), "userroles");

            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("didUserAlreadyHaveRole").getAsBoolean());

            // check that the user actually has only that role
            String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
            Utils.checkThatArraysAreEqual(role, userRoles);
        }

        {
            // add the role to the user again and check that the user already had the role
            // add the role to a user
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("role", role[0]);
            requestBody.addProperty("userId", userId);

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                    SemVer.v2_14.get(), "userroles");

            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("didUserAlreadyHaveRole").getAsBoolean());

            // check the users roles havent changed
            String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
            Utils.checkThatArraysAreEqual(role, userRoles);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAddingARoleToAUserByCallingAddUserRoleAPI() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // create a role
        String[] role = new String[]{"role"};
        String userId = "userId";
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role[0], null);

        // add the role to a user
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("role", role[0]);
        requestBody.addProperty("userId", userId);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                SemVer.v2_14.get(), "userroles");

        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertFalse(response.get("didUserAlreadyHaveRole").getAsBoolean());

        // check that the user actually has only that role
        String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
        Utils.checkThatArraysAreEqual(role, userRoles);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAddingAnUnknownRoleToAUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // add an unknown role to a user
        String userId = "test_user";
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("role", "unknown_role");
        requestBody.addProperty("userId", userId);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                SemVer.v2_14.get(), "userroles");

        assertEquals("UNKNOWN_ROLE_ERROR", response.get("status").getAsString());
        assertEquals(1, response.entrySet().size());

        // check that user has no role associated with them
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);
        String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
        assertEquals(0, userRoles.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
