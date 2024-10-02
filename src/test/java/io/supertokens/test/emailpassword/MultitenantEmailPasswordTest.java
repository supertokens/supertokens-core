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

package io.supertokens.test.emailpassword;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.EmailChangeNotAllowedException;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.useridmapping.UserIdType;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MultitenantEmailPasswordTest {
    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    private void createTenants(TestingProcessManager.TestingProcess process)
            throws StorageQueryException, TenantOrAppNotFoundException, InvalidProviderConfigException,
            FeatureNotEnabledException, IOException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        // User pool 1 - (null, a1, null)
        // User pool 2 - (null, a1, t1), (null, a1, t2)

        { // tenant 1
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", null);

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 1);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
                            null, null,
                            config
                    )
            );
        }

        { // tenant 2
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t1");

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 2);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
                            null, null,
                            config
                    )
            );
        }

        { // tenant 3
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t2");

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 2);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
                            null, null,
                            config
                    )
            );
        }
    }

    @Test
    public void testSignUpAndLoginInDifferentTenants()
            throws InterruptedException, StorageQueryException, InvalidProviderConfigException,
            FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException,
            WrongCredentialsException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants(process);

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        Storage t1storage = (StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        Storage t2storage = (StorageLayer.getStorage(t2, process.getProcess()));
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");
        Storage t3storage = (StorageLayer.getStorage(t3, process.getProcess()));

        {
            EmailPassword.signUp(t1, t1storage, process.getProcess(), "user1@example.com", "password1");
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(t1, t1storage, process.getProcess(), "user1@example.com",
                    "password1");
            assertEquals("user1@example.com", userInfo.loginMethods[0].email);
        }

        {
            EmailPassword.signUp(t2, t2storage, process.getProcess(), "user2@example.com", "password2");
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(t2, t2storage, process.getProcess(), "user2@example.com",
                    "password2");
            assertEquals("user2@example.com", userInfo.loginMethods[0].email);
        }

        {
            EmailPassword.signUp(t3, t3storage, process.getProcess(), "user3@example.com", "password3");
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(t3, t3storage, process.getProcess(), "user3@example.com",
                    "password3");
            assertEquals("user3@example.com", userInfo.loginMethods[0].email);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSameEmailWithDifferentPasswordsOnDifferentTenantsWorksCorrectly()
            throws InterruptedException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException,
            WrongCredentialsException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants(process);

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        Storage t1storage = (StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        Storage t2storage = (StorageLayer.getStorage(t2, process.getProcess()));
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");
        Storage t3storage = (StorageLayer.getStorage(t3, process.getProcess()));


        EmailPassword.signUp(t1, t1storage, process.getProcess(), "user@example.com", "password1");
        EmailPassword.signUp(t2, t2storage, process.getProcess(), "user@example.com", "password2");
        EmailPassword.signUp(t3, t3storage, process.getProcess(), "user@example.com", "password3");

        {
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(t1, t1storage, process.getProcess(), "user@example.com",
                    "password1");
            assertEquals("user@example.com", userInfo.loginMethods[0].email);
        }

        {
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(t2, t2storage, process.getProcess(), "user@example.com",
                    "password2");
            assertEquals("user@example.com", userInfo.loginMethods[0].email);
        }

        {
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(t3, t3storage, process.getProcess(), "user@example.com",
                    "password3");
            assertEquals("user@example.com", userInfo.loginMethods[0].email);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetUserUsingIdReturnsCorrectUser()
            throws InterruptedException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException,
            UnknownUserIdException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants(process);

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        Storage t1storage = (StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        Storage t2storage = (StorageLayer.getStorage(t2, process.getProcess()));
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");
        Storage t3storage = (StorageLayer.getStorage(t3, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1, t1storage, process.getProcess(), "user1@example.com",
                "password1");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(t2, t2storage, process.getProcess(), "user2@example.com",
                "password2");
        AuthRecipeUserInfo user3 = EmailPassword.signUp(t3, t3storage, process.getProcess(), "user3@example.com",
                "password3");

        Storage[] storages = StorageLayer.getStoragesForApp(process.getProcess(), new AppIdentifier(null, "a1"));

        {
            AuthRecipeUserInfo userInfo = EmailPassword.getUserUsingId(
                    new AppIdentifier(null, "a1"),
                    StorageLayer.findStorageAndUserIdMappingForUser(
                            new AppIdentifier(null, "a1"), storages,
                            user1.getSupertokensUserId(),
                            UserIdType.SUPERTOKENS).storage, user1.getSupertokensUserId());
            assertEquals(user1, userInfo);
        }

        {
            AuthRecipeUserInfo userInfo = EmailPassword.getUserUsingId(
                    new AppIdentifier(null, "a1"),
                    StorageLayer.findStorageAndUserIdMappingForUser(
                            new AppIdentifier(null, "a1"), storages,
                            user2.getSupertokensUserId(),
                            UserIdType.SUPERTOKENS).storage, user2.getSupertokensUserId());
            assertEquals(user2, userInfo);
        }

        {
            AuthRecipeUserInfo userInfo = EmailPassword.getUserUsingId(
                    new AppIdentifier(null, "a1"),
                    StorageLayer.findStorageAndUserIdMappingForUser(
                            new AppIdentifier(null, "a1"), storages,
                            user3.getSupertokensUserId(),
                            UserIdType.SUPERTOKENS).storage, user3.getSupertokensUserId());
            assertEquals(user3, userInfo);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetUserUsingEmailReturnsTheUserFromTheSpecificTenant()
            throws InterruptedException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants(process);

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        Storage t1storage = (StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        Storage t2storage = (StorageLayer.getStorage(t2, process.getProcess()));
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");
        Storage t3storage = (StorageLayer.getStorage(t3, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1, t1storage, process.getProcess(), "user@example.com",
                "password1");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(t2, t2storage, process.getProcess(), "user@example.com",
                "password2");
        AuthRecipeUserInfo user3 = EmailPassword.signUp(t3, t3storage, process.getProcess(), "user@example.com",
                "password3");

        {
            AuthRecipeUserInfo userInfo = EmailPassword.getUserUsingEmail(t1, t1storage, user1.loginMethods[0].email);
            assertEquals(user1, userInfo);
        }

        {
            AuthRecipeUserInfo userInfo = EmailPassword.getUserUsingEmail(t2, t2storage, user2.loginMethods[0].email);
            assertEquals(user2, userInfo);
        }

        {
            AuthRecipeUserInfo userInfo = EmailPassword.getUserUsingEmail(t3, t3storage, user3.loginMethods[0].email);
            assertEquals(user3, userInfo);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatePasswordWorksCorrectlyAcrossAllTenants()
            throws InterruptedException, InvalidProviderConfigException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException,
            UnknownUserIdException, StorageTransactionLogicException, WrongCredentialsException,
            EmailChangeNotAllowedException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        createTenants(process);

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        Storage t1storage = (StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        Storage t2storage = (StorageLayer.getStorage(t2, process.getProcess()));
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");
        Storage t3storage = (StorageLayer.getStorage(t3, process.getProcess()));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(t1, t1storage, process.getProcess(), "user@example.com",
                "password1");
        AuthRecipeUserInfo user2 = EmailPassword.signUp(t2, t2storage, process.getProcess(), "user@example.com",
                "password2");
        AuthRecipeUserInfo user3 = EmailPassword.signUp(t3, t3storage, process.getProcess(), "user@example.com",
                "password3");

        Storage[] storages = StorageLayer.getStoragesForApp(process.getProcess(), new AppIdentifier(null, "a1"));

        EmailPassword.updateUsersEmailOrPassword(
                new AppIdentifier(null, "a1"),
                StorageLayer.findStorageAndUserIdMappingForUser(
                        new AppIdentifier(null, "a1"), storages,
                        user1.getSupertokensUserId(),
                        UserIdType.SUPERTOKENS).storage,
                process.getProcess(), user1.getSupertokensUserId(), null, "newpassword1");
        EmailPassword.updateUsersEmailOrPassword(
                new AppIdentifier(null, "a1"),
                StorageLayer.findStorageAndUserIdMappingForUser(
                        new AppIdentifier(null, "a1"), storages,
                        user2.getSupertokensUserId(),
                        UserIdType.SUPERTOKENS).storage,
                process.getProcess(), user2.getSupertokensUserId(), null, "newpassword2");
        EmailPassword.updateUsersEmailOrPassword(
                new AppIdentifier(null, "a1"),
                StorageLayer.findStorageAndUserIdMappingForUser(
                        new AppIdentifier(null, "a1"), storages,
                        user3.getSupertokensUserId(),
                        UserIdType.SUPERTOKENS).storage,
                process.getProcess(), user3.getSupertokensUserId(), null, "newpassword3");

        {
            t1storage = StorageLayer.findStorageAndUserIdMappingForUser(process.getProcess(), t1,
                    user1.getSupertokensUserId(),
                    UserIdType.SUPERTOKENS).storage;
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(t1, t1storage, process.getProcess(), "user@example.com",
                    "newpassword1");
            assertEquals(user1.getSupertokensUserId(), userInfo.getSupertokensUserId());
        }

        {
            t2storage = StorageLayer.findStorageAndUserIdMappingForUser(process.getProcess(), t2,
                    user2.getSupertokensUserId(),
                    UserIdType.SUPERTOKENS).storage;
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(t2, t2storage, process.getProcess(), "user@example.com",
                    "newpassword2");
            assertEquals(user2.getSupertokensUserId(), userInfo.getSupertokensUserId());
        }

        {
            t3storage = StorageLayer.findStorageAndUserIdMappingForUser(process.getProcess(), t3,
                    user3.getSupertokensUserId(),
                    UserIdType.SUPERTOKENS).storage;
            AuthRecipeUserInfo userInfo = EmailPassword.signIn(t3, t3storage, process.getProcess(), "user@example.com",
                    "newpassword3");
            assertEquals(user3.getSupertokensUserId(), userInfo.getSupertokensUserId());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
