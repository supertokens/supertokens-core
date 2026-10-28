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

import io.supertokens.Main;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.ResetPasswordInvalidTokenException;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.authRecipe.exceptions.UnknownUserIdException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

import static org.junit.Assert.fail;

public class UpdateUsersEmailAndPasswordTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testUpdateInfoWithoutUser() throws Exception {
        TestingProcessManager.withSharedProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            try {
                EmailPassword.updateUsersEmailOrPassword(main, "someUserId", "dave.doe@example.com", null);
                fail();
            } catch (UnknownUserIdException ignored) {
            }
        });
    }

    @Test
    public void testUpdateEmailOnly() throws Exception {
        TestingProcessManager.withSharedProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            AuthRecipeUserInfo userInfo = EmailPassword.signUp(main, "john.doe@example.com", "password");

            // when
            EmailPassword.updateUsersEmailOrPassword(main, userInfo.getSupertokensUserId(), "dave.doe@example.com",
                    null);

            // then
            AuthRecipeUserInfo changedEmailUserInfo = EmailPassword.signIn(main, "dave.doe@example.com", "password");

            Assert.assertEquals(userInfo.getSupertokensUserId(), changedEmailUserInfo.getSupertokensUserId());
            Assert.assertEquals("dave.doe@example.com", changedEmailUserInfo.loginMethods[0].email);
        });
    }

    @Test
    public void testUpdateEmailToAnotherThatAlreadyExists() throws Exception {
        TestingProcessManager.withSharedProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            AuthRecipeUserInfo userInfo = EmailPassword.signUp(main, "john.doe@example.com", "password");
            AuthRecipeUserInfo userInfo2 = EmailPassword.signUp(main, "john.doe1@example.com", "password");

            // when
            try {
                EmailPassword.updateUsersEmailOrPassword(main, userInfo.getSupertokensUserId(),
                        userInfo2.loginMethods[0].email, null);
                Assert.fail();
            } catch (DuplicateEmailException ignored) {
            }

        });
    }

    @Test
    public void testUpdatePasswordOnly() throws Exception {
        TestingProcessManager.withSharedProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            AuthRecipeUserInfo userInfo = EmailPassword.signUp(main, "john.doe@example.com", "password");

            // when
            EmailPassword.updateUsersEmailOrPassword(main, userInfo.getSupertokensUserId(), null, "newPassword");

            // then
            AuthRecipeUserInfo changedEmailUserInfo = EmailPassword.signIn(main, "john.doe@example.com", "newPassword");

            Assert.assertEquals(userInfo.getSupertokensUserId(), changedEmailUserInfo.getSupertokensUserId());
        });
    }

    @Test
    public void testUpdateEmailAndPassword() throws Exception {
        TestingProcessManager.withSharedProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            AuthRecipeUserInfo userInfo = EmailPassword.signUp(main, "john.doe@example.com", "password");

            // when
            EmailPassword.updateUsersEmailOrPassword(main, userInfo.getSupertokensUserId(), "dave.doe@example.com",
                    "newPassword");

            // then
            AuthRecipeUserInfo changedCredentialsUserInfo = EmailPassword.signIn(main, "dave.doe@example.com",
                    "newPassword");

            Assert.assertEquals(userInfo.getSupertokensUserId(), changedCredentialsUserInfo.getSupertokensUserId());
            Assert.assertEquals("dave.doe@example.com", changedCredentialsUserInfo.loginMethods[0].email);
            Assert.assertFalse(changedCredentialsUserInfo.loginMethods[0].verified);
        });
    }

    @Test
    public void testUpdateEmailAndPasswordFromVerifiedToNotVerified() throws Exception {
        TestingProcessManager.withSharedProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            AuthRecipeUserInfo userInfo = EmailPassword.signUp(main, "john.doe@example.com", "password");

            String emailVerificationToken = EmailVerification.generateEmailVerificationToken(main, userInfo.getSupertokensUserId(),
                    userInfo.loginMethods[0].email);
            EmailVerification.verifyEmail(main, emailVerificationToken);
            AuthRecipeUserInfo originalUser = EmailPassword.signIn(main, "john.doe@example.com", "password");
            Assert.assertTrue(originalUser.loginMethods[0].verified);

            // when
            EmailPassword.updateUsersEmailOrPassword(main, userInfo.getSupertokensUserId(), "dave.doe@example.com",
                    "newPassword");

            // then
            AuthRecipeUserInfo changedCredentialsUserInfo = EmailPassword.signIn(main, "dave.doe@example.com",
                    "newPassword");

            Assert.assertEquals(userInfo.getSupertokensUserId(), changedCredentialsUserInfo.getSupertokensUserId());
            Assert.assertEquals("dave.doe@example.com", changedCredentialsUserInfo.loginMethods[0].email);
            Assert.assertFalse(changedCredentialsUserInfo.loginMethods[0].verified);
        });
    }

    @Test
    public void testIssue101_resetTokenInvalidAfterEmailChange() throws Exception {
        TestingProcessManager.withSharedProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            AuthRecipeUserInfo userInfo = EmailPassword.signUp(main, "old.mail@example.com", "password");
            String token = EmailPassword.generatePasswordResetToken(main, userInfo.getSupertokensUserId(),
                    "old.mail@example.com");

            EmailPassword.updateUsersEmailOrPassword(main, userInfo.getSupertokensUserId(), "new.mail@example.com",
                    null);

            try {
                EmailPassword.consumeResetPasswordToken(main, token);
                fail();
            } catch (ResetPasswordInvalidTokenException ignored) {
            }

            // a token for the new email still works
            String newToken = EmailPassword.generatePasswordResetToken(main, userInfo.getSupertokensUserId(),
                    "new.mail@example.com");
            EmailPassword.ConsumeResetPasswordTokenResult result = EmailPassword.consumeResetPasswordToken(main,
                    newToken);
            Assert.assertEquals(userInfo.getSupertokensUserId(), result.userId);
            Assert.assertEquals("new.mail@example.com", result.email);
        });
    }

    @Test
    public void testIssue101_resetTokenInvalidAfterPasswordChange() throws Exception {
        TestingProcessManager.withSharedProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            AuthRecipeUserInfo userInfo = EmailPassword.signUp(main, "pw.change@example.com", "password");
            String token = EmailPassword.generatePasswordResetToken(main, userInfo.getSupertokensUserId(),
                    "pw.change@example.com");

            EmailPassword.updateUsersEmailOrPassword(main, userInfo.getSupertokensUserId(), null, "newPassword");

            try {
                EmailPassword.consumeResetPasswordToken(main, token);
                fail();
            } catch (ResetPasswordInvalidTokenException ignored) {
            }
        });
    }

    @Test
    public void testIssue101_resetTokenRejectedWhenEmailNoLongerBelongsToAccount() throws Exception {
        TestingProcessManager.withSharedProcess(process -> {
            Main main = process.getProcess();

            if (StorageLayer.getStorage(main).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            AuthRecipeUserInfo userInfo = EmailPassword.signUp(main, "current.mail@example.com", "password");
            // a token whose email does not match the login method's current email
            String token = EmailPassword.generatePasswordResetToken(main, userInfo.getSupertokensUserId(),
                    "previous.mail@example.com");

            try {
                EmailPassword.consumeResetPasswordToken(main, token);
                fail();
            } catch (ResetPasswordInvalidTokenException ignored) {
            }

            // the deprecated reset API applies the same check
            String token2 = EmailPassword.generatePasswordResetToken(main, userInfo.getSupertokensUserId(),
                    "previous.mail@example.com");
            try {
                EmailPassword.resetPassword(main, token2, "newPassword");
                fail();
            } catch (ResetPasswordInvalidTokenException ignored) {
            }
            Assert.assertNotNull(EmailPassword.signIn(main, "current.mail@example.com", "password"));
        });
    }
}
