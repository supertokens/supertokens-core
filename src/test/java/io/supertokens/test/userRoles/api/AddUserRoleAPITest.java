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

import java.util.Arrays;

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
    public void TestBadInput() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            // request body is empty
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role", new JsonObject(), 1000, 1000, null,
                        Utils.getCdiVersion2_13ForTests(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is invalid in JSON input"));
            }
        }

        {
            // missing userId in request
            String role = "role";
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("role", role);
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                        Utils.getCdiVersion2_13ForTests(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is invalid in JSON input"));
            }
        }

        {
            // missing role in request
            String userId = "userId";
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", userId);
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                        Utils.getCdiVersion2_13ForTests(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' is invalid in JSON input"));
            }
        }

        {
            // empty userId in request
            String userId = "  ";
            String role = "role";

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", userId);
            requestBody.addProperty("role", role);
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                        Utils.getCdiVersion2_13ForTests(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is invalid in JSON input"));
            }
        }

        {
            // empty userId in request
            String userId = "userId";
            String role = "  ";

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", userId);
            requestBody.addProperty("role", role);
            try {
                HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                        Utils.getCdiVersion2_13ForTests(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' is invalid in JSON input"));
            }
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void addingARoleToAUserWhereRoleDoesNotExistTest() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";
        String role = "role";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);
        requestBody.addProperty("role", role);

        JsonObject resp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "userroles");

        assertEquals(1, resp.entrySet().size());
        assertEquals("UNKNOWN_ROLE_ERROR", resp.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void addingARoleToAUserWhereRoleExistsTest() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a role
        String permissions[] = new String[] { "" };
        String role = "role";
        UserRoles.setRole(process.main, role, permissions);

        String userId = "userId";
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);
        requestBody.addProperty("role", role);
        JsonObject resp = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/role", requestBody, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "userroles");

        assertEquals(1, resp.entrySet().size());
        assertEquals("OK", resp.get("status").getAsString());

        // check if the input role is associated with the userId
        assertTrue(Arrays.asList(UserRoles.getUsersForRole(process.main, role)).contains(userId));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
