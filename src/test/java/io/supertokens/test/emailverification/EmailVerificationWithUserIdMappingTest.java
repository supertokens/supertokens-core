/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.emailverification.exception.EmailAlreadyVerifiedException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.useridmapping.UserIdMapping;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class EmailVerificationWithUserIdMappingTest {
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
    public void testUserIdMappingCreationAfterEmailVerificationForEmailPasswordUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        String token = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                user.getSupertokensUserId(), "test@example.com");
        EmailVerification.verifyEmail(process.getProcess(), token);

        // create mapping
        // should be allowed although used in non-auth recipe
        UserIdMapping.createUserIdMapping(process.getProcess(), user.getSupertokensUserId(), "euid", null, false, true);

        // email for the user should remain verified
        AuthRecipeUserInfo userInfo = AuthRecipe.getUserById(process.getProcess(), user.getSupertokensUserId());
        assertTrue(userInfo.loginMethods[0].verified);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUserIdMappingCreationAfterEmailVerificationForThirdPartyUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = ThirdParty.signInUp(process.getProcess(), "google", "googleid1", "test@example.com",
                true).user;
        try {
            String token = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                    user.getSupertokensUserId(), "test@example.com");
            EmailVerification.verifyEmail(process.getProcess(), token);
            fail();
        } catch (EmailAlreadyVerifiedException e) {
            // email should already be verified
        }

        // create mapping
        // should be allowed although used in non-auth recipe
        UserIdMapping.createUserIdMapping(process.getProcess(), user.getSupertokensUserId(), "euid", null, false, true);

        // email for the user should remain verified
        AuthRecipeUserInfo userInfo = AuthRecipe.getUserById(process.getProcess(), user.getSupertokensUserId());
        assertTrue(userInfo.loginMethods[0].verified);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUserIdMappingCreationAfterEmailVerificationForPasswordlessUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Passwordless.CreateCodeResponse code = Passwordless.createCode(process.getProcess(), "test@example.com", null,
                null, null);
        AuthRecipeUserInfo user = Passwordless.consumeCode(process.getProcess(), code.deviceId, code.deviceIdHash,
                code.userInputCode, null, true).user;

        try {
            String token = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                    user.getSupertokensUserId(), "test@example.com");
            EmailVerification.verifyEmail(process.getProcess(), token);
            fail();
        } catch (EmailAlreadyVerifiedException e) {
            // email should already be verified
        }

        // create mapping
        // should be allowed although used in non-auth recipe
        UserIdMapping.createUserIdMapping(process.getProcess(), user.getSupertokensUserId(), "euid", null, false, true);

        // email for the user should remain verified
        AuthRecipeUserInfo userInfo = AuthRecipe.getUserById(process.getProcess(), user.getSupertokensUserId());
        assertTrue(userInfo.loginMethods[0].verified);

        assertTrue(EmailVerification.isEmailVerified(process.getProcess(), "euid", "test@example.com"));
        assertFalse(EmailVerification.isEmailVerified(process.getProcess(), user.getSupertokensUserId(),
                "test@example.com"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUserIdMappingCreationAfterEmailVerificationForPasswordlessUserWithOlderCDIBehaviour()
            throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Passwordless.CreateCodeResponse code = Passwordless.createCode(process.getProcess(), "test@example.com", null,
                null, null);
        AuthRecipeUserInfo user = Passwordless.consumeCode(process.getProcess(), code.deviceId, code.deviceIdHash,
                code.userInputCode, null, true).user;

        // create mapping
        // this should not be allowed
        try {
            UserIdMapping.createUserIdMapping(process.getProcess(), user.getSupertokensUserId(), "euid", null, false,
                    false);
            fail();
        } catch (Exception e) {
            assert e.getMessage().contains("UserId is already in use in EmailVerification recipe");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testEmailVerificationWithTokenAfterWithUserIdMapping() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        AuthRecipeUserInfo user = EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        String token = EmailVerification.generateEmailVerificationToken(process.getProcess(),
                user.getSupertokensUserId(), "test@example.com");
        // create mapping before verifying token
        // should be allowed although used in non-auth recipe
        UserIdMapping.createUserIdMapping(process.getProcess(), user.getSupertokensUserId(), "euid", null, false, true);

        EmailVerification.verifyEmail(process.getProcess(), token);

        // email for the user should be verified
        AuthRecipeUserInfo userInfo = AuthRecipe.getUserById(process.getProcess(), user.getSupertokensUserId());
        assertTrue(userInfo.loginMethods[0].verified);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
