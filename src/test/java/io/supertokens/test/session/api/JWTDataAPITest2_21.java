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
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.*;

public class JWTDataAPITest2_21 {

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
    public void testThatAddingProtectedPropsThrow() throws Exception {
        String[] args = {"../"};

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
                "http://localhost:3567/recipe/session", request, 1000, 1000, null, SemVer.v2_21.get(),
                "session");
        assertEquals(sessionInfo.get("status").getAsString(), "OK");
        String sessionHandle = sessionInfo.get("session").getAsJsonObject().get("handle").getAsString();


        // change JWT payload using API
        JsonObject newUserDataInJwt = new JsonObject();
        newUserDataInJwt.addProperty("sub", "value2");
        request = new JsonObject();
        request.addProperty("sessionHandle", sessionHandle);
        request.add("userDataInJWT", newUserDataInJwt);
        try {
            HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/jwt/data", request, 1000, 1000, null, SemVer.v2_21.get(),
                    "session");
            fail();
        } catch (HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: The user payload contains protected field");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }
}
