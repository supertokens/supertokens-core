/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.session.Session;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/**
 * A create-session request whose JWT payload carries a protected claim is rejected with a 400. The rejection has
 * to happen before the session row is written: the access token is minted after the locked insert, so a late
 * rejection used to leave an unusable session behind for the user.
 */
public class SessionCreateProtectedPayloadTest {

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    // CDI version -> the claims its access token version protects (V3, V4, V5)
    private static final Object[][] PROTECTED_CLAIMS_BY_CDI = {
            {"2.21", new String[]{"sub", "exp", "iat", "sessionHandle", "refreshTokenHash1",
                    "parentRefreshTokenHash1", "antiCsrfToken"}},
            {"3.0", new String[]{"sub", "tId"}},
            {"4.0", new String[]{"sub", "tId", "rsub"}},
    };

    @Test
    public void rejectedProtectedClaimLeavesNoSessionForExistingUser() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // A real user takes the locked insert path.
        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password123");
        assertRejectedWithoutSession(process, user.getSupertokensUserId());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void rejectedProtectedClaimLeavesNoSessionForUnknownUser() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // An id with no auth user takes the unlocked fallback insert.
        assertRejectedWithoutSession(process, "unknown-user");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void v2PayloadMayStillUseV3ClaimNames() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // V2 tokens nest the payload under userData, so "sub" cannot collide; released behaviour is kept.
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request("userId", "sub"), 1000, 1000, null, "2.9",
                "session");
        assertEquals("OK", response.get("status").getAsString());
        assertEquals(1, Session.getAllNonExpiredSessionHandlesForUser(process.getProcess(), "userId").length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static void assertRejectedWithoutSession(TestingProcessManager.TestingProcess process, String userId)
            throws Exception {
        for (Object[] row : PROTECTED_CLAIMS_BY_CDI) {
            String cdi = (String) row[0];
            for (String claim : (String[]) row[1]) {
                try {
                    HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/session", request(userId, claim), 1000, 1000, null, cdi,
                            "session");
                    fail("CDI " + cdi + " accepted protected claim " + claim);
                } catch (HttpResponseException e) {
                    assertEquals(400, e.statusCode);
                    assertEquals("Http error. Status Code: 400. Message: The user payload contains protected field",
                            e.getMessage());
                }
                assertEquals("CDI " + cdi + " left a session behind for claim " + claim, 0,
                        Session.getAllNonExpiredSessionHandlesForUser(process.getProcess(), userId).length);
            }
        }
    }

    private static JsonObject request(String userId, String claim) {
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty(claim, "value");
        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", new JsonObject());
        request.addProperty("enableAntiCsrf", false);
        return request;
    }
}
