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
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.userroles.sqlStorage.UserRolesSQLStorage;
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

import java.util.Arrays;

import static org.junit.Assert.*;

public class CreateRoleAPITest {
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

        {
            // request body is empty
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/recipe/role",
                        new JsonObject(), 1000, 1000, null, SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' is invalid in JSON input"));
            }
        }

        {
            // role is missing in request
            String[] permissions = new String[]{"testPermission"};
            String permissionsString = "{ permissions : " + Arrays.toString(permissions) + " }";

            JsonObject requestBody = new JsonParser().parse(permissionsString).getAsJsonObject();

            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/recipe/role",
                        requestBody, 1000, 1000, null, SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' is invalid in JSON input"));
            }
        }

        {
            // invalid permission in permission array
            String permissions = "{ permissions: [ testPermission1, \" \" , testPermissions2 ]}";
            JsonObject requestBody = new JsonParser().parse(permissions).getAsJsonObject();
            requestBody.addProperty("role", "testRole");

            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/recipe/role",
                        requestBody, 1000, 1000, null, SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'permissions' cannot contain an empty string"));
            }
        }

        {
            // set role as a number

            String[] permissions = new String[]{"testPermission"};
            String permissionsString = "{ permissions : " + Arrays.toString(permissions) + " }";

            JsonObject requestBody = new JsonParser().parse(permissionsString).getAsJsonObject();
            requestBody.addProperty("role", 1);
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/recipe/role",
                        requestBody, 1000, 1000, null, SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' is invalid in JSON input"));
            }
        }

        {
            // set permissions as a number
            int permissions = 1;
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("permissions", permissions);
            requestBody.addProperty("role", "testRole");
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/recipe/role",
                        requestBody, 1000, 1000, null, SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'permissions' is invalid in JSON input"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void createTheSameRoleTwiceTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            JsonObject requestBody = new JsonObject();
            String role = "testRole";
            requestBody.addProperty("role", role);
            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/role", requestBody, 1000, 1000, null,
                    SemVer.v2_14.get(), "userroles");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("createdNewRole").getAsBoolean());

            // retrieve all roles and check that the newly created role is returned
            UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);
            String[] roles = storage.getRoles(new AppIdentifier(null, null));
            assertEquals(1, roles.length);
            assertEquals(roles[0], role);
        }

        // create the same role again and check that a new role is not created

        {
            JsonObject requestBody = new JsonObject();
            String role = "testRole";
            requestBody.addProperty("role", role);
            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/role", requestBody, 1000, 1000, null,
                    SemVer.v2_14.get(), "userroles");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("createdNewRole").getAsBoolean());

            // retrieve all roles and check that no new role has been created
            UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);
            String[] roles = storage.getRoles(new AppIdentifier(null, null));
            assertEquals(1, roles.length);
            assertEquals(roles[0], role);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createANewRoleWithoutPermissionsTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a new role without permissions

        JsonObject requestBody = new JsonObject();
        String role = "testRole";
        requestBody.addProperty("role", role);
        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/role", requestBody, 1000, 1000, null, SemVer.v2_14.get(),
                "userroles");
        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertTrue(response.get("createdNewRole").getAsBoolean());

        // retrieve all roles and check that the newly created role is returned
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);
        String[] roles = storage.getRoles(new AppIdentifier(null, null));
        assertEquals(1, roles.length);
        assertEquals(roles[0], role);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createANewRoleWithPermissionsTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a new role with permissions
        String role = "testRole";
        String[] permissions = new String[]{"testPermissions1", "testPermission2"};
        String permissionsString = "{ permissions: " + Arrays.toString(permissions) + "}";
        JsonObject requestBody = new JsonParser().parse(permissionsString).getAsJsonObject();
        requestBody.addProperty("role", role);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/role", requestBody, 1000, 1000, null, SemVer.v2_14.get(),
                "userroles");

        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertTrue(response.get("createdNewRole").getAsBoolean());

        // check if role is created
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);
        String[] roles = storage.getRoles(new AppIdentifier(null, null));
        assertEquals(1, roles.length);
        assertEquals(roles[0], role);

        // check if permissions have been added
        String[] rolePermissions = storage.getPermissionsForRole(new AppIdentifier(null, null), role);

        Utils.checkThatArraysAreEqual(permissions, rolePermissions);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
