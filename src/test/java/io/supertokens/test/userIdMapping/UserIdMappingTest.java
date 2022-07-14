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
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.useridmapping.UserIdMappingStorage;
import io.supertokens.pluginInterface.useridmapping.exception.UnknownSuperTokensUserIdException;
import io.supertokens.pluginInterface.useridmapping.exception.UserIdMappingAlreadyExistsException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
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

    @Test
    public void testRetrievingUseridMappingWithUnknownId() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // get mapping with unknown userId with userIdType SUPERTOKENS
        assertNull(UserIdMapping.getUserIdMapping(process.main, "unknownUserId", UserIdType.SUPERTOKENS));

        // get mapping with unknown userId with userIdType EXTERNAL
        assertNull(UserIdMapping.getUserIdMapping(process.main, "unknownUserId", UserIdType.EXTERNAL));

        // get mapping with unknown userId with userIdTYPE ANY
        assertNull(UserIdMapping.getUserIdMapping(process.main, "unknownUserId", UserIdType.ANY));

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

        // create a User and then a UserId mapping
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String superTokensUserId = userInfo.id;
        String externalUserId = "externalId";
        String externalUserIdInfo = "externalIdInfo";

        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo);

        // retrieve mapping with supertokensUserId and validate response

        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

            assertNotNull(response);
            assertEquals(superTokensUserId, response.superTokensUserId);
            assertEquals(externalUserId, response.externalUserId);
            assertEquals(externalUserIdInfo, response.externalUserIdInfo);
        }

        // retrieve mapping externalUserId and validate response

        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, externalUserId, UserIdType.EXTERNAL);

            assertNotNull(response);
            assertEquals(superTokensUserId, response.superTokensUserId);
            assertEquals(externalUserId, response.externalUserId);
            assertEquals(externalUserIdInfo, response.externalUserIdInfo);
        }

        // retrieve mapping with using ANY
        {
            {
                // with supertokensUserId
                io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                        .getUserIdMapping(process.main, superTokensUserId, UserIdType.ANY);

                assertNotNull(response);
                assertEquals(superTokensUserId, response.superTokensUserId);
                assertEquals(externalUserId, response.externalUserId);
                assertEquals(externalUserIdInfo, response.externalUserIdInfo);
            }

            {
                // with externalUserId
                io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                        .getUserIdMapping(process.main, externalUserId, UserIdType.ANY);

                assertNotNull(response);
                assertEquals(superTokensUserId, response.superTokensUserId);
                assertEquals(externalUserId, response.externalUserId);
                assertEquals(externalUserIdInfo, response.externalUserIdInfo);
            }
        }

        // create a new mapping where the superTokensUserId of Mapping1 = externalUserId of Mapping2
        UserInfo userInfo2 = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");
        String newSuperTokensUserId = userInfo2.id;
        String newExternalUserId = userInfo.id;
        String newExternalUserIdInfo = "newExternalUserIdInfo";

        UserIdMapping.createUserIdMapping(process.main, newSuperTokensUserId, newExternalUserId, newExternalUserIdInfo);

        // retrieve the mapping with newExternalUserId using ANY, it should return Mapping 1
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, newExternalUserId, UserIdType.ANY);

            // query with the storage layer and check that the db returns two entries
            UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(process.main);
            io.supertokens.pluginInterface.useridmapping.UserIdMapping[] storageResponse = storage
                    .getUserIdMapping(newExternalUserId);
            assertEquals(2, storageResponse.length);

            assertNotNull(response);
            assertEquals(superTokensUserId, response.superTokensUserId);
            assertEquals(externalUserId, response.externalUserId);
            assertEquals(externalUserIdInfo, response.externalUserIdInfo);
        }

        // retrieve the mapping with newExternalUserId using EXTERNAL, it should return Mapping 2
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, newExternalUserId, UserIdType.EXTERNAL);

            assertNotNull(response);
            assertEquals(newSuperTokensUserId, response.superTokensUserId);
            assertEquals(newExternalUserId, response.externalUserId);
            assertEquals(newExternalUserIdInfo, response.externalUserIdInfo);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingUserIdMappingWithUnknownId() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // deleting a mapping with an unknown UserId with userIdType as SUPERTOKENS

        assertFalse(UserIdMapping.deleteUserIdMapping(process.main, "unknownUserId", UserIdType.SUPERTOKENS));

        // deleting a mapping with an unknown UserId with userIdType as EXTERNAL

        assertFalse(UserIdMapping.deleteUserIdMapping(process.main, "unknownUserId", UserIdType.EXTERNAL));

        // deleting a mapping with an unknown UserId with userIdType as ANY

        assertFalse(UserIdMapping.deleteUserIdMapping(process.main, "unknownUserId", UserIdType.ANY));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingUserIdMapping() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create mapping and check that it exists
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String superTokensUserId = userInfo.id;
        String externalUserId = "externalId";
        String externalUserIdInfo = "externalIdInfo";

        io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping_1 = new io.supertokens.pluginInterface.useridmapping.UserIdMapping(
                superTokensUserId, externalUserId, externalUserIdInfo);

        {
            UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo);

            // retrieve mapping and validate response

            {
                io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                        .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

                assertNotNull(response);
                assertEquals(userIdMapping_1, response);
            }

            // Delete mapping with userIdType SUPERTOKENS and check that it is deleted
            assertTrue(UserIdMapping.deleteUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS));

            // check that mapping does not exist
            assertNull(UserIdMapping.getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS));
        }

        {
            // create mapping and check that it exists
            UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo);

            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, externalUserId, UserIdType.EXTERNAL);

            assertNotNull(response);
            assertEquals(userIdMapping_1, response);

            // delete mapping with userIdType EXTERNAL and check that it is deleted
            assertTrue(UserIdMapping.deleteUserIdMapping(process.main, externalUserId, UserIdType.EXTERNAL));

            // check that mapping does not exist
            assertNull(UserIdMapping.getUserIdMapping(process.main, externalUserId, UserIdType.EXTERNAL));

        }

        {
            {
                // create mapping and check that it exists
                UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo);

                io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                        .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

                assertNotNull(response);
                assertEquals(userIdMapping_1, response);

                // delete mapping with superTokensUserId with userIdType ANY and check that it is deleted
                assertTrue(UserIdMapping.deleteUserIdMapping(process.main, superTokensUserId, UserIdType.ANY));

                // check that mapping does not exist
                assertNull(UserIdMapping.getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS));
            }

            {
                // create mapping and check that it exists
                UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo);

                io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                        .getUserIdMapping(process.main, externalUserId, UserIdType.ANY);

                assertNotNull(response);
                assertEquals(userIdMapping_1, response);

                // delete mapping with externalUserId with userIdType ANY and check that it is deleted
                assertTrue(UserIdMapping.deleteUserIdMapping(process.main, externalUserId, UserIdType.ANY));

                // check that mapping does not exist
                assertNull(UserIdMapping.getUserIdMapping(process.main, externalUserId, UserIdType.ANY));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingUserIdMappingWithSharedId() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Create two UserId mappings where superTokensUserId in Mapping 1 = externalUserId in mapping 2

        // Create UserId mapping 1

        UserInfo userInfo_1 = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping_1 = new io.supertokens.pluginInterface.useridmapping.UserIdMapping(
                userInfo_1.id, "externalUserId", "externalUserIdInfo");

        // create the mapping and check that it exists
        {
            UserIdMapping.createUserIdMapping(process.main, userIdMapping_1.superTokensUserId,
                    userIdMapping_1.externalUserId, userIdMapping_1.externalUserIdInfo);
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, userIdMapping_1.superTokensUserId, UserIdType.SUPERTOKENS);
            assertEquals(userIdMapping_1, response);
        }

        // Create UserId mapping 2

        UserInfo userInfo_2 = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");
        io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping_2 = new io.supertokens.pluginInterface.useridmapping.UserIdMapping(
                userInfo_2.id, userIdMapping_1.superTokensUserId, "externalUserIdInfo2");

        // create the mapping and check that it exists
        {
            UserIdMapping.createUserIdMapping(process.main, userIdMapping_2.superTokensUserId,
                    userIdMapping_2.externalUserId, userIdMapping_2.externalUserIdInfo);
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, userIdMapping_2.superTokensUserId, UserIdType.SUPERTOKENS);
            assertEquals(userIdMapping_2, response);
        }

        // delete userIdMapping with userIdMapping_2.externalUserId with userIdType ANY, userIdMapping_1 should be
        // deleted
        assertTrue(UserIdMapping.deleteUserIdMapping(process.main, userIdMapping_2.externalUserId, UserIdType.ANY));

        assertNull(UserIdMapping.getUserIdMapping(process.main, userIdMapping_1.superTokensUserId,
                UserIdType.SUPERTOKENS));

        // check that userIdMapping_2 still exists
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, userIdMapping_2.superTokensUserId, UserIdType.SUPERTOKENS);
            assertEquals(userIdMapping_2, response);
        }

        // delete userIdMapping with userIdMapping_2.externalUserId with EXTERNAL, userIdMapping_2 should be deleted
        assertTrue(
                UserIdMapping.deleteUserIdMapping(process.main, userIdMapping_2.externalUserId, UserIdType.EXTERNAL));
        assertNull(UserIdMapping.getUserIdMapping(process.main, userIdMapping_2.superTokensUserId,
                UserIdType.SUPERTOKENS));

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

        String userId = "unknownId";

        // update with unknown supertokensUserId
        assertFalse(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, userId, UserIdType.SUPERTOKENS, null));

        // update with unknown externalUserId
        assertFalse(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, userId, UserIdType.EXTERNAL, null));

        // update with unknown userId
        assertFalse(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, userId, UserIdType.ANY, null));

        // check that there are no mappings with the userId

        assertNull(UserIdMapping.getUserIdMapping(process.main, userId, UserIdType.ANY));

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

        // create User
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");

        String superTokensUserId = userInfo.id;
        String externalUserId = "externalId";

        // create a userId mapping
        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, null);
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertNull(userIdMapping.externalUserIdInfo);
        }

        // update from null to externalUserIdInfo using userIdType SUPERTOKENS
        String externalUserIdInfo = "externalUserIdInfo";
        assertTrue(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, superTokensUserId,
                UserIdType.SUPERTOKENS, externalUserIdInfo));

        // retrieve mapping and validate
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        // update externalUserIdInfo using userIdType EXTERNAL
        String newExternalUserIdInfo = "newExternalUserIdInfo";
        assertTrue(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, externalUserId, UserIdType.EXTERNAL,
                newExternalUserIdInfo));

        // retrieve mapping and validate with the new externalUserIdInfo
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(newExternalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        // delete externalUserIdInfo by passing null with superTokensUserId with ANY
        assertTrue(
                UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, superTokensUserId, UserIdType.ANY, null));

        // retrieve mapping and check that externalUserIdInfo is null
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertNull(userIdMapping.externalUserIdInfo);
        }

        // update the externalUserIdInfo with externalUserId with ANY
        {
            assertTrue(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, externalUserId, UserIdType.ANY,
                    externalUserIdInfo));

            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingExternalUserIdInfoWithSharedUserIds() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create two UserMappings where superTokensUserId in Mapping 1 = externalUserId in Mapping 2

        // Create mapping 1
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");

        String superTokensUserId = userInfo.id;
        String externalUserId = "externalId";
        String externalUserIdInfo = "externalUserIdInfo";

        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo);

        // check that mapping exists
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        // Create mapping 2
        UserInfo userInfo2 = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");
        String superTokensUserId2 = userInfo2.id;
        String externalUserId2 = userInfo.id;
        String externalUserIdInfo2 = "newExternalUserIdInfo";

        UserIdMapping.createUserIdMapping(process.main, superTokensUserId2, externalUserId2, externalUserIdInfo2);

        // check that the mapping exists
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId2, UserIdType.SUPERTOKENS);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId2, userIdMapping.superTokensUserId);
            assertEquals(externalUserId2, userIdMapping.externalUserId);
            assertEquals(externalUserIdInfo2, userIdMapping.externalUserIdInfo);
        }

        // update the mapping with externalUserId2 with userIdType ANY, userId mapping 1 should be updated
        assertTrue(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, externalUserId2, UserIdType.ANY, null));

        // check that userId mapping 1 got updated and userId mapping 2 is the same
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping_1 = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);
            assertNotNull(userIdMapping_1);
            assertEquals(superTokensUserId, userIdMapping_1.superTokensUserId);
            assertEquals(externalUserId, userIdMapping_1.externalUserId);
            assertNull(userIdMapping_1.externalUserIdInfo);

            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping_2 = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId2, UserIdType.SUPERTOKENS);

            assertNotNull(userIdMapping_2);
            assertEquals(superTokensUserId2, userIdMapping_2.superTokensUserId);
            assertEquals(externalUserId2, userIdMapping_2.externalUserId);
            assertEquals(externalUserIdInfo2, userIdMapping_2.externalUserIdInfo);
        }

        // delete externalUserIdInfo with EXTERNAL from userIdMapping 2 and check that it gets updated
        assertTrue(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, externalUserId2, UserIdType.EXTERNAL,
                null));

        io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                .getUserIdMapping(process.main, superTokensUserId2, UserIdType.SUPERTOKENS);

        assertNotNull(userIdMapping);
        assertEquals(superTokensUserId2, userIdMapping.superTokensUserId);
        assertEquals(externalUserId2, userIdMapping.externalUserId);
        assertNull(userIdMapping.externalUserIdInfo);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
