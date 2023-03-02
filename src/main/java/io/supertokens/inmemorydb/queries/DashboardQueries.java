/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import io.supertokens.inmemorydb.ResultSetValueExtractor;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.dashboard.DashboardSessionInfo;
import io.supertokens.pluginInterface.dashboard.DashboardUser;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class DashboardQueries {
    public static String getQueryToCreateDashboardUsersTable(Start start) {
        String tableName = Config.getConfig(start).getDashboardUsersTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "user_id CHAR(36) NOT NULL,"
                + "email VARCHAR(256) NOT NULL UNIQUE,"
                + "password_hash VARCHAR(256) NOT NULL,"
                + "time_joined BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY(user_id));";
        // @formatter:on
    }

    public static String getQueryToCreateDashboardUserSessionsTable(Start start) {
        String tableName = Config.getConfig(start).getDashboardSessionsTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "session_id CHAR(36) NOT NULL,"
                + "user_id CHAR(36) NOT NULL,"
                + "time_created BIGINT UNSIGNED NOT NULL,"
                + "expiry BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY(session_id),"
                + "FOREIGN KEY(user_id) REFERENCES " + Config.getConfig(start).getDashboardUsersTable()
                + "(user_id) ON DELETE CASCADE);";
        // @formatter:on
    }

    static String getQueryToCreateDashboardUserSessionsExpiryIndex(Start start) {
        return "CREATE INDEX dashboard_user_sessions_expiry_index ON "
                + Config.getConfig(start).getDashboardSessionsTable() + "(expiry);";
    }

    public static void createDashboardUser(Start start, String userId, String email, String passwordHash,
            long timeJoined) throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getDashboardUsersTable()
                + "(user_id, email, password_hash, time_joined)" + " VALUES(?, ?, ?, ?)";
        update(start, QUERY, pst -> {
            pst.setString(1, userId);
            pst.setString(2, email);
            pst.setString(3, passwordHash);
            pst.setLong(4, timeJoined);
        });
    }

    public static DashboardUser[] getAllDashBoardUsers(Start start) throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM "
                + Config.getConfig(start).getDashboardUsersTable() + " ORDER BY time_joined ASC";
        return execute(start, QUERY, null, new DashboardUserInfoResultExtractor());
    }

    public static boolean deleteDashboardUserWithUserId(Start start, String userId)
            throws StorageTransactionLogicException, StorageQueryException {

        return start.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            int rowUpdatedCount = 0;
            try {
                {
                    // delete the user from the dashboard_users table
                    String QUERY = "DELETE FROM " + Config.getConfig(start).getDashboardUsersTable()
                            + " WHERE user_id = ?";
                    // store the number of rows updated
                    rowUpdatedCount = update(start, QUERY, pst -> pst.setString(1, userId));
                }

                {
                    // since SQLite does not support foreign key constraints with delete cascade we
                    // will need to manually delete the sessions associated with the user
                    String QUERY = "DELETE FROM " + Config.getConfig(start).getDashboardSessionsTable()
                            + " WHERE user_id = ?";
                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, userId);
                    });
                }

                sqlCon.commit();
            } catch (SQLException throwables) {
                throw new StorageTransactionLogicException(throwables);
            }
            return rowUpdatedCount > 0;
        });
    }

    public static boolean deleteDashboardUserSessionWithSessionId(Start start, String sessionId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getDashboardSessionsTable()
                + " WHERE session_id = ?";
        // store the number of rows updated
        int rowUpdatedCount = update(start, QUERY, pst -> pst.setString(1, sessionId));

        return rowUpdatedCount > 0;
    }

    public static DashboardUser getDashboardUserByEmail(Start start, String email)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM "
                + Config.getConfig(start).getDashboardUsersTable() + " WHERE email = ?";
        return execute(start, QUERY, pst -> pst.setString(1, email), result -> {
            if (result.next()) {
                return DashboardInfoMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static DashboardUser getDashboardUserByUserId(Start start, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM "
                + Config.getConfig(start).getDashboardUsersTable() + " WHERE user_id = ?";
        return execute(start, QUERY, pst -> pst.setString(1, userId), result -> {
            if (result.next()) {
                return DashboardInfoMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static boolean updateDashboardUsersEmailWithUserId_Transaction(Start start, Connection con, String userId,
            String newEmail) throws SQLException, StorageQueryException {
        String QUERY = "UPDATE " + Config.getConfig(start).getDashboardUsersTable()
                + " SET email = ? WHERE user_id = ?";
        int rowsUpdated = update(con, QUERY, pst -> {
            pst.setString(1, newEmail);
            pst.setString(2, userId);
        });

        return rowsUpdated > 0;
    }

    public static boolean updateDashboardUsersPasswordWithUserId_Transaction(Start start, Connection con, String userId,
            String newPassword) throws SQLException, StorageQueryException {
        String QUERY = "UPDATE " + Config.getConfig(start).getDashboardUsersTable()
                + " SET password_hash = ? WHERE user_id = ?";
        int rowsUpdated = update(con, QUERY, pst -> {
            pst.setString(1, newPassword);
            pst.setString(2, userId);
        });
        
        return rowsUpdated > 0;
    }

    public static void createDashboardSession(Start start, String userId, String sessionId, long timeCreated,
            long expiry) throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getDashboardSessionsTable()
                + "(user_id, session_id, time_created, expiry)" + " VALUES(?, ?, ?, ?)";
        update(start, QUERY, pst -> {
            pst.setString(1, userId);
            pst.setString(2, sessionId);
            pst.setLong(3, timeCreated);
            pst.setLong(4, expiry);
        });
    }

    public static DashboardSessionInfo getSessionInfoWithSessionId(Start start, String sessionId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM "
                + Config.getConfig(start).getDashboardSessionsTable() + " WHERE session_id = ?";
        return execute(start, QUERY, pst -> pst.setString(1, sessionId), result -> {
            if (result.next()) {
                return DashboardSessionInfoMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static DashboardSessionInfo[] getAllSessionsForUserId(Start start, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM "
                + Config.getConfig(start).getDashboardSessionsTable() + " WHERE user_id = ?";
        return execute(start, QUERY, pst -> pst.setString(1, userId), new DashboardSessionInfoResultExtractor());
    }

    public static void deleteExpiredSessions(Start start) throws SQLException, StorageQueryException {
        long currentTimeMillis = System.currentTimeMillis();
        String QUERY = "DELETE FROM " + Config.getConfig(start).getDashboardSessionsTable()
                + " WHERE expiry < ?";
        // store the number of rows updated
        update(start, QUERY, pst -> pst.setLong(1, currentTimeMillis));
    }

    private static class DashboardInfoMapper implements RowMapper<DashboardUser, ResultSet> {
        private static final DashboardInfoMapper INSTANCE = new DashboardInfoMapper();

        private DashboardInfoMapper() {
        }

        private static DashboardInfoMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public DashboardUser map(ResultSet rs) throws Exception {

            return new DashboardUser(rs.getString("user_id"), rs.getString("email"), rs.getString("password_hash"),
                    rs.getLong("time_joined"));
        }
    }

    private static class DashboardUserInfoResultExtractor implements ResultSetValueExtractor<DashboardUser[]> {
        @Override
        public DashboardUser[] extract(ResultSet result) throws SQLException, StorageQueryException {
            List<DashboardUser> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(DashboardInfoMapper.getInstance().mapOrThrow(result));
            }
            return temp.toArray(DashboardUser[]::new);
        }
    }

    private static class DashboardSessionInfoMapper implements RowMapper<DashboardSessionInfo, ResultSet> {
        private static final DashboardSessionInfoMapper INSTANCE = new DashboardSessionInfoMapper();

        private DashboardSessionInfoMapper() {
        }

        private static DashboardSessionInfoMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public DashboardSessionInfo map(ResultSet rs) throws Exception {
            return new DashboardSessionInfo(rs.getString("user_id"), rs.getString("session_id"),
                    rs.getLong("time_created"), rs.getLong("expiry"));
        }
    }

    private static class DashboardSessionInfoResultExtractor
            implements ResultSetValueExtractor<DashboardSessionInfo[]> {
        @Override
        public DashboardSessionInfo[] extract(ResultSet result) throws SQLException, StorageQueryException {
            List<DashboardSessionInfo> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(DashboardSessionInfoMapper.getInstance().mapOrThrow(result));
            }
            return temp.toArray(DashboardSessionInfo[]::new);
        }
    }
}