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
import com.google.gson.JsonPrimitive;
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

public class SessionGetAPIJWTTest2_8 {
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
    public void testAPIFailuresWithBadInputParameters() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // Get Request input errors
        // null is sent in parameters
        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/session", null,
                    1000, 1000, null, SemVer.v2_8.get(), "session");
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
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/recipe/session", map,
                    1000, 1000, null, SemVer.v2_8.get(), "session");
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 400 && e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'sessionHandle' is missing in GET "
                            + "request"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetRequestWithNoSessionReturnsUnauthorised() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        HashMap<String, String> map = new HashMap<>();
        map.put("sessionHandle", "");

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", map, 1000, 1000, null, SemVer.v2_8.get(),
                "session");
        assertEquals(response.entrySet().size(), 2);
        assertEquals(response.get("status").getAsString(), "UNAUTHORISED");

        assertEquals(response.get("message").getAsString(), "Session does not exist.");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatGetWithValidParametersWorks() throws Exception {
        String[] args = {"../"};

        String sessionJsonInput = "{\n" + "\t\"userId\": \"UserID\",\n" + "\t\"userDataInJWT\": {\n"
                + "\t\t\"userData1\": \"temp1\",\n" + "\t\t\"userData2\": \"temp2\"\n" + "\t},\n"
                + "\t\"userDataInDatabase\": {\n" + "\t\t\"userData\": \"value\"\n" + "\t},\n"
                + "\t\"customSigningKey\": \"string\",\n" + "\t\"enableAntiCsrf\": false\n" + "}";

        JsonObject sessionBody = new JsonParser().parse(sessionJsonInput).getAsJsonObject();

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // Create session
        JsonObject session = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionBody, 1000, 1000, null, SemVer.v2_8.get(),
                "session");

        assertEquals(session.get("status").getAsString(), "OK");

        // Get session info
        HashMap<String, String> map = new HashMap<>();
        map.put("sessionHandle", session.get("session").getAsJsonObject().get("handle").getAsString());

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", map, 1000, 1000, null, SemVer.v2_8.get(),
                "session");

        // Validate response
        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(response.entrySet().size(), 7);

        assertEquals(response.get("userDataInDatabase").getAsJsonObject(),
                sessionBody.get("userDataInDatabase").getAsJsonObject());

        assertEquals(response.get("userDataInJWT").getAsJsonObject(),
                sessionBody.get("userDataInJWT").getAsJsonObject());

        assertEquals(response.get("userId").getAsString(), sessionBody.get("userId").getAsString());

        JsonPrimitive expiry = response.get("expiry").getAsJsonPrimitive();
        assertNotNull(expiry);
        assertTrue(expiry.isNumber());

        JsonPrimitive timeCreated = response.get("timeCreated").getAsJsonPrimitive();
        assertNotNull(timeCreated);
        assertTrue(timeCreated.isNumber());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
