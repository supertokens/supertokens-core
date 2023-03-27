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

package io.supertokens.userroles;

import io.supertokens.Main;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.userroles.exception.DuplicateUserRoleMappingException;
import io.supertokens.pluginInterface.userroles.exception.UnknownRoleException;
import io.supertokens.pluginInterface.userroles.sqlStorage.UserRolesSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nullable;

public class UserRoles {
    // add a role to a user and return true, if the role is already mapped to the user return false, but if
    // the role does not exist, throw an UNKNOWN_ROLE_EXCEPTION error
    public static boolean addRoleToUser(TenantIdentifier tenantIdentifier, Main main, String userId,
                                        String role)
            throws StorageQueryException, UnknownRoleException, TenantOrAppNotFoundException {
        try {
            StorageLayer.getUserRolesStorage(tenantIdentifier, main).addRoleToUser(tenantIdentifier, userId, role);
            return true;
        } catch (DuplicateUserRoleMappingException e) {
            // user already has role
            return false;
        }
    }

    @TestOnly
    public static boolean addRoleToUser(Main main, String userId,
                                        String role)
            throws StorageQueryException, UnknownRoleException {
        try {
            return addRoleToUser(new TenantIdentifier(null, null, null), main, userId, role);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // create a new role if it doesn't exist and add permissions to the role. This will create the role
    // in the user pool associated with the tenant used to query this API, so that this role can then
    // be shared across any tenant in that same user pool.
    public static boolean createNewRoleOrModifyItsPermissions(TenantIdentifier tenantIdentifier, Main main,
                                                              String role, String[] permissions)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(tenantIdentifier, main);
        return storage.startTransaction(con -> {
            boolean wasANewRoleCreated = storage.createNewRoleOrDoNothingIfExists_Transaction(
                    tenantIdentifier.toAppIdentifier(), con, role);

            if (permissions != null) {
                for (int i = 0; i < permissions.length; i++) {
                    try {
                        storage.addPermissionToRoleOrDoNothingIfExists_Transaction(tenantIdentifier.toAppIdentifier(),
                                con, role, permissions[i]);
                    } catch (UnknownRoleException e) {
                        // ignore exception, should not come here since role should always exist in this transaction
                    }
                }
            }
            storage.commitTransaction(con);
            return wasANewRoleCreated;
        });
    }

    @TestOnly
    public static boolean createNewRoleOrModifyItsPermissions(Main main,
                                                              String role, String[] permissions)
            throws StorageQueryException, StorageTransactionLogicException {
        try {
            return createNewRoleOrModifyItsPermissions(new TenantIdentifier(null, null, null), main, role, permissions);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean doesRoleExist(TenantIdentifier tenantIdentifier, Main main, String role)
            throws StorageQueryException, TenantOrAppNotFoundException {
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(tenantIdentifier, main);
        return storage.doesRoleExist(tenantIdentifier.toAppIdentifier(), role);
    }

    @TestOnly
    public static boolean doesRoleExist(Main main, String role)
            throws StorageQueryException {
        try {
            return doesRoleExist(new TenantIdentifier(null, null, null), main, role);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // remove a role mapped to a user, if the role doesn't exist throw a UNKNOWN_ROLE_EXCEPTION error
    public static boolean removeUserRole(TenantIdentifier tenantIdentifier, Main main, String userId,
                                         String role)
            throws StorageQueryException, StorageTransactionLogicException, UnknownRoleException,
            TenantOrAppNotFoundException {

        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(tenantIdentifier, main);

        try {
            return storage.startTransaction(con -> {

                boolean doesRoleExist = storage.doesRoleExist_Transaction(tenantIdentifier.toAppIdentifier(), con,
                        role);

                if (doesRoleExist) {
                    return storage.deleteRoleForUser_Transaction(tenantIdentifier, con, userId, role);
                } else {
                    throw new StorageTransactionLogicException(new UnknownRoleException());
                }
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof UnknownRoleException) {
                throw (UnknownRoleException) e.actualException;
            }
            throw e;
        }
    }

    @TestOnly
    public static boolean removeUserRole(Main main, String userId,
                                         String role)
            throws StorageQueryException, StorageTransactionLogicException, UnknownRoleException {
        try {
            return removeUserRole(new TenantIdentifier(null, null, null), main, userId, role);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // retrieve all roles associated with the user
    public static String[] getRolesForUser(TenantIdentifier tenantIdentifier, Main main, String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getUserRolesStorage(tenantIdentifier, main).getRolesForUser(tenantIdentifier, userId);
    }

    @TestOnly
    public static String[] getRolesForUser(Main main, String userId)
            throws StorageQueryException {
        try {
            return getRolesForUser(new TenantIdentifier(null, null, null), main, userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // retrieve all users who have the input role, if role does not exist then throw UNKNOWN_ROLE_EXCEPTION
    public static String[] getUsersForRole(TenantIdentifier tenantIdentifier, Main main, String role)
            throws StorageQueryException, UnknownRoleException, TenantOrAppNotFoundException {
        // Since getUsersForRole does not change any data we do not use a transaction since it would not solve any
        // problem
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(tenantIdentifier, main);
        boolean doesRoleExist = storage.doesRoleExist(tenantIdentifier.toAppIdentifier(), role);
        if (doesRoleExist) {
            return storage.getUsersForRole(tenantIdentifier, role);
        } else {
            throw new UnknownRoleException();
        }
    }

    @TestOnly
    public static String[] getUsersForRole(Main main, String role)
            throws StorageQueryException, UnknownRoleException {
        try {
            return getUsersForRole(new TenantIdentifier(null, null, null), main, role);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // retrieve all permissions associated with the role
    public static String[] getPermissionsForRole(TenantIdentifier tenantIdentifier, Main main, String role)
            throws StorageQueryException, UnknownRoleException, TenantOrAppNotFoundException {
        // Since getPermissionsForRole does not change any data we do not use a transaction since it would not solve any
        // problem
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(tenantIdentifier, main);
        boolean doesRoleExist = storage.doesRoleExist(tenantIdentifier.toAppIdentifier(), role);

        if (doesRoleExist) {
            return StorageLayer.getUserRolesStorage(tenantIdentifier, main)
                    .getPermissionsForRole(tenantIdentifier.toAppIdentifier(), role);
        } else {
            throw new UnknownRoleException();
        }
    }

    @TestOnly
    public static String[] getPermissionsForRole(Main main, String role)
            throws StorageQueryException, UnknownRoleException {
        try {
            return getPermissionsForRole(new TenantIdentifier(null, null, null), main, role);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // delete permissions from a role, if the role doesn't exist throw an UNKNOWN_ROLE_EXCEPTION
    public static void deletePermissionsFromRole(TenantIdentifier tenantIdentifier, Main main, String role,
                                                 @Nullable String[] permissions)
            throws StorageQueryException, StorageTransactionLogicException, UnknownRoleException,
            TenantOrAppNotFoundException {
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(tenantIdentifier, main);
        try {
            storage.startTransaction(con -> {
                boolean doesRoleExist = storage.doesRoleExist_Transaction(tenantIdentifier.toAppIdentifier(), con,
                        role);
                if (doesRoleExist) {
                    if (permissions == null) {
                        storage.deleteAllPermissionsForRole_Transaction(tenantIdentifier.toAppIdentifier(), con, role);
                    } else {
                        for (int i = 0; i < permissions.length; i++) {
                            storage.deletePermissionForRole_Transaction(tenantIdentifier.toAppIdentifier(), con, role,
                                    permissions[i]);
                        }
                    }
                } else {
                    throw new StorageTransactionLogicException(new UnknownRoleException());
                }
                storage.commitTransaction(con);
                return null;
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof UnknownRoleException) {
                throw (UnknownRoleException) e.actualException;
            }
            throw e;
        }

    }

    @TestOnly
    public static void deletePermissionsFromRole(Main main, String role,
                                                 @Nullable String[] permissions)
            throws StorageQueryException, StorageTransactionLogicException, UnknownRoleException {
        try {
            deletePermissionsFromRole(new TenantIdentifier(null, null, null), main, role, permissions);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // retrieve roles that have the input permission
    public static String[] getRolesThatHavePermission(TenantIdentifier tenantIdentifier, Main main,
                                                      String permission)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getUserRolesStorage(tenantIdentifier, main)
                .getRolesThatHavePermission(tenantIdentifier.toAppIdentifier(), permission);
    }

    @TestOnly
    public static String[] getRolesThatHavePermission(Main main,
                                                      String permission) throws StorageQueryException {
        try {
            return getRolesThatHavePermission(new TenantIdentifier(null, null, null), main, permission);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // delete a role
    public static boolean deleteRole(TenantIdentifier tenantIdentifier, Main main, String role)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getUserRolesStorage(tenantIdentifier, main)
                .deleteRole(tenantIdentifier.toAppIdentifier(), role);
    }

    @TestOnly
    public static boolean deleteRole(Main main, String role)
            throws StorageQueryException {
        try {
            return deleteRole(new TenantIdentifier(null, null, null), main, role);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // retrieve all roles that have been created
    public static String[] getRoles(TenantIdentifier tenantIdentifier, Main main)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getUserRolesStorage(tenantIdentifier, main).getRoles(tenantIdentifier.toAppIdentifier());
    }

    @TestOnly
    public static String[] getRoles(Main main)
            throws StorageQueryException {
        try {
            return getRoles(new TenantIdentifier(null, null, null), main);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // delete all roles associated with a user
    public static int deleteAllRolesForUser(TenantIdentifier tenantIdentifier, Main main, String userId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        return StorageLayer.getUserRolesStorage(tenantIdentifier, main).deleteAllRolesForUser(tenantIdentifier, userId);
    }

    @TestOnly
    public static int deleteAllRolesForUser(Main main, String userId)
            throws StorageQueryException {
        try {
            return deleteAllRolesForUser(new TenantIdentifier(null, null, null), main, userId);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

}
