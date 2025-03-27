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

import io.supertokens.Main;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.emailverification.User;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertFalse;

public class UnverifyEmailTest {
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
    public void testEmailIsUnverified() throws Exception {
        TestingProcessManager.withSharedProcess(process -> {

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            Main main = process.getProcess();

            // given
            String token = EmailVerification.generateEmailVerificationToken(main, "mockUserId", "john.doe@example.com");
            User user = EmailVerification.verifyEmail(main, token);
            EmailVerification.unverifyEmail(main, user.id, user.email);

            // when
            boolean isEmailVerified = EmailVerification.isEmailVerified(main, user.id, user.email);

            // then
            assertFalse(isEmailVerified);
        });
    }
}
