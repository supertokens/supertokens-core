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

package io.supertokens.inmemorydb;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.KeyValueInfoWithLastUpdated;
import io.supertokens.pluginInterface.noSqlStorage.NoSQLStorage_1;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.pluginInterface.tokenInfo.PastTokenInfo;
import io.supertokens.utils.Utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Queries {

    private static boolean doesTableExists(String tableName) {
        try {
            String QUERY = "SELECT 1 FROM " + tableName + " LIMIT 1";
            try (Connection con = ConnectionPool.getConnection();
                 PreparedStatement pst = con.prepareStatement(QUERY)) {
                pst.executeQuery();
            }
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private static String getQueryToCreateKeyValueTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getKeyValueTable() + " (" + "name VARCHAR(128),"
                + "value TEXT," + "last_updated_sign TEXT," + "created_at_time BIGINT ," +
                "PRIMARY KEY(name)" + " );";
    }

    private static String getQueryToCreateSessionInfoTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getSessionInfoTable() + " ("
                + "session_handle VARCHAR(255) NOT NULL," + "user_id VARCHAR(128) NOT NULL,"
                + "refresh_token_hash_2 VARCHAR(128) NOT NULL," + "session_data TEXT,"
                + "expires_at BIGINT  NOT NULL," + "created_at_time BIGINT NOT NULL," +
                "jwt_user_payload TEXT," + "last_updated_sign TEXT ," + "PRIMARY KEY(session_handle)" + " );";

    }

    private static String getQueryToCreatePastTokensTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getPastTokensTable() + " ("
                + "refresh_token_hash_2 VARCHAR(128) NOT NULL," + "parent_refresh_token_hash_2 VARCHAR(128) NOT NULL,"
                + "session_handle VARCHAR(255) NOT NULL," + "created_at_time BIGINT NOT NULL,"
                + "PRIMARY KEY(refresh_token_hash_2)" + " );";

    }

    static void createTablesIfNotExists(Start start) throws SQLException {
        if (!doesTableExists(Config.getConfig(start).getKeyValueTable())) {
            try (Connection con = ConnectionPool.getConnection();
                 PreparedStatement pst = con.prepareStatement(getQueryToCreateKeyValueTable(start))) {
                pst.executeUpdate();
            }
        }

        if (!doesTableExists(Config.getConfig(start).getSessionInfoTable())) {
            try (Connection con = ConnectionPool.getConnection();
                 PreparedStatement pst = con.prepareStatement(getQueryToCreateSessionInfoTable(start))) {
                pst.executeUpdate();
            }
        }

        if (!doesTableExists(Config.getConfig(start).getPastTokensTable())) {
            try (Connection con = ConnectionPool.getConnection();
                 PreparedStatement pst = con.prepareStatement(getQueryToCreatePastTokensTable(start))) {
                pst.executeUpdate();
            }
        }
    }

    static boolean setKeyValue_Transaction(Start start, String key, KeyValueInfoWithLastUpdated info)
            throws SQLException {
        // here we want to do something like insert on conflict, but not exactly that since if the user has
        // specificed info
        // .lastUpdatedSign, then it must only be an update operation and it should not create a new document. So we
        // do an update if that is not null. Else we do an insert.

        if (info.lastUpdatedSign != null) {
            String UPDATE = "UPDATE " + Config.getConfig(start).getKeyValueTable() +
                    " SET value = ?, created_at_time = ?, last_updated_sign = ? WHERE name = ? AND last_updated_sign " +
                    "= ?";
            try (Connection con = ConnectionPool.getConnection();
                 PreparedStatement pst = con.prepareStatement(UPDATE)) {
                pst.setString(1, info.value);
                pst.setLong(2, info.createdAtTime);
                pst.setString(3, Utils.getUUID());
                pst.setString(4, key);
                pst.setString(5, info.lastUpdatedSign);
                int numberOfRowsAffected = pst.executeUpdate();
                return numberOfRowsAffected == 1;
            }

        } else {
            String INSERT = "INSERT INTO " + Config.getConfig(start).getKeyValueTable()
                    + "(name, value, created_at_time, last_updated_sign) VALUES(?, ?, ?, ?) ";
            try (Connection con = ConnectionPool.getConnection();
                 PreparedStatement pst = con.prepareStatement(INSERT)) {
                pst.setString(1, key);
                pst.setString(2, info.value);
                pst.setLong(3, info.createdAtTime);
                pst.setString(4, Utils.getUUID());
                pst.executeUpdate();
                return true;
            } catch (SQLException e) {
                if (e.getErrorCode() != 19) {
                    throw e;
                }
            }
            return false;
        }

    }

    static void setKeyValue(Start start, String key, KeyValueInfo info)
            throws SQLException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getKeyValueTable()
                + "(name, value, created_at_time, last_updated_sign) VALUES(?, ?, ?, ?) "
                + "ON CONFLICT (name) DO UPDATE SET value = ?, created_at_time = ?, last_updated_sign = ?";

        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, key);
            pst.setString(2, info.value);
            pst.setLong(3, info.createdAtTime);
            pst.setString(4, Utils.getUUID());
            pst.setString(5, info.value);
            pst.setLong(6, info.createdAtTime);
            pst.setString(7, Utils.getUUID());
            pst.executeUpdate();
        }
    }


    static KeyValueInfo getKeyValue(Start start, String key) throws SQLException {
        String QUERY = "SELECT value, created_at_time FROM "
                + Config.getConfig(start).getKeyValueTable() + " WHERE name = ?";

        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, key);
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return new KeyValueInfo(result.getString("value"), result.getLong("created_at_time"));
            }
        }
        return null;
    }

    static KeyValueInfoWithLastUpdated getKeyValue_Transaction(Start start, String key)
            throws SQLException {

        String QUERY = "SELECT value, created_at_time, last_updated_sign FROM "
                + Config.getConfig(start).getKeyValueTable() + " WHERE name = ? ";

        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, key);
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return new KeyValueInfoWithLastUpdated(result.getString("value"), result.getLong("created_at_time"),
                        result.getString("last_updated_sign"));
            }
        }
        return null;
    }

    static PastTokenInfo getPastTokenInfo(Start start, String refreshTokenHash2) throws SQLException {
        String QUERY = "SELECT parent_refresh_token_hash_2, session_handle, created_at_time FROM "
                + Config.getConfig(start).getPastTokensTable() + " WHERE refresh_token_hash_2 = ? ";

        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, refreshTokenHash2);
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return new PastTokenInfo(refreshTokenHash2, result.getString("session_handle"),
                        result.getString("parent_refresh_token_hash_2"), result.getLong("created_at_time"));
            }
            return null;
        }
    }

    static void insertPastTokenInfo(Start start, PastTokenInfo info) throws SQLException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getPastTokensTable()
                + "(refresh_token_hash_2, parent_refresh_token_hash_2, session_handle, created_at_time)"
                + " VALUES(?, ?, ?, ?)";

        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, info.refreshTokenHash2);
            pst.setString(2, info.parentRefreshTokenHash2);
            pst.setString(3, info.sessionHandle);
            pst.setLong(4, info.createdTime);
            pst.executeUpdate();
        }
    }

    static int getNumberOfPastTokens(Start start) throws SQLException {
        String QUERY = "SELECT count(*) as num FROM " + Config.getConfig(start).getPastTokensTable();

        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return result.getInt("num");
            }
            throw new SQLException("Should not have come here.");
        }
    }

    static void createNewSession(Start start, String sessionHandle, String userId, String refreshTokenHash2,
                                 JsonObject userDataInDatabase, long expiry, JsonObject userDataInJWT,
                                 long createdAtTime)
            throws SQLException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getSessionInfoTable()
                + "(session_handle, user_id, refresh_token_hash_2, session_data, expires_at, jwt_user_payload, " +
                "created_at_time, last_updated_sign)"
                + " VALUES(?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, sessionHandle);
            pst.setString(2, userId);
            pst.setString(3, refreshTokenHash2);
            pst.setString(4, userDataInDatabase.toString());
            pst.setLong(5, expiry);
            pst.setString(6, userDataInJWT.toString());
            pst.setLong(7, createdAtTime);
            pst.setString(8, Utils.getUUID());
            pst.executeUpdate();
        }
    }

    static NoSQLStorage_1.SessionInfoWithLastUpdated getSessionInfo_Transaction(Start start,
                                                                                String sessionHandle)
            throws SQLException {
        String QUERY =
                "SELECT session_handle, user_id, refresh_token_hash_2, session_data, expires_at, last_updated_sign," +
                        "created_at_time, jwt_user_payload FROM "
                        + Config.getConfig(start).getSessionInfoTable() + " WHERE session_handle = ? ";
        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, sessionHandle);
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return new NoSQLStorage_1.SessionInfoWithLastUpdated(sessionHandle, result.getString("user_id"),
                        result.getString("refresh_token_hash_2"),
                        new JsonParser().parse(result.getString("session_data")).getAsJsonObject(),
                        result.getLong("expires_at"),
                        new JsonParser().parse(result.getString("jwt_user_payload")).getAsJsonObject(),
                        result.getLong("created_at_time"),
                        result.getString("last_updated_sign"));
            }
        }
        return null;
    }

    static boolean updateSessionInfo_Transaction(Start start, String sessionHandle,
                                                 String refreshTokenHash2, long expiry, String lastUpdatedSign)
            throws SQLException {
        if (lastUpdatedSign == null) {
            throw new SQLException(new Exception("lastUpdatedSign cannot be null for this update operation"));
        }
        String QUERY = "UPDATE " + Config.getConfig(start).getSessionInfoTable()
                + " SET refresh_token_hash_2 = ?, expires_at = ?, last_updated_sign = ?"
                + " WHERE session_handle = ? AND last_updated_sign = ?";

        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, refreshTokenHash2);
            pst.setLong(2, expiry);
            pst.setString(3, Utils.getUUID());
            pst.setString(4, sessionHandle);
            pst.setString(5, lastUpdatedSign);
            int numberOfRowsAffected = pst.executeUpdate();
            return numberOfRowsAffected == 1;
        }
    }

    static int getNumberOfSessions(Start start) throws SQLException {
        String QUERY = "SELECT count(*) as num FROM " + Config.getConfig(start).getSessionInfoTable();

        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return result.getInt("num");
            }
            throw new SQLException("Should not have come here.");
        }
    }

    static int deleteSession(Start start, String[] sessionHandles) throws SQLException {
        if (sessionHandles.length == 0) {
            return 0;
        }
        StringBuilder QUERY = new StringBuilder("DELETE FROM " + Config.getConfig(start).getSessionInfoTable() +
                " WHERE session_handle IN (");
        for (int i = 0; i < sessionHandles.length; i++) {
            if (i == sessionHandles.length - 1) {
                QUERY.append("?)");
            } else {
                QUERY.append("?, ");
            }
        }

        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY.toString())) {
            for (int i = 0; i < sessionHandles.length; i++) {
                pst.setString(i + 1, sessionHandles[i]);
            }
            return pst.executeUpdate();
        }
    }

    static String[] getAllSessionHandlesForUser(Start start, String userId) throws SQLException {
        String QUERY = "SELECT session_handle FROM " + Config.getConfig(start).getSessionInfoTable() +
                " WHERE user_id = ?";

        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            ResultSet result = pst.executeQuery();
            List<String> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(result.getString("session_handle"));
            }
            String[] finalResult = new String[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        }
    }

    static SQLStorage.SessionInfo getSession(Start start, String sessionHandle) throws SQLException {
        String QUERY = "SELECT session_handle, user_id, refresh_token_hash_2, session_data, expires_at, " +
                "created_at_time, jwt_user_payload FROM "
                + Config.getConfig(start).getSessionInfoTable() + " WHERE session_handle = ?";
        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, sessionHandle);
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return new SQLStorage.SessionInfo(result.getString("session_handle"), result.getString("user_id"),
                        result.getString("refresh_token_hash_2"),
                        new JsonParser().parse(result.getString("session_data")).getAsJsonObject(),
                        result.getLong("expires_at"),
                        new JsonParser().parse(result.getString("jwt_user_payload")).getAsJsonObject(),
                        result.getLong("created_at_time"));
            }
        }
        return null;
    }


    static int updateSession(Start start, String sessionHandle, JsonObject sessionData, JsonObject jwtPayload)
            throws SQLException {
        if (sessionData == null && jwtPayload == null) {
            throw new SQLException("sessionData and jwtPayload are null when updating session info");
        }
        String QUERY = "UPDATE " + Config.getConfig(start).getSessionInfoTable() + " SET ";
        QUERY += "last_updated_sign = ?";

        if (sessionData != null) {
            QUERY += ", session_data = ?";
        }

        if (jwtPayload != null) {
            QUERY += ", jwt_user_payload = ?";
        }

        QUERY += " WHERE session_handle = ?";

        int currIndex = 1;
        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(currIndex, Utils.getUUID());
            currIndex++;
            if (sessionData != null) {
                pst.setString(currIndex, sessionData.toString());
                currIndex++;
            }
            if (jwtPayload != null) {
                pst.setString(currIndex, jwtPayload.toString());
                currIndex++;
            }
            pst.setString(currIndex, sessionHandle);
            return pst.executeUpdate();
        }

    }

    static void deleteAllExpiredSessions(Start start) throws SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getSessionInfoTable() +
                " WHERE expires_at <= ?";

        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setLong(1, System.currentTimeMillis());
            pst.executeUpdate();
        }
    }

    static void deletePastOrphanedTokens(Start start, long createdBefore) throws SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getPastTokensTable() +
                " WHERE created_at_time < ? AND parent_refresh_token_hash_2 NOT IN (" +
                "SELECT refresh_token_hash_2 FROM " + Config.getConfig(start).getSessionInfoTable() + ") " +
                "AND refresh_token_hash_2 NOT IN (" +
                "SELECT refresh_token_hash_2 FROM " + Config.getConfig(start).getSessionInfoTable() + ")";

        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setLong(1, createdBefore);
            pst.executeUpdate();
        }
    }
}
