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
import io.supertokens.emailpassword.EmailPassword;
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

/*
 * TODO: /recipe/user GET API
 *  - Check for bad input (missing fields)
 *  - Check good input works
 *  - Check for all types of output
 * */

public class ThirdPartyGetUserAPITest2_7 {

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
    // Failure condition: giving appropriate query params fails the test
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
                        new HashMap<>(), 1000, 1000, null, SemVer.v2_7.get(), "thirdparty");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage()
                        .equals("Http error. Status Code: 400. Message: Please provide one of userId or "
                                + "(thirdPartyId & thirdPartyUserId)"));
            }
        }

        // thirdPartyUserId missing
        {
            HashMap<String, String> QueryParams = new HashMap<>();
            QueryParams.put("thirdPartyId", "testThirdPartId");
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/user",
                        QueryParams, 1000, 1000, null, SemVer.v2_7.get(), "thirdparty");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage()
                        .equals("Http error. Status Code: 400. Message: Please provide one of userId or "
                                + "(thirdPartyId & thirdPartyUserId)"));
            }
        }

        // thirdPartyId missing
        {
            HashMap<String, String> QueryParams = new HashMap<>();
            QueryParams.put("thirdPartyUserId", "testThirdPartyUserId");
            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/user",
                        QueryParams, 1000, 1000, null, SemVer.v2_7.get(), "thirdparty");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage()
                        .equals("Http error. Status Code: 400. Message: Please provide one of userId or "
                                + "(thirdPartyId & thirdPartyUserId)"));
            }
        }
    }

    // Check good input works
    // Failure condition: incorrect query params/returned userInfo does not match
    @Test
    public void testGoodInput() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // check with userid and check with (thirdParty details)
        String email = "test@example.com";
        String thirdPartyId = "testThirdPartyId";
        String thirdPartyUserId = "testThirdPartyUserID";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email);

        // query with userId
        {
            HashMap<String, String> QueryParams = new HashMap<>();
            QueryParams.put("userId", signUpResponse.user.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", QueryParams, 1000, 1000, null,
                    SemVer.v2_7.get(), "thirdparty");
            assertEquals("OK", response.get("status").getAsString());

            JsonObject userInfo = response.get("user").getAsJsonObject();
            checkUser(userInfo, thirdPartyId, thirdPartyUserId, email);
        }

        // query with thirdParty
        {
            HashMap<String, String> QueryParams = new HashMap<>();
            QueryParams.put("thirdPartyId", thirdPartyId);
            QueryParams.put("thirdPartyUserId", thirdPartyUserId);

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", QueryParams, 1000, 1000, null,
                    SemVer.v2_7.get(), "thirdparty");
            assertEquals("OK", response.get("status").getAsString());

            JsonObject userInfo = response.get("user").getAsJsonObject();
            checkUser(userInfo, thirdPartyId, thirdPartyUserId, email);

        }
    }

    // - Check for all types of output
    // failure condition: valid userid/thirdParty details are sent, status message does not match
    @Test
    public void testAllTypesOfOutput() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // query with unknown userId
        {
            HashMap<String, String> QueryParams = new HashMap<>();
            QueryParams.put("userId", "randomUserId");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", QueryParams, 1000, 1000, null,
                    SemVer.v2_7.get(), "thirdparty");
            assertEquals("UNKNOWN_USER_ID_ERROR", response.get("status").getAsString());
        }

        // query with unknown thirdParty
        {
            HashMap<String, String> QueryParams = new HashMap<>();
            QueryParams.put("thirdPartyId", "randomThirdPartyId");
            QueryParams.put("thirdPartyUserId", "randomThirdPartyUserId");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", QueryParams, 1000, 1000, null,
                    SemVer.v2_7.get(), "thirdparty");
            assertEquals("UNKNOWN_THIRD_PARTY_USER_ERROR", response.get("status").getAsString());
        }
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
        Passwordless.CreateCodeResponse user2code = Passwordless.createCode(process.getProcess(), "test@example.com",
                null, null, null);
        AuthRecipeUserInfo user2 = Passwordless.consumeCode(process.getProcess(), user2code.deviceId,
                user2code.deviceIdHash, user2code.userInputCode, null).user;

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", user1.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                    "thirdparty");
            assertEquals(response.get("status").getAsString(), "UNKNOWN_USER_ID_ERROR");
        }

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", user2.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                    "thirdparty");
            assertEquals(response.get("status").getAsString(), "UNKNOWN_USER_ID_ERROR");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    public static void checkUser(JsonObject user, String thirdPartyId, String thirdPartyUserId, String email) {
        assertNotNull(user.get("id"));
        assertNotNull(user.get("timeJoined"));
        assertEquals(user.get("email").getAsString(), email);
        assertEquals(user.getAsJsonObject("thirdParty").get("userId").getAsString(), thirdPartyUserId);
        assertEquals(user.getAsJsonObject("thirdParty").get("id").getAsString(), thirdPartyId);
    }
}
