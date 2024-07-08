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

package io.supertokens.inmemorydb.queries;

import io.supertokens.inmemorydb.ConnectionWithLocks;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;
import static io.supertokens.inmemorydb.config.Config.getConfig;

public class UserRolesQueries {
    public static String getQueryToCreateRolesTable(Start start) {
        String tableName = Config.getConfig(start).getRolesTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "role VARCHAR(255) NOT NULL,"
                + "PRIMARY KEY(app_id, role),"
                + "CONSTRAINT " + tableName + "_app_id_fkey FOREIGN KEY (app_id) REFERENCES " +
                Config.getConfig(start).getAppsTable()
                + " (app_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    public static String getQueryToCreateRolePermissionsTable(Start start) {
        String tableName = Config.getConfig(start).getUserRolesPermissionsTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "role VARCHAR(255) NOT NULL,"
                + "permission VARCHAR(255) NOT NULL,"
                + "PRIMARY KEY(app_id, role, permission),"
                + "FOREIGN KEY(app_id, role) REFERENCES " + Config.getConfig(start).getRolesTable()
                + " (app_id, role) ON DELETE CASCADE );";
        // @formatter:on
    }

    static String getQueryToCreateRolePermissionsPermissionIndex(Start start) {
        return "CREATE INDEX role_permissions_permission_index ON "
                + Config.getConfig(start).getUserRolesPermissionsTable() + "(app_id, permission);";
    }

    public static String getQueryToCreateUserRolesTable(Start start) {
        String tableName = Config.getConfig(start).getUserRolesTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "tenant_id VARCHAR(64) DEFAULT 'public',"
                + "user_id VARCHAR(128) NOT NULL,"
                + "role VARCHAR(255) NOT NULL,"
                + "PRIMARY KEY(app_id, tenant_id, user_id, role),"
                + "FOREIGN KEY(app_id, tenant_id) REFERENCES " + Config.getConfig(start).getTenantsTable()
                + " (app_id, tenant_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    static String getQueryToCreateUserRolesRoleIndex(Start start) {
        return "CREATE INDEX user_roles_role_index ON " + Config.getConfig(start).getUserRolesTable()
                + "(app_id, tenant_id, role);";
    }

