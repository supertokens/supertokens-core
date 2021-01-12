/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.User;
import io.supertokens.emailpassword.exceptions.EmailAlreadyVerifiedException;
import io.supertokens.emailpassword.exceptions.EmailVerificationInvalidTokenException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.EmailVerificationTokenInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailVerificationTokenException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/*
 * TODO:
 *  - Create an email verification token two times, and check that there are two entries in the db for that user with
 *  the right values
 *  - Verify the email successfully, then create an email verification token and check that the right error is thrown.
 *  - (later) Email verify double lock test. First we lock the token table, then the user table. Does this work?
 *  - Do all password reset token tests with email verification token. For example:
 *    - Give invalid token
 *    - Generate two tokens, verify with one token, the other token should throw an invalid token error
 *    - Use an expired token, it should throw an error
 *    - Test the format of the email verification token
 *    - (later) Create token, change email of user, use the token -> should fail with invalid token
 * */

public class EmailPasswordTest_2 {
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

    // Create an email verification token two times, and check that there are two entries in the db for that user with
    // *  the right values
    @Test
    public void testGeneratingEmailVerificationTokenTwoTimes() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        User user = EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123");
        String token1 = EmailPassword.generateEmailVerificationToken(process.getProcess(), user.id);
        String token2 = EmailPassword.generateEmailVerificationToken(process.getProcess(), user.id);

        assertFalse(token1.equals(token2));

        EmailVerificationTokenInfo[] tokenInfo = StorageLayer.getEmailPasswordStorage(process.getProcess())
                .getAllEmailVerificationTokenInfoForUser(user.id);

        assertEquals(tokenInfo.length, 2);
        assertTrue((tokenInfo[0].token.equals(io.supertokens.utils.Utils.hashSHA256(token1))) ||
                (tokenInfo[0].token.equals(io.supertokens.utils.Utils.hashSHA256(token2))));
        assertTrue((tokenInfo[1].token.equals(io.supertokens.utils.Utils.hashSHA256(token1))) ||
                (tokenInfo[1].token.equals(io.supertokens.utils.Utils.hashSHA256(token2))));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Verify the email successfully, then create an email verification token and check that the right error is thrown.
    @Test
    public void testVerifyingEmailAndGeneratingToken() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        User user = EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123");
        String token = EmailPassword.generateEmailVerificationToken(process.getProcess(), user.id);

        EmailPassword.verifyEmail(process.getProcess(), token);
        assertTrue(EmailPassword.isEmailVerified(process.getProcess(), user.id));

        try {

            EmailPassword.generateEmailVerificationToken(process.getProcess(), user.id);
            throw new Exception("should not come here");
        } catch (EmailAlreadyVerifiedException e) {
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // give invalid token to verify email
    @Test
    public void testInvalidTokenInputToVerifyEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            EmailPassword.verifyEmail(process.getProcess(), "invalidToken");
            throw new Exception("should not come here");
        } catch (EmailVerificationInvalidTokenException e) {
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Generate two tokens, verify with one token, the other token should throw an invalid token error
    @Test
    public void testGeneratingTwoTokenVerifyOtherTokenShouldThrowAnError() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        User user = EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123");

        String token1 = EmailPassword.generateEmailVerificationToken(process.getProcess(), user.id);
        String token2 = EmailPassword.generateEmailVerificationToken(process.getProcess(), user.id);

        EmailPassword.verifyEmail(process.getProcess(), token1);
        assertTrue(EmailPassword.isEmailVerified(process.getProcess(), user.id));

        try {
            EmailPassword.verifyEmail(process.getProcess(), token2);
            throw new Exception("should not come here");
        } catch (EmailVerificationInvalidTokenException e) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Use an expired token, it should throw an error
    @Test
    public void useAnExpiredTokenItShouldThrowAnError() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);

        io.supertokens.emailpassword.EmailPasswordTest.getInstance(process.getProcess())
                .setEmailVerificationTokenLifetime(10);

        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        User user = EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123");

        String token = EmailPassword.generateEmailVerificationToken(process.getProcess(), user.id);

        Thread.sleep(20);

        try {
            EmailPassword.verifyEmail(process.getProcess(), token);
            throw new Exception("should not come here");
        } catch (EmailVerificationInvalidTokenException e) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Test the format of the email verification token
    @Test
    public void testFormatOfEmailVerificationToken() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        User user = EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123");

        for (int i = 0; i < 100; i++) {
            String verifyToken = EmailPassword.generatePasswordResetToken(process.getProcess(), user.id);
            assertEquals(verifyToken.length(), 128);
            assertFalse(verifyToken.contains("+"));
            assertFalse(verifyToken.contains("="));
            assertFalse(verifyToken.contains("/"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void unknownUserIdWhileGeneratingEmailVerificationToken() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            StorageLayer.getEmailPasswordStorage(process.getProcess()).addEmailVerificationToken(
                    new EmailVerificationTokenInfo("8ed86166-bfd8-4234-9dfe-abca9606dbd5", "token",
                            0, "test@supertokens.io"));
            assert (false);
        } catch (UnknownUserIdException ignored) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void clashingEmailVerificationToken() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // we add a user first.
        User user = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");

        StorageLayer.getEmailPasswordStorage(process.getProcess())
                .addEmailVerificationToken(new EmailVerificationTokenInfo(
                        user.id, "token",
                        System.currentTimeMillis() + EmailPassword.PASSWORD_RESET_TOKEN_LIFETIME_MS,
                        "test1@example.com"));

        try {
            StorageLayer.getEmailPasswordStorage(process.getProcess())
                    .addEmailVerificationToken(new EmailVerificationTokenInfo(
                            user.id, "token",
                            System.currentTimeMillis() + EmailPassword.PASSWORD_RESET_TOKEN_LIFETIME_MS,
                            "test1@example.com"));
            assert (false);
        } catch (DuplicateEmailVerificationTokenException ignored) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void verifyEmail() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        User user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");

        assert (!EmailPassword.isEmailVerified(process.getProcess(), user.id));

        String token = EmailPassword.generateEmailVerificationToken(process.getProcess(), user.id);

        assert (token != null);

        EmailPassword.verifyEmail(process.getProcess(), token);

        assert (EmailPassword.isEmailVerified(process.getProcess(), user.id));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
