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
import com.google.gson.JsonPrimitive;

import io.supertokens.ProcessState;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;

public class SessionAPITest2_13 {

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
    public void successOutputCheckWithGrantPayload() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("cookie_domain", "localhost");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject grantPayload = Utils.getExampleGrantPayload();

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.add("grants", grantPayload);
        request.addProperty("enableAntiCsrf", true);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, Utils.getCdiVersion2_13ForTests(),
                "session");

        checkSessionResponse(response, process, userId, userDataInJWT, grantPayload);
        assertTrue(response.has("antiCsrfToken"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void successOutputCheckWithEmptyGrantPayload() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("cookie_domain", "localhost");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject grantPayload = new JsonObject();

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.add("grants", grantPayload);
        request.addProperty("enableAntiCsrf", true);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, Utils.getCdiVersion2_13ForTests(),
                "session");

        checkSessionResponse(response, process, userId, userDataInJWT, grantPayload);
        assertTrue(response.has("antiCsrfToken"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void successGetOutputCheckWithOldToken() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("cookie_domain", "localhost");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject createReq = new JsonObject();
        createReq.addProperty("userId", userId);
        createReq.add("userDataInJWT", userDataInJWT);
        createReq.add("userDataInDatabase", userDataInDatabase);
        createReq.addProperty("enableAntiCsrf", true);

        JsonObject session = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", createReq, 1000, 1000, null, Utils.getCdiVersion2_12ForTests(),
                "session");
        assertEquals(session.get("status").getAsString(), "OK");

        // Get session info
        HashMap<String, String> map = new HashMap<>();
        map.put("sessionHandle", session.get("session").getAsJsonObject().get("handle").getAsString());

        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", map, 1000, 1000, null, Utils.getCdiVersion2_13ForTests(),
                "session");

        // Validate response
        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(response.entrySet().size(), 8);

        assertEquals(response.get("userDataInDatabase").getAsJsonObject(), userDataInDatabase);

        assertEquals(response.get("userDataInJWT").getAsJsonObject(), userDataInJWT.getAsJsonObject());

        assertEquals(response.get("grants").getAsJsonObject(), new JsonObject());

        assertEquals(response.get("userId").getAsString(), userId);

        JsonPrimitive expiry = response.get("expiry").getAsJsonPrimitive();
        assertNotNull(expiry);
        assertTrue(expiry.isNumber());

        JsonPrimitive timeCreated = response.get("timeCreated").getAsJsonPrimitive();
        assertNotNull(timeCreated);
        assertTrue(timeCreated.isNumber());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void badInputTest() throws Exception {

        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        try {
            JsonObject request = new JsonObject();
            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);
            request.addProperty("enableAntiCsrf", false);
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/session",
                    request, 1000, 1000, null, Utils.getCdiVersion2_13ForTests(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'grants' is invalid in JSON input");
        }

        try {
            JsonObject request = new JsonObject();
            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);
            request.addProperty("enableAntiCsrf", false);
            request.addProperty("grants", "test");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/session",
                    request, 1000, 1000, null, Utils.getCdiVersion2_13ForTests(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'grants' is invalid in JSON input");
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    public static void checkSessionResponse(JsonObject response, TestingProcessManager.TestingProcess process,
            String userId, JsonObject userDataInJWT, JsonObject grantPayload) {
        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());

        if (grantPayload != null) {
            assertEquals(response.get("session").getAsJsonObject().get("grants").getAsJsonObject().toString(),
                    grantPayload.toString());
        } else {
            assertEquals(response.get("session").getAsJsonObject().get("grants").getAsJsonObject().toString(),
                    new JsonObject().toString());
        }
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 4);

        assertTrue(response.get("accessToken").getAsJsonObject().has("token"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("accessToken").getAsJsonObject().entrySet().size(), 3);

        String token = response.get("accessToken").getAsJsonObject().get("token").getAsString();
        String[] splittedToken = token.split("\\.");
        JsonObject payload = (JsonObject) new JsonParser()
                .parse(io.supertokens.utils.Utils.convertFromBase64(splittedToken[1]));
        assertTrue(payload.has("userData"));
        assertTrue(payload.has("grants"));

        assertTrue(response.get("refreshToken").getAsJsonObject().has("token"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("refreshToken").getAsJsonObject().entrySet().size(), 3);

        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("token"));
        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("idRefreshToken").getAsJsonObject().entrySet().size(), 3);

        assertTrue(response.has("jwtSigningPublicKey"));
        assertTrue(response.has("jwtSigningPublicKeyExpiryTime"));
        assertTrue(response.has("jwtSigningPublicKeyList"));
        JsonArray respPubKeyList = response.get("jwtSigningPublicKeyList").getAsJsonArray();
        for (int i = 0; i < respPubKeyList.size(); ++i) {
            assertTrue(respPubKeyList.get(i).getAsJsonObject().has("publicKey"));
            assertTrue(respPubKeyList.get(i).getAsJsonObject().has("expiryTime"));
            assertTrue(respPubKeyList.get(i).getAsJsonObject().has("createdAt"));
            assertEquals(respPubKeyList.get(i).getAsJsonObject().entrySet().size(), 3);
        }
    }
}
