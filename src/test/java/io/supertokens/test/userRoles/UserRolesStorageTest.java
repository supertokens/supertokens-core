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

        // !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")
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
        AtomicBoolean r1_success = new AtomicBoolean(true);
        AtomicBoolean r2_success = new AtomicBoolean(true);
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
                        // should not come here
                    }

                    // add permissions
                    try {
                        storage.addPermissionToRole_Transaction(con, role, permissions[0]);
                    } catch (UnknownRoleException e) {
                        // should not come here
                        r1_success.set(false);
                    }

                    storage.commitTransaction(con);
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
            } catch (StorageQueryException e) {
                // should not come here
                r2_success.set(false);
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

        // check that the transaction in r1 runs once
        assertEquals(1, numberOfIterations.get());

        // check that the role is created and the permission still exists
        assertTrue(storage.doesRoleExist(role));
        assertArrayEquals(storage.getPermissionsForRole(role), permissions);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // test that createNewRole_Transaction throws no error when role already exists
    @Test
    public void testCreateNewRole_TransactionResponses() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        // create a new role
        String role = "testRole";
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);

        // check that the role exists
        assertTrue(UserRoles.doesRoleExist(process.main, role));

        // check that createNewRole_transaction doesn't throw error
        {
            storage.startTransaction(con -> {
                storage.createNewRoleOrDoNothingIfExists_Transaction(con, role);
                return null;
            });
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
                        storage.addPermissionToRole_Transaction(con, "unknown_role", "testPermission");
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
                        storage.addPermissionToRole_Transaction(con, role, permission);
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

        // crate a role and call doesRoleExist
        {
            storage.startTransaction(con -> {
                storage.createNewRoleOrDoNothingIfExists_Transaction(con, role);
                return null;
            });

            // check that the role is created
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
}
