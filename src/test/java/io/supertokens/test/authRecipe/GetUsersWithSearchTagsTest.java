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

package io.supertokens.test.authRecipe;

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import static org.junit.Assert.*;
import static org.junit.Assert.assertNotNull;

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.UserPaginationContainer;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.Passwordless.ConsumeCodeResponse;
import io.supertokens.passwordless.Passwordless.CreateCodeResponse;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.dashboard.DashboardSearchTags;
import io.supertokens.pluginInterface.passwordless.UserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.ThirdParty;

public class GetUsersWithSearchTagsTest {
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
    public void createMultipleUsersAndMapTheirIdsRetrieveAllUsersAndCheckThatExternalIdIsReturned() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create emailpassword user
        EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123");
        EmailPassword.signUp(process.getProcess(), "test2@example.com", "testPass123");
        // create thirdparty user
        ThirdParty.signInUp(process.getProcess(), "testTPID", "test", "test2@example.com");

        // create passwordless user
        CreateCodeResponse createCodeResponse =  Passwordless.createCode(process.getProcess(), "test@example.com", null, null, null);
        Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId, createCodeResponse.deviceIdHash, createCodeResponse.userInputCode, null);

        ArrayList<String> emails = new ArrayList<>();
        emails.add("st");
        ArrayList<String> recipeIds = new ArrayList<>();
        recipeIds.add("emailpassword");
        recipeIds.add("thirdparty");
        recipeIds.add("passwordless");

        DashboardSearchTags tags = new DashboardSearchTags(emails, null, null, recipeIds);

        UserPaginationContainer info = AuthRecipe.getUsers(process.getProcess(), 100, "ASC", null, null, tags);
        assertEquals(4, info.users.length);
        
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
