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
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;
import static io.supertokens.inmemorydb.config.Config.getConfig;

public class UserIdMappingQueries {

    public static String getQueryToCreateUserIdMappingTable(Start start) {
        String tableName = Config.getConfig(start).getUserIdMappingTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "supertokens_user_id CHAR(36) NOT NULL UNIQUE,"
                + "external_user_id VARCHAR(128) NOT NULL UNIQUE,"
                + "external_user_id_info TEXT,"
                + "PRIMARY KEY(supertokens_user_id, external_user_id),"
                + "FOREIGN KEY(supertokens_user_id) REFERENCES " + Config.getConfig(start).getUsersTable()
                + "(user_id) ON DELETE CASCADE );";

        // @formatter:on
    }

    public static void createUserIdMapping(Start start, String superTokensUserId, String externalUserId,
            String externalUserIdInfo) throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getUserIdMappingTable()
                + " (supertokens_user_id, external_user_id, external_user_id_info)" + " VALUES(?, ?, ?)";

        update(start, QUERY, pst -> {
            pst.setString(1, superTokensUserId);
            pst.setString(2, externalUserId);
            pst.setString(3, externalUserIdInfo);
        });
    }

    public static UserIdMapping getUserIdMappingWithSuperTokensUserId(Start start, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getUserIdMappingTable()
                + " WHERE supertokens_user_id = ?";

        return execute(start, QUERY, pst -> pst.setString(1, userId), result -> {
            if (result.next()) {
                return UserIdMappingRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static UserIdMapping getUserIdMappingWithExternalUserId(Start start, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getUserIdMappingTable()
                + " WHERE external_user_id = ?";

        return execute(start, QUERY, pst -> pst.setString(1, userId), result -> {
            if (result.next()) {
                return UserIdMappingRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static UserIdMapping[] getUserIdMappingWithEitherSuperTokensUserIdOrExternalUserId(Start start,
            String userId) throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getUserIdMappingTable()
                + " WHERE supertokens_user_id = ? OR external_user_id = ? ";

        return execute(start, QUERY, pst -> {
            pst.setString(1, userId);
            pst.setString(2, userId);
        }, result -> {
            ArrayList<UserIdMapping> userIdMappingArray = new ArrayList<>();
            while (result.next()) {
                userIdMappingArray.add(UserIdMappingRowMapper.getInstance().mapOrThrow(result));
            }
            return userIdMappingArray.toArray(UserIdMapping[]::new);
        });

    }

    public static boolean deleteUserIdMappingWithSuperTokensUserId(Start start, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getUserIdMappingTable() + " WHERE supertokens_user_id = ?";

        // store the number of rows updated
        int rowUpdatedCount = update(start, QUERY, pst -> pst.setString(1, userId));

        return rowUpdatedCount > 0;
    }

    public static boolean deleteUserIdMappingWithExternalUserId(Start start, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getUserIdMappingTable() + " WHERE external_user_id = ?";

        // store the number of rows updated
        int rowUpdatedCount = update(start, QUERY, pst -> pst.setString(1, userId));

        return rowUpdatedCount > 0;
    }

    public static boolean updateOrDeleteExternalUserIdInfoWithSuperTokensUserId(Start start, String userId,
            @Nullable String externalUserIdInfo) throws SQLException, StorageQueryException {
        String QUERY = "UPDATE " + getConfig(start).getUserIdMappingTable()
                + " SET external_user_id_info = ? WHERE supertokens_user_id = ?";

        int rowUpdated = update(start, QUERY, pst -> {
            pst.setString(1, externalUserIdInfo);
            pst.setString(2, userId);
        });

        return rowUpdated > 0;
    }

    public static boolean updateOrDeleteExternalUserIdInfoWithExternalUserId(Start start, String userId,
            @Nullable String externalUserIdInfo) throws SQLException, StorageQueryException {
        String QUERY = "UPDATE " + getConfig(start).getUserIdMappingTable()
                + " SET external_user_id_info = ? WHERE external_user_id = ?";

        int rowUpdated = update(start, QUERY, pst -> {
            pst.setString(1, externalUserIdInfo);
            pst.setString(2, userId);
        });

        return rowUpdated > 0;
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