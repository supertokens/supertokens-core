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
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.multitenancy.exception.DeletionInProgressException;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateClientTypeException;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateTenantException;
import io.supertokens.pluginInterface.multitenancy.exceptions.DuplicateThirdPartyIdException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

// TODO DO NOT REVIEW YET, TESTS ARE NOT WORKING
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
            throws DuplicateThirdPartyIdException, DuplicateClientTypeException, DuplicateTenantException,
            StorageQueryException, TenantOrAppNotFoundException, InvalidProviderConfigException,
            DeletionInProgressException, FeatureNotEnabledException, IOException, InvalidConfigException,
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
                            config
                    )
            );
        }
    }

    @Test
    public void testSignUpAndLoginInDifferentTenants()
            throws InterruptedException, DuplicateThirdPartyIdException, DuplicateClientTypeException,
            DuplicateTenantException, StorageQueryException, InvalidProviderConfigException,
            DeletionInProgressException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException,
            WrongCredentialsException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");

        createTenants(process);

        {
            EmailPassword.signUp(t1, process.getProcess(), "user1@example.com", "password1");
            UserInfo userInfo = EmailPassword.signIn(t1, process.getProcess(), "user1@example.com", "password1");
            assertEquals("user1@example.com", userInfo.email);
        }

        {
            EmailPassword.signUp(t2, process.getProcess(), "user2@example.com", "password2");
            UserInfo userInfo = EmailPassword.signIn(t2, process.getProcess(), "user2@example.com", "password2");
            assertEquals("user2@example.com", userInfo.email);
        }

        {
            EmailPassword.signUp(t3, process.getProcess(), "user3@example.com", "password3");
            UserInfo userInfo = EmailPassword.signIn(t3, process.getProcess(), "user3@example.com", "password3");
            assertEquals("user3@example.com", userInfo.email);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSameEmailWithDifferentPasswordsOnDifferentTenantsWorksCorrectly()
            throws InterruptedException, DuplicateThirdPartyIdException, DuplicateClientTypeException,
            InvalidProviderConfigException, DuplicateTenantException, DeletionInProgressException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException,
            WrongCredentialsException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");

        createTenants(process);

        EmailPassword.signUp(t1, process.getProcess(), "user@example.com", "password1");
        EmailPassword.signUp(t2, process.getProcess(), "user@example.com", "password2");
        EmailPassword.signUp(t3, process.getProcess(), "user@example.com", "password3");

        {
            UserInfo userInfo = EmailPassword.signIn(t1, process.getProcess(), "user@example.com", "password1");
            assertEquals("user@example.com", userInfo.email);
        }

        {
            UserInfo userInfo = EmailPassword.signIn(t2, process.getProcess(), "user@example.com", "password2");
            assertEquals("user@example.com", userInfo.email);
        }

        {
            UserInfo userInfo = EmailPassword.signIn(t3, process.getProcess(), "user@example.com", "password3");
            assertEquals("user@example.com", userInfo.email);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetUserUsingIdReturnsCorrectUser()
            throws InterruptedException, DuplicateThirdPartyIdException, DuplicateClientTypeException,
            InvalidProviderConfigException, DuplicateTenantException, DeletionInProgressException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");

        createTenants(process);

        UserInfo user1 = EmailPassword.signUp(t1, process.getProcess(), "user1@example.com", "password1");
        UserInfo user2 = EmailPassword.signUp(t2, process.getProcess(), "user2@example.com", "password2");
        UserInfo user3 = EmailPassword.signUp(t3, process.getProcess(), "user3@example.com", "password3");

        {
            UserInfo userInfo = EmailPassword.getUserUsingId(new AppIdentifier(null, "a1"), user1.id);
            assertEquals(user1, userInfo);
        }

        {
            UserInfo userInfo = EmailPassword.getUserUsingId(new AppIdentifier(null, "a1"), user2.id);
            assertEquals(user2, userInfo);
        }

        {
            UserInfo userInfo = EmailPassword.getUserUsingId(new AppIdentifier(null, "a1"), user3.id);
            assertEquals(user3, userInfo);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetUserUsingEmailReturnsTheUserFromTheSpecificTenant()
            throws InterruptedException, DuplicateThirdPartyIdException, DuplicateClientTypeException,
            InvalidProviderConfigException, DuplicateTenantException, DeletionInProgressException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");

        createTenants(process);

        UserInfo user1 = EmailPassword.signUp(t1, process.getProcess(), "user@example.com", "password1");
        UserInfo user2 = EmailPassword.signUp(t2, process.getProcess(), "user@example.com", "password2");
        UserInfo user3 = EmailPassword.signUp(t3, process.getProcess(), "user@example.com", "password3");

        {
            UserInfo userInfo = EmailPassword.getUserUsingEmail(t1, user1.email);
            assertEquals(user1, userInfo);
        }

        {
            UserInfo userInfo = EmailPassword.getUserUsingEmail(t2, user2.email);
            assertEquals(user2, userInfo);
        }

        {
            UserInfo userInfo = EmailPassword.getUserUsingEmail(t3, user3.email);
            assertEquals(user3, userInfo);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatePasswordWorksCorrectlyAcrossAllTenants()
            throws InterruptedException, DuplicateThirdPartyIdException, DuplicateClientTypeException,
            InvalidProviderConfigException, DuplicateTenantException, DeletionInProgressException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException,
            StorageTransactionLogicException, UnknownUserIdException, WrongCredentialsException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");

        createTenants(process);

        UserInfo user1 = EmailPassword.signUp(t1, process.getProcess(), "user@example.com", "password1");
        UserInfo user2 = EmailPassword.signUp(t2, process.getProcess(), "user@example.com", "password2");
        UserInfo user3 = EmailPassword.signUp(t3, process.getProcess(), "user@example.com", "password3");

        EmailPassword.updateUsersEmailOrPassword(new AppIdentifier(null, "a1"), process.getProcess(), user1.id, null, "newpassword1");
        EmailPassword.updateUsersEmailOrPassword(new AppIdentifier(null, "a1"), process.getProcess(), user2.id, null, "newpassword2");
        EmailPassword.updateUsersEmailOrPassword(new AppIdentifier(null, "a1"), process.getProcess(), user3.id, null, "newpassword3");

        {
            UserInfo userInfo = EmailPassword.signIn(t1, process.getProcess(), "user@example.com", "newpassword1");
            assertEquals(user1.id, userInfo.id);
        }

        {
            UserInfo userInfo = EmailPassword.signIn(t2, process.getProcess(), "user@example.com", "newpassword2");
            assertEquals(user2.id, userInfo.id);
        }

        {
            UserInfo userInfo = EmailPassword.signIn(t3, process.getProcess(), "user@example.com", "newpassword3");
            assertEquals(user3.id, userInfo.id);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
