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

package io.supertokens.test.emailpassword.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.*;

public class EmailPasswordGetUserAPITest2_7 {

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
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/user",
                        null, 1000, 1000, null, SemVer.v2_7.get(), "emailpassword");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage()
                        .equals("Http error. Status Code: 400. Message: Please provide one of userId or " + "email"));
            }
        }

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", "randomID");
            map.put("email", "random@gmail.com");
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/user", map,
                        1000, 1000, null, SemVer.v2_7.get(), "emailpassword");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage().equals(
                        "Http error. Status Code: 400. Message: Please provide only one of userId or " + "email"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Check good input works
    @Test
    public void testGoodInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            JsonObject signUpResponse = Utils.signUpRequest_2_5(process, "random@gmail.com", "validPass123");
            assertEquals(signUpResponse.get("status").getAsString(), "OK");
            assertEquals(signUpResponse.entrySet().size(), 2);

            JsonObject signUpUser = signUpResponse.get("user").getAsJsonObject();
            assertEquals(signUpUser.get("email").getAsString(), "random@gmail.com");
            assertNotNull(signUpUser.get("id"));

            HashMap<String, String> map = new HashMap<>();
            map.put("email", "randoM@gmail.com");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                    "emailpassword");
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.entrySet().size(), 2);

            JsonObject userInfo = signUpResponse.get("user").getAsJsonObject();
            assertEquals(signUpUser.get("email").getAsString(), userInfo.get("email").getAsString());
            assertEquals(signUpUser.get("id").getAsString(), userInfo.get("id").getAsString());
            signUpUser.get("timeJoined").getAsLong();
            assertEquals(signUpUser.entrySet().size(), 3);
        }

        {
            JsonObject signUpResponse = Utils.signUpRequest_2_5(process, "random2@gmail.com", "validPass123");
            assertEquals(signUpResponse.get("status").getAsString(), "OK");
            assertEquals(signUpResponse.entrySet().size(), 2);

            JsonObject signUpUser = signUpResponse.get("user").getAsJsonObject();
            assertEquals(signUpUser.get("email").getAsString(), "random2@gmail.com");
            assertNotNull(signUpUser.get("id"));

            HashMap<String, String> map = new HashMap<>();
            map.put("userId", signUpUser.get("id").getAsString());

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                    "emailpassword");
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.entrySet().size(), 2);

            JsonObject userInfo = signUpResponse.get("user").getAsJsonObject();
            assertEquals(signUpUser.get("email").getAsString(), userInfo.get("email").getAsString());
            assertEquals(signUpUser.get("id").getAsString(), userInfo.get("id").getAsString());
            signUpUser.get("timeJoined").getAsLong();
            assertEquals(signUpUser.entrySet().size(), 3);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Check for all types of output
    // Failure condition: passing a valid email/userId will cause the test to fail
    @Test
    public void testForAllTypesOfOutput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("email", "random@gmail.com");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                    "emailpassword");
            assertEquals(response.get("status").getAsString(), "UNKNOWN_EMAIL_ERROR");
            assertEquals(response.entrySet().size(), 1);
        }

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", "randomId");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                    "emailpassword");
            assertEquals(response.get("status").getAsString(), "UNKNOWN_USER_ID_ERROR");
            assertEquals(response.entrySet().size(), 1);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetUserForUsersOfOtherRecipeIds() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user1 = ThirdParty.signInUp(process.getProcess(), "google", "googleid",
                "test@example.com").user;
        Passwordless.CreateCodeResponse user2code = Passwordless.createCode(process.getProcess(), "test@example.com",
                null, null, null);
        AuthRecipeUserInfo user2 = Passwordless.consumeCode(process.getProcess(), user2code.deviceId,
                user2code.deviceIdHash, user2code.userInputCode, null).user;

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", user1.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                    "emailpassword");
            assertEquals(response.get("status").getAsString(), "UNKNOWN_USER_ID_ERROR");
        }

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", user2.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                    "emailpassword");
            assertEquals(response.get("status").getAsString(), "UNKNOWN_USER_ID_ERROR");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
