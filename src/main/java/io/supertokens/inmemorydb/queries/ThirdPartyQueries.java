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
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.thirdparty.UserInfo;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ThirdPartyQueries {

    static String getQueryToCreateUsersTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getThirdPartyUsersTable() + " ("
                + "third_party_id VARCHAR(28) NOT NULL," + "third_party_user_id VARCHAR(128) NOT NULL,"
                + "user_id CHAR(36) NOT NULL UNIQUE," + "email VARCHAR(256) NOT NULL,"
                + "time_joined BIGINT UNSIGNED NOT NULL," + "PRIMARY KEY (third_party_id, third_party_user_id));";
    }

    static String getQueryToCreateUserPaginationIndex(Start start) {
        return "CREATE INDEX thirdparty_users_pagination_index ON " +
                Config.getConfig(start).getThirdPartyUsersTable() + "(time_joined DESC, user_id " +
                "DESC);";
    }

    public static void signUp(Start start, io.supertokens.pluginInterface.thirdparty.UserInfo userInfo)
            throws SQLException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getThirdPartyUsersTable()
                + "(third_party_id, third_party_user_id, user_id, email, time_joined)"
                + " VALUES(?, ?, ?, ?, ?)";

        try (Connection con = ConnectionPool.getConnection(start);
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, userInfo.thirdParty.id);
            pst.setString(2, userInfo.thirdParty.userId);
            pst.setString(3, userInfo.id);
            pst.setString(4, userInfo.thirdParty.email);
            pst.setLong(5, userInfo.timeJoined);
            pst.executeUpdate();
        }
    }


    public static UserInfo getThirdPartyUserInfoUsingId(Start start, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT user_id, third_party_id, third_party_user_id, email, time_joined FROM "
                + Config.getConfig(start).getThirdPartyUsersTable() + " WHERE user_id = ?";
        try (Connection con = ConnectionPool.getConnection(start);
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return UserInfoRowMapper.getInstance().mapOrThrow(result);
            }
        }
        return null;
    }

    public static UserInfo getThirdPartyUserInfoUsingId(Start start, String thirdPartyId, String thirdPartyUserId)
            throws SQLException, StorageQueryException {

        String QUERY = "SELECT user_id, third_party_id, third_party_user_id, email, time_joined FROM "
                + Config.getConfig(start).getThirdPartyUsersTable() +
                " WHERE third_party_id = ? AND third_party_user_id = ?";
        try (Connection con = ConnectionPool.getConnection(start);
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, thirdPartyId);
            pst.setString(2, thirdPartyUserId);
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return UserInfoRowMapper.getInstance().mapOrThrow(result);
            }
        }
        return null;
    }

    public static void updateUserEmail_Transaction(Start start, Connection con, String thirdPartyId,
                                                   String thirdPartyUserId,
                                                   String newEmail) throws SQLException, StorageQueryException {
        String QUERY = "UPDATE " + Config.getConfig(start).getThirdPartyUsersTable()
                + " SET email = ? WHERE third_party_id = ? AND third_party_user_id = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, newEmail);
            pst.setString(2, thirdPartyId);
            pst.setString(3, thirdPartyUserId);
            pst.executeUpdate();
        }
    }

    public static UserInfo getUserInfoUsingId_Transaction(Start start, Connection con,
                                                          String thirdPartyId, String thirdPartyUserId)
            throws SQLException, StorageQueryException {

        ((ConnectionWithLocks) con)
                .lock(thirdPartyId + "," + thirdPartyUserId + Config.getConfig(start).getThirdPartyUsersTable());

        String QUERY = "SELECT user_id, third_party_id, third_party_user_id, email, time_joined FROM "
                + Config.getConfig(start).getThirdPartyUsersTable() +
                " WHERE third_party_id = ? AND third_party_user_id = ?";
        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, thirdPartyId);
            pst.setString(2, thirdPartyUserId);
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return UserInfoRowMapper.getInstance().mapOrThrow(result);
            }
        }
        return null;
    }

    public static UserInfo[] getThirdPartyUsers(Start start, @NotNull Integer limit, @NotNull String timeJoinedOrder)
            throws SQLException, StorageQueryException {
        String QUERY =
                "SELECT user_id, third_party_id, third_party_user_id, email, time_joined FROM " +
                        Config.getConfig(start).getThirdPartyUsersTable() +
                        " ORDER BY time_joined " + timeJoinedOrder + ", user_id DESC LIMIT ?";
        try (Connection con = ConnectionPool.getConnection(start);
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setInt(1, limit);
            ResultSet result = pst.executeQuery();
            List<UserInfo> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(UserInfoRowMapper.getInstance().mapOrThrow(result));
            }
            UserInfo[] finalResult = new UserInfo[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        }
    }

    public static UserInfo[] getThirdPartyUsers(Start start, @NotNull String userId, @NotNull Long timeJoined,
                                                @NotNull Integer limit,
                                                @NotNull String timeJoinedOrder)
            throws SQLException, StorageQueryException {
        String timeJoinedOrderSymbol = timeJoinedOrder.equals("ASC") ? ">" : "<";
        String QUERY =
                "SELECT user_id, third_party_id, third_party_user_id, email, time_joined FROM " +
                        Config.getConfig(start).getThirdPartyUsersTable() +
                        " WHERE time_joined " + timeJoinedOrderSymbol +
                        " ? OR (time_joined = ? AND user_id <= ?) ORDER BY time_joined " + timeJoinedOrder +
                        ", user_id DESC LIMIT ?";
        try (Connection con = ConnectionPool.getConnection(start);
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setLong(1, timeJoined);
            pst.setLong(2, timeJoined);
            pst.setString(3, userId);
            pst.setInt(4, limit);
            ResultSet result = pst.executeQuery();
            List<UserInfo> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(UserInfoRowMapper.getInstance().mapOrThrow(result));
            }
            UserInfo[] finalResult = new UserInfo[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        }
    }

    public static long getUsersCount(Start start) throws SQLException {
        String QUERY =
                "SELECT COUNT(*) as total FROM " +
                        Config.getConfig(start).getThirdPartyUsersTable();
        try (Connection con = ConnectionPool.getConnection(start);
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return result.getLong("total");
            }
            return 0;
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
            return new UserInfo(result.getString("user_id"), new UserInfo.ThirdParty(
                    result.getString("third_party_id"),
                    result.getString("third_party_user_id"),
                    result.getString("email")),
                    result.getLong("time_joined"));
        }
    }
}
