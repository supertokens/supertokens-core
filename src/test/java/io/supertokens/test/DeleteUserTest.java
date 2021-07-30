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

import com.google.gson.JsonObject;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.ResetPasswordInvalidTokenException;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.emailverification.exception.EmailVerificationInvalidTokenException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.session.Session;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;

public class DeleteUserTest {
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

    @Test(expected = UnauthorisedException.class)
    public void testFailSessionRefreshForDeletedUser() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            UserInfo user = EmailPassword.signUp(process.getProcess(), "john.doe@example.com", "password");

            SessionInformationHolder sessionWrapper = Session.createNewSession(process.getProcess(), user.id, new JsonObject(), new JsonObject());
            assert sessionWrapper.refreshToken != null;

            AuthRecipe.deleteUser(process.getProcess(), user.id);

            // when
            Session.refreshSession(process.getProcess(), sessionWrapper.refreshToken.token, null, false);

            // then should throw
        });
    }

    @Test(expected = UnauthorisedException.class)
    public void testFailSessionVerification() throws Exception {
        Utils.setValueInConfig("access_token_blacklisting", "true");

        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            UserInfo user = EmailPassword.signUp(process.getProcess(), "john.doe@example.com", "password");

            SessionInformationHolder sessionWrapper = Session.createNewSession(process.getProcess(), user.id, new JsonObject(), new JsonObject());

            // when
            AuthRecipe.deleteUser(process.getProcess(), user.id);

            Session.getSession(process.getProcess(), sessionWrapper.session.handle);

            // then should throw
        });
    }

    @Test(expected = EmailVerificationInvalidTokenException.class)
    public void testRemoveEmailValidationTokens() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            UserInfo user = EmailPassword.signUp(process.getProcess(), "john.doe@example.com", "password");

            String token = EmailVerification.generateEmailVerificationToken(process.getProcess(), user.id, user.email);
            AuthRecipe.deleteUser(process.getProcess(), user.id);

            // when
            EmailVerification.verifyEmail(process.getProcess(), token);

            // then should throw
        });
    }

    @Test(expected = ResetPasswordInvalidTokenException.class)
    public void testRemovePasswordResetTokens() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            // given
            UserInfo user = EmailPassword.signUp(process.getProcess(), "john.doe@example.com", "password");

            String token = EmailPassword.generatePasswordResetToken(process.getProcess(), user.id);
            AuthRecipe.deleteUser(process.getProcess(), user.id);

            // when
            EmailPassword.resetPassword(process.getProcess(), token, "newpassword");

            // then should throw
        });
    }

    @Test
    public void testNotReturnRecipeUser() throws Exception {
        TestingProcessManager.withProcess(process -> {
            {
                // given
                UserInfo user = EmailPassword.signUp(process.getProcess(), "john.doe@example.com", "password");

                long usersCount = AuthRecipe.getUsersCount(process.getProcess(),
                        new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD});

                assertEquals(1, usersCount);

                // when
                AuthRecipe.deleteUser(process.getProcess(), user.id);

                // then
                usersCount = AuthRecipe.getUsersCount(process.getProcess(),
                        new RECIPE_ID[]{RECIPE_ID.EMAIL_PASSWORD});

                assertEquals(0, usersCount);
            }
            {
                // given
                ThirdParty.SignInUpResponse response = ThirdParty.signInUp(process.getProcess(), "mockThirdPartyId", "johnDoeId", "john.doe@example.com", true);

                long usersCount = AuthRecipe.getUsersCount(process.getProcess(),
                        new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY});

                assertEquals(1, usersCount);

                // when
                AuthRecipe.deleteUser(process.getProcess(), response.user.id);

                // then
                usersCount = AuthRecipe.getUsersCount(process.getProcess(),
                        new RECIPE_ID[]{RECIPE_ID.THIRD_PARTY});

                assertEquals(0, usersCount);
            }
        });
    }
}