    public static boolean createNewRoleOrDoNothingIfExists_Transaction(Start start, Connection con,
                                                                       AppIdentifier appIdentifier, String role)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + getConfig(start).getRolesTable()
                + "(app_id, role) VALUES (?, ?) ON CONFLICT DO NOTHING;";
        int rowsUpdated = update(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, role);
        });
        return rowsUpdated > 0;
    }

    public static void addPermissionToRoleOrDoNothingIfExists_Transaction(Start start, Connection con,
                                                                          AppIdentifier appIdentifier, String role,
                                                                          String permission)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + getConfig(start).getUserRolesPermissionsTable()
                + " (app_id, role, permission) VALUES(?, ?, ?) ON CONFLICT DO NOTHING";

        update(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, role);
            pst.setString(3, permission);
        });
    }

    public static boolean deleteRole(Start start, AppIdentifier appIdentifier,
                                     String role) throws SQLException, StorageQueryException {

        try {
            return start.startTransaction(con -> {
                // Row lock must be taken to delete the role, otherwise the table may be locked for delete
                Connection sqlCon = (Connection) con.getConnection();
                ((ConnectionWithLocks) sqlCon).lock(
                        appIdentifier.getAppId() + "~" + role + Config.getConfig(start).getRolesTable());

                String QUERY = "DELETE FROM " + getConfig(start).getRolesTable()
                        + " WHERE app_id = ? AND role = ? ;";

                try {
                    return update(sqlCon, QUERY, pst -> {
                        pst.setString(1, appIdentifier.getAppId());
                        pst.setString(2, role);
                    }) == 1;
                } catch (SQLException e) {
                    throw new StorageTransactionLogicException(e);
                }
            });
        } catch (StorageTransactionLogicException e) {
            throw new StorageQueryException(e.actualException);
        }
    }

    public static boolean doesRoleExist(Start start, AppIdentifier appIdentifier, String role)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT 1 FROM " + getConfig(start).getRolesTable()
                + " WHERE app_id = ? AND role = ?";
        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, role);
        }, ResultSet::next);
    }

    public static String[] getPermissionsForRole(Start start, AppIdentifier appIdentifier, String role)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT permission FROM " + Config.getConfig(start).getUserRolesPermissionsTable()
                + " WHERE app_id = ? AND role = ?;";
        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, role);
        }, result -> {
            ArrayList<String> permissions = new ArrayList<>();
            while (result.next()) {
                permissions.add(result.getString("permission"));
            }
            return permissions.toArray(String[]::new);
        });
    }

    public static String[] getRoles(Start start, AppIdentifier appIdentifier)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT role FROM " + getConfig(start).getRolesTable() + " WHERE app_id = ?";
        return execute(start, QUERY, pst -> pst.setString(1, appIdentifier.getAppId()), result -> {
            ArrayList<String> roles = new ArrayList<>();
            while (result.next()) {
                roles.add(result.getString("role"));
            }
            return roles.toArray(String[]::new);
        });
    }

    public static int addRoleToUser(Start start, TenantIdentifier tenantIdentifier, String userId, String role)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + getConfig(start).getUserRolesTable()
                + "(app_id, tenant_id, user_id, role) VALUES(?, ?, ?, ?);";
        return update(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
            pst.setString(4, role);
        });
    }

    public static String[] getRolesForUser(Start start, TenantIdentifier tenantIdentifier, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT role FROM " + getConfig(start).getUserRolesTable()
                + " WHERE app_id = ? AND tenant_id = ? AND user_id = ? ;";

        return execute(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
        }, result -> {
            ArrayList<String> roles = new ArrayList<>();
            while (result.next()) {
                roles.add(result.getString("role"));
            }
            return roles.toArray(String[]::new);
        });
    }

    public static String[] getRolesForUser(Start start, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT role FROM " + getConfig(start).getUserRolesTable()
                + " WHERE app_id = ? AND user_id = ? ;";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, result -> {
            ArrayList<String> roles = new ArrayList<>();
            while (result.next()) {
                roles.add(result.getString("role"));
            }
            return roles.toArray(String[]::new);
        });
    }

    public static boolean deleteRoleForUser_Transaction(Start start, Connection con, TenantIdentifier tenantIdentifier,
                                                        String userId, String role)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getUserRolesTable()
                + " WHERE app_id = ? AND tenant_id = ? AND user_id = ? AND role = ? ;";

        // store the number of rows updated
        int rowUpdatedCount = update(con, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
            pst.setString(4, role);
        });
        return rowUpdatedCount > 0;
    }

    public static boolean doesRoleExist_transaction(Start start, Connection con, AppIdentifier appIdentifier,
                                                    String role)
            throws SQLException, StorageQueryException {
        ((ConnectionWithLocks) con).lock(
                appIdentifier.getAppId() + "~" + role + Config.getConfig(start).getRolesTable());

        String QUERY = "SELECT 1 FROM " + getConfig(start).getRolesTable()
                + " WHERE app_id = ? AND role = ?";
        return execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, role);
        }, ResultSet::next);
    }

    public static String[] getUsersForRole(Start start, TenantIdentifier tenantIdentifier, String role)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT user_id FROM " + getConfig(start).getUserRolesTable()
                + " WHERE app_id = ? AND tenant_id = ? AND role = ? ";
        return execute(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, role);
        }, result -> {
            ArrayList<String> userIds = new ArrayList<>();
            while (result.next()) {
                userIds.add(result.getString("user_id"));
            }
            return userIds.toArray(String[]::new);
        });
    }

    public static boolean deletePermissionForRole_Transaction(Start start, Connection con, AppIdentifier appIdentifier,
                                                              String role,
                                                              String permission)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getUserRolesPermissionsTable()
                + " WHERE app_id = ? AND role = ? AND permission = ? ";

        // store the number of rows updated
        int rowUpdatedCount = update(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, role);
            pst.setString(3, permission);
        });

        return rowUpdatedCount > 0;
    }

    public static int deleteAllPermissionsForRole_Transaction(Start start, Connection con, AppIdentifier appIdentifier,
                                                              String role)
            throws SQLException, StorageQueryException {

        String QUERY = "DELETE FROM " + getConfig(start).getUserRolesPermissionsTable()
                + " WHERE app_id = ? AND role = ? ";
        // return the number of rows updated
        return update(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, role);
        });

    }

    public static String[] getRolesThatHavePermission(Start start, AppIdentifier appIdentifier, String permission)
            throws SQLException, StorageQueryException {

        String QUERY = "SELECT role FROM " + getConfig(start).getUserRolesPermissionsTable()
                + " WHERE app_id = ? AND permission = ? ";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, permission);
        }, result -> {
            ArrayList<String> roles = new ArrayList<>();

            while (result.next()) {
                roles.add(result.getString("role"));
            }

            return roles.toArray(String[]::new);
        });
    }

    public static int deleteAllRolesForUser(Start start, TenantIdentifier tenantIdentifier, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getUserRolesTable()
                + " WHERE app_id = ? AND tenant_id = ? AND user_id = ?";
        return update(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
        });
    }

    public static int deleteAllRolesForUser_Transaction(Connection con, Start start,
                                                        AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getUserRolesTable()
                + " WHERE app_id = ? AND user_id = ?";
        return update(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        });
    }

    public static boolean deleteAllUserRoleAssociationsForRole(Start start, AppIdentifier appIdentifier, String role)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getUserRolesTable()
                + " WHERE app_id = ? AND role = ? ;";
        return update(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, role);
        }) >= 1;
    }
}
