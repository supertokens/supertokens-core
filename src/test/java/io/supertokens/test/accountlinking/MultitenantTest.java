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
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.userroles.UserRoles;
import org.junit.Test;

import static org.junit.Assert.*;

public class MultitenantTest extends MultitenantTestBase {

    @Test
    public void testUserAreNotAutomaticallySharedBetweenTenantsOfLinkedAccountsForPless() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants(process.getProcess());

        t1 = new io.supertokens.pluginInterface.multitenancy.TenantIdentifier(null, "a1", null);
        t2 = new io.supertokens.pluginInterface.multitenancy.TenantIdentifier(null, "a1", "t1");

        Storage t1Storage = (StorageLayer.getStorage(t1, process.getProcess()));
        Storage t2Storage = (StorageLayer.getStorage(t2, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1, t1Storage, process.getProcess(), "test@example.com",
                "password");

        Passwordless.CreateCodeResponse user2Code = Passwordless.createCode(t1, t1Storage, process.getProcess(),
                "test@example.com", null, null, null);
        AuthRecipeUserInfo user2 = Passwordless.consumeCode(t1, t1Storage, process.getProcess(), user2Code.deviceId,
                user2Code.deviceIdHash, user2Code.userInputCode, null).user;

        AuthRecipe.createPrimaryUser(process.getProcess(), t1.toAppIdentifier(), t1Storage,
                user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), t1.toAppIdentifier(), t1Storage, user2.getSupertokensUserId(),
                user1.getSupertokensUserId());

        Multitenancy.addUserIdToTenant(process.getProcess(), t2, t2Storage, user1.getSupertokensUserId());

        {   // user2 should not be shared in tenant2
            Passwordless.CreateCodeResponse user3Code = Passwordless.createCode(t2, t2Storage, process.getProcess(),
                    "test@example.com", null, null, null);
            Passwordless.ConsumeCodeResponse res = Passwordless.consumeCode(t2, t2Storage, process.getProcess(),
                    user3Code.deviceId, user3Code.deviceIdHash, user3Code.userInputCode, null);
            assertTrue(res.createdNewUser);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUserAreNotAutomaticallySharedBetweenTenantsOfLinkedAccountsForTP() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants(process.getProcess());

        t1 = new io.supertokens.pluginInterface.multitenancy.TenantIdentifier(null, "a1", null);
        t2 = new io.supertokens.pluginInterface.multitenancy.TenantIdentifier(null, "a1", "t1");

        Storage t1Storage = (StorageLayer.getStorage(t1, process.getProcess()));
        Storage t2Storage = (StorageLayer.getStorage(t2, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1, t1Storage, process.getProcess(), "test@example.com",
                "password");
        AuthRecipeUserInfo user2 = ThirdParty.signInUp(t1, t1Storage, process.getProcess(), "google", "googleid1",
                "test@example.com").user;

        AuthRecipe.createPrimaryUser(process.getProcess(), t1.toAppIdentifier(), t1Storage,
                user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), t1.toAppIdentifier(), t1Storage, user2.getSupertokensUserId(),
                user1.getSupertokensUserId());

        Multitenancy.addUserIdToTenant(process.getProcess(), t2, t2Storage, user1.getSupertokensUserId());

        {   // user2 should not be shared in tenant2
            ThirdParty.SignInUpResponse res = ThirdParty.signInUp(t2, t2Storage, process.getProcess(), "google",
                    "googleid1", "test@example.com");
            assertTrue(res.createdNewUser);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testTenantDeletionWithAccountLinking() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants(process.getProcess());

        t1 = new io.supertokens.pluginInterface.multitenancy.TenantIdentifier(null, "a1", null);
        t2 = new io.supertokens.pluginInterface.multitenancy.TenantIdentifier(null, "a1", "t1");

        Storage t1Storage = (StorageLayer.getStorage(t1, process.getProcess()));
        Storage t2Storage = (StorageLayer.getStorage(t2, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t2, t2Storage, process.getProcess(), "test@example.com",
                "password");
        AuthRecipeUserInfo user2 = ThirdParty.signInUp(t2, t2Storage, process.getProcess(), "google", "googleid1",
                "test@example.com").user;

        AuthRecipe.createPrimaryUser(process.getProcess(), t2.toAppIdentifier(), t2Storage,
                user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), t2.toAppIdentifier(), t2Storage, user2.getSupertokensUserId(),
                user1.getSupertokensUserId());

        Multitenancy.deleteTenant(t2, process.getProcess());

        AuthRecipeUserInfo getUser1 = AuthRecipe.getUserById(t1.toAppIdentifier(), t1Storage,
                user1.getSupertokensUserId());
        for (LoginMethod lm : getUser1.loginMethods) {
            assertEquals(0, lm.tenantIds.size());
        }

        AuthRecipeUserInfo getUser2 = AuthRecipe.getUserById(t1.toAppIdentifier(), t1Storage,
                user2.getSupertokensUserId());
        for (LoginMethod lm : getUser2.loginMethods) {
            assertEquals(0, lm.tenantIds.size());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testTenantDeletionWithAccountLinkingWithUserRoles() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants(process.getProcess());

        t1 = new io.supertokens.pluginInterface.multitenancy.TenantIdentifier(null, "a1", null);
        t2 = new io.supertokens.pluginInterface.multitenancy.TenantIdentifier(null, "a1", "t1");

        Storage t1Storage = (StorageLayer.getStorage(t1, process.getProcess()));
        Storage t2Storage = (StorageLayer.getStorage(t2, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t2, t2Storage, process.getProcess(), "test@example.com",
                "password");
        AuthRecipeUserInfo user2 = ThirdParty.signInUp(t2, t2Storage, process.getProcess(), "google", "googleid1",
                "test@example.com").user;

        AuthRecipe.createPrimaryUser(process.getProcess(), t2.toAppIdentifier(), t2Storage,
                user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), t2.toAppIdentifier(), t2Storage, user2.getSupertokensUserId(),
                user1.getSupertokensUserId());

        UserRoles.createNewRoleOrModifyItsPermissions(t2.toAppIdentifier(), t2Storage, "admin", new String[]{"p1"});
        UserRoles.addRoleToUser(process.getProcess(), t2, t2Storage, user1.getSupertokensUserId(), "admin");

        Multitenancy.deleteTenant(t2, process.getProcess());

        createTenants(process.getProcess()); // create the tenant again

        Multitenancy.addUserIdToTenant(process.getProcess(), t2, t2Storage, user1.getSupertokensUserId()); // add
        // the user to the tenant again
        Multitenancy.addUserIdToTenant(process.getProcess(), t2, t2Storage, user2.getSupertokensUserId()); // add
        // the user to the tenant again

        AuthRecipeUserInfo getUser1 = AuthRecipe.getUserById(t1.toAppIdentifier(), t1Storage,
                user1.getSupertokensUserId());
        for (LoginMethod lm : getUser1.loginMethods) {
            assertEquals(1, lm.tenantIds.size());
        }

        AuthRecipeUserInfo getUser2 = AuthRecipe.getUserById(t1.toAppIdentifier(), t1Storage,
                user2.getSupertokensUserId());
        for (LoginMethod lm : getUser2.loginMethods) {
            assertEquals(1, lm.tenantIds.size());
        }

        String[] roles = UserRoles.getRolesForUser(t2, t2Storage, user1.getSupertokensUserId());
        assertEquals(0, roles.length); // must be deleted with tenant

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
