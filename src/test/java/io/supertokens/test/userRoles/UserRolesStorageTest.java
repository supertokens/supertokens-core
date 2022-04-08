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
import io.supertokens.pluginInterface.userroles.exception.DuplicateRoleException;
import io.supertokens.pluginInterface.userroles.exception.DuplicateRolePermissionMappingException;
import io.supertokens.pluginInterface.userroles.exception.UnknownRoleException;
import io.supertokens.pluginInterface.userroles.sqlStorage.UserRolesSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.userroles.UserRoles;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.Arrays;

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
     * addPermissionToRole_Transaction -> commit (should cause a retry of the transaction). In thread 2: Wait for thread
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

        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        // thread 1: start transaction -> call createNewRole_Transaction -> wait... ->
        // call addPermissionToRole_Transaction -> commit
        String role = "role";
        String[] permissions = new String[] { "permission" };
        Runnable r1 = () -> {

            try {
                storage.startTransaction(con -> {

                    // create a new Role
                    try {
                        storage.createNewRole_Transaction(con, role);
                    } catch (DuplicateRoleException e) {
                        // ignore exception, should not come here
                    }

                    // wait for some time
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // should not come here
                        fail();
                    }

                    // add permissions
                    try {
                        storage.addPermissionToRole_Transaction(con, role, permissions[0]);
                    } catch (UnknownRoleException | DuplicateRolePermissionMappingException e) {
                        // should not come here
                        fail();
                    }

                    storage.commitTransaction(con);
                    return null;
                });
            } catch (StorageQueryException | StorageTransactionLogicException e) {
                // should not come here
                fail();
            }
        };

        Runnable r2 = () -> {
            Exception error = null;
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
                error = e;
            }
            assertNotNull(error);
            assertTrue(error.getMessage()
                    .contains("[SQLITE_LOCKED]  A table in the database is locked (database table is locked)"));
        };

        Thread thread1 = new Thread(r1);
        Thread thread2 = new Thread(r2);

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        // check that the role is created and the permission still exists
        assertTrue(storage.doesRoleExist(role));
        assertArrayEquals(storage.getPermissionsForRole(role), permissions);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // test that createNewRole_Transaction throws DuplicateRoleException when a role already exists
    @Test
    public void testThatCreateNewRole_TransactionResponses() throws Exception {
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

        // check that createNewRole_transaction throws DuplicateRoleException with the same role
        {
            Exception error = null;
            try {
                storage.startTransaction(con -> {
                    try {
                        storage.createNewRole_Transaction(con, role);
                    } catch (DuplicateRoleException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }
            assertNotNull(error);
            assert (error instanceof DuplicateRoleException);
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
                    } catch (DuplicateRolePermissionMappingException | UnknownRoleException e) {
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
                    } catch (DuplicateRolePermissionMappingException | UnknownRoleException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                    return null;
                });
            } catch (StorageTransactionLogicException e) {
                error = e.actualException;
            }
            assertNotNull(error);
            assert (error instanceof DuplicateRolePermissionMappingException);
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
                try {
                    storage.createNewRole_Transaction(con, role);
                } catch (DuplicateRoleException e) {
                    // should not come here
                    fail();
                }
                return null;
            });

            // check that the role is created
            assertTrue(storage.doesRoleExist(role));
        }
    }
}
