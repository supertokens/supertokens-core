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

public class RemoveUserRoleAPITest {
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
    public void testBadInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            // request body is empty
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role/remove", new JsonObject(), 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is invalid in JSON input"));
            }
        }

        {
            // userId is missing
            JsonObject request = new JsonObject();
            request.addProperty("role", "role");

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role/remove", request, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is invalid in JSON input"));
            }
        }

        {
            // userId is a number
            JsonObject request = new JsonObject();
            request.addProperty("userId", 1);
            request.addProperty("role", "role");

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role/remove", request, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is invalid in JSON input"));
            }
        }

        {
            // role is missing
            JsonObject request = new JsonObject();
            request.addProperty("userId", "userId");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role/remove", request, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' is invalid in JSON input"));
            }
        }

        {
            // role is a number
            JsonObject request = new JsonObject();
            request.addProperty("userId", "userId");
            request.addProperty("role", 1);
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role/remove", request, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' is invalid in JSON input"));
            }
        }
        {
            // role is an empty string
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", "userId");
            requestBody.addProperty("role", "  ");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role/remove", requestBody, 1000, 1000, null,
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
    public void testRemovingARoleFromAUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String[] roles = new String[]{"role1"};
        String userId = "userId";
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // create a role
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, roles[0], null);

        // assign the role to a user
        UserRoles.addRoleToUser(process.main, userId, roles[0]);

        {
            // check that the user has the role
            String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
            Utils.checkThatArraysAreEqual(roles, userRoles);
        }

        // remove the role from the user
        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.addProperty("role", roles[0]);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/role/remove", request, 1000, 1000, null,
                SemVer.v2_14.get(), "userroles");
        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertTrue(response.get("didUserHaveRole").getAsBoolean());

        // check that user doesnt have any role
        String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
        assertEquals(0, userRoles.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRemovingARoleFromAUserWhereTheUserDoesNotHaveTheRole() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String[] roles = new String[]{"role1"};
        String userId = "userId";
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // create a role
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, roles[0], null);

        // remove the role from the user
        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.addProperty("role", roles[0]);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/role/remove", request, 1000, 1000, null,
                SemVer.v2_14.get(), "userroles");
        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertFalse(response.get("didUserHaveRole").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRemovingAnUnknownRoleFromAUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject request = new JsonObject();
        request.addProperty("userId", "userId");
        request.addProperty("role", "unknown_role");

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/role/remove", request, 1000, 1000, null,
                SemVer.v2_14.get(), "userroles");

        assertEquals(1, response.entrySet().size());
        assertEquals("UNKNOWN_ROLE_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
