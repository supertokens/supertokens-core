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
import io.supertokens.inmemorydb.PreparedStatementValueSetter;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;
import static io.supertokens.inmemorydb.config.Config.getConfig;

public class UserRoleQueries {
    public static String getQueryToCreateRolesTable(Start start) {
        String tableName = Config.getConfig(start).getRolesTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "role VARCHAR(255) NOT NULL,"
                + "PRIMARY KEY(role)" + " );";

        // @formatter:on
    }

    public static String getQueryToCreateRolePermissionsTable(Start start) {
        String tableName = Config.getConfig(start).getUserRolesPermissionsTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "role VARCHAR(255) NOT NULL,"
                + "permission VARCHAR(255) NOT NULL,"
                + "PRIMARY KEY(role, permission),"
                + "FOREIGN KEY(role) REFERENCES " + Config.getConfig(start).getRolesTable()
                +"(role) ON DELETE CASCADE );";

        // @formatter:on
    }

    static String getQueryToCreateRolePermissionsPermissionIndex(Start start) {
        return "CREATE INDEX role_permissions_permission_index ON "
                + Config.getConfig(start).getUserRolesPermissionsTable() + "(permission);";
    }

    public static String getQueryToCreateUserRolesTable(Start start) {
        String tableName = Config.getConfig(start).getUserRolesTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "user_id VARCHAR(128) NOT NULL,"
                + "role VARCHAR(255) NOT NULL,"
                + "PRIMARY KEY(user_id, role),"
                + "FOREIGN KEY(role) REFERENCES " + Config.getConfig(start).getRolesTable()
                + "(role) ON DELETE CASCADE );";

        // @formatter:on
    }

    static String getQueryToCreateUserRolesRoleIndex(Start start) {
        return "CREATE INDEX user_roles_role_index ON " + Config.getConfig(start).getUserRolesTable() + "(role);";
    }

