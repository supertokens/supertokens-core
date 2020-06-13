/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.test;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertNotNull;

public class SessionAPITest {

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
    public void successOutputCheckWithAntiCsrf() throws InterruptedException, IOException, HttpResponseException {
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

        JsonObject response = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/session", request, 1000, 1000, null);

        assertEquals(response.get("status").getAsString(), "OK");

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 3);

        assertTrue(response.get("accessToken").getAsJsonObject().has("token"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("createdTime"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("cookiePath"));
        assertEquals(response.get("accessToken").getAsJsonObject().get("cookiePath").getAsString(),
                Config.getConfig(process.getProcess()).getAccessTokenPath());
        assertTrue(response.get("accessToken").getAsJsonObject().has("cookieSecure"));
        assertEquals(response.get("accessToken").getAsJsonObject().get("cookieSecure").getAsBoolean(),
                Config.getConfig(process.getProcess()).getCookieSecure(process.getProcess()));
        assertEquals(response.get("accessToken").getAsJsonObject().get("domain").getAsString(),
                Config.getConfig(process.getProcess()).getCookieDomain());
        assertEquals(response.get("accessToken").getAsJsonObject().entrySet().size(), 6);

        assertTrue(response.get("refreshToken").getAsJsonObject().has("token"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("createdTime"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("cookiePath"));
        assertEquals(response.get("refreshToken").getAsJsonObject().get("cookiePath").getAsString(),
                Config.getConfig(process.getProcess()).getRefreshAPIPath());
        assertTrue(response.get("refreshToken").getAsJsonObject().has("cookieSecure"));
        assertEquals(response.get("refreshToken").getAsJsonObject().get("cookieSecure").getAsBoolean(),
                Config.getConfig(process.getProcess()).getCookieSecure(process.getProcess()));
        assertEquals(response.get("refreshToken").getAsJsonObject().get("domain").getAsString(),
                Config.getConfig(process.getProcess()).getCookieDomain());
        assertEquals(response.get("refreshToken").getAsJsonObject().entrySet().size(), 6);

        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("token"));
        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("idRefreshToken").getAsJsonObject().entrySet().size(), 3);

        assertTrue(response.has("antiCsrfToken"));
        assertTrue(response.has("jwtSigningPublicKey"));
        assertTrue(response.has("jwtSigningPublicKeyExpiryTime"));
        assertEquals(response.entrySet().size(), 8);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void successOutputCheckWithNoAntiCsrfAndSameSiteDifferent()
            throws InterruptedException, IOException, HttpResponseException {
        Utils.setValueInConfig("enable_anti_csrf", "false");

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

        JsonObject response = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/session", request, 1000, 1000, null);

        assertEquals(response.get("status").getAsString(), "OK");

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 3);

        assertTrue(response.get("accessToken").getAsJsonObject().has("token"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("createdTime"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("cookiePath"));
        assertEquals(response.get("accessToken").getAsJsonObject().get("cookiePath").getAsString(),
                Config.getConfig(process.getProcess()).getAccessTokenPath());
        assertTrue(response.get("accessToken").getAsJsonObject().has("cookieSecure"));
        assertEquals(response.get("accessToken").getAsJsonObject().get("cookieSecure").getAsBoolean(),
                Config.getConfig(process.getProcess()).getCookieSecure(process.getProcess()));
        assertEquals(response.get("accessToken").getAsJsonObject().get("domain").getAsString(),
                Config.getConfig(process.getProcess()).getCookieDomain());
        assertEquals(response.get("accessToken").getAsJsonObject().entrySet().size(), 6);

        assertTrue(response.get("refreshToken").getAsJsonObject().has("token"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("createdTime"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("cookiePath"));
        assertEquals(response.get("refreshToken").getAsJsonObject().get("cookiePath").getAsString(),
                Config.getConfig(process.getProcess()).getRefreshAPIPath());
        assertTrue(response.get("refreshToken").getAsJsonObject().has("cookieSecure"));
        assertEquals(response.get("refreshToken").getAsJsonObject().get("cookieSecure").getAsBoolean(),
                Config.getConfig(process.getProcess()).getCookieSecure(process.getProcess()));
        assertEquals(response.get("refreshToken").getAsJsonObject().get("domain").getAsString(),
                Config.getConfig(process.getProcess()).getCookieDomain());
        assertEquals(response.get("refreshToken").getAsJsonObject().entrySet().size(), 6);

        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("token"));
        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("idRefreshToken").getAsJsonObject().entrySet().size(), 3);

        assertNotNull(response.get("jwtSigningPublicKey").getAsString());
        assertTrue(response.has("jwtSigningPublicKeyExpiryTime"));
        assertEquals(response.entrySet().size(), 7);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void badInputTest() throws InterruptedException, IOException, HttpResponseException {

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        try {
            JsonObject request = new JsonObject();
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/session", request, 1000, 1000, null);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'userId' is invalid in JSON input");
        }

        try {
            JsonObject request = new JsonObject();
            request.addProperty("userId", userId);
            request.add("userDataInDatabase", userDataInDatabase);
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/session", request, 1000, 1000, null);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'userDataInJWT' is invalid in JSON input");
        }

        try {
            JsonObject request = new JsonObject();
            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/session", request, 1000, 1000, null);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'userDataInDatabase' is invalid in JSON input");
        }

        try {
            JsonObject request = new JsonObject();
            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/session", request, 1000, 1000, null);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'userDataInDatabase' is invalid in JSON input");
        }

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/session", request, 1000, 1000, null);

        request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/session", request, 1000, 1000, null);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deleteSessionBySessionHandleTest() throws InterruptedException, IOException, HttpResponseException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // create new session
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);

        JsonObject response = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/session", request, 1000, 1000, null);

        String sessionHandle = response.get("session").getAsJsonObject().get("handle").getAsString();
        String userIdFromResponse = response.get("session").getAsJsonObject().get("userId").getAsString();


        // delete session using session handle
        JsonObject request2 = new JsonObject();
        request2.addProperty("sessionHandle", sessionHandle);
        HttpRequest
                .sendJsonDELETERequest(process.getProcess(), "", "http://localhost:3567/session", request2, 1000, 1000,
                        null);


        // get all sessions for user
        Map<String, String> request3 = new HashMap<>();
        request3.put("userId", userIdFromResponse);
        JsonObject multiResponse = HttpRequest
                .sendGETRequest(process.getProcess(), "", "http://localhost:3567/session/user", request3, 1000, 1000,
                        null);

        assertEquals(0, multiResponse.get("sessionHandles").getAsJsonArray().size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deleteSessionByUserIdTest() throws InterruptedException, IOException, HttpResponseException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // create new session
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);

        JsonObject response = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/session", request, 1000, 1000, null);

        String userIdFromResponse = response.get("session").getAsJsonObject().get("userId").getAsString();


        // delete session using session handle
        JsonObject request2 = new JsonObject();
        request2.addProperty("userId", userIdFromResponse);
        HttpRequest
                .sendJsonDELETERequest(process.getProcess(), "", "http://localhost:3567/session", request2, 1000, 1000,
                        null);


        // get all sessions for user
        Map<String, String> request3 = new HashMap<>();
        request3.put("userId", userIdFromResponse);
        JsonObject multiResponse = HttpRequest
                .sendGETRequest(process.getProcess(), "", "http://localhost:3567/session/user", request3, 1000, 1000,
                        null);

        assertEquals(0, multiResponse.get("sessionHandles").getAsJsonArray().size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
