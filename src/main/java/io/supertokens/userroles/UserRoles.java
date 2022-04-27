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
import io.supertokens.pluginInterface.userroles.exception.DuplicateUserRoleMappingException;
import io.supertokens.pluginInterface.userroles.exception.UnknownRoleException;
import io.supertokens.pluginInterface.userroles.sqlStorage.UserRolesSQLStorage;
import io.supertokens.storageLayer.StorageLayer;

public class UserRoles {
    // add a role to a user and return true, if the role is already mapped to the user return false, but if
    // the role does not exist, throw an UNKNOWN_ROLE_EXCEPTION error
    public static boolean addRoleToUser(Main main, String userId, String role)
            throws StorageQueryException, UnknownRoleException {
        try {
            StorageLayer.getUserRolesStorage(main).addRoleToUser(userId, role);
            return true;
        } catch (DuplicateUserRoleMappingException e) {
            // user already has role
            return false;
        }
    }

    // create a new role if it doesn't exist and add permissions to the role
    public static boolean createNewRoleOrModifyItsPermissions(Main main, String role, String[] permissions)
            throws StorageQueryException, StorageTransactionLogicException {
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(main);
        return storage.startTransaction(con -> {
            boolean wasANewRoleCreated = storage.createNewRoleOrDoNothingIfExists_Transaction(con, role);

            if (permissions != null) {
                for (int i = 0; i < permissions.length; i++) {
                    try {
                        storage.addPermissionToRoleOrDoNothingIfExists_Transaction(con, role, permissions[i]);
                    } catch (UnknownRoleException e) {
                        // ignore exception, should not come here since role should always exist in this transaction
                    }
                }
            }
            storage.commitTransaction(con);
            return wasANewRoleCreated;
        });
    }

    public static boolean doesRoleExist(Main main, String role) throws StorageQueryException {
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(main);
        return storage.doesRoleExist(role);
    }

    // remove a role mapped to a user, if the role doesn't exist throw a UNKNOWN_ROLE_EXCEPTION error
    public static boolean removeUserRole(Main main, String userId, String role)
            throws StorageQueryException, StorageTransactionLogicException, UnknownRoleException {

        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(main);

        try {
            return storage.startTransaction(con -> {

                boolean doesRoleExist = storage.doesRoleExist_Transaction(con, role);

                if (doesRoleExist) {
                    return storage.deleteRoleForUser_Transaction(con, userId, role);
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

    // retrieve all roles associated with the user
    public static String[] getRolesForUser(Main main, String userId) throws StorageQueryException {
        return StorageLayer.getUserRolesStorage(main).getRolesForUser(userId);
    }

    // retrieve all users who have the input role, if role does not exist then throw UNKNOWN_ROLE_EXCEPTION
    public static String[] getUsersForRole(Main main, String role) throws StorageQueryException, UnknownRoleException {
        // Since getUsersForRole does not change any data we do not use a transaction since it would not solve any
        // problem
        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(main);
        boolean doesRoleExist = storage.doesRoleExist(role);
        if (doesRoleExist) {
            return storage.getUsersForRole(role);
        } else {
            throw new UnknownRoleException();
        }
    }

    // retrieve all permissions associated with the role
    public static String[] getPermissionsForRole(Main main, String role)
            throws StorageQueryException, UnknownRoleException {

        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(main);
        boolean doesRoleExist = storage.doesRoleExist(role);

        if (doesRoleExist) {
            return StorageLayer.getUserRolesStorage(main).getPermissionsForRole(role);
        } else {
            throw new UnknownRoleException();
        }
    }

//    // delete permissions from a role, if the role doesn't exist throw an UNKNOWN_ROLE_EXCEPTION
//    public static void deletePermissionsFromRole(Main main, String role, @Nullable String[] permissions)
//            throws StorageQueryException, StorageTransactionLogicException {
//        UserRolesSQLStorage storage = StorageLayer.getUserRolesStorage(main);
//        storage.startTransaction(con -> {
//            boolean doesRoleExist = storage.doesRoleExist_Transaction(con, role);
//            if (doesRoleExist) {
//                if (permissions == null) {
//                    storage.deleteAllPermissionsForRole_Transaction(con, role);
//                } else {
//                    for (int i = 0; i < permissions.length; i++) {
//                        storage.deletePermissionForRole_Transaction(con, role, permissions[i]);
//                    }
//                }
//            } else {
//                throw new UnknownRoleException();
//            }
//            return null;
//        });
//    }
//
//    // retrieve roles that have the input permission
//    public static String[] getRolesThatHavePermission(Main main, String permission) throws StorageQueryException {
//        return StorageLayer.getUserRolesStorage(main).getRolesThatHavePermission(permission);
//    }
//
//    // delete a role
//    public static int deleteRole(Main main, String role) throws StorageQueryException {
//        return StorageLayer.getUserRolesStorage(main).deleteRole(role);
//    }
//
//    // retrieve all roles that have been created
//    public static String[] getRoles(Main main) throws StorageQueryException {
//        return StorageLayer.getUserRolesStorage(main).getRoles();
//    }
//
//    // delete all roles associated with a user
//    public static int deleteAllRolesForUser(Main main, String userId) throws StorageQueryException {
//        return StorageLayer.getUserRolesStorage(main).deleteAllRolesForUser(userId);
//    }

}
