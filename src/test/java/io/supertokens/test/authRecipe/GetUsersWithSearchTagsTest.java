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
import io.supertokens.passwordless.Passwordless.CreateCodeResponse;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.dashboard.DashboardSearchTags;
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
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create emailpassword user
        ArrayList<String> userIds = new ArrayList<>();
        userIds.add(
                EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123").getSupertokensUserId());
        userIds.add(
                EmailPassword.signUp(process.getProcess(), "test2@example.com", "testPass123").getSupertokensUserId());

        // create thirdparty user
        userIds.add(ThirdParty.signInUp(process.getProcess(), "testTPID", "test",
                "test2@example.com").user.getSupertokensUserId());

        // create passwordless user
        CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), "test@example.com",
                "+123456789012",
                null, null);
        userIds.add(Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId,
                createCodeResponse.deviceIdHash,
                createCodeResponse.userInputCode, null).user.getSupertokensUserId());

        // partial search with input emails as "test"
        {
            ArrayList<String> emailArrayList = new ArrayList<>();
            emailArrayList.add("test");

            DashboardSearchTags tags = new DashboardSearchTags(emailArrayList, null, null);

            UserPaginationContainer info = AuthRecipe.getUsers(process.getProcess(), 10, "ASC", null, null, tags);
            assertEquals(userIds.size(), info.users.length);
            for (int i = 0; i < info.users.length; i++) {
                assertTrue(userIds.contains(info.users[i].getSupertokensUserId()));
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
            assertEquals(userIds.get(2), info.users[0].getSupertokensUserId());
            assertEquals("thirdparty", info.users[0].loginMethods[0].recipeId.toString());

        }

        // test retrieving the passwordless user with partial phone number
        {
            ArrayList<String> arrayList = new ArrayList<>();
            arrayList.add("+12345");

            // check that only passwordless user is retrieved

            DashboardSearchTags tags = new DashboardSearchTags(null, arrayList, null);
            UserPaginationContainer info = AuthRecipe.getUsers(process.getProcess(), 10, "ASC", null, null, tags);
            assertEquals(1, info.users.length);
            assertEquals(userIds.get(3), info.users[0].getSupertokensUserId());
            assertEquals("passwordless", info.users[0].loginMethods[0].recipeId.toString());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUsersWithConflictingTagsReturnsEmptyList() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create emailpassword user
        ArrayList<String> userIds = new ArrayList<>();
        userIds.add(
                EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123").getSupertokensUserId());
        userIds.add(
                EmailPassword.signUp(process.getProcess(), "test2@example.com", "testPass123").getSupertokensUserId());

        // create thirdparty user
        userIds.add(ThirdParty.signInUp(process.getProcess(), "testTPID", "test",
                "test2@example.com").user.getSupertokensUserId());

        // create passwordless user
        CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), "test@example.com",
                "+123456789012",
                null, null);
        userIds.add(Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId,
                createCodeResponse.deviceIdHash,
                createCodeResponse.userInputCode, null).user.getSupertokensUserId());

        // test retrieving a user with a phoneNumber and provider
        {
            ArrayList<String> phoneNumberList = new ArrayList<>();
            phoneNumberList.add("+1234");
            ArrayList<String> providerList = new ArrayList<>();
            providerList.add("testTPID");

            DashboardSearchTags tags = new DashboardSearchTags(null, phoneNumberList, providerList);
            UserPaginationContainer info = AuthRecipe.getUsers(process.getProcess(), 10, "ASC", null, null, tags);

            assertEquals(0, info.users.length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSearchParamRegex() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create emailpassword user
        ArrayList<String> userIds = new ArrayList<>();
        userIds.add(
                EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123").getSupertokensUserId());
        userIds.add(
                EmailPassword.signUp(process.getProcess(), "abc@example.com", "testPass123").getSupertokensUserId());
        userIds.add(EmailPassword.signUp(process.getProcess(), "user@abc.com", "testPass123").getSupertokensUserId());

        // create thirdparty user
        userIds.add(ThirdParty.signInUp(process.getProcess(), "testTPID", "test",
                "test2@example.com").user.getSupertokensUserId());

        // create passwordless user
        CreateCodeResponse createCodeResponse = Passwordless.createCode(process.getProcess(), "test@example.com",
                "+123456789012",
                null, null);
        userIds.add(Passwordless.consumeCode(process.getProcess(), createCodeResponse.deviceId,
                createCodeResponse.deviceIdHash,
                createCodeResponse.userInputCode, null).user.getSupertokensUserId());

        // regex for emails: email* and *@email*
        {

            {
                // retrieve emails for users whose email start with "test" or have "@test"
                // domain
                ArrayList<String> emailList = new ArrayList<>();
                emailList.add("test");

                DashboardSearchTags tags = new DashboardSearchTags(emailList, null, null);
                UserPaginationContainer info = AuthRecipe.getUsers(process.getProcess(), 10, "ASC", null, null, tags);
                assertEquals(3, info.users.length);
                assertEquals(userIds.get(0), info.users[0].getSupertokensUserId());
                assertEquals(userIds.get(3), info.users[1].getSupertokensUserId());
                assertEquals(userIds.get(4), info.users[2].getSupertokensUserId());
            }

            // retrieve emails for users whose email starts with abc or have domain abc
            {
                // retrieve emails for users whose email start with "test" or have "@test"
                // domain
                ArrayList<String> emailList = new ArrayList<>();
                emailList.add("abc");

                DashboardSearchTags tags = new DashboardSearchTags(emailList, null, null);
                UserPaginationContainer info = AuthRecipe.getUsers(process.getProcess(), 10, "ASC", null, null, tags);
                assertEquals(2, info.users.length);
                assertEquals(userIds.get(1), info.users[0].getSupertokensUserId());
                assertEquals(userIds.get(2), info.users[1].getSupertokensUserId());

            }

            // search for phone number
            {
                {
                    ArrayList<String> phoneList = new ArrayList<>();
                    phoneList.add("+123");

                    DashboardSearchTags tags = new DashboardSearchTags(null, phoneList, null);
                    UserPaginationContainer info = AuthRecipe.getUsers(process.getProcess(), 10, "ASC", null, null,
                            tags);
                    assertEquals(1, info.users.length);
                    assertEquals(userIds.get(4), info.users[0].getSupertokensUserId());
                }

            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatQueryLimitIsCappedAt1000PerTable() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create 1005 emailpassword users
        ArrayList<String> userIds = new ArrayList<>();

        for (int i = 0; i < 1005; i++) {
            userIds.add(EmailPassword.signUp(process.getProcess(), "test" + i + "@example.com", "testPass123")
                    .getSupertokensUserId());
            Thread.sleep(10);
        }

        // retrieve users
        ArrayList<String> emailList = new ArrayList<>();
        emailList.add("test");

        DashboardSearchTags tags = new DashboardSearchTags(emailList, null, null);
        UserPaginationContainer info = AuthRecipe.getUsers(process.getProcess(), 10, "ASC", null, null, tags);
        assertEquals(1000, info.users.length);
        for (int i = 0; i < info.users.length; i++) {
            assertTrue(userIds.contains(info.users[i].getSupertokensUserId()));

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
