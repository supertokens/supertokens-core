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
import io.supertokens.ResourceDistributor;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.bulkimport.exceptions.BulkImportBatchInsertException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.userroles.exception.DuplicateUserRoleMappingException;
import io.supertokens.pluginInterface.userroles.exception.UnknownRoleException;
import io.supertokens.pluginInterface.userroles.sqlStorage.UserRolesSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nullable;
import java.util.*;

public class UserRoles {
    // add a role to a user and return true, if the role is already mapped to the user return false, but if
    // the role does not exist, throw an UNKNOWN_ROLE_EXCEPTION error
    public static boolean addRoleToUser(Main main, TenantIdentifier tenantIdentifier, Storage storage, String userId,
                                        String role)
            throws StorageQueryException, UnknownRoleException, TenantOrAppNotFoundException {

        // Roles are stored in public tenant storage and role to user mapping is stored in the tenant's storage
        // We do this because it's not straight forward to replicate roles to all storages of an app
        Storage appStorage = StorageLayer.getStorage(
                tenantIdentifier.toAppIdentifier().getAsPublicTenantIdentifier(), main);
        if (!doesRoleExist(tenantIdentifier.toAppIdentifier(), appStorage, role)) {
            throw new UnknownRoleException();
        }

        try {
            StorageUtils.getUserRolesStorage(storage).addRoleToUser(tenantIdentifier, userId, role);
            return true;
        } catch (DuplicateUserRoleMappingException e) {
            // user already has role
            return false;
        }
    }

    public static void addMultipleRolesToMultipleUsers(Main main, AppIdentifier appIdentifier, Storage storage,
                                                       Map<TenantIdentifier, Map<String, List<String>>> rolesToUserByTenant)
            throws StorageTransactionLogicException, TenantOrAppNotFoundException {

        // Roles are stored in public tenant storage and role to user mapping is stored in the tenant's storage
        // We do this because it's not straight forward to replicate roles to all storages of an app
        Storage appStorage = StorageLayer.getStorage(
                appIdentifier.getAsPublicTenantIdentifier(), main);

        try {
            UserRolesSQLStorage userRolesStorage = StorageUtils.getUserRolesStorage(storage);
            UserRolesSQLStorage publicRoleStorage = StorageUtils.getUserRolesStorage(appStorage);
            Map<String, Exception> errorsByUser = new HashMap<>();
            publicRoleStorage.startTransaction(con -> {
                Set<String> rolesToSearchFor = new HashSet<>();
                for (TenantIdentifier tenantIdentifier : rolesToUserByTenant.keySet()) {
                    for(String userId : rolesToUserByTenant.get(tenantIdentifier).keySet()){
                        rolesToSearchFor.addAll(rolesToUserByTenant.get(tenantIdentifier).get(userId));
                    }
                }
                List<String> rolesFound = ((UserRolesSQLStorage) appStorage).doesMultipleRoleExist_Transaction(
                        appIdentifier, con,
                        new ArrayList<>(rolesToSearchFor));

                for (Map<String, List<String>> rolesToUsers : rolesToUserByTenant.values()) {
                    for (String userId : rolesToUsers.keySet()) {
                        List<String> rolesOfUser = rolesToUsers.get(userId);
                        if (!new HashSet<>(rolesFound).containsAll(rolesOfUser)) { //wrapping in hashset for performance reasons
                            errorsByUser.put(userId, new UnknownRoleException());
                        }
                    }
                }
                if (!errorsByUser.isEmpty()) {
                    throw new StorageTransactionLogicException(
                            new BulkImportBatchInsertException("Roles errors", errorsByUser));
                }
                return null;
            });
            userRolesStorage.startTransaction(con -> {
                userRolesStorage.addRolesToUsers_Transaction(con, rolesToUserByTenant);
                userRolesStorage.commitTransaction(con);
                return null;
            });

        } catch (StorageQueryException e) {
            throw new StorageTransactionLogicException(e);
        }
    }

