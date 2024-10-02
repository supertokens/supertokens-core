/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;
import static io.supertokens.inmemorydb.config.Config.getConfig;

public class UserIdMappingQueries {

    public static String getQueryToCreateUserIdMappingTable(Start start) {
        String tableName = Config.getConfig(start).getUserIdMappingTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "supertokens_user_id CHAR(36) NOT NULL,"
                + "external_user_id VARCHAR(128) NOT NULL,"
                + "external_user_id_info TEXT,"
                + "UNIQUE (app_id, supertokens_user_id),"
                + "UNIQUE (app_id, external_user_id),"
                + "PRIMARY KEY(app_id, supertokens_user_id, external_user_id),"
                + "FOREIGN KEY(app_id, supertokens_user_id) REFERENCES " +
                Config.getConfig(start).getAppIdToUserIdTable()
                + " (app_id, user_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    public static void createUserIdMapping(Start start, AppIdentifier appIdentifier, String superTokensUserId,
                                           String externalUserId,
                                           String externalUserIdInfo) throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getUserIdMappingTable()
                + " (app_id, supertokens_user_id, external_user_id, external_user_id_info)" + " VALUES(?, ?, ?, ?)";

        update(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, superTokensUserId);
            pst.setString(3, externalUserId);
            pst.setString(4, externalUserIdInfo);
        });
    }

    public static UserIdMapping getuseraIdMappingWithSuperTokensUserId(Start start, AppIdentifier appIdentifier,
                                                                       String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getUserIdMappingTable()
                + " WHERE app_id = ? AND supertokens_user_id = ?";
        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, result -> {
            if (result.next()) {
                return UserIdMappingRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static UserIdMapping getUserIdMappingWithExternalUserId(Start start, AppIdentifier appIdentifier,
                                                                   String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getUserIdMappingTable()
                + " WHERE app_id = ? AND external_user_id = ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, result -> {
            if (result.next()) {
                return UserIdMappingRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static UserIdMapping[] getUserIdMappingWithEitherSuperTokensUserIdOrExternalUserId(Start start,
                                                                                              AppIdentifier appIdentifier,
                                                                                              String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getUserIdMappingTable()
                + " WHERE app_id = ? AND (supertokens_user_id = ? OR external_user_id = ?)";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
            pst.setString(3, userId);
        }, result -> {
            ArrayList<UserIdMapping> userIdMappingArray = new ArrayList<>();
            while (result.next()) {
                userIdMappingArray.add(UserIdMappingRowMapper.getInstance().mapOrThrow(result));
            }
            return userIdMappingArray.toArray(UserIdMapping[]::new);
        });

    }

    public static UserIdMapping[] getUserIdMappingWithEitherSuperTokensUserIdOrExternalUserId_Transaction(Start start,
                                                                                                          Connection sqlCon,
                                                                                                          AppIdentifier appIdentifier,
                                                                                                          String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getUserIdMappingTable()
                + " WHERE app_id = ? AND (supertokens_user_id = ? OR external_user_id = ?)";

        return execute(sqlCon, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
            pst.setString(3, userId);
        }, result -> {
            ArrayList<UserIdMapping> userIdMappingArray = new ArrayList<>();
            while (result.next()) {
                userIdMappingArray.add(UserIdMappingRowMapper.getInstance().mapOrThrow(result));
            }
            return userIdMappingArray.toArray(UserIdMapping[]::new);
        });

    }

    public static HashMap<String, String> getUserIdMappingWithUserIds(Start start,
                                                                      AppIdentifier appIdentifier, List<String> userIds)
            throws SQLException, StorageQueryException {

        if (userIds.size() == 0) {
            return new HashMap<>();
        }

        // No need to filter based on tenantId because the id list is already filtered for a tenant
        StringBuilder QUERY = new StringBuilder(
                "SELECT * FROM " + Config.getConfig(start).getUserIdMappingTable() + " WHERE app_id = ? AND " +
                        "supertokens_user_id IN (");
        for (int i = 0; i < userIds.size(); i++) {
            QUERY.append("?");
            if (i != userIds.size() - 1) {
                // not the last element
                QUERY.append(",");
            }
        }
        QUERY.append(")");
        return execute(start, QUERY.toString(), pst -> {
            pst.setString(1, appIdentifier.getAppId());
            for (int i = 0; i < userIds.size(); i++) {
                // i+2 cause this starts with 1 and not 0, and 1 is appId
                pst.setString(i + 2, userIds.get(i));
            }
        }, result -> {
            HashMap<String, String> userIdMappings = new HashMap<>();
            while (result.next()) {
                UserIdMapping temp = UserIdMappingRowMapper.getInstance().mapOrThrow(result);
                userIdMappings.put(temp.superTokensUserId, temp.externalUserId);
            }
            return userIdMappings;
        });
    }

    public static HashMap<String, String> getUserIdMappingWithUserIds_Transaction(Start start, Connection sqlCon,
                                                                                  AppIdentifier appIdentifier,
                                                                                  List<String> userIds)
            throws SQLException, StorageQueryException {

        if (userIds.size() == 0) {
            return new HashMap<>();
        }

        // No need to filter based on tenantId because the id list is already filtered for a tenant
        StringBuilder QUERY = new StringBuilder(
                "SELECT * FROM " + Config.getConfig(start).getUserIdMappingTable() + " WHERE app_id = ? AND " +
                        "supertokens_user_id IN (");
        for (int i = 0; i < userIds.size(); i++) {
            QUERY.append("?");
            if (i != userIds.size() - 1) {
                // not the last element
                QUERY.append(",");
            }
        }
        QUERY.append(")");
        return execute(sqlCon, QUERY.toString(), pst -> {
            pst.setString(1, appIdentifier.getAppId());
            for (int i = 0; i < userIds.size(); i++) {
                // i+2 cause this starts with 1 and not 0, and 1 is appId
                pst.setString(i + 2, userIds.get(i));
            }
        }, result -> {
            HashMap<String, String> userIdMappings = new HashMap<>();
            while (result.next()) {
                UserIdMapping temp = UserIdMappingRowMapper.getInstance().mapOrThrow(result);
                userIdMappings.put(temp.superTokensUserId, temp.externalUserId);
            }
            return userIdMappings;
        });
    }

    public static boolean deleteUserIdMappingWithSuperTokensUserId(Start start, AppIdentifier appIdentifier,
                                                                   String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getUserIdMappingTable()
                + " WHERE app_id = ? AND supertokens_user_id = ?";

        // store the number of rows updated
        int rowUpdatedCount = update(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        });

        return rowUpdatedCount > 0;
    }

    public static boolean deleteUserIdMappingWithExternalUserId(Start start, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getUserIdMappingTable() +
                " WHERE app_id = ? AND external_user_id = ?";

        // store the number of rows updated
        int rowUpdatedCount = update(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        });

        return rowUpdatedCount > 0;
    }

    public static boolean updateOrDeleteExternalUserIdInfoWithSuperTokensUserId(Start start,
                                                                                AppIdentifier appIdentifier,
                                                                                String userId,
                                                                                @Nullable String externalUserIdInfo)
            throws SQLException, StorageQueryException {
        String QUERY = "UPDATE " + Config.getConfig(start).getUserIdMappingTable()
                + " SET external_user_id_info = ? WHERE app_id = ? AND supertokens_user_id = ?";

        int rowUpdated = update(start, QUERY, pst -> {
            pst.setString(1, externalUserIdInfo);
            pst.setString(2, appIdentifier.getAppId());
            pst.setString(3, userId);
        });

        return rowUpdated > 0;
    }

    public static boolean updateOrDeleteExternalUserIdInfoWithExternalUserId(Start start, AppIdentifier appIdentifier,
                                                                             String userId,
                                                                             @Nullable String externalUserIdInfo)
            throws SQLException, StorageQueryException {
        String QUERY = "UPDATE " + Config.getConfig(start).getUserIdMappingTable()
                + " SET external_user_id_info = ? WHERE app_id = ? AND external_user_id = ?";

        int rowUpdated = update(start, QUERY, pst -> {
            pst.setString(1, externalUserIdInfo);
            pst.setString(2, appIdentifier.getAppId());
            pst.setString(3, userId);
        });

        return rowUpdated > 0;
    }

    public static UserIdMapping getuseraIdMappingWithSuperTokensUserId_Transaction(Start start, Connection sqlCon,
                                                                                   AppIdentifier appIdentifier,
                                                                                   String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getUserIdMappingTable()
                + " WHERE app_id = ? AND supertokens_user_id = ?";
        return execute(sqlCon, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, result -> {
            if (result.next()) {
                return UserIdMappingRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static UserIdMapping getUserIdMappingWithExternalUserId_Transaction(Start start, Connection sqlCon,
                                                                               AppIdentifier appIdentifier,
                                                                               String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getUserIdMappingTable()
                + " WHERE app_id = ? AND external_user_id = ?";

        return execute(sqlCon, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, result -> {
            if (result.next()) {
                return UserIdMappingRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    private static class UserIdMappingRowMapper implements RowMapper<UserIdMapping, ResultSet> {
        private static final UserIdMappingRowMapper INSTANCE = new UserIdMappingRowMapper();

        private UserIdMappingRowMapper() {
        }

        private static UserIdMappingRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public UserIdMapping map(ResultSet rs) throws Exception {
            return new UserIdMapping(rs.getString("supertokens_user_id"), rs.getString("external_user_id"),
                    rs.getString("external_user_id_info"));
        }
    }

}
