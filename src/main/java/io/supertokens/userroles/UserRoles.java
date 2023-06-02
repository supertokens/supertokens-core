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
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
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
    public static boolean addRoleToUser(TenantIdentifierWithStorage tenantIdentifierWithStorage, String userId,
                                        String role)
            throws StorageQueryException, UnknownRoleException, TenantOrAppNotFoundException {
        try {
            tenantIdentifierWithStorage.getUserRolesStorage().addRoleToUser(tenantIdentifierWithStorage, userId, role);
            return true;
        } catch (DuplicateUserRoleMappingException e) {
            // user already has role
            return false;
        }
    }

    @TestOnly
    public static boolean addRoleToUser(Main main, String userId, String role)
            throws StorageQueryException, UnknownRoleException {
        Storage storage = StorageLayer.getStorage(main);
        try {
            return addRoleToUser(
                    new TenantIdentifierWithStorage(null, null, null, storage),
                    userId, role);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // create a new role if it doesn't exist and add permissions to the role. This will create the role
    // in the user pool associated with the tenant used to query this API, so that this role can then
    // be shared across any tenant in that same user pool.
    public static boolean createNewRoleOrModifyItsPermissions(AppIdentifierWithStorage appIdentifierWithStorage,
                                                              String role, String[] permissions)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        UserRolesSQLStorage storage = appIdentifierWithStorage.getUserRolesStorage();

        try {
            return storage.startTransaction(con -> {
                boolean wasANewRoleCreated = false;
                try {
                    wasANewRoleCreated = storage.createNewRoleOrDoNothingIfExists_Transaction(
                            appIdentifierWithStorage, con, role);
                } catch (TenantOrAppNotFoundException e) {
                    throw new StorageTransactionLogicException(e);
                }

                if (permissions != null) {
                    for (int i = 0; i < permissions.length; i++) {
                        try {
                            storage.addPermissionToRoleOrDoNothingIfExists_Transaction(appIdentifierWithStorage,
                                    con, role, permissions[i]);
                        } catch (UnknownRoleException e) {
                            // ignore exception, should not come here since role should always exist in this transaction
                        }
                    }
                }
                storage.commitTransaction(con);
                return wasANewRoleCreated;
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof  TenantOrAppNotFoundException) {
                throw (TenantOrAppNotFoundException) e.actualException;
            }
            throw e;
        }
    }

    @TestOnly
    public static boolean createNewRoleOrModifyItsPermissions(Main main,
                                                              String role, String[] permissions)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        Storage storage = StorageLayer.getStorage(main);
        return createNewRoleOrModifyItsPermissions(
                new AppIdentifierWithStorage(null, null, storage), role,
                permissions);
    }

    public static boolean doesRoleExist(AppIdentifierWithStorage appIdentifierWithStorage, String role)
            throws StorageQueryException {
        UserRolesSQLStorage storage = appIdentifierWithStorage.getUserRolesStorage();
        return storage.doesRoleExist(appIdentifierWithStorage, role);
    }

    @TestOnly
    public static boolean doesRoleExist(Main main, String role)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return doesRoleExist(new AppIdentifierWithStorage(null, null, storage), role);
    }

    // remove a role mapped to a user, if the role doesn't exist throw a UNKNOWN_ROLE_EXCEPTION error
    public static boolean removeUserRole(TenantIdentifierWithStorage tenantIdentifierWithStorage, String userId,
                                         String role)
            throws StorageQueryException, StorageTransactionLogicException, UnknownRoleException {

        UserRolesSQLStorage storage = tenantIdentifierWithStorage.getUserRolesStorage();

        try {
            return storage.startTransaction(con -> {

                boolean doesRoleExist = storage.doesRoleExist_Transaction(
                        tenantIdentifierWithStorage.toAppIdentifier(), con, role);

                if (doesRoleExist) {
                    return storage.deleteRoleForUser_Transaction(tenantIdentifierWithStorage, con, userId, role);
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
    public static boolean removeUserRole(Main main, String userId, String role)
            throws StorageQueryException, StorageTransactionLogicException, UnknownRoleException {
        Storage storage = StorageLayer.getStorage(main);
        return removeUserRole(
                new TenantIdentifierWithStorage(null, null, null, storage),
                userId, role);
    }

    // retrieve all roles associated with the user
    public static String[] getRolesForUser(TenantIdentifierWithStorage tenantIdentifierWithStorage, String userId)
            throws StorageQueryException {
        return tenantIdentifierWithStorage.getUserRolesStorage().getRolesForUser(tenantIdentifierWithStorage, userId);
    }

    @TestOnly
    public static String[] getRolesForUser(Main main, String userId)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getRolesForUser(
                new TenantIdentifierWithStorage(null, null, null, storage), userId);
    }

    // retrieve all users who have the input role, if role does not exist then throw UNKNOWN_ROLE_EXCEPTION
    public static String[] getUsersForRole(TenantIdentifierWithStorage tenantIdentifierWithStorage, String role)
            throws StorageQueryException, UnknownRoleException {
        // Since getUsersForRole does not change any data we do not use a transaction since it would not solve any
        // problem
        UserRolesSQLStorage storage = tenantIdentifierWithStorage.getUserRolesStorage();
        boolean doesRoleExist = storage.doesRoleExist(tenantIdentifierWithStorage.toAppIdentifier(), role);
        if (doesRoleExist) {
            return storage.getUsersForRole(tenantIdentifierWithStorage, role);
        } else {
            throw new UnknownRoleException();
        }
    }

    @TestOnly
    public static String[] getUsersForRole(Main main, String role)
            throws StorageQueryException, UnknownRoleException {
        Storage storage = StorageLayer.getStorage(main);
        return getUsersForRole(
                new TenantIdentifierWithStorage(null, null, null, storage), role);
    }

    // retrieve all permissions associated with the role
    public static String[] getPermissionsForRole(AppIdentifierWithStorage appIdentifierWithStorage, String role)
            throws StorageQueryException, UnknownRoleException {
        // Since getPermissionsForRole does not change any data we do not use a transaction since it would not solve any
        // problem
        UserRolesSQLStorage storage = appIdentifierWithStorage.getUserRolesStorage();
        boolean doesRoleExist = storage.doesRoleExist(appIdentifierWithStorage, role);

        if (doesRoleExist) {
            return appIdentifierWithStorage.getUserRolesStorage()
                    .getPermissionsForRole(appIdentifierWithStorage, role);
        } else {
            throw new UnknownRoleException();
        }
    }

    @TestOnly
    public static String[] getPermissionsForRole(Main main, String role)
            throws StorageQueryException, UnknownRoleException {
        Storage storage = StorageLayer.getStorage(main);
        return getPermissionsForRole(
                new AppIdentifierWithStorage(null, null, storage), role);
    }

    // delete permissions from a role, if the role doesn't exist throw an UNKNOWN_ROLE_EXCEPTION
    public static void deletePermissionsFromRole(AppIdentifierWithStorage appIdentifierWithStorage, String role,
                                                 @Nullable String[] permissions)
            throws StorageQueryException, StorageTransactionLogicException, UnknownRoleException {
        UserRolesSQLStorage storage = appIdentifierWithStorage.getUserRolesStorage();
        try {
            storage.startTransaction(con -> {
                boolean doesRoleExist = storage.doesRoleExist_Transaction(appIdentifierWithStorage, con, role);
                if (doesRoleExist) {
                    if (permissions == null) {
                        storage.deleteAllPermissionsForRole_Transaction(appIdentifierWithStorage, con, role);
                    } else {
                        for (int i = 0; i < permissions.length; i++) {
                            storage.deletePermissionForRole_Transaction(appIdentifierWithStorage, con, role,
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
        Storage storage = StorageLayer.getStorage(main);
        deletePermissionsFromRole(new AppIdentifierWithStorage(null, null, storage),
                role, permissions);
    }

    // retrieve roles that have the input permission
    public static String[] getRolesThatHavePermission(AppIdentifierWithStorage appIdentifierWithStorage,
                                                      String permission)
            throws StorageQueryException {
        return appIdentifierWithStorage.getUserRolesStorage().getRolesThatHavePermission(
                appIdentifierWithStorage, permission);
    }

    @TestOnly
    public static String[] getRolesThatHavePermission(Main main,
                                                      String permission) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getRolesThatHavePermission(
                new AppIdentifierWithStorage(null, null, storage), permission);
    }

    // delete a role
    public static boolean deleteRole(AppIdentifierWithStorage appIdentifierWithStorage, String role)
            throws StorageQueryException {
        return appIdentifierWithStorage.getUserRolesStorage().deleteRole(appIdentifierWithStorage, role);
    }

    @TestOnly
    public static boolean deleteRole(Main main, String role) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return deleteRole(new AppIdentifierWithStorage(null, null, storage), role);
    }

    // retrieve all roles that have been created
    public static String[] getRoles(AppIdentifierWithStorage appIdentifierWithStorage)
            throws StorageQueryException {
        return appIdentifierWithStorage.getUserRolesStorage().getRoles(appIdentifierWithStorage);
    }

    @TestOnly
    public static String[] getRoles(Main main) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getRoles(new AppIdentifierWithStorage(null, null, storage));
    }

    // delete all roles associated with a user
    public static int deleteAllRolesForUser(TenantIdentifierWithStorage tenantIdentifierWithStorage, String userId)
            throws StorageQueryException {
        return tenantIdentifierWithStorage.getUserRolesStorage().deleteAllRolesForUser(
                tenantIdentifierWithStorage, userId);
    }

    @TestOnly
    public static int deleteAllRolesForUser(Main main, String userId)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return deleteAllRolesForUser(
                new TenantIdentifierWithStorage(null, null, null, storage), userId);
    }

}
