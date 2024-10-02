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
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.passwordless.PasswordlessStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;

import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.utils.SemVer;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PasswordlessUserGetAPITest2_11 {
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
            HashMap<String, String> map = new HashMap<>();
            HttpResponseException error = null;
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/user", map,
                        1000, 1000, null, SemVer.v2_10.get(), "passwordless");
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
            map.put("email", "notExists");
            HttpResponseException error = null;
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/user", map,
                        1000, 1000, null, SemVer.v2_10.get(), "passwordless");
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
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_10.get(),
                    "passwordless");

            assertEquals("UNKNOWN_USER_ID_ERROR", response.get("status").getAsString());
        }
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("email", "notExists");
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_10.get(),
                    "passwordless");

            assertEquals("UNKNOWN_EMAIL_ERROR", response.get("status").getAsString());
        }
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("phoneNumber", "notExists");
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_10.get(),
                    "passwordless");

            assertEquals("UNKNOWN_PHONE_NUMBER_ERROR", response.get("status").getAsString());
        }
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("phoneNumber", "+918888823456");
            map.put("email", "sample@test.com");
            Exception exception = null;
            try {
                JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_10.get(),
                        "passwordless");
            } catch (Exception ex) {
                exception = ex;
            }

            assertNotNull(exception);
            assert (exception instanceof HttpResponseException);
            assertEquals(400, ((HttpResponseException) exception).statusCode);
            assertEquals(exception.getMessage(),
                    "Http error. Status Code: 400. Message: Please provide exactly one of userId, email or " +
                            "phoneNumber");
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

        PasswordlessStorage storage = (PasswordlessStorage) StorageLayer.getStorage(process.getProcess());

        // length of user ID needs to be 36 character long, otherwise it throws error
        // with postgres DB
        String userIdEmail = "pZ9SP0USbXbejGFO6qx7x3JBjupJZVtw4RkF";
        String userIdPhone = "pZ9SP0USbXbejGFO6qx7x3JBjupJZVtw4RkD";
        String email = "random@gmail.com";
        String phoneNumber = "1234";

        storage.createUser(new TenantIdentifier(null, null, null),
                userIdEmail, email, null, System.currentTimeMillis());
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", userIdEmail);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_10.get(),
                    "passwordless");

            assertEquals("OK", response.get("status").getAsString());
            checkUser(response, userIdEmail, email, null);
        }
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("email", email.toUpperCase());
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_10.get(),
                    "passwordless");

            assertEquals("OK", response.get("status").getAsString());
            checkUser(response, userIdEmail, email, null);
        }

        /*
         * get user with phone number
         */
        storage.createUser(new TenantIdentifier(null, null, null),
                userIdPhone, null, phoneNumber, System.currentTimeMillis());
        {
            HashMap<String, String> map = new HashMap<>();
            map.put("phoneNumber", phoneNumber);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_10.get(),
                    "passwordless");

            assertEquals("OK", response.get("status").getAsString());
            checkUser(response, userIdPhone, null, phoneNumber);
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

        assert (user.has("timeJoined"));
        assert (System.currentTimeMillis() - 10000 < user.get("timeJoined").getAsLong());
        assertEquals(3, user.entrySet().size());
    }

    @Test
    public void testGetUserForUsersOfOtherRecipeIds() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        AuthRecipeUserInfo user2 = ThirdParty.signInUp(process.getProcess(), "google", "googleid",
                "test@example.com").user;

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", user1.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                    "passwordless");
            assertEquals(response.get("status").getAsString(), "UNKNOWN_USER_ID_ERROR");
        }

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", user2.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                    "passwordless");
            assertEquals(response.get("status").getAsString(), "UNKNOWN_USER_ID_ERROR");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
