/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.thirdparty;

import io.supertokens.ProcessState;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateThirdPartyUserException;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateUserIdException;
import io.supertokens.pluginInterface.thirdparty.sqlStorage.ThirdPartySQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class ThirdPartyTest {

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

    // - Test simple sign up and then sign in to get the same user. Check number of rows in db is one
    // - Failure condition: if signup/signin responses return incorrect values/ an additional
    // row is created when trying to signin
    @Test
    public void testSignInAndSignUp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId = "thirdPartyUserId";
        String email = "test@example.com";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email);
        checkSignInUpResponse(signUpResponse, thirdPartyUserId, thirdPartyId, email, true);

        ThirdParty.SignInUpResponse signInResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email);
        checkSignInUpResponse(signInResponse, thirdPartyUserId, thirdPartyId, email, false);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // After sign up, with verified email being false, check that email is not verified
    // Failure condition: test fails if signin is called with isEmailVerified is true
    @Test
    public void testSignUpWithEmailNotVerifiedAndCheckEmailIsNotVerified() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId = "thirdPartyUserId";
        String email = "test@example.com";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email);
        checkSignInUpResponse(signUpResponse, thirdPartyUserId, thirdPartyId, email, true);

        assertFalse(EmailVerification.isEmailVerified(process.getProcess(), signUpResponse.user.getSupertokensUserId(),
                signUpResponse.user.loginMethods[0].email));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // - Sign up with verified email being true and check that it signs up
    @Test
    public void testSignUpWithVerifiedEmailTrueAndCheckSignUp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId = "thirdPartyUserId";
        String email = "test@example.com";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email);
        checkSignInUpResponse(signUpResponse, thirdPartyUserId, thirdPartyId, email, true);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // - Sign up with false verified email, and then sign in with verified email and check that its verified
    // Failure condition: test fails if isEmailVerified is set to false on signin
    @Test
    public void testSignUpWithFalseVerifiedEmailAndSignInWithVerifiedEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId = "thirdPartyUserId";
        String email = "test@example.com";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email);
        checkSignInUpResponse(signUpResponse, thirdPartyUserId, thirdPartyId, email, true);

        assertFalse(EmailVerification.isEmailVerified(process.getProcess(), signUpResponse.user.getSupertokensUserId(),
                signUpResponse.user.loginMethods[0].email));

        ThirdParty.SignInUpResponse signInResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email);
        checkSignInUpResponse(signInResponse, thirdPartyUserId, thirdPartyId, email, false);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Sign up with email A, then sign in with email B, and check that email is updated, A is verified, and B is not
    // * verified
    // failure condition: if isEmailVerified set to true in signin the test will fail
    @Test
    public void testUpdatingEmailAndCheckVerification() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId = "thirdPartyUserId";
        String email_1 = "testA@example.com";
        String email_2 = "testB@example.com";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email_1);
        checkSignInUpResponse(signUpResponse, thirdPartyUserId, thirdPartyId, email_1, true);

        ThirdParty.SignInUpResponse signInResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email_2);
        checkSignInUpResponse(signInResponse, thirdPartyUserId, thirdPartyId, email_2, false);

        assertFalse(EmailVerification.isEmailVerified(process.getProcess(), signInResponse.user.getSupertokensUserId(),
                signInResponse.user.loginMethods[0].email));

        AuthRecipeUserInfo updatedUserInfo = ThirdParty.getUser(process.getProcess(), thirdPartyId, thirdPartyUserId);
        assertEquals(updatedUserInfo.loginMethods[0].email, email_2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Sign up with same third party ID, but diff third party userID and check two diff rows
    // Failure condition: if both sign in with same thirdPartyUserId, test fails
    @Test
    public void testSignUpWithSameThirdPartyIdButDiffThirdPartyUserId() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId_1 = "thirdPartyUserIdA";
        String thirdPartyUserId_2 = "thirdPartyUserIdB";
        String email_1 = "testA@example.com";
        String email_2 = "testB@example.com";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId_1, email_1);

        checkSignInUpResponse(signUpResponse, thirdPartyUserId_1, thirdPartyId, email_1, true);

        ThirdParty.SignInUpResponse signInUpResponse_2 = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId_2, email_2);

        checkSignInUpResponse(signInUpResponse_2, thirdPartyUserId_2, thirdPartyId, email_2, true);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // - Sign up with same third party ID, diff third party userID, but same email and check two diff rows
    // Failure condition: if both sign in with same thirdPartyUserId, test fails
    @Test
    public void testSignUpWithSameThirdPartyIdButDiffThirdPartyUserIdWithSameMail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId_1 = "thirdPartyUserIdA";
        String thirdPartyUserId_2 = "thirdPartyUserIdB";
        String email = "test@example.com";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId_1, email);

        checkSignInUpResponse(signUpResponse, thirdPartyUserId_1, thirdPartyId, email, true);

        ThirdParty.SignInUpResponse signInUpResponse_2 = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId_2, email);

        checkSignInUpResponse(signInUpResponse_2, thirdPartyUserId_2, thirdPartyId, email, true);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Try to sign up with same third part ID and third party userId and check you get DuplicateThirdPartyUserException
    // // Failure condition: if you signin with different third part ID and third party userId
    @Test
    public void testSignUpWithSameThirdPartyThirdPartyUserIdException() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId = "thirdPartyUserId";
        String email = "test@example.com";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email);

        checkSignInUpResponse(signUpResponse, thirdPartyUserId, thirdPartyId, email, true);
        try {
            ((ThirdPartySQLStorage) StorageLayer.getStorage(process.getProcess()))
                    .signUp(new TenantIdentifier(null, null, null), io.supertokens.utils.Utils.getUUID(), email,
                            new LoginMethod.ThirdParty(thirdPartyId, thirdPartyUserId), System.currentTimeMillis());
            throw new Exception("Should not come here");
        } catch (DuplicateThirdPartyUserException ignored) {
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Try to sign up with same userId and check that you get DuplicateUserIdException
    // failure condition: sign up with different userId
    @Test
    public void testSignUpWithSameUserIdAndCheckDuplicateUserIdException() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId = "thirdPartyUserId";
        String email = "test@example.com";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email);

        checkSignInUpResponse(signUpResponse, thirdPartyUserId, thirdPartyId, email, true);
        try {
            ((ThirdPartySQLStorage) StorageLayer.getStorage(process.getProcess()))
                    .signUp(new TenantIdentifier(null, null, null), signUpResponse.user.getSupertokensUserId(), email,
                            new LoginMethod.ThirdParty("newThirdParty", "newThirdPartyUserId"),
                            System.currentTimeMillis());
            throw new Exception("Should not come here");
        } catch (DuplicateUserIdException ignored) {
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Sign up with verified email, then sign in, but make that email unverified. Check that email is still verified
    // * in our system
    @Test
    public void testSignUpWithVerifiedEmailSignInWithUnVerifiedEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId = "thirdPartyUserId";
        String email = "test@example.com";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email);

        checkSignInUpResponse(signUpResponse, thirdPartyUserId, thirdPartyId, email, true);

        ThirdParty.SignInUpResponse signInResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email);

        checkSignInUpResponse(signInResponse, thirdPartyUserId, thirdPartyId, email, false);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Test both getUser functions
    // failure condition: test fails if getUser function response is not proper
    @Test
    public void testGetUserFunctions() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId = "thirdPartyUserId";
        String email = "test@example.com";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email);

        checkSignInUpResponse(signUpResponse, thirdPartyUserId, thirdPartyId, email, true);

        AuthRecipeUserInfo getUserInfoFromId = ThirdParty.getUser(process.getProcess(),
                signUpResponse.user.getSupertokensUserId());
        assertEquals(getUserInfoFromId.getSupertokensUserId(), signUpResponse.user.getSupertokensUserId());
        assertEquals(getUserInfoFromId.timeJoined, signUpResponse.user.timeJoined);
        assertEquals(getUserInfoFromId.loginMethods[0].email, signUpResponse.user.loginMethods[0].email);
        assertEquals(getUserInfoFromId.loginMethods[0].thirdParty.userId,
                signUpResponse.user.loginMethods[0].thirdParty.userId);
        assertEquals(getUserInfoFromId.loginMethods[0].thirdParty.id,
                signUpResponse.user.loginMethods[0].thirdParty.id);

        AuthRecipeUserInfo getUserInfoFromThirdParty = ThirdParty.getUser(process.getProcess(),
                signUpResponse.user.loginMethods[0].thirdParty.id,
                signUpResponse.user.loginMethods[0].thirdParty.userId);
        assertEquals(getUserInfoFromThirdParty.getSupertokensUserId(), signUpResponse.user.getSupertokensUserId());
        assertEquals(getUserInfoFromThirdParty.timeJoined, signUpResponse.user.timeJoined);
        assertEquals(getUserInfoFromThirdParty.loginMethods[0].email, signUpResponse.user.loginMethods[0].email);
        assertEquals(getUserInfoFromThirdParty.loginMethods[0].thirdParty.userId,
                signUpResponse.user.loginMethods[0].thirdParty.userId);
        assertEquals(getUserInfoFromThirdParty.loginMethods[0].thirdParty.id,
                signUpResponse.user.loginMethods[0].thirdParty.id);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    public static void checkSignInUpResponse(ThirdParty.SignInUpResponse response, String thirdPartyUserId,
                                             String thirdPartyId, String email, boolean createNewUser) {
        assertEquals(response.createdNewUser, createNewUser);
        assertNotNull(response.user.getSupertokensUserId());
        assertEquals(response.user.loginMethods[0].thirdParty.userId, thirdPartyUserId);
        assertEquals(response.user.loginMethods[0].thirdParty.id, thirdPartyId);
        assertEquals(response.user.loginMethods[0].email, email);

    }
}
