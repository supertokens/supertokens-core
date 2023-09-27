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
import io.supertokens.authRecipe.UserPaginationContainer;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.dashboard.DashboardSearchTags;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TimeJoinedTest {
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
    public void testThatTimeJoinedIsCorrectWhileLinkingAndUnlinking() throws Exception {
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
        Thread.sleep(100);
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");

        AuthRecipe.createPrimaryUser(process.getProcess(), user2.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user1.getSupertokensUserId(), user2.getSupertokensUserId());

        {
            AuthRecipeUserInfo userInfo = AuthRecipe.getUserById(process.getProcess(), user2.getSupertokensUserId());
            assertEquals(user1.timeJoined, userInfo.timeJoined);
        }

        AuthRecipe.unlinkAccounts(process.getProcess(), user1.getSupertokensUserId());

        {
            AuthRecipeUserInfo userInfo = AuthRecipe.getUserById(process.getProcess(), user2.getSupertokensUserId());
            assertEquals(user2.timeJoined, userInfo.timeJoined);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatTimeJoinedIsCorrectWhileAssociatingTenants() throws Exception {
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
        Thread.sleep(100);
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");

        AuthRecipe.createPrimaryUser(process.getProcess(), user2.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user1.getSupertokensUserId(), user2.getSupertokensUserId());

        {
            AuthRecipeUserInfo userInfo = AuthRecipe.getUserById(process.getProcess(), user2.getSupertokensUserId());
            assertEquals(user1.timeJoined, userInfo.timeJoined);
        }

        TenantIdentifierWithStorage baseTenant = TenantIdentifier.BASE_TENANT.withStorage(StorageLayer.getStorage(process.getProcess()));

        Multitenancy.removeUserIdFromTenant(process.getProcess(), baseTenant, user1.getSupertokensUserId(), null);
        Multitenancy.removeUserIdFromTenant(process.getProcess(), baseTenant, user2.getSupertokensUserId(), null);

        {
            AuthRecipeUserInfo userInfo = AuthRecipe.getUserById(process.getProcess(), user2.getSupertokensUserId());
            assertEquals(user1.timeJoined, userInfo.timeJoined);
        }

        Multitenancy.addUserIdToTenant(process.getProcess(), baseTenant, user2.getSupertokensUserId());

        {
            AuthRecipeUserInfo userInfo = AuthRecipe.getUserById(process.getProcess(), user2.getSupertokensUserId());
            assertEquals(user1.timeJoined, userInfo.timeJoined);
        }

        Multitenancy.addUserIdToTenant(process.getProcess(), baseTenant, user1.getSupertokensUserId());

        {
            AuthRecipeUserInfo userInfo = AuthRecipe.getUserById(process.getProcess(), user2.getSupertokensUserId());
            assertEquals(user1.timeJoined, userInfo.timeJoined);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUserPaginationIsFineWithUnlinkAndUnlinkAccounts() throws Exception {
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
        Thread.sleep(100);
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");

        AuthRecipe.createPrimaryUser(process.getProcess(), user2.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user1.getSupertokensUserId(), user2.getSupertokensUserId());

        TenantIdentifierWithStorage baseTenant = TenantIdentifier.BASE_TENANT.withStorage(StorageLayer.getStorage(process.getProcess()));

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 10, "DESC",
                    null, null, null);
            assertEquals(1, users.users.length);
        }

        AuthRecipe.unlinkAccounts(process.getProcess(), user1.getSupertokensUserId());

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 10, "DESC",
                    null, null, null);
            assertEquals(2, users.users.length);
        }

        AuthRecipe.linkAccounts(process.getProcess(), user1.getSupertokensUserId(), user2.getSupertokensUserId());

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 10, "DESC",
                    null, null, null);
            assertEquals(1, users.users.length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUserPaginationIsFineWithTenantAssociation() throws Exception {
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
        Thread.sleep(100);
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");

        AuthRecipe.createPrimaryUser(process.getProcess(), user2.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user1.getSupertokensUserId(), user2.getSupertokensUserId());

        TenantIdentifierWithStorage baseTenant = TenantIdentifier.BASE_TENANT.withStorage(StorageLayer.getStorage(process.getProcess()));

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 10, "DESC",
                    null, null, null);
            assertEquals(1, users.users.length);
        }

        Multitenancy.removeUserIdFromTenant(process.getProcess(), baseTenant, user1.getSupertokensUserId(), null);

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 10, "DESC",
                    null, null, null);
            assertEquals(1, users.users.length);
        }

        Multitenancy.addUserIdToTenant(process.getProcess(), baseTenant, user1.getSupertokensUserId());

        {
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 10, "DESC",
                    null, null, null);
            assertEquals(1, users.users.length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUserSearchWorksWithUnlinkAndLinkAccounts() throws Exception {
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
        Thread.sleep(100);
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");

        AuthRecipe.createPrimaryUser(process.getProcess(), user2.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user1.getSupertokensUserId(), user2.getSupertokensUserId());

        TenantIdentifierWithStorage baseTenant = TenantIdentifier.BASE_TENANT.withStorage(StorageLayer.getStorage(process.getProcess()));

        {
            ArrayList<String> emails = new ArrayList<>();
            emails.add("test");
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 10, "DESC",
                    null, null, new DashboardSearchTags(emails, null, null));
            assertEquals(1, users.users.length);
        }

        AuthRecipe.unlinkAccounts(process.getProcess(), user1.getSupertokensUserId());

        {
            ArrayList<String> emails = new ArrayList<>();
            emails.add("test");
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 10, "DESC",
                    null, null, new DashboardSearchTags(emails, null, null));
            assertEquals(2, users.users.length);
        }

        AuthRecipe.linkAccounts(process.getProcess(), user1.getSupertokensUserId(), user2.getSupertokensUserId());

        {
            ArrayList<String> emails = new ArrayList<>();
            emails.add("test");
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 10, "DESC",
                    null, null, new DashboardSearchTags(emails, null, null));
            assertEquals(1, users.users.length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUserSearchWorksWithTenantAssociation() throws Exception {
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
        Thread.sleep(100);
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");

        AuthRecipe.createPrimaryUser(process.getProcess(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        TenantIdentifierWithStorage baseTenant = TenantIdentifier.BASE_TENANT.withStorage(StorageLayer.getStorage(process.getProcess()));

        {
            ArrayList<String> emails = new ArrayList<>();
            emails.add("test");
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 10, "DESC",
                    null, null, new DashboardSearchTags(emails, null, null));
            assertEquals(1, users.users.length);
        }

        Multitenancy.removeUserIdFromTenant(process.getProcess(), baseTenant, user2.getSupertokensUserId(), null);

        {
            ArrayList<String> emails = new ArrayList<>();
            emails.add("test");
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 10, "DESC",
                    null, null, new DashboardSearchTags(emails, null, null));
            assertEquals(1, users.users.length);
        }

        Multitenancy.addUserIdToTenant(process.getProcess(), baseTenant, user2.getSupertokensUserId());

        {
            ArrayList<String> emails = new ArrayList<>();
            emails.add("test");
            UserPaginationContainer users = AuthRecipe.getUsers(process.getProcess(), 10, "DESC",
                    null, null, new DashboardSearchTags(emails, null, null));
            assertEquals(1, users.users.length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
