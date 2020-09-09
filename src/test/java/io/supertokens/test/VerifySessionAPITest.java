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
import io.supertokens.config.Config;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertNotNull;

public class VerifySessionAPITest {
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
    public void successOutputCheckNoNewAccessToken() throws InterruptedException, IOException, HttpResponseException {
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

        JsonObject sessionInfo = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/session", sessionRequest, 1000, 1000, null);

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        JsonObject response = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/verify", request, 1000,
                        1000,
                        null);

        assertEquals(response.get("status").getAsString(), "OK");

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 3);

        assertNotNull(response.get("jwtSigningPublicKey").getAsString());
        assertTrue(response.has("jwtSigningPublicKeyExpiryTime"));
        assertEquals(response.entrySet().size(), 4);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void successOutputCheckNewAccessToken() throws InterruptedException, IOException, HttpResponseException {
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
        JsonObject sessionInfo = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/session", sessionRequest, 1000, 1000, null);

        JsonObject refreshRequest = new JsonObject();
        refreshRequest.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        sessionInfo = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/session/refresh", refreshRequest, 1000, 1000, null);

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        JsonObject response = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/verify", request, 1000,
                        1000,
                        null);

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
                Config.getConfig(process.getProcess()).getCookieDomain(Utils.getCdiVersion1ForTests()));
        assertEquals(response.get("accessToken").getAsJsonObject().entrySet().size(), 6);

        assertNotNull(response.get("jwtSigningPublicKey").getAsString());
        assertTrue(response.has("jwtSigningPublicKeyExpiryTime"));
        assertEquals(response.entrySet().size(), 5);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void tryRefreshTokenOutputCheck() throws InterruptedException, IOException, HttpResponseException {

        Utils.setValueInConfig("access_token_blacklisting", "true");

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", "randomToken");
        request.addProperty("antiCsrfToken", "random");
        request.addProperty("doAntiCsrfCheck", true);
        JsonObject response = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/verify", request, 1000,
                        1000,
                        null);

        assertEquals(response.get("status").getAsString(), "TRY_REFRESH_TOKEN");
        assertEquals(response.get("message").getAsString(), "io.supertokens.session.jwt.JWT$JWTException: Invalid JWT");

        assertEquals(response.entrySet().size(), 2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void badInputTest() throws InterruptedException, IOException, HttpResponseException {
        String[] args = {"../"};
        Utils.setValueInConfig("enable_anti_csrf", "true");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");


        try {
            JsonObject sessionRequest = new JsonObject();
            sessionRequest.addProperty("userId", userId);
            sessionRequest.add("userDataInJWT", userDataInJWT);
            sessionRequest.add("userDataInDatabase", userDataInDatabase);
            JsonObject sessionInfo = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/session", sessionRequest, 1000, 1000, null);

            JsonObject request = new JsonObject();
            request.addProperty("antiCsrfToken", sessionInfo.get("antiCsrfToken").getAsString());
            request.addProperty("doAntiCsrfCheck", true);
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/verify", request,
                    1000,
                    1000,
                    null);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'accessToken' is invalid in JSON input");
        }

        {
            JsonObject sessionRequest = new JsonObject();
            sessionRequest.addProperty("userId", userId);
            sessionRequest.add("userDataInJWT", userDataInJWT);
            sessionRequest.add("userDataInDatabase", userDataInDatabase);
            JsonObject sessionInfo = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/session", sessionRequest, 1000, 1000, null);

            JsonObject request = new JsonObject();
            request.addProperty("accessToken",
                    sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
            request.addProperty("doAntiCsrfCheck", true);
            JsonObject response = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/verify", request,
                            1000,
                            1000,
                            null);
            assertEquals(response.get("status").getAsString(), "TRY_REFRESH_TOKEN");
            assertEquals(response.get("message").getAsString(), "anti-csrf check failed");

            request = new JsonObject();
            request.addProperty("accessToken",
                    sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
            request.addProperty("doAntiCsrfCheck", false);
            response = HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/verify", request,
                            1000,
                            1000,
                            null);
            assertEquals(response.get("status").getAsString(), "OK");
        }

        try {
            JsonObject sessionRequest = new JsonObject();
            sessionRequest.addProperty("userId", userId);
            sessionRequest.add("userDataInJWT", userDataInJWT);
            sessionRequest.add("userDataInDatabase", userDataInDatabase);
            JsonObject sessionInfo = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/session", sessionRequest, 1000, 1000, null);

            JsonObject request = new JsonObject();
            request.addProperty("accessToken",
                    sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
            request.addProperty("antiCsrfToken", sessionInfo.get("antiCsrfToken").getAsString());
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/verify", request,
                    1000,
                    1000,
                    null);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'doAntiCsrfCheck' is invalid in JSON input");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
