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
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
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

import java.util.ArrayList;
import java.util.HashMap;

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
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);

        Exception error = null;

        try {
            storage.createUserIdMapping(new AppIdentifier(null, null), "unknownSuperTokensUserId",
                    "externalUserId", null);

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
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);

        // create a user
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPassword");

        String externalUserId = "external-test";
        String externalUserIdInfo = "external-info";

        // create a userId mapping
        storage.createUserIdMapping(new AppIdentifier(null, null), userInfo.getSupertokensUserId(), externalUserId,
                externalUserIdInfo);

        // check that the mapping exists
        UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null),
                userInfo.getSupertokensUserId(),
                true);
        assertEquals(userInfo.getSupertokensUserId(), userIdMapping.superTokensUserId);
        assertEquals(externalUserId, userIdMapping.externalUserId);
        assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDuplicateUserIdMapping() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);

        // create a user
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPassword");
        String externalUserId = "external-test";

        storage.createUserIdMapping(new AppIdentifier(null, null), userInfo.getSupertokensUserId(), externalUserId,
                null);

        {
            // duplicate exception with both supertokensUserId and externalUserId
            Exception error = null;
            try {
                storage.createUserIdMapping(new AppIdentifier(null, null), userInfo.getSupertokensUserId(),
                        externalUserId, null);
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
                storage.createUserIdMapping(new AppIdentifier(null, null), userInfo.getSupertokensUserId(),
                        "newExternalId", null);
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

            AuthRecipeUserInfo newUser = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");
            Exception error = null;
            try {
                storage.createUserIdMapping(new AppIdentifier(null, null), newUser.getSupertokensUserId(),
                        externalUserId, null);
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
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);

        // create a User
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String externalUserId = "externalUserId";

        // create a userId mapping
        storage.createUserIdMapping(new AppIdentifier(null, null), userInfo.getSupertokensUserId(), externalUserId,
                null);

        // create a new mapping with unknown superTokensUserId and existing externalUserId
        Exception error = null;
        try {
            storage.createUserIdMapping(new AppIdentifier(null, null), "unknownUserId", externalUserId, null);
        } catch (Exception e) {
            error = e;
        }

        assertNotNull(error);

        if (error instanceof UserIdMappingAlreadyExistsException) {
            UserIdMappingAlreadyExistsException userIdMappingAlreadyExistsException =
                    (UserIdMappingAlreadyExistsException) error;
            assertFalse(userIdMappingAlreadyExistsException.doesSuperTokensUserIdExist);
            assertTrue(userIdMappingAlreadyExistsException.doesExternalUserIdExist);
        } else {
            assertTrue(error instanceof UnknownSuperTokensUserIdException);
        }
    }

    @Test
    public void testRetrievingUserIdMappingWithUnknownSuperTokensUserId() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);

        {
            UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null), "unknownId",
                    true);
            assertNull(userIdMapping);
        }

        {
            UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null), "unknownId",
                    false);
            assertNull(userIdMapping);
        }

        {
            UserIdMapping[] userIdMappings = storage.getUserIdMapping(new AppIdentifier(null, null),
                    "unknownUd");
            assertEquals(0, userIdMappings.length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUserIdMapping() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);

        // create a user
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");

        String externalUserId = "externalUserId";
        String externalUserIdInfo = "externalUserIdInfo";

        // create the mapping
        storage.createUserIdMapping(new AppIdentifier(null, null), userInfo.getSupertokensUserId(), externalUserId,
                externalUserIdInfo);

        // check that the mapping exists with supertokensUserId
        {
            UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null),
                    userInfo.getSupertokensUserId(),
                    true);

            assertNotNull(userIdMapping);
            assertEquals(userInfo.getSupertokensUserId(), userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        // check that the mapping exists with externalUserId
        {
            UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null),
                    externalUserId, false);

            assertNotNull(userIdMapping);
            assertEquals(userInfo.getSupertokensUserId(), userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        // check that the mapping exists with either
        {
            UserIdMapping[] userIdMappings = storage.getUserIdMapping(new AppIdentifier(null, null),
                    userInfo.getSupertokensUserId());
            assertEquals(1, userIdMappings.length);
            assertEquals(userInfo.getSupertokensUserId(), userIdMappings[0].superTokensUserId);
            assertEquals(externalUserId, userIdMappings[0].externalUserId);
            assertEquals(externalUserIdInfo, userIdMappings[0].externalUserIdInfo);
        }
        {
            UserIdMapping[] userIdMappings = storage.getUserIdMapping(new AppIdentifier(null, null),
                    externalUserId);
            assertEquals(1, userIdMappings.length);
            assertEquals(userInfo.getSupertokensUserId(), userIdMappings[0].superTokensUserId);
            assertEquals(externalUserId, userIdMappings[0].externalUserId);
            assertEquals(externalUserIdInfo, userIdMappings[0].externalUserIdInfo);

        }

        // create a new user and create a mapping where the externalUserId is equal to the previous users
        // superTokensUserId

        {
            AuthRecipeUserInfo newUserInfo = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");
            String externalUserId2 = userInfo.getSupertokensUserId();

            storage.createUserIdMapping(new AppIdentifier(null, null), newUserInfo.getSupertokensUserId(),
                    externalUserId2, null);

            UserIdMapping[] userIdMappings = storage.getUserIdMapping(new AppIdentifier(null, null),
                    externalUserId2);
            assertEquals(2, userIdMappings.length);

            boolean checkThatUser1MappingIsReturned = false;
            boolean checkThatUser2MappingIsReturned = false;

            for (UserIdMapping userIdMapping : userIdMappings) {
                if (userIdMapping.superTokensUserId.equals(userInfo.getSupertokensUserId())) {
                    assertEquals(userInfo.getSupertokensUserId(), userIdMapping.superTokensUserId);
                    assertEquals(externalUserId, userIdMapping.externalUserId);
                    assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
                    checkThatUser1MappingIsReturned = true;
                } else {
                    assertEquals(newUserInfo.getSupertokensUserId(), userIdMapping.superTokensUserId);
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
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);

        assertFalse(storage.deleteUserIdMapping(new AppIdentifier(null, null), "unknownUserId", true));

        assertFalse(storage.deleteUserIdMapping(new AppIdentifier(null, null), "unknownUserId", false));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingAUserIdMapping() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);

        // create a user
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String superTokensUserId = userInfo.getSupertokensUserId();
        String externalUserId = "externalUserId";
        {
            // create a new userId mapping
            storage.createUserIdMapping(new AppIdentifier(null, null), superTokensUserId, externalUserId,
                    null);

            // retrieve mapping and check that it exists
            UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null),
                    superTokensUserId, true);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertNull(userIdMapping.externalUserIdInfo);

            // delete mapping with a supertokensUserId
            assertTrue(storage.deleteUserIdMapping(new AppIdentifier(null, null), superTokensUserId, true));

            // check that the mapping does not exist
            assertNull(storage.getUserIdMapping(new AppIdentifier(null, null), superTokensUserId, true));

        }

        {
            // create a new userId mapping
            String newExternalUserId = "externalUserIdNew";
            storage.createUserIdMapping(new AppIdentifier(null, null), superTokensUserId, newExternalUserId,
                    null);
            // retrieve mapping and check that it exists
            UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null),
                    newExternalUserId, false);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(newExternalUserId, userIdMapping.externalUserId);
            assertNull(userIdMapping.externalUserIdInfo);

            // delete mapping with externalUserId
            assertTrue(storage.deleteUserIdMapping(new AppIdentifier(null, null), newExternalUserId, false));

            // check that the mapping does not exist
            assertNull(storage.getUserIdMapping(new AppIdentifier(null, null), newExternalUserId, false));

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingExternalUserIdInfoWithUnknownUserId() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);

        String userId = "unknownId";

        // update with unknown supertokensUserId
        assertFalse(
                storage.updateOrDeleteExternalUserIdInfo(new AppIdentifier(null, null), userId, true, null));

        // update with unknown externalUserId
        assertFalse(
                storage.updateOrDeleteExternalUserIdInfo(new AppIdentifier(null, null), userId, false, null));

        // check that there are no mappings with the userId

        UserIdMapping[] userIdMappings = storage.getUserIdMapping(new AppIdentifier(null, null), userId);

        assertEquals(0, userIdMappings.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingExternalUserIdInfo() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);

        // create User
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");

        String superTokensUserId = userInfo.getSupertokensUserId();
        String externalUserId = "externalId";
        String externalUserIdInfo = "externalUserIdInfo";

        // create a userId mapping
        storage.createUserIdMapping(new AppIdentifier(null, null), superTokensUserId, externalUserId, null);
        {
            UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null),
                    superTokensUserId, true);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertNull(userIdMapping.externalUserIdInfo);
        }

        // update from null to externalUserIdInfo
        assertTrue(storage.updateOrDeleteExternalUserIdInfo(new AppIdentifier(null, null), superTokensUserId,
                true, externalUserIdInfo));

        // retrieve mapping and validate
        {
            UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null),
                    superTokensUserId, true);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        // update externalUserIdInfo
        String newExternalUserIdInfo = "newExternalUserIdInfo";
        assertTrue(storage.updateOrDeleteExternalUserIdInfo(new AppIdentifier(null, null), superTokensUserId,
                true, newExternalUserIdInfo));

        // retrieve mapping and validate with the new externalUserIdInfo
        {
            UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null),
                    superTokensUserId, true);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(newExternalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        // delete externalUserIdInfo by passing null
        assertTrue(
                storage.updateOrDeleteExternalUserIdInfo(new AppIdentifier(null, null), externalUserId, false,
                        null));

        // retrieve mapping and check that externalUserIdInfo is null
        {
            UserIdMapping userIdMapping = storage.getUserIdMapping(new AppIdentifier(null, null),
                    externalUserId, false);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertNull(userIdMapping.externalUserIdInfo);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createUsersMapTheirIdsCheckRetrieveUseIdMappingsWithListOfUserIds() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);
        ArrayList<String> superTokensUserIdList = new ArrayList<>();
        ArrayList<String> externalUserIdList = new ArrayList<>();

        // create users equal to the User Pagination limit
        for (int i = 1; i <= AuthRecipe.USER_PAGINATION_LIMIT; i++) {
            AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test" + i + "@example.com",
                    "testPass123");
            superTokensUserIdList.add(userInfo.getSupertokensUserId());
            String superTokensUserId = userInfo.getSupertokensUserId();
            String externalUserId = "externalId" + i;
            externalUserIdList.add(externalUserId);

            // create a userId mapping
            storage.createUserIdMapping(new AppIdentifier(null, null), superTokensUserId, externalUserId,
                    null);
        }
        HashMap<String, String> response = storage.getUserIdMappingForSuperTokensIds(
                new AppIdentifier(null, null), superTokensUserIdList);
        assertEquals(AuthRecipe.USER_PAGINATION_LIMIT, response.size());
        for (int i = 0; i < response.size(); i++) {
            assertEquals(externalUserIdList.get(i), response.get(superTokensUserIdList.get(i)));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCallingGetUserIdMappingForSuperTokensIdsWithEmptyList() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);
        ArrayList<String> emptyList = new ArrayList<>();

        HashMap<String, String> response = storage.getUserIdMappingForSuperTokensIds(
                new AppIdentifier(null, null), emptyList);
        assertEquals(0, response.size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCallingGetUserIdMappingForSuperTokensIdsWhenNoMappingExists() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);
        ArrayList<String> superTokensUserIdList = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test" + i + "@example.com",
                    "testPass123");
            superTokensUserIdList.add(userInfo.getSupertokensUserId());
        }

        HashMap<String, String> userIdMapping = storage.getUserIdMappingForSuperTokensIds(
                new AppIdentifier(null, null), superTokensUserIdList);
        assertEquals(0, userIdMapping.size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void create10UsersAndMap5UsersIds() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = (UserIdMappingStorage) StorageLayer.getStorage(process.main);
        ArrayList<String> superTokensUserIdList = new ArrayList<>();
        ArrayList<String> userIdList = new ArrayList<>();

        // create users equal to the User Pagination limit
        for (int i = 1; i <= 10; i++) {
            AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test" + i + "@example.com",
                    "testPass123");
            superTokensUserIdList.add(userInfo.getSupertokensUserId());

            if (i <= 5) {
                userIdList.add(userInfo.getSupertokensUserId());
            } else {
                // create userIdMapping for the last 5 users
                String externalUserId = "externalId" + i;
                userIdList.add(externalUserId);
                storage.createUserIdMapping(new AppIdentifier(null, null), userInfo.getSupertokensUserId(),
                        externalUserId, null);
            }
        }

        // retrieve UserIDMapping
        HashMap<String, String> response = storage.getUserIdMappingForSuperTokensIds(
                new AppIdentifier(null, null), superTokensUserIdList);
        assertEquals(5, response.size());

        // check that the last 5 users have their ids mapped
        for (int i = 5; i <= superTokensUserIdList.size() - 1; i++) {
            assertEquals(userIdList.get(i), response.get(superTokensUserIdList.get(i)));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
