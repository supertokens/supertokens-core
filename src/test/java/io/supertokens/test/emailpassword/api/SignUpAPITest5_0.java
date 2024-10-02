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

package io.supertokens.test.emailpassword.api;

import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.*;


public class SignUpAPITest5_0 {

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

    // Check good input works
    @Test
    public void testGoodInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("email", "random@gmail.com");
        responseBody.addProperty("password", "validPass123");

        Thread.sleep(1); // add a small delay to ensure a unique timestamp
        long beforeSignIn = System.currentTimeMillis();

        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signup", responseBody, 1000, 1000, null, SemVer.v5_0.get(),
                "emailpassword");

        assertEquals(signInResponse.get("status").getAsString(), "OK");
        assertEquals(signInResponse.entrySet().size(), 3);

        JsonObject jsonUser = signInResponse.get("user").getAsJsonObject();
        assertNotNull(jsonUser.get("id"));
        assertNotNull(jsonUser.get("timeJoined"));
        assert (!jsonUser.get("isPrimaryUser").getAsBoolean());
        assert (jsonUser.get("emails").getAsJsonArray().size() == 1);
        assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("random@gmail.com"));
        assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
        assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 0);
        assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 1);
        JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
        assertFalse(lM.get("verified").getAsBoolean());
        assertNotNull(lM.get("timeJoined"));
        assertNotNull(lM.get("recipeUserId"));
        assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
        assertEquals(lM.get("email").getAsString(), "random@gmail.com");
        assert (lM.entrySet().size() == 6);

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), beforeSignIn);
        assert (activeUsers == 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSignUpWithFakeEmailMarksTheEmailAsVerified() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("email", "user1.google@@stfakeemail.supertokens.com");
        responseBody.addProperty("password", "validPass123");

        Thread.sleep(1); // add a small delay to ensure a unique timestamp
        long beforeSignIn = System.currentTimeMillis();

        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signup", responseBody, 1000, 1000, null, SemVer.v5_0.get(),
                "emailpassword");

        assertEquals(signInResponse.get("status").getAsString(), "OK");
        assertEquals(signInResponse.entrySet().size(), 3);

        JsonObject jsonUser = signInResponse.get("user").getAsJsonObject();
        assertNotNull(jsonUser.get("id"));
        assertNotNull(jsonUser.get("timeJoined"));
        assert (!jsonUser.get("isPrimaryUser").getAsBoolean());
        assert (jsonUser.get("emails").getAsJsonArray().size() == 1);
        assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString()
                .equals("user1.google@@stfakeemail.supertokens.com"));
        assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
        assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 0);
        assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 1);
        JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
        assertTrue(lM.get("verified").getAsBoolean()); // Email must be verified
        assertNotNull(lM.get("timeJoined"));
        assertNotNull(lM.get("recipeUserId"));
        assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
        assertEquals(lM.get("email").getAsString(), "user1.google@@stfakeemail.supertokens.com");
        assert (lM.entrySet().size() == 6);

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), beforeSignIn);
        assert (activeUsers == 1);

        // double ensure that the email is verified using email verification

        String userId = jsonUser.get("id").getAsString();

        HashMap<String, String> map = new HashMap<>();
        map.put("userId", userId);
        map.put("email", "user1.google@@stfakeemail.supertokens.com");

        JsonObject verifyResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/email/verify", map, 1000, 1000, null,
                SemVer.v2_7.get(), "emailverification");
        assertEquals(verifyResponse.entrySet().size(), 2);
        assertEquals(verifyResponse.get("status").getAsString(), "OK");
        assertTrue(verifyResponse.get("isVerified").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
