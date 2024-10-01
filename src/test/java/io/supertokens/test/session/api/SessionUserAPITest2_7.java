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

package io.supertokens.test.session.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.*;

public class SessionUserAPITest2_7 {
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
    public void inputErrorsInSessionUserAPITest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // null is sent as the parameter
        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/session/user",
                    null, 1000, 1000, null, SemVer.v2_7.get(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'userId' is missing in GET request"));
        }

        // typo in the key of the parameter
        HashMap<String, String> map = new HashMap<>();
        map.put("urId", "");

        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/session/user",
                    map, 1000, 1000, null, SemVer.v2_7.get(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'userId' is missing in GET request"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void differentPossibleSessionUserAPIOutputTest() throws Exception {
        String[] args = {"../"};

        String createSessionJsonInput = "{" + "\"userId\": \"UserID\"," + "\"userDataInJWT\": {"
                + "\"userData1\": \"temp1\"," + "\"userData2\": \"temp2\"" + "}," + "\"userDataInDatabase\": {"
                + "\"jsonObject\": \"temp\"" + "}," + "\"customSigningKey\": \"string\"," + "\"enableAntiCsrf\": false"
                + "}";

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // when no session exists
        HashMap<String, String> map = new HashMap<>();
        map.put("userId", "");

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                "session");

        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(0, response.get("sessionHandles").getAsJsonArray().size());

