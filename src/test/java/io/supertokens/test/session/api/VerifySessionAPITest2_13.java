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

public class VerifySessionAPITest2_13 {
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
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject grantPayload = Utils.getExampleGrantPayload();

        JsonObject sessionRequest = new JsonObject();
        sessionRequest.addProperty("userId", userId);
        sessionRequest.add("userDataInJWT", userDataInJWT);
        sessionRequest.add("userDataInDatabase", userDataInDatabase);
        sessionRequest.add("grants", grantPayload);
        sessionRequest.addProperty("enableAntiCsrf", false);

        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        request.addProperty("enableAntiCsrf", false);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");

        assertEquals(response.get("status").getAsString(), "OK");

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().get("grants").getAsJsonObject().toString(),
                grantPayload.toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 4);

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
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");
        JsonObject grantPayload = Utils.getExampleGrantPayload();

        JsonObject sessionRequest = new JsonObject();
        sessionRequest.addProperty("userId", userId);
        sessionRequest.add("userDataInJWT", userDataInJWT);
        sessionRequest.add("userDataInDatabase", userDataInDatabase);
        sessionRequest.add("grants", grantPayload);
        sessionRequest.addProperty("enableAntiCsrf", false);
        JsonObject sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", sessionRequest, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");

        JsonObject refreshRequest = new JsonObject();
        refreshRequest.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        refreshRequest.addProperty("enableAntiCsrf", false);
        sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", refreshRequest, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        request.addProperty("enableAntiCsrf", false);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");

        assertEquals(response.get("status").getAsString(), "OK");

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().get("grants").getAsJsonObject().toString(),
                grantPayload.toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 4);

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
    public void successOutputCheckNoNewAccessTokenWithV2Token() throws Exception {
        String[] args = { "../" };
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
                Utils.getCdiVersion2_9ForTests(), "session");

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        request.addProperty("enableAntiCsrf", false);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");

        assertEquals(response.get("status").getAsString(), "OK");

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().get("grants").getAsJsonObject().toString(),
                new JsonObject().toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 4);

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
    public void successOutputCheckNewAccessTokenWithV2Token() throws Exception {
        String[] args = { "../" };
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
                Utils.getCdiVersion2_9ForTests(), "session");

        JsonObject refreshRequest = new JsonObject();
        refreshRequest.addProperty("refreshToken",
                sessionInfo.get("refreshToken").getAsJsonObject().get("token").getAsString());
        refreshRequest.addProperty("enableAntiCsrf", false);
        sessionInfo = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", refreshRequest, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", sessionInfo.get("accessToken").getAsJsonObject().get("token").getAsString());
        request.addProperty("doAntiCsrfCheck", true);
        request.addProperty("enableAntiCsrf", false);
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/verify", request, 1000, 1000, null,
                Utils.getCdiVersion2_13ForTests(), "session");

        assertEquals(response.get("status").getAsString(), "OK");

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().get("grants").getAsJsonObject().toString(),
                new JsonObject().toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 4);

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
}
