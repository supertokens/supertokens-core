/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class OAuthQueries {

    public static String getQueryToCreateOAuthClientTable(Start start) {
        String oAuth2ClientTable = Config.getConfig(start).getOAuthClientTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + oAuth2ClientTable + " ("
                + "app_id VARCHAR(64),"
                + "client_id VARCHAR(128) NOT NULL,"
                + " PRIMARY KEY (app_id, client_id),"
                + " FOREIGN KEY(app_id) REFERENCES " + Config.getConfig(start).getAppsTable() + "(app_id) ON DELETE CASCADE);";
        // @formatter:on
    }

    public static String getQueryToCreateOAuthRevokeTable(Start start) {
        String oAuth2ClientTable = Config.getConfig(start).getOAuthRevokeTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + oAuth2ClientTable + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "target_type VARCHAR(16) NOT NULL,"
                + "target_value VARCHAR(128) NOT NULL,"
                + "timestamp BIGINT NOT NULL, "
                + "PRIMARY KEY (app_id, target_type, target_value),"
                + "FOREIGN KEY(app_id) "
                + " REFERENCES " + Config.getConfig(start).getAppsTable() + "(app_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    public static String getQueryToCreateOAuthRevokeTimestampIndex(Start start) {
        String oAuth2ClientTable = Config.getConfig(start).getOAuthRevokeTable();
        return "CREATE INDEX IF NOT EXISTS oauth_revoke_timestamp_index ON "
                + oAuth2ClientTable + "(timestamp DESC, app_id DESC);";
    }

    public static boolean isClientIdForAppId(Start start, String clientId, AppIdentifier appIdentifier)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT app_id FROM " + Config.getConfig(start).getOAuthClientTable() +
            " WHERE client_id = ? AND app_id = ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, clientId);
            pst.setString(2, appIdentifier.getAppId());
        }, ResultSet::next);
    }

    public static List<String> listClientsForApp(Start start, AppIdentifier appIdentifier)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT client_id FROM " + Config.getConfig(start).getOAuthClientTable() +
                " WHERE app_id = ?";
        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
        }, (result) -> {
            List<String> res = new ArrayList<>();
            while (result.next()) {
                res.add(result.getString("client_id"));
            }
            return res;
        });
    }

    public static void insertClientIdForAppId(Start start, String clientId, AppIdentifier appIdentifier)
            throws SQLException, StorageQueryException {
        String INSERT = "INSERT INTO " + Config.getConfig(start).getOAuthClientTable()
                + "(app_id, client_id) VALUES(?, ?)";
        update(start, INSERT, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, clientId);
        });
    }

    public static boolean deleteClientIdForAppId(Start start, String clientId, AppIdentifier appIdentifier)
            throws SQLException, StorageQueryException {
        String DELETE = "DELETE FROM " + Config.getConfig(start).getOAuthClientTable()
                + " WHERE app_id = ? AND client_id = ?";
        int numberOfRow = update(start, DELETE, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, clientId);
        });
        return numberOfRow > 0;
    }

    public static void revoke(Start start, AppIdentifier appIdentifier, String targetType, String targetValue)
            throws SQLException, StorageQueryException {
        String INSERT = "INSERT INTO " + Config.getConfig(start).getOAuthRevokeTable()
                + "(app_id, target_type, target_value, timestamp) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (app_id, target_type, target_value) DO UPDATE SET timestamp = ?";

        long currentTime = System.currentTimeMillis() / 1000;
        update(start, INSERT, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, targetType);
            pst.setString(3, targetValue);
            pst.setLong(4, currentTime);
            pst.setLong(5, currentTime);
        });
    }

    public static boolean isRevoked(Start start, AppIdentifier appIdentifier, String[] targetTypes, String[] targetValues, long issuedAt)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT app_id FROM " + Config.getConfig(start).getOAuthRevokeTable() +
                " WHERE app_id = ? AND timestamp > ? AND (";

        for (int i = 0; i < targetTypes.length; i++) {
            QUERY += "(target_type = ? AND target_value = ?)";

            if (i < targetTypes.length - 1) {
                QUERY += " OR ";
            }
        }

        QUERY += ")";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setLong(2, issuedAt);

            int index = 3;
            for (int i = 0; i < targetTypes.length; i++) {
                pst.setString(index, targetTypes[i]);
                index++;
                pst.setString(index, targetValues[i]);
                index++;
            }
        }, ResultSet::next);
    }
}