        // when a session exists
        JsonObject sessionCreatedResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session",
                new JsonParser().parse(createSessionJsonInput).getAsJsonObject(), 1000, 1000, null,
                SemVer.v2_7.get(), "session");

        assertEquals("OK", sessionCreatedResponse.get("status").getAsString());

        map = new HashMap<>();
        map.put("userId", "UserID");
        response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                "session");

        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(response.entrySet().size(), 2);
        assertEquals(1, response.get("sessionHandles").getAsJsonArray().size());
        assertEquals(response.get("sessionHandles").getAsString(),
                sessionCreatedResponse.get("session").getAsJsonObject().get("handle").getAsString());

        // when multiple sessions are created

        // second session created
        JsonObject sessionCreatedResponse1 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session",
                new JsonParser().parse(createSessionJsonInput).getAsJsonObject(), 1000, 1000, null,
                SemVer.v2_7.get(), "session");

        assertEquals(sessionCreatedResponse1.get("status").getAsString(), "OK");

        // third session created
        JsonObject sessionCreatedResponse2 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session",
                new JsonParser().parse(createSessionJsonInput).getAsJsonObject(), 1000, 1000, null,
                SemVer.v2_7.get(), "session");

        assertEquals(sessionCreatedResponse2.get("status").getAsString(), "OK");

        JsonObject multiResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                "session");

        assertEquals(multiResponse.get("status").getAsString(), "OK");
        assertEquals(multiResponse.entrySet().size(), 2);
        assertEquals(multiResponse.get("sessionHandles").getAsJsonArray().size(), 3);

        JsonArray sessionArray = multiResponse.get("sessionHandles").getAsJsonArray();

        assertTrue(sessionArray.contains(sessionCreatedResponse.get("session").getAsJsonObject().get("handle"))
                && sessionArray.contains(sessionCreatedResponse1.get("session").getAsJsonObject().get("handle"))
                && sessionArray.contains(sessionCreatedResponse2.get("session").getAsJsonObject().get("handle")));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void multipleUsersMultipleSessionTest() throws Exception {
        String[] args = {"../"};

        HashMap<String, String> map = new HashMap<>();

        String userJsonInput1 = "{\n" + "\"userId\": \"UserID1\"," + "\"userDataInJWT\": {"
                + "\"userData1\": \"temp1\"," + "\"userData2\": \"temp2\"" + "}," + "\"userDataInDatabase\": {"
                + "\"jsonObject\": \"temp\"" + "}," + "\"customSigningKey\": \"value\"," + "\"enableAntiCsrf\": false"
                + "}";
        String userJsonInput2 = "{\n" + "\"userId\": \"UserID2\"," + "\"userDataInJWT\": {"
                + "\"userData1\": \"temp1\"," + "\"userData2\": \"temp2\"" + "}," + "\"userDataInDatabase\": {"
                + "\"jsonObject\": \"temp\"" + "}," + "\"customSigningKey\": \"value\"," + "\"enableAntiCsrf\": false"
                + "}";
        String userJsonInput3 = "{" + "\"userId\": \"UserID3\"," + "\"userDataInJWT\": {" + "\"userData1\": \"temp1\","
                + "\"userData2\": \"temp2\"" + "}," + "\"userDataInDatabase\": {" + "\"jsonObject\": \"temp\"" + "},"
                + "\"customSigningKey\": \"value\"," + "\"enableAntiCsrf\": false" + "}";

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // UserID1
        // session 1
        JsonObject user1Response1 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", new JsonParser().parse(userJsonInput1).getAsJsonObject(), 1000,
                1000, null, SemVer.v2_7.get(), "session");

        assertEquals("OK", user1Response1.get("status").getAsString());

        // session 2
        JsonObject user1Response2 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", new JsonParser().parse(userJsonInput1).getAsJsonObject(), 1000,
                1000, null, SemVer.v2_7.get(), "session");

        assertEquals("OK", user1Response2.get("status").getAsString());

        // session 3
        JsonObject user1Response3 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", new JsonParser().parse(userJsonInput1).getAsJsonObject(), 1000,
                1000, null, SemVer.v2_7.get(), "session");

        assertEquals("OK", user1Response3.get("status").getAsString());

        // UserID2

        // session 1

        JsonObject user2Response1 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", new JsonParser().parse(userJsonInput2).getAsJsonObject(), 1000,
                1000, null, SemVer.v2_7.get(), "session");

        assertEquals("OK", user2Response1.get("status").getAsString());

        // session 2

        JsonObject user2Response2 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", new JsonParser().parse(userJsonInput2).getAsJsonObject(), 1000,
                1000, null, SemVer.v2_7.get(), "session");

        assertEquals("OK", user2Response2.get("status").getAsString());

        // session 3

        JsonObject user2Response3 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", new JsonParser().parse(userJsonInput2).getAsJsonObject(), 1000,
                1000, null, SemVer.v2_7.get(), "session");

        assertEquals("OK", user2Response3.get("status").getAsString());

        // UserID3

        // session 1

        JsonObject user3Response1 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", new JsonParser().parse(userJsonInput3).getAsJsonObject(), 1000,
                1000, null, SemVer.v2_7.get(), "session");

        assertEquals("OK", user3Response1.get("status").getAsString());

        // session 2

        JsonObject user3Response2 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", new JsonParser().parse(userJsonInput3).getAsJsonObject(), 1000,
                1000, null, SemVer.v2_7.get(), "session");

        assertEquals("OK", user3Response2.get("status").getAsString());

        // session 3

        JsonObject user3Response3 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", new JsonParser().parse(userJsonInput3).getAsJsonObject(), 1000,
                1000, null, SemVer.v2_7.get(), "session");

        assertEquals("OK", user3Response3.get("status").getAsString());

        // user1 session handle check

        map.put("userId", "UserID1");

        JsonObject multiResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                "session");

        assertEquals("OK", multiResponse.get("status").getAsString());
        assertEquals(multiResponse.get("sessionHandles").getAsJsonArray().size(), 3);

        JsonArray sessionArray = multiResponse.get("sessionHandles").getAsJsonArray();

        assertTrue(sessionArray.contains(user1Response1.get("session").getAsJsonObject().get("handle"))
                && sessionArray.contains(user1Response2.get("session").getAsJsonObject().get("handle"))
                && sessionArray.contains(user1Response3.get("session").getAsJsonObject().get("handle")));

        // user2 session handle check
        map = new HashMap<>();
        map.put("userId", "UserID2");

        multiResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                "session");
        assertEquals("OK", multiResponse.get("status").getAsString());

        sessionArray = multiResponse.get("sessionHandles").getAsJsonArray();

        assertTrue(sessionArray.contains(user2Response1.get("session").getAsJsonObject().get("handle"))
                && sessionArray.contains(user2Response2.get("session").getAsJsonObject().get("handle"))
                && sessionArray.contains(user2Response3.get("session").getAsJsonObject().get("handle")));

        // user3 session handle check
        map = new HashMap<>();
        map.put("userId", "UserID3");

        multiResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/user", map, 1000, 1000, null, SemVer.v2_7.get(),
                "session");
        assertEquals("OK", multiResponse.get("status").getAsString());

        sessionArray = multiResponse.get("sessionHandles").getAsJsonArray();

        assertTrue(sessionArray.contains(user3Response1.get("session").getAsJsonObject().get("handle"))
                && sessionArray.contains(user3Response2.get("session").getAsJsonObject().get("handle"))
                && sessionArray.contains(user3Response3.get("session").getAsJsonObject().get("handle")));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
