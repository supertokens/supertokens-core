/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.authRecipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;

import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.Passwordless.CreateCodeResponse;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.thirdparty.ThirdParty;

public class GetUsersWithSearchTagsAPITest {
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
    public void testSearchingWhenFieldsHaveEmptyInputsWillBehaveLikeRegularPaginationAPI() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create emailpassword user
        ArrayList<String> userIds = new ArrayList<>();
        userIds.add(
                EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123").getSupertokensUserId());

        // create thirdparty user
        userIds.add(ThirdParty.signInUp(process.getProcess(), "testTPID", "test",
                "test2@example.com").user.getSupertokensUserId());

        // create passwordless user
        CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), "test@example.com", null,
                null, null);
        userIds.add(Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId,
                createCodeResponse.deviceIdHash,
                createCodeResponse.userInputCode, null).user.getSupertokensUserId());

        // search with empty input for email field
        {
            HashMap<String, String> params = new HashMap<>();
            params.put("email", ";;  ;");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users", params, 1000, 1000, null, SemVer.v2_18.get(), null);
            assertEquals("OK", response.get("status").getAsString());
            JsonArray users = response.get("users").getAsJsonArray();

            for (int i = 0; i < userIds.size(); i++) {
                assertTrue(userIds.contains(
                        users.get(i).getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString()));
            }
        }

        // search with empty input for phone field
        {
            HashMap<String, String> params = new HashMap<>();
            params.put("phone", ";;  ;");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users", params, 1000, 1000, null, SemVer.v2_18.get(), null);
            assertEquals("OK", response.get("status").getAsString());
            JsonArray users = response.get("users").getAsJsonArray();

            for (int i = 0; i < userIds.size(); i++) {
                assertTrue(userIds.contains(
                        users.get(i).getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString()));
            }
        }

        // search with empty input for provider field
        {
            HashMap<String, String> params = new HashMap<>();
            params.put("phone", ";;  ;");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users", params, 1000, 1000, null, SemVer.v2_18.get(), null);
            assertEquals("OK", response.get("status").getAsString());
            JsonArray users = response.get("users").getAsJsonArray();

            for (int i = 0; i < userIds.size(); i++) {
                assertTrue(userIds.contains(
                        users.get(i).getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString()));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSearchingForUsers() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create emailpassword user
        ArrayList<String> userIds = new ArrayList<>();
        userIds.add(
                EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123").getSupertokensUserId());
        userIds.add(
                EmailPassword.signUp(process.getProcess(), "test2@example.com", "testPass123").getSupertokensUserId());

        // create thirdparty user
        userIds.add(ThirdParty.signInUp(process.getProcess(), "testTPID", "test",
                "test2@example.com").user.getSupertokensUserId());

        // create passwordless user
        CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), "test@example.com", null,
                null, null);
        userIds.add(Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId,
                createCodeResponse.deviceIdHash,
                createCodeResponse.userInputCode, null).user.getSupertokensUserId());

        // search with partial input for email field
        HashMap<String, String> params = new HashMap<>();
        params.put("email", "test");

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users", params, 1000, 1000, null, SemVer.v2_18.get(), null);
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(4, response.get("users").getAsJsonArray().size());
        JsonArray users = response.get("users").getAsJsonArray();

        for (int i = 0; i < userIds.size(); i++) {
            assertTrue(userIds
                    .contains(users.get(i).getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString()));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSearchingForUsersWithMultipleInputsForEachField() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create emailpassword user
        ArrayList<String> userIds = new ArrayList<>();
        userIds.add(
                EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123").getSupertokensUserId());
        Thread.sleep(50);
        userIds.add(
                EmailPassword.signUp(process.getProcess(), "abc@example.com", "testPass123").getSupertokensUserId());
        Thread.sleep(50);

        // search with multiple inputs to email
        {
            HashMap<String, String> params = new HashMap<>();
            params.put("email", "test;abc");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users", params, 1000, 1000, null, SemVer.v2_18.get(), null);
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(2, response.get("users").getAsJsonArray().size());
            JsonArray users = response.get("users").getAsJsonArray();

            for (int i = 0; i < userIds.size(); i++) {
                assertEquals(userIds.get(i),
                        users.get(i).getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString());
            }
        }

        // create thirdparty user
        userIds.add(ThirdParty.signInUp(process.getProcess(), "testpid", "test",
                "test@example.com").user.getSupertokensUserId());
        Thread.sleep(50);
        userIds.add(ThirdParty.signInUp(process.getProcess(), "newtestpid", "test123",
                "test@example.com").user.getSupertokensUserId());
        Thread.sleep(50);
        // search with multiple inputs to provider
        {
            HashMap<String, String> params = new HashMap<>();
            params.put("provider", "test;newtest");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users", params, 1000, 1000, null, SemVer.v2_18.get(), null);
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(2, response.get("users").getAsJsonArray().size());
            JsonArray users = response.get("users").getAsJsonArray();
            assertEquals(userIds.get(2),
                    users.get(0).getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString());
            assertEquals(userIds.get(3),
                    users.get(1).getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString());

        }

        // create passwordless user
        {
            CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), "test@example.com",
                    "+121234567890",
                    null, null);
            userIds.add(Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId,
                    createCodeResponse.deviceIdHash,
                    createCodeResponse.userInputCode, null).user.getSupertokensUserId());
        }
        Thread.sleep(50);
        {
            CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), "test2@example.com",
                    "+911987654321",
                    null, null);
            userIds.add(Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId,
                    createCodeResponse.deviceIdHash,
                    createCodeResponse.userInputCode, null).user.getSupertokensUserId());
        }
        Thread.sleep(50);

        // search with multiple inputs to phone
        {
            HashMap<String, String> params = new HashMap<>();
            params.put("phone", "+121;+911");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users", params, 1000, 1000, null, SemVer.v2_18.get(), null);
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(2, response.get("users").getAsJsonArray().size());
            JsonArray users = response.get("users").getAsJsonArray();
            assertEquals(userIds.get(4),
                    users.get(0).getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString());
            assertEquals(userIds.get(5),
                    users.get(1).getAsJsonObject().get("user").getAsJsonObject().get("id").getAsString());
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUsersWithConflictingTagsReturnsEmptyList() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create emailpassword user
        ArrayList<String> userIds = new ArrayList<>();
        userIds.add(
                EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123").getSupertokensUserId());

        // create thirdparty user
        userIds.add(ThirdParty.signInUp(process.getProcess(), "testTPID", "test",
                "test@example.com").user.getSupertokensUserId());

        // create passwordless user
        CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), "test@example.com",
                "+101234566907",
                null, null);
        userIds.add(Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId,
                createCodeResponse.deviceIdHash,
                createCodeResponse.userInputCode, null).user.getSupertokensUserId());

        HashMap<String, String> params = new HashMap<>();
        params.put("email", "test@example.com");
        params.put("phone", "+101234566907");
        params.put("provider", "testTPID");

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users", params, 1000, 1000, null, SemVer.v2_18.get(),
                null);
        assertEquals(2, response.entrySet().size());
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(0, response.get("users").getAsJsonArray().size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testNormalizingSearchInputsWorksCorrectly() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create emailpassword user
        ArrayList<String> userIds = new ArrayList<>();
        userIds.add(
                EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123").getSupertokensUserId());

        // create thirdparty user
        userIds.add(ThirdParty.signInUp(process.getProcess(), "testpid", "test",
                "test@example.com").user.getSupertokensUserId());

        {
            // searching for email with upper and lower case combination
            HashMap<String, String> params = new HashMap<>();
            params.put("email", "tEsT@example.com");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users", params, 1000, 1000, null, SemVer.v2_18.get(),
                    null);
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(2, response.get("users").getAsJsonArray().size());
        }

        {
            // searching for provider with upper and lower case combination
            HashMap<String, String> params = new HashMap<>();
            params.put("provider", "TestPid");
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/users", params, 1000, 1000, null, SemVer.v2_18.get(),
                    null);
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(1, response.get("users").getAsJsonArray().size());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testMultipleParams() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        EmailPassword.signUp(process.getProcess(), "a@supertokens.com", "password");

        EmailPassword.signUp(process.getProcess(), "b@supertokens.com", "password");

        EmailPassword.signUp(process.getProcess(), "c@supertokens.com", "password");

        EmailPassword.signUp(process.getProcess(), "johndoe@testing.weird", "password");

        ThirdParty.signInUp(process.getProcess(), "kakao", "305773f6-2857-4591-93e9-9ed68c1936c6",
                "johndoe@testing.weird");
        ThirdParty.signInUp(process.getProcess(), "google", "648f3b76-4a5e-4f62-a181-ee09b6f3f1bb",
                "thirdparty+ABC@gmail.com");

        HashMap<String, String> params = new HashMap<>();
        params.put("email", "a;g;b");
        params.put("provider", "k");

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/users", params, 1000, 1000, null, SemVer.v2_18.get(),
                null);

        assertEquals("OK", response.get("status").getAsString());
        assertEquals(0, response.get("users").getAsJsonArray().size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

}
