/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.userIdMapping;

import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.useridmapping.UserIdMappingStorage;
import io.supertokens.pluginInterface.useridmapping.exception.UnknownSuperTokensUserIdException;
import io.supertokens.pluginInterface.useridmapping.exception.UserIdMappingAlreadyExistsException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.useridmapping.UserIdMapping;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

public class UserIdMappingTest {
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
    public void testCreatingUserIdMappingWithUnknownSuperTokensUserId() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a userId mapping with unknown SuperTokens UserId
        Exception error = null;
        try {
            UserIdMapping.createUserIdMapping(process.main, "unknownSuperTokensUserId", "externalUserId", "someInfi");
        } catch (Exception e) {
            error = e;
        }

        assertNotNull(error);
        assertTrue(error instanceof UnknownSuperTokensUserIdException);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDuplicateUserIdMapping() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a user
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPassword");

        String externalUserId = "external-test";

        UserIdMapping.createUserIdMapping(process.main, userInfo.id, externalUserId, null);

        {
            // duplicate exception with both supertokensUserId and externalUserId
            Exception error = null;
            try {
                UserIdMapping.createUserIdMapping(process.main, userInfo.id, externalUserId, null);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assertTrue(error instanceof UserIdMappingAlreadyExistsException);

            UserIdMappingAlreadyExistsException usersIdMappingExistsError = (UserIdMappingAlreadyExistsException) error;
            assertTrue(usersIdMappingExistsError.doesExternalUserIdExist);
            assertTrue(usersIdMappingExistsError.doesSuperTokensUserIdExist);
        }

        {
            // duplicate exception with superTokensUserId
            Exception error = null;
            try {
                UserIdMapping.createUserIdMapping(process.main, userInfo.id, "newExternalId", null);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assertTrue(error instanceof UserIdMappingAlreadyExistsException);

            UserIdMappingAlreadyExistsException usersIdMappingExistsError = (UserIdMappingAlreadyExistsException) error;
            assertFalse(usersIdMappingExistsError.doesExternalUserIdExist);
            assertTrue(usersIdMappingExistsError.doesSuperTokensUserIdExist);

        }

        {
            // duplicate exception with externalUserId

            UserInfo newUser = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");
            Exception error = null;
            try {
                UserIdMapping.createUserIdMapping(process.main, newUser.id, externalUserId, null);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assertTrue(error instanceof UserIdMappingAlreadyExistsException);

            UserIdMappingAlreadyExistsException usersIdMappingExistsError = (UserIdMappingAlreadyExistsException) error;
            assertTrue(usersIdMappingExistsError.doesExternalUserIdExist);
            assertFalse(usersIdMappingExistsError.doesSuperTokensUserIdExist);

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingUserIdMapping() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(process.main);

        // create a user
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPassword");

        String externalUserId = "external-test";
        String externalUserIdInfo = "external-info";

        // create a userId mapping
        UserIdMapping.createUserIdMapping(process.getProcess(), userInfo.id, externalUserId, externalUserIdInfo);

        // check that the mapping exists
        io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = storage.getUserIdMapping(userInfo.id,
                true);
        assertEquals(userInfo.id, userIdMapping.superTokensUserId);
        assertEquals(externalUserId, userIdMapping.externalUserId);
        assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
