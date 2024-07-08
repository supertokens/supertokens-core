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

import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.inmemorydb.ConnectionWithLocks;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;

import java.sql.Connection;
import java.sql.SQLException;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;
import static io.supertokens.inmemorydb.config.Config.getConfig;

public class UserMetadataQueries {

    public static String getQueryToCreateUserMetadataTable(Start start) {
        String tableName = Config.getConfig(start).getUserMetadataTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "user_id VARCHAR(128) NOT NULL,"
                + "user_metadata TEXT NOT NULL,"
                + "PRIMARY KEY(app_id, user_id),"
                + "FOREIGN KEY (app_id) REFERENCES " + Config.getConfig(start).getAppsTable()
                + " (app_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    public static int deleteUserMetadata(Start start, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getUserMetadataTable()
                + " WHERE app_id = ? AND user_id = ?";

        return update(start, QUERY.toString(), pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        });
    }

    public static int deleteUserMetadata_Transaction(Connection sqlCon, Start start, AppIdentifier appIdentifier,
                                                     String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getUserMetadataTable()
                + " WHERE app_id = ? AND user_id = ?";

        return update(sqlCon, QUERY.toString(), pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        });
    }

    public static int setUserMetadata_Transaction(Start start, Connection con, AppIdentifier appIdentifier,
                                                  String userId, JsonObject metadata)
            throws SQLException, StorageQueryException {

        String QUERY = "INSERT INTO " + getConfig(start).getUserMetadataTable()
                + "(app_id, user_id, user_metadata) VALUES(?, ?, ?) "
                + "ON CONFLICT(app_id, user_id) DO UPDATE SET user_metadata=excluded.user_metadata;";

        return update(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
            pst.setString(3, metadata.toString());
        });
    }

    public static JsonObject getUserMetadata_Transaction(Start start, Connection con, AppIdentifier appIdentifier,
                                                         String userId)
            throws SQLException, StorageQueryException {
        ((ConnectionWithLocks) con).lock(
                appIdentifier.getAppId() + "~" + userId + Config.getConfig(start).getUserMetadataTable());

        String QUERY = "SELECT user_metadata FROM " + getConfig(start).getUserMetadataTable()
                + " WHERE app_id = ? AND user_id = ?";
        return execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, result -> {
            if (result.next()) {
                JsonParser jp = new JsonParser();
                return jp.parse(result.getString("user_metadata")).getAsJsonObject();
            }
            return null;
        });
    }

    public static JsonObject getUserMetadata(Start start, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT user_metadata FROM " + getConfig(start).getUserMetadataTable()
                + " WHERE app_id = ? AND user_id = ?";
        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, result -> {
            if (result.next()) {
                JsonParser jp = new JsonParser();
                return jp.parse(result.getString("user_metadata")).getAsJsonObject();
            }
            return null;
        });
    }
}
