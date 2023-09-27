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
import io.supertokens.session.Session;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.webserver.WebserverAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;


public class UnlinkAccountsAPITest {
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
    public void unlinkAccountSuccess() throws Exception {
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

        Session.createNewSession(process.main, user2.getSupertokensUserId(), new JsonObject(), new JsonObject());
        String[] sessions = Session.getAllNonExpiredSessionHandlesForUser(process.main, user2.getSupertokensUserId());
        assert (sessions.length == 1);

        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", user2.getSupertokensUserId());
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/unlink", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("wasRecipeUserDeleted").getAsBoolean());
            assertTrue(response.get("wasLinked").getAsBoolean());
        }


        AuthRecipeUserInfo refetchUser2 = AuthRecipe.getUserById(process.main, user2.getSupertokensUserId());
        assert (!refetchUser2.isPrimaryUser);
        assert (refetchUser2.getSupertokensUserId().equals(user2.getSupertokensUserId()));
        assert (refetchUser2.loginMethods.length == 1);
        assert (refetchUser2.loginMethods[0].getSupertokensUserId().equals(user2.getSupertokensUserId()));

        AuthRecipeUserInfo refetchUser = AuthRecipe.getUserById(process.main, user.getSupertokensUserId());
        assert (!refetchUser2.equals(refetchUser));
        assert (refetchUser.isPrimaryUser);
        assert (refetchUser.loginMethods.length == 1);
        assert (refetchUser.loginMethods[0].getSupertokensUserId().equals(user.getSupertokensUserId()));

        // cause linkAccounts revokes sessions for the recipe user ID
        sessions = Session.getAllNonExpiredSessionHandlesForUser(process.main, user2.getSupertokensUserId());
        assert (sessions.length == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }


    @Test
    public void unlinkAccountBadRequest() throws Exception {
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

        try {
            JsonObject params = new JsonObject();
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/unlink", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assert (false);
        } catch (HttpResponseException e) {
            assert (e.statusCode == 400);
            assert (e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'recipeUserId' is invalid in JSON " +
                            "input"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void unlinkAccountSuccessWithUserIdMapping() throws Exception {
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
        UserIdMapping.createUserIdMapping(process.main, user2.getSupertokensUserId(), "e2", null, false);

        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(), user.getSupertokensUserId());

        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", "e2");
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/unlink", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("wasRecipeUserDeleted").getAsBoolean());
            assertTrue(response.get("wasLinked").getAsBoolean());
        }


        AuthRecipeUserInfo refetchUser2 = AuthRecipe.getUserById(process.main, user2.getSupertokensUserId());
        assert (!refetchUser2.isPrimaryUser);
        assert (refetchUser2.getSupertokensUserId().equals(user2.getSupertokensUserId()));
        assert (refetchUser2.loginMethods.length == 1);
        assert (refetchUser2.loginMethods[0].getSupertokensUserId().equals(user2.getSupertokensUserId()));

        AuthRecipeUserInfo refetchUser = AuthRecipe.getUserById(process.main, user.getSupertokensUserId());
        assert (!refetchUser2.equals(refetchUser));
        assert (refetchUser.isPrimaryUser);
        assert (refetchUser.loginMethods.length == 1);
        assert (refetchUser.loginMethods[0].getSupertokensUserId().equals(user.getSupertokensUserId()));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void unlinkAccountWithoutPrimaryUserId() throws Exception {
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

        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", user.getSupertokensUserId());
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/unlink", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("wasRecipeUserDeleted").getAsBoolean());
            assertFalse(response.get("wasLinked").getAsBoolean());
        }


        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void unlinkAccountWithUnknownUserId() throws Exception {
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

        try {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", "random");
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/unlink", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assert (false);
        } catch (HttpResponseException e) {
            assert (e.statusCode == 400);
            assert (e.getMessage().equals("Http error. Status Code: 400. Message: Unknown user ID provided"));
        }


        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void unlinkAccountSuccessButDeletesUser() throws Exception {
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
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", user.getSupertokensUserId());
            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/unlink", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("wasRecipeUserDeleted").getAsBoolean());
            assertTrue(response.get("wasLinked").getAsBoolean());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
