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

package io.supertokens.test.userRoles;

import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.userroles.exception.DuplicateUserRoleMappingException;
import io.supertokens.pluginInterface.userroles.exception.UnknownRoleException;
import io.supertokens.pluginInterface.userroles.sqlStorage.UserRolesSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.userroles.UserRoles;
import io.supertokens.version.Version;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class UserRolesStorageTest {

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

    // Deleting a role whilst it's being removed from a user
    @Test
    public void testDeletingARoleWhileItIsBeingRemovedFromAUser() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String role = "role";
        String userId = "userId";
        // create a role
        boolean newRoleCreated = UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);
        assertTrue(newRoleCreated);

        // assign role to user
        boolean didUserAlreadyHaveRole = UserRoles.addRoleToUser(process.main, userId, role);
        assertTrue(didUserAlreadyHaveRole);

        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);
        AtomicBoolean r1_success = new AtomicBoolean(false);
        AtomicBoolean r2_success = new AtomicBoolean(false);

        Runnable r1 = () -> {

            try {
                storage.startTransaction(con -> {
                    // wait for some time
                    storage.doesRoleExist_Transaction(con, role);

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignored
                    }

                    // add permissions
                    boolean wasRoleRemovedFromUser = storage.deleteRoleForUser_Transaction(con, userId, role);

                    storage.commitTransaction(con);
                    r1_success.set(wasRoleRemovedFromUser);
                    return null;
                });
            } catch (StorageQueryException | StorageTransactionLogicException e) {
                // should not come here
            }
        };

        Runnable r2 = () -> {
            // wait for some time so doesRoleExist runs
            try {
                Thread.sleep(700);
            } catch (InterruptedException e) {
                // Ignore
            }
            // delete the role
            try {
                boolean wasRoleDeleted = storage.deleteRole(role);
                r2_success.set(wasRoleDeleted);
            } catch (StorageQueryException e) {
                // should not come here
            }
        };

        Thread thread1 = new Thread(r1);
        Thread thread2 = new Thread(r2);

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        // check that role was removed from the user
        assertTrue(r1_success.get());
        // check that role was deleted
        assertTrue(r2_success.get());

        // check that role was actuall removed from user
        assertEquals(0, storage.getRolesForUser(userId).length);

        // check that role was actually deleted
        assertFalse(storage.doesRoleExist(role));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /*
     * In thread 1: Start a transaction -> call createNewRole_Transaction -> wait.... -> call
     * addPermissionToRole_Transaction -> commit. In thread 2: Wait for thread
     * 1 to start waiting, then delete the role it created. At the end we want to make sure that the role was created.
     */

    @Test
    public void testCreatingAndAddingAPermissionToARoleInTransactionAndDeletingRole() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // we only want to run this test for MySQL and Postgres since the behaviour for SQLite is different
        if (Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
            return;
        }

        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        // thread 1: start transaction -> call createNewRole_Transaction -> wait... ->
        // call addPermissionToRole_Transaction -> commit
        String role = "role";
        String[] permissions = new String[] { "permission" };
        AtomicInteger numberOfIterations = new AtomicInteger(0);
        AtomicBoolean r1_success = new AtomicBoolean(false);
        AtomicBoolean r2_success = new AtomicBoolean(false);
        Runnable r1 = () -> {

            try {
                storage.startTransaction(con -> {
                    numberOfIterations.incrementAndGet();
                    // create a new Role
                    storage.createNewRoleOrDoNothingIfExists_Transaction(con, role);

                    // wait for some time
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignored
                    }

                    // add permissions
                    try {
                        storage.addPermissionToRoleOrDoNothingIfExists_Transaction(con, role, permissions[0]);
                    } catch (UnknownRoleException e) {
                        throw new StorageQueryException(e);
                    }
                    storage.commitTransaction(con);
                    r1_success.set(true);
                    return null;
                });
            } catch (StorageQueryException | StorageTransactionLogicException e) {
                // should not come here
            }
        };

        Runnable r2 = () -> {
            // wait for some time so createNewRoleTransaction is run
            try {
                Thread.sleep(700);
            } catch (InterruptedException e) {
                // Ignore
            }
            // delete the newly created role
            try {
                storage.deleteRole(role);
                r2_success.set(true);
            } catch (StorageQueryException e) {
                // should not come here
            }
        };

        Thread thread1 = new Thread(r1);
        Thread thread2 = new Thread(r2);

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        // check that the addPermissionToRole_transaction and deleteRole correctly ran
        assertTrue(r1_success.get());
        assertTrue(r2_success.get());

        // check that the transaction in r1 runs once. This happens cause it's
        // running in serializable transaction isolation level. If we run it in
        // repeatable read, it will happen twice.
        assertEquals(1, numberOfIterations.get());

        // check that the role is created and the permission still exists
        assertTrue(storage.doesRoleExist(role));
        assertArrayEquals(storage.getPermissionsForRole(role), permissions);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // test that createNewRoleOrDoNothingIfExists_Transaction throws no error when role already exists
    @Test
    public void testCreateNewRoleOrDoNothingIfExists_TransactionResponses() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        // create a new role
        String role = "testRole";
        {
            boolean wasRoleCreated = UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);
            // check that a role was created
            assertTrue(wasRoleCreated);

        }
        // check that the role exists
        assertTrue(UserRoles.doesRoleExist(process.main, role));

        // check that createNewRole_transaction doesn't throw error, and no role was created
        {
            boolean wasRoleCreated = storage
                    .startTransaction(con -> storage.createNewRoleOrDoNothingIfExists_Transaction(con, role));
            assertFalse(wasRoleCreated);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // test addPermissionToRole_Transaction
    @Test
    public void testAddPermissionToRole_TransactionResponses() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        {
            // add permissions to a role that does not exist
            Exception error = null;
            try {
                storage.startTransaction(con -> {
                    try {
                        storage.addPermissionToRoleOrDoNothingIfExists_Transaction(con, "unknown_role",
                                "testPermission");
                    } catch (UnknownRoleException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }
            assertNotNull(error);
            assert (error instanceof UnknownRoleException);
        }

        {

            // crate a role with permissions
            String role = "role";
            String permission = "permission";
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, new String[] { permission });

            // add duplicate permissions

            Exception error = null;
            try {
                storage.startTransaction(con -> {
                    try {
                        storage.addPermissionToRoleOrDoNothingIfExists_Transaction(con, role, permission);
                    } catch (UnknownRoleException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }
            assertNull(error);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDoesRoleExistResponses() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        // call doesRoleExist on a role which doesn't exist
        String role = "role";
        assertFalse(storage.doesRoleExist(role));

        // create a role and call doesRoleExist
        {
            boolean wasRoleCreated = storage
                    .startTransaction(con -> storage.createNewRoleOrDoNothingIfExists_Transaction(con, role));

            // check that the role is created
            assertTrue(wasRoleCreated);
            assertTrue(storage.doesRoleExist(role));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetRolesResponses() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        // call getRoles when no roles exist
        String[] emptyRoles = storage.getRoles();

        assertEquals(0, emptyRoles.length);

        // create multiple roles and check if they are properly returned
        String[] createdRoles = new String[] { "role1", "role2" };
        storage.startTransaction(con -> {
            for (int i = 0; i < createdRoles.length; i++) {
                storage.createNewRoleOrDoNothingIfExists_Transaction(con, createdRoles[i]);
            }
            return null;
        });

        // check that the getRoles retrieved the correct roles
        String[] retrievedRoles = storage.getRoles();
        Utils.checkThatArraysAreEqual(createdRoles, retrievedRoles);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAssociatingAnUnknownRoleWithUser() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        Exception error = null;
        try {

            storage.addRoleToUser("userId", "unknownRole");
        } catch (Exception e) {
            error = e;
        }

        assertNotNull(error);
        assertTrue(error instanceof UnknownRoleException);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAssociatingRolesWithUser() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        // associate multiple roles with a user and check that the user actually has those roles

        // create multiple roles
        String[] roles = new String[] { "role1", "role2", "role3" };
        String userId = "userId";
        storage.startTransaction(con -> {
            for (String role : roles) {
                storage.createNewRoleOrDoNothingIfExists_Transaction(con, role);
            }
            storage.commitTransaction(con);
            return null;
        });

        // associate user with roles
        for (String role : roles) {
            storage.addRoleToUser(userId, role);
        }

        // check if user actually has the roles
        String[] userRoles = storage.getRolesForUser(userId);
        Utils.checkThatArraysAreEqual(roles, userRoles);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAssociatingTheSameRoleWithUserTwice() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        // associate multiple roles with a user and check that the user actually has those roles

        // create multiple roles
        String role = "role";
        String userId = "userId";
        storage.startTransaction(con -> {
            storage.createNewRoleOrDoNothingIfExists_Transaction(con, role);
            storage.commitTransaction(con);
            return null;
        });

        // associate user with roles
        storage.addRoleToUser(userId, role);

        // check if user actually has the role
        String[] userRoles = storage.getRolesForUser(userId);
        assertEquals(1, userRoles.length);
        assertEquals(role, userRoles[0]);

        // associate the role with the user again, should throw DuplicateUserRoleMappingException
        Exception error = null;
        try {
            storage.addRoleToUser(userId, role);
        } catch (Exception e) {
            error = e;
        }

        assertNotNull(error);
        assertTrue(error instanceof DuplicateUserRoleMappingException);

        // check that the user still has only one role
        String[] userRoles_2 = storage.getRolesForUser(userId);
        assertEquals(1, userRoles_2.length);
        assertEquals(role, userRoles_2[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRemovingAnUnUnknownRoleFromAUser() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        boolean response = storage
                .startTransaction(con -> storage.deleteRoleForUser_Transaction(con, "userId", "unknown_role"));

        assertFalse(response);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRemovingARoleFromAUser() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        String[] roles = new String[] { "role" };
        String userId = "userId";

        // create a role
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, roles[0], null);

        // add role to user
        UserRoles.addRoleToUser(process.main, userId, roles[0]);

        {
            // check that user has the roles
            String[] userRoles = storage.getRolesForUser(userId);
            Utils.checkThatArraysAreEqual(roles, userRoles);
        }

        {
            // remove the role from the user
            boolean response = storage
                    .startTransaction(con -> storage.deleteRoleForUser_Transaction(con, userId, roles[0]));
            assertTrue(response);

            // check that user does not have any roles
            String[] userRoles = storage.getRolesForUser(userId);
            assertEquals(0, userRoles.length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUsersForRoles() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        // create a role
        String role = "role";
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);

        // add role to multiple users
        String[] userIds = new String[] { "user1", "user2", "user3" };
        for (String userId : userIds) {
            UserRoles.addRoleToUser(process.main, userId, role);
        }

        String[] userIdsWithSameRole = storage.getUsersForRole(role);

        // check that the users you have the role is correct
        Utils.checkThatArraysAreEqual(userIds, userIdsWithSameRole);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUserIdsForRoleWhichHasNoUsers() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        // create a role
        String role = "role";
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);

        String[] userIdsWithRole = storage.getUsersForRole(role);

        assertEquals(0, userIdsWithRole.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUserIdsForAnUnknownRole() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        String[] userIdsWithRole = storage.getUsersForRole("unknownRole");

        assertEquals(0, userIdsWithRole.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
