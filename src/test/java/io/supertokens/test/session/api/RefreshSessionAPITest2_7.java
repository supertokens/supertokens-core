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

import io.supertokens.ActiveUsers;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import io.supertokens.version.Version;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class RefreshSessionAPITest2_7 {
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
        request.addProperty("enableAntiCsrf", false);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_7.get(),
                "session");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        process.kill(false);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        if (Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
            // in mem db cannot pass this test
            return;
        }

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        {
            JsonObject sessionRefreshBody = new JsonObject();

            sessionRefreshBody.addProperty("refreshToken",
                    sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
            sessionRefreshBody.addProperty("enableAntiCsrf", true);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                    SemVer.v2_7.get(), "session");

            checkRefreshSessionResponse(response, process, userId, userDataInJWT, true);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void refreshWithAntiCsrfOnOffOn() throws Exception {

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
        request.addProperty("enableAntiCsrf", true);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_7.get(),
                "session");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        process.kill(false);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        if (Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
            // in mem db cannot pass this test
            return;
        }

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        {
            JsonObject sessionRefreshBody = new JsonObject();

            sessionRefreshBody.addProperty("refreshToken",
                    sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
            sessionRefreshBody.addProperty("enableAntiCsrf", false);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                    SemVer.v2_7.get(), "session");

            checkRefreshSessionResponse(response, process, userId, userDataInJWT, false);
        }

        process.kill(false);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        {
            JsonObject sessionRefreshBody = new JsonObject();

            sessionRefreshBody.addProperty("refreshToken",
                    sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
            sessionRefreshBody.addProperty("enableAntiCsrf", true);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                    SemVer.v2_7.get(), "session");

            assertEquals(response.entrySet().size(), 2);
            assertEquals(response.get("status").getAsString(), "UNAUTHORISED");
            assertEquals(response.get("message").getAsString(), "Anti CSRF token missing, or not matching");
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
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/refresh", null, 1000, 1000, null,
                    SemVer.v2_7.get(), "session");
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
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/refresh", jsonBody, 1000, 1000, null,
                    SemVer.v2_7.get(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'refreshToken' is invalid in JSON input");

        }

        try {
            String userId = "userId";
            JsonObject userDataInJWT = new JsonObject();
            userDataInJWT.addProperty("key", "value");
            JsonObject userDataInDatabase = new JsonObject();
            userDataInDatabase.addProperty("key", "value");
            JsonObject request = new JsonObject();
            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);
            request.addProperty("enableAntiCsrf", false);

            JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_7.get(),
                    "session");
            assertEquals(sessionInfo.get("status").getAsString(), "OK");

            JsonObject sessionRefreshBody = new JsonObject();

            sessionRefreshBody.addProperty("refreshToken",
                    sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());

            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                    SemVer.v2_7.get(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'enableAntiCsrf' is invalid in JSON input");

        }

        try {
            String userId = "userId";
            JsonObject userDataInJWT = new JsonObject();
            userDataInJWT.addProperty("key", "value");
            JsonObject userDataInDatabase = new JsonObject();
            userDataInDatabase.addProperty("key", "value");
            JsonObject request = new JsonObject();
            request.addProperty("userId", userId);
            request.add("userDataInJWT", userDataInJWT);
            request.add("userDataInDatabase", userDataInDatabase);
            request.addProperty("enableAntiCsrf", false);

            JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_7.get(),
                    "session");
            assertEquals(sessionInfo.get("status").getAsString(), "OK");

            JsonObject sessionRefreshBody = new JsonObject();

            sessionRefreshBody.addProperty("refreshToken",
                    sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
            sessionRefreshBody.addProperty("enableAntiCsrf", "false");

            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                    SemVer.v2_7.get(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'enableAntiCsrf' is invalid in JSON input");

        }
    }

    @Test
    public void successOutputWithInvalidRefreshTokenTest() throws Exception {
        String[] args = {"../"};

        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("refreshToken", "");
        jsonBody.addProperty("enableAntiCsrf", false);

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("enableAntiCsrf", false);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", jsonBody, 1000, 1000, null,
                SemVer.v2_7.get(), "session");

        assertEquals(response.entrySet().size(), 2);
        assertEquals(response.get("status").getAsString(), "UNAUTHORISED");
        assertEquals(response.get("message").getAsString(),
                "io.supertokens.session.refreshToken.RefreshToken$InvalidRefreshTokenFormatException: version of "
                        + "refresh token not recognised");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void successOutputWithInvalidRefresh2TokenTest() throws Exception {
        String[] args = {"../"};

        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("refreshToken", "MAKE_INVALID4dx2zJTl67cLnPHj2nrYNPJkYWRhRb1BZtiy4DtZ"
                + "/bfGZHM0DEy9xX8nXkzdRYjQfYLGlcteX7noVxuCRk0zeewXGTG+fvnkAgPE8SK62X/U4VX5LsHKAxDsiFw"
                +
                "+eh5mxuJE9DrPCjPk2ObkTMgRaA7TSzMInPt1OWZHhx8FvbQjgskwalYuptk4RdVMX7I6hwjflyCQ8kxhdZOAWzNati1ROyGmchQ5x6sIMIhpc0YzMi/BRfBpEIGSuXMHtxQuR/swvUbXlzpxDD375S1EDzQeW9ghXOt1AJDMCbVuIdXb4MEwqIWa473yYi6XujwUCbHo/3tNWtmn6tOh7w==.ef2c9aab475728ec8817813c5e02077af30425562341289b5a3e1c67122ea853.V2");
        jsonBody.addProperty("enableAntiCsrf", false);

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("enableAntiCsrf", false);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", jsonBody, 1000, 1000, null,
                SemVer.v2_7.get(), "session");

        assertEquals(response.entrySet().size(), 2);
        assertEquals(response.get("status").getAsString(), "UNAUTHORISED");

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
        userDataInJWT.add("nullProp", JsonNull.INSTANCE);
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("enableAntiCsrf", false);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_7.get(),
                "session");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        JsonObject sessionRefreshBody = new JsonObject();

        sessionRefreshBody.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        sessionRefreshBody.addProperty("enableAntiCsrf", false);

        JsonObject sessionRefreshResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                SemVer.v2_7.get(), "session");

        checkRefreshSessionResponse(sessionRefreshResponse, process, userId, userDataInJWT, false);
    }

    @Test
    public void activeUsersTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Failure case:
        long start1 = System.currentTimeMillis();

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/refresh", null, 1000, 1000, null,
                    SemVer.v2_7.get(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals("Http error. Status Code: 400. Message: Invalid Json Input", e.getMessage());
        }

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), start1);
        assert (activeUsers == 0);

        // Success case:
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.add("nullProp", JsonNull.INSTANCE);
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("enableAntiCsrf", false);

        Thread.sleep(1); // Ensures a unique timestamp
        long startTs = System.currentTimeMillis();

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_7.get(),
                "session");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        JsonObject sessionRefreshBody = new JsonObject();

        sessionRefreshBody.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        sessionRefreshBody.addProperty("enableAntiCsrf", false);

        Thread.sleep(1); // ensures a unique timestamp
        long afterSessionCreateTs = System.currentTimeMillis();

        JsonObject sessionRefreshResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                SemVer.v2_7.get(), "session");

        checkRefreshSessionResponse(sessionRefreshResponse, process, userId, userDataInJWT, false);

        activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 1);

        int activeUsersAfterSessionCreate = ActiveUsers.countUsersActiveSince(process.getProcess(),
                afterSessionCreateTs);
        assert (activeUsersAfterSessionCreate == 1);
    }

    @Test
    public void refreshWithAntiCsrfOn() throws Exception {

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
        request.addProperty("enableAntiCsrf", true);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_7.get(),
                "session");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        {
            JsonObject sessionRefreshBody = new JsonObject();

            sessionRefreshBody.addProperty("refreshToken",
                    sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
            sessionRefreshBody.addProperty("antiCsrfToken", "someRandomString");
            sessionRefreshBody.addProperty("enableAntiCsrf", true);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                    SemVer.v2_7.get(), "session");

            assertEquals(response.entrySet().size(), 2);
            assertEquals(response.get("status").getAsString(), "UNAUTHORISED");
            assertEquals(response.get("message").getAsString(), "Anti CSRF token missing, or not matching");
        }

        {
            JsonObject sessionRefreshBody = new JsonObject();

            sessionRefreshBody.addProperty("refreshToken",
                    sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
            sessionRefreshBody.addProperty("enableAntiCsrf", true);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                    SemVer.v2_7.get(), "session");

            assertEquals(response.entrySet().size(), 2);
            assertEquals(response.get("status").getAsString(), "UNAUTHORISED");
            assertEquals(response.get("message").getAsString(), "Anti CSRF token missing, or not matching");
        }

        {
            JsonObject sessionRefreshBody = new JsonObject();

            sessionRefreshBody.addProperty("refreshToken",
                    sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
            sessionRefreshBody.addProperty("antiCsrfToken", sessionInfo.get("antiCsrfToken").getAsString());
            sessionRefreshBody.addProperty("enableAntiCsrf", true);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                    SemVer.v2_7.get(), "session");

            checkRefreshSessionResponse(response, process, userId, userDataInJWT, true);
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
        request.addProperty("enableAntiCsrf", false);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_7.get(),
                "session");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        JsonObject sessionRefreshBody = new JsonObject();

        sessionRefreshBody.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        sessionRefreshBody.addProperty("antiCsrfToken", "someRandomString");
        sessionRefreshBody.addProperty("enableAntiCsrf", false);

        JsonObject sessionRefreshResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                SemVer.v2_7.get(), "session");

        checkRefreshSessionResponse(sessionRefreshResponse, process, userId, userDataInJWT, false);
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
        request.addProperty("enableAntiCsrf", false);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_7.get(),
                "session");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");

        JsonObject sessionRefreshBody = new JsonObject();

        sessionRefreshBody.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        sessionRefreshBody.addProperty("enableAntiCsrf", false);

        JsonObject sessionRefreshResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                SemVer.v2_7.get(), "session");

        checkRefreshSessionResponse(sessionRefreshResponse, process, userId, userDataInJWT, false);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    private static void checkRefreshSessionResponse(JsonObject response, TestingProcessManager.TestingProcess process,
                                                    String userId, JsonObject userDataInJWT, boolean hasAntiCsrf) {

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

        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("token"));
        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("idRefreshToken").getAsJsonObject().has("createdTime"));

        assertEquals(response.get("idRefreshToken").getAsJsonObject().entrySet().size(), 3);

        assertEquals(response.has("antiCsrfToken"), hasAntiCsrf);

        assertEquals(response.entrySet().size(), hasAntiCsrf ? 6 : 5);
    }

}
