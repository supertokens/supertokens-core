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
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

import static org.junit.Assert.fail;

public class UpdateUsersEmailAndPasswordTest {
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
    public void testUpdateInfoWithoutUser() throws Exception {
        TestingProcessManager.withProcess(process -> {
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
        TestingProcessManager.withProcess(process -> {
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
        TestingProcessManager.withProcess(process -> {
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
        TestingProcessManager.withProcess(process -> {
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
        TestingProcessManager.withProcess(process -> {
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
        });
    }
}
