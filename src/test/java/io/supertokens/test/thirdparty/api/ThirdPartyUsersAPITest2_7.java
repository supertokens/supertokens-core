/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.thirdparty.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.*;

/*
 * TODO:
 *  - Check for bad input (missing fields)
 *  - Check good input works (add 5 users)
 *    - no params passed should return 5 users
 *    - only limit passed (limit: 2. users are returned in ASC order based on timeJoined)
 *    - limit and timeJoinedOrder passed (limit: 2, timeJoinedOrder: DESC. users are returned in DESC order based on
 * timeJoined)
 * */

public class ThirdPartyUsersAPITest2_7 {

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

    // Check for bad input (missing fields)
    @Test
    public void testBadInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        {
            HashMap<String, String> QueryParams = new HashMap<>();
            QueryParams.put("paginationToken", "randomValue");
            try {
                HttpRequestForTesting
                        .sendGETRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/users", QueryParams, 1000,
                                1000,
                                null, Utils.getCdiVersion2_7ForTests(), "thirdparty");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 &&
                        e.getMessage().equals("Http error. Status Code: 400. Message: invalid pagination token"));
            }
        }
        {
            HashMap<String, String> QueryParams = new HashMap<>();
            QueryParams.put("limit", "randomValue");
            try {
                HttpRequestForTesting
                        .sendGETRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/users", QueryParams, 1000,
                                1000,
                                null, Utils.getCdiVersion2_7ForTests(), "thirdparty");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 &&
                        e.getMessage()
                                .equals("Http error. Status Code: 400. Message: Field name 'limit' must be an int in " +
                                        "the GET request"));
            }
        }
        {
            HashMap<String, String> QueryParams = new HashMap<>();
            QueryParams.put("timeJoinedOrder", "randomValue");
            try {
                HttpRequestForTesting
                        .sendGETRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/users", QueryParams, 1000,
                                1000,
                                null, Utils.getCdiVersion2_7ForTests(), "thirdparty");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 &&
                        e.getMessage()
                                .equals("Http error. Status Code: 400. Message: timeJoinedOrder can be either ASC OR " +
                                        "DESC"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    //-- Check good input works (add 5 users)
    // *    - no params passed should return 5 users
    // *    - only limit passed (limit: 2. users are returned in ASC order based on timeJoined)
    // *    - limit and timeJoinedOrder passed (limit: 2, timeJoinedOrder: DESC. users are returned in DESC order
    // based on timeJoined)
    @Test
    public void testGoodInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // added Thread.sleep(100) as sometimes tests would fail due to inconsistent signup order
        ThirdParty.signInUp(process.getProcess(), "thirdPartyId",
                "thirdPartyUserId", "test@example.com");
        Thread.sleep(100);
        ThirdParty.signInUp(process.getProcess(), "thirdPartyId",
                "thirdPartyUserId1", "test1@example.com");
        Thread.sleep(100);
        ThirdParty.signInUp(process.getProcess(), "thirdPartyId",
                "thirdPartyUserId2", "test2@example.com");
        Thread.sleep(100);
        ThirdParty.signInUp(process.getProcess(), "thirdPartyId",
                "thirdPartyUserId3", "test3@example.com");
        Thread.sleep(100);
        ThirdParty.signInUp(process.getProcess(), "thirdPartyId",
                "thirdPartyUserId4", "test4@example.com");
        Thread.sleep(100);


        {
            HashMap<String, String> queryParams = new HashMap<>();
            queryParams.put("limit", "1");
            JsonObject response = HttpRequestForTesting
                    .sendGETRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/users", queryParams, 1000,
                            1000,
                            null, Utils.getCdiVersion2_7ForTests(), "thirdparty");

            assertEquals("OK", response.get("status").getAsString());
            assertNotNull(response.get("nextPaginationToken"));
            assertEquals(1, response.getAsJsonArray("users").size());

            JsonObject user = response.getAsJsonArray("users").get(0).getAsJsonObject();
            assertNotNull(user.get("id"));
            assertNotNull(user.get("timeJoined"));
            assertEquals("test@example.com", user.get("email").getAsString());

            JsonObject userThirdParty = user.get("thirdParty").getAsJsonObject();
            assertEquals("thirdPartyId", userThirdParty.get("id").getAsString());
            assertEquals("thirdPartyUserId", userThirdParty.get("userId").getAsString());
        }

        // no params passed should return 5 users
        {
            JsonObject response = HttpRequestForTesting
                    .sendGETRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/users", new HashMap<>(), 1000,
                            1000,
                            null, Utils.getCdiVersion2_7ForTests(), "thirdparty");
            assertEquals(5, response.getAsJsonArray("users").size());
        }

        // only limit passed (limit: 2. users are returned in ASC order based on timeJoined)
        {
            HashMap<String, String> queryParams = new HashMap<>();
            queryParams.put("limit", "2");
            JsonObject response = HttpRequestForTesting
                    .sendGETRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/users", queryParams, 1000,
                            1000,
                            null, Utils.getCdiVersion2_7ForTests(), "thirdparty");
            assertEquals(2, response.getAsJsonArray("users").size());

            JsonObject user_1 = response.getAsJsonArray("users").get(0).getAsJsonObject();
            JsonObject user_2 = response.getAsJsonArray("users").get(1).getAsJsonObject();

            assertEquals("test@example.com", user_1.get("email").getAsString());
            assertEquals("test1@example.com", user_2.get("email").getAsString());

            assert (user_1.get("timeJoined").getAsLong() < user_2.get("timeJoined").getAsLong());
        }

        // limit and timeJoinedOrder passed (limit: 2, timeJoinedOrder: DESC. users are returned in DESC order based on
        // * timeJoined)
        {
            HashMap<String, String> queryParams = new HashMap<>();
            queryParams.put("limit", "2");
            queryParams.put("timeJoinedOrder", "DESC");
            JsonObject response = HttpRequestForTesting
                    .sendGETRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/users", queryParams, 1000,
                            1000,
                            null, Utils.getCdiVersion2_7ForTests(), "thirdparty");
            assertEquals(2, response.getAsJsonArray("users").size());

            JsonObject user_1 = response.getAsJsonArray("users").get(0).getAsJsonObject();
            JsonObject user_2 = response.getAsJsonArray("users").get(1).getAsJsonObject();

            assertEquals("test4@example.com", user_1.get("email").getAsString());
            assertEquals("test3@example.com", user_2.get("email").getAsString());

            assert (user_2.get("timeJoined").getAsLong() < user_1.get("timeJoined").getAsLong());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
