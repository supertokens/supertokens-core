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

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.inmemorydb.ConnectionPool;
import io.supertokens.inmemorydb.ConnectionWithLocks;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class GeneralQueries {

    private static boolean doesTableExists(Start start, String tableName) {
        try {
            String QUERY = "SELECT 1 FROM " + tableName + " LIMIT 1";
            try (Connection con = ConnectionPool.getConnection(start);
                 PreparedStatement pst = con.prepareStatement(QUERY)) {
                pst.executeQuery();
            }
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    static String getQueryToCreateKeyValueTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getKeyValueTable() + " (" + "name VARCHAR(128),"
                + "value TEXT," + "created_at_time BIGINT UNSIGNED," + "PRIMARY KEY(name)" + " );";
    }


    public static void createTablesIfNotExists(Start start, Main main) throws SQLException {
        if (!doesTableExists(start, Config.getConfig(start).getKeyValueTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                 PreparedStatement pst = con.prepareStatement(getQueryToCreateKeyValueTable(start))) {
                pst.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getSessionInfoTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                 PreparedStatement pst = con.prepareStatement(SessionQueries.getQueryToCreateSessionInfoTable(start))) {
                pst.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getUsersTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                 PreparedStatement pst = con.prepareStatement(EmailPasswordQueries.getQueryToCreateUsersTable(start))) {
                pst.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getPasswordResetTokensTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                 PreparedStatement pst = con
                         .prepareStatement(EmailPasswordQueries.getQueryToCreatePasswordResetTokensTable(start))) {
                pst.executeUpdate();
            }
            // index
            try (Connection con = ConnectionPool.getConnection(start);
                 PreparedStatement pstIndex = con
                         .prepareStatement(
                                 EmailPasswordQueries.getQueryToCreatePasswordResetTokenExpiryIndex(start))) {
                pstIndex.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getEmailVerificationTokensTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                 PreparedStatement pst = con
                         .prepareStatement(EmailPasswordQueries.getQueryToCreateEmailVerificationTokensTable(start))) {
                pst.executeUpdate();
            }
            // index
            try (Connection con = ConnectionPool.getConnection(start);
                 PreparedStatement pstIndex = con
                         .prepareStatement(
                                 EmailPasswordQueries.getQueryToCreateEmailVerificationTokenExpiryIndex(start))) {
                pstIndex.executeUpdate();
            }
        }

    }

    public static void setKeyValue_Transaction(Start start, Connection con, String key, KeyValueInfo info)
            throws SQLException {

        String QUERY = "INSERT INTO " + Config.getConfig(start).getKeyValueTable()
                + "(name, value, created_at_time) VALUES(?, ?, ?) "
                + "ON CONFLICT(name) DO UPDATE SET value = ?, created_at_time = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, key);
            pst.setString(2, info.value);
            pst.setLong(3, info.createdAtTime);
            pst.setString(4, info.value);
            pst.setLong(5, info.createdAtTime);
            pst.executeUpdate();
        }
    }

    public static void setKeyValue(Start start, String key, KeyValueInfo info)
            throws SQLException {
        try (Connection con = ConnectionPool.getConnection(start)) {
            setKeyValue_Transaction(start, con, key, info);
        }
    }


    public static KeyValueInfo getKeyValue(Start start, String key) throws SQLException, StorageQueryException {
        String QUERY = "SELECT value, created_at_time FROM "
                + Config.getConfig(start).getKeyValueTable() + " WHERE name = ?";

        try (Connection con = ConnectionPool.getConnection(start);
             PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, key);
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return KeyValueInfoRowMapper.getInstance().mapOrThrow(result);
            }
        }
        return null;
    }

    public static KeyValueInfo getKeyValue_Transaction(Start start, Connection con, String key)
            throws SQLException, StorageQueryException {

        ((ConnectionWithLocks) con).lock(key);

        String QUERY = "SELECT value, created_at_time FROM "
                + Config.getConfig(start).getKeyValueTable() + " WHERE name = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, key);
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return KeyValueInfoRowMapper.getInstance().mapOrThrow(result);
            }
        }
        return null;
    }

    private static class KeyValueInfoRowMapper implements RowMapper<KeyValueInfo, ResultSet> {
        private static final KeyValueInfoRowMapper INSTANCE = new KeyValueInfoRowMapper();

        private KeyValueInfoRowMapper() {
        }

        private static KeyValueInfoRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public KeyValueInfo map(ResultSet result) throws Exception {
            return new KeyValueInfo(result.getString("value"), result.getLong("created_at_time"));
        }
    }
}
