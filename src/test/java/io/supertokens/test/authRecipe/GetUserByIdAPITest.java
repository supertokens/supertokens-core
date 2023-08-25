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

package io.supertokens.test.authRecipe;

import com.google.gson.JsonObject;
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
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.webserver.WebserverAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class GetUserByIdAPITest {
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
    public void getUserSuccess() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        assert (!user.isPrimaryUser);

        Thread.sleep(50);

        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        assert (!user2.isPrimaryUser);

        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(), user.getSupertokensUserId());

        {
            Map<String, String> params = new HashMap<>();
            params.put("userId", user2.getSupertokensUserId());
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            JsonObject jsonUser = response.get("user").getAsJsonObject();
            assert (jsonUser.get("id").getAsString().equals(user.getSupertokensUserId()));
            assert (jsonUser.get("timeJoined").getAsLong() == user.timeJoined);
            assert (jsonUser.get("isPrimaryUser").getAsBoolean());
            assert (jsonUser.get("emails").getAsJsonArray().size() == 2);
            assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("test@example.com") ||
                    jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("test2@example.com"));
            assert (jsonUser.get("emails").getAsJsonArray().get(1).getAsString().equals("test@example.com") ||
                    jsonUser.get("emails").getAsJsonArray().get(1).getAsString().equals("test2@example.com"));
            assert (!jsonUser.get("emails").getAsJsonArray().get(1).getAsString()
                    .equals(jsonUser.get("emails").getAsJsonArray().get(0).getAsString()));
            assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
            assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 0);
            assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 2);
            {
                JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
                assertFalse(lM.get("verified").getAsBoolean());
                assertEquals(lM.get("timeJoined").getAsLong(), user.timeJoined);
                assertEquals(lM.get("recipeUserId").getAsString(), user.getSupertokensUserId());
                assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
                assertEquals(lM.get("email").getAsString(), "test@example.com");
                assert (lM.entrySet().size() == 6);
            }
            {
                JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(1).getAsJsonObject();
                assertFalse(lM.get("verified").getAsBoolean());
                assertEquals(lM.get("timeJoined").getAsLong(), user2.timeJoined);
                assertEquals(lM.get("recipeUserId").getAsString(), user2.getSupertokensUserId());
                assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
                assertEquals(lM.get("email").getAsString(), "test2@example.com");
                assert (lM.entrySet().size() == 6);
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void getUserSuccessWithUserIdMapping() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        assert (!user.isPrimaryUser);
        UserIdMapping.createUserIdMapping(process.main, user.getSupertokensUserId(), "e1", null, false);

        Thread.sleep(50);

        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        assert (!user2.isPrimaryUser);
        UserIdMapping.createUserIdMapping(process.main, user2.getSupertokensUserId(), "e2", null, false);

        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(), user.getSupertokensUserId());

        {
            Map<String, String> params = new HashMap<>();
            params.put("userId", "e2");
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            JsonObject jsonUser = response.get("user").getAsJsonObject();
            assert (jsonUser.get("id").getAsString().equals("e1"));
            assert (jsonUser.get("timeJoined").getAsLong() == user.timeJoined);
            assert (jsonUser.get("isPrimaryUser").getAsBoolean());
            assert (jsonUser.get("emails").getAsJsonArray().size() == 2);
            assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("test@example.com") ||
                    jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("test2@example.com"));
            assert (jsonUser.get("emails").getAsJsonArray().get(1).getAsString().equals("test@example.com") ||
                    jsonUser.get("emails").getAsJsonArray().get(1).getAsString().equals("test2@example.com"));
            assert (!jsonUser.get("emails").getAsJsonArray().get(1).getAsString()
                    .equals(jsonUser.get("emails").getAsJsonArray().get(0).getAsString()));
            assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
            assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 0);
            assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 2);
            {
                JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
                assertFalse(lM.get("verified").getAsBoolean());
                assertEquals(lM.get("timeJoined").getAsLong(), user.timeJoined);
                assertEquals(lM.get("recipeUserId").getAsString(), "e1");
                assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
                assertEquals(lM.get("email").getAsString(), "test@example.com");
                assert (lM.entrySet().size() == 6);
            }
            {
                JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(1).getAsJsonObject();
                assertFalse(lM.get("verified").getAsBoolean());
                assertEquals(lM.get("timeJoined").getAsLong(), user2.timeJoined);
                assertEquals(lM.get("recipeUserId").getAsString(), "e2");
                assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
                assertEquals(lM.get("email").getAsString(), "test2@example.com");
                assert (lM.entrySet().size() == 6);
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void getUserSuccess2() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        assert (!user.isPrimaryUser);

        Thread.sleep(50);


        ThirdParty.SignInUpResponse signInUpRespone = ThirdParty.signInUp(process.getProcess(), "google", "google-user",
                "test@example.com");
        AuthRecipeUserInfo user2 = signInUpRespone.user;
        assert (!user2.isPrimaryUser);

        AuthRecipe.createPrimaryUser(process.main, user2.getSupertokensUserId());

        AuthRecipe.linkAccounts(process.main, user.getSupertokensUserId(), user2.getSupertokensUserId());

        {
            Map<String, String> params = new HashMap<>();
            params.put("userId", user2.getSupertokensUserId());
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            JsonObject jsonUser = response.get("user").getAsJsonObject();
            assert (jsonUser.get("id").getAsString().equals(user2.getSupertokensUserId()));
            assert (jsonUser.get("timeJoined").getAsLong() == user.timeJoined);
            assert (jsonUser.get("isPrimaryUser").getAsBoolean());
            assert (jsonUser.get("emails").getAsJsonArray().size() == 1);
            assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("test@example.com"));
            assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
            assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 1);
            assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 2);
            {
                JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
                assertFalse(lM.get("verified").getAsBoolean());
                assertEquals(lM.get("timeJoined").getAsLong(), user.timeJoined);
                assertEquals(lM.get("recipeUserId").getAsString(), user.getSupertokensUserId());
                assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
                assertEquals(lM.get("email").getAsString(), "test@example.com");
                assert (lM.entrySet().size() == 6);
            }
            {
                JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(1).getAsJsonObject();
                assertFalse(lM.get("verified").getAsBoolean());
                assertEquals(lM.get("timeJoined").getAsLong(), user2.timeJoined);
                assertEquals(lM.get("recipeUserId").getAsString(), user2.getSupertokensUserId());
                assertEquals(lM.get("recipeId").getAsString(), "thirdparty");
                assertEquals(lM.get("email").getAsString(), "test@example.com");
                assert (lM.entrySet().size() == 7);
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void getUnknownUser() throws Exception {
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

        {
            Map<String, String> params = new HashMap<>();
            params.put("userId", "random");
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(1, response.entrySet().size());
            assertEquals("UNKNOWN_USER_ID_ERROR", response.get("status").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
