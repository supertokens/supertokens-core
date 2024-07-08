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
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertNotNull;

public class VerifySessionAPITest2_9 {
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
    public void successOutputCheckNoNewAccessToken() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        userDataInJWT.add("nullProp", JsonNull.INSTANCE);
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject sessionRequest = new JsonObject();
        sessionRequest.addProperty("userId", userId);
        sessionRequest.add("userDataInJWT", userDataInJWT);
        sessionRequest.add("userDataInDatabase", userDataInDatabase);
        sessionRequest.addProperty("enableAntiCsrf", false);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                SemVer.v2_9.get(), "session");

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        request.addProperty("enableAntiCsrf", false);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                SemVer.v2_9.get(), "session");

        assertEquals(response.get("status").getAsString(), "OK");

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 3);

        assertNotNull(response.get("jwtSigningPublicKey").getAsString());
        assertTrue(response.has("jwtSigningPublicKeyExpiryTime"));
        assertTrue(response.has("jwtSigningPublicKeyList"));
        JsonArray respPubKeyList = response.get("jwtSigningPublicKeyList").getAsJsonArray();
        for (int i = 0; i < respPubKeyList.size(); ++i) {
            assertTrue(respPubKeyList.get(i).getAsJsonObject().has("publicKey"));
            assertTrue(respPubKeyList.get(i).getAsJsonObject().has("expiryTime"));
            assertTrue(respPubKeyList.get(i).getAsJsonObject().has("createdAt"));
            assertEquals(respPubKeyList.get(i).getAsJsonObject().entrySet().size(), 3);
        }
        assertEquals(response.entrySet().size(), 5);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void successOutputCheckNewAccessToken() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        userDataInJWT.add("nullProp", JsonNull.INSTANCE);
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject sessionRequest = new JsonObject();
        sessionRequest.addProperty("userId", userId);
        sessionRequest.add("userDataInJWT", userDataInJWT);
        sessionRequest.add("userDataInDatabase", userDataInDatabase);
        sessionRequest.addProperty("enableAntiCsrf", false);
        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                SemVer.v2_9.get(), "session");

        JsonObject refreshRequest = new JsonObject();
        refreshRequest.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        refreshRequest.addProperty("enableAntiCsrf", false);
        sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", refreshRequest, 1000, 1000, null,
                SemVer.v2_9.get(), "session");

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        request.addProperty("enableAntiCsrf", false);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                SemVer.v2_9.get(), "session");

        assertEquals(response.get("status").getAsString(), "OK");

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 3);

        assertTrue(response.get("accessToken").getAsJsonObject().has("token"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("createdTime"));

        assertEquals(response.get("accessToken").getAsJsonObject().entrySet().size(), 3);

        assertNotNull(response.get("jwtSigningPublicKey").getAsString());
        assertTrue(response.has("jwtSigningPublicKeyExpiryTime"));
        assertTrue(response.has("jwtSigningPublicKeyList"));
        JsonArray respPubKeyList = response.get("jwtSigningPublicKeyList").getAsJsonArray();
        for (int i = 0; i < respPubKeyList.size(); ++i) {
            assertTrue(respPubKeyList.get(i).getAsJsonObject().has("publicKey"));
            assertTrue(respPubKeyList.get(i).getAsJsonObject().has("expiryTime"));
            assertTrue(respPubKeyList.get(i).getAsJsonObject().has("createdAt"));
            assertEquals(respPubKeyList.get(i).getAsJsonObject().entrySet().size(), 3);
        }
        assertEquals(response.entrySet().size(), 6);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void successOutputCheckNewAccessTokenWithCookieDomain() throws Exception {
        String[] args = {"../"};
        Utils.setValueInConfig("cookie_domain", "localhost");

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
        sessionRequest.addProperty("enableAntiCsrf", false);
        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                SemVer.v2_9.get(), "session");

        JsonObject refreshRequest = new JsonObject();
        refreshRequest.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        refreshRequest.addProperty("enableAntiCsrf", false);
        sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", refreshRequest, 1000, 1000, null,
                SemVer.v2_9.get(), "session");

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        request.addProperty("enableAntiCsrf", false);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                SemVer.v2_9.get(), "session");

        assertEquals(response.get("status").getAsString(), "OK");

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 3);

        assertTrue(response.get("accessToken").getAsJsonObject().has("token"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("createdTime"));

        assertEquals(response.get("accessToken").getAsJsonObject().entrySet().size(), 3);

        assertNotNull(response.get("jwtSigningPublicKey").getAsString());
        assertTrue(response.has("jwtSigningPublicKeyExpiryTime"));
        assertTrue(response.has("jwtSigningPublicKeyList"));
        JsonArray respPubKeyList = response.get("jwtSigningPublicKeyList").getAsJsonArray();
        for (int i = 0; i < respPubKeyList.size(); ++i) {
            assertTrue(respPubKeyList.get(i).getAsJsonObject().has("publicKey"));
            assertTrue(respPubKeyList.get(i).getAsJsonObject().has("expiryTime"));
            assertTrue(respPubKeyList.get(i).getAsJsonObject().has("createdAt"));
            assertEquals(respPubKeyList.get(i).getAsJsonObject().entrySet().size(), 3);
        }

        assertEquals(response.entrySet().size(), 6);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void unauthorisedOutputCheck() throws Exception {

        Utils.setValueInConfig("access_token_blacklisting", "true");

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
        sessionRequest.addProperty("enableAntiCsrf", false);
        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                SemVer.v2_9.get(), "session");

        String sessionRemoveBodyString = "{" + " sessionHandles : [ "
                + sessionInfo.get("session").getAsJsonObject().get("handle").getAsString() + " ] " + "}";
        JsonObject deleteRequest = new JsonParser().parse(sessionRemoveBodyString).getAsJsonObject();

        HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/remove", deleteRequest, 1000, 1000, null,
                SemVer.v2_9.get(), "session");

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        request.addProperty("enableAntiCsrf", false);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                SemVer.v2_9.get(), "session");

        assertEquals(response.get("status").getAsString(), "UNAUTHORISED");
        assertEquals(response.get("message").getAsString(), "Either the session has ended or has been blacklisted");

        assertEquals(response.entrySet().size(), 2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void tryRefreshTokenOutputCheck() throws Exception {

        Utils.setValueInConfig("access_token_blacklisting", "true");

        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", "randomToken");
        request.addProperty("antiCsrfToken", "random");
        request.addProperty("doAntiCsrfCheck", true);
        request.addProperty("enableAntiCsrf", false);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                SemVer.v2_9.get(), "session");

        assertEquals(response.get("status").getAsString(), "TRY_REFRESH_TOKEN");
        assertEquals(response.get("message").getAsString(), "io.supertokens.session.jwt.JWT$JWTException: Invalid JWT");

        assertEquals(response.entrySet().size(), 5);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void badInputTest() throws Exception {
        String[] args = {"../"};
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
            sessionRequest.addProperty("enableAntiCsrf", true);
            JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                    SemVer.v2_9.get(), "session");

            JsonObject request = new JsonObject();
            request.addProperty("doAntiCsrfCheck", true);
            request.addProperty("enableAntiCsrf", true);
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                    SemVer.v2_9.get(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'accessToken' is invalid in JSON input");
        }

        try {
            JsonObject sessionRequest = new JsonObject();
            sessionRequest.addProperty("userId", userId);
            sessionRequest.add("userDataInJWT", userDataInJWT);
            sessionRequest.add("userDataInDatabase", userDataInDatabase);
            sessionRequest.addProperty("enableAntiCsrf", true);
            JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                    SemVer.v2_9.get(), "session");

            JsonObject request = new JsonObject();
            request.addProperty("accessToken",
                    sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
            request.addProperty("doAntiCsrfCheck", true);
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                    SemVer.v2_9.get(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'enableAntiCsrf' is invalid in JSON input");
        }

        try {
            JsonObject sessionRequest = new JsonObject();
            sessionRequest.addProperty("userId", userId);
            sessionRequest.add("userDataInJWT", userDataInJWT);
            sessionRequest.add("userDataInDatabase", userDataInDatabase);
            sessionRequest.addProperty("enableAntiCsrf", true);
            JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                    SemVer.v2_9.get(), "session");

            JsonObject request = new JsonObject();
            request.addProperty("accessToken",
                    sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
            request.addProperty("doAntiCsrfCheck", true);
            request.addProperty("enableAntiCsrf", "true");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                    SemVer.v2_9.get(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'enableAntiCsrf' is invalid in JSON input");
        }

        {
            JsonObject sessionRequest = new JsonObject();
            sessionRequest.addProperty("userId", userId);
            sessionRequest.add("userDataInJWT", userDataInJWT);
            sessionRequest.add("userDataInDatabase", userDataInDatabase);
            sessionRequest.addProperty("enableAntiCsrf", true);
            JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                    SemVer.v2_9.get(), "session");

            JsonObject request = new JsonObject();
            request.addProperty("accessToken",
                    sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
            request.addProperty("doAntiCsrfCheck", true);
            request.addProperty("enableAntiCsrf", true);
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                    SemVer.v2_9.get(), "session");

            assertEquals(response.get("status").getAsString(), "TRY_REFRESH_TOKEN");
            assertEquals(response.get("message").getAsString(), "anti-csrf check failed");

            request = new JsonObject();
            request.addProperty("accessToken",
                    sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
            request.addProperty("doAntiCsrfCheck", false);
            request.addProperty("enableAntiCsrf", true);
            response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                    SemVer.v2_9.get(), "session");
            assertEquals(response.get("status").getAsString(), "OK");
        }

        try {
            JsonObject sessionRequest = new JsonObject();
            sessionRequest.addProperty("userId", userId);
            sessionRequest.add("userDataInJWT", userDataInJWT);
            sessionRequest.add("userDataInDatabase", userDataInDatabase);
            sessionRequest.addProperty("enableAntiCsrf", true);
            JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                    SemVer.v2_9.get(), "session");

            JsonObject request = new JsonObject();
            request.addProperty("accessToken",
                    sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
            request.addProperty("antiCsrfToken", sessionInfo.get("antiCsrfToken").getAsString());
            request.addProperty("enableAntiCsrf", true);
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                    SemVer.v2_9.get(), "session");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'doAntiCsrfCheck' is invalid in JSON input");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
