/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test;

import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertNotNull;


public class AuthRecipeTest {
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
    public void getUsersCount() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), null);
            assert (count == 0);
        }

        EmailPassword.signUp(process.getProcess(), "test0@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "test1@example.com", "password1");

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), null);
            assert (count == 2);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD});
            assert (count == 2);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY});
            assert (count == 0);
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId_1 = "thirdPartyUserIdA";
        String email_1 = "testA@example.com";

        ThirdParty.signInUp(process.getProcess(), thirdPartyId, thirdPartyUserId_1, email_1, false);

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{});
            assert (count == 3);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(),
                    new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD, RECIPE_ID.THIRD_PARTY});
            assert (count == 3);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD});
            assert (count == 2);
        }

        {
            long count = AuthRecipe.getUsersCount(process.getProcess(), new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY});
            assert (count == 1);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
