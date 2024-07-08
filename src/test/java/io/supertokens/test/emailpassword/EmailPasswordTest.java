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

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.config.Config;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.PasswordHashing;
import io.supertokens.emailpassword.exceptions.EmailChangeNotAllowedException;
import io.supertokens.emailpassword.exceptions.ResetPasswordInvalidTokenException;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicatePasswordResetTokenException;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.ThirdParty;
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
                StorageUtils.getEmailPasswordStorage(StorageLayer.getStorage(process.getProcess()));
                throw new Exception("Should not come here");
            } catch (UnsupportedOperationException e) {
            }
        } else {
            StorageUtils.getEmailPasswordStorage(StorageLayer.getStorage(process.getProcess()));
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

        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.getProcess(), "random@gmail.com", "validPass123");
        assertEquals(userInfo.loginMethods[0].email, "random@gmail.com");
        assertNotNull(userInfo.getSupertokensUserId());

        for (int i = 0; i < 100; i++) {
            String generatedResetToken = EmailPassword.generatePasswordResetTokenBeforeCdi4_0(process.getProcess(),
                    userInfo.getSupertokensUserId());

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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "random@gmail.com", "validPass123");

        AuthRecipeUserInfo userInfo = ((AuthRecipeStorage) StorageLayer.getStorage(process.getProcess()))
                .listPrimaryUsersByEmail(new TenantIdentifier(null, null, null), user.loginMethods[0].email)[0];
        assertNotEquals(userInfo.loginMethods[0].passwordHash, "validPass123");
        assertTrue(PasswordHashing.getInstance(process.getProcess()).verifyPasswordWithHash("validPass123",
                userInfo.loginMethods[0].passwordHash));

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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "random@gmail.com", "validPass123");

        String resetToken = EmailPassword.generatePasswordResetTokenBeforeCdi4_0(process.getProcess(),
                user.getSupertokensUserId());
        PasswordResetTokenInfo resetTokenInfo = ((EmailPasswordSQLStorage) StorageLayer.getStorage(
                process.getProcess()))
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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "random@gmail.com", "validPass123");

        String resetToken = EmailPassword.generatePasswordResetTokenBeforeCdi4_0(process.getProcess(),
                user.getSupertokensUserId());

        EmailPassword.resetPassword(process.getProcess(), resetToken, "newValidPass123");

        AuthRecipeUserInfo userInfo = ((AuthRecipeStorage) StorageLayer.getStorage(process.getProcess()))
                .listPrimaryUsersByEmail(new TenantIdentifier(null, null, null), user.loginMethods[0].email)[0];
        assertNotEquals(userInfo.loginMethods[0].passwordHash, "newValidPass123");

        assertTrue(PasswordHashing.getInstance(process.getProcess()).verifyPasswordWithHash("newValidPass123",
                userInfo.loginMethods[0].passwordHash));

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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");

        String tok = EmailPassword.generatePasswordResetTokenBeforeCdi4_0(process.getProcess(),
                user.getSupertokensUserId());

        assert (((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null),
                        user.getSupertokensUserId()).length == 1);

        Thread.sleep(20);

        try {
            EmailPassword.resetPassword(process.getProcess(), tok, "newPassword");
            assert (false);
        } catch (ResetPasswordInvalidTokenException ignored) {

        }

        assert (((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null),
                        user.getSupertokensUserId()).length == 0);

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

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");

        EmailPassword.generatePasswordResetTokenBeforeCdi4_0(process.getProcess(), user.getSupertokensUserId());
        String tok = EmailPassword.generatePasswordResetTokenBeforeCdi4_0(process.getProcess(),
                user.getSupertokensUserId());
        EmailPassword.generatePasswordResetTokenBeforeCdi4_0(process.getProcess(), user.getSupertokensUserId());

        PasswordResetTokenInfo[] tokens = ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null), user.getSupertokensUserId());

        assert (tokens.length == 3);

        EmailPassword.resetPassword(process.getProcess(), tok, "newPassword");

        tokens = ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null), user.getSupertokensUserId());
        assert (tokens.length == 0);

        try {
            EmailPassword.signIn(process.getProcess(), "test1@example.com", "password");
            assert (false);
        } catch (WrongCredentialsException ignored) {

        }

        AuthRecipeUserInfo user1 = EmailPassword.signIn(process.getProcess(), "test1@example.com", "newPassword");

        assertEquals(user1.loginMethods[0].email, user.loginMethods[0].email);

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
        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");

        ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .addPasswordResetToken(new AppIdentifier(null, null), new PasswordResetTokenInfo(
                        user.getSupertokensUserId(), "token",
                        System.currentTimeMillis() +
                                Config.getConfig(process.getProcess()).getPasswordResetTokenLifetime(), "email"));

        try {
            ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                    .addPasswordResetToken(new AppIdentifier(null, null),
                            new PasswordResetTokenInfo(user.getSupertokensUserId(), "token", System.currentTimeMillis()
                                    + Config.getConfig(process.getProcess()).getPasswordResetTokenLifetime(), "email"));
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
            EmailPassword.generatePasswordResetTokenBeforeCdi4_0(process.getProcess(),
                    "8ed86166-bfd8-4234-9dfe-abca9606dbd5");
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

        AuthRecipeUserInfo userSignUp = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");

        AuthRecipeUserInfo user = EmailPassword.signIn(process.getProcess(), "test@example.com", "password");

        assert (user.loginMethods[0].email.equals("test@example.com"));

        assert (userSignUp.getSupertokensUserId().equals(user.getSupertokensUserId()));

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

    @Test
    public void testGeneratingResetPasswordTokenForNonEPUserNonPrimary() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.getProcess(), "google",
                "user-google",
                "test@example.com");

        try {
            EmailPassword.generatePasswordResetTokenBeforeCdi4_0(process.main,
                    signInUpResponse.user.getSupertokensUserId());
            assert false;
        } catch (UnknownUserIdException ignored) {

        }

        String token = EmailPassword.generatePasswordResetToken(process.main,
                signInUpResponse.user.getSupertokensUserId(),
                "test@example.com");

        EmailPassword.ConsumeResetPasswordTokenResult res = EmailPassword.consumeResetPasswordToken(process.main,
                token);
        assert (res.email.equals("test@example.com"));
        assert (res.userId.equals(signInUpResponse.user.getSupertokensUserId()));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGeneratingResetPasswordTokenForNonEPUserPrimary() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.getProcess(), "google",
                "user-google",
                "test@example.com");

        AuthRecipe.createPrimaryUser(process.main, signInUpResponse.user.getSupertokensUserId());

        String token = EmailPassword.generatePasswordResetToken(process.main,
                signInUpResponse.user.getSupertokensUserId(),
                "test@example.com");

        EmailPassword.ConsumeResetPasswordTokenResult res = EmailPassword.consumeResetPasswordToken(process.main,
                token);
        assert (res.email.equals("test@example.com"));
        assert (res.userId.equals(signInUpResponse.user.getSupertokensUserId()));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGeneratingResetPasswordTokenForNonEPUserPrimaryButDeletedWithOtherLinked() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.getProcess(), "google",
                "user-google",
                "test@example.com");

        ThirdParty.SignInUpResponse signInUpResponse2 = ThirdParty.signInUp(process.getProcess(), "fb",
                "user-fb",
                "test2@example.com");

        AuthRecipe.createPrimaryUser(process.main, signInUpResponse.user.getSupertokensUserId());
        AuthRecipe.linkAccounts(process.main, signInUpResponse2.user.getSupertokensUserId(),
                signInUpResponse.user.getSupertokensUserId());
        assert (AuthRecipe.unlinkAccounts(process.main, signInUpResponse.user.getSupertokensUserId()));

        String token = EmailPassword.generatePasswordResetToken(process.main,
                signInUpResponse.user.getSupertokensUserId(),
                "test@example.com");

        EmailPassword.ConsumeResetPasswordTokenResult res = EmailPassword.consumeResetPasswordToken(process.main,
                token);
        assert (res.email.equals("test@example.com"));
        assert (res.userId.equals(signInUpResponse.user.getSupertokensUserId()));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void deletionOfTpUserDeletesPasswordResetToken() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        ThirdParty.SignInUpResponse signInUpResponse = ThirdParty.signInUp(process.getProcess(), "google",
                "user-google",
                "test@example.com");


        String token = EmailPassword.generatePasswordResetToken(process.main,
                signInUpResponse.user.getSupertokensUserId(),
                "test@example.com");
        token = io.supertokens.utils.Utils.hashSHA256(token);

        assertNotNull(((EmailPasswordSQLStorage) StorageLayer.getStorage(process.main)).getPasswordResetTokenInfo(
                new AppIdentifier(null, null), token));

        AuthRecipe.deleteUser(process.main, signInUpResponse.user.getSupertokensUserId());

        assertNull(((EmailPasswordSQLStorage) StorageLayer.getStorage(process.main)).getPasswordResetTokenInfo(
                new AppIdentifier(null, null), token));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void passwordResetTokenExpiredCheckWithConsumeCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        Utils.setValueInConfig("password_reset_token_lifetime", "10");
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");

        String tok = EmailPassword.generatePasswordResetTokenBeforeCdi4_0(process.getProcess(),
                user.getSupertokensUserId());

        assert (((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null),
                        user.getSupertokensUserId()).length == 1);

        Thread.sleep(20);

        try {
            EmailPassword.consumeResetPasswordToken(process.getProcess(), tok);
            assert (false);
        } catch (ResetPasswordInvalidTokenException ignored) {

        }

        assert (((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null),
                        user.getSupertokensUserId()).length == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void multiplePasswordResetTokensPerUserAndThenVerifyWithSigninWithConsumeCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");

        EmailPassword.generatePasswordResetTokenBeforeCdi4_0(process.getProcess(), user.getSupertokensUserId());
        String tok = EmailPassword.generatePasswordResetTokenBeforeCdi4_0(process.getProcess(),
                user.getSupertokensUserId());
        EmailPassword.generatePasswordResetTokenBeforeCdi4_0(process.getProcess(), user.getSupertokensUserId());

        PasswordResetTokenInfo[] tokens = ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null), user.getSupertokensUserId());

        assert (tokens.length == 3);

        EmailPassword.consumeResetPasswordToken(process.getProcess(), tok);

        tokens = ((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null), user.getSupertokensUserId());
        assert (tokens.length == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void wrongPasswordResetTokenWithConsumeCode() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        try {
            EmailPassword.consumeResetPasswordToken(process.getProcess(), "token");
            assert (false);
        } catch (ResetPasswordInvalidTokenException ignored) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void consumeCodeCorrectlySetsTheUserEmailForOlderTokens() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test1@example.com", "password");

        String tok = EmailPassword.generatePasswordResetTokenBeforeCdi4_0WithoutAddingEmail(process.getProcess(),
                user.getSupertokensUserId());

        assert (((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null),
                        user.getSupertokensUserId()).length == 1);
        assert (((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null),
                        user.getSupertokensUserId())[0].email == null);

        EmailPassword.ConsumeResetPasswordTokenResult result = EmailPassword.consumeResetPasswordToken(
                process.getProcess(), tok);
        assert (result.email.equals("test1@example.com"));
        assert (result.userId.equals(user.getSupertokensUserId()));

        assert (((EmailPasswordSQLStorage) StorageLayer.getStorage(process.getProcess()))
                .getAllPasswordResetTokenInfoForUser(new AppIdentifier(null, null),
                        user.getSupertokensUserId()).length == 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }


    @Test
    public void updateEmailFailsIfEmailUsedByOtherPrimaryUserInSameTenant() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user0 = EmailPassword.signUp(process.getProcess(), "someemail1@gmail.com", "somePass");
        AuthRecipe.createPrimaryUser(process.main, user0.getSupertokensUserId());

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "someemail@gmail.com", "somePass");
        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        try {
            EmailPassword.updateUsersEmailOrPassword(process.main, user.getSupertokensUserId(), "someemail1@gmail.com",
                    null);
            assert (false);
        } catch (EmailChangeNotAllowedException ignored) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void updateEmailSucceedsIfEmailUsedByOtherPrimaryUserInDifferentTenantWhichThisUserIsNotAPartOf()
            throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(process.main, new TenantIdentifier(null, null, null),
                new TenantConfig(new TenantIdentifier(null, null, "t1"), new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[0]), new PasswordlessConfig(true),
                        null, null,
                        new JsonObject()));

        Storage storage = (StorageLayer.getStorage(process.main));
        AuthRecipeUserInfo user0 = EmailPassword.signUp(
                new TenantIdentifier(null, null, "t1"), storage, process.getProcess(),
                "someemail1@gmail.com",
                "pass1234");

        AuthRecipe.createPrimaryUser(process.main, user0.getSupertokensUserId());

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "someemail@gmail.com", "somePass");
        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        EmailPassword.updateUsersEmailOrPassword(process.main, user.getSupertokensUserId(), "someemail1@gmail.com",
                null);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void updateEmailFailsIfEmailUsedByOtherPrimaryUserInDifferentTenant()
            throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(process.main, new TenantIdentifier(null, null, null),
                new TenantConfig(new TenantIdentifier(null, null, "t1"), new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, new ThirdPartyConfig.Provider[0]), new PasswordlessConfig(true),
                        null, null,
                        new JsonObject()));

        Storage storage = (StorageLayer.getStorage(process.main));
        AuthRecipeUserInfo user0 = EmailPassword.signUp(
                new TenantIdentifier(null, null, "t1"), storage, process.getProcess(),
                "someemail1@gmail.com",
                "pass1234");

        AuthRecipe.createPrimaryUser(process.main, user0.getSupertokensUserId());

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "someemail@gmail.com", "somePass");
        AuthRecipe.createPrimaryUser(process.main, user.getSupertokensUserId());

        Multitenancy.addUserIdToTenant(process.main,
                new TenantIdentifier(null, null, "t1"), storage, user.getSupertokensUserId());

        try {
            EmailPassword.updateUsersEmailOrPassword(process.main, user.getSupertokensUserId(), "someemail1@gmail.com",
                    null);
            assert (false);
        } catch (EmailChangeNotAllowedException ignored) {

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
