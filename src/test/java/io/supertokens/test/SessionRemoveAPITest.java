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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.test.httpRequest.HttpRequest;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;


public class SessionRemoveAPITest {

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

    // *  - create session s1, s2, s3, s4. Remove s2 and s4 - make sure they are returned. Remove s1, s2, s3, s4,
    // make sure
    // *  only s1 and s3 are returned.
    @Test
    public void testRemovingMultipleSessionsGivesCorrectOutput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        //create sessions s1, s2, s3, s4
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject sessionRequest = new JsonObject();
        sessionRequest.addProperty("userId", userId);
        sessionRequest.add("userDataInJWT", userDataInJWT);
        sessionRequest.add("userDataInDatabase", userDataInDatabase);

        //create session s1
        JsonObject s1Info = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", sessionRequest, 1000,
                        1000, 2, Utils.getCdiVersion2ForTests());
        assertEquals(s1Info.get("status").getAsString(), "OK");

        //create session s2
        JsonObject s2Info = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", sessionRequest, 1000,
                        1000, 2, Utils.getCdiVersion2ForTests());
        assertEquals(s2Info.get("status").getAsString(), "OK");

        //create session s3
        JsonObject s3Info = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", sessionRequest, 1000,
                        1000, 2, Utils.getCdiVersion2ForTests());
        assertEquals(s3Info.get("status").getAsString(), "OK");

        //create session s4
        JsonObject s4Info = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", sessionRequest, 1000,
                        1000, 2, Utils.getCdiVersion2ForTests());
        assertEquals(s4Info.get("status").getAsString(), "OK");

        // remove s2 and s4 and make sure they are returned

        String sessionRemoveBodyString = "{" +
                " sessionHandles : [ " + s2Info.get("session").getAsJsonObject().get("handle").getAsString() + " ," +
                s4Info.get("session").getAsJsonObject().get("handle").getAsString() +
                " ] " +
                "}";
        JsonObject sessionRemoveBody = new JsonParser().parse(sessionRemoveBodyString).getAsJsonObject();
        JsonObject sessionRemovedResponse = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/remove",
                        sessionRemoveBody,
                        1000, 1000, null, Utils.getCdiVersion2ForTests());
        JsonArray revokedSessions = sessionRemovedResponse.getAsJsonArray("sessionHandlesRevoked");

        for (int i = 0; i < revokedSessions.size(); i++) {
            assertTrue(sessionRemoveBody.getAsJsonArray("sessionHandles").contains(revokedSessions.get(i)));
        }
        assertEquals(sessionRemoveBody.getAsJsonArray("sessionHandles").size(), revokedSessions.size());


        // revoke s1 s2 s3 and s4 and make sure only s1 and s3 are returned
        sessionRemoveBodyString = "{" +
                " sessionHandles : [ " + s1Info.get("session").getAsJsonObject().get("handle").getAsString() + " ," +
                s2Info.get("session").getAsJsonObject().get("handle").getAsString() + " ," +
                s3Info.get("session").getAsJsonObject().get("handle").getAsString() + " ," +
                s4Info.get("session").getAsJsonObject().get("handle").getAsString() +
                " ] " +
                "}";
        sessionRemoveBody = new JsonParser().parse(sessionRemoveBodyString).getAsJsonObject();

        sessionRemovedResponse = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/remove",
                        sessionRemoveBody,
                        1000, 1000, null, Utils.getCdiVersion2ForTests());
        revokedSessions = sessionRemovedResponse.getAsJsonArray("sessionHandlesRevoked");

        //check that response should only contain s1 and s3 session handles
        assertTrue(revokedSessions.contains(s1Info.get("session").getAsJsonObject().get("handle")));
        assertTrue(revokedSessions.contains(s2Info.get("session").getAsJsonObject().get("handle")));

        assertEquals(revokedSessions.size(), 2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testThatVersion1IsUnsupportedForSessionRemovePOST() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/remove", null, 1000,
                            1000, null, "1.0");
            fail();
        } catch (HttpResponseException e) {
            assertEquals(
                    "Http error. Status Code: 400. Message: /session/remove POST is only available in >= CDI 2.0. " +
                            "Please call /session DELETE instead",
                    e.getMessage());
        }

        process.kill();

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRevoking1SessionUsingSessionHandle() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject sessionRequest = new JsonObject();
        sessionRequest.addProperty("userId", userId);
        sessionRequest.add("userDataInJWT", userDataInJWT);
        sessionRequest.add("userDataInDatabase", userDataInDatabase);

        //create Session
        JsonObject sessionInfo = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", sessionRequest, 1000,
                        1000, 2, Utils.getCdiVersion2ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");


        String sessionRemoveBodyString = "{" +
                " sessionHandles : [ " + sessionInfo.get("session").getAsJsonObject().get("handle").getAsString() +
                " ] " +
                "}";
        JsonObject sessionRemoveBody = new JsonParser().parse(sessionRemoveBodyString).getAsJsonObject();

        //remove session using sessionHandle
        JsonObject sessionRemovedResponse = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/remove",
                        sessionRemoveBody,
                        1000, 1000, null, Utils.getCdiVersion2ForTests());

        assertEquals(sessionRemovedResponse.get("status").getAsString(), "OK");
        assertEquals(sessionRemovedResponse.get("sessionHandlesRevoked").getAsJsonArray().size(),
                1);
        assertEquals(sessionRemovedResponse.get("sessionHandlesRevoked").getAsJsonArray().get(0).getAsString(),
                sessionInfo.get("session").getAsJsonObject().get("handle").getAsString());

        //check that the number of sessions for user is 0
        Map<String, String> userParams = new HashMap<>();
        userParams.put("userId", userId);

        JsonObject userResponse = HttpRequest
                .sendGETRequest(process.getProcess(), "", "http://localhost:3567/session/user", userParams, 1000, 1000,
                        null, Utils.getCdiVersion2ForTests());
        assertEquals(userResponse.get("status").getAsString(), "OK");
        assertEquals(userResponse.get("sessionHandles").getAsJsonArray().size(), 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testRemovingSessionByUserId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        //create new Session
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject sessionRequest = new JsonObject();
        sessionRequest.addProperty("userId", userId);
        sessionRequest.add("userDataInJWT", userDataInJWT);
        sessionRequest.add("userDataInDatabase", userDataInDatabase);

        //create Session
        JsonObject sessionInfo = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", sessionRequest, 1000,
                        1000, 2, Utils.getCdiVersion2ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        JsonObject session2Info = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", sessionRequest, 1000,
                        1000, 2, Utils.getCdiVersion2ForTests());
        assertEquals(session2Info.get("status").getAsString(), "OK");

        //remove session using user id
        JsonObject removeSessionBody = new JsonObject();
        removeSessionBody.addProperty("userId", userId);

        JsonObject sessionRemovedResponse = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/remove",
                        removeSessionBody, 1000, 1000, null, Utils.getCdiVersion2ForTests());
        assertEquals(sessionRemovedResponse.get("status").getAsString(), "OK");

        assertEquals(sessionRemovedResponse.get("sessionHandlesRevoked").getAsJsonArray().size(),
                2);

        assertTrue(sessionRemovedResponse.getAsJsonArray("sessionHandlesRevoked")
                .contains(sessionInfo.get("session").getAsJsonObject().get("handle")));
        assertTrue(sessionRemovedResponse.getAsJsonArray("sessionHandlesRevoked")
                .contains(session2Info.get("session").getAsJsonObject().get("handle")));

        //check that the number of sessions for user is 0
        Map<String, String> userParams = new HashMap<>();
        userParams.put("userId", userId);

        JsonObject userResponse = HttpRequest
                .sendGETRequest(process.getProcess(), "", "http://localhost:3567/session/user", userParams, 1000, 1000,
                        null, Utils.getCdiVersion2ForTests());
        assertEquals(userResponse.get("status").getAsString(), "OK");
        assertEquals(userResponse.get("sessionHandles").getAsJsonArray().size(), 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
