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
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
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
import java.util.Collections;

import static org.junit.Assert.*;

public class GetUserByIdTest {
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
    public void testAllLoginMethods() throws Exception {
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

        AuthRecipeUserInfo user1 = createEmailPasswordUser(process.getProcess(), "test@example.com", "password1");
        Thread.sleep(50);
        AuthRecipeUserInfo user2 = createThirdPartyUser(process.getProcess(), "google", "userid1", "test@example.com");
        Thread.sleep(50);
        AuthRecipeUserInfo user3 = createPasswordlessUserWithEmail(process.getProcess(), "test@example.com");
        Thread.sleep(50);
        AuthRecipeUserInfo user4 = createPasswordlessUserWithPhone(process.getProcess(), "+919876543210");

        assertFalse(user1.isPrimaryUser);
        assertFalse(user2.isPrimaryUser);
        assertFalse(user3.isPrimaryUser);
        assertFalse(user4.isPrimaryUser);

        AuthRecipeUserInfo userToTest = AuthRecipe.getUserById(process.getProcess(), user1.getSupertokensUserId());
        assertNotNull(userToTest.getSupertokensUserId());
        assertFalse(userToTest.isPrimaryUser);
        assertEquals(1, userToTest.loginMethods.length);
        assertEquals("test@example.com", userToTest.loginMethods[0].email);
        assertEquals(RECIPE_ID.EMAIL_PASSWORD, userToTest.loginMethods[0].recipeId);
        assertEquals(user1.getSupertokensUserId(), userToTest.loginMethods[0].getSupertokensUserId());
        assertFalse(userToTest.loginMethods[0].verified);
        assert (userToTest.loginMethods[0].timeJoined > 0);

        assertEquals(user1, AuthRecipe.getUserById(process.getProcess(), user1.getSupertokensUserId()));
        assertEquals(user2, AuthRecipe.getUserById(process.getProcess(), user2.getSupertokensUserId()));
        assertEquals(user3, AuthRecipe.getUserById(process.getProcess(), user3.getSupertokensUserId()));
        assertEquals(user4, AuthRecipe.getUserById(process.getProcess(), user4.getSupertokensUserId()));

        AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(),
                user4.getSupertokensUserId()).user;
        AuthRecipe.linkAccounts(process.getProcess(), user3.getSupertokensUserId(), primaryUser.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user2.getSupertokensUserId(), primaryUser.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.getProcess(), user1.getSupertokensUserId(), primaryUser.getSupertokensUserId());

        for (String userId : new String[]{user1.getSupertokensUserId(), user2.getSupertokensUserId(),
                user3.getSupertokensUserId(), user4.getSupertokensUserId()}) {
            AuthRecipeUserInfo result = AuthRecipe.getUserById(process.getProcess(), userId);
            assertTrue(result.isPrimaryUser);

            assertEquals(4, result.loginMethods.length);
            assertEquals(user1.loginMethods[0], result.loginMethods[0]);
            assertEquals(user2.loginMethods[0], result.loginMethods[1]);
            assertEquals(user3.loginMethods[0], result.loginMethods[2]);
            assertEquals(user4.loginMethods[0], result.loginMethods[3]);

            assertEquals(user1.timeJoined, result.timeJoined);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUnknownUserIdReturnsNull() throws Exception {
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

        assertNull(AuthRecipe.getUserById(process.getProcess(), "unknownid"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testLoginMethodsAreSortedByTime() throws Exception {
        for (int i = 0; i < 10; i++) {
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

            // Create users
            AuthRecipeUserInfo user4 = createPasswordlessUserWithPhone(process.getProcess(), "+919876543210");
            Thread.sleep(50);
            AuthRecipeUserInfo user2 = createThirdPartyUser(process.getProcess(), "google", "userid1",
                    "test@example.com");
            Thread.sleep(50);
            AuthRecipeUserInfo user3 = createPasswordlessUserWithEmail(process.getProcess(), "test@example.com");
            Thread.sleep(50);
            AuthRecipeUserInfo user1 = createEmailPasswordUser(process.getProcess(), "test@example.com", "password1");

            // Link accounts randomly
            String[] userIds = new String[]{user1.getSupertokensUserId(), user2.getSupertokensUserId(),
                    user3.getSupertokensUserId(), user4.getSupertokensUserId()};
            Collections.shuffle(Arrays.asList(userIds));
            AuthRecipeUserInfo primaryUser = AuthRecipe.createPrimaryUser(process.getProcess(), userIds[0]).user;
            AuthRecipe.linkAccounts(process.getProcess(), userIds[1], primaryUser.getSupertokensUserId());
            AuthRecipe.linkAccounts(process.getProcess(), userIds[2], primaryUser.getSupertokensUserId());
            AuthRecipe.linkAccounts(process.getProcess(), userIds[3], primaryUser.getSupertokensUserId());

            for (String userId : userIds) {
                AuthRecipeUserInfo result = AuthRecipe.getUserById(process.getProcess(), userId);
                assertTrue(result.isPrimaryUser);

                assertEquals(4, result.loginMethods.length);
                assert (result.loginMethods[0].timeJoined <= result.loginMethods[1].timeJoined);
                assert (result.loginMethods[1].timeJoined <= result.loginMethods[2].timeJoined);
                assert (result.loginMethods[2].timeJoined <= result.loginMethods[3].timeJoined);
            }
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }
}
