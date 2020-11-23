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

public class GetUserAPITest2_4 {

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

        {
            try {
                io.supertokens.test.httpRequest.HttpRequest
                        .sendGETRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/user", null, 1000,
                                1000,
                                null, Utils.getCdiVersion2_4ForTests());
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 &&
                        e.getMessage()
                                .equals("Http error. Status Code: 400. Message: Please provide one of userId or " +
                                        "email"));
            }
        }

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", "randomID");
            map.put("email", "random@gmail.com");
            try {
                io.supertokens.test.httpRequest.HttpRequest
                        .sendGETRequest(process.getProcess(), "",
                                "http://localhost:3567/recipe/user", map, 1000,
                                1000,
                                null, Utils.getCdiVersion2_4ForTests());
                throw new Exception("Should not come here");
            } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
                assertTrue(e.statusCode == 400 &&
                        e.getMessage()
                                .equals("Http error. Status Code: 400. Message: Please provide only one of userId or " +
                                        "email"));
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

        {
            JsonObject signUpResponse = Utils.signUpRequest(process, "random@gmail.com", "validPass123");
            assertEquals(signUpResponse.get("status").getAsString(), "OK");
            assertEquals(signUpResponse.entrySet().size(), 2);

            JsonObject signUpUser = signUpResponse.get("user").getAsJsonObject();
            assertEquals(signUpUser.get("email").getAsString(), "random@gmail.com");
            assertNotNull(signUpUser.get("id"));

            HashMap<String, String> map = new HashMap<>();
            map.put("email", "random@gmail.com");

            JsonObject response = io.supertokens.test.httpRequest.HttpRequest
                    .sendGETRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/user", map, 1000,
                            1000,
                            null, Utils.getCdiVersion2_4ForTests());
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.entrySet().size(), 2);

            JsonObject userInfo = signUpResponse.get("user").getAsJsonObject();
            assertEquals(signUpUser.get("email").getAsString(), userInfo.get("email").getAsString());
            assertEquals(signUpUser.get("id").getAsString(), userInfo.get("id").getAsString());
        }

        {
            JsonObject signUpResponse = Utils.signUpRequest(process, "random2@gmail.com", "validPass123");
            assertEquals(signUpResponse.get("status").getAsString(), "OK");
            assertEquals(signUpResponse.entrySet().size(), 2);

            JsonObject signUpUser = signUpResponse.get("user").getAsJsonObject();
            assertEquals(signUpUser.get("email").getAsString(), "random2@gmail.com");
            assertNotNull(signUpUser.get("id"));

            HashMap<String, String> map = new HashMap<>();
            map.put("userId", signUpUser.get("id").getAsString());

            JsonObject response = io.supertokens.test.httpRequest.HttpRequest
                    .sendGETRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/user", map, 1000,
                            1000,
                            null, Utils.getCdiVersion2_4ForTests());
            assertEquals(response.get("status").getAsString(), "OK");
            assertEquals(response.entrySet().size(), 2);

            JsonObject userInfo = signUpResponse.get("user").getAsJsonObject();
            assertEquals(signUpUser.get("email").getAsString(), userInfo.get("email").getAsString());
            assertEquals(signUpUser.get("id").getAsString(), userInfo.get("id").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    //Check for all types of output
    @Test
    public void testForAllTypesOfOutput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("email", "random@gmail.com");

            JsonObject response = io.supertokens.test.httpRequest.HttpRequest
                    .sendGETRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/user", map, 1000,
                            1000,
                            null, Utils.getCdiVersion2_4ForTests());
            assertEquals(response.get("status").getAsString(), "UNKNOWN_EMAIL_ERROR");
            assertEquals(response.entrySet().size(), 1);
        }

        {
            HashMap<String, String> map = new HashMap<>();
            map.put("userId", "randomId");

            JsonObject response = io.supertokens.test.httpRequest.HttpRequest
                    .sendGETRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/user", map, 1000,
                            1000,
                            null, Utils.getCdiVersion2_4ForTests());
            assertEquals(response.get("status").getAsString(), "UNKNOWN_USER_ID_ERROR");
            assertEquals(response.entrySet().size(), 1);
        }


        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
