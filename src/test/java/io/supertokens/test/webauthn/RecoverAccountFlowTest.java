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
    public void testIssue101_recoverAccountTokenInvalidAfterEmailChange() throws Exception {
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
        String recipeUserId = users.get(0).get("recipeUserId").getAsString();

        JsonObject recoverAccountToken = Utils.generateRecoverAccountTokenForEmail(process.getProcess(),
                "user0@example.com", users.get(0).getAsJsonObject("user").get("id").getAsString());
        assertTrue(recoverAccountToken.has("token"));

        JsonObject updateEmailResponse = Utils.updateEmail(process.getProcess(), recipeUserId, "new@example.com");
        assertEquals("OK", updateEmailResponse.get("status").getAsString());

        JsonObject recoverAccountResponse = Utils.consumeToken(process.getProcess(),
                recoverAccountToken.get("token").getAsString());
        assertEquals("RECOVER_ACCOUNT_TOKEN_INVALID_ERROR", recoverAccountResponse.get("status").getAsString());

        // a token for the new email still works
        JsonObject newToken = Utils.generateRecoverAccountTokenForEmail(process.getProcess(),
                "new@example.com", users.get(0).getAsJsonObject("user").get("id").getAsString());
        JsonObject newTokenResponse = Utils.consumeToken(process.getProcess(), newToken.get("token").getAsString());
        assertEquals("OK", newTokenResponse.get("status").getAsString());
        assertEquals("new@example.com", newTokenResponse.get("email").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testIssue101_recoverAccountTokenForPrimaryIdWithLinkedLoginMethodEmailIsConsumable()
            throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // primary created from a webauthn user with user0@example.com, linked email-password login method with
        // the verified email user1@example.com
        List<JsonObject> users = Utils.registerUsers(process.getProcess(), 1);
        String primaryId = users.get(0).getAsJsonObject("user").get("id").getAsString();
        Utils.makePrimaryUserFrom(process, primaryId);
        List<AuthRecipeUserInfo> emailPasswordUsers = Utils.createEmailPasswordUsers(process, 1, false, 1);
        Utils.verifyEmailFor(process.getProcess(), emailPasswordUsers.get(0).getSupertokensUserId(),
                "user1@example.com");
        Utils.linkAccounts(process.getProcess(), List.of(primaryId),
                List.of(emailPasswordUsers.get(0).getSupertokensUserId()));

        // token issued for the primary id with the linked login method's email (to add a webauthn login method)
        JsonObject recoverAccountToken = Utils.generateRecoverAccountTokenForEmail(process.getProcess(),
                "user1@example.com", primaryId);
        assertTrue(recoverAccountToken.has("token"));

        JsonObject recoverAccountResponse = Utils.consumeToken(process.getProcess(),
                recoverAccountToken.get("token").getAsString());
        assertEquals("OK", recoverAccountResponse.get("status").getAsString());
        assertEquals(primaryId, recoverAccountResponse.get("userId").getAsString());
        assertEquals("user1@example.com", recoverAccountResponse.get("email").getAsString());

        // a token for an email that is on no login method of the account is rejected
        JsonObject otherToken = Utils.generateRecoverAccountTokenForEmail(process.getProcess(),
                "nobody@example.com", primaryId);
        JsonObject otherResponse = Utils.consumeToken(process.getProcess(), otherToken.get("token").getAsString());
        assertEquals("RECOVER_ACCOUNT_TOKEN_INVALID_ERROR", otherResponse.get("status").getAsString());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
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

        List<AuthRecipeUserInfo> emailPasswordUsers = Utils.createEmailPasswordUsers(process, 1, true);
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

        List<AuthRecipeUserInfo> emailPasswordUsers = Utils.createEmailPasswordUsers(process, 1, true);
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
