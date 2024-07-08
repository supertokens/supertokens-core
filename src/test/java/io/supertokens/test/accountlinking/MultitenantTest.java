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
import io.supertokens.authRecipe.exception.RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.EmailChangeNotAllowedException;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.*;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.exceptions.PhoneNumberChangeNotAllowedException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicatePhoneNumberException;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateThirdPartyUserException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.userroles.UserRoles;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

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

    TenantIdentifier t1, t2, t3, t4;

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
                            null, null, config
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
                            null, null, config
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
                            null, null, config
                    )
            );
        }

        { // tenant 4
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t3");

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
                            null, null, config
                    )
            );
        }
    }

    @Test
    public void testUserAreNotAutomaticallySharedBetweenTenantsOfLinkedAccountsForPless() throws Exception {
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

        createTenants(process.getProcess());

        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a1", "t1");

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
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants(process.getProcess());

        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a1", "t1");

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
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants(process.getProcess());

        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a1", "t1");

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
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants(process.getProcess());

        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a1", "t1");

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

    @Test
    public void testVariousCases() throws Exception {
        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a1", "t1");
        t3 = new TenantIdentifier(null, "a1", "t2");
        t4 = new TenantIdentifier(null, "a1", "t3");

        TestCase[] testCases = new TestCase[]{
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new CreatePlessUserWithEmail(t2, "test@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new AssociateUserToTenant(t2, 0),
                }),
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new CreatePlessUserWithEmail(t2, "test@example.com"),
                        new AssociateUserToTenant(t2, 0),
                        new MakePrimaryUser(t1, 0),
                }),
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new CreatePlessUserWithEmail(t2, "test@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new SignInEmailPasswordUser(t2, 0).expect(new WrongCredentialsException())
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t1, 2).expect(new DuplicateEmailException()),
                        new AssociateUserToTenant(t2, 2), // Allowed
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new MakePrimaryUser(t3, 2),
                        new AssociateUserToTenant(t2, 2).expect(
                                new AnotherPrimaryUserWithEmailAlreadyExistsException("")),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t2, 2),
                        new MakePrimaryUser(t3, 2).expect(
                                new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException("", "")),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t1, 2).expect(new DuplicateEmailException()),
                        new AssociateUserToTenant(t2, 2), // Allowed
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new MakePrimaryUser(t3, 2),
                        new AssociateUserToTenant(t2, 2).expect(
                                new AnotherPrimaryUserWithEmailAlreadyExistsException("")),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t2, 2),
                        new MakePrimaryUser(t3, 2).expect(
                                new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException("", "")),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t1, 2),
                        new AssociateUserToTenant(t2, 2), // Allowed
                }),

                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new MakePrimaryUser(t3, 2),
                        new AssociateUserToTenant(t2, 2).expect(
                                new AnotherPrimaryUserWithEmailAlreadyExistsException("")),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t2, 2),
                        new MakePrimaryUser(t3, 2).expect(
                                new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException("", "")),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t2, "test2@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new UpdatePlessUserEmail(t1, 0, "test2@example.com"),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t1, "test3@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new UpdatePlessUserEmail(t1, 0, "test3@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t1, "test3@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new UpdatePlessUserEmail(t1, 1, "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithPhone(t1, "+1000001"),
                        new CreatePlessUserWithPhone(t1, "+1000003"),
                        new CreatePlessUserWithPhone(t2, "+1000002"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new UpdatePlessUserPhone(t1, 0, "+1000003").expect(new PhoneNumberChangeNotAllowedException()),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithPhone(t1, "+1000001"),
                        new CreatePlessUserWithPhone(t1, "+1000003"),
                        new CreatePlessUserWithPhone(t2, "+1000002"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new UpdatePlessUserPhone(t1, 1, "+1000001").expect(new PhoneNumberChangeNotAllowedException()),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t1, "test3@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new UpdateEmailPasswordUserEmail(t1, 0, "test3@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t1, "test3@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new UpdateEmailPasswordUserEmail(t1, 1, "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateThirdPartyUser(t2, "google", "googleid", "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t1, 2).expect(new DuplicateEmailException()),
                        new AssociateUserToTenant(t2, 2), // Allowed
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateThirdPartyUser(t2, "google", "googleid", "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new MakePrimaryUser(t3, 2),
                        new AssociateUserToTenant(t2, 2).expect(
                                new AnotherPrimaryUserWithEmailAlreadyExistsException("")),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateThirdPartyUser(t2, "google", "googleid", "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t2, 2),
                        new MakePrimaryUser(t3, 2).expect(
                                new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException("", "")),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid", "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t1, 2),
                        new AssociateUserToTenant(t2, 2), // Allowed
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid", "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new MakePrimaryUser(t3, 2),
                        new AssociateUserToTenant(t2, 2).expect(
                                new AnotherPrimaryUserWithEmailAlreadyExistsException("")),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid", "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateEmailPasswordUser(t3, "test1@example.com"),
                        new AssociateUserToTenant(t2, 2),
                        new MakePrimaryUser(t3, 2).expect(
                                new AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException("", "")),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test1@example.com"),
                        new CreateThirdPartyUser(t2, "google", "googleid2", "test2@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test2@example.com"),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test1@example.com"),
                        new CreateThirdPartyUser(t1, "google", "googleid3", "test3@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test3@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test1@example.com"),
                        new CreateThirdPartyUser(t1, "google", "googleid3", "test3@example.com"),
                        new CreateEmailPasswordUser(t2, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 2),
                        new MakePrimaryUser(t2, 1),
                        new CreateThirdPartyUser(t1, "google", "googleid3", "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithPhone(t1, "+1000001"),
                        new CreatePlessUserWithPhone(t2, "+1000002"),
                        new CreatePlessUserWithPhone(t3, "+1000001"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new MakePrimaryUser(t3, 2),
                        new AssociateUserToTenant(t1, 2).expect(new DuplicatePhoneNumberException()),
                        new AssociateUserToTenant(t2, 2).expect(
                                new AnotherPrimaryUserWithPhoneNumberAlreadyExistsException("")),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test1@example.com"),
                        new CreateThirdPartyUser(t2, "google", "googleid2", "test2@example.com"),
                        new CreateThirdPartyUser(t3, "google", "googleid1", "test3@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new MakePrimaryUser(t3, 2),
                        new AssociateUserToTenant(t1, 2).expect(new DuplicateThirdPartyUserException()),
                        new AssociateUserToTenant(t2, 2).expect(
                                new AnotherPrimaryUserWithThirdPartyInfoAlreadyExistsException("")),
                }),
                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test1@example.com"),
                        new CreateThirdPartyUser(t2, "google", "googleid2", "test2@example.com"),
                        new CreateThirdPartyUser(t1, "google", "googleid3", "test3@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new MakePrimaryUser(t1, 2),
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test3@example.com").expect(
                                new EmailChangeNotAllowedException()),
                        new CreateThirdPartyUser(t1, "google", "googleid3", "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new CreateEmailPasswordUser(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new UnlinkAccount(t1, 0),
                        new AssociateUserToTenant(t2, 0).expect(new UnknownUserIdException()),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test@example.com"),
                        new CreatePlessUserWithEmail(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new UnlinkAccount(t1, 0),
                        new AssociateUserToTenant(t2, 0).expect(new UnknownUserIdException()),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateThirdPartyUser(t1, "google", "googleid1", "test@example.com"),
                        new CreateThirdPartyUser(t1, "google", "googleid2", "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new UnlinkAccount(t1, 0),
                        new AssociateUserToTenant(t2, 0).expect(new UnknownUserIdException()),
                }),
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new CreateEmailPasswordUser(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new DisassociateUserFromTenant(t1, 0),
                        new AssociateUserToTenant(t2, 0),
                        new TestCaseStep() {
                            @Override
                            public void execute(Main main) throws Exception {
                                Storage t1Storage = (StorageLayer.getStorage(t1, main));
                                AuthRecipeUserInfo user = AuthRecipe.getUserById(t1.toAppIdentifier(), t1Storage,
                                        TestCase.users.get(0).getSupertokensUserId());
                                assertEquals(2, user.loginMethods.length);
                                assertTrue(user.loginMethods[0].tenantIds.contains(t2.getTenantId()));
                                assertTrue(user.loginMethods[1].tenantIds.contains(t1.getTenantId()));
                            }
                        }
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new DisassociateUserFromTenant(t1, 0),
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new DisassociateUserFromTenant(t1, 1),
                        new MakePrimaryUser(t1, 0),
                        new MakePrimaryUser(t1, 1),
                        new AssociateUserToTenant(t1, 0),
                        new AssociateUserToTenant(t1, 1).expect(new DuplicateEmailException()),
                        new LinkAccounts(t1, 0, 1).expect(
                                new RecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException(null, "")),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new DisassociateUserFromTenant(t1, 0),
                        new CreateEmailPasswordUser(t1, "test@example.com"),
                        new DisassociateUserFromTenant(t1, 1),
                        new MakePrimaryUser(t1, 0),
                        new AssociateUserToTenant(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new AssociateUserToTenant(t1, 1).expect(new DuplicateEmailException()),
                        new AssociateUserToTenant(t2, 1),
                }),
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new UnlinkAccount(t1, 0),
                        new TestCaseStep() {
                            @Override
                            public void execute(Main main) throws Exception {
                                Storage t1Storage = (StorageLayer.getStorage(t1, main));
                                AuthRecipe.deleteUser(t1.toAppIdentifier(), t1Storage,
                                        TestCase.users.get(1).getSupertokensUserId());
                            }
                        },
                        new TestCaseStep() {
                            @Override
                            public void execute(Main main) throws Exception {
                                Storage t1Storage = (StorageLayer.getStorage(t1, main));
                                AuthRecipeUserInfo user = AuthRecipe.getUserById(t1.toAppIdentifier(), t1Storage,
                                        TestCase.users.get(0).getSupertokensUserId());
                                assertNull(user);
                            }
                        }
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new UpdatePlessUserEmail(t1, 1, "test1@example.com"),
                }),
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new UpdateEmailPasswordUserEmail(t1, 1, "test1@example.com"),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 1),
                        new UpdatePlessUserEmail(t1, 1, "test1@example.com"),
                }),
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 1),
                        new UpdateEmailPasswordUserEmail(t1, 1, "test1@example.com"),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreatePlessUserWithEmail(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new MakePrimaryUser(t1, 1),
                        new UpdatePlessUserEmail(t1, 1, "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),
                new TestCase(new TestCaseStep[]{
                        new CreatePlessUserWithEmail(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t1, "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new MakePrimaryUser(t1, 1),
                        new UpdateEmailPasswordUserEmail(t1, 1, "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),

                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateThirdPartyUser(t1, "google", "googleid", "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new CreateThirdPartyUser(t1, "google", "googleid", "test2@example.com"), // allowed
                }),
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateThirdPartyUser(t1, "google", "googleid", "test2@example.com"),
                        new MakePrimaryUser(t1, 1),
                        new CreateThirdPartyUser(t1, "google", "googleid", "test2@example.com"), // allowed
                }),
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateThirdPartyUser(t1, "google", "googleid", "test2@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new MakePrimaryUser(t1, 1),
                        new CreateThirdPartyUser(t1, "google", "googleid", "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),
                new TestCase(new TestCaseStep[]{
                        new CreateEmailPasswordUser(t1, "test1@example.com"),
                        new CreateEmailPasswordUser(t2, "test3@example.com"),
                        new MakePrimaryUser(t1, 0),
                        new LinkAccounts(t1, 0, 1),
                        new CreateThirdPartyUser(t2, "google", "googleid", "test2@example.com"),
                        new MakePrimaryUser(t2, 2),
                        new CreateThirdPartyUser(t2, "google", "googleid", "test1@example.com").expect(
                                new EmailChangeNotAllowedException()),
                }),
        };

        int i = 0;
        for (TestCase testCase : testCases) {
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

            createTenants(process.getProcess());

            System.out.println("Executing test case : " + i);
            testCase.doTest(process.getProcess());

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
            i++;
        }
    }

    private static class TestCase {
        TestCaseStep[] steps;
        public static List<AuthRecipeUserInfo> users;

        public static void resetUsers() {
            users = new ArrayList<>();
        }

        public static void addUser(AuthRecipeUserInfo user) {
            users.add(user);
        }

        public TestCase(TestCaseStep[] steps) {
            this.steps = steps;
        }

        public void doTest(Main main) throws Exception {
            TestCase.resetUsers();

            for (TestCaseStep step : steps) {
                step.doStep(main);
            }
        }
    }

    private static abstract class TestCaseStep {
        Exception e;

        public TestCaseStep expect(Exception e) {
            this.e = e;
            return this;
        }

        public void doStep(Main main) throws Exception {
            if (e == null) {
                this.execute(main);
            } else {
                try {
                    this.execute(main);
                    fail();
                } catch (Exception e) {
                    assertEquals(this.e.getClass(), e.getClass());
                }
            }
        }

        abstract public void execute(Main main) throws Exception;
    }

    private static class CreateEmailPasswordUser extends TestCaseStep {
        private final TenantIdentifier tenantIdentifier;
        private final String email;

        public CreateEmailPasswordUser(TenantIdentifier tenantIdentifier, String email) {
            this.tenantIdentifier = tenantIdentifier;
            this.email = email;
        }

        @Override
        public void execute(Main main) throws Exception {
            Storage storage = (StorageLayer.getStorage(tenantIdentifier, main));
            AuthRecipeUserInfo user = EmailPassword.signUp(tenantIdentifier, storage, main, email,
                    "password");
            TestCase.addUser(user);
        }
    }

    private static class CreatePlessUserWithEmail extends TestCaseStep {
        private final TenantIdentifier tenantIdentifier;
        private final String email;

        public CreatePlessUserWithEmail(TenantIdentifier tenantIdentifier, String email) {
            this.tenantIdentifier = tenantIdentifier;
            this.email = email;
        }

        @Override
        public void execute(Main main) throws Exception {
            Storage storage = (StorageLayer.getStorage(tenantIdentifier, main));
            Passwordless.CreateCodeResponse code = Passwordless.createCode(tenantIdentifier,
                    storage, main,
                    email, null, null, null);
            AuthRecipeUserInfo user = Passwordless.consumeCode(tenantIdentifier, storage, main,
                    code.deviceId,
                    code.deviceIdHash, code.userInputCode, null).user;
            TestCase.addUser(user);
        }
    }

    private static class CreatePlessUserWithPhone extends TestCaseStep {
        private final TenantIdentifier tenantIdentifier;
        private final String phoneNumber;

        public CreatePlessUserWithPhone(TenantIdentifier tenantIdentifier, String phoneNumber) {
            this.tenantIdentifier = tenantIdentifier;
            this.phoneNumber = phoneNumber;
        }

        @Override
        public void execute(Main main) throws Exception {
            Storage storage = (StorageLayer.getStorage(tenantIdentifier, main));
            Passwordless.CreateCodeResponse code = Passwordless.createCode(tenantIdentifier,
                    storage, main,
                    null, phoneNumber, null, null);
            AuthRecipeUserInfo user = Passwordless.consumeCode(tenantIdentifier, storage, main,
                    code.deviceId,
                    code.deviceIdHash, code.userInputCode, null).user;
            TestCase.addUser(user);
        }
    }

    private static class CreateThirdPartyUser extends TestCaseStep {
        TenantIdentifier tenantIdentifier;
        String thirdPartyId;
        String thirdPartyUserId;
        String email;

        public CreateThirdPartyUser(TenantIdentifier tenantIdentifier, String thirdPartyId, String thirdPartyUserId,
                                    String email) {
            this.tenantIdentifier = tenantIdentifier;
            this.thirdPartyId = thirdPartyId;
            this.thirdPartyUserId = thirdPartyUserId;
            this.email = email;
        }

        @Override
        public void execute(Main main) throws Exception {
            Storage storage = (StorageLayer.getStorage(tenantIdentifier, main));
            AuthRecipeUserInfo user = ThirdParty.signInUp(tenantIdentifier, storage, main,
                    thirdPartyId,
                    thirdPartyUserId, email).user;
            TestCase.addUser(user);
        }
    }

    private static class MakePrimaryUser extends TestCaseStep {
        TenantIdentifier tenantIdentifier;
        int userIndex;

        public MakePrimaryUser(TenantIdentifier tenantIdentifier, int userIndex) {
            this.tenantIdentifier = tenantIdentifier;
            this.userIndex = userIndex;
        }

        @Override
        public void execute(Main main) throws Exception {
            Storage storage = (StorageLayer.getStorage(tenantIdentifier, main));
            AuthRecipe.createPrimaryUser(main, tenantIdentifier.toAppIdentifier(), storage,
                    TestCase.users.get(userIndex).getSupertokensUserId());
        }
    }

    private static class LinkAccounts extends TestCaseStep {
        TenantIdentifier tenantIdentifier;
        int primaryUserIndex;
        int recipeUserIndex;

        public LinkAccounts(TenantIdentifier tenantIdentifier, int primaryUserIndex, int recipeUserIndex) {
            this.tenantIdentifier = tenantIdentifier;
            this.primaryUserIndex = primaryUserIndex;
            this.recipeUserIndex = recipeUserIndex;
        }

        @Override
        public void execute(Main main) throws Exception {
            Storage storage = (StorageLayer.getStorage(tenantIdentifier, main));
            AuthRecipe.linkAccounts(main, tenantIdentifier.toAppIdentifier(), storage,
                    TestCase.users.get(recipeUserIndex).getSupertokensUserId(),
                    TestCase.users.get(primaryUserIndex).getSupertokensUserId());
        }
    }

    private static class AssociateUserToTenant extends TestCaseStep {
        TenantIdentifier tenantIdentifier;
        int userIndex;

        public AssociateUserToTenant(TenantIdentifier tenantIdentifier, int userIndex) {
            this.tenantIdentifier = tenantIdentifier;
            this.userIndex = userIndex;
        }

        @Override
        public void execute(Main main) throws Exception {
            Storage storage = (StorageLayer.getStorage(tenantIdentifier, main));
            Multitenancy.addUserIdToTenant(main, tenantIdentifier, storage,
                    TestCase.users.get(userIndex).getSupertokensUserId());
        }
    }

    private static class UpdateEmailPasswordUserEmail extends TestCaseStep {
        TenantIdentifier tenantIdentifier;
        int userIndex;
        String email;

        public UpdateEmailPasswordUserEmail(TenantIdentifier tenantIdentifier, int userIndex, String email) {
            this.tenantIdentifier = tenantIdentifier;
            this.userIndex = userIndex;
            this.email = email;
        }

        @Override
        public void execute(Main main) throws Exception {
            Storage storage = (StorageLayer.getStorage(tenantIdentifier, main));
            EmailPassword.updateUsersEmailOrPassword(tenantIdentifier.toAppIdentifier(), storage,
                    main, TestCase.users.get(userIndex).getSupertokensUserId(), email, null);
        }
    }

    private static class UpdatePlessUserEmail extends TestCaseStep {
        TenantIdentifier tenantIdentifier;
        int userIndex;
        String email;

        public UpdatePlessUserEmail(TenantIdentifier tenantIdentifier, int userIndex, String email) {
            this.tenantIdentifier = tenantIdentifier;
            this.userIndex = userIndex;
            this.email = email;
        }

        @Override
        public void execute(Main main) throws Exception {
            Storage storage = (StorageLayer.getStorage(tenantIdentifier, main));
            Passwordless.updateUser(tenantIdentifier.toAppIdentifier(), storage,
                    TestCase.users.get(userIndex).getSupertokensUserId(), new Passwordless.FieldUpdate(email), null);
        }
    }

    private static class UpdatePlessUserPhone extends TestCaseStep {
        TenantIdentifier tenantIdentifier;
        int userIndex;
        String phoneNumber;

        public UpdatePlessUserPhone(TenantIdentifier tenantIdentifier, int userIndex, String phoneNumber) {
            this.tenantIdentifier = tenantIdentifier;
            this.userIndex = userIndex;
            this.phoneNumber = phoneNumber;
        }

        @Override
        public void execute(Main main) throws Exception {
            Storage storage = (StorageLayer.getStorage(tenantIdentifier, main));
            Passwordless.updateUser(tenantIdentifier.toAppIdentifier(), storage,
                    TestCase.users.get(userIndex).getSupertokensUserId(), null,
                    new Passwordless.FieldUpdate(phoneNumber));
        }
    }

    private static class UnlinkAccount extends TestCaseStep {
        TenantIdentifier tenantIdentifier;
        int userIndex;

        public UnlinkAccount(TenantIdentifier tenantIdentifier, int userIndex) {
            this.tenantIdentifier = tenantIdentifier;
            this.userIndex = userIndex;
        }

        @Override
        public void execute(Main main) throws Exception {
            Storage storage = (StorageLayer.getStorage(tenantIdentifier, main));
            AuthRecipe.unlinkAccounts(main, tenantIdentifier.toAppIdentifier(), storage,
                    TestCase.users.get(userIndex).getSupertokensUserId());
        }
    }

    private static class SignInEmailPasswordUser extends TestCaseStep {
        TenantIdentifier tenantIdentifier;
        int userIndex;

        public SignInEmailPasswordUser(TenantIdentifier tenantIdentifier, int userIndex) {
            this.tenantIdentifier = tenantIdentifier;
            this.userIndex = userIndex;
        }

        @Override
        public void execute(Main main) throws Exception {
            Storage storage = (StorageLayer.getStorage(tenantIdentifier, main));
            EmailPassword.signIn(tenantIdentifier, storage, main,
                    TestCase.users.get(userIndex).loginMethods[0].email, "password");
        }
    }

    private static class DisassociateUserFromTenant extends TestCaseStep {
        TenantIdentifier tenantIdentifier;
        int userIndex;

        public DisassociateUserFromTenant(TenantIdentifier tenantIdentifier, int userIndex) {
            this.tenantIdentifier = tenantIdentifier;
            this.userIndex = userIndex;
        }

        @Override
        public void execute(Main main) throws Exception {
            Storage storage = (StorageLayer.getStorage(tenantIdentifier, main));
            Multitenancy.removeUserIdFromTenant(main, tenantIdentifier, storage,
                    TestCase.users.get(userIndex).getSupertokensUserId(), null);
        }
    }
}
