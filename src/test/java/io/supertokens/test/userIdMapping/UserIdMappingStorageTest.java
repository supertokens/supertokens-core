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

        if (error instanceof UserIdMappingAlreadyExistsException) {
            UserIdMappingAlreadyExistsException userIdMappingAlreadyExistsException = (UserIdMappingAlreadyExistsException) error;
            assertFalse(userIdMappingAlreadyExistsException.doesSuperTokensUserIdExist);
            assertTrue(userIdMappingAlreadyExistsException.doesExternalUserIdExist);
        } else {
            assertTrue(error instanceof UnknownSuperTokensUserIdException);
        }
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
            UserIdMapping userIdMapping = storage.getUserIdMapping("unknownId", false);
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
            UserIdMapping[] userIdMappings = storage.getUserIdMapping(userInfo.id);
            assertEquals(1, userIdMappings.length);
            assertEquals(userInfo.id, userIdMappings[0].superTokensUserId);
            assertEquals(externalUserId, userIdMappings[0].externalUserId);
            assertEquals(externalUserIdInfo, userIdMappings[0].externalUserIdInfo);
        }
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

            boolean checkThatUser1MappingIsReturned = false;
            boolean checkThatUser2MappingIsReturned = false;

            for (UserIdMapping userIdMapping : userIdMappings) {
                if (userIdMapping.superTokensUserId.equals(userInfo.id)) {
                    assertEquals(userInfo.id, userIdMapping.superTokensUserId);
                    assertEquals(externalUserId, userIdMapping.externalUserId);
                    assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
                    checkThatUser1MappingIsReturned = true;
                } else {
                    assertEquals(newUserInfo.id, userIdMapping.superTokensUserId);
                    assertEquals(externalUserId2, userIdMapping.externalUserId);
                    assertNull(userIdMapping.externalUserIdInfo);
                    checkThatUser2MappingIsReturned = true;
                }
            }
            assertTrue(checkThatUser1MappingIsReturned && checkThatUser2MappingIsReturned);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingUserIdMappingWithAnUnknownId() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(process.main);

        assertFalse(storage.deleteUserIdMapping("unknownUserId", true));

        assertFalse(storage.deleteUserIdMapping("unknownUserId", false));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingAUserIdMapping() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(process.main);

        // create a user
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String superTokensUserId = userInfo.id;
        String externalUserId = "externalUserId";
        {
            // create a new userId mapping
            storage.createUserIdMapping(superTokensUserId, externalUserId, null);

            // retrieve mapping and check that it exists
            UserIdMapping userIdMapping = storage.getUserIdMapping(superTokensUserId, true);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertNull(userIdMapping.externalUserIdInfo);

            // delete mapping with a supertokensUserId
            assertTrue(storage.deleteUserIdMapping(superTokensUserId, true));

            // check that the mapping does not exist
            assertNull(storage.getUserIdMapping(superTokensUserId, true));

        }

        {
            // create a new userId mapping
            String newExternalUserId = "externalUserIdNew";
            storage.createUserIdMapping(superTokensUserId, newExternalUserId, null);
            // retrieve mapping and check that it exists
            UserIdMapping userIdMapping = storage.getUserIdMapping(newExternalUserId, false);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(newExternalUserId, userIdMapping.externalUserId);
            assertNull(userIdMapping.externalUserIdInfo);

            // delete mapping with externalUserId
            assertTrue(storage.deleteUserIdMapping(newExternalUserId, false));

            // check that the mapping does not exist
            assertNull(storage.getUserIdMapping(newExternalUserId, false));

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingExternalUserIdInfoWithUnknownUserId() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(process.main);

        String userId = "unknownId";

        // update with unknown supertokensUserId
        assertFalse(storage.updateOrDeleteExternalUserIdInfo(userId, true, null));

        // update with unknown externalUserId
        assertFalse(storage.updateOrDeleteExternalUserIdInfo(userId, false, null));

        // check that there are no mappings with the userId

        UserIdMapping[] userIdMappings = storage.getUserIdMapping(userId);

        assertEquals(0, userIdMappings.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingExternalUserIdInfo() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(process.main);

        // create User
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");

        String superTokensUserId = userInfo.id;
        String externalUserId = "externalId";
        String externalUserIdInfo = "externalUserIdInfo";

        // create a userId mapping
        storage.createUserIdMapping(superTokensUserId, externalUserId, null);
        {
            UserIdMapping userIdMapping = storage.getUserIdMapping(superTokensUserId, true);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertNull(userIdMapping.externalUserIdInfo);
        }

        // update from null to externalUserIdInfo
        assertTrue(storage.updateOrDeleteExternalUserIdInfo(superTokensUserId, true, externalUserIdInfo));

        // retrieve mapping and validate
        {
            UserIdMapping userIdMapping = storage.getUserIdMapping(superTokensUserId, true);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        // update externalUserIdInfo
        String newExternalUserIdInfo = "newExternalUserIdInfo";
        assertTrue(storage.updateOrDeleteExternalUserIdInfo(superTokensUserId, true, newExternalUserIdInfo));

        // retrieve mapping and validate with the new externalUserIdInfo
        {
            UserIdMapping userIdMapping = storage.getUserIdMapping(superTokensUserId, true);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(newExternalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        // delete externalUserIdInfo by passing null
        assertTrue(storage.updateOrDeleteExternalUserIdInfo(externalUserId, false, null));

        // retrieve mapping and check that externalUserIdInfo is null
        {
            UserIdMapping userIdMapping = storage.getUserIdMapping(externalUserId, false);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertNull(userIdMapping.externalUserIdInfo);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