    public static int addRoleToUser(Start start, String userId, String role)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + getConfig(start).getUserRolesTable() + "(user_id, role) VALUES(?, ?);";
        return update(start, QUERY, pst -> {
            pst.setString(1, userId);
            pst.setString(2, role);
        });
    }

    public static String[] getRolesForUser(Start start, String userId) throws SQLException, StorageQueryException {
        String QUERY = "SELECT role FROM " + getConfig(start).getUserRolesTable() + " WHERE user_id = ? ;";
        return execute(start, QUERY, pst -> pst.setString(1, userId), result -> {
            ArrayList<String> roles = new ArrayList<>();
            while (result.next()) {
                roles.add(result.getString("role"));
            }
            return roles.toArray(String[]::new);
        });
    }

    public static String[] getUsersForRole(Start start, String role) throws SQLException, StorageQueryException {
        String QUERY = "SELECT user_id FROM " + getConfig(start).getUserRolesTable() + " WHERE role = ? ";

        return execute(start, QUERY, pst -> pst.setString(1, role), result -> {
            ArrayList<String> permissions = new ArrayList<>();
            while (result.next()) {
                permissions.add(result.getString("user_id"));
            }
            return permissions.toArray(String[]::new);
        });
    }

    public static String[] getPermissionsForRole(Start start, String role) throws SQLException, StorageQueryException {
        String QUERY = "SELECT permission FROM " + getConfig(start).getUserRolesPermissionsTable()
                + " WHERE role = ? ;";

        return execute(start, QUERY, pst -> pst.setString(1, role), result -> {
            ArrayList<String> permissions = new ArrayList<>();
            while (result.next()) {
                permissions.add(result.getString("permission"));
            }
            return permissions.toArray(String[]::new);
        });
    }

    public static String[] getRolesThatHavePermission(Start start, String permission)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT role FROM " + getConfig(start).getUserRolesPermissionsTable() + "WHERE permission = ? ;";
        return execute(start, QUERY, pst -> pst.setString(1, permission), result -> {
            ArrayList<String> roles = new ArrayList<>();
            while (result.next()) {
                roles.add(result.getString("role"));
            }
            return roles.toArray(String[]::new);
        });
    }

    public static boolean deleteRole(Start start, String role)
            throws StorageQueryException, StorageTransactionLogicException {

        // SQLite is not compiled with foreign key constraint and so we must implement
        // cascading deletes here
        return start.startTransaction(con -> {
            boolean response;

            Connection sqlCon = (Connection) con.getConnection();
            ((ConnectionWithLocks) sqlCon).lock(role + getConfig(start).getRolesTable());
            try {
                {
                    String QUERY = "DELETE FROM " + getConfig(start).getRolesTable() + " WHERE role = ? ;";
                    response = update(sqlCon, QUERY, pst -> {
                        pst.setString(1, role);
                    }) == 1;
                }

                {
                    String QUERY = "DELETE FROM " + getConfig(start).getUserRolesPermissionsTable()
                            + " WHERE role = ? ;";
                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, role);
                    });
                }
                {
                    String QUERY = "DELETE FROM " + getConfig(start).getUserRolesTable() + " WHERE role = ?";
                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, role);
                    });
                }

                sqlCon.commit();
                return response;
            } catch (SQLException e) {
                throw new StorageTransactionLogicException(e);
            }
        });
    }

    public static String[] getRoles(Start start) throws SQLException, StorageQueryException {
        String QUERY = "SELECT role FROM " + getConfig(start).getRolesTable();

        return execute(start, QUERY, PreparedStatementValueSetter.NO_OP_SETTER, result -> {
            ArrayList<String> roles = new ArrayList<>();
            while (result.next()) {
                roles.add(result.getString("role"));
            }
            return roles.toArray(String[]::new);
        });
    }

    public static boolean doesRoleExist(Start start, String role) throws SQLException, StorageQueryException {
        String QUERY = "SELECT 1 FROM " + getConfig(start).getRolesTable() + " WHERE role = ?";

        return execute(start, QUERY, pst -> pst.setString(1, role), ResultSet::next);
    }

    public static int deleteAllRolesForUser(Start start, String userId) throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getUserRolesTable() + " WHERE user_id = ?";
        return update(start, QUERY, pst -> pst.setString(1, userId));
    }

    public static boolean deleteRoleForUser_Transaction(Start start, Connection con, String userId, String role)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getUserRolesTable() + " WHERE user_id = ? AND role = ? ;";

        // store the number of rows updated
        int rowUpdatedCount = update(con, QUERY, pst -> {
            pst.setString(1, userId);
            pst.setString(2, role);
        });

        return rowUpdatedCount > 0;
    }

    public static boolean createNewRoleOrDoNothingIfExists_Transaction(Start start, Connection con, String role)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + getConfig(start).getRolesTable() + " VALUES(?) ON CONFLICT(role) DO NOTHING;";

        // store the number of rows updated
        int rowUpdatedCount = update(con, QUERY, pst -> {
            pst.setString(1, role);
        });

        return rowUpdatedCount > 0;
    }

    public static void addPermissionToRoleOrDoNothingIfExists_Transaction(Start start, Connection con, String role,
            String permission) throws SQLException, StorageQueryException {

        String QUERY = "INSERT INTO " + getConfig(start).getUserRolesPermissionsTable()
                + " (role, permission) VALUES(?, ?) ON CONFLICT(role, permission) DO NOTHING";
        update(con, QUERY, pst -> {
            pst.setString(1, role);
            pst.setString(2, permission);
        });
    }

    public static boolean doesRoleExist_transaction(Start start, Connection con, String role)
            throws SQLException, StorageQueryException {

        ((ConnectionWithLocks) con).lock(role + getConfig(start).getRolesTable());

        String QUERY = "SELECT 1 FROM " + getConfig(start).getRolesTable() + " WHERE role = ?";

        return execute(con, QUERY, pst -> pst.setString(1, role), ResultSet::next);
    }

    public static boolean deletePermissionForRole_Transaction(Start start, Connection con, String role,
            String permission) throws SQLException, StorageQueryException {

        String QUERY = "DELETE FROM " + getConfig(start).getUserRolesPermissionsTable()
                + " WHERE role = ? AND permission = ? ";
        // store the number of rows updated
        int rowUpdatedCount = update(con, QUERY, pst -> {
            pst.setString(1, role);
            pst.setString(2, permission);
        });

        return rowUpdatedCount > 0;
    }

    public static int deleteAllPermissionsForRole_Transaction(Start start, Connection con, String role)
            throws SQLException, StorageQueryException {

        String QUERY = "DELETE FROM " + getConfig(start).getUserRolesPermissionsTable() + " WHERE role = ? ";
        // return the number of rows updated
        return update(con, QUERY, pst -> {
            pst.setString(1, role);
        });
    }

}
