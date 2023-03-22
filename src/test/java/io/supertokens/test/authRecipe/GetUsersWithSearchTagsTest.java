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
import io.supertokens.authRecipe.UserPaginationContainer.UsersContainer;
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
    public void retriveUsersUsingSearchTags() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create emailpassword user
        ArrayList<String> userIds = new ArrayList<>();
        userIds.add(EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123").id);
        userIds.add(EmailPassword.signUp(process.getProcess(), "test2@example.com", "testPass123").id);

        // create thirdparty user
        userIds.add(ThirdParty.signInUp(process.getProcess(), "testTPID", "test", "test2@example.com").user.id);

        // create passwordless user
        CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), "test@example.com", "+123456789012",
                null, null);
        userIds.add(Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId, createCodeResponse.deviceIdHash,
        createCodeResponse.userInputCode, null).user.id);
        
        // partial search with input emails as "test"
        {
            ArrayList<String> emailArrayList = new ArrayList<>();
            emailArrayList.add("test");

            DashboardSearchTags tags = new DashboardSearchTags(emailArrayList, null, null);

            UserPaginationContainer info = AuthRecipe.getUsers(process.getProcess(), 10, "ASC", null, null, tags);
            assertEquals(userIds.size(), info.users.length);
            for (int i = 0; i < info.users.length; i++) {
                assertEquals(userIds.get(i), info.users[i].user.id);   
            }
        }

        // search with provider and input email as "test"
        {
            ArrayList<String> arrayList = new ArrayList<>();
            arrayList.add("test");

            // check that only thirdparty user is retrieved

            DashboardSearchTags tags = new DashboardSearchTags(arrayList, null, arrayList);
            UserPaginationContainer info = AuthRecipe.getUsers(process.getProcess(), 10, "ASC", null, null, tags);
            assertEquals(1, info.users.length);
            assertEquals(userIds.get(2), info.users[0].user.id);
            assertEquals("thirdparty", info.users[0].recipeId);

        }

        // test retrieving the passwordless user with partial phone number
        {
            ArrayList<String> arrayList = new ArrayList<>();
            arrayList.add("+12345");

            // check that only passwordless user is retrieved

            DashboardSearchTags tags = new DashboardSearchTags(null, arrayList, null);
            UserPaginationContainer info = AuthRecipe.getUsers(process.getProcess(), 10, "ASC", null, null, tags);
            assertEquals(1, info.users.length);
            assertEquals(userIds.get(3), info.users[0].user.id);
            assertEquals("passwordless", info.users[0].recipeId);   
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUsersWithConflictingTagsReturnsEmptyList() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create emailpassword user
        ArrayList<String> userIds = new ArrayList<>();
        userIds.add(EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123").id);
        userIds.add(EmailPassword.signUp(process.getProcess(), "test2@example.com", "testPass123").id);

        // create thirdparty user
        userIds.add(ThirdParty.signInUp(process.getProcess(), "testTPID", "test", "test2@example.com").user.id);

        // create passwordless user
        CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), "test@example.com", null,
                null, null);
        userIds.add(Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId, createCodeResponse.deviceIdHash,
        createCodeResponse.userInputCode, null).user.id);

        

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
