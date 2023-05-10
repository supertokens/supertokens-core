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

package io.supertokens.test.emailpassword;

import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.PasswordHashing;
import io.supertokens.emailpassword.exceptions.ResetPasswordInvalidTokenException;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicatePasswordResetTokenException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
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
 *  - (later) Test that if there are two transactions running with the same password reset token, only one of them
 *  succeed and the other throws ResetPasswordInvalidTokenException, and that there are no more tokens left for that
 *  user.
 * */

public class EmailPasswordTest {
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

    // Check that StorageLayer.getEmailPasswordStorageLayer throws an exception if the storage type is not SQL (and
    // vice versa)
    // Failure condition: If the StorageLayer type is NOSQL and if the EmailPasswordStorageLayer is called and it
    // does not throw an Error, the test will fail
    @Test
    public void testStorageLayerGetMailPasswordStorageLayerThrowsExceptionIfTypeIsNotSQL() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            try {
                new TenantIdentifierWithStorage(null, null, null, StorageLayer.getStorage(process.getProcess())).getEmailPasswordStorage();
                throw new Exception("Should not come here");
            } catch (UnsupportedOperationException e) {
            }
        } else {
            new TenantIdentifierWithStorage(null, null, null, StorageLayer.getStorage(process.getProcess())).getEmailPasswordStorage();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Test normaliseEmail function
    @Test
    public void testTheNormaliseEmailFunction() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        String normaliseEmail = io.supertokens.utils.Utils.normaliseEmail("RaNdOm@gmail.com");
        assertEquals(normaliseEmail, "random@gmail.com");

        normaliseEmail = io.supertokens.utils.Utils.normaliseEmail("RaNdOm@hotmail.com");
        assertEquals(normaliseEmail, "random@hotmail.com");

        normaliseEmail = io.supertokens.utils.Utils.normaliseEmail("RaNdOm@googlemail.com");
        assertEquals(normaliseEmail, "random@googlemail.com");

        normaliseEmail = io.supertokens.utils.Utils.normaliseEmail("RaNdOm@outlook.com");
        assertEquals(normaliseEmail, "random@outlook.com");

        normaliseEmail = io.supertokens.utils.Utils.normaliseEmail("RaNdOm@yahoo.com");
        assertEquals(normaliseEmail, "random@yahoo.com");

        normaliseEmail = io.supertokens.utils.Utils.normaliseEmail("RaNdOm@icloud.com");
        assertEquals(normaliseEmail, "random@icloud.com");

        normaliseEmail = io.supertokens.utils.Utils.normaliseEmail("RaNdOm@random.com");
        assertEquals(normaliseEmail, "random@random.com");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Test that the reset password token length is 128 and has URL safe characters (generate a token 100 times and
    // * for each, check the above).
    // Failure condition: the test will fail if the generatePasswordResetToken function returns a token whose length
    // is not 128 characters long and is not URL sage
    @Test
    public void testResetPasswordToken() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserInfo userInfo = EmailPassword.signUp(process.getProcess(), "random@gmail.com", "validPass123");
        assertEquals(userInfo.email, "random@gmail.com");
        assertNotNull(userInfo.id);

        for (int i = 0; i < 100; i++) {
            String generatedResetToken = EmailPassword.generatePasswordResetToken(process.getProcess(), userInfo.id);

            assertEquals(generatedResetToken.length(), 128);
            assertFalse(generatedResetToken.contains("+"));
            assertFalse(generatedResetToken.contains("="));
            assertFalse(generatedResetToken.contains("/"));
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // After sign up, check that the password is hashed in the db
    // Failure condition: If the password data returned from the database is not hashed or the hash value does not
    // match the check, the test will fail
    @Test
    public void testThatAfterSignUpThePasswordIsHashedAndStoredInTheDatabase() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserInfo user = EmailPassword.signUp(process.getProcess(), "random@gmail.com", "validPass123");

        UserInfo userInfo = ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getUserInfoUsingEmail(new TenantIdentifier(null, null, null), user.email);
        assertNotEquals(userInfo.passwordHash, "validPass123");
        assertTrue(PasswordHashing.getInstance(process.getProcess()).verifyPasswordWithHash("validPass123",
                userInfo.passwordHash));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // After reset password generate token, check that the token is hashed in the db
    // Failure condition: If the token returned from the database is not hashed or the hash value does not
    // match the check, the test will fail
    @Test
    public void testThatAfterResetPasswordGenerateTokenTheTokenIsHashedInTheDatabase() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserInfo user = EmailPassword.signUp(process.getProcess(), "random@gmail.com", "validPass123");

        String resetToken = EmailPassword.generatePasswordResetToken(process.getProcess(), user.id);
        PasswordResetTokenInfo resetTokenInfo = ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getPasswordResetTokenInfo(new AppIdentifier(null, null),
                        io.supertokens.utils.Utils.hashSHA256(resetToken));

        assertNotEquals(resetToken, resetTokenInfo.token);
        assertEquals(io.supertokens.utils.Utils.hashSHA256(resetToken), resetTokenInfo.token);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // After reset password completed, check that the password is hashed in the db
    // Failure condition: If the password data returned from the database is not hashed or the hash value does not
    // match the check, the test will fail
    @Test
    public void testThatAfterResetPasswordIsCompletedThePasswordIsHashedInTheDatabase() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserInfo user = EmailPassword.signUp(process.getProcess(), "random@gmail.com", "validPass123");

        String resetToken = EmailPassword.generatePasswordResetToken(process.getProcess(), user.id);

        EmailPassword.resetPassword(process.getProcess(), resetToken, "newValidPass123");

        UserInfo userInfo = ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getUserInfoUsingEmail(new TenantIdentifier(null, null, null), user.email);
        assertNotEquals(userInfo.passwordHash, "newValidPass123");

        assertTrue(PasswordHashing.getInstance(process.getProcess()).verifyPasswordWithHash("newValidPass123",
                userInfo.passwordHash));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void passwordResetTokenExpiredCheck() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        Utils.setValueInConfig("password_reset_token_lifetime", "10");
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserInfo user = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");

        String tok = EmailPassword.generatePasswordResetToken(process.getProcess(), user.id);

        assert (((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null), user.id).length == 1);

        Thread.sleep(20);

        try {
            EmailPassword.resetPassword(process.getProcess(), tok, "newPassword");
            assert (false);
        } catch (ResetPasswordInvalidTokenException ignored) {

        }

        assert (((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null), user.id).length == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void multiplePasswordResetTokensPerUserAndThenVerifyWithSignin() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserInfo user = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");

        EmailPassword.generatePasswordResetToken(process.getProcess(), user.id);
        String tok = EmailPassword.generatePasswordResetToken(process.getProcess(), user.id);
        EmailPassword.generatePasswordResetToken(process.getProcess(), user.id);

        PasswordResetTokenInfo[] tokens = ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null), user.id);

        assert (tokens.length == 3);

        EmailPassword.resetPassword(process.getProcess(), tok, "newPassword");

        tokens = ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null), user.id);
        assert (tokens.length == 0);

        try {
            EmailPassword.signIn(process.getProcess(), "test1@example.com", "password");
            assert (false);
        } catch (WrongCredentialsException ignored) {

        }

        UserInfo user1 = EmailPassword.signIn(process.getProcess(), "test1@example.com", "newPassword");

        assertEquals(user1.email, user.email);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void zeroPasswordTokens() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        PasswordResetTokenInfo[] tokens = ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null),
                        "8ed86166-bfd8-4234-9dfe-abca9606dbd5");

        assert (tokens.length == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void wrongPasswordResetToken() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            EmailPassword.resetPassword(process.getProcess(), "token", "newPassword");
            assert (false);
        } catch (ResetPasswordInvalidTokenException ignored) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void clashingPassowordResetToken() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // we add a user first.
        UserInfo user = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");

        ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .addPasswordResetToken(new AppIdentifier(null, null), new PasswordResetTokenInfo(
                        user.id, "token",
                        System.currentTimeMillis() +
                                Config.getConfig(process.getProcess()).getPasswordResetTokenLifetime()));

        try {
            ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                    .addPasswordResetToken(new AppIdentifier(null, null),
                            new PasswordResetTokenInfo(user.id, "token", System.currentTimeMillis()
                                    + Config.getConfig(process.getProcess()).getPasswordResetTokenLifetime()));
            assert (false);
        } catch (DuplicatePasswordResetTokenException ignored) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void unknownUserIdWhileGeneratingPasswordResetToken() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            EmailPassword.generatePasswordResetToken(process.getProcess(), "8ed86166-bfd8-4234-9dfe-abca9606dbd5");
            assert (false);
        } catch (UnknownUserIdException ignored) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void clashingUserIdDuringSignUp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .signUp(new TenantIdentifier(null, null, null),
                        "8ed86166-bfd8-4234-9dfe-abca9606dbd5", "test@example.com", "password",
                        System.currentTimeMillis());

        try {
            ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                    .signUp(new TenantIdentifier(null, null, null),
                            "8ed86166-bfd8-4234-9dfe-abca9606dbd5", "test1@example.com", "password",
                                    System.currentTimeMillis());
            assert (false);
        } catch (DuplicateUserIdException ignored) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void clashingEmailIdDuringSignUp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        EmailPassword.signUp(process.getProcess(), "test@example.com", "password");

        try {
            EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
            assert (false);
        } catch (DuplicateEmailException ignored) {

        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void clashingEmailAndUserIdDuringSignUp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .signUp(new TenantIdentifier(null, null, null),
                        "8ed86166-bfd8-4234-9dfe-abca9606dbd5", "test@example.com", "password",
                        System.currentTimeMillis());

        try {
            ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                    .signUp(new TenantIdentifier(null, null, null),
                            "8ed86166-bfd8-4234-9dfe-abca9606dbd5", "test@example.com", "password",
                                    System.currentTimeMillis());
            assert (false);
        } catch (DuplicateUserIdException ignored) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void signUpAndThenSignIn() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserInfo userSignUp = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");

        UserInfo user = EmailPassword.signIn(process.getProcess(), "test@example.com", "password");

        assert (user.email.equals("test@example.com"));

        assert (userSignUp.id.equals(user.id));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void signInWrongEmailWrongPassword() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            EmailPassword.signIn(process.getProcess(), "test@example.com", "password");
            assert (false);
        } catch (WrongCredentialsException ignored) {

        }

        EmailPassword.signUp(process.getProcess(), "test@example.com", "password");

        try {
            EmailPassword.signIn(process.getProcess(), "test@example.com", "password1");
            assert (false);
        } catch (WrongCredentialsException ignored) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void changePasswordResetLifetimeTest() throws Exception {
        {
            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            assertEquals(EmailPassword.getPasswordResetTokenLifetimeForTests(process.getProcess()), 3600 * 1000);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("password_reset_token_lifetime", "100");

            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            assertEquals(EmailPassword.getPasswordResetTokenLifetimeForTests(process.getProcess()), 100);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("password_reset_token_lifetime", "0");

            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertEquals(e.exception.getCause().getMessage(), "'password_reset_token_lifetime' must be >= 0");

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }
}
