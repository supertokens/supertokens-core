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
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.emailpassword.UserInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class EmailPasswordQueries {

    static String getQueryToCreateUsersTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getUsersTable() + " ("
                + "user_id CHAR(36) NOT NULL," + "email VARCHAR(256) NOT NULL UNIQUE,"
                + "password_hash VARCHAR(128) NOT NULL," + "time_joined BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY (user_id));";
    }

    static String getQueryToCreatePasswordResetTokensTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getPasswordResetTokensTable() + " ("
                + "user_id CHAR(36) NOT NULL," + "token VARCHAR(80) NOT NULL UNIQUE,"
                + "token_expiry BIGINT UNSIGNED NOT NULL," + "PRIMARY KEY (user_id, token),"
                + "FOREIGN KEY (user_id) REFERENCES " + Config.getConfig(start).getUsersTable() +
                "(user_id) ON DELETE CASCADE ON UPDATE CASCADE);" +
                "CREATE INDEX token_expiry_index ON " + Config.getConfig(start).getPasswordResetTokensTable() +
                "(token_expiry);";
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