    @TestOnly
    public static boolean addRoleToUser(Main main, String userId, String role)
            throws StorageQueryException, UnknownRoleException {
        Storage storage = StorageLayer.getStorage(main);
        try {
            return addRoleToUser(
                    main, ResourceDistributor.getAppForTesting(),
                    storage, userId, role);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // create a new role if it doesn't exist and add permissions to the role. This will create the role
    // in the user pool associated with the tenant used to query this API, so that this role can then
    // be shared across any tenant in that same user pool.
    public static boolean createNewRoleOrModifyItsPermissions(AppIdentifier appIdentifier, Storage storage,
                                                              String role, String[] permissions)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException {
        UserRolesSQLStorage userRolesStorage = StorageUtils.getUserRolesStorage(storage);

        try {
            return userRolesStorage.startTransaction(con -> {
                boolean wasANewRoleCreated = false;
                try {
                    wasANewRoleCreated = userRolesStorage.createNewRoleOrDoNothingIfExists_Transaction(
                            appIdentifier, con, role);
                } catch (TenantOrAppNotFoundException e) {
                    throw new StorageTransactionLogicException(e);
                }

                if (permissions != null) {
                    for (int i = 0; i < permissions.length; i++) {
                        try {
                            userRolesStorage.addPermissionToRoleOrDoNothingIfExists_Transaction(appIdentifier,
                                    con, role, permissions[i]);
                        } catch (UnknownRoleException e) {
                            // ignore exception, should not come here since role should always exist in this transaction
                        }
                    }
                }
                userRolesStorage.commitTransaction(con);
                return wasANewRoleCreated;
            });
        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof TenantOrAppNotFoundException) {
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
                ResourceDistributor.getAppForTesting().toAppIdentifier(), storage, role,
                permissions);
    }

    public static boolean doesRoleExist(AppIdentifier appIdentifier, Storage storage, String role)
            throws StorageQueryException {
        UserRolesSQLStorage userRolesStorage = StorageUtils.getUserRolesStorage(storage);
        return userRolesStorage.doesRoleExist(appIdentifier, role);
    }

    @TestOnly
    public static boolean doesRoleExist(Main main, String role)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return doesRoleExist(ResourceDistributor.getAppForTesting().toAppIdentifier(), storage, role);
    }

    // remove a role mapped to a user, if the role doesn't exist throw a UNKNOWN_ROLE_EXCEPTION error
    public static boolean removeUserRole(TenantIdentifier tenantIdentifier, Storage storage, String userId,
                                         String role)
            throws StorageQueryException, StorageTransactionLogicException, UnknownRoleException {

        UserRolesSQLStorage userRolesStorage = StorageUtils.getUserRolesStorage(storage);

        try {
            return userRolesStorage.startTransaction(con -> {

                boolean doesRoleExist = userRolesStorage.doesRoleExist_Transaction(
                        tenantIdentifier.toAppIdentifier(), con, role);

                if (doesRoleExist) {
                    return userRolesStorage.deleteRoleForUser_Transaction(tenantIdentifier, con, userId, role);
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
                ResourceDistributor.getAppForTesting(), storage,
                userId, role);
    }

    // retrieve all roles associated with the user
    public static String[] getRolesForUser(TenantIdentifier tenantIdentifier, Storage storage, String userId)
            throws StorageQueryException {
        return StorageUtils.getUserRolesStorage(storage).getRolesForUser(tenantIdentifier, userId);
    }

    @TestOnly
    public static String[] getRolesForUser(Main main, String userId)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getRolesForUser(ResourceDistributor.getAppForTesting(), storage, userId);
    }

    // retrieve all users who have the input role, if role does not exist then throw UNKNOWN_ROLE_EXCEPTION
    public static String[] getUsersForRole(TenantIdentifier tenantIdentifier, Storage storage, String role)
            throws StorageQueryException, UnknownRoleException {
        // Since getUsersForRole does not change any data we do not use a transaction since it would not solve any
        // problem
        UserRolesSQLStorage userRolesStorage = StorageUtils.getUserRolesStorage(storage);
        boolean doesRoleExist = userRolesStorage.doesRoleExist(tenantIdentifier.toAppIdentifier(), role);
        if (doesRoleExist) {
            return userRolesStorage.getUsersForRole(tenantIdentifier, role);
        } else {
            throw new UnknownRoleException();
        }
    }

    @TestOnly
    public static String[] getUsersForRole(Main main, String role)
            throws StorageQueryException, UnknownRoleException {
        Storage storage = StorageLayer.getStorage(main);
        return getUsersForRole(ResourceDistributor.getAppForTesting(), storage, role);
    }

    // retrieve all permissions associated with the role
    public static String[] getPermissionsForRole(AppIdentifier appIdentifier, Storage storage, String role)
            throws StorageQueryException, UnknownRoleException {
        // Since getPermissionsForRole does not change any data we do not use a transaction since it would not solve any
        // problem
        UserRolesSQLStorage userRolesStorage = StorageUtils.getUserRolesStorage(storage);
        boolean doesRoleExist = userRolesStorage.doesRoleExist(appIdentifier, role);

        if (doesRoleExist) {
            return StorageUtils.getUserRolesStorage(storage)
                    .getPermissionsForRole(appIdentifier, role);
        } else {
            throw new UnknownRoleException();
        }
    }

    @TestOnly
    public static String[] getPermissionsForRole(Main main, String role)
            throws StorageQueryException, UnknownRoleException {
        Storage storage = StorageLayer.getStorage(main);
        return getPermissionsForRole(ResourceDistributor.getAppForTesting().toAppIdentifier(), storage, role);
    }

    // delete permissions from a role, if the role doesn't exist throw an UNKNOWN_ROLE_EXCEPTION
    public static void deletePermissionsFromRole(AppIdentifier appIdentifier, Storage storage, String role,
                                                 @Nullable String[] permissions)
            throws StorageQueryException, StorageTransactionLogicException, UnknownRoleException {
        UserRolesSQLStorage userRolesStorage = StorageUtils.getUserRolesStorage(storage);
        try {
            userRolesStorage.startTransaction(con -> {
                boolean doesRoleExist = userRolesStorage.doesRoleExist_Transaction(appIdentifier, con, role);
                if (doesRoleExist) {
                    if (permissions == null) {
                        userRolesStorage.deleteAllPermissionsForRole_Transaction(appIdentifier, con, role);
                    } else {
                        for (int i = 0; i < permissions.length; i++) {
                            userRolesStorage.deletePermissionForRole_Transaction(appIdentifier, con, role,
                                    permissions[i]);
                        }
                    }
                } else {
                    throw new StorageTransactionLogicException(new UnknownRoleException());
                }
                userRolesStorage.commitTransaction(con);
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
        deletePermissionsFromRole(ResourceDistributor.getAppForTesting().toAppIdentifier(), storage,
                role, permissions);
    }

    // retrieve roles that have the input permission
    public static String[] getRolesThatHavePermission(AppIdentifier appIdentifier, Storage storage,
                                                      String permission)
            throws StorageQueryException {
        return StorageUtils.getUserRolesStorage(storage).getRolesThatHavePermission(
                appIdentifier, permission);
    }

    @TestOnly
    public static String[] getRolesThatHavePermission(Main main,
                                                      String permission) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getRolesThatHavePermission(
                ResourceDistributor.getAppForTesting().toAppIdentifier(), storage, permission);
    }

    // delete a role
    public static boolean deleteRole(Main main, AppIdentifier appIdentifier, String role)
            throws StorageQueryException, TenantOrAppNotFoundException {

        Storage[] storages = StorageLayer.getStoragesForApp(main, appIdentifier);
        boolean deletedRole = false;
        for (Storage storage : storages) {
            UserRolesSQLStorage userRolesStorage = StorageUtils.getUserRolesStorage(storage);
            deletedRole = userRolesStorage.deleteAllUserRoleAssociationsForRole(appIdentifier, role) || deletedRole;
        }

        // Delete the role from the public tenant storage in the end so that the user
        // never sees a role for user that has been deleted while the deletion is in progress
        Storage appStorage = StorageLayer.getStorage(appIdentifier.getAsPublicTenantIdentifier(), main);
        UserRolesSQLStorage userRolesStorage = StorageUtils.getUserRolesStorage(appStorage);
        deletedRole = userRolesStorage.deleteRole(appIdentifier, role) || deletedRole;

        return deletedRole;
    }

    @TestOnly
    public static boolean deleteRole(Main main, String role) throws StorageQueryException,
            TenantOrAppNotFoundException {
        return deleteRole(main, ResourceDistributor.getAppForTesting().toAppIdentifier(), role);
    }

    // retrieve all roles that have been created
    public static String[] getRoles(AppIdentifier appIdentifier, Storage storage)
            throws StorageQueryException {
        return StorageUtils.getUserRolesStorage(storage).getRoles(appIdentifier);
    }

    @TestOnly
    public static String[] getRoles(Main main) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getRoles(ResourceDistributor.getAppForTesting().toAppIdentifier(), storage);
    }

    // delete all roles associated with a user
    public static int deleteAllRolesForUser(TenantIdentifier tenantIdentifier, Storage storage, String userId)
            throws StorageQueryException {
        return StorageUtils.getUserRolesStorage(storage).deleteAllRolesForUser(
                tenantIdentifier, userId);
    }

    @TestOnly
    public static int deleteAllRolesForUser(Main main, String userId)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return deleteAllRolesForUser(ResourceDistributor.getAppForTesting(), storage, userId);
    }

}
