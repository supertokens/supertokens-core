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

public class CredentialRegisterFlowTest {

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
    public void registerCredential() throws Exception {
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

        JsonObject signInResponse = Utils.signInWithUser(process.getProcess(), users.get(0));
        assertEquals("OK", signInResponse.get("status").getAsString());

        JsonObject registerCredentialsResponse = Utils.registerCredentialForUser(process.getProcess(), "user0@example.com", signInResponse.getAsJsonObject("user").get("id").getAsString());
        assertEquals("OK", registerCredentialsResponse.get("status").getAsString());

        JsonObject signInAgainResponse = Utils.signInWithUser(process.getProcess(), users.get(0));
        assertEquals("OK", signInAgainResponse.get("status").getAsString());
    }

    @Test
    public void registerCredentialWithUserIdMapping() throws Exception {
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

        JsonObject signInResponse = Utils.signInWithUser(process.getProcess(), users.get(0));
        assertEquals("OK", signInResponse.get("status").getAsString());
        assertEquals("external_id", signInResponse.getAsJsonObject("user").get("id").getAsString());
        assertEquals("external_id", signInResponse.get("recipeUserId").getAsString());

        JsonObject registerCredentialsResponse = Utils.registerCredentialForUser(process.getProcess(), "user0@example.com", "external_id");
        assertEquals("OK", registerCredentialsResponse.get("status").getAsString());
        assertEquals("external_id", registerCredentialsResponse.get("recipeUserId").getAsString());

        JsonObject signInAgainResponse = Utils.signInWithUser(process.getProcess(), users.get(0));
        assertEquals("OK", signInAgainResponse.get("status").getAsString());
        assertEquals("external_id", signInResponse.getAsJsonObject("user").get("id").getAsString());
        assertEquals("external_id", signInResponse.get("recipeUserId").getAsString());
        assertTrue(signInAgainResponse.getAsJsonObject("user").getAsJsonObject("webauthn").getAsJsonArray("credentialIds").contains(registerCredentialsResponse.get("webauthnCredentialId").getAsJsonPrimitive()));
    }

    @Test
    public void registerCredentialWithAccountLinking() throws Exception {
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

        JsonObject signInResponse = Utils.signInWithUser(process.getProcess(), users.get(0));
        assertEquals("OK", signInResponse.get("status").getAsString());
        assertEquals("external_id", signInResponse.getAsJsonObject("user").get("id").getAsString());
        assertEquals("external_id", signInResponse.get("recipeUserId").getAsString());

        List<AuthRecipeUserInfo> epUsers = Utils.createEmailPasswordUsers(process.getProcess(), 1, true);
        Utils.linkAccounts(process.getProcess(), epUsers.stream().map(AuthRecipeUserInfo::getSupertokensUserId).collect(
                Collectors.toList()), users.stream().map(user -> user.getAsJsonObject("user").get("id").getAsString()).collect(Collectors.toList()));

        JsonObject registerCredentialsResponse = Utils.registerCredentialForUser(process.getProcess(), "user0@example.com", "external_id");
        assertEquals("OK", registerCredentialsResponse.get("status").getAsString());
        assertEquals("external_id", registerCredentialsResponse.get("recipeUserId").getAsString());

        JsonObject signInAgainResponse = Utils.signInWithUser(process.getProcess(), users.get(0));
        assertEquals("OK", signInAgainResponse.get("status").getAsString());
        assertEquals("external_id", signInResponse.getAsJsonObject("user").get("id").getAsString());
        assertEquals("external_id", signInResponse.get("recipeUserId").getAsString());
        assertTrue(signInAgainResponse.getAsJsonObject("user").getAsJsonObject("webauthn").getAsJsonArray("credentialIds").contains(registerCredentialsResponse.get("webauthnCredentialId").getAsJsonPrimitive()));
    }
}
