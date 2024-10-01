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
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithPrimaryUserIdException;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.multitenancy.*;
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


public class CreatePrimaryUserTest {
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
    public void testThatOnSignUpUserIsNotAPrimaryUser() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "abcd1234");
        assert (!user.isPrimaryUser);
        assert (user.loginMethods.length == 1);
        assert (user.loginMethods[0].recipeId == RECIPE_ID.EMAIL_PASSWORD);
        assert (user.loginMethods[0].email.equals("test@example.com"));
        assert (user.loginMethods[0].passwordHash != null);
        assert (user.loginMethods[0].thirdParty == null);
        assert (user.getSupertokensUserId().equals(user.loginMethods[0].getSupertokensUserId()));
        assert (user.loginMethods[0].phoneNumber == null);

        ThirdParty.SignInUpResponse resp = ThirdParty.signInUp(process.getProcess(), "google", "user-google",
                "test@example.com");
        assert (!resp.user.isPrimaryUser);
        assert (resp.user.loginMethods.length == 1);
        assert (resp.user.loginMethods[0].recipeId == RECIPE_ID.THIRD_PARTY);
        assert (resp.user.loginMethods[0].email.equals("test@example.com"));
        assert (resp.user.loginMethods[0].thirdParty.userId.equals("user-google"));
        assert (resp.user.loginMethods[0].thirdParty.id.equals("google"));
        assert (resp.user.loginMethods[0].phoneNumber == null);
        assert (resp.user.loginMethods[0].passwordHash == null);
        assert (resp.user.getSupertokensUserId().equals(resp.user.loginMethods[0].getSupertokensUserId()));

        {
            Passwordless.CreateCodeResponse code = Passwordless.createCode(process.getProcess(), "u@e.com", null, null,
                    null);
            Passwordless.ConsumeCodeResponse pResp = Passwordless.consumeCode(process.getProcess(), code.deviceId,
                    code.deviceIdHash, code.userInputCode, null);
            assert (!pResp.user.isPrimaryUser);
            assert (pResp.user.loginMethods.length == 1);
            assert (pResp.user.loginMethods[0].recipeId == RECIPE_ID.PASSWORDLESS);
            assert (pResp.user.loginMethods[0].email.equals("u@e.com"));
            assert (pResp.user.loginMethods[0].passwordHash == null);
            assert (pResp.user.loginMethods[0].thirdParty == null);
            assert (pResp.user.loginMethods[0].phoneNumber == null);
            assert (pResp.user.getSupertokensUserId().equals(pResp.user.loginMethods[0].getSupertokensUserId()));
        }

        {
            Passwordless.CreateCodeResponse code = Passwordless.createCode(process.getProcess(), null, "12345", null,
                    null);
            Passwordless.ConsumeCodeResponse pResp = Passwordless.consumeCode(process.getProcess(), code.deviceId,
                    code.deviceIdHash, code.userInputCode, null);
            assert (!pResp.user.isPrimaryUser);
            assert (pResp.user.loginMethods.length == 1);
            assert (pResp.user.loginMethods[0].recipeId == RECIPE_ID.PASSWORDLESS);
            assert (pResp.user.loginMethods[0].email == null);
            assert (pResp.user.loginMethods[0].passwordHash == null);
            assert (pResp.user.loginMethods[0].thirdParty == null);
            assert (pResp.user.loginMethods[0].phoneNumber.equals("12345"));
            assert (pResp.user.getSupertokensUserId().equals(pResp.user.loginMethods[0].getSupertokensUserId()));
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatCreationOfPrimaryUserRequiresAccountLinkingFeatureToBeEnabled() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.isInMemDb(process.getProcess())) {
            // Features are enabled by default for inmemory db
            return;
        }

        try {
            AuthRecipe.createPrimaryUser(process.main,
                    new AppIdentifier(null, null), StorageLayer.getStorage(process.main), "");
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
    public void makeEmailPasswordPrimaryUserSuccess() throws Exception {
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

        AuthRecipeUserInfo emailPasswordUser = EmailPassword.signUp(process.getProcess(), "test@example.com",
                "pass1234");

        AuthRecipe.CreatePrimaryUserResult result = AuthRecipe.createPrimaryUser(process.main,
                emailPasswordUser.getSupertokensUserId());
        assert (!result.wasAlreadyAPrimaryUser);
        assert (result.user.isPrimaryUser);
        assert (result.user.loginMethods.length == 1);
        assert (result.user.loginMethods[0].recipeId == RECIPE_ID.EMAIL_PASSWORD);
        assert (result.user.loginMethods[0].email.equals("test@example.com"));
        assert (result.user.loginMethods[0].passwordHash != null);
        assert (result.user.loginMethods[0].thirdParty == null);
        assert (result.user.getSupertokensUserId().equals(result.user.loginMethods[0].getSupertokensUserId()));
        assert (result.user.loginMethods[0].phoneNumber == null);

        AuthRecipeUserInfo refetchedUser = AuthRecipe.getUserById(process.main, result.user.getSupertokensUserId());

        assert (refetchedUser.equals(result.user));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void makeThirdPartyPrimaryUserSuccess() throws Exception {
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

        ThirdParty.SignInUpResponse signInUp = ThirdParty.signInUp(process.getProcess(), "google",
                "user-google",
                "test@example.com");

        AuthRecipe.CreatePrimaryUserResult result = AuthRecipe.createPrimaryUser(process.main,
                signInUp.user.getSupertokensUserId());
        assert (!result.wasAlreadyAPrimaryUser);
        assert (result.user.isPrimaryUser);
        assert (result.user.loginMethods.length == 1);
        assert (result.user.loginMethods[0].recipeId == RECIPE_ID.THIRD_PARTY);
        assert (result.user.loginMethods[0].email.equals("test@example.com"));
        assert (result.user.loginMethods[0].thirdParty.userId.equals("user-google"));
        assert (result.user.loginMethods[0].thirdParty.id.equals("google"));
        assert (result.user.loginMethods[0].phoneNumber == null);
        assert (result.user.loginMethods[0].passwordHash == null);
        assert (result.user.getSupertokensUserId().equals(result.user.loginMethods[0].getSupertokensUserId()));

        AuthRecipeUserInfo refetchedUser = AuthRecipe.getUserById(process.main, result.user.getSupertokensUserId());

        assert (refetchedUser.equals(result.user));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void makePasswordlessEmailPrimaryUserSuccess() throws Exception {
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

        Passwordless.CreateCodeResponse code = Passwordless.createCode(process.getProcess(), "u@e.com", null, null,
                null);
        Passwordless.ConsumeCodeResponse pResp = Passwordless.consumeCode(process.getProcess(), code.deviceId,
                code.deviceIdHash, code.userInputCode, null);

        AuthRecipe.CreatePrimaryUserResult result = AuthRecipe.createPrimaryUser(process.main,
                pResp.user.getSupertokensUserId());
        assert (!result.wasAlreadyAPrimaryUser);
        assert (result.user.isPrimaryUser);
        assert (result.user.loginMethods.length == 1);
        assert (result.user.loginMethods[0].recipeId == RECIPE_ID.PASSWORDLESS);
        assert (result.user.loginMethods[0].email.equals("u@e.com"));
        assert (result.user.loginMethods[0].passwordHash == null);
        assert (result.user.loginMethods[0].thirdParty == null);
        assert (result.user.loginMethods[0].phoneNumber == null);
        assert (result.user.getSupertokensUserId().equals(result.user.loginMethods[0].getSupertokensUserId()));

        AuthRecipeUserInfo refetchedUser = AuthRecipe.getUserById(process.main, result.user.getSupertokensUserId());

        assert (refetchedUser.equals(result.user));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void makePasswordlessPhonePrimaryUserSuccess() throws Exception {
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

        Passwordless.CreateCodeResponse code = Passwordless.createCode(process.getProcess(), null, "1234", null,
                null);
        Passwordless.ConsumeCodeResponse pResp = Passwordless.consumeCode(process.getProcess(), code.deviceId,
                code.deviceIdHash, code.userInputCode, null);

        AuthRecipe.CreatePrimaryUserResult result = AuthRecipe.createPrimaryUser(process.main,
                pResp.user.getSupertokensUserId());
        assert (!result.wasAlreadyAPrimaryUser);
        assert (result.user.isPrimaryUser);
        assert (result.user.loginMethods.length == 1);
        assert (result.user.loginMethods[0].recipeId == RECIPE_ID.PASSWORDLESS);
        assert (result.user.loginMethods[0].email == null);
        assert (result.user.loginMethods[0].passwordHash == null);
        assert (result.user.loginMethods[0].thirdParty == null);
        assert (result.user.loginMethods[0].phoneNumber.equals("1234"));
        assert (result.user.getSupertokensUserId().equals(result.user.loginMethods[0].getSupertokensUserId()));

        AuthRecipeUserInfo refetchedUser = AuthRecipe.getUserById(process.main, result.user.getSupertokensUserId());

        assert (refetchedUser.equals(result.user));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void alreadyPrimaryUsertest() throws Exception {
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

        AuthRecipeUserInfo emailPasswordUser = EmailPassword.signUp(process.getProcess(), "test@example.com",
                "pass1234");

        AuthRecipe.CreatePrimaryUserResult result = AuthRecipe.createPrimaryUser(process.main,
                emailPasswordUser.getSupertokensUserId());
        assert (!result.wasAlreadyAPrimaryUser);

        result = AuthRecipe.createPrimaryUser(process.main, emailPasswordUser.getSupertokensUserId());
        assert (result.wasAlreadyAPrimaryUser);
        assert (result.user.getSupertokensUserId().equals(emailPasswordUser.getSupertokensUserId()));
        assert (result.user.isPrimaryUser);
        assert (result.user.loginMethods.length == 1);
        assert (result.user.loginMethods[0].recipeId == RECIPE_ID.EMAIL_PASSWORD);
        assert (result.user.loginMethods[0].email.equals("test@example.com"));
        assert (result.user.loginMethods[0].passwordHash != null);
        assert (result.user.loginMethods[0].thirdParty == null);
        assert (result.user.getSupertokensUserId().equals(result.user.loginMethods[0].getSupertokensUserId()));
        assert (result.user.loginMethods[0].phoneNumber == null);

        AuthRecipeUserInfo refetchedUser = AuthRecipe.getUserById(process.main, result.user.getSupertokensUserId());

        assert (refetchedUser.equals(result.user));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void makePrimaryUserFailsCauseAnotherAccountWithSameEmailAlreadyAPrimaryUser() throws Exception {
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

        AuthRecipeUserInfo emailPasswordUser = EmailPassword.signUp(process.getProcess(), "test@example.com",
                "pass1234");

        AuthRecipe.CreatePrimaryUserResult result = AuthRecipe.createPrimaryUser(process.main,
                emailPasswordUser.getSupertokensUserId());
        assert (!result.wasAlreadyAPrimaryUser);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.main, "google", "user-google",
                "test@example.com");

        try {
            AuthRecipe.createPrimaryUser(process.main, signInUpResponse.user.getSupertokensUserId());
            assert (false);
        } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            assert (e.primaryUserId.equals(emailPasswordUser.getSupertokensUserId()));
            assert (e.getMessage().equals("This user's email is already associated with another user ID"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void makePrimarySucceedsEvenIfAnotherAccountWithSameEmailButIsNotAPrimaryUser() throws Exception {
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

        AuthRecipeUserInfo emailPasswordUser = EmailPassword.signUp(process.getProcess(), "test@example.com",
                "pass1234");

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.main, "google", "user-google",
                "test@example.com");

        AuthRecipe.CreatePrimaryUserResult r = AuthRecipe.createPrimaryUser(process.main,
                signInUpResponse.user.getSupertokensUserId());
        assert (!r.wasAlreadyAPrimaryUser);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void makePrimaryUserFailsCauseAnotherAccountWithSameEmailAlreadyAPrimaryUserInAnotherTenant()
            throws Exception {
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
        AuthRecipeUserInfo emailPasswordUser = EmailPassword.signUp(new TenantIdentifier(null, null, "t1"),
                storage, process.getProcess(),
                "test@example.com",
                "pass1234");

        AuthRecipe.CreatePrimaryUserResult result = AuthRecipe.createPrimaryUser(process.main,
                emailPasswordUser.getSupertokensUserId());
        assert (!result.wasAlreadyAPrimaryUser);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.main, "google", "user-google",
                "test@example.com");

        Multitenancy.addUserIdToTenant(process.main, new TenantIdentifier(null, null, "t1"),
                storage,
                signInUpResponse.user.getSupertokensUserId());

        try {
            AuthRecipe.createPrimaryUser(process.main, signInUpResponse.user.getSupertokensUserId());
            assert (false);
        } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            assert (e.primaryUserId.equals(emailPasswordUser.getSupertokensUserId()));
            assert (e.getMessage().equals("This user's email is already associated with another user ID"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void makePrimarySucceedsEvenIfAnotherAccountWithSameEmailButInADifferentTenant()
            throws Exception {
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
        AuthRecipeUserInfo emailPasswordUser = EmailPassword.signUp(new TenantIdentifier(null, null, "t1"),
                storage, process.getProcess(),
                "test@example.com",
                "pass1234");

        AuthRecipe.CreatePrimaryUserResult result = AuthRecipe.createPrimaryUser(process.main,
                emailPasswordUser.getSupertokensUserId());
        assert (!result.wasAlreadyAPrimaryUser);

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.main, "google", "user-google",
                "test@example.com");

        AuthRecipe.CreatePrimaryUserResult r = AuthRecipe.createPrimaryUser(process.main,
                signInUpResponse.user.getSupertokensUserId());
        assert !r.wasAlreadyAPrimaryUser;

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void makePrimaryUserFailsCauseOfUnknownUserId() throws Exception {
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

        try {
            AuthRecipe.createPrimaryUser(process.main, "random");
            assert (false);
        } catch (UnknownUserIdException ignored) {
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void makingPrimaryUserFailsCauseAlreadyLinkedToAnotherAccount() throws Exception {
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

        AuthRecipeUserInfo emailPasswordUser1 = EmailPassword.signUp(process.getProcess(), "test@example.com",
                "pass1234");
        AuthRecipeUserInfo emailPasswordUser2 = EmailPassword.signUp(process.getProcess(), "test2@example.com",
                "pass1234");

        AuthRecipe.createPrimaryUser(process.main, emailPasswordUser1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.main, emailPasswordUser2.getSupertokensUserId(),
                emailPasswordUser1.getSupertokensUserId());

        try {
            AuthRecipe.createPrimaryUser(process.main, emailPasswordUser2.getSupertokensUserId());
            assert (false);
        } catch (RecipeUserIdAlreadyLinkedWithPrimaryUserIdException e) {
            assert (e.primaryUserId.equals(emailPasswordUser1.getSupertokensUserId()));
            assert (e.getMessage().equals("This user ID is already linked to another user ID"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
