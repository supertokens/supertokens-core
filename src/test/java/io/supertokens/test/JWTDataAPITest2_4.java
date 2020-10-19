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
import io.supertokens.test.httpRequest.HttpRequest;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.*;


public class JWTDataAPITest2_4 {

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


    // *   - create session with some JWT payload -> change JWT payload using API -> check this is reflected in db
    @Test
    public void testCreateSessionWithPayloadChangePayloadWithApiAndCheckChangeReflectedInDB() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        //createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);

        JsonObject sessionInfo = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2_4ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        String sessionHandle = sessionInfo.get("session").getAsJsonObject().get("handle").getAsString();

        // change JWT payload using API
        JsonObject newUserDataInJwt = new JsonObject();
        newUserDataInJwt.addProperty("key", "value2");
        request = new JsonObject();
        request.addProperty("sessionHandle", sessionHandle);
        request.add("userDataInJWT", newUserDataInJwt);
        JsonObject putJwtData = HttpRequest
                .sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/jwt/data", request, 1000, 1000,
                        null, Utils.getCdiVersion2_4ForTests());
        assertEquals(putJwtData.get("status").getAsString(), "OK");

        HashMap<String, String> params = new HashMap<>();
        params.put("sessionHandle", sessionHandle);

        //check this is reflected in db
        JsonObject getJwtData = HttpRequest
                .sendGETRequest(process.getProcess(), "", "http://localhost:3567/jwt/data", params, 1000, 1000, null,
                        Utils.getCdiVersion2_4ForTests());
        assertEquals(getJwtData.get("status").getAsString(), "OK");
        assertEquals(getJwtData.get("userDataInJWT"), newUserDataInJwt);
        assertNotEquals(getJwtData.get("userDataInJWT"), userDataInJWT);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    //*  - create session with some JWT payload -> change JWT payload to be empty using session -> check this is
    // reflected in db
    @Test
    public void testCreateSessionWithJwtPayloadChangePayloadToEmptyUsingSessionCheckIfReflectedInDB() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        //createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);

        JsonObject sessionInfo = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2_4ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        String sessionHandle = sessionInfo.get("session").getAsJsonObject().get("handle").getAsString();
        JsonObject emptyJwtUserData = new JsonObject();
        JsonObject jwtPutRequest = new JsonObject();
        jwtPutRequest.addProperty("sessionHandle", sessionHandle);
        jwtPutRequest.add("userDataInJWT", emptyJwtUserData);

        JsonObject putRequest = HttpRequest
                .sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/jwt/data", jwtPutRequest, 1000,
                        1000, null, Utils.getCdiVersion2_4ForTests());
        assertEquals(putRequest.get("status").getAsString(), "OK");

        HashMap<String, String> params = new HashMap<>();
        params.put("sessionHandle", sessionHandle);

        //check that getData returns empty userDataInJwt
        JsonObject getJwtData = HttpRequest
                .sendGETRequest(process.getProcess(), "", "http://localhost:3567/jwt/data", params, 1000, 1000, null,
                        Utils.getCdiVersion2_4ForTests());
        assertEquals(getJwtData.get("status").getAsString(), "OK");
        assertEquals(getJwtData.get("userDataInJWT").getAsJsonObject(), emptyJwtUserData);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // *  - create session -> let it expire, remove from db -> call update API -> make sure you get unauthorised error
    @Test
    public void testCreateSessionLetItExpireCallPutAPIAndCheckUnauthorised() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("access_token_validity", "1");// 1 second validity
        Utils.setValueInConfig("refresh_token_validity", "" + 1.0 / 60);// 1 second validity (value in mins)


        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        //createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);

        JsonObject sessionInfo = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2_4ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        String sessionHandle = sessionInfo.get("session").getAsJsonObject().get("handle").getAsString();

        //let it expire, remove from db

        Thread.sleep(2000);
        JsonObject removeSessionBody = new JsonObject();
        removeSessionBody.addProperty("userId", userId);

        JsonObject sessionRemovedResponse = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/remove",
                        removeSessionBody, 1000, 1000, null, Utils.getCdiVersion2_4ForTests());
        assertEquals(sessionRemovedResponse.get("status").getAsString(), "OK");


        // change JWT payload using API
        JsonObject newUserDataInJwt = new JsonObject();
        newUserDataInJwt.addProperty("key", "value2");
        request = new JsonObject();
        request.addProperty("sessionHandle", sessionHandle);
        request.add("userDataInJWT", newUserDataInJwt);

        JsonObject response = HttpRequest
                .sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/jwt/data", request, 1000, 1000,
                        null, Utils.getCdiVersion2_4ForTests());
        assertEquals(response.get("status").getAsString(), "UNAUTHORISED");
        assertEquals(response.get("message").getAsString(), "Session does not exist.");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // *  - create session -> let it expire, remove from db -> call get API -> make sure you get unauthorised error
    @Test
    public void testCreateSessionLetItExpreRemoveFromDBCallGetAPIAndCheckUnauthorised() throws Exception {

        String[] args = {"../"};

        Utils.setValueInConfig("access_token_validity", "1");// 1 second validity
        Utils.setValueInConfig("refresh_token_validity", "" + 1.0 / 60);// 1 second validity (value in mins)


        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        //createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);

        JsonObject sessionInfo = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2_4ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        String sessionHandle = sessionInfo.get("session").getAsJsonObject().get("handle").getAsString();

        //let it expire, remove from db

        Thread.sleep(2000);
        JsonObject removeSessionBody = new JsonObject();
        removeSessionBody.addProperty("userId", userId);

        JsonObject sessionRemovedResponse = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/remove",
                        removeSessionBody, 1000, 1000, null, Utils.getCdiVersion2_4ForTests());
        assertEquals(sessionRemovedResponse.get("status").getAsString(), "OK");

        //call get api

        HashMap<String, String> params = new HashMap<>();
        params.put("sessionHandle", sessionHandle);

        JsonObject response = HttpRequest
                .sendGETRequest(process.getProcess(), "", "http://localhost:3567/jwt/data", params, 1000, 1000, null,
                        Utils.getCdiVersion2_4ForTests());

        // make sure you get unauthorised error
        assertEquals(response.get("status").getAsString(), "UNAUTHORISED");
        assertEquals(response.get("message").getAsString(), "Session does not exist.");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // *  - input / output check for both APIs
    @Test
    public void testJWTDataAPI() throws Exception {

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        //createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);

        JsonObject sessionInfo = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2_4ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        String sessionHandle = sessionInfo.get("session").getAsJsonObject().get("handle").getAsString();

        // check values for put api
        JsonObject newUserDataInJwt = new JsonObject();
        newUserDataInJwt.addProperty("key", "value2");
        request = new JsonObject();
        request.addProperty("sessionHandle", sessionHandle);
        request.add("userDataInJWT", newUserDataInJwt);
        JsonObject putJwtData = HttpRequest
                .sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/jwt/data", request, 1000, 1000,
                        null, Utils.getCdiVersion2_4ForTests());

        assertEquals(putJwtData.get("status").getAsString(), "OK");
        assertEquals(putJwtData.entrySet().size(), 1);

        //check values for get api
        HashMap<String, String> params = new HashMap<>();
        params.put("sessionHandle", sessionHandle);

        //check this is reflected in db
        JsonObject getJwtData = HttpRequest
                .sendGETRequest(process.getProcess(), "", "http://localhost:3567/jwt/data", params, 1000, 1000, null,
                        Utils.getCdiVersion2_4ForTests());
        assertEquals(getJwtData.get("status").getAsString(), "OK");
        assertEquals(getJwtData.get("userDataInJWT"), newUserDataInJwt);
        assertEquals(getJwtData.entrySet().size(), 2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testBapInputJWTDataAPI() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        //using invalid json in put
        JsonObject invalidJsonObject = new JsonObject();

        try {
            HttpRequest
                    .sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/jwt/data", invalidJsonObject,
                            1000, 1000, null, Utils.getCdiVersion2_4ForTests());
            fail();
        } catch (HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'sessionHandle' is invalid" +
                            " in JSON input");
        }

        try {
            HttpRequest
                    .sendGETRequest(process.getProcess(), "", "http://localhost:3567/jwt/data", null,
                            1000, 1000, null, Utils.getCdiVersion2_4ForTests());
            fail();
        } catch (HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'sessionHandle' is missing in GET request");
        }

        //invalid method
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);

        JsonObject sessionInfo = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2_4ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        String sessionHandle = sessionInfo.get("session").getAsJsonObject().get("handle").getAsString();

        JsonObject invalidUserData = new JsonObject();
        invalidUserData.addProperty("sessionHandle", sessionHandle);
        try {
            HttpRequest
                    .sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/jwt/data", invalidUserData,
                            1000, 1000, null, Utils.getCdiVersion2_4ForTests());

            fail();

        } catch (HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'userDataInJWT' is invalid in JSON input");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

    }
}
