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

package io.supertokens.test.mfa.api;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.*;
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

public class CreatePrimaryUserAPITest {
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
    public void createReturnsSucceeds() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MFA, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "abcd1234");

        JsonObject userObj;
        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", user.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/primary", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("wasAlreadyAPrimaryUser").getAsBoolean());

            // check user object
            JsonObject jsonUser = response.get("user").getAsJsonObject();
            assert (jsonUser.get("id").getAsString().equals(user.getSupertokensUserId()));
            assert (jsonUser.get("timeJoined").getAsLong() == user.timeJoined);
            assert (jsonUser.get("isPrimaryUser").getAsBoolean());
            assert (jsonUser.get("emails").getAsJsonArray().size() == 1);
            assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("test@example.com"));
            assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
            assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 0);
            assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 1);
            JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
            assertFalse(lM.get("verified").getAsBoolean());
            assertEquals(lM.get("timeJoined").getAsLong(), user.timeJoined);
            assertEquals(lM.get("recipeUserId").getAsString(), user.getSupertokensUserId());
            assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
            assertEquals(lM.get("email").getAsString(), "test@example.com");
            assert (lM.entrySet().size() == 6);
            userObj = jsonUser;
        }

        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", user.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/primary", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("wasAlreadyAPrimaryUser").getAsBoolean());
            assertEquals(response.get("user"), userObj);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createReturnsTrueWithUserIdMapping() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MFA, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "abcd1234");
        UserIdMapping.createUserIdMapping(process.main, user.getSupertokensUserId(), "r1", null, false);

        JsonObject userObj;
        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", "r1");

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/primary", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("wasAlreadyAPrimaryUser").getAsBoolean());
            // check user object
            JsonObject jsonUser = response.get("user").getAsJsonObject();
            assert (jsonUser.get("id").getAsString().equals("r1"));
            assert (jsonUser.get("timeJoined").getAsLong() == user.timeJoined);
            assert (jsonUser.get("isPrimaryUser").getAsBoolean());
            assert (jsonUser.get("emails").getAsJsonArray().size() == 1);
            assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("test@example.com"));
            assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
            assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 0);
            assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 1);
            JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
            assertFalse(lM.get("verified").getAsBoolean());
            assertEquals(lM.get("timeJoined").getAsLong(), user.timeJoined);
            assertEquals(lM.get("recipeUserId").getAsString(), "r1");
            assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
            assertEquals(lM.get("email").getAsString(), "test@example.com");
            assert (lM.entrySet().size() == 6);
            userObj = jsonUser;
        }

        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", "r1");

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/primary", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("wasAlreadyAPrimaryUser").getAsBoolean());
            assertEquals(response.get("user"), userObj);
        }

        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", user.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/primary", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("wasAlreadyAPrimaryUser").getAsBoolean());
            assertEquals(response.get("user"), userObj);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createPrimaryUserBadInput() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MFA, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            Map<String, String> params = new HashMap<>();

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/accountlinking/user/primary", new JsonObject(), 1000, 1000, null,
                        WebserverAPI.getLatestCDIVersion().get(), "");
                assert (false);
            } catch (HttpResponseException e) {
                assert (e.statusCode == 400);
                assert (e.getMessage()
                        .equals("Http error. Status Code: 400. Message: Field name 'recipeUserId' is invalid in JSON " +
                                "input"));
            }
        }

        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", "random");

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/accountlinking/user/primary", params, 1000, 1000, null,
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
    public void makePrimaryUserFailsCauseAnotherAccountWithSameEmailAlreadyAPrimaryUser() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MFA, EE_FEATURES.MULTI_TENANCY});
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
                "test@example.com");

        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", signInUpResponse.user.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/primary", params, 1000, 1000, null,
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
    public void makingPrimaryUserFailsCauseAlreadyLinkedToAnotherAccount() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MFA, EE_FEATURES.MULTI_TENANCY});
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

        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", emailPasswordUser2.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/primary", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("RECIPE_USER_ID_ALREADY_LINKED_WITH_PRIMARY_USER_ID_ERROR",
                    response.get("status").getAsString());
            assertEquals(emailPasswordUser1.getSupertokensUserId(), response.get("primaryUserId").getAsString());
            assertEquals("This user ID is already linked to another user ID",
                    response.get("description").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void makePrimaryUserFailsCauseAnotherAccountWithSameEmailAlreadyAPrimaryUserWithUserIdMapping()
            throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MFA, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo emailPasswordUser = EmailPassword.signUp(process.getProcess(), "test@example.com",
                "pass1234");
        UserIdMapping.createUserIdMapping(process.main, emailPasswordUser.getSupertokensUserId(), "r1", null, false);

        AuthRecipe.CreatePrimaryUserResult result = AuthRecipe.createPrimaryUser(process.main,
                emailPasswordUser.getSupertokensUserId());
        assert (!result.wasAlreadyAPrimaryUser);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.main, "google", "user-google",
                "test@example.com");

        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", signInUpResponse.user.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/primary", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("ACCOUNT_INFO_ALREADY_ASSOCIATED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR",
                    response.get("status").getAsString());
            assertEquals("r1", response.get("primaryUserId").getAsString());
            assertEquals("This user's email is already associated with another user ID",
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
                        EE_FEATURES.MFA, EE_FEATURES.MULTI_TENANCY});
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

        AuthRecipe.createPrimaryUser(process.main, emailPasswordUser1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.main, emailPasswordUser2.getSupertokensUserId(),
                emailPasswordUser1.getSupertokensUserId());

        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", emailPasswordUser2.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/primary", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("RECIPE_USER_ID_ALREADY_LINKED_WITH_PRIMARY_USER_ID_ERROR",
                    response.get("status").getAsString());
            assertEquals("r1", response.get("primaryUserId").getAsString());
            assertEquals("This user ID is already linked to another user ID",
                    response.get("description").getAsString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createPrimaryUserInTenantWithAnotherStorage() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MFA, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        JsonObject coreConfig = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 2);

        TenantIdentifier tenantIdentifier = new TenantIdentifier(null, null, "t1");
        Multitenancy.addNewOrUpdateAppOrTenant(
                process.getProcess(),
                new TenantIdentifier(null, null, null),
                new TenantConfig(
                        tenantIdentifier,
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, coreConfig
                )
        );

        AuthRecipeUserInfo user = EmailPassword.signUp(
                tenantIdentifier, (StorageLayer.getStorage(tenantIdentifier, process.main)),
                process.getProcess(), "test@example.com", "abcd1234");

        JsonObject userObj;
        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", user.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/primary", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertFalse(response.get("wasAlreadyAPrimaryUser").getAsBoolean());

            // check user object
            JsonObject jsonUser = response.get("user").getAsJsonObject();
            assert (jsonUser.get("id").getAsString().equals(user.getSupertokensUserId()));
            assert (jsonUser.get("tenantIds").getAsJsonArray().size() == 1);
            assert (jsonUser.get("tenantIds").getAsJsonArray().get(0).getAsString().equals("t1"));
            assert (jsonUser.get("timeJoined").getAsLong() == user.timeJoined);
            assert (jsonUser.get("isPrimaryUser").getAsBoolean());
            assert (jsonUser.get("emails").getAsJsonArray().size() == 1);
            assert (jsonUser.get("emails").getAsJsonArray().get(0).getAsString().equals("test@example.com"));
            assert (jsonUser.get("phoneNumbers").getAsJsonArray().size() == 0);
            assert (jsonUser.get("thirdParty").getAsJsonArray().size() == 0);
            assert (jsonUser.get("loginMethods").getAsJsonArray().size() == 1);
            JsonObject lM = jsonUser.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject();
            assertFalse(lM.get("verified").getAsBoolean());
            assert (lM.get("tenantIds").getAsJsonArray().size() == 1);
            assert (lM.get("tenantIds").getAsJsonArray().get(0).getAsString().equals("t1"));
            assertEquals(lM.get("timeJoined").getAsLong(), user.timeJoined);
            assertEquals(lM.get("recipeUserId").getAsString(), user.getSupertokensUserId());
            assertEquals(lM.get("recipeId").getAsString(), "emailpassword");
            assertEquals(lM.get("email").getAsString(), "test@example.com");
            assert (lM.entrySet().size() == 6);
            userObj = jsonUser;
        }

        AuthRecipe.createPrimaryUser(process.main,
                tenantIdentifier.toAppIdentifier(), (StorageLayer.getStorage(tenantIdentifier, process.main)),
                user.getSupertokensUserId());

        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", user.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/primary", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("wasAlreadyAPrimaryUser").getAsBoolean());
            assertEquals(response.get("user"), userObj);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createReturnsFailsWithoutFeatureEnabled() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "abcd1234");

        JsonObject userObj;
        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", user.getSupertokensUserId());

            try {
                HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/accountlinking/user/primary", params, 1000, 1000, null,
                        WebserverAPI.getLatestCDIVersion().get(), "");
                assert (false);
            } catch (HttpResponseException e) {
                assertEquals(402, e.statusCode);
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }
}
