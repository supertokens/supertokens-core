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
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.*;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
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
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public abstract class MultitenantTestBase {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    protected TenantIdentifier t1, t2, t3, t4;

    protected void initTenantIdentifiers() {
        t1 = new TenantIdentifier(null, "a1", null);
        t2 = new TenantIdentifier(null, "a1", "t1");
        t3 = new TenantIdentifier(null, "a1", "t2");
        t4 = new TenantIdentifier(null, "a1", "t3");
    }

    protected void createTenants(Main main)
            throws StorageQueryException, TenantOrAppNotFoundException, InvalidProviderConfigException,
            FeatureNotEnabledException, IOException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        long t_allStart = System.currentTimeMillis();
        { // tenant 1
            long t0 = System.currentTimeMillis();
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
            System.out.println("[TIMING] createTenants: tenant1 (a1/public) took " + (System.currentTimeMillis() - t0) + "ms");
        }

        { // tenant 2
            long t0 = System.currentTimeMillis();
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t1");

            long t_mod0 = System.currentTimeMillis();
            StorageLayer.getStorage(new TenantIdentifier(null, null, null), main)
                    .modifyConfigToAddANewUserPoolForTesting(config, 1);
            long t_mod = System.currentTimeMillis() - t_mod0;

            long t_add0 = System.currentTimeMillis();
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
            long t_add = System.currentTimeMillis() - t_add0;
            System.out.println("[TIMING] createTenants: tenant2 (a1/t1) took " + (System.currentTimeMillis() - t0) + "ms (modifyConfig=" + t_mod + "ms, addNewOrUpdate=" + t_add + "ms)");
        }

        { // tenant 3
            long t0 = System.currentTimeMillis();
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
            System.out.println("[TIMING] createTenants: tenant3 (a1/t2) took " + (System.currentTimeMillis() - t0) + "ms");
        }

        { // tenant 4
            long t0 = System.currentTimeMillis();
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
            System.out.println("[TIMING] createTenants: tenant4 (a1/t3) took " + (System.currentTimeMillis() - t0) + "ms");
        }
        System.out.println("[TIMING] createTenants: ALL 4 tenants took " + (System.currentTimeMillis() - t_allStart) + "ms");
    }

    protected void runTestCases(TestCase... testCases) throws Exception {
        int i = 0;
        for (TestCase testCase : testCases) {
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
            System.out.println("Executing test case : " + i);
            testCase.doTest(process.getProcess());
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
            i++;
        }
    }

    static class TestCase {
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
                Thread.sleep(20);
            }
        }
    }

    static abstract class TestCaseStep {
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

    static class CreateEmailPasswordUser extends TestCaseStep {
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

    static class CreatePlessUserWithEmail extends TestCaseStep {
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

    static class CreatePlessUserWithPhone extends TestCaseStep {
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

    static class CreateThirdPartyUser extends TestCaseStep {
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

    static class MakePrimaryUser extends TestCaseStep {
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

    static class LinkAccounts extends TestCaseStep {
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

    static class AssociateUserToTenant extends TestCaseStep {
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

    static class UpdateEmailPasswordUserEmail extends TestCaseStep {
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

    static class UpdatePlessUserEmail extends TestCaseStep {
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

    static class UpdatePlessUserPhone extends TestCaseStep {
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

    static class UnlinkAccount extends TestCaseStep {
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

    static class SignInEmailPasswordUser extends TestCaseStep {
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

    static class DisassociateUserFromTenant extends TestCaseStep {
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
