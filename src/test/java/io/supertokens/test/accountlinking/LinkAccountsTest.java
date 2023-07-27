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
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.exception.AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException;
import io.supertokens.authRecipe.exception.InputUserIdIsNotAPrimaryUserException;
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.session.Session;
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

        AuthRecipe.createPrimaryUser(process.main, user.id);

        Session.createNewSession(process.main, user2.id, new JsonObject(), new JsonObject());
        String[] sessions = Session.getAllNonExpiredSessionHandlesForUser(process.main, user2.id);
        assert (sessions.length == 1);

        boolean wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user2.id, user.id);
        assert (!wasAlreadyLinked);

        AuthRecipeUserInfo refetchUser2 = AuthRecipe.getUserById(process.main, user2.id);
        AuthRecipeUserInfo refetchUser = AuthRecipe.getUserById(process.main, user.id);
        assert (refetchUser2.equals(refetchUser));
        assert (refetchUser2.loginMethods.length == 2);
        assert (refetchUser.loginMethods[0].equals(user.loginMethods[0]));
        assert (refetchUser.loginMethods[1].equals(user2.loginMethods[0]));
        assert (refetchUser.tenantIds.size() == 1);
        assert (refetchUser.isPrimaryUser);
        assert (refetchUser.id.equals(user.id));

        // cause linkAccounts revokes sessions for the recipe user ID
        sessions = Session.getAllNonExpiredSessionHandlesForUser(process.main, user2.id);
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

        AuthRecipe.createPrimaryUser(process.main, user.id);

        Session.createNewSession(process.main, user2.id, new JsonObject(), new JsonObject());
        String[] sessions = Session.getAllNonExpiredSessionHandlesForUser(process.main, user2.id);
        assert (sessions.length == 1);

        boolean wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user2.id, user.id);
        assert (!wasAlreadyLinked);

        AuthRecipeUserInfo refetchUser2 = AuthRecipe.getUserById(process.main, user2.id);
        AuthRecipeUserInfo refetchUser = AuthRecipe.getUserById(process.main, user.id);
        assert (refetchUser2.equals(refetchUser));
        assert (refetchUser2.loginMethods.length == 2);
        assert (refetchUser.loginMethods[0].equals(user.loginMethods[0]));
        assert (refetchUser.loginMethods[1].equals(user2.loginMethods[0]));
        assert (refetchUser.tenantIds.size() == 1);
        assert (refetchUser.isPrimaryUser);
        assert (refetchUser.id.equals(user.id));

        // cause linkAccounts revokes sessions for the recipe user ID
        sessions = Session.getAllNonExpiredSessionHandlesForUser(process.main, user2.id);
        assert (sessions.length == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatLinkingAccountsRequiresAccountLinkingFeatureToBeEnabled() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

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

        AuthRecipe.createPrimaryUser(process.main, user.id);

        boolean wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user2.id, user.id);
        assert (!wasAlreadyLinked);

        AuthRecipeUserInfo user3 = EmailPassword.signUp(process.getProcess(), "test3@example.com", "password");
        assert (!user3.isPrimaryUser);

        wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user3.id, user2.id);
        assert (!wasAlreadyLinked);

        AuthRecipeUserInfo refetchUser = AuthRecipe.getUserById(process.main, user.id);
        assert (refetchUser.loginMethods.length == 3);
        assert (refetchUser.loginMethods[0].equals(user.loginMethods[0]));
        assert (refetchUser.loginMethods[1].equals(user2.loginMethods[0]));
        assert (refetchUser.loginMethods[2].equals(user3.loginMethods[0]));
        assert (refetchUser.tenantIds.size() == 1);
        assert (refetchUser.isPrimaryUser);
        assert (refetchUser.id.equals(user.id));

        AuthRecipeUserInfo refetchUser3 = AuthRecipe.getUserById(process.main, user3.id);
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

        AuthRecipe.createPrimaryUser(process.main, user.id);

        boolean wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user2.id, user.id);
        assert (!wasAlreadyLinked);

        Session.createNewSession(process.main, user2.id, new JsonObject(), new JsonObject());
        String[] sessions = Session.getAllNonExpiredSessionHandlesForUser(process.main, user2.id);
        assert (sessions.length == 1);

        wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user2.id, user.id);
        assert (wasAlreadyLinked);

        // cause linkAccounts revokes sessions for the recipe user ID
        sessions = Session.getAllNonExpiredSessionHandlesForUser(process.main, user2.id);
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

        AuthRecipe.createPrimaryUser(process.main, user.id);

        boolean wasAlreadyLinked = AuthRecipe.linkAccounts(process.main, user2.id, user.id);
        assert (!wasAlreadyLinked);

        AuthRecipeUserInfo user3 = EmailPassword.signUp(process.getProcess(), "test3@example.com", "password");
        assert (!user.isPrimaryUser);
        AuthRecipe.createPrimaryUser(process.main, user3.id);

        try {
            AuthRecipe.linkAccounts(process.main, user2.id, user3.id);
            assert (false);
        } catch (RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException e) {
            assert (e.primaryUserId.equals(user.id));
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
            AuthRecipe.linkAccounts(process.main, user2.id, user.id);
            assert (false);
        } catch (InputUserIdIsNotAPrimaryUserException e) {
            assert (e.userId.equals(user.id));
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

        AuthRecipe.createPrimaryUser(process.main, user.id);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.getProcess(), "google",
                "user-google",
                "test@example.com");
        AuthRecipeUserInfo user2 = signInUpResponse.user;
        assert (!user2.isPrimaryUser);

        try {
            AuthRecipe.linkAccounts(process.main, user2.id, "random");
            assert (false);
        } catch (UnknownUserIdException e) {
        }

        try {
            AuthRecipe.linkAccounts(process.main, "random2", user.id);
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
        AuthRecipe.createPrimaryUser(process.main, user.id);

        Thread.sleep(50);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.getProcess(), "google",
                "user-google",
                "test@example.com");
        AuthRecipeUserInfo user2 = signInUpResponse.user;
        assert (!user2.isPrimaryUser);

        AuthRecipeUserInfo otherPrimaryUser = EmailPassword.signUp(process.getProcess(), "test3@example.com",
                "password");

        AuthRecipe.createPrimaryUser(process.main, otherPrimaryUser.id);

        try {
            AuthRecipe.linkAccounts(process.main, user2.id, otherPrimaryUser.id);
            assert (false);
        } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            assert (e.primaryUserId.equals(user.id));
            assert (e.getMessage().equals("This user's email is already associated with another user ID"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
