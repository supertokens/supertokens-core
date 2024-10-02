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
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
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
        String[] args = {"../"};

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

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);
        AtomicBoolean r1_success = new AtomicBoolean(false);
        AtomicBoolean r2_success = new AtomicBoolean(false);

        Runnable r1 = () -> {

            try {
                storage.startTransaction(con -> {
                    // wait for some time
                    storage.doesRoleExist_Transaction(new AppIdentifier(null, null), con, role);

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignored
                    }

                    // add permissions
                    boolean wasRoleRemovedFromUser = storage.deleteRoleForUser_Transaction(
                            new TenantIdentifier(null, null, null), con, userId, role);

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
                boolean wasRoleDeleted = storage.deleteRole(new AppIdentifier(null, null), role);
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
        assertEquals(0, storage.getRolesForUser(new TenantIdentifier(null, null, null), userId).length);

        // check that role was actually deleted
        assertFalse(storage.doesRoleExist(new AppIdentifier(null, null), role));

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
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // we only want to run this test for MySQL and Postgres since the behaviour for SQLite is different
        if (Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
            return;
        }

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // thread 1: start transaction -> call createNewRole_Transaction -> wait... ->
        // call addPermissionToRole_Transaction -> commit
        String role = "role";
        String[] permissions = new String[]{"permission"};
        AtomicInteger numberOfIterations = new AtomicInteger(0);
        AtomicBoolean r1_success = new AtomicBoolean(false);
        AtomicBoolean r2_success = new AtomicBoolean(false);
        Runnable r1 = () -> {

            try {
                storage.startTransaction(con -> {
                    numberOfIterations.incrementAndGet();
                    // create a new Role
                    try {
                        storage.createNewRoleOrDoNothingIfExists_Transaction(new AppIdentifier(null, null), con,
                                role);
                    } catch (TenantOrAppNotFoundException e) {
                        throw new IllegalStateException(e);
                    }

                    // wait for some time
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignored
                    }

                    // add permissions
                    try {
                        storage.addPermissionToRoleOrDoNothingIfExists_Transaction(new AppIdentifier(null, null), con,
                                role, permissions[0]);
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
                boolean wasRoleDeleted = storage.deleteAllUserRoleAssociationsForRole(new AppIdentifier(null, null),
                        role);
                wasRoleDeleted = storage.deleteRole(new AppIdentifier(null, null), role) || wasRoleDeleted;
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

        // check that either case passes:
        boolean useCase1;
        boolean useCase2;

        {
            // 1: The role and permissions still exist
            // check that the role is created and the permission still exists
            String[] retrievedPermissions = storage.getPermissionsForRole(new AppIdentifier(null, null), role);
            useCase1 = (storage.doesRoleExist(new AppIdentifier(null, null), role) &&
                    retrievedPermissions[0].equals(permissions[0])
                    && retrievedPermissions.length == 1);
        }

        {
            // 2. The role and permissions have been deleted, no mappings for the role-permission exist
            String[] retrievedPermissions = storage.getPermissionsForRole(new AppIdentifier(null, null), role);
            useCase2 = (!storage.doesRoleExist(new AppIdentifier(null, null), role) &&
                    retrievedPermissions.length == 0);

        }

        assertTrue(useCase1 || useCase2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // test that createNewRoleOrDoNothingIfExists_Transaction throws no error when role already exists
    @Test
    public void testCreateNewRoleOrDoNothingIfExists_TransactionResponses() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

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
                    .startTransaction(con -> {
                        try {
                            return storage.createNewRoleOrDoNothingIfExists_Transaction(
                                    new AppIdentifier(null, null), con, role);
                        } catch (TenantOrAppNotFoundException e) {
                            throw new IllegalStateException(e);
                        }
                    });
            assertFalse(wasRoleCreated);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // test addPermissionToRole_Transaction
    @Test
    public void testAddPermissionToRole_TransactionResponses() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        {
            // add permissions to a role that does not exist
            Exception error = null;
            try {
                storage.startTransaction(con -> {
                    try {
                        storage.addPermissionToRoleOrDoNothingIfExists_Transaction(new AppIdentifier(null, null), con,
                                "unknown_role",
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
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, new String[]{permission});

            // add duplicate permissions

            Exception error = null;
            try {
                storage.startTransaction(con -> {
                    try {
                        storage.addPermissionToRoleOrDoNothingIfExists_Transaction(new AppIdentifier(null, null), con,
                                role, permission);
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
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // call doesRoleExist on a role which doesn't exist
        String role = "role";
        assertFalse(storage.doesRoleExist(new AppIdentifier(null, null), role));

        // create a role and call doesRoleExist
        {
            boolean wasRoleCreated = storage
                    .startTransaction(con -> {
                        try {
                            return storage.createNewRoleOrDoNothingIfExists_Transaction(
                                    new AppIdentifier(null, null), con, role);
                        } catch (TenantOrAppNotFoundException e) {
                            throw new IllegalStateException(e);
                        }
                    });

            // check that the role is created
            assertTrue(wasRoleCreated);
            assertTrue(storage.doesRoleExist(new AppIdentifier(null, null), role));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetRolesResponses() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // call getRoles when no roles exist
        String[] emptyRoles = storage.getRoles(new AppIdentifier(null, null));

        assertEquals(0, emptyRoles.length);

        // create multiple roles and check if they are properly returned
        String[] createdRoles = new String[]{"role1", "role2"};
        storage.startTransaction(con -> {
            for (int i = 0; i < createdRoles.length; i++) {
                try {
                    storage.createNewRoleOrDoNothingIfExists_Transaction(new AppIdentifier(null, null), con,
                            createdRoles[i]);
                } catch (TenantOrAppNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
            return null;
        });

        // check that the getRoles retrieved the correct roles
        String[] retrievedRoles = storage.getRoles(new AppIdentifier(null, null));
        Utils.checkThatArraysAreEqual(createdRoles, retrievedRoles);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAssociatingAnUnknownRoleWithUser() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        Exception error = null;
        try {
            UserRoles.addRoleToUser(
                    process.getProcess(), new TenantIdentifier(null, null, null),
                    StorageLayer.getBaseStorage(process.getProcess()), "userId", "unknownRole");
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
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // associate multiple roles with a user and check that the user actually has those roles

        // create multiple roles
        String[] roles = new String[]{"role1", "role2", "role3"};
        String userId = "userId";
        storage.startTransaction(con -> {
            for (String role : roles) {
                try {
                    storage.createNewRoleOrDoNothingIfExists_Transaction(new AppIdentifier(null, null), con, role);
                } catch (TenantOrAppNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
            storage.commitTransaction(con);
            return null;
        });

        // associate user with roles
        for (String role : roles) {
            storage.addRoleToUser(new TenantIdentifier(null, null, null), userId, role);
        }

        // check if user actually has the roles
        String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
        Utils.checkThatArraysAreEqual(roles, userRoles);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAssociatingTheSameRoleWithUserTwice() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // associate multiple roles with a user and check that the user actually has those roles

        // create multiple roles
        String role = "role";
        String userId = "userId";
        storage.startTransaction(con -> {
            try {
                storage.createNewRoleOrDoNothingIfExists_Transaction(new AppIdentifier(null, null), con, role);
            } catch (TenantOrAppNotFoundException e) {
                throw new IllegalStateException(e);
            }
            storage.commitTransaction(con);
            return null;
        });

        // associate user with roles
        storage.addRoleToUser(new TenantIdentifier(null, null, null), userId, role);

        // check if user actually has the role
        String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
        assertEquals(1, userRoles.length);
        assertEquals(role, userRoles[0]);

        // associate the role with the user again, should throw DuplicateUserRoleMappingException
        Exception error = null;
        try {
            storage.addRoleToUser(new TenantIdentifier(null, null, null), userId, role);
        } catch (Exception e) {
            error = e;
        }

        assertNotNull(error);
        assertTrue(error instanceof DuplicateUserRoleMappingException);

        // check that the user still has only one role
        String[] userRoles_2 = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
        assertEquals(1, userRoles_2.length);
        assertEquals(role, userRoles_2[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRemovingAnUnUnknownRoleFromAUser() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        boolean response = storage
                .startTransaction(
                        con -> storage.deleteRoleForUser_Transaction(new TenantIdentifier(null, null, null), con,
                                "userId", "unknown_role"));

        assertFalse(response);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRemovingARoleFromAUser() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        String[] roles = new String[]{"role"};
        String userId = "userId";

        // create a role
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, roles[0], null);

        // add role to user
        UserRoles.addRoleToUser(process.main, userId, roles[0]);

        {
            // check that user has the roles
            String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
            Utils.checkThatArraysAreEqual(roles, userRoles);
        }

        {
            // remove the role from the user
            boolean response = storage
                    .startTransaction(
                            con -> storage.deleteRoleForUser_Transaction(new TenantIdentifier(null, null, null), con,
                                    userId, roles[0]));
            assertTrue(response);

            // check that user does not have any roles
            String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
            assertEquals(0, userRoles.length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUsersForRoles() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // create a role
        String role = "role";
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);

        // add role to multiple users
        String[] userIds = new String[]{"user1", "user2", "user3"};
        for (String userId : userIds) {
            UserRoles.addRoleToUser(process.main, userId, role);
        }

        String[] userIdsWithSameRole = storage.getUsersForRole(new TenantIdentifier(null, null, null), role);

        // check that the users you have retrieved is correct
        Utils.checkThatArraysAreEqual(userIds, userIdsWithSameRole);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingPermissionsForARole() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // create a role with permissions
        String role = "role";
        String[] permissions = new String[]{"permission1", "permission2", "permission3"};
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);

        // retrieve permissions and check that the permissions retrieved are the same as those set
        String[] retrievedPermissions = storage.getPermissionsForRole(new AppIdentifier(null, null), role);
        Utils.checkThatArraysAreEqual(permissions, retrievedPermissions);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingAPermissionFromARole() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // create a role with permissions
        String role = "role";
        String[] permissions = new String[]{"permission1", "permission2", "permission3"};
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);

        // delete a permission from the role

        boolean wasPermissionDeleted = storage
                .startTransaction(
                        con -> storage.deletePermissionForRole_Transaction(new AppIdentifier(null, null), con, role,
                                "permission2"));

        assertTrue(wasPermissionDeleted);

        String[] newPermissions = new String[]{"permission1", "permission3"};

        // retrieve permissions for a role and check that the correct permissions are retrieved
        String[] retrievedPermissions = UserRoles.getPermissionsForRole(process.main, role);
        Utils.checkThatArraysAreEqual(newPermissions, retrievedPermissions);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingAllPermissionFromARole() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // create a role with permissions
        String role = "role";
        String[] permissions = new String[]{"permission1", "permission2", "permission3"};
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);

        // delete all permissions from the role

        int numberOfPermissionsDeleted = storage
                .startTransaction(
                        con -> storage.deleteAllPermissionsForRole_Transaction(new AppIdentifier(null, null), con,
                                role));

        assertEquals(permissions.length, numberOfPermissionsDeleted);

        // retrieve permissions for a role and check that no permissions are returned
        String[] retrievedPermissions = UserRoles.getPermissionsForRole(process.main, role);
        assertEquals(0, retrievedPermissions.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingRolesForAPermission() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // create two roles, assign [permission1] to role1 and [permission1, permission2] to role2
        String[] roles = new String[]{"role1", "role2"};
        String permission1 = "permission1";
        String permission2 = "permission2";

        // create role1 with permission [permission1]
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, roles[0], new String[]{permission1});
        // create role2 with permissions [permission1, permission2]
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, roles[1],
                new String[]{permission1, permission2});

        {
            // check that role1 and role2 have permission1
            String[] retrievedRoles = storage.getRolesThatHavePermission(new AppIdentifier(null, null), permission1);
            assertEquals(2, retrievedRoles.length);
            Utils.checkThatArraysAreEqual(roles, retrievedRoles);
        }
        {
            // check that role2 has permission2
            String[] retrievedRoles = storage.getRolesThatHavePermission(new AppIdentifier(null, null), permission2);
            assertEquals(1, retrievedRoles.length);
            assertEquals(roles[1], retrievedRoles[0]);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingRoleResponses() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        String role = "role";

        {
            // delete a role which exists

            // create a role
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);

            // delete role
            boolean didRoleExist = storage.deleteAllUserRoleAssociationsForRole(new AppIdentifier(null, null), role);
            assertTrue(didRoleExist = storage.deleteRole(new AppIdentifier(null, null), role) || didRoleExist);
            assertTrue(didRoleExist);

            // check that role doesnt exist
            assertFalse(UserRoles.doesRoleExist(process.main, role));
        }
        {
            // delete a role which doesnt exist
            boolean didRoleExist = storage.deleteAllUserRoleAssociationsForRole(new AppIdentifier(null, null), role);
            didRoleExist = storage.deleteRole(new AppIdentifier(null, null), role) || didRoleExist;
            assertFalse(didRoleExist);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGettingAllCreatedRoles() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // create roles
        String[] roles = new String[]{"role1", "role2", "role3"};
        for (String role : roles) {
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);
        }

        // retrieve all role and check for correct output
        {
            String[] retrievedRoles = storage.getRoles(new AppIdentifier(null, null));
            Utils.checkThatArraysAreEqual(roles, retrievedRoles);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingAllRolesForAUser() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // create multiple roles and assign them to a user
        String userId = "userId";
        String[] roles = new String[]{"role1", "role2", "role3"};

        for (String role : roles) {
            // create role
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);
            // assign role to user
            UserRoles.addRoleToUser(process.main, userId, role);
        }

        {
            // check that user has the roles
            String[] retrievedRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
            Utils.checkThatArraysAreEqual(roles, retrievedRoles);
        }

        // delete all roles for the user
        int numberOfRolesDeleted = storage.deleteAllRolesForUser(new TenantIdentifier(null, null, null), userId);
        assertEquals(roles.length, numberOfRolesDeleted);

        // check that the user does not have any roles
        {
            String[] retrievedRoles = UserRoles.getRolesForUser(process.main, userId);
            assertEquals(0, retrievedRoles.length);
        }

        {
            // check that no roles have been deleted
            String[] retrievedRoles = UserRoles.getRoles(process.main);
            Utils.checkThatArraysAreEqual(roles, retrievedRoles);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
