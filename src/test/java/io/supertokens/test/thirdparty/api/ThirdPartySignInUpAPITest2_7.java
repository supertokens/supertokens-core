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

import io.supertokens.ActiveUsers;
import io.supertokens.ProcessState;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;
/*
 * TODO:
 *  - good input
 *  - Sign up with unnormalised email, and sign in with normailised email to get the same user.
 *  - bad input
 *     - simple bad input
 *     - email sub object's fiels are missing
 *  - all error states
 * */

public class ThirdPartySignInUpAPITest2_7 {

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

    // good input
    // failure condition: test fails if signinup response does not match api spec
    @Test
    public void testGoodInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        JsonObject response = Utils.signInUpRequest_2_7(process, "test@example.com", true, "testThirdPartyId",
                "testThirdPartyUserId");
        checkSignInUpResponse(response, "testThirdPartyId", "testThirdPartyUserId", "test@example.com", true);

        {
            JsonObject user = response.getAsJsonObject("user");
            assertTrue(EmailVerification.isEmailVerified(process.getProcess(), user.get("id").getAsString(),
                    user.get("email").getAsString()));
        }

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Sign up with unnormalised email, and sign in with normailised email to get the same user.
    // failure condition: test fails if signin causes a new user to be created
    @Test
    public void testEmailNormalisation() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject response_1 = Utils.signInUpRequest_2_7(process, "TeSt@example.com", false, "testThirdPartyId",
                "testThirdPartyUserId");
        checkSignInUpResponse(response_1, "testThirdPartyId", "testThirdPartyUserId", "test@example.com", true);

        JsonObject response_2 = Utils.signInUpRequest_2_7(process, "test@example.com", false, "testThirdPartyId",
                "testThirdPartyUserId");
        checkSignInUpResponse(response_2, "testThirdPartyId", "testThirdPartyUserId", "test@example.com", false);

        JsonObject response_1_user = response_1.getAsJsonObject("user");
        JsonObject response_2_user = response_2.getAsJsonObject("user");

        assertEquals(response_1_user.get("id").getAsString(), response_2_user.get("id").getAsString());
        assertEquals(response_1_user.get("timeJoined").getAsString(), response_2_user.get("timeJoined").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // bad input
    // simple bad input
    @Test
    public void testBadInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        {
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup", null, 1000, 1000, null,
                        SemVer.v2_7.get(), "thirdparty");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400
                        && e.getMessage().equals("Http error. Status Code: 400. Message: Invalid Json Input"));
            }
        }

        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("id", "test@example.com");
        emailObject.addProperty("isVerified", false);

        {

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("thirdPartyId", 12345);
            requestBody.addProperty("thirdPartyUserId", "testThirdPartyUserID");
            requestBody.add("email", emailObject);
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup", requestBody, 1000, 1000, null,
                        SemVer.v2_7.get(), "thirdparty");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400
                        && e.getMessage().equals("Http error. Status Code: 400. Message: Field name 'thirdPartyId' is "
                        + "invalid " + "in " + "JSON input"));
            }
        }
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("thirdPartyId", "testThirdPartyId");
            requestBody.addProperty("thirdPartyUserId", 12345);
            requestBody.add("email", emailObject);
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup", requestBody, 1000, 1000, null,
                        SemVer.v2_7.get(), "thirdparty");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage()
                        .equals("Http error. Status Code: 400. Message: Field name 'thirdPartyUserId' is " + "invalid "
                                + "in " + "JSON input"));
            }
        }

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // email sub object's fields are missing
    @Test
    public void testBadInputInEmailSubObject() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("thirdPartyId", "testThirdPartyId");
        requestBody.addProperty("thirdPartyUserId", "testThirdPartyUserID");

        {
            JsonObject emailObject = new JsonObject();
            emailObject.addProperty("isVerified", false);
            requestBody.add("email", emailObject);

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup", requestBody, 1000, 1000, null,
                        SemVer.v2_7.get(), "thirdparty");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400
                        && e.getMessage().equals("Http error. Status Code: 400. Message: Field name 'id' is "
                        + "invalid " + "in " + "JSON input"));
            }
        }
        {
            JsonObject emailObject = new JsonObject();
            emailObject.addProperty("id", "test@example.com");
            requestBody.add("email", emailObject);
            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signinup", requestBody, 1000, 1000, null,
                        SemVer.v2_7.get(), "thirdparty");
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400
                        && e.getMessage().equals("Http error. Status Code: 400. Message: Field name 'isVerified' is "
                        + "invalid " + "in " + "JSON input"));
            }
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    public static void checkSignInUpResponse(JsonObject response, String thirdPartyId, String thirdPartyUserId,
                                             String email, boolean createdNewUser) {
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(3, response.entrySet().size());
        assertEquals(createdNewUser, response.get("createdNewUser").getAsBoolean());

        JsonObject user = response.getAsJsonObject("user");
        assertNotNull(user.get("id"));
        assertNotNull(user.get("timeJoined"));
        assertEquals(email, user.get("email").getAsString());

        JsonObject userThirdParty = user.getAsJsonObject("thirdParty");
        assertEquals(2, userThirdParty.entrySet().size());
        assertEquals(thirdPartyId, userThirdParty.get("id").getAsString());
        assertEquals(thirdPartyUserId, userThirdParty.get("userId").getAsString());
    }
}
