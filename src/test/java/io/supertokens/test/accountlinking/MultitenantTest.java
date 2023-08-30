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
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.exception.AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.function.Function;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class MultitenantTest {
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

    TenantIdentifier t1, t2, t3;

    private void createTenants(Main main)
            throws StorageQueryException, TenantOrAppNotFoundException, InvalidProviderConfigException,
            FeatureNotEnabledException, IOException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        // User pool 1 - (null, a1, null)
        // User pool 2 - (null, a1, t1), (null, a1, t2)

        { // tenant 1
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", null);

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), main)
                    .modifyConfigToAddANewUserPoolForTesting(config, 1);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    main,
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
        }

        { // tenant 2
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t1");

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), main)
                    .modifyConfigToAddANewUserPoolForTesting(config, 1);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    main,
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
        }

        { // tenant 3
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t2");

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), main)
                    .modifyConfigToAddANewUserPoolForTesting(config, 1);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    main,
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(true, null),
                            new PasswordlessConfig(true),
                            config
                    )
            );
        }

        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a1", "t1");
        t3 = new TenantIdentifier(null, "a1", "t2");
    }

    @Test
    public void testWithEmailPasswordUsers1() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        createTenants(process.getProcess());

        TenantIdentifierWithStorage t1WithStorage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifierWithStorage t2WithStorage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1WithStorage, process.getProcess(), "test1@example.com", "password");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(t2WithStorage, process.getProcess(), "test2@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        try {
            // Credentials does not exist in t2
            EmailPassword.signIn(t2WithStorage, process.getProcess(), "test1@example.com", "password");
            fail();
        } catch (WrongCredentialsException e) {
            // ignore
        }

        Multitenancy.addUserIdToTenant(process.getProcess(), t2WithStorage, user1.getSupertokensUserId());

        // Sign in should now pass
        EmailPassword.signIn(t2WithStorage, process.getProcess(), "test1@example.com", "password");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testWithEmailPasswordUsers2() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        createTenants(process.getProcess());

        TenantIdentifierWithStorage t1WithStorage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifierWithStorage t2WithStorage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1WithStorage, process.getProcess(), "test1@example.com", "password");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(t2WithStorage, process.getProcess(), "test2@example.com", "password");
        AuthRecipe.createPrimaryUser(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        try {
            // Credentials does not exist in t2
            EmailPassword.signIn(t2WithStorage, process.getProcess(), "test1@example.com", "password");
            fail();
        } catch (WrongCredentialsException e) {
            // ignore
        }

        // same email is allowed to sign up
        AuthRecipeUserInfo user3 = EmailPassword.signUp(t1WithStorage, process.getProcess(), "test2@example.com", "password2");

        // Sign in should pass
        EmailPassword.signIn(t1WithStorage, process.getProcess(), "test2@example.com", "password2");

        try {
            // user 3 cannot become a primary user
            AuthRecipe.createPrimaryUser(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user3.getSupertokensUserId());
            fail();
        } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            // Ignore
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testWithEmailPasswordUsersAndPasswordlessUser1() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        createTenants(process.getProcess());

        TenantIdentifierWithStorage t1WithStorage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifierWithStorage t2WithStorage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1WithStorage, process.getProcess(), "test1@example.com", "password");
        Passwordless.CreateCodeResponse user2Code = Passwordless.createCode(t2WithStorage, process.getProcess(),
                "test2@example.com", null, null, null);
        AuthRecipeUserInfo user2 = Passwordless.consumeCode(t2WithStorage, process.getProcess(), user2Code.deviceId, user2Code.deviceIdHash, user2Code.userInputCode, null).user;
        AuthRecipe.createPrimaryUser(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        try {
            // Credentials does not exist in t2
            EmailPassword.signIn(t2WithStorage, process.getProcess(), "test1@example.com", "password");
            fail();
        } catch (WrongCredentialsException e) {
            // ignore
        }

        // same email is allowed to sign up
        AuthRecipeUserInfo user3 = EmailPassword.signUp(t1WithStorage, process.getProcess(), "test2@example.com", "password2");

        // Sign in should pass
        EmailPassword.signIn(t1WithStorage, process.getProcess(), "test2@example.com", "password2");

        try {
            // user 3 cannot become a primary user
            AuthRecipe.createPrimaryUser(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user3.getSupertokensUserId());
            fail();
        } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            // Ignore
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testWithEmailPasswordUsersAndPasswordlessUser2() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        createTenants(process.getProcess());

        TenantIdentifierWithStorage t1WithStorage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifierWithStorage t2WithStorage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1WithStorage, process.getProcess(), "test1@example.com", "password");
        Passwordless.CreateCodeResponse user2Code = Passwordless.createCode(t2WithStorage, process.getProcess(),
                "test2@example.com", null, null, null);
        AuthRecipeUserInfo user2 = Passwordless.consumeCode(t2WithStorage, process.getProcess(), user2Code.deviceId, user2Code.deviceIdHash, user2Code.userInputCode, null).user;
        AuthRecipe.createPrimaryUser(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        // same email is allowed to sign in up
        Passwordless.CreateCodeResponse user3code = Passwordless.createCode(t1WithStorage, process.getProcess(),
                "test2@example.com", null, null, null);

        AuthRecipeUserInfo user3 = Passwordless.consumeCode(t1WithStorage, process.getProcess(), user3code.deviceId, user3code.deviceIdHash, user3code.userInputCode, null).user;

        try {
            // user 3 cannot become a primary user
            AuthRecipe.createPrimaryUser(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user3.getSupertokensUserId());
            fail();
        } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            // Ignore
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testWithEmailPasswordUserAndThirdPartyUser() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        createTenants(process.getProcess());

        TenantIdentifierWithStorage t1WithStorage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifierWithStorage t2WithStorage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1WithStorage, process.getProcess(), "test1@example.com", "password");
        AuthRecipeUserInfo user2 = ThirdParty.signInUp(t2WithStorage, process.getProcess(), "google", "google-user", "test2@example.com").user;
        AuthRecipe.createPrimaryUser(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        try {
            // Credentials does not exist in t2
            EmailPassword.signIn(t2WithStorage, process.getProcess(), "test1@example.com", "password");
            fail();
        } catch (WrongCredentialsException e) {
            // ignore
        }

        // same email is allowed to sign up
        AuthRecipeUserInfo user3 = ThirdParty.signInUp(t1WithStorage, process.getProcess(), "google", "google-user", "test2@example.com").user;

        try {
            // user 3 cannot become a primary user
            AuthRecipe.createPrimaryUser(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user3.getSupertokensUserId());
            fail();
        } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            // Ignore
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testTenantAssociationWithEPUsers1() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        createTenants(process.getProcess());

        TenantIdentifierWithStorage t1WithStorage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifierWithStorage t2WithStorage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));
        TenantIdentifierWithStorage t3WithStorage = t3.withStorage(StorageLayer.getStorage(t3, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1WithStorage, process.getProcess(), "test1@example.com", "password1");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(t2WithStorage, process.getProcess(), "test2@example.com", "password2");
        AuthRecipeUserInfo user3 = EmailPassword.signUp(t3WithStorage, process.getProcess(), "test1@example.com", "password3");

        AuthRecipe.createPrimaryUser(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        Multitenancy.addUserIdToTenant(process.getProcess(), t2WithStorage, user3.getSupertokensUserId());
        try {
            AuthRecipe.createPrimaryUser(process.getProcess(), t2WithStorage.toAppIdentifierWithStorage(), user3.getSupertokensUserId());
            fail();
        } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            // ignore
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testTenantAssociationWithEPUsers2() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        createTenants(process.getProcess());

        TenantIdentifierWithStorage t1WithStorage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifierWithStorage t2WithStorage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));
        TenantIdentifierWithStorage t3WithStorage = t3.withStorage(StorageLayer.getStorage(t3, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1WithStorage, process.getProcess(), "test1@example.com", "password1");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(t2WithStorage, process.getProcess(), "test2@example.com", "password2");
        AuthRecipeUserInfo user3 = EmailPassword.signUp(t3WithStorage, process.getProcess(), "test1@example.com", "password3");

        AuthRecipe.createPrimaryUser(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user1.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), t1WithStorage.toAppIdentifierWithStorage(), user2.getSupertokensUserId(), user1.getSupertokensUserId());

        AuthRecipe.createPrimaryUser(process.getProcess(), t2WithStorage.toAppIdentifierWithStorage(), user3.getSupertokensUserId());
        try {
            Multitenancy.addUserIdToTenant(process.getProcess(), t2WithStorage, user3.getSupertokensUserId());
            fail();
        } catch (DuplicateEmailException e) {
            // ignore
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
