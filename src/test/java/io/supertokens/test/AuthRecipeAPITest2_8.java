/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.utils.SemVer;
import org.junit.*;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;

public class AuthRecipeAPITest2_8 {
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
    public void getUsersCountArrayFormat() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users/count", null, 1000, 1000, null, SemVer.v2_8.get(), "");
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.get("count").getAsLong(), 0);
        }

        EmailPassword.signUp(process.getProcess(), "test0@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "test1@example.com", "password1");

        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users/count", null, 1000, 1000, null, SemVer.v2_8.get(), "");
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.get("count").getAsLong(), 2);
        }

        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users/count?includeRecipeIds=emailpassword", null, 1000, 1000, null,
                    SemVer.v2_8.get(), "");
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.get("count").getAsLong(), 2);
        }

        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users/count?includeRecipeIds=thirdparty", null, 1000, 1000, null,
                    SemVer.v2_8.get(), "");
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.get("count").getAsLong(), 0);
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId_1 = "thirdPartyUserIdA";
        String email_1 = "testA@example.com";

        ThirdParty.signInUp(process.getProcess(), thirdPartyId, thirdPartyUserId_1, email_1);

        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users/count", null, 1000, 1000, null, SemVer.v2_8.get(), "");
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.get("count").getAsLong(), 3);
        }

        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users/count?includeRecipeIds=emailpassword,thirdparty", null, 1000, 1000,
                    null, SemVer.v2_8.get(), "");
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.get("count").getAsLong(), 3);
        }

        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users/count?includeRecipeIds=emailpassword", null, 1000, 1000, null,
                    SemVer.v2_8.get(), "");
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.get("count").getAsLong(), 2);
        }

        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users/count?includeRecipeIds=thirdparty", null, 1000, 1000, null,
                    SemVer.v2_8.get(), "");
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.get("count").getAsLong(), 1);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void getUsersCountBadInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users/count?includeRecipeIds=thirdparty,random", null, 1000, 1000, null,
                    SemVer.v2_8.get(), "");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400
                    && e.getMessage().equals("Http error. Status Code: 400. Message: Unknown recipe ID: random"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void paginationtBadInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users?includeRecipeIds=thirdparty,random", null, 1000, 1000, null,
                    SemVer.v2_8.get(), "");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400
                    && e.getMessage().equals("Http error. Status Code: 400. Message: Unknown recipe ID: random"));
        }

        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/users?limit=-1", null,
                    1000, 1000, null, SemVer.v2_8.get(), "");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage().equals(
                    "Http error. Status Code: 400. Message: limit must a positive integer with min " + "value 1"));
        }

        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/users?limit=501",
                    null, 1000, 1000, null, SemVer.v2_8.get(), "");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400
                    && e.getMessage().equals("Http error. Status Code: 400. Message: max limit allowed is 500"));
        }

        {
            HashMap<String, String> QueryParams = new HashMap<String, String>();
            QueryParams.put("paginationToken", "randomString");
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/users",
                        QueryParams, 1000, 1000, null, SemVer.v2_7.get(), "");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400
                        && e.getMessage().equals("Http error. Status Code: 400. Message: invalid pagination token"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGoodInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // added Thread.sleep(100) as sometimes tests would fail due to inconsistent signup order
        ThirdParty.signInUp(process.getProcess(), "thirdPartyId", "thirdPartyUserId", "test@example.com");
        Thread.sleep(100);
        ThirdParty.signInUp(process.getProcess(), "thirdPartyId", "thirdPartyUserId1", "test1@example.com");
        Thread.sleep(100);
        ThirdParty.signInUp(process.getProcess(), "thirdPartyId", "thirdPartyUserId2", "test2@example.com");
        Thread.sleep(100);
        EmailPassword.signUp(process.getProcess(), "test3@example.com", "password123$");
        Thread.sleep(100);
        EmailPassword.signUp(process.getProcess(), "test4@example.com", "password123$");
        Thread.sleep(100);

        {
            HashMap<String, String> queryParams = new HashMap<>();
            queryParams.put("limit", "1");
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users", queryParams, 1000, 1000, null, SemVer.v2_7.get(), "");

            Assert.assertEquals("OK", response.get("status").getAsString());
            assertNotNull(response.get("nextPaginationToken"));
            Assert.assertEquals(1, response.getAsJsonArray("users").size());

            JsonObject user = response.getAsJsonArray("users").get(0).getAsJsonObject();
            Assert.assertEquals("thirdparty", user.get("recipeId").getAsString());
            user = user.getAsJsonObject("user");
            assertNotNull(user.get("id"));
            assertNotNull(user.get("timeJoined"));
            Assert.assertEquals("test@example.com", user.get("email").getAsString());

            JsonObject userThirdParty = user.get("thirdParty").getAsJsonObject();
            Assert.assertEquals("thirdPartyId", userThirdParty.get("id").getAsString());
            Assert.assertEquals("thirdPartyUserId", userThirdParty.get("userId").getAsString());
        }

        // no params passed should return 5 users
        {
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users", new HashMap<>(), 1000, 1000, null, SemVer.v2_7.get(),
                    "");
            Assert.assertEquals(5, response.getAsJsonArray("users").size());

            {
                JsonObject user = response.getAsJsonArray("users").get(0).getAsJsonObject();
                Assert.assertEquals("thirdparty", user.get("recipeId").getAsString());
                user = user.getAsJsonObject("user");
                assertNotNull(user.get("id"));
                assertNotNull(user.get("timeJoined"));
                Assert.assertEquals("test@example.com", user.get("email").getAsString());

                JsonObject userThirdParty = user.get("thirdParty").getAsJsonObject();
                Assert.assertEquals("thirdPartyId", userThirdParty.get("id").getAsString());
                Assert.assertEquals("thirdPartyUserId", userThirdParty.get("userId").getAsString());
            }

            {
                JsonObject user = response.getAsJsonArray("users").get(4).getAsJsonObject();
                Assert.assertEquals("emailpassword", user.get("recipeId").getAsString());
                user = user.getAsJsonObject("user");
                assertNotNull(user.get("id"));
                assertNotNull(user.get("timeJoined"));
                Assert.assertEquals("test4@example.com", user.get("email").getAsString());
            }

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
