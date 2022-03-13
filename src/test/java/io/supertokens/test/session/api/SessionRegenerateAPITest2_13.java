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
import io.supertokens.ProcessState;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class SessionRegenerateAPITest2_13 {
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
    public void testCallRegenerateAPIWithNewGrantPayloadAndV2Token() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
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
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, Utils.getCdiVersion2_12ForTests(),
                "session");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        String accessToken = sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString();

        AccessToken.AccessTokenInfo accessTokenBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                accessToken, false);

        JsonObject newGrantPayload = Utils.getExampleGrantPayload();

        JsonObject sessionRegenerateRequest = new JsonObject();
        sessionRegenerateRequest.addProperty("accessToken", accessToken);
        sessionRegenerateRequest.add("grants", newGrantPayload);

        JsonObject sessionRegenerateResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/regenerate", sessionRegenerateRequest, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");

        assertEquals(sessionRegenerateResponse.get("status").getAsString(), "OK");

        // check that session object and all has new payload info
        assertEquals(sessionRegenerateResponse.get("session").getAsJsonObject().get("grants"), newGrantPayload);

        // - exipry time of new token is same as old, but lmrt and payload has been changed
        AccessToken.AccessTokenInfo accessTokenAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionRegenerateResponse.get("accessToken").getAsJsonObject().get("token").getAsString(), false);

        assertEquals(accessTokenBefore.expiryTime, accessTokenAfter.expiryTime);
        assertNotEquals(accessTokenBefore.lmrt, accessTokenAfter.lmrt);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCallRegenerateAPIWithNewGrantPayloadAndCheckResponses() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject initialGrantPayload = new JsonObject();

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.add("grants", initialGrantPayload);
        request.addProperty("enableAntiCsrf", false);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, Utils.getCdiVersion2_13ForTests(),
                "session");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        String accessToken = sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString();

        AccessToken.AccessTokenInfo accessTokenBefore = AccessToken.getInfoFromAccessToken(process.getProcess(),
                accessToken, false);

        JsonObject newGrantPayload = Utils.getExampleGrantPayload();

        JsonObject sessionRegenerateRequest = new JsonObject();
        sessionRegenerateRequest.addProperty("accessToken", accessToken);
        sessionRegenerateRequest.add("grants", newGrantPayload);

        JsonObject sessionRegenerateResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/regenerate", sessionRegenerateRequest, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");

        assertEquals(sessionRegenerateResponse.get("status").getAsString(), "OK");

        // check that session object and all has new payload info
        assertEquals(sessionRegenerateResponse.get("session").getAsJsonObject().get("grants"), newGrantPayload);

        // - exipry time of new token is same as old, but lmrt and payload has been changed
        AccessToken.AccessTokenInfo accessTokenAfter = AccessToken.getInfoFromAccessToken(process.getProcess(),
                sessionRegenerateResponse.get("accessToken").getAsJsonObject().get("token").getAsString(), false);

        assertEquals(accessTokenBefore.expiryTime, accessTokenAfter.expiryTime);
        assertNotEquals(accessTokenBefore.lmrt, accessTokenAfter.lmrt);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - create session -> wait for access token to expire -> call regenerate API with new JWT payload -> check
    // responses:
    // * - session object and all has new payload info

    // * - access token is null

    @Test
    public void testWaitForAccessTokenToExpireCallRegenerateWithNewGrantPayloadAndCheckResponses() throws Exception {
        String[] args = { "../" };

        Utils.setValueInConfig("access_token_validity", "1");

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject initialGrantPayload = new JsonObject();

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.add("grants", initialGrantPayload);
        request.addProperty("enableAntiCsrf", false);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, Utils.getCdiVersion2_13ForTests(),
                "session");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        String accessToken = sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString();

        // wait for accessToken to expire

        Thread.sleep(2000);

        JsonObject newGrantPayload = Utils.getExampleGrantPayload();

        JsonObject sessionRegenerateRequest = new JsonObject();
        sessionRegenerateRequest.addProperty("accessToken", accessToken);
        sessionRegenerateRequest.add("grants", newGrantPayload);

        JsonObject sessionRegenerateResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/regenerate", sessionRegenerateRequest, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");
        assertEquals(sessionRegenerateResponse.get("status").getAsString(), "OK");

        // session object and all has new payload info
        assertEquals(sessionRegenerateResponse.get("session").getAsJsonObject().get("grants"), newGrantPayload);

        // access token is null
        assertNull(sessionRegenerateResponse.get("accessToken"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
