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

package io.supertokens.test;

import io.supertokens.ProcessState;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.pluginInterface.thirdparty.UserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/*
 * TODO:
 *  - Test simple sign up and then sign in to get the same user. Check number of rows in db is one
 *  - After sign up, with verified email being false, check that email is not verified
 *  - Sign up with verified email being true and check that it signs up
 *  - Sign up with false verified email, and then sign in with verified email and check that its verified
 *  - Sign up with email A, then sign in with email B, and check that email is updated, A is verified, and B is not
 *     verified
 *  - Sign up with same third party ID, but diff third party userID and check two diff rows
 *  - Sign up with same third party ID, diff third party userID, but same email and check two diff rows
 *  - Try to sign up with same third part ID and third party userId and check you get DuplicateThirdPartyUserException
 *  - Try to sign up with same userId and check that you get DuplicateUserIdException
 *  - Sign up with verified email, then sign in, but make that email unverified. Check that email is still verified
 *     in our system
 *  - Test both getUser functions (perhaps already tested with the above...)
 *  - getUsers from emailpassword tests
 *  - getUsersCount from emailpassword tests
 * */

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
    @Test
    public void testSignInAndSignUp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId = "thirdPartyUserId";
        String email = "test@example.com";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email, false);
        checkSignInUpResponse(signUpResponse, thirdPartyUserId, thirdPartyId,
                email, true);

        ThirdParty.SignInUpResponse signInResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email, false);
        checkSignInUpResponse(signInResponse, thirdPartyUserId, thirdPartyId, email, false);

        assertEquals(1, StorageLayer.getThirdPartyStorage(process.getProcess()).getThirdPartyUsersCount());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    //After sign up, with verified email being false, check that email is not verified
    @Test
    public void testSignUpWithEmailNotVerifiedAndCheckEmailIsNotVerified() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId = "thirdPartyUserId";
        String email = "test@example.com";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email, false);
        checkSignInUpResponse(signUpResponse, thirdPartyUserId, thirdPartyId,
                email, true);

        UserInfo user = ThirdParty.getUser(process.getProcess(), thirdPartyUserId);
        assertFalse(EmailVerification.isEmailVerified(process.getProcess(), user.id, email));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // - Sign up with verified email being true and check that it signs up
    @Test
    public void testSignUpWithVerifiedEmailTrueAndCheckSignUp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String thirdPartyId = "testThirdParty";
        String thirdPartyUserId = "thirdPartyUserId";
        String email = "test@example.com";

        ThirdParty.SignInUpResponse signUpResponse = ThirdParty.signInUp(process.getProcess(), thirdPartyId,
                thirdPartyUserId, email, true);
        checkSignInUpResponse(signUpResponse, thirdPartyUserId, thirdPartyId,
                email, true);
        

    }

    public static void checkSignInUpResponse(ThirdParty.SignInUpResponse response, String thirdPartyUserId,
                                             String thirdPartyId, String email, boolean createNewUser) {
        assertEquals(response.createdNewUser, createNewUser);
        assertNotNull(response.user.id);
        assertNotNull(response.user.timeJoined);
        assertEquals(response.user.thirdParty.userId, thirdPartyUserId);
        assertEquals(response.user.thirdParty.id, thirdPartyId);
        assertEquals(response.user.thirdParty.email, email);

    }


}
