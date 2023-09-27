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
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;


public class SignInAPITest4_0 {

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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.main, "random@gmail.com", "validPass123");

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("email", "random@gmail.com");
        responseBody.addProperty("password", "validPass123");

        Thread.sleep(1); // add a small delay to ensure a unique timestamp
        long beforeSignIn = System.currentTimeMillis();

        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signin", responseBody, 1000, 1000, null, SemVer.v4_0.get(),
                "emailpassword");

        assertEquals(signInResponse.get("status").getAsString(), "OK");
        assertEquals(signInResponse.entrySet().size(), 3);

        JsonObject jsonUser = signInResponse.get("user").getAsJsonObject();
        assert (jsonUser.get("id").getAsString().equals(user.getSupertokensUserId()));
        assert (jsonUser.get("timeJoined").getAsLong() == user.timeJoined);
        assert (!jsonUser.get("isPrimaryUser").getAsBoolean());
        assert (jsonUser.get("emails").getAsJsonArray().size() == 1);
        assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("random@gmail.com"));
        assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
        assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 0);
        assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 1);
        JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
        assertFalse(lM.get("verified").getAsBoolean());
        assertEquals(lM.get("timeJoined").getAsLong(), user.timeJoined);
        assertEquals(lM.get("recipeUserId").getAsString(), user.getSupertokensUserId());
        assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
        assertEquals(lM.get("email").getAsString(), "random@gmail.com");
        assert (lM.entrySet().size() == 6);

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), beforeSignIn);
        assert (activeUsers == 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGoodInputWithUserIdMapping() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.main, "random@gmail.com", "validPass123");
        UserIdMapping.createUserIdMapping(process.main, user.getSupertokensUserId(), "e1", null, false);

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("email", "random@gmail.com");
        responseBody.addProperty("password", "validPass123");

        Thread.sleep(1); // add a small delay to ensure a unique timestamp
        long beforeSignIn = System.currentTimeMillis();

        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signin", responseBody, 1000, 1000, null, SemVer.v4_0.get(),
                "emailpassword");

        assertEquals(signInResponse.get("status").getAsString(), "OK");
        assertEquals(signInResponse.entrySet().size(), 3);

        JsonObject jsonUser = signInResponse.get("user").getAsJsonObject();
        assert (jsonUser.get("id").getAsString().equals("e1"));
        assert (jsonUser.get("timeJoined").getAsLong() == user.timeJoined);
        assert (!jsonUser.get("isPrimaryUser").getAsBoolean());
        assert (jsonUser.get("emails").getAsJsonArray().size() == 1);
        assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("random@gmail.com"));
        assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
        assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 0);
        assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 1);
        JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
        assertFalse(lM.get("verified").getAsBoolean());
        assertEquals(lM.get("timeJoined").getAsLong(), user.timeJoined);
        assertEquals(lM.get("recipeUserId").getAsString(), "e1");
        assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
        assertEquals(lM.get("email").getAsString(), "random@gmail.com");
        assert (lM.entrySet().size() == 6);

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), beforeSignIn);
        assert (activeUsers == 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGoodInputWithUserIdMappingAndMultipleLinkedAccounts() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user0 = EmailPassword.signUp(process.main, "random1@gmail.com", "validPass123");
        UserIdMapping.createUserIdMapping(process.main, user0.getSupertokensUserId(), "e0", null, false);

        Thread.sleep(1); // add a small delay to ensure a unique timestamp

        AuthRecipeUserInfo user = EmailPassword.signUp(process.main, "random@gmail.com", "validPass123");
        UserIdMapping.createUserIdMapping(process.main, user.getSupertokensUserId(), "e1", null, false);
        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.main, user0.getSupertokensUserId(), user.getSupertokensUserId());

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("email", "random@gmail.com");
        responseBody.addProperty("password", "validPass123");

        Thread.sleep(1); // add a small delay to ensure a unique timestamp
        long beforeSignIn = System.currentTimeMillis();

        JsonObject signInResponse = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signin", responseBody, 1000, 1000, null, SemVer.v4_0.get(),
                "emailpassword");

        assertEquals(signInResponse.get("status").getAsString(), "OK");
        assertEquals(signInResponse.entrySet().size(), 3);

        JsonObject jsonUser = signInResponse.get("user").getAsJsonObject();
        assert (jsonUser.get("id").getAsString().equals("e1"));
        assert (jsonUser.get("timeJoined").getAsLong() == user0.timeJoined);
        assert (jsonUser.get("isPrimaryUser").getAsBoolean());
        assert (jsonUser.get("emails").getAsJsonArray().size() == 2);
        assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("random@gmail.com") ||
                jsonUser.get("emails").getAsJsonArray().get(1).getAsString().equals("random@gmail.com"));
        assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("random1@gmail.com") ||
                jsonUser.get("emails").getAsJsonArray().get(1).getAsString().equals("random1@gmail.com"));
        assert (!jsonUser.get("emails").getAsJsonArray().get(0).getAsString()
                .equals(jsonUser.get("emails").getAsJsonArray().get(1).getAsString()));
        assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
        assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 0);
        assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 2);
        {
            JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(1).getAsJsonObject();
            assertFalse(lM.get("verified").getAsBoolean());
            assertEquals(lM.get("timeJoined").getAsLong(), user.timeJoined);
            assertEquals(lM.get("recipeUserId").getAsString(), "e1");
            assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
            assertEquals(lM.get("email").getAsString(), "random@gmail.com");
            assert (lM.entrySet().size() == 6);
        }
        {
            JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
            assertFalse(lM.get("verified").getAsBoolean());
            assertEquals(lM.get("timeJoined").getAsLong(), user0.timeJoined);
            assertEquals(lM.get("recipeUserId").getAsString(), "e0");
            assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
            assertEquals(lM.get("email").getAsString(), "random1@gmail.com");
            assert (lM.entrySet().size() == 6);
        }

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), beforeSignIn);
        assert (activeUsers == 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
