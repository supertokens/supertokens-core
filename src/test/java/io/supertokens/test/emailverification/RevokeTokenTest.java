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
import io.supertokens.emailverification.exception.EmailVerificationInvalidTokenException;
import io.supertokens.pluginInterface.emailverification.EmailVerificationTokenInfo;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.*;
import org.junit.rules.TestRule;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RevokeTokenTest {
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

    @Test(expected = EmailVerificationInvalidTokenException.class)
    public void testTokenCannotBeUsedToVerifyIfRevoked() throws Exception {
        TestingProcessManager.withProcess(process -> {


            Main main = process.getProcess();

            // given
            String token = generateAndRevokeToken(main);

            // when
            EmailVerification.verifyEmail(main, token);

            // then should throw
        });
    }

    @Test
    public void testTokenIsNotReturnedForUserIfRevoked() throws Exception {
        TestingProcessManager.withProcess(process -> {
            Main main = process.getProcess();

            // given
            generateAndRevokeToken(main);

            // when
            EmailVerificationTokenInfo[] tokenInfos = EmailVerification.getTokensForUser(main, "mockUserId", "john.doe@example.com");

            // then
            assertEquals(0, tokenInfos.length);
        });
    }

    @Test
    public void testUserIsNotReturnedFromTokenIfRevoked() throws Exception {
        TestingProcessManager.withProcess(process -> {
            Main main = process.getProcess();

            // given
            String token = generateAndRevokeToken(main);

            // when
            Optional<User> user = EmailVerification.getUserFromToken(main, token);

            // then
            assertTrue(user.isEmpty());
        });
    }

    @Test
    public void testRevokingNonExistingTokenShouldPass() throws Exception {
        TestingProcessManager.withProcess(process -> {
            Main main = process.getProcess();

            // given
            String token = generateAndRevokeToken(main);

            // when
            // revoked for a second time
            EmailVerification.revokeToken(main, token);

            // then shouldn't throw
        });
    }

    private String generateAndRevokeToken(Main main) throws Exception {
        String token = EmailVerification.generateEmailVerificationToken(main, "mockUserId", "john.doe@example.com");

        EmailVerification.revokeToken(main, token);

        return token;
    }
}
