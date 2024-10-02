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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.thirdparty.ThirdParty;
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

public class ThirdPartyAPITest {
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
    public void testSignInAPI() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a User
        String thirdPartyId = "google";
        String thirdPartyUserId = "test-google";
        String email = "test@example.com";
        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.main, thirdPartyId, thirdPartyUserId,
                email);
        String superTokensUserId = signInUpResponse.user.getSupertokensUserId();
        String externalUserId = "externalId";

        // create the mapping
        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, null, false);

        // check that mapping exists
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);
            assertNotNull(response);
            assertEquals(response.superTokensUserId, superTokensUserId);
            assertEquals(response.externalUserId, externalUserId);
        }

        // call signIn api and check that the externalId is returned to the response
        {
            JsonObject signInRequestBody = new JsonObject();
            signInRequestBody.addProperty("thirdPartyId", thirdPartyId);
            signInRequestBody.addProperty("thirdPartyUserId", thirdPartyUserId);
            JsonObject emailJsonObject = new JsonObject();
            emailJsonObject.addProperty("id", email);
            signInRequestBody.add("email", emailJsonObject);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signinup", signInRequestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "thirdparty");
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(externalUserId, response.get("user").getAsJsonObject().get("id").getAsString());
        }

        // delete User and check that the mapping no longer exists
        {
            AuthRecipe.deleteUser(process.main, superTokensUserId);
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);
            assertNull(response);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetUsersByEmailAPI() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a User
        String thirdPartyId = "google";
        String thirdPartyUserId = "test-google";
        String email = "test@example.com";
        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.main, thirdPartyId, thirdPartyUserId,
                email);
        String superTokensUserId = signInUpResponse.user.getSupertokensUserId();
        String externalUserId = "externalId";

        // create the mapping
        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, null, false);

        // call getUsersByEmail
        {
            HashMap<String, String> query = new HashMap<>();
            query.put("email", email);

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/users/by-email", query, 1000, 1000, null,
                    SemVer.v2_15.get(), "thirdparty");
            assertEquals("OK", response.get("status").getAsString());

            JsonArray users = response.get("users").getAsJsonArray();
            assertEquals(1, users.size());
            assertEquals(externalUserId, users.get(0).getAsJsonObject().get("id").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetUserById() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a User
        String thirdPartyId = "google";
        String thirdPartyUserId = "test-google";
        String email = "test@example.com";
        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.main, thirdPartyId, thirdPartyUserId,
                email);
        String superTokensUserId = signInUpResponse.user.getSupertokensUserId();
        String externalUserId = "externalId";

        // create the mapping
        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, null, false);

        // get User with Id
        {
            HashMap<String, String> query = new HashMap<>();
            query.put("userId", superTokensUserId);

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", query, 1000, 1000, null, SemVer.v2_15.get(),
                    "thirdparty");

            assertEquals("OK", response.get("status").getAsString());
            String responseId = response.get("user").getAsJsonObject().get("id").getAsString();
            assertEquals(externalUserId, responseId);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetUserByThirdPartyId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a User
        String thirdPartyId = "google";
        String thirdPartyUserId = "test-google";
        String email = "test@example.com";
        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.main, thirdPartyId, thirdPartyUserId,
                email);
        String superTokensUserId = signInUpResponse.user.getSupertokensUserId();
        String externalUserId = "externalId";

        // create the mapping
        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, null, false);

        // get User with Id
        {
            HashMap<String, String> query = new HashMap<>();
            query.put("thirdPartyId", thirdPartyId);
            query.put("thirdPartyUserId", thirdPartyUserId);

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", query, 1000, 1000, null, SemVer.v2_15.get(),
                    "thirdparty");

            assertEquals("OK", response.get("status").getAsString());
            String responseId = response.get("user").getAsJsonObject().get("id").getAsString();
            assertEquals(externalUserId, responseId);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
