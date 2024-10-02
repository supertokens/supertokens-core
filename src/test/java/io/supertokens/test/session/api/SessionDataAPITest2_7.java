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

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.*;

public class SessionDataAPITest2_7 {
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

        // Get Request input errors
        // null is sent in parameters
        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/session/data",
                    null, 1000, 1000, null, SemVer.v2_7.get(), "session");
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'sessionHandle' is missing in GET "
                            + "request"));
        }

        // typo in parameter
        HashMap<String, String> map = new HashMap<>();
        map.put("sessiondle", "");

        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/session/data",
                    map, 1000, 1000, null, SemVer.v2_7.get(), "session");
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'sessionHandle' is missing in GET "
                            + "request"));
        }

        // Put request input errors

        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/data", null, 1000, 1000, null,
                    SemVer.v2_7.get(), "session");
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 400
                    && e.getMessage().equals("Http error. Status Code: 400. Message: Invalid Json Input"));
        }

        // typo in sessionHandle
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("sessiondle", "temp");
        jsonBody.addProperty("userDataInDatabase", "temp");

        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/data", jsonBody, 1000, 1000, null,
                    SemVer.v2_7.get(), "session");
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.getMessage().equals(
                    "Http error. Status Code: 400. Message: Field name 'sessionHandle' is invalid in JSON " + "input")
                    && e.statusCode == 400);
        }

        // typo in userDataInDatabase
        jsonBody = new JsonObject();
        jsonBody.addProperty("sessionHandle", "");
        jsonBody.addProperty("userDaInDatabase", "");

        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/data", jsonBody, 1000, 1000, null,
                    SemVer.v2_7.get(), "session");
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'userDataInDatabase' is invalid in "
                            + "JSON input"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void getRequestSuccessOutputCheckTest() throws Exception {
        String[] args = {"../"};

        String sessionJsonInput = "{\n" + "\t\"userId\": \"UserID\",\n" + "\t\"userDataInJWT\": {\n"
                + "\t\t\"userData1\": \"temp1\",\n" + "\t\t\"userData2\": \"temp2\"\n" + "\t},\n"
                + "\t\"userDataInDatabase\": {\n" + "\t\t\"userData\": \"value\",\n" + "\t\t\"nullProp\": null\n\t},\n"
                + "\t\"customSigningKey\": \"string\",\n" + "\t\"enableAntiCsrf\": false\n" + "}";

        JsonObject sessionBody = new JsonParser().parse(sessionJsonInput).getAsJsonObject();

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // Get request when no session exists
        HashMap<String, String> map = new HashMap<>();
        map.put("sessionHandle", "");

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/data", map, 1000, 1000, null, SemVer.v2_7.get(),
                "session");
        assertEquals(response.entrySet().size(), 2);
        assertEquals(response.get("status").getAsString(), "UNAUTHORISED");

        assertEquals(response.get("message").getAsString(), "Session does not exist.");

        // Get request when session exists
        JsonObject session = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionBody, 1000, 1000, null, SemVer.v2_7.get(),
                "session");

        assertEquals(session.get("status").getAsString(), "OK");

        map = new HashMap<>();
        map.put("sessionHandle", session.get("session").getAsJsonObject().get("handle").getAsString());

        response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/data", map, 1000, 1000, null, SemVer.v2_7.get(),
                "session");

        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(response.entrySet().size(), 2);

        assertEquals(response.get("userDataInDatabase").getAsJsonObject(),
                sessionBody.get("userDataInDatabase").getAsJsonObject());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void putRequestSuccessOutputCheckTest() throws Exception {
        String[] args = {"../"};

        String sessionJsonInput = "{\n" + "\t\"userId\": \"UserID\",\n" + "\t\"userDataInJWT\": {\n"
                + "\t\t\"userData1\": \"temp1\",\n" + "\t\t\"userData2\": \"temp2\"\n" + "\t},\n"
                + "\t\"userDataInDatabase\": {\n" + "\t\t\"userData\": \"value\"\n" + "\t},\n"
                + "\t\"customSigningKey\": \"string\",\n" + "\t\"enableAntiCsrf\": false\n" + "}";

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // updating session handle which does not exist

        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("userData1", "value1");
        userDataInDatabase.add("nullProp", JsonNull.INSTANCE);

        JsonObject putRequestBody = new JsonObject();
        putRequestBody.addProperty("sessionHandle", "123abc");
        putRequestBody.add("userDataInDatabase", userDataInDatabase);

        JsonObject sessionDataResponse = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/data", putRequestBody, 1000, 1000, null,
                SemVer.v2_7.get(), "session");

        assertEquals(sessionDataResponse.entrySet().size(), 2);
        assertEquals(sessionDataResponse.get("status").getAsString(), "UNAUTHORISED");
        assertEquals(sessionDataResponse.get("message").getAsString(), "Session does not exist.");

        // updating session handle which exists

        JsonObject session = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", new JsonParser().parse(sessionJsonInput), 1000, 1000, null,
                SemVer.v2_7.get(), "session");

        assertEquals(session.get("status").getAsString(), "OK");

        putRequestBody = new JsonObject();
        putRequestBody.addProperty("sessionHandle",
                session.get("session").getAsJsonObject().get("handle").getAsString());
        putRequestBody.add("userDataInDatabase", userDataInDatabase);

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/data", putRequestBody, 1000, 1000, null,
                SemVer.v2_7.get(), "session");

        assertEquals(response.entrySet().size(), 1);
        assertEquals(response.get("status").getAsString(), "OK");

        HashMap<String, String> map = new HashMap<>();
        map.put("sessionHandle", session.get("session").getAsJsonObject().get("handle").getAsString());

        response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/data", map, 1000, 1000, null, SemVer.v2_7.get(),
                "session");

        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(response.entrySet().size(), 2);

        assertEquals(response.get("userDataInDatabase").getAsJsonObject(), userDataInDatabase.getAsJsonObject());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
