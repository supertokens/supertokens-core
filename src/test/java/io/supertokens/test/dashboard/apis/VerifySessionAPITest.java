/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.dashboard.apis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonObject;

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;

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
    public void testSessionBehavior() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create dashboard user, create session and verify session
        String email = "test@example.com";
        String password = "testPass123";

        Dashboard.signUpDashboardUser(process.getProcess(), email, password);

        // create a session

        String sessionId = Dashboard.signInDashboardUser(process.getProcess(), email, password);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("sessionId", sessionId);
        JsonObject verifyResponse1 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/dashboard/session/verify", requestBody, 1000, 1000, null,
                SemVer.v2_18.get(), "dashboard");
        assertEquals(1, verifyResponse1.entrySet().size());
        assertEquals("OK", verifyResponse1.get("status").getAsString());

        Dashboard.revokeSessionWithSessionId(process.getProcess(), sessionId);

        JsonObject verifyResponse2 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/dashboard/session/verify", requestBody, 1000, 1000, null,
                SemVer.v2_18.get(), "dashboard");

        assertEquals(1, verifyResponse2.entrySet().size());
        assertEquals("INVALID_SESSION_ERROR", verifyResponse2.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSessionBehaviorForCDI2_22() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create dashboard user, create session and verify session
        String email = "test@example.com";
        String password = "testPass123";

        Dashboard.signUpDashboardUser(process.getProcess(), email, password);

        // create a session

        String sessionId = Dashboard.signInDashboardUser(process.getProcess(), email, password);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("sessionId", sessionId);
        JsonObject verifyResponse1 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/dashboard/session/verify", requestBody, 1000, 1000, null,
                SemVer.v3_0.get(), "dashboard");
        assertEquals(2, verifyResponse1.entrySet().size());
        assertEquals("OK", verifyResponse1.get("status").getAsString());
        assertEquals("test@example.com", verifyResponse1.get("email").getAsString());

        Dashboard.revokeSessionWithSessionId(process.getProcess(), sessionId);

        JsonObject verifyResponse2 = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/dashboard/session/verify", requestBody, 1000, 1000, null,
                SemVer.v3_0.get(), "dashboard");

        assertEquals(1, verifyResponse2.entrySet().size());
        assertEquals("INVALID_SESSION_ERROR", verifyResponse2.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
