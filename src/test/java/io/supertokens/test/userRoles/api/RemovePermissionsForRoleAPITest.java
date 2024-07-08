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

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class RemovePermissionsForRoleAPITest {
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
                        "http://localhost:3567/recipe/role/permissions/remove", new JsonObject(), 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' is invalid in JSON input"));
            }
        }

        {
            // role is missing
            String[] permissions = new String[]{"testPermission"};
            String permissionsString = "{ permissions : " + Arrays.toString(permissions) + " }";

            JsonObject request = new JsonParser().parse(permissionsString).getAsJsonObject();

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/role/permissions/remove", request, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' is invalid in JSON input"));
            }
        }

        {
            // role is a number
            String[] permissions = new String[]{"testPermission"};
            String permissionsString = "{ permissions : " + Arrays.toString(permissions) + " }";
            JsonObject request = new JsonParser().parse(permissionsString).getAsJsonObject();
            request.addProperty("role", 1);

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/role/permissions/remove", request, 1000, 1000, null,
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
                        "http://localhost:3567/recipe/role/permissions/remove", requestBody, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' cannot be an empty String"));
            }
        }

        {
            // invalid permission in permission array
            String permissions = "{ permissions: [ testPermission1, \" \" , testPermissions2 ]}";
            JsonObject request = new JsonParser().parse(permissions).getAsJsonObject();
            request.addProperty("role", "testRole");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/role/permissions/remove", request, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'permissions' cannot contain an empty string"));
            }
        }
        {
            // permissions is a number
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("permissions", 1);
            requestBody.addProperty("role", "testRole");
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/role/permissions/remove", requestBody, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
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
    public void deletePermissionsFromRoleTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a role with permissions
        String[] permissions = new String[]{"permission1", "permission2", "permission3"};
        String role = "role";

        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);

        // call api to remove some permissions
        String[] permissionsToRemove = new String[]{"permission1", "permission2"};
        String permissionsString = "{ permissions : " + Arrays.toString(permissionsToRemove) + " }";
        JsonObject requestBody = new JsonParser().parse(permissionsString).getAsJsonObject();

        requestBody.addProperty("role", role);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/role/permissions/remove", requestBody, 1000, 1000, null,
                SemVer.v2_14.get(), "userroles");

        assertEquals(1, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        // check that only 1 permission exists for role
        String[] retrievedPermissions = UserRoles.getPermissionsForRole(process.main, role);
        assertEquals(1, retrievedPermissions.length);
        assertEquals("permission3", retrievedPermissions[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deleteAllPermissionsFromRoleTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a role with permissions
        String[] permissions = new String[]{"permission1", "permission2", "permission3"};
        String role = "role";

        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);

        // call api to remove all permissions
        JsonObject requestBody = new JsonObject();

        requestBody.addProperty("role", role);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/role/permissions/remove", requestBody, 1000, 1000, null,
                SemVer.v2_14.get(), "userroles");

        assertEquals(1, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        // check that there are no permissions for the role
        String[] retrievedPermissions = UserRoles.getPermissionsForRole(process.main, role);
        assertEquals(0, retrievedPermissions.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deletePermissionsFromAnUnknownRole() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // call api to remove some permissions from an unknownRole
        String[] permissionsToRemove = new String[]{"testPermission"};
        String permissionsString = "{ permissions : " + Arrays.toString(permissionsToRemove) + " }";
        JsonObject requestBody = new JsonParser().parse(permissionsString).getAsJsonObject();

        requestBody.addProperty("role", "unknownRole");

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/role/permissions/remove", requestBody, 1000, 1000, null,
                SemVer.v2_14.get(), "userroles");

        assertEquals(1, response.entrySet().size());
        assertEquals("UNKNOWN_ROLE_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
