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

package io.supertokens.test.userIdMapping.recipe;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.*;

public class PasswordlessAPITest {
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
    public void testCreatingAPasswordlessUserMapTheirUserIdAndRetrieveUserId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String externalId = "externalId";
        String superTokensUserId;
        // create a passwordless User
        {
            Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.main,
                    "test@example.com", null, null, null);
            Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.main,
                    createCodeResponse.deviceId, createCodeResponse.deviceIdHash, createCodeResponse.userInputCode,
                    null);
            assertTrue(consumeCodeResponse.createdNewUser);
            superTokensUserId = consumeCodeResponse.user.getSupertokensUserId();

            // create mapping
            UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalId, null, false);

            // check that mapping exists
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);
            assertNotNull(response);
            assertEquals(response.superTokensUserId, superTokensUserId);
            assertEquals(response.externalUserId, externalId);
        }

        // sign in user again and check that externalId is present in response user
        {
            String email = "test@example.com";
            Passwordless.CreateCodeResponse createResp = Passwordless.createCode(process.getProcess(), email, null,
                    null, null);

            JsonObject consumeCodeRequestBody = new JsonObject();
            consumeCodeRequestBody.addProperty("preAuthSessionId", createResp.deviceIdHash);
            consumeCodeRequestBody.addProperty("linkCode", createResp.linkCode);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup/code/consume", consumeCodeRequestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "passwordless");
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.get("user").getAsJsonObject().get("id").getAsString(), externalId);

        }

        {
            // delete User and check that the mapping is also deleted
            AuthRecipe.deleteUser(process.main, superTokensUserId);

            // check that mapping no longer exists
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);
            assertNull(response);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingAPasswordlessUserAndRetrieveInfo() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String externalId = "externalId";
        String superTokensUserId;
        String email = "test@example.com";
        // create a passwordless User
        {
            Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.main, email, null,
                    null, null);
            Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.main,
                    createCodeResponse.deviceId, createCodeResponse.deviceIdHash, createCodeResponse.userInputCode,
                    null);
            assertTrue(consumeCodeResponse.createdNewUser);
            superTokensUserId = consumeCodeResponse.user.getSupertokensUserId();

            // create mapping
            UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalId, null, false);

            // check that mapping exists
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);
            assertNotNull(response);
            assertEquals(response.superTokensUserId, superTokensUserId);
            assertEquals(response.externalUserId, externalId);
        }

        {
            // retrieving UserInfo with email
            HashMap<String, String> query = new HashMap<>();
            query.put("email", email);

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", query, 1000, 1000, null, SemVer.v2_15.get(),
                    "passwordless");
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(externalId, response.get("user").getAsJsonObject().get("id").getAsString());

        }

        {
            // retrieving UserInfo with superTokensUserId
            {
                HashMap<String, String> query = new HashMap<>();
                query.put("userId", superTokensUserId);

                JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user", query, 1000, 1000, null, SemVer.v2_15.get(),
                        "passwordless");
                assertEquals("OK", response.get("status").getAsString());
                assertEquals(externalId, response.get("user").getAsJsonObject().get("id").getAsString());
            }

            // retrieving UserInfo with externalId
            {
                HashMap<String, String> query = new HashMap<>();
                query.put("userId", externalId);

                JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user", query, 1000, 1000, null, SemVer.v2_15.get(),
                        "passwordless");
                assertEquals("OK", response.get("status").getAsString());
                assertEquals(externalId, response.get("user").getAsJsonObject().get("id").getAsString());
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingPasswordlessUserWithPhoneNumberAndRetrieveInfo() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String externalId = "externalId";
        String superTokensUserId;
        String phoneNumber = "+911234567890";
        // create a passwordless User
        {
            Passwordless.CreateCodeResponse createCodeResponse = Passwordless.createCode(process.main, null,
                    phoneNumber, null, null);
            Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.main,
                    createCodeResponse.deviceId, createCodeResponse.deviceIdHash, createCodeResponse.userInputCode,
                    null);
            assertTrue(consumeCodeResponse.createdNewUser);
            superTokensUserId = consumeCodeResponse.user.getSupertokensUserId();

            // create mapping
            UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalId, null, false);

            // check that mapping exists
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);
            assertNotNull(response);
            assertEquals(response.superTokensUserId, superTokensUserId);
            assertEquals(response.externalUserId, externalId);
        }

        {
            // retrieving UserInfo with phoneNumber
            HashMap<String, String> query = new HashMap<>();
            query.put("phoneNumber", phoneNumber);

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", query, 1000, 1000, null, SemVer.v2_15.get(),
                    "passwordless");
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(externalId, response.get("user").getAsJsonObject().get("id").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingPasswordlessUserWithTheirExternalId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String externalId = "externalId";
        String superTokensUserId;

        // create a passwordless user
        Passwordless.CreateCodeResponse response = Passwordless.createCode(process.main, "test@example.com", null, null,
                null);
        Passwordless.ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(process.main, response.deviceId,
                response.deviceIdHash, response.userInputCode, null);
        superTokensUserId = consumeCodeResponse.user.getSupertokensUserId();

        // map their userId
        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalId, null, false);

        // call the update API and update email
        String newEmail = "testnew@example.com";
        {
            JsonObject updateUserRequestBody = new JsonObject();
            updateUserRequestBody.addProperty("userId", externalId);
            updateUserRequestBody.addProperty("email", newEmail);

            JsonObject updateUserResponse = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", updateUserRequestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "passwordless");
            assertEquals(updateUserResponse.get("status").getAsString(), "OK");

            // check that user got updated
            AuthRecipeUserInfo userInfo = Passwordless.getUserByEmail(process.main, newEmail);
            assertNotNull(userInfo);
            assertEquals(userInfo.getSupertokensUserId(), superTokensUserId);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
