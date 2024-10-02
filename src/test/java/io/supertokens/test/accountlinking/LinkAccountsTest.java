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

import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.exception.AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.InputUserIdIsNotAPrimaryUserException;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.session.Session;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.webserver.WebserverAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class LinkAccountsTest {
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
    public void linkAccountSuccess() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        assert (!user.isPrimaryUser);

        Thread.sleep(50);

        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        assert (!user2.isPrimaryUser);

        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        Session.createNewSession(process.main, user2.getSupertokensUserId(), new JsonObject(), new JsonObject());
        String[] sessions = Session.getAllNonExpiredSessionHandlesForUser(process.main, user2.getSupertokensUserId());
        assert (sessions.length == 1);

        boolean wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(),
                user.getSupertokensUserId()).wasAlreadyLinked;
        assert (!wasAlreadyLinked);

        AuthRecipeUserInfo refetchUser2 = AuthRecipe.getUserById(process.main, user2.getSupertokensUserId());
        AuthRecipeUserInfo refetchUser = AuthRecipe.getUserById(process.main, user.getSupertokensUserId());
        assert (refetchUser2.equals(refetchUser));
        assert (refetchUser2.loginMethods.length == 2);
        assert (refetchUser.loginMethods[0].equals(user.loginMethods[0]));
        assert (refetchUser.loginMethods[1].equals(user2.loginMethods[0]));
        assert (refetchUser.tenantIds.size() == 1);
        assert (refetchUser.isPrimaryUser);
        assert (refetchUser.getSupertokensUserId().equals(user.getSupertokensUserId()));

        // cause linkAccounts revokes sessions for the recipe user ID
        sessions = Session.getAllNonExpiredSessionHandlesForUser(process.main, user2.getSupertokensUserId());
        assert (sessions.length == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkAccountSuccessWithSameEmail() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        assert (!user.isPrimaryUser);

        Thread.sleep(50);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.getProcess(), "google",
                "user-google",
                "test@example.com");
        AuthRecipeUserInfo user2 = signInUpResponse.user;
        assert (!user2.isPrimaryUser);

        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        Session.createNewSession(process.main, user2.getSupertokensUserId(), new JsonObject(), new JsonObject());
        String[] sessions = Session.getAllNonExpiredSessionHandlesForUser(process.main, user2.getSupertokensUserId());
        assert (sessions.length == 1);

        boolean wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(),
                user.getSupertokensUserId()).wasAlreadyLinked;
        assert (!wasAlreadyLinked);

        AuthRecipeUserInfo refetchUser2 = AuthRecipe.getUserById(process.main, user2.getSupertokensUserId());
        AuthRecipeUserInfo refetchUser = AuthRecipe.getUserById(process.main, user.getSupertokensUserId());
        assert (refetchUser2.equals(refetchUser));
        assert (refetchUser2.loginMethods.length == 2);
        assert (refetchUser.loginMethods[0].equals(user.loginMethods[0]));
        assert (refetchUser.loginMethods[1].equals(user2.loginMethods[0]));
        assert (refetchUser.tenantIds.size() == 1);
        assert (refetchUser.isPrimaryUser);
        assert (refetchUser.getSupertokensUserId().equals(user.getSupertokensUserId()));

        // cause linkAccounts revokes sessions for the recipe user ID
        sessions = Session.getAllNonExpiredSessionHandlesForUser(process.main, user2.getSupertokensUserId());
        assert (sessions.length == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatLinkingAccountsRequiresAccountLinkingFeatureToBeEnabled() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        try {
            AuthRecipe.linkAccounts(process.main, "", "");
            assert (false);
        } catch (FeatureNotEnabledException e) {
            assert (e.getMessage()
                    .equals("Account linking feature is not enabled for this app. Please contact support to enable it" +
                            "."));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkAccountSuccessEvenIfUsingRecipeUserIdThatIsLinkedToPrimaryUser() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        assert (!user.isPrimaryUser);

        Thread.sleep(50);

        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        assert (!user2.isPrimaryUser);

        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        boolean wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(),
                user.getSupertokensUserId()).wasAlreadyLinked;
        assert (!wasAlreadyLinked);

        AuthRecipeUserInfo user3 = EmailPassword.signUp(process.getProcess(), "test3@example.com", "password");
        assert (!user3.isPrimaryUser);

        wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user3.getSupertokensUserId(),
                user2.getSupertokensUserId()).wasAlreadyLinked;
        assert (!wasAlreadyLinked);

        AuthRecipeUserInfo refetchUser = AuthRecipe.getUserById(process.main, user.getSupertokensUserId());
        assert (refetchUser.loginMethods.length == 3);
        assert (refetchUser.loginMethods[0].equals(user.loginMethods[0]));
        assert (refetchUser.loginMethods[1].equals(user2.loginMethods[0]));
        assert (refetchUser.loginMethods[2].equals(user3.loginMethods[0]));
        assert (refetchUser.tenantIds.size() == 1);
        assert (refetchUser.isPrimaryUser);
        assert (refetchUser.getSupertokensUserId().equals(user.getSupertokensUserId()));

        AuthRecipeUserInfo refetchUser3 = AuthRecipe.getUserById(process.main, user3.getSupertokensUserId());
        assert (refetchUser3.equals(refetchUser));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void alreadyLinkAccountLinkAgain() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        assert (!user.isPrimaryUser);

        Thread.sleep(50);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.getProcess(), "google",
                "user-google",
                "test@example.com");
        AuthRecipeUserInfo user2 = signInUpResponse.user;
        assert (!user2.isPrimaryUser);

        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        boolean wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(),
                user.getSupertokensUserId()).wasAlreadyLinked;
        assert (!wasAlreadyLinked);

        Session.createNewSession(process.main, user2.getSupertokensUserId(), new JsonObject(), new JsonObject());
        String[] sessions = Session.getAllNonExpiredSessionHandlesForUser(process.main, user2.getSupertokensUserId());
        assert (sessions.length == 1);

        wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(),
                user.getSupertokensUserId()).wasAlreadyLinked;
        assert (wasAlreadyLinked);

        // cause linkAccounts revokes sessions for the recipe user ID
        sessions = Session.getAllNonExpiredSessionHandlesForUser(process.main, user2.getSupertokensUserId());
        assert (sessions.length == 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkAccountFailureCauseRecipeUserIdLinkedWithAnotherPrimaryUser() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        assert (!user.isPrimaryUser);

        Thread.sleep(50);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.getProcess(), "google",
                "user-google",
                "test@example.com");
        AuthRecipeUserInfo user2 = signInUpResponse.user;
        assert (!user2.isPrimaryUser);

        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        boolean wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(),
                user.getSupertokensUserId()).wasAlreadyLinked;
        assert (!wasAlreadyLinked);

        AuthRecipeUserInfo user3 = EmailPassword.signUp(process.getProcess(), "test3@example.com", "password");
        assert (!user.isPrimaryUser);
        AuthRecipe.createPrimaryUser(process.main, user3.getSupertokensUserId());

        try {
            AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(), user3.getSupertokensUserId());
            assert (false);
        } catch (RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException e) {
            assert (e.recipeUser.getSupertokensUserId().equals(user.getSupertokensUserId()));
            assert (e.getMessage().equals("The input recipe user ID is already linked to another user ID"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkAccountFailureInputUserIsNotAPrimaryUser() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        assert (!user.isPrimaryUser);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.getProcess(), "google",
                "user-google",
                "test@example.com");
        AuthRecipeUserInfo user2 = signInUpResponse.user;
        assert (!user2.isPrimaryUser);

        try {
            AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(), user.getSupertokensUserId());
            assert (false);
        } catch (InputUserIdIsNotAPrimaryUserException e) {
            assert (e.userId.equals(user.getSupertokensUserId()));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkAccountFailureUserDoesNotExist() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        assert (!user.isPrimaryUser);

        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.getProcess(), "google",
                "user-google",
                "test@example.com");
        AuthRecipeUserInfo user2 = signInUpResponse.user;
        assert (!user2.isPrimaryUser);

        try {
            AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(), "random");
            assert (false);
        } catch (UnknownUserIdException e) {
        }

        try {
            AuthRecipe.linkAccounts(process.main, "random2", user.getSupertokensUserId());
            assert (false);
        } catch (UnknownUserIdException e) {
        }

        try {
            AuthRecipe.linkAccounts(process.main, "random2", "random");
            assert (false);
        } catch (UnknownUserIdException e) {
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkAccountFailureCauseAccountInfoAssociatedWithAPrimaryUser() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        assert (!user.isPrimaryUser);
        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        Thread.sleep(50);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.getProcess(), "google",
                "user-google",
                "test@example.com");
        AuthRecipeUserInfo user2 = signInUpResponse.user;
        assert (!user2.isPrimaryUser);

        AuthRecipeUserInfo otherPrimaryUser = EmailPassword.signUp(process.getProcess(), "test3@example.com",
                "password");

        AuthRecipe.createPrimaryUser(process.main, otherPrimaryUser.getSupertokensUserId());

        try {
            AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(),
                    otherPrimaryUser.getSupertokensUserId());
            assert (false);
        } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            assert (e.primaryUserId.equals(user.getSupertokensUserId()));
            assert (e.getMessage().equals("This user's email is already associated with another user ID"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkAccountFailureCauseAccountInfoAssociatedWithAPrimaryUserEvenIfInDifferentTenant() throws Exception {
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

        Multitenancy.addNewOrUpdateAppOrTenant(process.main, new TenantIdentifier(null, null, null),
                new TenantConfig(new TenantIdentifier(null, null, "t1"), new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null), new PasswordlessConfig(true),
                        null, null, new JsonObject()));

        Storage storage = (StorageLayer.getStorage(process.main));

        AuthRecipeUserInfo user =
                EmailPassword.signUp(new TenantIdentifier(null, null, "t1"), storage,
                        process.getProcess(),
                        "test@example.com", "password");
        assert (!user.isPrimaryUser);
        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        Thread.sleep(50);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(
                new TenantIdentifier(null, null, "t1"), storage,
                process.getProcess(), "google",
                "user-google",
                "test@example.com");
        AuthRecipeUserInfo user2 = signInUpResponse.user;
        assert (!user2.isPrimaryUser);

        AuthRecipeUserInfo otherPrimaryUser = EmailPassword.signUp(process.getProcess(), "test3@example.com",
                "password");

        AuthRecipe.createPrimaryUser(process.main, otherPrimaryUser.getSupertokensUserId());

        try {
            AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(),
                    otherPrimaryUser.getSupertokensUserId());
            assert (false);
        } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            assert (e.primaryUserId.equals(user.getSupertokensUserId()));
            assert (e.getMessage().equals("This user's email is already associated with another user ID"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkAccountFailureCauseAccountInfoAssociatedWithAPrimaryUserEvenIfInDifferentTenant2() throws Exception {
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

        Multitenancy.addNewOrUpdateAppOrTenant(process.main, new TenantIdentifier(null, null, null),
                new TenantConfig(new TenantIdentifier(null, null, "t1"), new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null), new PasswordlessConfig(true),
                        null, null, new JsonObject()));

        Multitenancy.addNewOrUpdateAppOrTenant(process.main, new TenantIdentifier(null, null, null),
                new TenantConfig(new TenantIdentifier(null, null, "t2"), new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null), new PasswordlessConfig(true),
                        null, null, new JsonObject()));

        Storage storage = (StorageLayer.getStorage(process.main));

        AuthRecipeUserInfo conflictingUser =
                EmailPassword.signUp(new TenantIdentifier(null, null, "t2"), storage,
                        process.getProcess(),
                        "test@example.com", "password");
        assert (!conflictingUser.isPrimaryUser);
        AuthRecipe.createPrimaryUser(process.main, conflictingUser.getSupertokensUserId());

        Thread.sleep(50);

        AuthRecipeUserInfo user1 =
                EmailPassword.signUp(new TenantIdentifier(null, null, "t1"), storage,
                        process.getProcess(),
                        "test@example.com", "password");
        assert (!user1.isPrimaryUser);

        AuthRecipeUserInfo user2 =
                EmailPassword.signUp(new TenantIdentifier(null, null, "t2"), storage,
                        process.getProcess(),
                        "test2@example.com", "password");
        assert (!user1.isPrimaryUser);


        AuthRecipe.createPrimaryUser(process.main, user1.getSupertokensUserId());

        try {
            AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(),
                    user1.getSupertokensUserId());
            assert (false);
        } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            assert (e.primaryUserId.equals(conflictingUser.getSupertokensUserId()));
            assert (e.getMessage().equals("This user's email is already associated with another user ID"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkAccountSuccessAcrossTenants() throws Exception {
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

        Multitenancy.addNewOrUpdateAppOrTenant(process.main, new TenantIdentifier(null, null, null),
                new TenantConfig(new TenantIdentifier(null, null, "t1"), new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[0]), new PasswordlessConfig(true),
                        null, null, new JsonObject()));

        Storage storage = (StorageLayer.getStorage(process.main));

        AuthRecipeUserInfo user = EmailPassword.signUp(new TenantIdentifier(null, null, "t1"),
                storage, process.getProcess(),
                "test@example.com", "password");
        assert (!user.isPrimaryUser);
        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        Thread.sleep(50);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(
                process.getProcess(), "google",
                "user-google",
                "test@example.com");
        AuthRecipeUserInfo user2 = signInUpResponse.user;
        assert (!user2.isPrimaryUser);

        boolean wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(),
                user.getSupertokensUserId()).wasAlreadyLinked;
        assert (!wasAlreadyLinked);

        AuthRecipeUserInfo refetchedUser1 = AuthRecipe.getUserById(process.main, user.getSupertokensUserId());
        AuthRecipeUserInfo refetchedUser2 = AuthRecipe.getUserById(process.main, user2.getSupertokensUserId());
        assert (refetchedUser1.getSupertokensUserId().equals(refetchedUser2.getSupertokensUserId()));
        assert refetchedUser1.loginMethods.length == 2;
        assert refetchedUser1.tenantIds.size() == 2;
        assert refetchedUser1.tenantIds.contains("t1");
        assert refetchedUser1.tenantIds.contains("public");
        assert refetchedUser1.getSupertokensUserId().equals(user.getSupertokensUserId());
        assert refetchedUser1.isPrimaryUser;
        assert refetchedUser1.loginMethods[0].getSupertokensUserId()
                .equals(user.loginMethods[0].getSupertokensUserId());
        assert refetchedUser1.loginMethods[1].getSupertokensUserId()
                .equals(user2.loginMethods[0].getSupertokensUserId());


        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkAccountSuccessWithPasswordlessEmailAndPhoneNumber() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        assert (!user.isPrimaryUser);

        Thread.sleep(50);

        Passwordless.CreateCodeResponse code = Passwordless.createCode(process.getProcess(), "u@e.com", null, null,
                null);
        Passwordless.ConsumeCodeResponse pResp = Passwordless.consumeCode(process.getProcess(), code.deviceId,
                code.deviceIdHash, code.userInputCode, null);
        AuthRecipeUserInfo user2 = pResp.user;
        assert (!user2.isPrimaryUser);

        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        Passwordless.updateUser(process.main, user2.getSupertokensUserId(), null, new Passwordless.FieldUpdate("1234"));
        user2 = AuthRecipe.getUserById(process.main, user2.getSupertokensUserId());

        boolean wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user2.getSupertokensUserId(),
                user.getSupertokensUserId()).wasAlreadyLinked;
        assert (!wasAlreadyLinked);

        AuthRecipeUserInfo refetchUser2 = AuthRecipe.getUserById(process.main, user2.getSupertokensUserId());
        AuthRecipeUserInfo refetchUser = AuthRecipe.getUserById(process.main, user.getSupertokensUserId());
        assert (refetchUser2.equals(refetchUser));
        assert (refetchUser2.loginMethods.length == 2);
        assert (refetchUser.loginMethods[0].equals(user.loginMethods[0]));
        assert (refetchUser.loginMethods[1].equals(user2.loginMethods[0]));
        assert (refetchUser.tenantIds.size() == 1);
        assert (refetchUser.isPrimaryUser);
        assert (refetchUser.getSupertokensUserId().equals(user.getSupertokensUserId()));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkAccountMergesLastActiveTimes() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        assert (!user.isPrimaryUser);
        ActiveUsers.updateLastActive(process.main, user.getSupertokensUserId());
        Thread.sleep(50);

        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        assert (!user2.isPrimaryUser);
        long secondUserTime = System.currentTimeMillis();
        ActiveUsers.updateLastActive(process.main, user2.getSupertokensUserId());

        assertEquals(ActiveUsers.countUsersActiveSince(process.main, 0), 2);
        long createPrimaryTime = System.currentTimeMillis();
        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());
        Thread.sleep(50);

        assertEquals(ActiveUsers.countUsersActiveSince(process.main, 0), 2);

        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", user2.getSupertokensUserId());
            params.addProperty("primaryUserId", user.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/link", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
        }

        assertEquals(ActiveUsers.countUsersActiveSince(process.main, 0), 1);
        assertEquals(ActiveUsers.countUsersActiveSince(process.main, secondUserTime), 1);
        assertEquals(ActiveUsers.countUsersActiveSince(process.main, createPrimaryTime),
                1); // 1 since we update user last active while linking

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void linkAccountMergesLastActiveTimes_PrimaryFirst() throws Exception {
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        assert (!user.isPrimaryUser);
        ActiveUsers.updateLastActive(process.main, user.getSupertokensUserId());
        Thread.sleep(50);

        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password");
        assert (!user2.isPrimaryUser);
        long secondUserTime = System.currentTimeMillis();
        ActiveUsers.updateLastActive(process.main, user2.getSupertokensUserId());

        assertEquals(ActiveUsers.countUsersActiveSince(process.main, 0), 2);
        long createPrimaryTime = System.currentTimeMillis();
        AuthRecipe.createPrimaryUser(process.main, user2.getSupertokensUserId());
        Thread.sleep(50);

        assertEquals(ActiveUsers.countUsersActiveSince(process.main, 0), 2);

        {
            JsonObject params = new JsonObject();
            params.addProperty("recipeUserId", user.getSupertokensUserId());
            params.addProperty("primaryUserId", user2.getSupertokensUserId());

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/accountlinking/user/link", params, 1000, 1000, null,
                    WebserverAPI.getLatestCDIVersion().get(), "");
            assertEquals(3, response.entrySet().size());
            assertEquals("OK", response.get("status").getAsString());
        }

        assertEquals(ActiveUsers.countUsersActiveSince(process.main, 0), 1);
        assertEquals(ActiveUsers.countUsersActiveSince(process.main, secondUserTime), 1);
        assertEquals(ActiveUsers.countUsersActiveSince(process.main, createPrimaryTime),
                1); // 1 since we update user last active while linking

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
