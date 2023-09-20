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

package io.supertokens.test.thirdparty.api;

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
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;


public class ThirdPartySignInUpAPITest4_0 {

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

    // good input
    // failure condition: test fails if signinup response does not match api spec
    @Test
    public void testGoodInput() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long startTs = System.currentTimeMillis();

        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("id", "test@example.com");
        emailObject.addProperty("isVerified", false);

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("thirdPartyId", "google");
        signUpRequestBody.addProperty("thirdPartyUserId", "google-user");
        signUpRequestBody.add("email", emailObject);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup", signUpRequestBody, 1000, 1000, null,
                SemVer.v4_0.get(), "thirdparty");

        {
            assert (response.get("status").getAsString().equals("OK"));
            assert (response.get("createdNewUser").getAsBoolean());
            JsonObject jsonUser = response.get("user").getAsJsonObject();
            assertNotNull(jsonUser.get("id"));
            assertNotNull(jsonUser.get("timeJoined"));
            assert (!jsonUser.get("isPrimaryUser").getAsBoolean());
            assert (jsonUser.get("emails").getAsJsonArray().size() == 1);
            assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("test@example.com"));
            assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
            assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 1);
            assert (jsonUser.get("thirdParty").getAsJsonArray().get(0).getAsJsonObject().get("id").getAsString()
                    .equals("google"));
            assert (jsonUser.get("thirdParty").getAsJsonArray().get(0).getAsJsonObject().get("userId").getAsString()
                    .equals("google-user"));
            assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 1);
            JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
            assertFalse(lM.get("verified").getAsBoolean());
            assertNotNull(lM.get("timeJoined"));
            assertNotNull(lM.get("recipeUserId"));
            assertEquals(lM.get("recipeId").getAsString(), "thirdparty");
            assertEquals(lM.get("email").getAsString(), "test@example.com");
            assert (lM.get("thirdParty").getAsJsonObject().get("id").getAsString()
                    .equals("google"));
            assert (lM.get("thirdParty").getAsJsonObject().get("userId").getAsString()
                    .equals("google-user"));
            assert (lM.entrySet().size() == 7);
        }

        int activeUsers = ActiveUsers.countUsersActiveSince(process.getProcess(), startTs);
        assert (activeUsers == 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testNotAllowedUpdateOfEmail() throws Exception {
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

        AuthRecipeUserInfo user0 = EmailPassword.signUp(process.getProcess(), "someemail1@gmail.com", "somePass");
        AuthRecipe.createPrimaryUser(process.main, user0.getSupertokensUserId());

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.getProcess(), "google", "user",
                "someemail@gmail.com");
        AuthRecipe.createPrimaryUser(process.main, signInUpResponse.user.getSupertokensUserId());

        JsonObject emailObject = new JsonObject();
        emailObject.addProperty("id", "someemail1@gmail.com");
        emailObject.addProperty("isVerified", false);

        JsonObject signUpRequestBody = new JsonObject();
        signUpRequestBody.addProperty("thirdPartyId", "google");
        signUpRequestBody.addProperty("thirdPartyUserId", "user");
        signUpRequestBody.add("email", emailObject);

        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/signinup", signUpRequestBody, 1000, 1000, null,
                SemVer.v4_0.get(), "thirdparty");

        assert (response.get("status").getAsString().equals("EMAIL_CHANGE_NOT_ALLOWED_ERROR"));
        assert (response.get("reason").getAsString().equals("Email already associated with another primary user."));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
