/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.bulkimport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import io.supertokens.ProcessState;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.bulkimport.BulkImportUserPaginationContainer;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage.BulkImportUserStatus;
import io.supertokens.pluginInterface.bulkimport.BulkImportUserInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;

import static io.supertokens.test.bulkimport.BulkImportTestUtils.generateBulkImportUser;

public class BulkImportTest {
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
    public void shouldAddUsersInBulkImportUsersTable() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<BulkImportUser> users = generateBulkImportUser(10);

        BulkImportStorage storage = (BulkImportStorage) StorageLayer.getStorage(process.main);
        BulkImport.addUsers(new AppIdentifierWithStorage(null, null, storage), users);

        List<BulkImportUserInfo> addedUsers = storage.getBulkImportUsers(new AppIdentifier(null, null), null, BulkImportUserStatus.NEW, null, null);

        // Verify that all users are present in addedUsers
        for (BulkImportUser user : users) {
            BulkImportUserInfo matchingUser = addedUsers.stream()
                    .filter(addedUser -> user.id.equals(addedUser.id))
                    .findFirst()
                    .orElse(null);

            assertNotNull(matchingUser);
            assertEquals(BulkImportUserStatus.NEW, matchingUser.status);
            assertEquals(user.toString(), matchingUser.rawData);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldCreatedNewIdsIfDuplicateIdIsFound() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        List<BulkImportUser> users = generateBulkImportUser(10);

        // We are setting the id of the second user to be the same as the first user to ensure a duplicate id is present
        users.get(1).id = users.get(0).id;

        List<String> initialIds = users.stream().map(user -> user.id).collect(Collectors.toList());

        BulkImportStorage storage = (BulkImportStorage) StorageLayer.getStorage(process.main);
        AppIdentifierWithStorage appIdentifierWithStorage = new AppIdentifierWithStorage(null, null, storage);
        BulkImport.addUsers(appIdentifierWithStorage, users);

        List<BulkImportUserInfo> addedUsers = storage.getBulkImportUsers(appIdentifierWithStorage, null, BulkImportUserStatus.NEW, null, null);

        // Verify that the other properties are same but ids changed
        for (BulkImportUser user : users) {
            BulkImportUserInfo matchingUser = addedUsers.stream()
                    .filter(addedUser -> user.toString().equals(addedUser.rawData))
                    .findFirst()
                    .orElse(null);

            assertNotNull(matchingUser);
            assertEquals(BulkImportUserStatus.NEW, matchingUser.status);
            assertFalse(initialIds.contains(matchingUser.id));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetUsersStatusFilter() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        
        BulkImportStorage storage = (BulkImportStorage) StorageLayer.getStorage(process.main);
        AppIdentifierWithStorage appIdentifierWithStorage = new AppIdentifierWithStorage(null, null, storage);

        // Test with status = 'NEW'
        {
            List<BulkImportUser> users = generateBulkImportUser(10);
            BulkImport.addUsers(appIdentifierWithStorage, users);

            List<BulkImportUserInfo> addedUsers = storage.getBulkImportUsers(appIdentifierWithStorage, null, BulkImportUserStatus.NEW, null, null);
            assertEquals(10, addedUsers.size());
        }

        // Test with status = 'PROCESSING'
        {
            List<BulkImportUser> users = generateBulkImportUser(10);
            BulkImport.addUsers(appIdentifierWithStorage, users);

            // Update the users status to PROCESSING
            for (BulkImportUser user : users) {
                storage.updateBulkImportUserStatus(appIdentifierWithStorage, user.id, BulkImportUserStatus.PROCESSING);
            }

            List<BulkImportUserInfo> addedUsers = storage.getBulkImportUsers(appIdentifierWithStorage, null, BulkImportUserStatus.PROCESSING, null, null);
            assertEquals(10, addedUsers.size());
        }

        // Test with status = 'FAILED'
        {
            List<BulkImportUser> users = generateBulkImportUser(10);
            BulkImport.addUsers(appIdentifierWithStorage, users);

            // Update the users status to FAILED
            for (BulkImportUser user : users) {
                storage.updateBulkImportUserStatus(appIdentifierWithStorage, user.id, BulkImportUserStatus.FAILED);
            }

            List<BulkImportUserInfo> addedUsers = storage.getBulkImportUsers(appIdentifierWithStorage, null, BulkImportUserStatus.FAILED, null, null);
            assertEquals(10, addedUsers.size());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void randomPaginationTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        BulkImportStorage storage = (BulkImportStorage) StorageLayer.getStorage(process.main);

        int numberOfUsers = 500;
        // Insert users in batches
        {
            int batchSize = 100;
            for (int i = 0; i < numberOfUsers; i += batchSize) {
                List<BulkImportUser> users = generateBulkImportUser(batchSize);
                BulkImport.addUsers(new AppIdentifierWithStorage(null, null, storage), users);
                // Adding a delay between each batch to ensure the createdAt different
                Thread.sleep(1000);
            }
        }

        // Get all inserted users
        List<BulkImportUserInfo> addedUsers = storage.getBulkImportUsers(new AppIdentifier(null, null), null, null, null, null);
        assertEquals(numberOfUsers, addedUsers.size());

        // We are sorting the users based on createdAt and id like we do in the storage layer
        List<BulkImportUserInfo> sortedUsers = addedUsers.stream()
                .sorted((user1, user2) -> {
                    int compareResult = user2.createdAt.compareTo(user1.createdAt);
                    if (compareResult == 0) {
                        return user2.id.compareTo(user1.id);
                    }
                    return compareResult;
                })
                .collect(Collectors.toList());

        int[] limits = new int[]{10, 14, 20, 23, 50, 100, 110, 150, 200, 510};

        for (int limit : limits) {
            int indexIntoUsers = 0;
            String paginationToken = null;
            do {
                BulkImportUserPaginationContainer users = BulkImport.getUsers(new AppIdentifierWithStorage(null, null, storage), limit, null, paginationToken);

                for (BulkImportUserInfo actualUser : users.users) {
                    BulkImportUserInfo expectedUser = sortedUsers.get(indexIntoUsers);

                    assertEquals(expectedUser.id, actualUser.id);
                    assertEquals(expectedUser.rawData, actualUser.rawData);
                    indexIntoUsers++;
                }

                paginationToken = users.nextPaginationToken;
            } while (paginationToken != null);

            assert (indexIntoUsers == sortedUsers.size());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void shouldDeleteUsersFromTheBulkImportUsersTable() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        
        BulkImportStorage storage = (BulkImportStorage) StorageLayer.getStorage(process.main);
        AppIdentifierWithStorage appIdentifierWithStorage = new AppIdentifierWithStorage(null, null, storage);

        // Test with status = 'NEW'
        {
            List<BulkImportUser> users = generateBulkImportUser(10);
            BulkImport.addUsers(appIdentifierWithStorage, users);

            List<BulkImportUserInfo> addedUsers = storage.getBulkImportUsers(appIdentifierWithStorage, null, BulkImportUserStatus.NEW, null, null);
            assertEquals(10, addedUsers.size());

            String userIds[] = addedUsers.stream().map(user -> user.id).toArray(String[]::new);
            BulkImport.deleteUsers(appIdentifierWithStorage, userIds);

            List<BulkImportUserInfo> usersAfterDelete = storage.getBulkImportUsers(appIdentifierWithStorage, null, BulkImportUserStatus.NEW, null, null);
            assertEquals(0, usersAfterDelete.size());
        }

        // Test with status = 'PROCESSING'
        {
            List<BulkImportUser> users = generateBulkImportUser(10);
            BulkImport.addUsers(appIdentifierWithStorage, users);

            // Update the users status to PROCESSING
            for (BulkImportUser user : users) {
                storage.updateBulkImportUserStatus(appIdentifierWithStorage, user.id, BulkImportUserStatus.PROCESSING);
            }

            List<BulkImportUserInfo> addedUsers = storage.getBulkImportUsers(appIdentifierWithStorage, null, BulkImportUserStatus.PROCESSING, null, null);
            assertEquals(10, addedUsers.size());

            String userIds[] = addedUsers.stream().map(user -> user.id).toArray(String[]::new);
            BulkImport.deleteUsers(appIdentifierWithStorage, userIds);

            List<BulkImportUserInfo> usersAfterDelete = storage.getBulkImportUsers(appIdentifierWithStorage, null, BulkImportUserStatus.PROCESSING, null, null);
            assertEquals(0, usersAfterDelete.size());
        }

        // Test with status = 'FAILED'
        {
            List<BulkImportUser> users = generateBulkImportUser(10);
            BulkImport.addUsers(appIdentifierWithStorage, users);

            // Update the users status to FAILED
            for (BulkImportUser user : users) {
                storage.updateBulkImportUserStatus(appIdentifierWithStorage, user.id, BulkImportUserStatus.FAILED);
            }

            List<BulkImportUserInfo> addedUsers = storage.getBulkImportUsers(appIdentifierWithStorage, null, BulkImportUserStatus.FAILED, null, null);
            assertEquals(10, addedUsers.size());

            String userIds[] = addedUsers.stream().map(user -> user.id).toArray(String[]::new);
            BulkImport.deleteUsers(appIdentifierWithStorage, userIds);

            List<BulkImportUserInfo> usersAfterDelete = storage.getBulkImportUsers(appIdentifierWithStorage, null, BulkImportUserStatus.PROCESSING, null, null);
            assertEquals(0, usersAfterDelete.size());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
