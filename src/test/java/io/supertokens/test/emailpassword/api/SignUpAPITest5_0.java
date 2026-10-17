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
import io.supertokens.cronjobs.rollupUserLastActive.RollupUserLastActive;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.useridmapping.UserIdMapping;
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

        // PLAN-011 cutover: fold emitted activity into the projection before reading the count.
        RollupUserLastActive.runOnceForAllStoragesForTesting(process.getProcess());
        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), beforeSignIn);
        assert (activeUsers == 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSignUpWithPlaceholderEmailOfExistingUserMarksTheEmailAsVerified() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // The placeholder the SDKs generate when a user without an email adds a password factor:
        // <existing user id>@stfakeemail.supertokens.com
        AuthRecipeUserInfo existingUser = EmailPassword.signUp(process.getProcess(), "existing@example.com",
                "validPass123");
        String placeholderEmail = existingUser.getSupertokensUserId() + "@stfakeemail.supertokens.com";

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("email", placeholderEmail);
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
                .equals(placeholderEmail));
        assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
        assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 0);
        assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 1);
        JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
        assertTrue(lM.get("verified").getAsBoolean()); // Email must be verified
        assertNotNull(lM.get("timeJoined"));
        assertNotNull(lM.get("recipeUserId"));
        assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
        assertEquals(lM.get("email").getAsString(), placeholderEmail);
        assert (lM.entrySet().size() == 6);

        // PLAN-011 cutover: fold emitted activity into the projection before reading the count.
        RollupUserLastActive.runOnceForAllStoragesForTesting(process.getProcess());
        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), beforeSignIn);
        assert (activeUsers == 1);

        // double ensure that the email is verified using email verification

        String userId = jsonUser.get("id").getAsString();

        HashMap<String, String> map = new HashMap<>();
        map.put("userId", userId);
        map.put("email", placeholderEmail);

        JsonObject verifyResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/user/email/verify", map, 1000, 1000, null,
                SemVer.v2_7.get(), "emailverification");
        assertEquals(verifyResponse.entrySet().size(), 2);
        assertEquals(verifyResponse.get("status").getAsString(), "OK");
        assertTrue(verifyResponse.get("isVerified").getAsBoolean());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static JsonObject signUpAndGetLoginMethod(TestingProcessManager.TestingProcess process, String email)
            throws Exception {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("email", email);
        requestBody.addProperty("password", "validPass123");
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signup", requestBody, 1000, 1000, null, SemVer.v5_0.get(),
                "emailpassword");
        assertEquals("OK", response.get("status").getAsString());
        JsonObject user = response.get("user").getAsJsonObject();
        assertEquals(1, user.get("loginMethods").getAsJsonArray().size());
        return user.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
    }

    private static void assertSignUpVerified(TestingProcessManager.TestingProcess process, String email,
                                             boolean expectedVerified) throws Exception {
        JsonObject lM = signUpAndGetLoginMethod(process, email);
        String normalisedEmail = io.supertokens.utils.Utils.normaliseEmail(email);
        assertEquals(normalisedEmail, lM.get("email").getAsString());
        assertEquals(email, expectedVerified, lM.get("verified").getAsBoolean());
        // the stored verification state must match what sign-up returned
        assertEquals(email, expectedVerified, EmailVerification.isEmailVerified(process.getProcess(),
                lM.get("recipeUserId").getAsString(), normalisedEmail));
    }

    @Test
    public void testIssue107_signUpDoesNotAutoVerifyAttackerChosenFakeEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // A third-party style placeholder whose third-party user does not exist (yet) is not verified
        assertSignUpVerified(process, "victimtwitterid.twitter@stfakeemail.supertokens.com", false);

        // Nor is it verified when the third-party user holding that placeholder exists: core cannot tell who is
        // signing up, and the SDK links it to its owner through the session user's own verified login method.
        ThirdParty.signInUp(process.getProcess(), "google", "tpuser1", "tpuser1.google@stfakeemail.supertokens.com");
        assertSignUpVerified(process, "tpuser1.google@stfakeemail.supertokens.com", false);

        // Addresses on *.fakeemail.com (a domain SuperTokens does not control) are not verified
        assertSignUpVerified(process, "someone@attacker.fakeemail.com", false);
        assertSignUpVerified(process, "victimid@twitter.fakeemail.com", false);

        // A placeholder on the SuperTokens domain whose local part is not a user id of this app is not verified
        assertSignUpVerified(process, java.util.UUID.randomUUID() + "@stfakeemail.supertokens.com", false);
        assertSignUpVerified(process, "@stfakeemail.supertokens.com", false);

        // <existing user id>@stfakeemail.supertokens.com (the SDK's password-factor placeholder) is still verified
        AuthRecipeUserInfo existingUser = EmailPassword.signUp(process.getProcess(), "existing@example.com",
                "validPass123");
        assertSignUpVerified(process, existingUser.getSupertokensUserId() + "@stfakeemail.supertokens.com", true);

        // ... also when the existing user's id is a mapped external user id, including a mixed-case one
        AuthRecipeUserInfo mappedUser = EmailPassword.signUp(process.getProcess(), "mapped@example.com",
                "validPass123");
        UserIdMapping.createUserIdMapping(process.getProcess(), mappedUser.getSupertokensUserId(), "ExtUser-AbC",
                null, false);
        assertSignUpVerified(process, "ExtUser-AbC@stfakeemail.supertokens.com", true);

        // ... but not for the same external id with a different case, which is not a user id of this app
        AuthRecipeUserInfo mappedUser2 = EmailPassword.signUp(process.getProcess(), "mapped2@example.com",
                "validPass123");
        UserIdMapping.createUserIdMapping(process.getProcess(), mappedUser2.getSupertokensUserId(), "OtherExt",
                null, false);
        assertSignUpVerified(process, "OTHEREXT@stfakeemail.supertokens.com", false);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
