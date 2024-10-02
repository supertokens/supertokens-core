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

package io.supertokens.test.accountlinking;

import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class EmailPasswordTests {
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
    public void testUpdatePasswordWithDifferentValidUserId() throws Exception {
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

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        AuthRecipeUserInfo user2 = ThirdParty.signInUp(process.getProcess(), "google", "googleid",
                "test@example.com").user;

        AuthRecipe.createPrimaryUser(process.getProcess(), user2.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user1.getSupertokensUserId(), user2.getSupertokensUserId());

        try {
            EmailPassword.updateUsersEmailOrPassword(process.getProcess(), user2.getSupertokensUserId(), null,
                    "password2");
            fail();
        } catch (UnknownUserIdException e) {
            // expecting this here
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testPasswordlessUserWithSameEmail() throws Exception {
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

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        Passwordless.CreateCodeResponse code = Passwordless.createCode(process.getProcess(), null, "+919876543210",
                null, null);
        AuthRecipeUserInfo user2 = Passwordless.consumeCode(process.getProcess(), code.deviceId, code.deviceIdHash,
                code.userInputCode, null).user;

        AuthRecipe.createPrimaryUser(process.getProcess(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        Passwordless.CreateCodeResponse code1 = Passwordless.createCode(process.getProcess(), "test@example.com", null,
                null, null);
        AuthRecipeUserInfo user3 = Passwordless.consumeCode(process.getProcess(), code1.deviceId, code1.deviceIdHash,
                code1.userInputCode, null).user;

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testPasswordlessUsersLinked() throws Exception {
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

        Passwordless.CreateCodeResponse code1 = Passwordless.createCode(process.getProcess(), "test@example.com", null,
                null, null);
        AuthRecipeUserInfo user1 = Passwordless.consumeCode(process.getProcess(), code1.deviceId, code1.deviceIdHash,
                code1.userInputCode, null).user;

        Thread.sleep(50);

        Passwordless.CreateCodeResponse code2 = Passwordless.createCode(process.getProcess(), null, "+919876543210",
                null, null);
        AuthRecipeUserInfo user2 = Passwordless.consumeCode(process.getProcess(), code2.deviceId, code2.deviceIdHash,
                code2.userInputCode, null).user;

        AuthRecipe.createPrimaryUser(process.getProcess(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        Passwordless.CreateCodeResponse code3 = Passwordless.createCode(process.getProcess(), null, "+919876543210",
                null, null);
        AuthRecipeUserInfo user3 = Passwordless.consumeCode(process.getProcess(), code3.deviceId, code3.deviceIdHash,
                code3.userInputCode, null).user;

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }
}
