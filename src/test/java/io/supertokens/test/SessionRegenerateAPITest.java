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
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.test.httpRequest.HttpRequest;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;


public class SessionRegenerateAPITest {
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
    public void testCallRegenerateAPIWithNewJwtPayloadAndCheckResponses() throws Exception {
        String[] args = {"../", "DEV"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        //createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);

        JsonObject sessionInfo = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        String accessToken = sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString();

        AccessToken.AccessTokenInfo accessTokenBefore = AccessToken
                .getInfoFromAccessToken(process.getProcess(), accessToken, true);

        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");

        JsonObject sessionRegenerateRequest = new JsonObject();
        sessionRegenerateRequest.addProperty("accessToken", accessToken);
        sessionRegenerateRequest.add("userDataInJWT", newUserDataInJWT);

        JsonObject sessionRegenerateResponse = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/regenerate",
                        sessionRegenerateRequest, 1000, 1000, null, Utils.getCdiVersion2ForTests());

        assertEquals(sessionRegenerateResponse.get("status").getAsString(), "OK");

        //check that session object and all has new payload info
        assertEquals(sessionRegenerateResponse.get("session").getAsJsonObject().get("userDataInJWT"), newUserDataInJWT);

        // - exipry time of new token is same as old, but lmrt and payload has been changed
        AccessToken.AccessTokenInfo accessTokenAfter = AccessToken
                .getInfoFromAccessToken(process.getProcess(),
                        sessionRegenerateResponse.get("accessToken").getAsJsonObject().get("token").getAsString(),
                        true);

        assertEquals(accessTokenBefore.expiryTime, accessTokenAfter.expiryTime);
        assertNotEquals(accessTokenBefore.lmrt, accessTokenAfter.lmrt);

        //all other cookie attributes
        assertEquals(sessionRegenerateResponse.get("accessToken").getAsJsonObject().get("cookiePath").getAsString(),
                Config.getConfig(process.getProcess()).getAccessTokenPath());
        assertEquals(sessionRegenerateResponse.get("accessToken").getAsJsonObject().get("cookieSecure").getAsBoolean(),
                Config.getConfig(process.getProcess()).getCookieSecure(process.getProcess()));
        assertEquals(sessionRegenerateResponse.get("accessToken").getAsJsonObject().get("domain").getAsString(),
                Config.getConfig(process.getProcess()).getCookieDomain());
        assertEquals(sessionRegenerateResponse.get("accessToken").getAsJsonObject().get("sameSite").getAsString(),
                Config.getConfig(process.getProcess()).getCookieSameSite());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    //  * - create session -> wait for access token to expire -> call regenerate API with new JWT payload -> check
    //  responses:
    //  * - session object and all has new payload info

    //  * - access token is null

    @Test
    public void testWaitForAccessTokenToExpireCallRegenerateWithNewJWTPayloadAndCheckResponses() throws Exception {
        String[] args = {"../", "DEV"};

        Utils.setValueInConfig("access_token_validity", "1");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        //createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);

        JsonObject sessionInfo = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        String accessToken = sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString();

        //wait for accessToken to expire

        Thread.sleep(2000);

        //call regenerate API with new JWT payload
        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");

        JsonObject sessionRegenerateRequest = new JsonObject();
        sessionRegenerateRequest.addProperty("accessToken", accessToken);
        sessionRegenerateRequest.add("userDataInJWT", newUserDataInJWT);

        JsonObject sessionRegenerateResponse = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/regenerate",
                        sessionRegenerateRequest, 1000, 1000, null, Utils.getCdiVersion2ForTests());
        assertEquals(sessionRegenerateResponse.get("status").getAsString(), "OK");

        //session object and all has new payload info
        assertEquals(sessionRegenerateResponse.get("session").getAsJsonObject().get("userDataInJWT"), newUserDataInJWT);

        //access token is null
        assertNull(sessionRegenerateResponse.get("accessToken"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /* - create session -> wait for refresh token to expire, remove from db -> call regenerate API with new JWT payload
     *  - throws UNAUTHORISED response.
     *  - check that not supported CDI 1.0
     * */

    @Test
    public void testRefreshTokenExpiryCallRegenerateAPIWithNewPayloadAndCheckResponse() throws Exception {

        String[] args = {"../", "DEV"};

        Utils.setValueInConfig("refresh_token_validity", "" + 1.0 / 60);// 1 second validity (value in mins)
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        //createSession with JWT payload
        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);

        JsonObject sessionInfo = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session", request, 1000, 1000,
                        null, Utils.getCdiVersion2ForTests());
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        String accessToken = sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString();

        //wait for refresh token to expire, remove from db
        Thread.sleep(2000);

        JsonObject removeSessionBody = new JsonObject();
        removeSessionBody.addProperty("userId", userId);

        JsonObject sessionRemovedResponse = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/remove",
                        removeSessionBody, 1000, 1000, null, Utils.getCdiVersion2ForTests());
        assertEquals(sessionRemovedResponse.get("status").getAsString(), "OK");


        // call regenerate API with new JWT payload
        JsonObject newUserDataInJWT = new JsonObject();
        newUserDataInJWT.addProperty("key2", "value2");

        JsonObject sessionRegenerateRequest = new JsonObject();
        sessionRegenerateRequest.addProperty("accessToken", accessToken);
        sessionRegenerateRequest.add("userDataInJWT", newUserDataInJWT);

        JsonObject sessionRegenerateResponse = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/regenerate",
                        sessionRegenerateRequest, 1000, 1000, null, Utils.getCdiVersion2ForTests());

        //throws UNAUTHORISED response.
        assertEquals(sessionRegenerateResponse.get("status").getAsString(), "UNAUTHORISED");

        // - check that not supported CDI 1.0
        try {
            HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/session/regenerate",
                            sessionRegenerateRequest, 1000, 1000, null, null);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(), "Http error. Status Code: 400. Message: CDI version not supported");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
