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
import com.google.gson.JsonParser;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.*;

public class GetUserRolesAPITest {
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
                        "http://localhost:3567/recipe/user/roles", new HashMap<>(), 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is missing in GET request"));
            }
        }

        {
            // userId is missing
            HashMap<String, String> QUERY_PARAM = new HashMap<>();
            QUERY_PARAM.put("invalid", "invalid");
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/roles", QUERY_PARAM, 1000, 1000, null,
                        SemVer.v2_14.get(), "userroles");
                throw new Exception("should not come here");
            } catch (HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message:" + " Field name 'userId' is missing in GET request"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testGettingRolesForAUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create and add multiple roles to a user, get roles for user
        {
            String[] roles = new String[]{"role1", "role2", "role3"};
            String userId = "userId";

            for (String role : roles) {
                // create role
                UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);
                // add role to User
                UserRoles.addRoleToUser(process.main, userId, role);
            }

            HashMap<String, String> QUERY_PARAMS = new HashMap<>();
            QUERY_PARAMS.put("userId", userId);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/roles", QUERY_PARAMS, 1000, 1000, null,
                    SemVer.v2_14.get(), "userroles");

            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());

            JsonArray userRolesArr = response.getAsJsonArray("roles");
            String[] userRoles = Utils.parseJsonArrayToStringArray(userRolesArr);
            Utils.checkThatArraysAreEqual(roles, userRoles);
        }

        // get roles for a user who has no roles
        {
            String userId2 = "userId2";
            HashMap<String, String> QUERY_PARAMS = new HashMap<>();
            QUERY_PARAMS.put("userId", userId2);

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/roles", QUERY_PARAMS, 1000, 1000, null,
                    SemVer.v2_14.get(), "userroles");

            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(0, response.getAsJsonArray("roles").size());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
