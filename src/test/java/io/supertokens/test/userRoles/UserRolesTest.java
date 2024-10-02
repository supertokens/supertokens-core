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
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
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
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);
        String role = "role";
        String[] permissions = new String[]{"permission"};

        {
            // create a new role
            boolean wasRoleCreated = UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);

            // check if role is created
            assertTrue(wasRoleCreated);
            assertTrue(storage.doesRoleExist(new AppIdentifier(null, null), role));

            // check if permissions are created
            assertArrayEquals(storage.getPermissionsForRole(new AppIdentifier(null, null), role), permissions);

        }

        {
            // create the same role again, should not throw an exception and no new role should be created
            boolean wasRoleCreated = UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);
            assertFalse(wasRoleCreated);
            // check that roles and permissions still exist
            assertTrue(storage.doesRoleExist(new AppIdentifier(null, null), role));
            checkThatArraysAreEqual(permissions, storage.getPermissionsForRole(new AppIdentifier(null, null), role));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Call setRole with a role + permissions and check it works. Call it again with existing set of permissions, but
    // one added, and check it returns OK + that the new permission was added.
    @Test
    public void testAddingPermissionsToARoleWithExistingPermissions() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        String[] oldPermissions = new String[]{"permission1", "permission2"};
        String role = "role";

        {
            // create a new role
            boolean wasRoleCreated = UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, oldPermissions);
            assertTrue(wasRoleCreated);

            // check that role and permissions were created

            // check if role is created
            assertTrue(storage.doesRoleExist(new AppIdentifier(null, null), role));

            // check if permissions are created
            String[] createdPermissions_1 = storage.getPermissionsForRole(new AppIdentifier(null, null), role);
            checkThatArraysAreEqual(oldPermissions, createdPermissions_1);
        }

        {
            // modify role with a new permission
            String[] newPermissions = new String[]{"permission1", "permission2", "permission3"};

            boolean wasRoleCreated = UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, newPermissions);
            // since only permissions were modified and no role was created, this should be false
            assertFalse(wasRoleCreated);

            String[] createdPermissions_2 = storage.getPermissionsForRole(new AppIdentifier(null, null), role);
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
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        {
            // create a role with null permissions
            String role = "role";
            boolean wasRoleCreated = UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);

            // check that role and permissions were created

            // check if role is created
            assertTrue(wasRoleCreated);
            assertTrue(storage.doesRoleExist(new AppIdentifier(null, null), role));
            // check that no permissions exist for the role
            assertEquals(0, storage.getPermissionsForRole(new AppIdentifier(null, null), role).length);
        }

        {
            // create role with empty array permissions
            String role = "role2";
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, new String[]{});
            // check if role is created
            assertTrue(storage.doesRoleExist(new AppIdentifier(null, null), role));
            // check that no permissions exist for the role
            assertEquals(0, storage.getPermissionsForRole(new AppIdentifier(null, null), role).length);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Call setRole with a role + permissions and check it works. Then call it again with just one new permission
    // (permissions array is just one item), and check that new permission was added.

    @Test
    public void testCreatingARoleWithPermissionsAndAddingOneMorePermission() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);
        String role = "role";

        {
            // create a new role
            String[] oldPermissions = new String[]{"permission1", "permission2"};

            boolean wasRoleCreated = UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, oldPermissions);
            // check that role and permissions were created

            // check if role is created
            assertTrue(wasRoleCreated);
            assertTrue(storage.doesRoleExist(new AppIdentifier(null, null), role));

            // retrieve permissions for role
            String[] createdPermissions = storage.getPermissionsForRole(new AppIdentifier(null, null), role);

            // check that permissions are the same
            checkThatArraysAreEqual(oldPermissions, createdPermissions);
        }

        {
            // add a new permission to the role
            String[] newPermission = new String[]{"permission3"};

            // add additional permission to role
            boolean wasRoleCreated = UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, newPermission);
            assertFalse(wasRoleCreated);

            // check that the role still exists
            assertTrue(storage.doesRoleExist(new AppIdentifier(null, null), role));

            // retrieve permissions for role
            String[] createdPermissions = storage.getPermissionsForRole(new AppIdentifier(null, null), role);

            // check that newly added permission is added
            String[] allPermissions = new String[]{"permission1", "permission2", "permission3"};
            checkThatArraysAreEqual(allPermissions, createdPermissions);

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAddRoleToUserResponses() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        String[] roles = new String[]{"role"};
        String userId = "userId";
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

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
            boolean wasRoleAddedToUser = UserRoles.addRoleToUser(process.main, userId, roles[0]);
            assertTrue(wasRoleAddedToUser);

            // check that the user actually has the role
            String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
            Utils.checkThatArraysAreEqual(roles, userRoles);
        }

        // assign the same role to the user again, it should not be added and not throw an exception
        {
            // assign the role to the user again
            boolean wasRoleAddedToUser = UserRoles.addRoleToUser(process.main, userId, roles[0]);
            assertFalse(wasRoleAddedToUser);

            // check that the user still has the same role/ no additional role has been added
            String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
            Utils.checkThatArraysAreEqual(roles, userRoles);

        }

        // assign another role to the user, and check that the user has 2 roles
        {
            String[] newRoles = new String[]{"role", "role2"};

            // create another role
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, newRoles[1], null);
            // assign role to user and check that the role was added
            boolean wasRoleAddedToUser = UserRoles.addRoleToUser(process.main, userId, newRoles[1]);
            assertTrue(wasRoleAddedToUser);

            // check that user has two roles
            String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
            Utils.checkThatArraysAreEqual(newRoles, userRoles);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRemovingAnUnknownRoleFromAUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Exception error = null;
        try {
            UserRoles.removeUserRole(process.main, "userId", "unknown_role");
        } catch (Exception e) {
            error = e;
        }
        assertNotNull(error);
        assertTrue(error instanceof UnknownRoleException);

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

        String userId = "userId";
        String[] roles = new String[]{"role"};
        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // create a role and assign the role to a user
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, roles[0], null);
        UserRoles.addRoleToUser(process.main, userId, roles[0]);

        {
            // check that the user has roles
            String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
            Utils.checkThatArraysAreEqual(roles, userRoles);
        }

        {
            // remove the roles from the user
            boolean didUserHaveRole = UserRoles.removeUserRole(process.main, userId, roles[0]);
            assertTrue(didUserHaveRole);

            // check that the user has no roles
            String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
            assertEquals(0, userRoles.length);
        }
        {
            // remove a role from a user where the user does not have the role
            boolean didUserHaveRole = UserRoles.removeUserRole(process.main, userId, roles[0]);
            assertFalse(didUserHaveRole);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRemovingARoleFromAUserWhoHasMultipleRoles() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        String[] roles = new String[]{"role1", "role2", "role3"};
        String userId = "userId";

        // create multiple roles and assign them to a user
        for (String role : roles) {
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);
            UserRoles.addRoleToUser(process.main, userId, role);
        }

        {
            // check that the user actually has the roles
            String[] userRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
            Utils.checkThatArraysAreEqual(roles, userRoles);
        }

        // remove a role from the user
        boolean didUserHaveRole = UserRoles.removeUserRole(process.main, userId, "role1");
        assertTrue(didUserHaveRole);

        {
            String[] currentUserRoles = new String[]{"role2", "role3"};
            String[] retrievedUserRoles = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);

            // check that the user has the correct roles
            Utils.checkThatArraysAreEqual(currentUserRoles, retrievedUserRoles);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testRetrievingRolesForUser() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // create multiple roles and add them to a user
        String[] roles = new String[]{"role1", "role2", "role3"};
        String userId = "userId";

        for (String role : roles) {
            // create role
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);
            // add role to user
            UserRoles.addRoleToUser(process.main, userId, role);
        }

        // retrieve roles and check that user has roles
        String[] userRoles = UserRoles.getRolesForUser(process.main, userId);
        Utils.checkThatArraysAreEqual(roles, userRoles);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingRolesForUserWithNoRoles() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "userId";

        // retrieve roles and check that user has no roles
        String[] userRoles = UserRoles.getRolesForUser(process.main, userId);
        assertEquals(0, userRoles.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUsersForRole() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a role
        String role = "role";
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);

        // add role to users
        String[] userIds = new String[]{"user1", "user2", "user3"};
        for (String userId : userIds) {
            UserRoles.addRoleToUser(process.main, userId, role);
        }

        // retrieve users with the input role and check that it is correct
        String[] usersWithTheSameRole = UserRoles.getUsersForRole(process.main, role);
        Utils.checkThatArraysAreEqual(userIds, usersWithTheSameRole);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUsersForAnUnknownRole() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // retrieve users with an unknown role
        Exception error = null;
        try {
            UserRoles.getUsersForRole(process.main, "unknownRole");
        } catch (Exception e) {
            error = e;
        }

        assertNotNull(error);
        assertTrue(error instanceof UnknownRoleException);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUserIdsForRoleWhichHasNoUsers() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a role
        String role = "role";
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);

        String[] userIdsWithRole = UserRoles.getUsersForRole(process.main, role);

        assertEquals(0, userIdsWithRole.length);

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

        // create a role
        String role = "role";
        String[] permissions = new String[]{"permission1", "permission2", "permission3"};
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);

        // check that role has permissions
        String[] retrievedPermissions = UserRoles.getPermissionsForRole(process.main, role);
        Utils.checkThatArraysAreEqual(permissions, retrievedPermissions);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingPermissionsForARoleWithNoPermissions() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a role
        String role = "role";
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);

        String[] retrievedPermissions = UserRoles.getPermissionsForRole(process.main, role);
        assertEquals(0, retrievedPermissions.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingPermissionsWithUnknownRole() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Exception error = null;
        try {
            UserRoles.getPermissionsForRole(process.main, "unknownRole");
        } catch (Exception e) {
            error = e;
        }

        assertNotNull(error);
        assertTrue(error instanceof UnknownRoleException);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingPermissionsFromARole() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a role with permissions
        String role = "role";
        String[] permissions = new String[]{"permission1", "permission2", "permission3"};
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);

        // remove permissions from role
        String[] permissionsToRemove = new String[]{"permission1", "permission2"};
        UserRoles.deletePermissionsFromRole(process.main, role, permissionsToRemove);

        // check that permissions have been removed
        String[] retrievedPermissions = UserRoles.getPermissionsForRole(process.main, role);
        assertEquals(1, retrievedPermissions.length);
        assertEquals("permission3", retrievedPermissions[0]);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingAllPermissionsFromARole() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a role with permissions
        String role = "role";
        String[] permissions = new String[]{"permission1", "permission2", "permission3"};
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);

        // remove all permissions from role
        UserRoles.deletePermissionsFromRole(process.main, role, null);

        // check that permissions have been removed
        String[] retrievedPermissions = UserRoles.getPermissionsForRole(process.main, role);
        assertEquals(0, retrievedPermissions.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingAPermissionWhichDoesNotExistFromARole() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a role with permissions
        String role = "role";
        String[] permissions = new String[]{"permission1", "permission2", "permission3"};
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);

        // remove all permissions from role
        String[] permissionToRemove = new String[]{"permission4"};
        UserRoles.deletePermissionsFromRole(process.main, role, permissionToRemove);

        // check that no permissions have been removed
        String[] retrievedPermissions = UserRoles.getPermissionsForRole(process.main, role);
        Utils.checkThatArraysAreEqual(permissions, retrievedPermissions);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingPermissionsFromAnUnknownRole() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // remove permissions from an unknown role
        Exception error = null;
        try {
            UserRoles.deletePermissionsFromRole(process.main, "unknownRole", null);
        } catch (Exception e) {
            error = e;
        }
        assertNotNull(error);
        assertTrue(error instanceof UnknownRoleException);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingAPermissionsFromARoleWithAnEmptyArray() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a role with permissions
        String role = "role";
        String[] permissions = new String[]{"permission1", "permission2", "permission3"};
        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);

        // remove all permissions from role
        UserRoles.deletePermissionsFromRole(process.main, role, new String[]{});

        // check that no permissions have been removed
        String[] retrievedPermissions = UserRoles.getPermissionsForRole(process.main, role);
        Utils.checkThatArraysAreEqual(permissions, retrievedPermissions);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingRolesForPermissions() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

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
            String[] retrievedRoles = UserRoles.getRolesThatHavePermission(process.main, permission1);
            Utils.checkThatArraysAreEqual(roles, retrievedRoles);
        }

        {
            String[] retrievedRoles = UserRoles.getRolesThatHavePermission(process.main, permission2);
            assertEquals(1, retrievedRoles.length);
            assertEquals(roles[1], retrievedRoles[0]);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingRolesForAnUnknownPermission() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // check that no roles are returned
        String[] retrievedRoles = UserRoles.getRolesThatHavePermission(process.main, "unknownPermission");
        assertEquals(0, retrievedRoles.length);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingARole() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserRolesSQLStorage storage = (UserRolesSQLStorage) StorageLayer.getStorage(process.main);

        // create a role with permissions and assign it to a user
        String role = "role";
        String userId = "userId";
        String[] permissions = new String[]{"permission1", "permission2", "permission3"};

        UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, permissions);
        UserRoles.addRoleToUser(process.main, userId, role);

        // delete role

        boolean didRoleExist = UserRoles.deleteRole(process.main, role);
        assertTrue(didRoleExist);

        // retrieving permission should throw unknownRoleException
        Exception error = null;
        try {
            UserRoles.getPermissionsForRole(process.main, role);
        } catch (Exception e) {
            error = e;
        }
        assertNotNull(error);
        assertTrue(error instanceof UnknownRoleException);

        // check that the role-permission mapping doesnt exist in the db
        String[] retrievedPermissions = storage.getPermissionsForRole(new AppIdentifier(null, null), role);
        assertEquals(0, retrievedPermissions.length);

        // check that user has no roles
        String[] retrievedRoles = UserRoles.getRolesForUser(process.main, userId);
        assertEquals(0, retrievedRoles.length);

        // check that the user-role mapping doesnt exist in the db
        String[] retrievedRolesFromDb = storage.getRolesForUser(new TenantIdentifier(null, null, null), userId);
        assertEquals(0, retrievedRolesFromDb.length);

        // check that role doesnt exist
        assertFalse(UserRoles.doesRoleExist(process.main, role));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingAnUnknownRole() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        boolean didRoleExist = UserRoles.deleteRole(process.main, "unknownRole");
        assertFalse(didRoleExist);

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

        {
            // call getRoles when no roles exist
            String[] retrievedRoles = UserRoles.getRoles(process.main);
            assertEquals(0, retrievedRoles.length);
        }

        // create roles
        String[] roles = new String[]{"role1", "role2", "role3"};
        for (String role : roles) {
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);
        }

        // retrieve all role and check for correct output
        {
            String[] retrievedRoles = UserRoles.getRoles(process.main);
            Utils.checkThatArraysAreEqual(roles, retrievedRoles);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingRolesFromAUserWhenMappingDoesExist() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create roles
        String[] roles = new String[]{"role1", "role2", "role3"};
        for (String role : roles) {
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);
        }

        // delete all roles for a user
        int numberOfRolesDeleted = UserRoles.deleteAllRolesForUser(process.main, "userId");
        assertEquals(0, numberOfRolesDeleted);

        // check that no roles have been deleted
        String[] retrievedRoles = UserRoles.getRoles(process.main);
        Utils.checkThatArraysAreEqual(roles, retrievedRoles);

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

        // create roles and assign them to a user
        String[] roles = new String[]{"role1", "role2", "role3"};
        String userId = "user";
        for (String role : roles) {
            UserRoles.createNewRoleOrModifyItsPermissions(process.main, role, null);
            UserRoles.addRoleToUser(process.main, userId, role);
        }

        // delete all roles for the user
        int numberOfRolesDeleted = UserRoles.deleteAllRolesForUser(process.main, userId);
        assertEquals(roles.length, numberOfRolesDeleted);

        // check that roles were removed from the user
        {
            String[] retrievedRoles = UserRoles.getRolesForUser(process.main, userId);
            assertEquals(0, retrievedRoles.length);
        }

        // check that no roles were removed
        {
            String[] retrievedRoles = UserRoles.getRoles(process.main);
            Utils.checkThatArraysAreEqual(roles, retrievedRoles);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void createAnAuthUserAssignRolesAndDeleteUser() throws Exception {
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

        // Create an Auth User
        AuthRecipeUserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPassword");

        // assign role to user
        UserRoles.addRoleToUser(process.main, userInfo.getSupertokensUserId(), role);

        {
            // check that user has role
            String[] retrievedRoles = UserRoles.getRolesForUser(process.main, userInfo.getSupertokensUserId());
            assertEquals(1, retrievedRoles.length);
            assertEquals(role, retrievedRoles[0]);
        }

        // delete User
        AuthRecipe.deleteUser(process.main, userInfo.getSupertokensUserId());

        {
            // check that user has no roles
            String[] retrievedRoles = UserRoles.getRolesForUser(process.main, userInfo.getSupertokensUserId());
            assertEquals(0, retrievedRoles.length);

            // check that the mapping for user role doesnt exist
            String[] roleUserMapping = storage.getRolesForUser(new TenantIdentifier(null, null, null),
                    userInfo.getSupertokensUserId());
            assertEquals(0, roleUserMapping.length);
        }

        {
            // check that role still exists
            String[] retrievedRoles = UserRoles.getRoles(process.main);
            assertEquals(1, retrievedRoles.length);
            assertEquals(role, retrievedRoles[0]);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
