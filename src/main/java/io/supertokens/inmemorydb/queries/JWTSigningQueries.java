/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.jwt.JWTAsymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSymmetricSigningKeyInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JWTSigningQueries {
    static String getQueryToCreateJWTSigningTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getJWTSigningKeysTable() + " ("
                + "key_id VARCHAR(255) NOT NULL," + "key_string TEXT NOT NULL,"
                + "algorithm VARCHAR(10) NOT NULL," + "created_at BIGINT UNSIGNED,"
                + "PRIMARY KEY(key_id));";
    }

    public static List<JWTSigningKeyInfo> getJWTSigningKeys_Transaction(Start start, Connection con)
            throws SQLException, StorageQueryException {

        // TODO: does this need locking?

        String QUERY = "SELECT * FROM "
                + Config.getConfig(start).getJWTSigningKeysTable()
                + " ORDER BY created_at DESC;";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            ResultSet result = pst.executeQuery();
            List<JWTSigningKeyInfo> keys = new ArrayList<>();

            while(result.next()) {
                JWTSigningKeyInfo keyInfo = JWTSigningKeyInfoRowMapper.getInstance().mapOrThrow(result);

                if (keyInfo.keyString.contains("|")) {
                    keys.add(JWTAsymmetricSigningKeyInfo.withKeyString(keyInfo.keyId, keyInfo.createdAtTime, keyInfo.algorithm, keyInfo.keyString));
                } else {
                    keys.add(new JWTSymmetricSigningKeyInfo(keyInfo.keyId, keyInfo.createdAtTime, keyInfo.algorithm, keyInfo.keyString));
                }
            }

            return keys;
        }
    }

    private static class JWTSigningKeyInfoRowMapper implements RowMapper<JWTSigningKeyInfo, ResultSet> {
        private static final JWTSigningKeyInfoRowMapper INSTANCE = new JWTSigningKeyInfoRowMapper();

        private JWTSigningKeyInfoRowMapper() {
        }

        private static JWTSigningKeyInfoRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public JWTSigningKeyInfo map(ResultSet result) throws Exception {
            String keyId = result.getString("key_id");
            String keyString = result.getString("key_string");
            long createdAt = result.getLong("created_at");
            String algorithm = result.getString("algorithm");

            return new JWTSigningKeyInfo(keyId, createdAt, algorithm, keyString);
        }
    }

    public static void setJWTSigningKeyInfo_Transaction(Start start, Connection con, JWTSigningKeyInfo info)
            throws SQLException {

        String QUERY = "INSERT INTO " + Config.getConfig(start).getJWTSigningKeysTable()
                + "(key_id, key_string, created_at, algorithm) VALUES(?, ?, ?, ?)";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, info.keyId);
            pst.setString(2, info.keyString);
            pst.setLong(3, info.createdAtTime);
            pst.setString(4, info.algorithm);
            pst.executeUpdate();
        }
    }
}
