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
import io.supertokens.pluginInterface.STORAGE_TYPE;
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
 *  - Check good input works
 *  - Test that sign in with unnormalised email like Test@gmail.com should also work
 *  - Test that giving an empty password, empty email, invalid email, random email or wrong password throws a wrong
 *      credentials error
 *  - Test that an empty password yields a WRONG_CREDENTIALS_ERROR output.
 * */

public class SignInAPITest2_5 {

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
                                "http://localhost:3567/recipe/signin", null, 1000,
                                1000,
                                null, Utils.getCdiVersion2_5ForTests());
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
                                "http://localhost:3567/recipe/signin", requestBody, 1000,
                                1000,
                                null, Utils.getCdiVersion2_5ForTests());
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
                                "http://localhost:3567/recipe/signin", requestBody, 1000,
                                1000,
                                null, Utils.getCdiVersion2_5ForTests());
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

    //Check good input works
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

        JsonObject userInfo = signUpResponse.get("user").getAsJsonObject();

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("email", "random@gmail.com");
        responseBody.addProperty("password", "validPass123");

        JsonObject signInResponse = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signin", responseBody, 1000,
                        1000,
                        null, Utils.getCdiVersion2_5ForTests());

        assertEquals(signInResponse.get("status").getAsString(), "OK");
        assertEquals(signInResponse.entrySet().size(), 2);

        assertEquals(signInResponse.get("user").getAsJsonObject().get("id").getAsString(),
                userInfo.get("id").getAsString());
        assertEquals(signInResponse.get("user").getAsJsonObject().get("email").getAsString(),
                userInfo.get("email").getAsString());
        signInResponse.get("user").getAsJsonObject().get("timeJoined").getAsLong();
        assertEquals(signInResponse.get("user").getAsJsonObject().entrySet().size(), 3);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    //Test that sign in with unnormalised email like Test@gmail.com should also work
    @Test
    public void testThatUnnormalisedEmailShouldAlsoWork() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject signUpResponse = Utils.signUpRequest_2_5(process, "test@gmail.com", "validPass123");
        assertEquals(signUpResponse.get("status").getAsString(), "OK");
        assertEquals(signUpResponse.entrySet().size(), 2);

        JsonObject userInfo = signUpResponse.get("user").getAsJsonObject();

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("email", "Test@gmail.com");
        responseBody.addProperty("password", "validPass123");

        JsonObject signInResponse = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/signin", responseBody, 1000,
                        1000,
                        null, Utils.getCdiVersion2_5ForTests());

        assertEquals(signInResponse.get("status").getAsString(), "OK");
        assertEquals(signInResponse.entrySet().size(), 2);
        assertEquals(signInResponse.get("user").getAsJsonObject().get("id").getAsString(),
                userInfo.get("id").getAsString());
        assertEquals(signInResponse.get("user").getAsJsonObject().get("email").getAsString(),
                userInfo.get("email").getAsString());
        signInResponse.get("user").getAsJsonObject().get("timeJoined").getAsLong();
        assertEquals(signInResponse.get("user").getAsJsonObject().entrySet().size(), 3);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    //Test that giving an empty password, empty email, invalid email, random email or wrong password throws a wrong
    // *      credentials error
    @Test
    public void testInputsToSignInAPI() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            JsonObject responseBody = new JsonObject();
            responseBody.addProperty("email", "random@gmail.com");
            responseBody.addProperty("password", "");

            JsonObject signInResponse = io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/signin", responseBody, 1000,
                            1000,
                            null, Utils.getCdiVersion2_5ForTests());

            assertEquals(signInResponse.get("status").getAsString(), "WRONG_CREDENTIALS_ERROR");
            assertEquals(signInResponse.entrySet().size(), 1);
        }

        {
            JsonObject responseBody = new JsonObject();
            responseBody.addProperty("email", "");
            responseBody.addProperty("password", "validPass123");

            JsonObject signInResponse = io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/signin", responseBody, 1000,
                            1000,
                            null, Utils.getCdiVersion2_5ForTests());

            assertEquals(signInResponse.get("status").getAsString(), "WRONG_CREDENTIALS_ERROR");
            assertEquals(signInResponse.entrySet().size(), 1);
        }

        {
            JsonObject responseBody = new JsonObject();
            responseBody.addProperty("email", "random@gmail.com");
            responseBody.addProperty("password", "randomPassword123");

            JsonObject signInResponse = io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/signin", responseBody, 1000,
                            1000,
                            null, Utils.getCdiVersion2_5ForTests());

            assertEquals(signInResponse.get("status").getAsString(), "WRONG_CREDENTIALS_ERROR");
            assertEquals(signInResponse.entrySet().size(), 1);
        }

        {
            JsonObject signUpResponse = Utils.signUpRequest_2_5(process, "test@gmail.com", "validPass123");
            assertEquals(signUpResponse.get("status").getAsString(), "OK");
            assertEquals(signUpResponse.entrySet().size(), 2);

            JsonObject responseBody = new JsonObject();
            responseBody.addProperty("email", "test@gmail.com");
            responseBody.addProperty("password", "wrongPassword");

            JsonObject signInResponse = io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/signin", responseBody, 1000,
                            1000,
                            null, Utils.getCdiVersion2_5ForTests());

            assertEquals(signInResponse.get("status").getAsString(), "WRONG_CREDENTIALS_ERROR");
            assertEquals(signInResponse.entrySet().size(), 1);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
