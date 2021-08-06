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

import io.supertokens.Main;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.emailverification.User;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.*;
import org.junit.rules.TestRule;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GetUserFromTokenTest {
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
    public void testReturnUserIfTokenExists() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            Main main = process.getProcess();

            // given
            // there is a token generated for user
            String token = EmailVerification.generateEmailVerificationToken(main, "mockUserId", "john.doe@example.com");

            // when
            Optional<User> maybeUser = EmailVerification.getUserFromToken(main, token);

            if (maybeUser.isEmpty()) {
                throw new Exception("User should exist!");
            }

            // then
            assertEquals(maybeUser.get().id, "mockUserId");
            assertEquals(maybeUser.get().email, "john.doe@example.com");
        });
    }

    @Test
    public void testNotReturnUserIfTokenDoesntExist() throws Exception {
        TestingProcessManager.withProcess(process -> {
            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            Main main = process.getProcess();

            // given
            // there is no token generated for user

            // when
            Optional<User> maybeUser = EmailVerification.getUserFromToken(main, "inexistent token");

            // then
            assertTrue(maybeUser.isEmpty());
        });
    }
}
