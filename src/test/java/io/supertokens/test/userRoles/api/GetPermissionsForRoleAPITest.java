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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
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

import java.util.HashMap;

import static org.junit.Assert.*;

public class GetPermissionsForRoleAPITest {
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
            // no request params
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/role/permissions", new HashMap<>(), 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' is missing in GET request"));
            }
        }

        {
            // role is missing
            HashMap<String, String> QUERY_PARAM = new HashMap<>();
            QUERY_PARAM.put("invalid", "invalid");
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/role/permissions", QUERY_PARAM, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'role' is missing in GET request"));
            }
        }

        {
            // role is an empty string
            HashMap<String, String> QUERY_PARAM = new HashMap<>();
            QUERY_PARAM.put("role", " ");
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/role/permissions", QUERY_PARAM, 1000, 1000, null,
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
    public void testRetrievingThePermissionsForARole() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a role with permissions
        String role = "role";
        String[] permissions = new String[]{"permission1", "permission2", "permission3"};
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);

        HashMap<String, String> QUERY_PARAM = new HashMap<>();
        QUERY_PARAM.put("role", role);

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/role/permissions", QUERY_PARAM, 1000, 1000, null,
                SemVer.v2_14.get(), "userroles");

        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());

        JsonArray arr = response.get("permissions").getAsJsonArray();
        String[] retrievedPermissions = Utils.parseJsonArrayToStringArray(arr);

        // check that retrieved permissions are the same as the ones set
        Utils.checkThatArraysAreEqual(permissions, retrievedPermissions);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingThePermissionsForAnUnknownRole() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        HashMap<String, String> QUERY_PARAM = new HashMap<>();
        QUERY_PARAM.put("role", "unknownRole");

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/role/permissions", QUERY_PARAM, 1000, 1000, null,
                SemVer.v2_14.get(), "userroles");

        assertEquals(1, response.entrySet().size());
        assertEquals("UNKNOWN_ROLE_ERROR", response.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
