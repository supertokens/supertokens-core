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

package io.supertokens.test.emailverification;

import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.emailverification.exception.EmailAlreadyVerifiedException;
import io.supertokens.emailverification.exception.EmailVerificationInvalidTokenException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailverification.EmailVerificationTokenInfo;
import io.supertokens.pluginInterface.emailverification.exception.DuplicateEmailVerificationTokenException;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;

/*
 * TODO:
 *  - Create an email verification token two times, and check that there are two entries in the db for that user with
 *  the right values
 *  - Verify the email successfully, then create an email verification token and check that the right error is thrown.
 *  - (later) Email verify double lock test. First we lock the token table, then the user table. Does this work?
 *   - (later) Create email verification token, change email of user, use the token -> should fail with invalid token

 * */

public class EmailVerificationTest {
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
    // * the right values
    @Test
    public void testGeneratingEmailVerificationTokenTwoTimes() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123");
        String token1 = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                user.getSupertokensUserId(), user.loginMethods[0].email);
        String token2 = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                user.getSupertokensUserId(), user.loginMethods[0].email);

        assertNotEquals(token1, token2);

        EmailVerificationTokenInfo[] tokenInfo = ((EmailVerificationSQLStorage) StorageLayer.getStorage(
                process.getProcess()))
                .getAllEmailVerificationTokenInfoForUser(new TenantIdentifier(null, null, null),
                        user.getSupertokensUserId(), user.loginMethods[0].email);

        assertEquals(tokenInfo.length, 2);
        assertTrue((tokenInfo[0].token.equals(io.supertokens.utils.Utils.hashSHA256(token1)))
                || (tokenInfo[0].token.equals(io.supertokens.utils.Utils.hashSHA256(token2))));
        assertTrue((tokenInfo[1].token.equals(io.supertokens.utils.Utils.hashSHA256(token1)))
                || (tokenInfo[1].token.equals(io.supertokens.utils.Utils.hashSHA256(token2))));

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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123");
        String token = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                user.getSupertokensUserId(), user.loginMethods[0].email);

        EmailVerification.verifyEmail(process.getProcess(), token);
        assertTrue(EmailVerification.isEmailVerified(process.getProcess(), user.getSupertokensUserId(),
                user.loginMethods[0].email));

        try {

            EmailVerification.generateEmailVerificationToken(process.getProcess(), user.getSupertokensUserId(),
                    user.loginMethods[0].email);
            throw new Exception("should not come here");
        } catch (EmailAlreadyVerifiedException ignored) {
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
            EmailVerification.verifyEmail(process.getProcess(), "invalidToken");
            throw new Exception("should not come here");
        } catch (EmailVerificationInvalidTokenException ignored) {
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
        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123");

        String token1 = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                user.getSupertokensUserId(), user.loginMethods[0].email);
        String token2 = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                user.getSupertokensUserId(), user.loginMethods[0].email);

        EmailVerification.verifyEmail(process.getProcess(), token1);
        assertTrue(EmailVerification.isEmailVerified(process.getProcess(), user.getSupertokensUserId(),
                user.loginMethods[0].email));

        try {
            EmailVerification.verifyEmail(process.getProcess(), token2);
            throw new Exception("should not come here");
        } catch (EmailVerificationInvalidTokenException ignored) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Use an expired token, it should throw an error
    @Test
    public void useAnExpiredTokenItShouldThrowAnError() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        Utils.setValueInConfig("email_verification_token_lifetime", "10");

        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123");

        String token = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                user.getSupertokensUserId(), user.loginMethods[0].email);

        Thread.sleep(20);

        try {
            EmailVerification.verifyEmail(process.getProcess(), token);
            throw new Exception("should not come here");
        } catch (EmailVerificationInvalidTokenException ignored) {

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
        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123");

        for (int i = 0; i < 100; i++) {
            String verifyToken = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                    user.getSupertokensUserId(),
                    user.loginMethods[0].email);
            assertEquals(128, verifyToken.length());
            assertFalse(verifyToken.contains("+"));
            assertFalse(verifyToken.contains("="));
            assertFalse(verifyToken.contains("/"));
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
        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");

        ((EmailVerificationSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .addEmailVerificationToken(new TenantIdentifier(null, null, null),
                        new EmailVerificationTokenInfo(user.getSupertokensUserId(), "token",
                                System.currentTimeMillis()
                                        + Config.getConfig(process.getProcess()).getEmailVerificationTokenLifetime(),
                                "test1@example.com"));

        try {
            ((EmailVerificationSQLStorage) StorageLayer.getStorage(process.getProcess()))
                    .addEmailVerificationToken(new TenantIdentifier(null, null, null),
                            new EmailVerificationTokenInfo(user.getSupertokensUserId(), "token",
                                    System.currentTimeMillis()
                                            +
                                            Config.getConfig(process.getProcess()).getEmailVerificationTokenLifetime(),
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");

        assert (!EmailVerification.isEmailVerified(process.getProcess(), user.getSupertokensUserId(),
                user.loginMethods[0].email));

        String token = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                user.getSupertokensUserId(), user.loginMethods[0].email);

        assert (token != null);

        EmailVerification.verifyEmail(process.getProcess(), token);

        assert (EmailVerification.isEmailVerified(process.getProcess(), user.getSupertokensUserId(),
                user.loginMethods[0].email));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void verifyEmailWithOldTokenAfterTokenGenerationChanged() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");

        assert (!EmailVerification.isEmailVerified(process.getProcess(), user.getSupertokensUserId(),
                user.loginMethods[0].email));

        String token = EmailVerification.generateEmailVerificationTokenTheOldWay(process.getProcess(),
                user.getSupertokensUserId(), user.loginMethods[0].email);

        assert (token != null);

        EmailVerification.verifyEmail(process.getProcess(), token);

        assert (EmailVerification.isEmailVerified(process.getProcess(), user.getSupertokensUserId(),
                user.loginMethods[0].email));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Verify the email successfully, then unverify and check that its unverified
    @Test
    public void testVerifyingEmailAndThenUnverify() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123");
        String token = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                user.getSupertokensUserId(), user.loginMethods[0].email);

        EmailVerification.verifyEmail(process.getProcess(), token);
        assertTrue(EmailVerification.isEmailVerified(process.getProcess(), user.getSupertokensUserId(),
                user.loginMethods[0].email));

        ((EmailVerificationSQLStorage) StorageLayer.getStorage(process.getProcess())).startTransaction(con -> {
            try {
                ((EmailVerificationSQLStorage) StorageLayer.getStorage(process.getProcess()))
                        .updateIsEmailVerified_Transaction(new AppIdentifier(null, null), con,
                                user.getSupertokensUserId(), user.loginMethods[0].email, false);
            } catch (TenantOrAppNotFoundException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        assertFalse(EmailVerification.isEmailVerified(process.getProcess(), user.getSupertokensUserId(),
                user.loginMethods[0].email));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Verify the same email twice
    @Test
    public void testVerifyingSameEmailTwice() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "testPass123");
        String token = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                user.getSupertokensUserId(), user.loginMethods[0].email);

        EmailVerification.verifyEmail(process.getProcess(), token);
        assertTrue(EmailVerification.isEmailVerified(process.getProcess(), user.getSupertokensUserId(),
                user.loginMethods[0].email));

        ((EmailVerificationSQLStorage) StorageLayer.getStorage(process.getProcess())).startTransaction(con -> {
            try {
                ((EmailVerificationSQLStorage) StorageLayer.getStorage(process.getProcess()))
                        .updateIsEmailVerified_Transaction(new AppIdentifier(null, null), con,
                                user.getSupertokensUserId(), user.loginMethods[0].email, true);
            } catch (TenantOrAppNotFoundException e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        assertTrue(EmailVerification.isEmailVerified(process.getProcess(), user.getSupertokensUserId(),
                user.loginMethods[0].email));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void changeEmailVerificationTokenLifetimeTest() throws Exception {
        {

            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            assertEquals(EmailVerification.getEmailVerificationTokenLifetimeForTests(process.getProcess()),
                    24 * 3600 * 1000);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("email_verification_token_lifetime", "100");

            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            assertEquals(EmailVerification.getEmailVerificationTokenLifetimeForTests(process.getProcess()), 100);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("email_verification_token_lifetime", "0");

            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertEquals(e.exception.getCause().getMessage(), "'email_verification_token_lifetime' must be >= 0");

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }
}
