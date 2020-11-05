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
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicatePasswordResetTokenException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertNotNull;

/*
 * TODO:
 *  - Check that StorageLayer.getEmailPasswordStorageLayer throws as exception if the storage type is not SQL (and
 *   vice versa)
 *  - Test normaliseEmail function
 *  - Test UpdatableBCrypt class
 *     - test time taken for hash
 *     - test hashing and verifying with short passwords and > 100 char password
 *  - Test that password reset token is generated, and can be verified from the db
 *  - Test that if 5 password reset tokens are created, there exist 5 against that user, after using any one, 0 exist
 *  for that user.
 *  - Test that the reset password token length is 128 and has URL safe characters (generate a token 100 times and
 *  for each, check the above).
 *
 * TODO:
 *  - Test that a wrong password reset token throws the right error.
 *  - Test that if there are two transactions running with the same password reset token, only one of them succeed
 *  and the other throws ResetPasswordInvalidTokenException, and that there are no more tokens left for that user.
 *  - Check that an expired password reset token throws the right error and that at the end, there are no more tokens
 *  for that user left.
 *  - Test that resetting a user's password actually reflects in the db
 *
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

    @Test
    public void clashingPassowordResetToken() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorageLayer(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // we add a user first.
        StorageLayer.getEmailPasswordStorageLayer(process.getProcess())
                .signUp(new UserInfo("8ed86166-bfd8-4234-9dfe-abca9606dbd5",
                        "test1@example.com", "passwordHash", System.currentTimeMillis()));

        StorageLayer.getEmailPasswordStorageLayer(process.getProcess())
                .addPasswordResetToken(new PasswordResetTokenInfo(
                        "8ed86166-bfd8-4234-9dfe-abca9606dbd5", "token",
                        System.currentTimeMillis() + EmailPassword.PASSWORD_RESET_TOKEN_LIFETIME_MS));

        try {
            StorageLayer.getEmailPasswordStorageLayer(process.getProcess())
                    .addPasswordResetToken(new PasswordResetTokenInfo(
                            "8ed86166-bfd8-4234-9dfe-abca9606dbd5", "token",
                            System.currentTimeMillis() + EmailPassword.PASSWORD_RESET_TOKEN_LIFETIME_MS));
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

        if (StorageLayer.getStorageLayer(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
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
    public void multiplePasswordResetTokensPerUserIsAllowed() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorageLayer(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        StorageLayer.getEmailPasswordStorageLayer(process.getProcess())
                .signUp(new UserInfo("8ed86166-bfd8-4234-9dfe-abca9606dbd5",
                        "test1@example.com", "passwordHash", System.currentTimeMillis()));
        EmailPassword.generatePasswordResetToken(process.getProcess(), "8ed86166-bfd8-4234-9dfe-abca9606dbd5");
        EmailPassword.generatePasswordResetToken(process.getProcess(), "8ed86166-bfd8-4234-9dfe-abca9606dbd5");
        EmailPassword.generatePasswordResetToken(process.getProcess(), "8ed86166-bfd8-4234-9dfe-abca9606dbd5");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void clashingUserIdDuringSignUp() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorageLayer(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        StorageLayer.getEmailPasswordStorageLayer(process.getProcess())
                .signUp(new UserInfo("8ed86166-bfd8-4234-9dfe-abca9606dbd5",
                        "test@example.com", "passwordHash", System.currentTimeMillis()));

        try {
            StorageLayer.getEmailPasswordStorageLayer(process.getProcess())
                    .signUp(new UserInfo("8ed86166-bfd8-4234-9dfe-abca9606dbd5",
                            "test1@example.com", "passwordHash", System.currentTimeMillis()));
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

        if (StorageLayer.getStorageLayer(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
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

        if (StorageLayer.getStorageLayer(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        StorageLayer.getEmailPasswordStorageLayer(process.getProcess())
                .signUp(new UserInfo("8ed86166-bfd8-4234-9dfe-abca9606dbd5",
                        "test@example.com", "passwordHash", System.currentTimeMillis()));

        try {
            StorageLayer.getEmailPasswordStorageLayer(process.getProcess())
                    .signUp(new UserInfo("8ed86166-bfd8-4234-9dfe-abca9606dbd5",
                            "test@example.com", "passwordHash", System.currentTimeMillis()));
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

        if (StorageLayer.getStorageLayer(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        User userSignUp = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");

        User user = EmailPassword.signIn(process.getProcess(), "test@example.com", "password");

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

        if (StorageLayer.getStorageLayer(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
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

}
