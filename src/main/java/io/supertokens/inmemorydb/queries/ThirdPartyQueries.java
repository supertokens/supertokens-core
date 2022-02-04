/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

import io.supertokens.inmemorydb.ConnectionPool;
import io.supertokens.inmemorydb.ConnectionWithLocks;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.thirdparty.UserInfo;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ThirdPartyQueries {

    static String getQueryToCreateUsersTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getThirdPartyUsersTable() + " ("
                + "third_party_id VARCHAR(28) NOT NULL," + "third_party_user_id VARCHAR(128) NOT NULL,"
                + "user_id CHAR(36) NOT NULL UNIQUE," + "email VARCHAR(256) NOT NULL,"
                + "time_joined BIGINT UNSIGNED NOT NULL," + "PRIMARY KEY (third_party_id, third_party_user_id));";
    }

    public static void signUp(Start start, io.supertokens.pluginInterface.thirdparty.UserInfo userInfo)
            throws StorageQueryException, StorageTransactionLogicException {
        start.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try {
                {
                    String QUERY = "INSERT INTO " + Config.getConfig(start).getUsersTable()
                            + "(user_id, recipe_id, time_joined)" + " VALUES(?, ?, ?)";
                    try (PreparedStatement pst = sqlCon.prepareStatement(QUERY)) {
                        pst.setString(1, userInfo.id);
                        pst.setString(2, RECIPE_ID.THIRD_PARTY.toString());
                        pst.setLong(3, userInfo.timeJoined);
                        pst.executeUpdate();
                    }
                }

                {
                    String QUERY = "INSERT INTO " + Config.getConfig(start).getThirdPartyUsersTable()
                            + "(third_party_id, third_party_user_id, user_id, email, time_joined)"
                            + " VALUES(?, ?, ?, ?, ?)";
                    try (PreparedStatement pst = sqlCon.prepareStatement(QUERY)) {
                        pst.setString(1, userInfo.thirdParty.id);
                        pst.setString(2, userInfo.thirdParty.userId);
                        pst.setString(3, userInfo.id);
                        pst.setString(4, userInfo.email);
                        pst.setLong(5, userInfo.timeJoined);
                        pst.executeUpdate();
                    }
                }

                sqlCon.commit();
            } catch (SQLException throwables) {
                throw new StorageTransactionLogicException(throwables);
            }
            return null;
        });
    }

    public static void deleteUser(Start start, String userId)
            throws StorageQueryException, StorageTransactionLogicException {
        start.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try {
                {
                    String QUERY = "DELETE FROM " + Config.getConfig(start).getUsersTable()
                            + " WHERE user_id = ? AND recipe_id = ?";
                    try (PreparedStatement pst = sqlCon.prepareStatement(QUERY)) {
                        pst.setString(1, userId);
                        pst.setString(2, RECIPE_ID.THIRD_PARTY.toString());
                        pst.executeUpdate();
                    }
                }

                {
                    String QUERY = "DELETE FROM " + Config.getConfig(start).getThirdPartyUsersTable()
                            + " WHERE user_id = ? ";
                    try (PreparedStatement pst = sqlCon.prepareStatement(QUERY)) {
                        pst.setString(1, userId);
                        pst.executeUpdate();
                    }
                }

                sqlCon.commit();
            } catch (SQLException throwables) {
                throw new StorageTransactionLogicException(throwables);
            }
            return null;
        });
    }

    public static UserInfo getThirdPartyUserInfoUsingId(Start start, String userId)
            throws SQLException, StorageQueryException {
        List<String> input = new ArrayList<>();
        input.add(userId);
        List<UserInfo> result = getUsersInfoUsingIdList(start, input);
        if (result.size() == 1) {
            return result.get(0);
        }
        return null;
    }

    public static List<UserInfo> getUsersInfoUsingIdList(Start start, List<String> ids)
            throws SQLException, StorageQueryException {
        List<UserInfo> finalResult = new ArrayList<>();
        if (ids.size() > 0) {
            StringBuilder QUERY = new StringBuilder(
                    "SELECT user_id, third_party_id, third_party_user_id, email, time_joined FROM "
                            + Config.getConfig(start).getThirdPartyUsersTable());
            QUERY.append(" WHERE user_id IN (");
            for (int i = 0; i < ids.size(); i++) {

                QUERY.append("?");
                if (i != ids.size() - 1) {
                    // not the last element
                    QUERY.append(",");
                }
            }
            QUERY.append(")");

            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pst = con.prepareStatement(QUERY.toString())) {
                for (int i = 0; i < ids.size(); i++) {
                    // i+1 cause this starts with 1 and not 0
                    pst.setString(i + 1, ids.get(i));
                }
                try (ResultSet result = pst.executeQuery()) {
                    while (result.next()) {
                        finalResult.add(UserInfoRowMapper.getInstance().mapOrThrow(result));
                    }
                }
            }
        }
        return finalResult;
    }

    public static UserInfo getThirdPartyUserInfoUsingId(Start start, String thirdPartyId, String thirdPartyUserId)
            throws SQLException, StorageQueryException {

        String QUERY = "SELECT user_id, third_party_id, third_party_user_id, email, time_joined FROM "
                + Config.getConfig(start).getThirdPartyUsersTable()
                + " WHERE third_party_id = ? AND third_party_user_id = ?";
        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, thirdPartyId);
            pst.setString(2, thirdPartyUserId);
            try (ResultSet result = pst.executeQuery()) {
                if (result.next()) {
                    return UserInfoRowMapper.getInstance().mapOrThrow(result);
                }
            }
        }
        return null;
    }

    public static void updateUserEmail_Transaction(Start start, Connection con, String thirdPartyId,
            String thirdPartyUserId, String newEmail) throws SQLException {
        String QUERY = "UPDATE " + Config.getConfig(start).getThirdPartyUsersTable()
                + " SET email = ? WHERE third_party_id = ? AND third_party_user_id = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, newEmail);
            pst.setString(2, thirdPartyId);
            pst.setString(3, thirdPartyUserId);
            pst.executeUpdate();
        }
    }

    public static UserInfo getUserInfoUsingId_Transaction(Start start, Connection con, String thirdPartyId,
            String thirdPartyUserId) throws SQLException, StorageQueryException {

        ((ConnectionWithLocks) con)
                .lock(thirdPartyId + "," + thirdPartyUserId + Config.getConfig(start).getThirdPartyUsersTable());

        String QUERY = "SELECT user_id, third_party_id, third_party_user_id, email, time_joined FROM "
                + Config.getConfig(start).getThirdPartyUsersTable()
                + " WHERE third_party_id = ? AND third_party_user_id = ?";
        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, thirdPartyId);
            pst.setString(2, thirdPartyUserId);
            try (ResultSet result = pst.executeQuery()) {
                if (result.next()) {
                    return UserInfoRowMapper.getInstance().mapOrThrow(result);
                }
            }
        }
        return null;
    }

    @Deprecated
    public static UserInfo[] getThirdPartyUsers(Start start, @NotNull Integer limit, @NotNull String timeJoinedOrder)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT user_id, third_party_id, third_party_user_id, email, time_joined FROM "
                + Config.getConfig(start).getThirdPartyUsersTable() + " ORDER BY time_joined " + timeJoinedOrder
                + ", user_id DESC LIMIT ?";
        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setInt(1, limit);

            return resultToArray(pst.executeQuery());
        }
    }

    @Deprecated
    public static UserInfo[] getThirdPartyUsers(Start start, @NotNull String userId, @NotNull Long timeJoined,
            @NotNull Integer limit, @NotNull String timeJoinedOrder) throws SQLException, StorageQueryException {
        String timeJoinedOrderSymbol = timeJoinedOrder.equals("ASC") ? ">" : "<";
        String QUERY = "SELECT user_id, third_party_id, third_party_user_id, email, time_joined FROM "
                + Config.getConfig(start).getThirdPartyUsersTable() + " WHERE time_joined " + timeJoinedOrderSymbol
                + " ? OR (time_joined = ? AND user_id <= ?) ORDER BY time_joined " + timeJoinedOrder
                + ", user_id DESC LIMIT ?";
        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setLong(1, timeJoined);
            pst.setLong(2, timeJoined);
            pst.setString(3, userId);
            pst.setInt(4, limit);

            return resultToArray(pst.executeQuery());
        }
    }

    public static UserInfo[] getThirdPartyUsersByEmail(Start start, @Nonnull String email)
            throws SQLException, StorageQueryException {

        String sqlQuery = "SELECT user_id, third_party_id, third_party_user_id, email, time_joined FROM "
                + Config.getConfig(start).getThirdPartyUsersTable() + " WHERE email = ?";

        try (Connection conn = ConnectionPool.getConnection(start);
                PreparedStatement statement = conn.prepareStatement(sqlQuery)) {
            statement.setString(1, email);

            return resultToArray(statement.executeQuery());
        }
    }

    public static UserInfo[] resultToArray(ResultSet result) throws SQLException, StorageQueryException {
        List<UserInfo> users = new ArrayList<>();

        while (result.next()) {
            users.add(UserInfoRowMapper.getInstance().mapOrThrow(result));
        }

        return users.toArray(UserInfo[]::new);
    }

    @Deprecated
    public static long getUsersCount(Start start) throws SQLException {
        String QUERY = "SELECT COUNT(*) as total FROM " + Config.getConfig(start).getThirdPartyUsersTable();
        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            try (ResultSet result = pst.executeQuery()) {
                if (result.next()) {
                    return result.getLong("total");
                }
                return 0;
            }
        }
    }

    private static class UserInfoRowMapper implements RowMapper<UserInfo, ResultSet> {
        private static final UserInfoRowMapper INSTANCE = new UserInfoRowMapper();

        private UserInfoRowMapper() {
        }

        private static UserInfoRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public UserInfo map(ResultSet result) throws Exception {
            return new UserInfo(result.getString("user_id"), result.getString("email"),
                    new UserInfo.ThirdParty(result.getString("third_party_id"),
                            result.getString("third_party_user_id")),
                    result.getLong("time_joined"));
        }
    }
}
