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
import io.supertokens.ProcessState;
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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotNull;

public class RefreshSessionAPITest3_0 {
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
                SemVer.v3_0.get(), "session");

        checkRefreshSessionResponse(sessionRefreshResponse, process, userId, userDataInJWT, false, false);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void successOutputUpgradeWithNonStaticKeySessionTest() throws Exception {
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
        sessionRefreshBody.addProperty("useDynamicSigningKey", true);

        JsonObject sessionRefreshResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                SemVer.v3_0.get(), "session");

        checkRefreshSessionResponse(sessionRefreshResponse, process, userId, userDataInJWT, false, false);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void successOutputUpgradeWithStaticKeySessionTest() throws Exception {
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
        sessionRefreshBody.addProperty("useDynamicSigningKey", false);

        JsonObject sessionRefreshResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", sessionRefreshBody, 1000, 1000, null,
                SemVer.v3_0.get(), "session");

        checkRefreshSessionResponse(sessionRefreshResponse, process, userId, userDataInJWT, false, true);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static void checkRefreshSessionResponse(JsonObject response, TestingProcessManager.TestingProcess process,
                                                    String userId, JsonObject userDataInJWT, boolean hasAntiCsrf,
                                                    boolean useStaticKey) throws
            JWT.JWTException {

        assertNotNull(response.get("session").getAsJsonObject().get("handle").getAsString());
        assertEquals(response.get("session").getAsJsonObject().get("userId").getAsString(), userId);
        assertEquals(response.get("session").getAsJsonObject().get("tenantId").getAsString(), "public");
        assertEquals(response.get("session").getAsJsonObject().get("userDataInJWT").getAsJsonObject().toString(),
                userDataInJWT.toString());
        assertEquals(response.get("session").getAsJsonObject().entrySet().size(), 4);

        assertTrue(response.get("accessToken").getAsJsonObject().has("token"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("accessToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("accessToken").getAsJsonObject().entrySet().size(), 3);

        JWT.JWTPreParseInfo tokenInfo = JWT.preParseJWTInfo(
                response.get("accessToken").getAsJsonObject().get("token").getAsString());

        if (useStaticKey) {
            assert (tokenInfo.kid.startsWith("s-"));
        } else {
            assert (tokenInfo.kid.startsWith("d-"));
        }

        assertTrue(response.get("refreshToken").getAsJsonObject().has("token"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("expiry"));
        assertTrue(response.get("refreshToken").getAsJsonObject().has("createdTime"));
        assertEquals(response.get("refreshToken").getAsJsonObject().entrySet().size(), 3);

        assertEquals(response.has("antiCsrfToken"), hasAntiCsrf);

        assertEquals(response.entrySet().size(), hasAntiCsrf ? 5 : 4);
    }
}
