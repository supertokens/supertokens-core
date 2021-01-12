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
import io.supertokens.emailpassword.UserPaginationContainer;
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

import static org.junit.Assert.assertNotNull;

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
 *  - Tests for getUsers function (add 5 users):
 *    - invalid nextPaginationToken should throw IllegalArgumentException
 *    - limit: 2, timeJoinedOrder: ASC. users are returned in ASC order based on timeJoined
 *    - limit: 2, timeJoinedOrder: DESC. users are returned in DESC order based on timeJoined
 *    - limit = 5, nextPaginationToken should not be present in the result
 *    - remove all users from db, response should not have any user and nextPaginationToken should not be present
 *    - limit: 2, timeJoinedOrder: ASC. call the function. from the result use nextPaginationToken to call the function again. from the result use nextPaginationToken to call the function again. the result of the final (3rd) function call should only have one item and nextPaginationToken is not present in the result
 *    - limit: 2, timeJoinedOrder: DESC. call the function. from the result use nextPaginationToken to call the function again. make sure the users obtained are in descending order based on the timeJoined. from the result use nextPaginationToken to call the function again. the result of the final (3rd) function call should only have one item and nextPaginationToken is not present in the result
 *  - Tests for getUsersCount function:
 *    - no users, the function should return 0
 *    - add 5 users, the function should return 5
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

    @Test
    public void getUsers() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        EmailPassword.signUp(process.getProcess(), "test0@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "test1@example.com", "password1");
        EmailPassword.signUp(process.getProcess(), "test2@example.com", "password2");
        EmailPassword.signUp(process.getProcess(), "test3@example.com", "password3");
        EmailPassword.signUp(process.getProcess(), "test4@example.com", "password4");

        {
            UserPaginationContainer users = EmailPassword.getUsers(process.getProcess(), null, 10, "ASC");
            assert (users.users.length == 5);
            assert (users.nextPaginationToken == null);
        }

        {
            UserPaginationContainer users = EmailPassword.getUsers(process.getProcess(), null, 1, "ASC");
            assert (users.users.length == 1);
            assertNotNull(users.nextPaginationToken);
            assert (users.users[0].email.equals("test0@example.com"));
            users = EmailPassword.getUsers(process.getProcess(), users.nextPaginationToken, 1, "ASC");
            assert (users.users.length == 1);
            assertNotNull(users.nextPaginationToken);
            assert (users.users[0].email.equals("test1@example.com"));
        }

        {
            UserPaginationContainer users = EmailPassword.getUsers(process.getProcess(), null, 1, "DESC");
            assert (users.users.length == 1);
            assertNotNull(users.nextPaginationToken);
            assert (users.users[0].email.equals("test4@example.com"));
            users = EmailPassword.getUsers(process.getProcess(), users.nextPaginationToken, 1, "DESC");
            assert (users.users.length == 1);
            assertNotNull(users.nextPaginationToken);
            assert (users.users[0].email.equals("test3@example.com"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void getUsersCount() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        long count = EmailPassword.getUsersCount(process.getProcess());
        assert (count == 0);

        EmailPassword.signUp(process.getProcess(), "test0@example.com", "password0");
        EmailPassword.signUp(process.getProcess(), "test1@example.com", "password1");
        EmailPassword.signUp(process.getProcess(), "test2@example.com", "password2");
        EmailPassword.signUp(process.getProcess(), "test3@example.com", "password3");
        EmailPassword.signUp(process.getProcess(), "test4@example.com", "password4");

        count = EmailPassword.getUsersCount(process.getProcess());
        assert (count == 5);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
