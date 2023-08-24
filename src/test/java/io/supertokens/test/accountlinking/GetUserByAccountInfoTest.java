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

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.EmailChangeNotAllowedException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.exceptions.*;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateLinkCodeHashException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static org.junit.Assert.*;

public class GetUserByAccountInfoTest {
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

    AuthRecipeUserInfo createEmailPasswordUser(Main main, String email, String password)
            throws DuplicateEmailException, StorageQueryException {
        return EmailPassword.signUp(main, email, password);
    }

    AuthRecipeUserInfo createThirdPartyUser(Main main, String thirdPartyId, String thirdPartyUserId, String email)
            throws EmailChangeNotAllowedException, StorageQueryException {
        return ThirdParty.signInUp(main, thirdPartyId, thirdPartyUserId, email).user;
    }

    AuthRecipeUserInfo createPasswordlessUserWithEmail(Main main, String email)
            throws DuplicateLinkCodeHashException, StorageQueryException, NoSuchAlgorithmException, IOException,
            RestartFlowException, InvalidKeyException, Base64EncodingException, DeviceIdHashMismatchException,
            StorageTransactionLogicException, IncorrectUserInputCodeException, ExpiredUserInputCodeException {
        Passwordless.CreateCodeResponse code = Passwordless.createCode(main, email, null,
                null, "123456");
        return Passwordless.consumeCode(main, code.deviceId, code.deviceIdHash,
                code.userInputCode, null).user;
    }

    AuthRecipeUserInfo createPasswordlessUserWithPhone(Main main, String phone)
            throws DuplicateLinkCodeHashException, StorageQueryException, NoSuchAlgorithmException, IOException,
            RestartFlowException, InvalidKeyException, Base64EncodingException, DeviceIdHashMismatchException,
            StorageTransactionLogicException, IncorrectUserInputCodeException, ExpiredUserInputCodeException {
        Passwordless.CreateCodeResponse code = Passwordless.createCode(main, null, phone,
                null, "123456");
        return Passwordless.consumeCode(main, code.deviceId, code.deviceIdHash,
                code.userInputCode, null).user;
    }

    @Test
    public void testListUsersByAccountInfoForUnlinkedAccounts() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        AuthRecipeUserInfo user1 = createEmailPasswordUser(process.getProcess(), "test1@example.com", "password1");
        AuthRecipeUserInfo user2 = createThirdPartyUser(process.getProcess(), "google", "userid1", "test2@example.com");
        AuthRecipeUserInfo user3 = createPasswordlessUserWithEmail(process.getProcess(), "test3@example.com");
        AuthRecipeUserInfo user4 = createPasswordlessUserWithPhone(process.getProcess(), "+919876543210");

        TenantIdentifierWithStorage tenantIdentifierWithStorage = TenantIdentifier.BASE_TENANT.withStorage(StorageLayer.getBaseStorage(process.getProcess()));

        AuthRecipeUserInfo userToTest = AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test1@example.com", null, null, null)[0];
        assertNotNull(userToTest.getUserIdNotToBeReturnedFromAPI());
        assertFalse(userToTest.isPrimaryUser);
        assertEquals(1, userToTest.loginMethods.length);
        assertEquals("test1@example.com", userToTest.loginMethods[0].email);
        assertEquals(RECIPE_ID.EMAIL_PASSWORD, userToTest.loginMethods[0].recipeId);
        assertEquals(user1.getUserIdNotToBeReturnedFromAPI(), userToTest.loginMethods[0].getRecipeUserIdNotToBeReturnedFromAPI());
        assertFalse(userToTest.loginMethods[0].verified);
        assert(userToTest.loginMethods[0].timeJoined > 0);

