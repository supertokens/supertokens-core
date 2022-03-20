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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.inmemorydb.ConnectionWithLocks;
import io.supertokens.inmemorydb.QueryExecutorTemplate;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.session.SessionInfo;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static io.supertokens.inmemorydb.PreparedStatementValueSetter.NO_OP_SETTER;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;
import static io.supertokens.inmemorydb.config.Config.getConfig;
import static java.lang.System.currentTimeMillis;

public class SessionQueries {

    static String getQueryToCreateSessionInfoTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getSessionInfoTable() + " ("
                + "session_handle VARCHAR(255) NOT NULL," + "user_id VARCHAR(128) NOT NULL,"
                + "refresh_token_hash_2 VARCHAR(128) NOT NULL," + "session_data TEXT,"
                + "expires_at BIGINT UNSIGNED NOT NULL," + "created_at_time BIGINT UNSIGNED NOT NULL,"
                + "jwt_user_payload TEXT," + "grant_payload TEXT, " + "PRIMARY KEY(session_handle)" + " );";
    }

    static String getQueryToCreateAccessTokenSigningKeysTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getAccessTokenSigningKeysTable() + " ("
                + "created_at_time BIGINT UNSIGNED NOT NULL," + "value TEXT," + "PRIMARY KEY(created_at_time)" + " );";
    }

    public static void createNewSession(Start start, String sessionHandle, String userId, String refreshTokenHash2,
            JsonObject userDataInDatabase, long expiry, JsonObject userDataInJWT, JsonObject grantPayload,
            long createdAtTime) throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + getConfig(start).getSessionInfoTable()
                + "(session_handle, user_id, refresh_token_hash_2, session_data, expires_at, jwt_user_payload, grant_payload,"
                + "created_at_time)" + " VALUES(?, ?, ?, ?, ?, ?, ?, ?)";

        update(start, QUERY, pst -> {
            pst.setString(1, sessionHandle);
            pst.setString(2, userId);
            pst.setString(3, refreshTokenHash2);
            pst.setString(4, userDataInDatabase.toString());
            pst.setLong(5, expiry);
            pst.setString(6, userDataInJWT.toString());
            pst.setString(7, grantPayload == null ? null : grantPayload.toString());
            pst.setLong(8, createdAtTime);
        });
    }

    public static SessionInfo getSessionInfo_Transaction(Start start, Connection con, String sessionHandle)
            throws SQLException, StorageQueryException {

        ((ConnectionWithLocks) con).lock(sessionHandle);

        String QUERY = "SELECT session_handle, user_id, refresh_token_hash_2, session_data, expires_at, "
                + "created_at_time, jwt_user_payload, grant_payload FROM " + getConfig(start).getSessionInfoTable()
                + " WHERE session_handle = ?";
        return QueryExecutorTemplate.execute(con, QUERY, pst -> {
            pst.setString(1, sessionHandle);
        }, result -> {
            if (result.next()) {
                return SessionInfoRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static void updateSessionGrantPayload_Transaction(Start start, Connection con, String sessionHandle,
            String grantPayload) throws SQLException, StorageQueryException {
        String QUERY = "UPDATE " + getConfig(start).getSessionInfoTable() + " SET grant_payload = ?"
                + " WHERE session_handle = ?";

        update(con, QUERY, pst -> {
            pst.setString(1, grantPayload);
            pst.setString(2, sessionHandle);
        });
    }

    public static void updateSessionInfo_Transaction(Start start, Connection con, String sessionHandle,
            String refreshTokenHash2, long expiry) throws SQLException, StorageQueryException {
        String QUERY = "UPDATE " + getConfig(start).getSessionInfoTable()
                + " SET refresh_token_hash_2 = ?, expires_at = ?" + " WHERE session_handle = ?";

        update(con, QUERY, pst -> {
            pst.setString(1, refreshTokenHash2);
            pst.setLong(2, expiry);
            pst.setString(3, sessionHandle);
        });
    }

    public static int getNumberOfSessions(Start start) throws SQLException, StorageQueryException {
        String QUERY = "SELECT count(*) as num FROM " + getConfig(start).getSessionInfoTable();

        return execute(start, QUERY, NO_OP_SETTER, result -> {
            if (result.next()) {
                return result.getInt("num");
            }
            throw new SQLException("Should not have come here.");
        });
    }

    public static int deleteSession(Start start, String[] sessionHandles) throws SQLException, StorageQueryException {
        if (sessionHandles.length == 0) {
            return 0;
        }
        StringBuilder QUERY = new StringBuilder(
                "DELETE FROM " + Config.getConfig(start).getSessionInfoTable() + " WHERE session_handle IN (");
        for (int i = 0; i < sessionHandles.length; i++) {
            if (i == sessionHandles.length - 1) {
                QUERY.append("?)");
            } else {
                QUERY.append("?, ");
            }
        }

        return update(start, QUERY.toString(), pst -> {
            for (int i = 0; i < sessionHandles.length; i++) {
                pst.setString(i + 1, sessionHandles[i]);
            }
        });
    }

    public static void deleteSessionsOfUser(Start start, String userId) throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getSessionInfoTable() + " WHERE user_id = ?";

        update(start, QUERY.toString(), pst -> pst.setString(1, userId));
    }

    public static String[] getAllSessionHandlesForUser(Start start, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT session_handle FROM " + getConfig(start).getSessionInfoTable() + " WHERE user_id = ?";

        return execute(start, QUERY, pst -> pst.setString(1, userId), result -> {
            List<String> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(result.getString("session_handle"));
            }
            return temp.toArray(String[]::new);
        });
    }

    public static void deleteAllExpiredSessions(Start start) throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getSessionInfoTable() + " WHERE expires_at <= ?";

        update(start, QUERY, pst -> pst.setLong(1, currentTimeMillis()));
    }

    public static SessionInfo getSession(Start start, String sessionHandle) throws SQLException, StorageQueryException {
        String QUERY = "SELECT session_handle, user_id, refresh_token_hash_2, session_data, expires_at, "
                + "created_at_time, jwt_user_payload, grant_payload FROM "
                + Config.getConfig(start).getSessionInfoTable() + " WHERE session_handle = ?";
        return execute(start, QUERY, pst -> pst.setString(1, sessionHandle), result -> {
            if (result.next()) {
                return SessionInfoRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static int updateSession(Start start, String sessionHandle, JsonObject sessionData, JsonObject jwtPayload,
            JsonObject grantPayload) throws SQLException, StorageQueryException {

        if (sessionData == null && jwtPayload == null && grantPayload == null) {
            throw new SQLException("sessionData, jwtPayload and grantPayload all null when updating session info");
        }

        String QUERY = "UPDATE " + Config.getConfig(start).getSessionInfoTable() + " SET";
        boolean somethingBefore = false;
        if (sessionData != null) {
            QUERY += " session_data = ?";
            somethingBefore = true;
        }
        if (jwtPayload != null) {
            QUERY += (somethingBefore ? "," : "") + " jwt_user_payload = ?";
            somethingBefore = true;
        }
        if (grantPayload != null) {
            QUERY += (somethingBefore ? "," : "") + " grant_payload = ?";
        }
        QUERY += " WHERE session_handle = ?";

        return update(start, QUERY, pst -> {
            int currIndex = 1;
            if (sessionData != null) {
                pst.setString(currIndex, sessionData.toString());
                currIndex++;
            }
            if (jwtPayload != null) {
                pst.setString(currIndex, jwtPayload.toString());
                currIndex++;
            }
            if (grantPayload != null) {
                pst.setString(currIndex, grantPayload.toString());
                currIndex++;
            }

            pst.setString(currIndex, sessionHandle);
        });
    }

    public static void addAccessTokenSigningKey_Transaction(Start start, Connection con, long createdAtTime,
            String value) throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + getConfig(start).getAccessTokenSigningKeysTable() + "(created_at_time, value)"
                + " VALUES(?, ?)";

        update(con, QUERY, pst -> {
            pst.setLong(1, createdAtTime);
            pst.setString(2, value);
        });
    }

    public static KeyValueInfo[] getAccessTokenSigningKeys_Transaction(Start start, Connection con)
            throws SQLException, StorageQueryException {
        String accessTokenSigningKeysTableName = getConfig(start).getAccessTokenSigningKeysTable();

        ((ConnectionWithLocks) con).lock(accessTokenSigningKeysTableName);
        String QUERY = "SELECT * FROM " + accessTokenSigningKeysTableName;

        return execute(con, QUERY, pst -> {

        }, result -> {
            List<KeyValueInfo> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(AccessTokenSigningKeyRowMapper.getInstance().mapOrThrow(result));
            }
            KeyValueInfo[] finalResult = new KeyValueInfo[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        });
    }

    public static void removeAccessTokenSigningKeysBefore(Start start, long time)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getAccessTokenSigningKeysTable()
                + " WHERE created_at_time < ?";
        update(start, QUERY, pst -> pst.setLong(1, time));
    }

    private static class SessionInfoRowMapper implements RowMapper<SessionInfo, ResultSet> {
        private static final SessionInfoRowMapper INSTANCE = new SessionInfoRowMapper();

        private SessionInfoRowMapper() {
        }

        private static SessionInfoRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public SessionInfo map(ResultSet result) throws Exception {
            JsonParser jp = new JsonParser();

            return new SessionInfo(result.getString("session_handle"), result.getString("user_id"),
                    result.getString("refresh_token_hash_2"),
                    jp.parse(result.getString("session_data")).getAsJsonObject(), result.getLong("expires_at"),
                    jp.parse(result.getString("jwt_user_payload")).getAsJsonObject(),
                    result.getString("grant_payload") == null ? null
                            : jp.parse(result.getString("grant_payload")).getAsJsonObject(),
                    result.getLong("created_at_time"));
        }
    }

    private static class AccessTokenSigningKeyRowMapper implements RowMapper<KeyValueInfo, ResultSet> {
        private static final AccessTokenSigningKeyRowMapper INSTANCE = new AccessTokenSigningKeyRowMapper();

        private AccessTokenSigningKeyRowMapper() {
        }

        private static AccessTokenSigningKeyRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public KeyValueInfo map(ResultSet result) throws Exception {
            return new KeyValueInfo(result.getString("value"), result.getLong("created_at_time"));
        }
    }
}
