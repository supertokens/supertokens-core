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

public class VerifyEmailAPITest2_5 {

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
            try {
                io.supertokens.test.httpRequest.HttpRequest
                        .sendJsonPOSTRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/user/email/verify", null, 1000,
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
            try {
                io.supertokens.test.httpRequest.HttpRequest
                        .sendJsonPOSTRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/user/email/verify", requestBody, 1000,
                                1000,
                                null, Utils.getCdiVersion2_5ForTests());
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 &&
                        e.getMessage()
                                .equals("Http error. Status Code: 400. Message: Field name 'method' is invalid in " +
                                        "JSON input"));
            }
        }

        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("method", "token");
            requestBody.addProperty("token", 12345);
            try {
                io.supertokens.test.httpRequest.HttpRequest
                        .sendJsonPOSTRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/user/email/verify", requestBody, 1000,
                                1000,
                                null, Utils.getCdiVersion2_5ForTests());
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 &&
                        e.getMessage()
                                .equals("Http error. Status Code: 400. Message: Field name 'token' is invalid in " +
                                        "JSON input"));
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

        JsonObject signUpResponse = Utils.signUpRequest_2_5(process, "random@gmail.com", "validPass123");
        assertEquals(signUpResponse.get("status").getAsString(), "OK");
        assertEquals(signUpResponse.entrySet().size(), 2);

        String userId = signUpResponse.get("user").getAsJsonObject().get("id").getAsString();
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);


        JsonObject response = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/email/verify/token", requestBody, 1000,
                        1000,
                        null, Utils.getCdiVersion2_5ForTests());

        assertEquals(response.entrySet().size(), 2);
        assertEquals(response.get("status").getAsString(), "OK");
        assertNotNull(response.get("token"));

        JsonObject verifyResponseBody = new JsonObject();
        verifyResponseBody.addProperty("method", "token");
        verifyResponseBody.addProperty("token", response.get("token").getAsString());

        JsonObject response2 = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/user/email/verify", verifyResponseBody, 1000,
                        1000,
                        null, Utils.getCdiVersion2_5ForTests());

        assertEquals(response2.entrySet().size(), 2);
        assertEquals(response2.get("status").getAsString(), "OK");

        assertEquals(response2.get("user").getAsJsonObject().entrySet().size(), 3);
        assertEquals(response2.get("user").getAsJsonObject().get("id").getAsString(), userId);
        assertEquals(response2.get("user").getAsJsonObject().get("email").getAsString(), "random@gmail.com");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Check for all types of output
    @Test
    public void testAllTypesOfOutput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // using incorrect method
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("method", "invalidMethod");
            requestBody.addProperty("token", "randomToken");

            try {
                io.supertokens.test.httpRequest.HttpRequest
                        .sendJsonPOSTRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/user/email/verify", requestBody, 1000,
                                1000,
                                null, Utils.getCdiVersion2_5ForTests());
                throw new Exception("Should not have come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 && e.getMessage()
                        .equals("Http error. Status Code: 400. Message: Unsupported method for email verification"));
            }
        }

        // passing invalid token
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("method", "token");
            requestBody.addProperty("token", "invalidToken");

            JsonObject response2 = io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/user/email/verify", requestBody, 1000,
                            1000,
                            null, Utils.getCdiVersion2_5ForTests());
            assertEquals(response2.get("status").getAsString(), "EMAIL_VERIFICATION_INVALID_TOKEN_ERROR");
            assertEquals(response2.entrySet().size(), 1);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
