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
import io.supertokens.webserver.WebserverAPITest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

public class RefreshSessionAPITest2_3 {
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
    public void refreshWithAntiCsrfOffOn() throws Exception {

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

        JsonObject sessionInfo = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2_3ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        Utils.setValueInConfig("enable_anti_csrf", "true");

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        {
            JsonObject sessionRefreshBody = new JsonObject();

            sessionRefreshBody.addProperty("refreshToken",
                    sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());

            JsonObject response = io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh",
                            sessionRefreshBody, 1000,
                            1000, null, Utils.getCdiVersion2_3ForTests());

            checkRefreshSessionResponse(response, process, userId, userDataInJWT, true, true);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void refreshWithAntiCsrfOnOffOn() throws Exception {

        Utils.setValueInConfig("enable_anti_csrf", "true");

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

        JsonObject sessionInfo = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2_3ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        Utils.setValueInConfig("enable_anti_csrf", "false");

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        {
            JsonObject sessionRefreshBody = new JsonObject();

            sessionRefreshBody.addProperty("refreshToken",
                    sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());

            JsonObject response = io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh",
                            sessionRefreshBody, 1000,
                            1000, null, Utils.getCdiVersion2_3ForTests());

            checkRefreshSessionResponse(response, process, userId, userDataInJWT, true, false);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        Utils.setValueInConfig("enable_anti_csrf", "true");

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        {
            JsonObject sessionRefreshBody = new JsonObject();

            sessionRefreshBody.addProperty("refreshToken",
                    sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());

            JsonObject response = io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh",
                            sessionRefreshBody, 1000,
                            1000, null, Utils.getCdiVersion2_3ForTests());

            assertEquals(response.entrySet().size(), 2);
            assertEquals(response.get("status").getAsString(), "UNAUTHORISED");
            assertEquals(response.get("message").getAsString(),
                    "Anti CSRF token missing, or not matching");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }


    @Test
    public void badInputErrorTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            io.supertokens.test.httpRequest
                    .HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh", null, 1000,
                            1000, null, Utils.getCdiVersion2_3ForTests());
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals("Http error. Status Code: 400. Message: Invalid Json Input", e.getMessage());
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        try {
            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("random", "random");
            io.supertokens.test.httpRequest
                    .HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh", jsonBody,
                            1000,
                            1000, null, Utils.getCdiVersion2_3ForTests());
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'refreshToken' is invalid in JSON input");

        }
    }


    @Test
    public void successOutputWithInvalidRefreshTokenTest() throws Exception {
        String[] args = {"../"};

        JsonObject josnBody = new JsonObject();
        josnBody.addProperty("refreshToken", "");

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject response = io.supertokens.test.httpRequest
                .HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh", josnBody, 1000,
                        1000, null, Utils.getCdiVersion2_3ForTests());

        assertEquals(response.entrySet().size(), 2);
        assertEquals(response.get("status").getAsString(), "UNAUTHORISED");
        assertEquals(response.get("message").getAsString(),
                "io.supertokens.session.refreshToken.RefreshToken$InvalidRefreshTokenFormatException: version of " +
                        "refresh token not recognised");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }


    @Test
    public void successOutputWithValidRefreshTokenTest() throws Exception {
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

        JsonObject sessionInfo = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2_3ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        JsonObject sessionRefreshBody = new JsonObject();

        sessionRefreshBody.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());

        JsonObject sessionRefreshResponse = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh",
                        sessionRefreshBody, 1000,
                        1000, null, Utils.getCdiVersion2_3ForTests());

        checkRefreshSessionResponse(sessionRefreshResponse, process, userId, userDataInJWT, true, false);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void refreshWithAntiCsrfOn() throws Exception {

        Utils.setValueInConfig("enable_anti_csrf", "true");

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

        JsonObject sessionInfo = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2_3ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        {
            JsonObject sessionRefreshBody = new JsonObject();

            sessionRefreshBody.addProperty("refreshToken",
                    sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
            sessionRefreshBody.addProperty("antiCsrfToken", "someRandomString");

            JsonObject response = io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh",
                            sessionRefreshBody, 1000,
                            1000, null, Utils.getCdiVersion2_3ForTests());

            assertEquals(response.entrySet().size(), 2);
            assertEquals(response.get("status").getAsString(), "UNAUTHORISED");
            assertEquals(response.get("message").getAsString(),
                    "Anti CSRF token missing, or not matching");
        }

        {
            JsonObject sessionRefreshBody = new JsonObject();

            sessionRefreshBody.addProperty("refreshToken",
                    sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());

            JsonObject response = io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh",
                            sessionRefreshBody, 1000,
                            1000, null, Utils.getCdiVersion2_3ForTests());

            assertEquals(response.entrySet().size(), 2);
            assertEquals(response.get("status").getAsString(), "UNAUTHORISED");
            assertEquals(response.get("message").getAsString(),
                    "Anti CSRF token missing, or not matching");
        }

        {
            JsonObject sessionRefreshBody = new JsonObject();

            sessionRefreshBody.addProperty("refreshToken",
                    sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
            sessionRefreshBody.addProperty("antiCsrfToken", sessionInfo.get("antiCsrfToken").getAsString());

            JsonObject response = io.supertokens.test.httpRequest.HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh",
                            sessionRefreshBody, 1000,
                            1000, null, Utils.getCdiVersion2_3ForTests());

            checkRefreshSessionResponse(response, process, userId, userDataInJWT, true, true);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void successOutputWithValidRefreshTokenInvalidAntiCsrfTokenTest() throws Exception {
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

        JsonObject sessionInfo = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2_3ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        JsonObject sessionRefreshBody = new JsonObject();

        sessionRefreshBody.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        sessionRefreshBody.addProperty("antiCsrfToken", "someRandomString");

        JsonObject sessionRefreshResponse = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh",
                        sessionRefreshBody, 1000,
                        1000, null, Utils.getCdiVersion2_3ForTests());

        checkRefreshSessionResponse(sessionRefreshResponse, process, userId, userDataInJWT, true, false);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void successOutputWithValidRefreshTokenWithWithCookieDomainTest() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("cookie_domain", "localhost");

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

        JsonObject sessionInfo = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2_3ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        JsonObject sessionRefreshBody = new JsonObject();

        sessionRefreshBody.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());

        JsonObject sessionRefreshResponse = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh",
                        sessionRefreshBody, 1000,
                        1000, null, Utils.getCdiVersion2_3ForTests());

        checkRefreshSessionResponse(sessionRefreshResponse, process, userId, userDataInJWT, false, false);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }


    @Test
    public void throwsRandomSessionExpiredTest() throws Exception {
        String[] args = {"../"};
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

        JsonObject sessionInfo = io.supertokens.test.httpRequest.HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/session", sessionRequest, 1000, 1000, null,
                        Utils.getCdiVersion2_3ForTests());

        JsonObject request = new JsonObject();
        request.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());

        JsonObject response = io.supertokens.test.httpRequest
                .HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/refresh", request, 1000,
                        1000,
                        null, Utils.getCdiVersion2_3ForTests());

        assertEquals(response.get("status").getAsString(), "UNAUTHORISED");
        assertEquals(response.get("message").getAsString(), "");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));


    }

    private static void checkRefreshSessionResponse(JsonObject response,
                                                    TestingProcessManager.TestingProcess process, String userId,
                                                    JsonObject userDataInJWT,
                                                    boolean cookieDomainShouldBeNull,
                                                    boolean hasAntiCsrf) {

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 3);

        assertTrue(response.get("accessToken").getAsJsonObject().has("token"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("accessToken").getAsJsonObject().get("cookiePath").getAsString(),
                Config.getConfig(process.getProcess()).getAccessTokenPath());
        assertEquals(response.get("accessToken").getAsJsonObject().get("cookieSecure").getAsBoolean(),
                Config.getConfig(process.getProcess()).getCookieSecure(process.getProcess()));
        if (cookieDomainShouldBeNull) {
            assertNull(response.get("accessToken").getAsJsonObject().get("domain"));
        } else {
            assertEquals(response.get("accessToken").getAsJsonObject().get("domain").getAsString(),
                    Config.getConfig(process.getProcess()).getCookieDomain(Utils.getCdiVersion2_3ForTests()));
        }
        assertEquals(response.get("accessToken").getAsJsonObject().get("sameSite").getAsString(),
                Config.getConfig(process.getProcess()).getCookieSameSite());
        assertEquals(response.get("accessToken").getAsJsonObject().entrySet().size(), cookieDomainShouldBeNull ? 6 : 7);

        assertTrue(response.get("refreshToken").getAsJsonObject().has("token"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("refreshToken").getAsJsonObject().get("cookiePath").getAsString(),
                Config.getConfig(process.getProcess()).getRefreshAPIPath());
        assertEquals(response.get("refreshToken").getAsJsonObject().get("cookieSecure").getAsBoolean(),
                Config.getConfig(process.getProcess()).getCookieSecure(process.getProcess()));
        if (cookieDomainShouldBeNull) {
            assertNull(response.get("refreshToken").getAsJsonObject().get("domain"));
        } else {
            assertEquals(response.get("refreshToken").getAsJsonObject().get("domain").getAsString(),
                    Config.getConfig(process.getProcess()).getCookieDomain(Utils.getCdiVersion2_3ForTests()));
        }
        assertEquals(response.get("refreshToken").getAsJsonObject().get("sameSite").getAsString(),
                Config.getConfig(process.getProcess()).getCookieSameSite());
        assertEquals(response.get("refreshToken").getAsJsonObject().entrySet().size(),
                cookieDomainShouldBeNull ? 6 : 7);

        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("token"));
        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("idRefreshToken").getAsJsonObject().get("cookiePath").getAsString(),
                Config.getConfig(process.getProcess()).getAccessTokenPath());
        assertEquals(response.get("idRefreshToken").getAsJsonObject().get("cookieSecure").getAsBoolean(),
                Config.getConfig(process.getProcess()).getCookieSecure(process.getProcess()));
        if (cookieDomainShouldBeNull) {
            assertNull(response.get("idRefreshToken").getAsJsonObject().get("domain"));
        } else {
            assertEquals(response.get("idRefreshToken").getAsJsonObject().get("domain").getAsString(),
                    Config.getConfig(process.getProcess()).getCookieDomain(Utils.getCdiVersion2_3ForTests()));
        }
        assertEquals(response.get("idRefreshToken").getAsJsonObject().get("sameSite").getAsString(),
                Config.getConfig(process.getProcess()).getCookieSameSite());

        assertEquals(response.get("idRefreshToken").getAsJsonObject().entrySet().size(),
                cookieDomainShouldBeNull ? 6 : 7);

        assertEquals(response.has("antiCsrfToken"), hasAntiCsrf);

        assertEquals(response.entrySet().size(), hasAntiCsrf ? 6 : 5);
    }

}
