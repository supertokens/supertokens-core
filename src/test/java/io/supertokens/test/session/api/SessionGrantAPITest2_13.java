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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.*;

public class SessionGrantAPITest2_13 {
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
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // Put request input errors

        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/data", null, 1000, 1000, null,
                    Utils.getCdiVersion2_13ForTests(), "session");
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 400
                    && e.getMessage().equals("Http error. Status Code: 400. Message: Invalid Json Input"));
        }

        // typo in sessionHandle
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("sessiondle", "temp");
        jsonBody.addProperty("grants", jsonBody.toString());

        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/data", jsonBody, 1000, 1000, null,
                    Utils.getCdiVersion2_13ForTests(), "session");
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.getMessage().equals(
                    "Http error. Status Code: 400. Message: Field name 'sessionHandle' is invalid in JSON " + "input")
                    && e.statusCode == 400);
        }

        // typo in userDataInDatabase
        jsonBody = new JsonObject();
        jsonBody.addProperty("sessionHandle", "");
        jsonBody.addProperty("grantsss", jsonBody.toString());

        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/data", jsonBody, 1000, 1000, null,
                    Utils.getCdiVersion2_13ForTests(), "session");
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
    public void putRequestSuccessOutputCheckTestWithV2Token() throws Exception {
        String[] args = { "../" };

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject grantPayload = Utils.getExampleGrantPayload();

        JsonObject sessionBody = new JsonObject();
        sessionBody.addProperty("userId", userId);
        sessionBody.add("userDataInJWT", userDataInJWT);
        sessionBody.add("userDataInDatabase", userDataInDatabase);
        sessionBody.addProperty("enableAntiCsrf", true);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject session = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionBody, 1000, 1000, null,
                Utils.getCdiVersion2_12ForTests(), "session");

        assertEquals(session.get("status").getAsString(), "OK");
        String sessionHandle = session.get("session").getAsJsonObject().get("handle").getAsString();

        JsonObject putBody = new JsonObject();
        putBody.addProperty("sessionHandle", sessionHandle);
        putBody.add("grants", grantPayload);

        JsonObject putResponse = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/grant", putBody, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");
        assertEquals(putResponse.get("status").getAsString(), "OK");

        HashMap<String, String> map = new HashMap<>();
        map.put("sessionHandle", sessionHandle);

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", map, 1000, 1000, null, Utils.getCdiVersion2_13ForTests(),
                "session");

        assertEquals(response.get("status").getAsString(), "OK");
        assertTrue(response.has("grants"));
        assertEquals(response.getAsJsonObject("grants"), grantPayload);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void putRequestSuccessOutputCheckTest() throws Exception {
        String[] args = { "../" };

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject initialGrantPayload = new JsonObject();
        JsonObject grantPayload = Utils.getExampleGrantPayload();

        JsonObject sessionBody = new JsonObject();
        sessionBody.addProperty("userId", userId);
        sessionBody.add("userDataInJWT", userDataInJWT);
        sessionBody.add("userDataInDatabase", userDataInDatabase);
        sessionBody.add("grants", initialGrantPayload);
        sessionBody.addProperty("enableAntiCsrf", true);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject session = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionBody, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");

        assertEquals(session.get("status").getAsString(), "OK");
        String sessionHandle = session.get("session").getAsJsonObject().get("handle").getAsString();

        JsonObject putBody = new JsonObject();
        putBody.addProperty("sessionHandle", sessionHandle);
        putBody.add("grants", grantPayload);

        JsonObject putResponse = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/grant", putBody, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");
        assertEquals(putResponse.get("status").getAsString(), "OK");

        HashMap<String, String> map = new HashMap<>();
        map.put("sessionHandle", sessionHandle);

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", map, 1000, 1000, null, Utils.getCdiVersion2_13ForTests(),
                "session");

        assertEquals(response.get("status").getAsString(), "OK");
        assertTrue(response.has("grants"));
        assertEquals(response.getAsJsonObject("grants"), grantPayload);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void putRequestNotOutputCheckTest() throws Exception {
        String[] args = { "../" };

        JsonObject grantPayload = Utils.getExampleGrantPayload();

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject putBody = new JsonObject();
        putBody.addProperty("sessionHandle", "abcd123");
        putBody.add("grants", grantPayload);

        JsonObject putResponse = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/grant", putBody, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");
        assertEquals(putResponse.entrySet().size(), 2);
        assertEquals(putResponse.get("status").getAsString(), "UNAUTHORISED");
        assertEquals(putResponse.get("message").getAsString(), "Session does not exist.");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }
}
