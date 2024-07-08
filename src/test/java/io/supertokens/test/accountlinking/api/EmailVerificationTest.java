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
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
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

public class EmailVerificationTest {
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
    public void testEmailVerificationWithUserIdMapping() throws Exception {
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

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");
        UserIdMapping.createUserIdMapping(process.getProcess(), user1.getSupertokensUserId(), "euserid1", null, false);
        String token = EmailVerification.generateEmailVerificationToken(process.getProcess(), "euserid1",
                "test1@example.com");
        EmailVerification.verifyEmail(process.getProcess(), token);

        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        UserIdMapping.createUserIdMapping(process.getProcess(), user2.getSupertokensUserId(), "euserid2", null, false);

        AuthRecipe.createPrimaryUser(process.getProcess(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        {
            Map<String, String> params = new HashMap<>();
            params.put("userId", "euserid1");
            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/user/id", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            JsonObject user = response.get("user").getAsJsonObject();
            assertTrue(
                    user.get("loginMethods").getAsJsonArray().get(0).getAsJsonObject().get("verified").getAsBoolean());
            assertFalse(
                    user.get("loginMethods").getAsJsonArray().get(1).getAsJsonObject().get("verified").getAsBoolean());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
