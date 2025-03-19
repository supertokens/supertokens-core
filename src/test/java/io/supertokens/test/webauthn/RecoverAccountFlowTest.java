/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.webauthn;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class RecoverAccountFlowTest {

    @Rule
    public TestRule watchman = io.supertokens.test.Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        io.supertokens.test.Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        io.supertokens.test.Utils.reset();
    }

    @Test
    public void recoverAccountTest() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<JsonObject> users = Utils.registerUsers(process.getProcess(), 1);
        assertEquals(1, users.size());

        JsonObject recoverAccountToken = Utils.generateRecoverAccountTokenForEmail(process.getProcess(), "user0@example.com", users.get(0).getAsJsonObject("user").get("id").getAsString());
        assertTrue(recoverAccountToken.has("token"));


        JsonObject userFromToken = Utils.getUserFromToken(process.getProcess(), recoverAccountToken.get("token").getAsString());
        assertTrue(userFromToken.has("user"));
        assertTrue(userFromToken.has("recipeUserId"));
        assertFalse(userFromToken.get("recipeUserId").isJsonNull());
        assertEquals(users.get(0).get("recipeUserId").getAsString(), userFromToken.get("recipeUserId").getAsString());

        JsonObject registerCredentialResponse = Utils.registerCredentialForUser(process.getProcess(), "user0@example.com", users.get(0).get("recipeUserId").getAsString());
        assertTrue(registerCredentialResponse.has("status"));
        assertTrue(registerCredentialResponse.has("webauthnCredentialId"));


        JsonObject recoverAccountResponse = Utils.consumeToken(process.getProcess(), recoverAccountToken.get("token").getAsString());
        assertTrue(recoverAccountResponse.has("status"));
        assertTrue(recoverAccountResponse.has("userId"));
        assertTrue(recoverAccountResponse.has("email"));

        assertEquals("user0@example.com", recoverAccountResponse.get("email").getAsString());
    }

    @Test
    public void recoverAccountFailsWithNonExistingUserTest() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<JsonObject> users = Utils.registerUsers(process.getProcess(), 1);
        assertEquals(1, users.size());

        JsonObject recoverAccountToken = Utils.generateRecoverAccountTokenForEmail(process.getProcess(), "user1@example.com", "not-existing");
        assertFalse(recoverAccountToken.has("token"));
        assertTrue(recoverAccountToken.has("status"));
        assertEquals("UNKNOWN_USER_ID_ERROR", recoverAccountToken.get("status").getAsString());
    }

    @Test
    public void recoverAccountWithLinkedPrimaryUserTest() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<JsonObject> users = Utils.registerUsers(process.getProcess(), 1);
        assertEquals(1, users.size());

        List<AuthRecipeUserInfo> emailPasswordUsers = Utils.createEmailPasswordUsers(process.getProcess(), 1, true);
        assertEquals(1, emailPasswordUsers.size());

        Utils.linkAccounts(process.getProcess(), emailPasswordUsers.stream().map(AuthRecipeUserInfo::getSupertokensUserId).collect(
                Collectors.toList()), users.stream().map(u -> u.getAsJsonObject("user").get("id").getAsString()).collect(
                Collectors.toList()));

        JsonObject recoverAccountToken = Utils.generateRecoverAccountTokenForEmail(process.getProcess(), "user0@example.com", emailPasswordUsers.get(0).getSupertokensUserId());
        assertTrue(recoverAccountToken.has("token"));


        JsonObject userFromToken = Utils.getUserFromToken(process.getProcess(), recoverAccountToken.get("token").getAsString());
        assertTrue(userFromToken.has("user"));
        assertTrue(userFromToken.has("recipeUserId"));
        assertEquals(users.get(0).get("recipeUserId").getAsString(), userFromToken.get("recipeUserId").getAsString());


        JsonObject registerCredentialResponse = Utils.registerCredentialForUser(process.getProcess(), "user0@example.com", users.get(0).get("recipeUserId").getAsString());
        assertTrue(registerCredentialResponse.has("status"));
        assertTrue(registerCredentialResponse.has("webauthnCredentialId"));


        JsonObject recoverAccountResponse = Utils.consumeToken(process.getProcess(), recoverAccountToken.get("token").getAsString());
        assertTrue(recoverAccountResponse.has("status"));
        assertTrue(recoverAccountResponse.has("userId"));
        assertTrue(recoverAccountResponse.has("email"));

        assertEquals("user0@example.com", recoverAccountResponse.get("email").getAsString());
    }

    @Test
    public void recoverAccountTokenWithoutWANUserButWithExistingEmailPrimaryUserTest() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<AuthRecipeUserInfo> emailPasswordUsers = Utils.createEmailPasswordUsers(process.getProcess(), 1, true);
        assertEquals(1, emailPasswordUsers.size());

        JsonObject recoverAccountToken = Utils.generateRecoverAccountTokenForEmail(process.getProcess(), "user0@example.com", emailPasswordUsers.get(0).getSupertokensUserId());
        assertTrue(recoverAccountToken.has("token"));

        JsonObject userFromToken = Utils.getUserFromToken(process.getProcess(), recoverAccountToken.get("token").getAsString());
        assertTrue(userFromToken.has("user"));
        assertTrue(userFromToken.has("recipeUserId"));
        assertTrue(userFromToken.get("recipeUserId").isJsonNull());
        assertEquals(emailPasswordUsers.get(0).getSupertokensUserId(), userFromToken.get("user").getAsJsonObject().get("id").getAsString());

        JsonObject consumeTokenRespone = Utils.consumeToken(process.getProcess(), recoverAccountToken.get("token").getAsString());
        String userIdFromConsumeResponse = consumeTokenRespone.get("userId").getAsString();
        assertEquals(emailPasswordUsers.get(0).getSupertokensUserId(), userIdFromConsumeResponse);

        JsonObject registerCredentialResponse = Utils.registerCredentialForUser(process.getProcess(), "user0@example.com", userIdFromConsumeResponse);
        assertEquals("UNKNOWN_USER_ID_ERROR", registerCredentialResponse.get("status").getAsString());

        JsonObject signupWithCredentialRegister = Utils.registerUserWithCredentials(process.getProcess(), "user0@example.com");

        Utils.linkAccounts(process.getProcess(), List.of(emailPasswordUsers.get(0).getSupertokensUserId()),
                List.of(signupWithCredentialRegister.get("user").getAsJsonObject().get("id").getAsString()));

        JsonObject signInResponse = Utils.signInWithUser(process.getProcess(), signupWithCredentialRegister);
        assertEquals("OK", signInResponse.get("status").getAsString());
        assertEquals(2, signInResponse.getAsJsonObject("user").get("loginMethods").getAsJsonArray().size());

    }


}
