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
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.pluginInterface.useridmapping.UserIdMappingStorage;
import io.supertokens.pluginInterface.useridmapping.exception.UnknownSuperTokensUserIdException;
import io.supertokens.pluginInterface.useridmapping.exception.UserIdMappingAlreadyExistsException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class UserIdMappingStorageTest {
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
    public void testCreatingAUserWithAnUnknownSuperTokensUserId() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(process.main);

        Exception error = null;

        try {
            storage.createUserIdMapping("unknownSuperTokensUserId", "externalUserId", null);

        } catch (Exception e) {
            error = e;
        }

        assertNotNull(error);
        assertTrue(error instanceof UnknownSuperTokensUserIdException);

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
        storage.createUserIdMapping(userInfo.id, externalUserId, externalUserIdInfo);

        // check that the mapping exists
        UserIdMapping userIdMapping = storage.getUserIdMapping(userInfo.id, true);
        assertEquals(userInfo.id, userIdMapping.superTokensUserId);
        assertEquals(externalUserId, userIdMapping.externalUserId);
        assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);

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

        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(process.main);

        // create a user
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPassword");
        String externalUserId = "external-test";

        storage.createUserIdMapping(userInfo.id, externalUserId, null);

        {
            // duplicate exception with both supertokensUserId and externalUserId
            Exception error = null;
            try {
                storage.createUserIdMapping(userInfo.id, externalUserId, null);
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
                storage.createUserIdMapping(userInfo.id, "newExternalId", null);
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
                storage.createUserIdMapping(newUser.id, externalUserId, null);
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
    public void testCreatingAMappingWithAnUnknownStUserIdAndAPreexistingExternalUserId() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(process.main);

        // create a User
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String externalUserId = "externalUserId";

        // create a userId mapping
        storage.createUserIdMapping(userInfo.id, externalUserId, null);

        // create a new mapping with unknown superTokensUserId and existing externalUserId
        Exception error = null;
        try {
            storage.createUserIdMapping("unknownUserId", externalUserId, null);
        } catch (Exception e) {
            error = e;
        }

        assertNotNull(error);
        assertTrue(error instanceof UnknownSuperTokensUserIdException);
    }

    @Test
    public void testRetrievingUserIdMappingWithUnknownSuperTokensUserId() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(process.main);

        {
            UserIdMapping userIdMapping = storage.getUserIdMapping("unknownId", true);
            assertNull(userIdMapping);
        }

        {
            UserIdMapping[] userIdMappings = storage.getUserIdMapping("unknownUd");
            assertEquals(0, userIdMappings.length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUserIdMapping() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(process.main);

        // create a user
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");

        String externalUserId = "externalUserId";
        String externalUserIdInfo = "externalUserIdInfo";

        // create the mapping
        storage.createUserIdMapping(userInfo.id, externalUserId, externalUserIdInfo);

        // check that the mapping exists with supertokensUserId
        {
            UserIdMapping userIdMapping = storage.getUserIdMapping(userInfo.id, true);

            assertNotNull(userIdMapping);
            assertEquals(userInfo.id, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        // check that the mapping exists with externalUserId
        {
            UserIdMapping userIdMapping = storage.getUserIdMapping(externalUserId, false);

            assertNotNull(userIdMapping);
            assertEquals(userInfo.id, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        // check that the mapping exists with either
        {
            UserIdMapping[] userIdMappings = storage.getUserIdMapping(externalUserId);
            assertEquals(1, userIdMappings.length);
            assertEquals(userInfo.id, userIdMappings[0].superTokensUserId);
            assertEquals(externalUserId, userIdMappings[0].externalUserId);
            assertEquals(externalUserIdInfo, userIdMappings[0].externalUserIdInfo);

        }

        // create a new user and create a mapping where the externalUserId is equal to the previous users
        // superTokensUserId

        {
            UserInfo newUserInfo = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");
            String externalUserId2 = userInfo.id;

            storage.createUserIdMapping(newUserInfo.id, externalUserId2, null);

            UserIdMapping[] userIdMappings = storage.getUserIdMapping(externalUserId2);
            assertEquals(2, userIdMappings.length);

            for (UserIdMapping userIdMapping : userIdMappings) {
                if (userIdMapping.superTokensUserId.equals(userInfo.id)) {
                    assertEquals(userInfo.id, userIdMapping.superTokensUserId);
                    assertEquals(externalUserId, userIdMapping.externalUserId);
                    assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
                } else {
                    assertEquals(newUserInfo.id, userIdMapping.superTokensUserId);
                    assertEquals(externalUserId2, userIdMapping.externalUserId);
                    assertNull(userIdMapping.externalUserIdInfo);
                }
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
