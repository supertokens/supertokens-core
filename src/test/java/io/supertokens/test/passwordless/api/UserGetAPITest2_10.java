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

package io.supertokens.test.passwordless.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.passwordless.UserInfo;
import io.supertokens.pluginInterface.passwordless.PasswordlessStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

import java.util.HashMap;

public class UserGetAPITest2_10 {
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
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", "notExists");
            map.put("email", "notExists");
            HttpResponseException error = null;
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/user", map,
                        1000, 1000, null, Utils.getCdiVersion2_10ForTests(), "passwordless");
                throw new Exception("Should not come here");
            } catch (HttpResponseException e) {
                error = e;
            }
            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of userId, email or phoneNumber",
                    error.getMessage());
        }
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", "notExists");
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, Utils.getCdiVersion2_10ForTests(),
                    "passwordless");

            assertEquals("UNKNOWN_USER_ID_ERROR", response.get("status").getAsString());
        }
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("email", "notExists");
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, Utils.getCdiVersion2_10ForTests(),
                    "passwordless");

            assertEquals("UNKNOWN_EMAIL_ERROR", response.get("status").getAsString());
        }
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("phoneNumber", "notExists");
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, Utils.getCdiVersion2_10ForTests(),
                    "passwordless");

            assertEquals("UNKNOWN_PHONE_NUMBER_ERROR", response.get("status").getAsString());
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGoodInput() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        PasswordlessStorage storage = StorageLayer.getPasswordlessStorage(process.getProcess());

        String userIdEmail = "userId";
        String userIdPhone = "userId";
        String email = "random@gmail.com";
        String phoneNumber = "1234";

        storage.createUser(new UserInfo(userIdEmail, email, null, System.currentTimeMillis()));
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", userIdEmail);
            map.put("email", email);
            HttpResponseException error = null;
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/user", map,
                        1000, 1000, null, Utils.getCdiVersion2_10ForTests(), "passwordless");
                throw new Exception("Should not come here");
            } catch (HttpResponseException e) {
                error = e;
            }
            assertNotNull(error);
            assertEquals(400, error.statusCode);
            assertEquals(
                    "Http error. Status Code: 400. Message: Please provide exactly one of userId, email or phoneNumber",
                    error.getMessage());
        }
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("phoneNumber", "notExists");
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, Utils.getCdiVersion2_10ForTests(),
                    "passwordless");

            assertEquals("UNKNOWN_PHONE_NUMBER_ERROR", response.get("status").getAsString());
        }
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", userIdEmail);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, Utils.getCdiVersion2_10ForTests(),
                    "passwordless");

            assertEquals("OK", response.get("status").getAsString());
            checkUser(response, userIdEmail, email, null);
        }
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("email", email);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, Utils.getCdiVersion2_10ForTests(),
                    "passwordless");

            assertEquals("OK", response.get("status").getAsString());
            checkUser(response, userIdEmail, email, null);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static void checkUser(JsonObject resp, String userId, String email, String phoneNumber) {
        assert (resp.has("user"));
        JsonObject user = resp.get("user").getAsJsonObject();
        assertEquals(user.get("id").getAsString(), userId);

        if (email != null) {
            assertEquals(user.get("email").getAsString(), email);
        } else {
            assert (!user.has("email"));
        }

        if (phoneNumber != null) {
            assertEquals(user.get("phoneNumber").getAsString(), phoneNumber);
        } else {
            assert (!user.has("phoneNumber"));
        }
    }
}
