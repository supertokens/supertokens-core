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

import io.supertokens.emailverification.EmailVerification;
import io.supertokens.emailverification.exception.EmailVerificationInvalidTokenException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

public class RevokeTokenTest {
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
    public void testTokenCannotBeUsedToVerifyIfRevoked() throws Exception {
        TestingProcessManager.withSharedProcess(process -> {

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            String token = EmailVerification.generateEmailVerificationToken(process.getProcess(), "mockUserId",
                    "john.doe@example.com");

            String token2 = EmailVerification.generateEmailVerificationToken(process.getProcess(), "mockUserId",
                    "john.doe@example.com");

            EmailVerification.revokeAllTokens(process.getProcess(), "mockUserId", "john.doe@example.com");

            try {
                EmailVerification.verifyEmail(process.getProcess(), token);
                Assert.fail();
            } catch (EmailVerificationInvalidTokenException ignored) {
            }

            try {
                EmailVerification.verifyEmail(process.getProcess(), token2);
                Assert.fail();
            } catch (EmailVerificationInvalidTokenException ignored) {
            }
        });
    }

    @Test
    public void testRevokingNonExistingTokenShouldPass() throws Exception {
        TestingProcessManager.withSharedProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }
            EmailVerification.revokeAllTokens(process.getProcess(), "mockUserId", "john.doe@example.com");
        });
    }
}
