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
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.UserInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class EmailPasswordQueries {

    static String getQueryToCreateUsersTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getUsersTable() + " ("
                + "user_id CHAR(36) NOT NULL," + "email VARCHAR(256) NOT NULL UNIQUE,"
                + "password_hash VARCHAR(128) NOT NULL," + "time_joined BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY (user_id));";
    }

    static String getQueryToCreatePasswordResetTokensTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getPasswordResetTokensTable() + " ("
                + "user_id CHAR(36) NOT NULL," + "token VARCHAR(128) NOT NULL UNIQUE,"
                + "token_expiry BIGINT UNSIGNED NOT NULL," + "PRIMARY KEY (user_id, token),"
                + "FOREIGN KEY (user_id) REFERENCES " + Config.getConfig(start).getUsersTable() +
                "(user_id) ON DELETE CASCADE ON UPDATE CASCADE);";
    }

    static String getQueryToCreatePasswordResetTokenExpiryIndex(Start start) {
        return "CREATE INDEX email_password_password_reset_token_expiry_index ON " +
                Config.getConfig(start).getPasswordResetTokensTable() +
                "(token_expiry);";
    }

    public static void deleteExpiredPasswordResetTokens(Start start) throws SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getPasswordResetTokensTable() +
                " WHERE token_expiry < ?";

        try (Connection con = ConnectionPool.getConnection(start);
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setLong(1, System.currentTimeMillis());
            pst.executeUpdate();
        }
    }

    public static void updateUsersPassword_Transaction(Start start, Connection con,
                                                       String userId, String newPassword)
            throws SQLException {
        String QUERY = "UPDATE " + Config.getConfig(start).getUsersTable()
                + " SET password_hash = ? WHERE user_id = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, newPassword);
            pst.setString(2, userId);
            pst.executeUpdate();
        }
    }

    public static void deleteAllPasswordResetTokensForUser_Transaction(Start start,
                                                                       Connection con, String userId)
            throws SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getPasswordResetTokensTable()
                + " WHERE user_id = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            pst.executeUpdate();
        }
    }

    public static PasswordResetTokenInfo[] getAllPasswordResetTokenInfoForUser(Start start, String userId)
            throws SQLException {
        String QUERY =
                "SELECT user_id, token, token_expiry FROM " + Config.getConfig(start).getPasswordResetTokensTable() +
                        " WHERE user_id = ?";

        try (Connection con = ConnectionPool.getConnection(start);
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            ResultSet result = pst.executeQuery();
            List<PasswordResetTokenInfo> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(new PasswordResetTokenInfo(result.getString("user_id"),
                        result.getString("token"),
                        result.getLong("token_expiry")));
            }
            PasswordResetTokenInfo[] finalResult = new PasswordResetTokenInfo[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        }
    }

    public static PasswordResetTokenInfo[] getAllPasswordResetTokenInfoForUser_Transaction(Start start, Connection con,
                                                                                           String userId)
            throws SQLException {

        ((ConnectionWithLocks) con).lock(userId);

        String QUERY =
                "SELECT user_id, token, token_expiry FROM " + Config.getConfig(start).getPasswordResetTokensTable() +
                        " WHERE user_id = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            ResultSet result = pst.executeQuery();
            List<PasswordResetTokenInfo> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(new PasswordResetTokenInfo(result.getString("user_id"),
                        result.getString("token"),
                        result.getLong("token_expiry")));
            }
            PasswordResetTokenInfo[] finalResult = new PasswordResetTokenInfo[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        }
    }

    public static PasswordResetTokenInfo getPasswordResetTokenInfo(Start start, String token) throws SQLException {
        String QUERY = "SELECT user_id, token, token_expiry FROM "
                + Config.getConfig(start).getPasswordResetTokensTable() + " WHERE token = ?";
        try (Connection con = ConnectionPool.getConnection(start);
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, token);
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return new PasswordResetTokenInfo(result.getString("user_id"),
                        result.getString("token"),
                        result.getLong("token_expiry"));
            }
        }
        return null;
    }

    public static void addPasswordResetToken(Start start, String userId, String tokenHash, long expiry)
            throws SQLException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getPasswordResetTokensTable()
                + "(user_id, token, token_expiry)"
                + " VALUES(?, ?, ?)";

        try (Connection con = ConnectionPool.getConnection(start);
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            pst.setString(2, tokenHash);
            pst.setLong(3, expiry);
            pst.executeUpdate();
        }
    }

    public static void signUp(Start start, String userId, String email, String passwordHash, long timeJoined)
            throws SQLException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getUsersTable()
                + "(user_id, email, password_hash, time_joined)"
                + " VALUES(?, ?, ?, ?)";

        try (Connection con = ConnectionPool.getConnection(start);
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            pst.setString(2, email);
            pst.setString(3, passwordHash);
            pst.setLong(4, timeJoined);
            pst.executeUpdate();
        }
    }

    public static UserInfo getUserInfoUsingId(Start start, String id) throws SQLException {
        String QUERY = "SELECT user_id, email, password_hash, time_joined FROM "
                + Config.getConfig(start).getUsersTable() + " WHERE user_id = ?";
        try (Connection con = ConnectionPool.getConnection(start);
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, id);
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return new UserInfo(result.getString("user_id"), result.getString("email"),
                        result.getString("password_hash"),
                        result.getLong("time_joined"));
            }
        }
        return null;
    }

    public static UserInfo getUserInfoUsingEmail(Start start, String email) throws SQLException {
        String QUERY = "SELECT user_id, email, password_hash, time_joined FROM "
                + Config.getConfig(start).getUsersTable() + " WHERE email = ?";
        try (Connection con = ConnectionPool.getConnection(start);
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, email);
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return new UserInfo(result.getString("user_id"), result.getString("email"),
                        result.getString("password_hash"),
                        result.getLong("time_joined"));
            }
        }
        return null;
    }
}
