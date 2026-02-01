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

import static org.junit.Assert.*;

public class UpdateEmailFlowTest {

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
    public void updateEmailForUser() throws Exception {
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
        assertEquals("user0@example.com", users.get(0).getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());

        JsonObject updateEmailResponse = Utils.updateEmail(process.getProcess(), users.get(0).getAsJsonObject("user").get("id").getAsString(), "newemail@example.com");
        assertEquals("OK", updateEmailResponse.get("status").getAsString());

        JsonObject signInResponse = Utils.signInWithUser(process.getProcess(), users.get(0));

        assertEquals("OK", signInResponse.get("status").getAsString());
        assertEquals("newemail@example.com", signInResponse.getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());
    }

    @Test
    public void updateEmailForUserWithExternalId() throws Exception {
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
        assertEquals("user0@example.com", users.get(0).getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());

        Utils.createUserIdMapping(process.getProcess(), users.get(0).getAsJsonObject("user").get("id").getAsString(), "external_id");

        JsonObject updateEmailResponse = Utils.updateEmail(process.getProcess(), "external_id", "newemail@example.com");
        assertEquals("OK", updateEmailResponse.get("status").getAsString());

        JsonObject signInResponse = Utils.signInWithUser(process.getProcess(), users.get(0));

        assertEquals("OK", signInResponse.get("status").getAsString());
        assertEquals("newemail@example.com", signInResponse.getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());

    }

    @Test
    public void updateEmailForNotExistingUser() throws Exception {
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
        assertEquals("user0@example.com", users.get(0).getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());

        JsonObject updateEmailResponse = Utils.updateEmail(process.getProcess(), "notexisting_user_id", "newemail@example.com");

        assertEquals("UNKNOWN_USER_ID_ERROR", updateEmailResponse.get("status").getAsString());
    }

    @Test
    public void updateEmailForUserForAlreadyExistingEmailInWebauthn() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<JsonObject> users = Utils.registerUsers(process.getProcess(), 2);
        assertEquals(2, users.size());
        assertEquals("user0@example.com", users.get(0).getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());
        assertEquals("user1@example.com", users.get(1).getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());

        JsonObject updateEmailResponse = Utils.updateEmail(process.getProcess(), users.get(0).getAsJsonObject("user").get("id").getAsString(), users.get(1).getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());

        assertEquals("EMAIL_ALREADY_EXISTS_ERROR", updateEmailResponse.get("status").getAsString());
    }

    @Test
    public void updateEmailForUserForAlreadyExistingEmailInEmailPassword() throws Exception {
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
        assertEquals("user0@example.com", users.get(0).getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());


        List<AuthRecipeUserInfo> epUsers = Utils.createEmailPasswordUsers(process.getProcess(), 2, true);
        assertEquals(2, epUsers.size());
        assertEquals("user1@example.com", epUsers.get(1).loginMethods[0].email);


        JsonObject updateEmailResponse = Utils.updateEmail(process.getProcess(), users.get(0).getAsJsonObject("user").get("id").getAsString(),
                "user1@example.com");

        assertEquals("OK", updateEmailResponse.get("status").getAsString());
    }

    @Test
    public void updateEmailForUserForAlreadyExistingEmailInEmailPasswordWithBothPrimary() throws Exception {
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
        assertEquals("user0@example.com", users.get(0).getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());

        Utils.makePrimaryUserFrom(process.getProcess(), users.get(0).getAsJsonObject("user").get("id").getAsString());

        List<AuthRecipeUserInfo> epUsers = Utils.createEmailPasswordUsers(process.getProcess(), 2, false);
        assertEquals(2, epUsers.size());
        assertEquals("user1@example.com", epUsers.get(1).loginMethods[0].email);
        Utils.makePrimaryUserFrom(process.getProcess(), epUsers.get(1).getSupertokensUserId()); // make the target user email's account primary


        JsonObject updateEmailResponse = Utils.updateEmail(process.getProcess(), users.get(0).getAsJsonObject("user").get("id").getAsString(),
                "user1@example.com");

        assertEquals("USER_WITH_EMAIL_ALREADY_EXISTS_ERROR", updateEmailResponse.get("status").getAsString());
    }

    @Test
    public void updateEmailForUserForAlreadyExistingEmailInEmailPasswordWithNoAccountLinking() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<JsonObject> users = Utils.registerUsers(process.getProcess(), 1);
        assertEquals(1, users.size());
        assertEquals("user0@example.com", users.get(0).getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());

        Utils.verifyEmailFor(process.getProcess(), users.get(0).getAsJsonObject("user").get("id").getAsString(), "user0@example.com");

        List<AuthRecipeUserInfo> epUsers = Utils.createEmailPasswordUsers(process.getProcess(), 1, false, 1);
        assertEquals(1, epUsers.size());
        assertEquals("user1@example.com", epUsers.get(0).loginMethods[0].email);


        JsonObject updateEmailResponse = Utils.updateEmail(process.getProcess(), users.get(0).getAsJsonObject("user").get("id").getAsString(),
                "user1@example.com");
        assertEquals("OK", updateEmailResponse.get("status").getAsString());

        JsonObject signInResponse = Utils.signInWithUser(process.getProcess(), users.get(0));
        System.out.println(signInResponse);

    }

    @Test
    public void updateEmailForUserForAlreadyExistingWithNoAccountLinking() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<JsonObject> users = Utils.registerUsers(process.getProcess(), 2);
        assertEquals(2, users.size());
        assertEquals("user0@example.com", users.get(0).getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());
        assertEquals("user1@example.com", users.get(1).getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());

        Utils.verifyEmailFor(process.getProcess(), users.get(0).getAsJsonObject("user").get("id").getAsString(), "user0@example.com");

        JsonObject updateEmailResponse = Utils.updateEmail(process.getProcess(), users.get(0).getAsJsonObject("user").get("id").getAsString(),
                "user1@example.com");
        assertEquals("EMAIL_ALREADY_EXISTS_ERROR", updateEmailResponse.get("status").getAsString());
    }

    @Test
    public void updateEmailForUserForNewEmailBecomesUnverifiedWithNoAccountLinking() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<JsonObject> users = Utils.registerUsers(process.getProcess(), 2);
        assertEquals(2, users.size());
        assertEquals("user0@example.com", users.get(0).getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());
        assertEquals("user1@example.com", users.get(1).getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());

        Utils.verifyEmailFor(process.getProcess(), users.get(0).getAsJsonObject("user").get("id").getAsString(), "user0@example.com");

        JsonObject updateEmailResponse = Utils.updateEmail(process.getProcess(), users.get(0).getAsJsonObject("user").get("id").getAsString(),
                "user3@example.com");
        assertEquals("OK", updateEmailResponse.get("status").getAsString());

        JsonObject signInResponse = Utils.signInWithUser(process.getProcess(), users.get(0));
        assertEquals("user3@example.com", signInResponse.getAsJsonObject("user").get("emails").getAsJsonArray().get(0).getAsString());
        assertFalse(signInResponse.getAsJsonObject("user").get("loginMethods").getAsJsonArray().get(0).getAsJsonObject().get("verified").getAsBoolean());
    }


}
