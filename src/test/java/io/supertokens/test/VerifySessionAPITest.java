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
import io.supertokens.webserver.WebserverAPITest;
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
        String[] args = {"../", "DEV"};
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
        request.addProperty("antiCsrfToken", sessionInfo.get("antiCsrfToken").getAsString());
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
        String[] args = {"../", "DEV"};
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
        request.addProperty("antiCsrfToken", sessionInfo.get("antiCsrfToken").getAsString());
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
                Config.getConfig(process.getProcess()).getCookieDomain());
        assertEquals(response.get("accessToken").getAsJsonObject().get("sameSite").getAsString(),
                Config.getConfig(process.getProcess()).getCookieSameSite());
        assertEquals(response.get("accessToken").getAsJsonObject().entrySet().size(), 7);

        assertNotNull(response.get("jwtSigningPublicKey").getAsString());
        assertTrue(response.has("jwtSigningPublicKeyExpiryTime"));
        assertEquals(response.entrySet().size(), 5);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void tryRefreshTokenOutputCheck() throws InterruptedException, IOException, HttpResponseException {

        Utils.setValueInConfig("access_token_blacklisting", "true");

        String[] args = {"../", "DEV"};
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
        String[] args = {"../", "DEV"};
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

    @Test
    public void devLicenseSessionExpiredTest() throws InterruptedException, IOException, HttpResponseException {
        String[] args = {"../", "DEV"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        WebserverAPITest.getInstance(process.getProcess()).setRandomnessThreshold(0);
        WebserverAPITest.getInstance(process.getProcess()).setTimeAfterWhichToThrowUnauthorised(0);
        process.startProcess();
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
        request.addProperty("antiCsrfToken", sessionInfo.get("antiCsrfToken").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        JsonObject response = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/verify", request, 1000,
                        1000,
                        null);

        assertEquals(response.get("status").getAsString(), "UNAUTHORISED");
        assertEquals(response.get("message").getAsString(), "");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
