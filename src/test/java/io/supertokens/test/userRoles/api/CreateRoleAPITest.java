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
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.userroles.UserRoles;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

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
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            // request body is empty
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/recipe/role",
                        new JsonObject(), 1000, 1000, null, Utils.getCdiVersion2_13ForTests(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' is invalid in JSON input"));
            }
        }

        {
            // role is missing in request
            String permissions = "{ permissions : [ testPermission1 ] }";

            JsonObject requestBody = new JsonParser().parse(permissions).getAsJsonObject();

            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/recipe/role",
                        requestBody, 1000, 1000, null, Utils.getCdiVersion2_13ForTests(), "userroles");
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
                        requestBody, 1000, 1000, null, Utils.getCdiVersion2_13ForTests(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals("Http error. Status Code: 400. Message:"
                        + " Field name 'permissions' is invalid in JSON input"));
            }
        }

    }

    @Test
    public void createANewRoleWithoutPermissionsTest() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a new role without permissions

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("role", "testRole");
        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/role", requestBody, 1000, 1000, null, Utils.getCdiVersion2_13ForTests(),
                "userroles");
        assertEquals(1, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        // TODO: check if you retrieve all roles, the newly created role is returned

    }

    @Test
    public void createANewRoleWithPermissionsTest() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a new role with permissions
        String permissions = "{ permissions: [ testPermission1, testPermissions2 ]}";
        JsonObject requestBody = new JsonParser().parse(permissions).getAsJsonObject();
        requestBody.addProperty("role", "testRole");

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/role", requestBody, 1000, 1000, null, Utils.getCdiVersion2_13ForTests(),
                "userroles");

        assertEquals(1, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        // TODO: check if you retrieve all roles, the newly created role is returned
        // TODO: retrieve all permissions for the role and check if the correct permissions are returned

    }

    @Test
    public void creatingADuplicateRoleShouldNotThrowExceptionTest() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            // create a new role with permissions
            String permissions = "{ permissions: [ testPermission1, testPermissions2 ]}";
            JsonObject requestBody = new JsonParser().parse(permissions).getAsJsonObject();
            requestBody.addProperty("role", "testRole");

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/role", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_13ForTests(), "userroles");

            assertEquals(1, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());

            // TODO: check if you retrieve all roles, the newly created role is returned
            // TODO: retrieve all permissions for the role and check if the correct permissions are returned
        }

        {
            // create duplicate role
            String permissions = "{ permissions: [ testPermission1, testPermissions2 ]}";
            JsonObject requestBody = new JsonParser().parse(permissions).getAsJsonObject();
            requestBody.addProperty("role", "testRole");

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/role", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_13ForTests(), "userroles");

            assertEquals(1, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());

            // TODO: check that role still exists and permissions are same
        }
    }

    @Test
    public void createARoleAndUpdateThePermissionsTest() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            // create a new role with permissions
            String permissions = "{ permissions: [ testPermission1, testPermissions2 ]}";
            JsonObject requestBody = new JsonParser().parse(permissions).getAsJsonObject();
            requestBody.addProperty("role", "testRole");

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/role", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_13ForTests(), "userroles");

            assertEquals(1, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());

            // TODO: check if you retrieve all roles, the newly created role is returned
            // TODO: retrieve all permissions for the role and check if the correct permissions are returned
        }

        {
            // update the role permissions
            String permissions = "{ permissions: [ testPermissions3 ]}";
            JsonObject requestBody = new JsonParser().parse(permissions).getAsJsonObject();
            requestBody.addProperty("role", "testRole");

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/role", requestBody, 1000, 1000, null,
                    Utils.getCdiVersion2_13ForTests(), "userroles");

            assertEquals(1, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());

            // TODO: check that role still exists and the new permissions are added
        }
    }
}
