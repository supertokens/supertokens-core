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
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/*
 * TODO:
 *  - Check for bad input (missing fields)
 *  - Check good input works and that user is there in db (and then call sign in)
 *  - Test the normalise email function
 *  - Test that only the normalised email is saved in the db
 *  - Test that giving an empty password throws a bad input error
 * */

public class SignUpAPITest2_7 {

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

    //Check for bad input (missing fields)
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
                io.supertokens.test.httpRequest.HttpRequest
                        .sendJsonPOSTRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/signup", null, 1000,
                                1000,
                                null, Utils.getCdiVersion2_7ForTests(), EmailPassword.RECIPE_ID);
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 &&
                        e.getMessage().equals("Http error. Status Code: 400. Message: Invalid Json Input"));
            }
        }

        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", "random@gmail.com");
            try {
                io.supertokens.test.httpRequest.HttpRequest
                        .sendJsonPOSTRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/signup", requestBody, 1000,
                                1000,
                                null, Utils.getCdiVersion2_7ForTests(), EmailPassword.RECIPE_ID);
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 &&
                        e.getMessage()
                                .equals("Http error. Status Code: 400. Message: Field name 'password' is invalid in " +
                                        "JSON input"));
            }
        }
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("password", "validPass123");
            try {
                io.supertokens.test.httpRequest.HttpRequest
                        .sendJsonPOSTRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/signup", requestBody, 1000,
                                1000,
                                null, Utils.getCdiVersion2_7ForTests(), EmailPassword.RECIPE_ID);
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 &&
                        e.getMessage()
                                .equals("Http error. Status Code: 400. Message: Field name 'email' is invalid in " +
                                        "JSON input"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    //Check good input works and that user is there in db (and then call sign in)
    @Test
    public void testGoodInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject signUpResponse = Utils.signUpRequest_2_5(process, "random@gmail.com", "validPass123");
        assertEquals(signUpResponse.get("status").getAsString(), "OK");
        assertEquals(signUpResponse.entrySet().size(), 2);

        JsonObject signUpUser = signUpResponse.get("user").getAsJsonObject();
        assertEquals(signUpUser.get("email").getAsString(), "random@gmail.com");
        assertNotNull(signUpUser.get("id"));

        UserInfo user = StorageLayer.getEmailPasswordStorage(process.getProcess())
                .getUserInfoUsingEmail("random@gmail.com");
        assertEquals(user.email, signUpUser.get("email").getAsString());
        assertEquals(user.id, signUpUser.get("id").getAsString());

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("email", "random@gmail.com");
        responseBody.addProperty("password", "validPass123");

        JsonObject signInResponse = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signin", responseBody, 1000,
                        1000,
                        null, Utils.getCdiVersion2_7ForTests(), EmailPassword.RECIPE_ID);

        assertEquals(signInResponse.get("status").getAsString(), "OK");
        assertEquals(signInResponse.entrySet().size(), 2);

        assertEquals(signInResponse.get("user").getAsJsonObject().get("id").getAsString(),
                signUpUser.get("id").getAsString());
        assertEquals(signInResponse.get("user").getAsJsonObject().get("email").getAsString(),
                signUpUser.get("email").getAsString());
        signInResponse.get("user").getAsJsonObject().get("timeJoined").getAsLong();
        assertEquals(signInResponse.get("user").getAsJsonObject().entrySet().size(), 3);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Test the normalise email function
    // Test that only the normalised email is saved in the db
    //Failure condition: If the email retrieved from the data is not normalised the test will fail
    @Test
    public void testTheNormaliseEmailFunction() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject signUpResponse = Utils.signUpRequest_2_5(process, "RaNdOm@gmail.com", "validPass123");
        assertEquals(signUpResponse.get("status").getAsString(), "OK");
        assertEquals(signUpResponse.entrySet().size(), 2);

        JsonObject signUpUser = signUpResponse.get("user").getAsJsonObject();
        assertEquals(signUpUser.get("email").getAsString(), "random@gmail.com");
        assertNotNull(signUpUser.get("id"));

        UserInfo userInfo = StorageLayer.getEmailPasswordStorage(process.getProcess())
                .getUserInfoUsingId(signUpUser.get("id").getAsString());

        assertEquals(userInfo.email, "random@gmail.com");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    //Test that giving an empty password throws a bad input error
    @Test
    public void testEmptyPasswordThrowsBadInputError() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            Utils.signUpRequest_2_5(process, "random@gmail.com", "");
            throw new Exception("Should not come here");
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 &&
                    e.getMessage().equals("Http error. Status Code: 400. Message: Password cannot be an empty string"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSignUpWithDupicateEMail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject signUpResponse = Utils.signUpRequest_2_5(process, "random@gmail.com", "validPass123");
        assertEquals(signUpResponse.get("status").getAsString(), "OK");
        assertEquals(signUpResponse.entrySet().size(), 2);

        JsonObject signUpUser = signUpResponse.get("user").getAsJsonObject();
        assertEquals(signUpUser.get("email").getAsString(), "random@gmail.com");
        assertNotNull(signUpUser.get("id"));

        JsonObject response = Utils.signUpRequest_2_5(process, "random@gmail.com", "validPass123");
        assertEquals(response.get("status").getAsString(), "EMAIL_ALREADY_EXISTS_ERROR");
        assertEquals(response.entrySet().size(), 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
