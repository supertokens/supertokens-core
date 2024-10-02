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

package io.supertokens.test.accountlinking.api;

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
import io.supertokens.test.httpRequest.HttpResponseException;
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

public class CanLinkAccountsAPITest {
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
    public void canLinkReturnsTrue() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "abcd1234");

        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "abcd1234");

        AuthRecipe.createPrimaryUser(process.main, user2.getSupertokensUserId());

        {
            Map<String, String> params = new HashMap<>();
            params.put("recipeUserId", user.getSupertokensUserId());
            params.put("primaryUserId", user2.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/link/check", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("accountsAlreadyLinked").getAsBoolean());
        }

        AuthRecipe.linkAccounts(process.main, user.getSupertokensUserId(), user2.getSupertokensUserId());

        {
            Map<String, String> params = new HashMap<>();
            params.put("recipeUserId", user.getSupertokensUserId());
            params.put("primaryUserId", user2.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/link/check", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("accountsAlreadyLinked").getAsBoolean());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void canLinkReturnsTrueWithUserIdMapping() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "abcd1234");
        UserIdMapping.createUserIdMapping(process.main, user.getSupertokensUserId(), "r1", null, false);

        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "abcd1234");
        UserIdMapping.createUserIdMapping(process.main, user2.getSupertokensUserId(), "r2", null, false);

        AuthRecipe.createPrimaryUser(process.main, user2.getSupertokensUserId());

        {
            Map<String, String> params = new HashMap<>();
            params.put("recipeUserId", "r1");
            params.put("primaryUserId", "r2");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/link/check", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("accountsAlreadyLinked").getAsBoolean());
        }

        AuthRecipe.linkAccounts(process.main, user.getSupertokensUserId(), user2.getSupertokensUserId());

        {
            Map<String, String> params = new HashMap<>();
            params.put("recipeUserId", "r1");
            params.put("primaryUserId", "r2");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/link/check", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(2, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("accountsAlreadyLinked").getAsBoolean());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void canLinkUserBadInput() throws Exception {
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

            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/accountlinking/user/link/check", params, 1000, 1000, null,
                        WebserverAPI.getLatestCDIVersion().get(), "");
                assert (false);
            } catch (HttpResponseException e) {
                assert (e.statusCode == 400);
                assert (e.getMessage()
                        .equals("Http error. Status Code: 400. Message: Field name 'recipeUserId' is missing in GET " +
                                "request"));
            }
        }

        {
            Map<String, String> params = new HashMap<>();
            params.put("recipeUserId", "random");

            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/accountlinking/user/link/check", params, 1000, 1000, null,
                        WebserverAPI.getLatestCDIVersion().get(), "");
                assert (false);
            } catch (HttpResponseException e) {
                assert (e.statusCode == 400);
                assert (e.getMessage()
                        .equals("Http error. Status Code: 400. Message: Field name 'primaryUserId' is missing in GET " +
                                "request"));
            }
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "abcd1234");
        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "abcd1234");

        {
            Map<String, String> params = new HashMap<>();
            params.put("recipeUserId", user2.getSupertokensUserId());
            params.put("primaryUserId", "random");

            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/accountlinking/user/link/check", params, 1000, 1000, null,
                        WebserverAPI.getLatestCDIVersion().get(), "");
                assert (false);
            } catch (HttpResponseException e) {
                assert (e.statusCode == 400);
                assert (e.getMessage()
                        .equals("Http error. Status Code: 400. Message: Unknown user ID provided"));
            }
        }

        {
            Map<String, String> params = new HashMap<>();
            params.put("recipeUserId", "random");
            params.put("primaryUserId", user.getSupertokensUserId());

            try {
                HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/accountlinking/user/link/check", params, 1000, 1000, null,
                        WebserverAPI.getLatestCDIVersion().get(), "");
                assert (false);
            } catch (HttpResponseException e) {
                assert (e.statusCode == 400);
                assert (e.getMessage()
                        .equals("Http error. Status Code: 400. Message: Unknown user ID provided"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkingUsersFailsCauseAnotherAccountWithSameEmailAlreadyAPrimaryUser() throws Exception {
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

        AuthRecipeUserInfo emailPasswordUser = EmailPassword.signUp(process.getProcess(), "test@example.com",
                "pass1234");

        AuthRecipe.CreatePrimaryUserResult result = AuthRecipe.createPrimaryUser(process.main,
                emailPasswordUser.getSupertokensUserId());
        assert (!result.wasAlreadyAPrimaryUser);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.main, "google", "user-google",
                "test2@example.com");

        AuthRecipe.createPrimaryUser(process.main, signInUpResponse.user.getSupertokensUserId());

        ThirdParty.SignInUpResponse signInUpResponse2 = ThirdParty.signInUp(process.main, "fb", "user-fb",
                "test@example.com");


        {
            Map<String, String> params = new HashMap<>();
            params.put("primaryUserId", signInUpResponse.user.getSupertokensUserId());
            params.put("recipeUserId", signInUpResponse2.user.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/link/check", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("ACCOUNT_INFO_ALREADY_ASSOCIATED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR",
                    response.get("status").getAsString());
            assertEquals(emailPasswordUser.getSupertokensUserId(), response.get("primaryUserId").getAsString());
            assertEquals("This user's email is already associated with another user ID",
                    response.get("description").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkingUsersFailsCauseAnotherAccountWithSameEmailAlreadyAPrimaryUserWithUserIdMapping()
            throws Exception {
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

        AuthRecipeUserInfo emailPasswordUser = EmailPassword.signUp(process.getProcess(), "test@example.com",
                "pass1234");
        UserIdMapping.createUserIdMapping(process.main, emailPasswordUser.getSupertokensUserId(), "e1", null, false);

        AuthRecipe.CreatePrimaryUserResult result = AuthRecipe.createPrimaryUser(process.main,
                emailPasswordUser.getSupertokensUserId());
        assert (!result.wasAlreadyAPrimaryUser);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.main, "google", "user-google",
                "test2@example.com");
        UserIdMapping.createUserIdMapping(process.main, signInUpResponse.user.getSupertokensUserId(), "e2", null,
                false);

        AuthRecipe.createPrimaryUser(process.main, signInUpResponse.user.getSupertokensUserId());

        ThirdParty.SignInUpResponse signInUpResponse2 = ThirdParty.signInUp(process.main, "fb", "user-fb",
                "test@example.com");
        UserIdMapping.createUserIdMapping(process.main, signInUpResponse2.user.getSupertokensUserId(), "e3", null,
                false);


        {
            Map<String, String> params = new HashMap<>();
            params.put("primaryUserId", "e2");
            params.put("recipeUserId", "e3");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/link/check", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("ACCOUNT_INFO_ALREADY_ASSOCIATED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR",
                    response.get("status").getAsString());
            assertEquals("e1", response.get("primaryUserId").getAsString());
            assertEquals("This user's email is already associated with another user ID",
                    response.get("description").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkingUserFailsCauseAlreadyLinkedToAnotherAccount() throws Exception {
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

        AuthRecipeUserInfo emailPasswordUser1 = EmailPassword.signUp(process.getProcess(), "test@example.com",
                "pass1234");
        AuthRecipeUserInfo emailPasswordUser2 = EmailPassword.signUp(process.getProcess(), "test2@example.com",
                "pass1234");

        AuthRecipe.createPrimaryUser(process.main, emailPasswordUser1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.main, emailPasswordUser2.getSupertokensUserId(),
                emailPasswordUser1.getSupertokensUserId());

        AuthRecipeUserInfo emailPasswordUser3 = EmailPassword.signUp(process.getProcess(), "test3@example.com",
                "pass1234");

        AuthRecipe.createPrimaryUser(process.main, emailPasswordUser3.getSupertokensUserId());

        {
            Map<String, String> params = new HashMap<>();
            params.put("recipeUserId", emailPasswordUser2.getSupertokensUserId());
            params.put("primaryUserId", emailPasswordUser3.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/link/check", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("RECIPE_USER_ID_ALREADY_LINKED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR",
                    response.get("status").getAsString());
            assertEquals(emailPasswordUser1.getSupertokensUserId(), response.get("primaryUserId").getAsString());
            assertEquals("The input recipe user ID is already linked to another user ID",
                    response.get("description").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void makingPrimaryUserFailsCauseAlreadyLinkedToAnotherAccountWithUserIdMapping() throws Exception {
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

        AuthRecipeUserInfo emailPasswordUser1 = EmailPassword.signUp(process.getProcess(), "test@example.com",
                "pass1234");
        UserIdMapping.createUserIdMapping(process.main, emailPasswordUser1.getSupertokensUserId(), "r1", null, false);
        AuthRecipeUserInfo emailPasswordUser2 = EmailPassword.signUp(process.getProcess(), "test2@example.com",
                "pass1234");
        UserIdMapping.createUserIdMapping(process.main, emailPasswordUser2.getSupertokensUserId(), "r2", null, false);

        AuthRecipe.createPrimaryUser(process.main, emailPasswordUser1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.main, emailPasswordUser2.getSupertokensUserId(),
                emailPasswordUser1.getSupertokensUserId());

        AuthRecipeUserInfo emailPasswordUser3 = EmailPassword.signUp(process.getProcess(), "test3@example.com",
                "pass1234");
        UserIdMapping.createUserIdMapping(process.main, emailPasswordUser3.getSupertokensUserId(), "r3", null, false);

        AuthRecipe.createPrimaryUser(process.main, emailPasswordUser3.getSupertokensUserId());

        {
            Map<String, String> params = new HashMap<>();
            params.put("recipeUserId", "r2");
            params.put("primaryUserId", "r3");

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/link/check", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("RECIPE_USER_ID_ALREADY_LINKED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR",
                    response.get("status").getAsString());
            assertEquals("r1", response.get("primaryUserId").getAsString());
            assertEquals("The input recipe user ID is already linked to another user ID",
                    response.get("description").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void inputUserIsNotAPrimaryUserTest() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "abcd1234");

        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "abcd1234");

        {
            Map<String, String> params = new HashMap<>();
            params.put("recipeUserId", user.getSupertokensUserId());
            params.put("primaryUserId", user2.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/link/check", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(1, response.entrySet().size());
            assertEquals("INPUT_USER_IS_NOT_A_PRIMARY_USER", response.get("status").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
