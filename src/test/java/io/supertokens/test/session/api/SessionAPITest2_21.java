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

import com.google.gson.*;
import io.supertokens.ProcessState;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.jwt.JWT;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertNotNull;

public class SessionAPITest2_21 {

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
    public void successOutputCheck() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("useStaticKey", false);
        request.addProperty("enableAntiCsrf", false);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_21.get(),
                "session");
        checkSessionResponse(response, process, userId, userDataInJWT, false);
        assertFalse(response.has("antiCsrfToken"));

        String iat = "" + JWT.getPayloadWithoutVerifying(
                response.get("accessToken").getAsJsonObject().get("token").getAsString()).payload.get("iat").getAsInt();
        assertEquals(10, iat.length());
        //noinspection ResultOfMethodCallIgnored
        Long.parseLong(iat); // We are checking that this doesn't throw, it would if it was in exponential form

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void badInputTest() throws Exception {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        try {
            JsonObject userDataInJWT = new JsonObject();
            userDataInJWT.addProperty("sub", "value");

            JsonObject request = new JsonObject();
            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);
            request.addProperty("enableAntiCsrf", false);
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/session",
                    request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: The user payload contains protected field");
        }

        try {
            JsonObject userDataInJWT = new JsonObject();
            userDataInJWT.addProperty("exp", "value");

            JsonObject request = new JsonObject();
            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);
            request.addProperty("enableAntiCsrf", false);
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/session",
                    request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: The user payload contains protected field");
        }

        try {
            JsonObject userDataInJWT = new JsonObject();
            userDataInJWT.addProperty("sessionHandle", "value");

            JsonObject request = new JsonObject();
            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);
            request.addProperty("enableAntiCsrf", false);
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/session",
                    request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: The user payload contains protected field");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void successOutputCheckWithStatic() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("useDynamicSigningKey", false);
        request.addProperty("enableAntiCsrf", false);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_21.get(),
                "session");
        checkSessionResponse(response, process, userId, userDataInJWT, true);
        assertFalse(response.has("antiCsrfToken"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    public static void checkSessionResponse(JsonObject response, TestingProcessManager.TestingProcess process,
                                            String userId, JsonObject userDataInJWT, boolean isStatic)
            throws JWT.JWTException {
        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 3);

        assertTrue(response.get("accessToken").getAsJsonObject().has("token"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("accessToken").getAsJsonObject().entrySet().size(), 3);

        assertTrue(response.get("refreshToken").getAsJsonObject().has("token"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("refreshToken").getAsJsonObject().entrySet().size(), 3);

        JWT.JWTPreParseInfo preParseInfo = JWT.preParseJWTInfo(
                response.get("accessToken").getAsJsonObject().get("token").getAsString());
        assertEquals(preParseInfo.version, AccessToken.VERSION.V3);
        assertNotNull(preParseInfo.kid);

        IllegalArgumentException caught = null;
        try {
            // We are just checking the format basically;
            UUID.fromString(preParseInfo.kid.substring(2));
        } catch (IllegalArgumentException ex) {
            caught = ex;
        }

        if (isStatic) {
            assertNull(caught);
            assertEquals("s-", preParseInfo.kid.substring(0, 2));
        } else {
            assertNotNull(caught);
            assertEquals("d-", preParseInfo.kid.substring(0, 2));
        }

        Base64.getUrlDecoder().decode(preParseInfo.header);
        Base64.getUrlDecoder().decode(preParseInfo.signature);


        JsonObject payload = new JsonParser().parse(
                        new String(Base64.getUrlDecoder().decode(preParseInfo.payload), StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertFalse(payload.has("userData"));

        for (Map.Entry<String, JsonElement> entry : userDataInJWT.entrySet()) {
            assertTrue(payload.has(entry.getKey()));
            assertEquals(payload.get(entry.getKey()).toString(), userDataInJWT.get(entry.getKey()).toString());
        }
    }
}
