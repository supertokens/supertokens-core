/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.userIdMapping.recipe;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;

import static org.junit.Assert.*;

public class EmailPasswordAPITest {
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
    public void testSignInAPI() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a User
        String email = "test@example.com";
        String password = "testPass123";
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, email, password);
        String superTokensUserId = userInfo.getSupertokensUserId();
        String externalUserId = "externalId";

        // create the mapping
        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, null, false);

        {
            // check that mapping exists
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);
            assertNotNull(response);
            assertEquals(response.superTokensUserId, superTokensUserId);
            assertEquals(response.externalUserId, externalUserId);
        }

        // call signIn api and check that the externalId is returned to the response
        {
            JsonObject signUpRequestBody = new JsonObject();
            signUpRequestBody.addProperty("email", email);
            signUpRequestBody.addProperty("password", password);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/signin", signUpRequestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "emailpassword");
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(externalUserId, response.get("user").getAsJsonObject().get("id").getAsString());
        }

        // delete User and check that the mapping no longer exists
        {
            AuthRecipe.deleteUser(process.main, superTokensUserId);
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);
            assertNull(response);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testResetPasswordFlowWithUserIdMapping() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a User
        String email = "test@example.com";
        String password = "testPass123";
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, email, password);
        String superTokensUserId = userInfo.getSupertokensUserId();
        String externalUserId = "externalId";

        // create the mapping
        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, null, false);

        // call GeneratePasswordResetTokenAPI api with externalId
        String passwordResetToken = null;

        {
            JsonObject passwordResetTokenBody = new JsonObject();
            passwordResetTokenBody.addProperty("userId", externalUserId);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/password/reset/token", passwordResetTokenBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "emailpassword");
            assertEquals("OK", response.get("status").getAsString());

            passwordResetToken = response.get("token").getAsString();
        }

        // reset the users' password by calling the ResetPasswordAPI
        String newPassword = "newTestPassword123";
        {
            JsonObject resetPasswordBody = new JsonObject();
            resetPasswordBody.addProperty("method", "token");
            resetPasswordBody.addProperty("token", passwordResetToken);
            resetPasswordBody.addProperty("newPassword", newPassword);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user/password/reset", resetPasswordBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "emailpassword");
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(externalUserId, response.get("userId").getAsString());
        }

        // sign in with the new password and check that it works
        AuthRecipeUserInfo userInfo1 = EmailPassword.signIn(process.main, email, newPassword);
        assertNotNull(userInfo1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a User
        String email = "test@example.com";
        String password = "testPass123";
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, email, password);
        String superTokensUserId = userInfo.getSupertokensUserId();
        String externalUserId = "externalId";

        // create the mapping
        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, null, false);

        // retrieving UserInfo with userId
        {
            HashMap<String, String> queryParam = new HashMap<>();
            queryParam.put("userId", externalUserId);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", queryParam, 1000, 1000, null,
                    SemVer.v2_15.get(), "emailpassword");
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(externalUserId, response.get("user").getAsJsonObject().get("id").getAsString());
        }

        // retrieving UserInfo with email
        {
            HashMap<String, String> queryParam = new HashMap<>();
            queryParam.put("email", email);
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", queryParam, 1000, 1000, null,
                    SemVer.v2_15.get(), "emailpassword");
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(externalUserId, response.get("user").getAsJsonObject().get("id").getAsString());
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingUsersEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a User
        String email = "test@example.com";
        String password = "testPass123";
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, email, password);
        String superTokensUserId = userInfo.getSupertokensUserId();
        String externalUserId = "externalId";

        // create the mapping
        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, null, false);

        // update the users email
        String newEmail = "testnew123@example.com";
        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("email", newEmail);
            requestBody.addProperty("userId", externalUserId);

            JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/user", requestBody, 1000, 1000, null,
                    SemVer.v2_15.get(), "emailpassword");
            assertEquals("OK", response.get("status").getAsString());
        }

        // check that you can now sign in with the new email
        AuthRecipeUserInfo userInfo1 = EmailPassword.signIn(process.main, newEmail, password);
        assertNotNull(userInfo1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
