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
import io.supertokens.pluginInterface.userroles.exception.UnknownRoleException;
import io.supertokens.pluginInterface.userroles.sqlStorage.UserRolesSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.userroles.UserRoles;
import jdk.jshell.execution.Util;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.Arrays;

import static io.supertokens.test.Utils.checkThatArraysAreEqual;
import static org.junit.Assert.*;

public class UserRolesTest {
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

    // createNewRoleOrModifyItsPermissions tests
    // Call setRole with only role and check it works. Call it again the same role and check it returns OK
    @Test
    public void testCreatingTheSameRoleTwice() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);
        String role = "role";
        String[] permissions = new String[] { "permission" };
        // create a new role
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);

        // check if role is created
        assertTrue(storage.doesRoleExist(role));
        // check if permissions are created
        assertArrayEquals(storage.getPermissionsForRole(role), permissions);

        // create the same role again, should not throw an exception
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);

        // check that roles and permissions still exist
        assertTrue(storage.doesRoleExist(role));
        checkThatArraysAreEqual(permissions, storage.getPermissionsForRole(role));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Call setRole with a role + permissions and check it works. Call it again with existing set of permissions, but
    // one added, and check it returns OK + that the new permission was added.
    @Test
    public void testAddingPermissionsToARoleWithExistingPermissions() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        String[] oldPermissions = new String[] { "permission1", "permission2" };
        String role = "role";

        {
            // create a new role
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, oldPermissions);

            // check that role and permissions were created

            // check if role is created
            assertTrue(storage.doesRoleExist(role));

            // check if permissions are created
            String[] createdPermissions_1 = storage.getPermissionsForRole(role);
            checkThatArraysAreEqual(oldPermissions, createdPermissions_1);
        }

        {
            // modify role with a new permission
            String[] newPermissions = new String[] { "permission1", "permission2", "permission3" };

            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, newPermissions);

            String[] createdPermissions_2 = storage.getPermissionsForRole(role);
            Arrays.sort(newPermissions);
            Arrays.sort(createdPermissions_2);

            // check if the new permission is added, sort arrays so positions of elements are the same
            assertArrayEquals(newPermissions, createdPermissions_2);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Call setRole with a role and null (or empty array) permissions, it should work as expected.
    @Test
    public void createRoleWithNullOrEmptyPermissions() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        {
            // create a role with null permissions
            String role = "role";
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);

            // check that role and permissions were created

            // check if role is created
            assertTrue(storage.doesRoleExist(role));
            // check that no permissions exist for the role
            assertEquals(0, storage.getPermissionsForRole(role).length);
        }

        {
            // create role with empty array permissions
            String role = "role2";
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, new String[] {});
            // check if role is created
            assertTrue(storage.doesRoleExist(role));
            // check that no permissions exist for the role
            assertEquals(0, storage.getPermissionsForRole(role).length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Call setRole with a role + permissions and check it works. Then call it again with just one new permission
    // (permissions array is just one item), and check that new permission was added.

    @Test
    public void testCreatingARoleWithPermissionsAndAddingOneMorePermission() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);
        String role = "role";

        {
            // create a new role
            String[] oldPermissions = new String[] { "permission1", "permission2" };

            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, oldPermissions);
            // check that role and permissions were created

            // check if role is created
            assertTrue(storage.doesRoleExist(role));

            // retrieve permissions for role
            String[] createdPermissions = storage.getPermissionsForRole(role);

            // check that permissions are the same
            checkThatArraysAreEqual(oldPermissions, createdPermissions);
        }

        {
            // add a new permission to the role
            String[] newPermission = new String[] { "permission3" };

            // add additional permission to role
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, newPermission);

            // check that the role still exists
            assertTrue(storage.doesRoleExist(role));

            // retrieve permissions for role
            String[] createdPermissions = storage.getPermissionsForRole(role);

            // check that newly added permission is added
            String[] allPermissions = new String[] { "permission1", "permission2", "permission3" };
            checkThatArraysAreEqual(allPermissions, createdPermissions);

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAddRoleToUserResponses() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        String[] roles = new String[] { "role" };
        String userId = "userId";
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(process.main);

        // assign an unknown role to a user, it should throw UNKNOWN_ROLE_EXCEPTION
        {
            Exception error = null;
            try {

                UserRoles.addRoleToUser(process.main, userId, "unknown_role");
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assertTrue(error instanceof UnknownRoleException);
        }

        // create a role and assign the role to a user, check that the user has the role
        {
            // create role
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, roles[0], null);
            // assign role to user
            UserRoles.addRoleToUser(process.main, userId, roles[0]);

            // check that user actually has the role
            String[] userRoles = storage.getRolesForUser(userId);
            Utils.checkThatArraysAreEqual(roles, userRoles);
        }

        // assign the same role to the user again, it should not throw an exception
        {
            // assign the role to the user again
            UserRoles.addRoleToUser(process.main, userId, roles[0]);

            // check that the user still has the same role/ no additional role has been added
            String[] userRoles = storage.getRolesForUser(userId);
            Utils.checkThatArraysAreEqual(roles, userRoles);

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