        // test for result
        assertEquals(user1, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, "test1@example.com", null, null, null)[0]);
        assertEquals(user2, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, null, null, "google", "userid1")[0]);
        assertEquals(user2, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, "test2@example.com", null, "google", "userid1")[0]);
        assertEquals(user3, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, "test3@example.com", null, null, null)[0]);
        assertEquals(user4, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, null, "+919876543210", null, null)[0]);

        // test for no result
        assertEquals(0, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, "test1@example.com", "+919876543210", null, null).length);
        assertEquals(0, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, "test2@example.com", "+919876543210", null, null).length);
        assertEquals(0, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, "test3@example.com", "+919876543210", null, null).length);
        assertEquals(0, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, null, "+919876543210", "google", "userid1").length);
        assertEquals(0, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, "test1@gmail.com", null, "google", "userid1").length);
        assertEquals(0, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, "test3@gmail.com", null, "google", "userid1").length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListUsersByAccountInfoForUnlinkedAccountsWithUnionOption() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        AuthRecipeUserInfo user1 = createEmailPasswordUser(process.getProcess(), "test1@example.com", "password1");
        AuthRecipeUserInfo user2 = createThirdPartyUser(process.getProcess(), "google", "userid1", "test2@example.com");
        AuthRecipeUserInfo user3 = createPasswordlessUserWithEmail(process.getProcess(), "test3@example.com");
        AuthRecipeUserInfo user4 = createPasswordlessUserWithPhone(process.getProcess(), "+919876543210");

        TenantIdentifierWithStorage tenantIdentifierWithStorage = TenantIdentifier.BASE_TENANT.withStorage(StorageLayer.getBaseStorage(process.getProcess()));
        {
            AuthRecipeUserInfo[] users = AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, true, "test1@example.com", "+919876543210", null, null);
            assertEquals(2, users.length);
            assertTrue(Arrays.asList(users).contains(user1));
            assertTrue(Arrays.asList(users).contains(user4));
        }
        {
            AuthRecipeUserInfo[] users = AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, true, "test1@example.com", null, "google", "userid1");
            assertEquals(2, users.length);
            assertTrue(Arrays.asList(users).contains(user1));
            assertTrue(Arrays.asList(users).contains(user2));
        }
        {
            AuthRecipeUserInfo[] users = AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, true, null, "+919876543210", "google", "userid1");
            assertEquals(2, users.length);
            assertTrue(Arrays.asList(users).contains(user4));
            assertTrue(Arrays.asList(users).contains(user2));
        }
        {
            AuthRecipeUserInfo[] users = AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, true, "test1@example.com", "+919876543210", "google", "userid1");
            assertEquals(3, users.length);
            assertTrue(Arrays.asList(users).contains(user1));
            assertTrue(Arrays.asList(users).contains(user2));
            assertTrue(Arrays.asList(users).contains(user4));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUnknownAccountInfo() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        TenantIdentifierWithStorage tenantIdentifierWithStorage = TenantIdentifier.BASE_TENANT.withStorage(StorageLayer.getBaseStorage(process.getProcess()));
        assertEquals(0, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, "test1@example.com", null, null, null).length);
        assertEquals(0, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, null, null, "google", "userid1").length);
        assertEquals(0, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, "test3@example.com", null, null, null).length);
        assertEquals(0, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false, null, "+919876543210", null, null).length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListUserByAccountInfoWhenAccountsAreLinked1() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password1");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = ThirdParty.signInUp(process.getProcess(), "google", "userid1", "test2@example.com").user;

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(), user1.getUserIdNotToBeReturnedFromAPI()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getUserIdNotToBeReturnedFromAPI(), primaryUser.getUserIdNotToBeReturnedFromAPI());

        TenantIdentifierWithStorage tenantIdentifierWithStorage = TenantIdentifier.BASE_TENANT.withStorage(
                StorageLayer.getBaseStorage(process.getProcess()));

        primaryUser = AuthRecipe.getUserById(process.getProcess(), user1.getUserIdNotToBeReturnedFromAPI());

        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test1@example.com", null, null, null)[0]);
        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test2@example.com", null, null, null)[0]);
        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                null, null, "google", "userid1")[0]);
        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test1@example.com", null, "google", "userid1")[0]);
        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test2@example.com", null, "google", "userid1")[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListUserByAccountInfoWhenAccountsAreLinked2() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        AuthRecipeUserInfo user1 = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password1");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = EmailPassword.signUp(process.getProcess(), "test2@example.com", "password2");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(), user1.getUserIdNotToBeReturnedFromAPI()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getUserIdNotToBeReturnedFromAPI(), primaryUser.getUserIdNotToBeReturnedFromAPI());

        TenantIdentifierWithStorage tenantIdentifierWithStorage = TenantIdentifier.BASE_TENANT.withStorage(
                StorageLayer.getBaseStorage(process.getProcess()));

        primaryUser = AuthRecipe.getUserById(process.getProcess(), user1.getUserIdNotToBeReturnedFromAPI());

        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test1@example.com", null, null, null)[0]);
        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test2@example.com", null, null, null)[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListUserByAccountInfoWhenAccountsAreLinked3() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        AuthRecipeUserInfo user1 = createEmailPasswordUser(process.getProcess(), "test1@example.com", "password1");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = createPasswordlessUserWithEmail(process.getProcess(), "test2@example.com");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(), user1.getUserIdNotToBeReturnedFromAPI()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getUserIdNotToBeReturnedFromAPI(), primaryUser.getUserIdNotToBeReturnedFromAPI());

        TenantIdentifierWithStorage tenantIdentifierWithStorage = TenantIdentifier.BASE_TENANT.withStorage(
                StorageLayer.getBaseStorage(process.getProcess()));

        primaryUser = AuthRecipe.getUserById(process.getProcess(), user1.getUserIdNotToBeReturnedFromAPI());

        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test1@example.com", null, null, null)[0]);
        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test2@example.com", null, null, null)[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListUserByAccountInfoWhenAccountsAreLinked4() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        AuthRecipeUserInfo user1 = createEmailPasswordUser(process.getProcess(), "test1@example.com", "password1");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = createPasswordlessUserWithPhone(process.getProcess(), "+919876543210");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(), user1.getUserIdNotToBeReturnedFromAPI()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getUserIdNotToBeReturnedFromAPI(), primaryUser.getUserIdNotToBeReturnedFromAPI());

        TenantIdentifierWithStorage tenantIdentifierWithStorage = TenantIdentifier.BASE_TENANT.withStorage(
                StorageLayer.getBaseStorage(process.getProcess()));

        primaryUser = AuthRecipe.getUserById(process.getProcess(), user1.getUserIdNotToBeReturnedFromAPI());

        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test1@example.com", null, null, null)[0]);
        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                null, "+919876543210", null, null)[0]);
        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test1@example.com", "+919876543210", null, null)[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testListUserByAccountInfoWhenAccountsAreLinked5() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        AuthRecipeUserInfo user1 = createEmailPasswordUser(process.getProcess(), "test1@example.com", "password1");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = createThirdPartyUser(process.getProcess(), "google", "userid1", "test2@example.com");

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(), user1.getUserIdNotToBeReturnedFromAPI()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user2.getUserIdNotToBeReturnedFromAPI(), primaryUser.getUserIdNotToBeReturnedFromAPI());

        TenantIdentifierWithStorage tenantIdentifierWithStorage = TenantIdentifier.BASE_TENANT.withStorage(
                StorageLayer.getBaseStorage(process.getProcess()));

        primaryUser = AuthRecipe.getUserById(process.getProcess(), user1.getUserIdNotToBeReturnedFromAPI());

        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test1@example.com", null, null, null)[0]);
        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test2@example.com", null, null, null)[0]);
        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                null, null, "google", "userid1")[0]);
        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test1@example.com", null, "google", "userid1")[0]);
        assertEquals(primaryUser, AuthRecipe.getUsersByAccountInfo(tenantIdentifierWithStorage, false,
                "test2@example.com", null, "google", "userid1")[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
