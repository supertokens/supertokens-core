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
import io.supertokens.inmemorydb.ConnectionPool;
import io.supertokens.inmemorydb.ConnectionWithLocks;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.supertokens.ProcessState.PROCESS_STATE.CREATING_NEW_TABLE;
import static io.supertokens.ProcessState.getInstance;
import static io.supertokens.inmemorydb.PreparedStatementValueSetter.NO_OP_SETTER;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;
import static io.supertokens.inmemorydb.config.Config.getConfig;
import static io.supertokens.inmemorydb.queries.EmailPasswordQueries.getQueryToCreatePasswordResetTokenExpiryIndex;
import static io.supertokens.inmemorydb.queries.EmailPasswordQueries.getQueryToCreatePasswordResetTokensTable;
import static io.supertokens.inmemorydb.queries.EmailVerificationQueries.*;
import static io.supertokens.inmemorydb.queries.JWTSigningQueries.getQueryToCreateJWTSigningTable;
import static io.supertokens.inmemorydb.queries.PasswordlessQueries.*;
import static io.supertokens.inmemorydb.queries.SessionQueries.getQueryToCreateAccessTokenSigningKeysTable;
import static io.supertokens.inmemorydb.queries.SessionQueries.getQueryToCreateSessionInfoTable;
import static io.supertokens.inmemorydb.queries.UserMetadataQueries.getQueryToCreateUserMetadataTable;

public class GeneralQueries {

    private static boolean doesTableExists(Start start, String tableName) {
        try {
            String QUERY = "SELECT 1 FROM " + tableName + " LIMIT 1";
            execute(start, QUERY, NO_OP_SETTER, result -> null);
            return true;
        } catch (SQLException | StorageQueryException e) {
            return false;
        }
    }

    static String getQueryToCreateUsersTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getUsersTable() + " ("
                + "user_id CHAR(36) NOT NULL," + "recipe_id VARCHAR(128) NOT NULL,"
                + "time_joined BIGINT UNSIGNED NOT NULL," + "PRIMARY KEY (user_id));";
    }

    static String getQueryToCreateUserPaginationIndex(Start start) {
        return "CREATE INDEX all_auth_recipe_users_pagination_index ON " + Config.getConfig(start).getUsersTable()
                + "(time_joined DESC, user_id " + "DESC);";
    }

    static String getQueryToCreateKeyValueTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getKeyValueTable() + " (" + "name VARCHAR(128),"
                + "value TEXT," + "created_at_time BIGINT UNSIGNED," + "PRIMARY KEY(name)" + " );";
    }

    public static void createTablesIfNotExists(Start start, Main main) throws SQLException, StorageQueryException {
        if (!doesTableExists(start, Config.getConfig(start).getKeyValueTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, getQueryToCreateKeyValueTable(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getUsersTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, getQueryToCreateUsersTable(start), NO_OP_SETTER);

            // index
            update(start, getQueryToCreateUserPaginationIndex(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getAccessTokenSigningKeysTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, getQueryToCreateAccessTokenSigningKeysTable(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getSessionInfoTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, getQueryToCreateSessionInfoTable(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getEmailPasswordUsersTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, EmailPasswordQueries.getQueryToCreateUsersTable(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getPasswordResetTokensTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, getQueryToCreatePasswordResetTokensTable(start), NO_OP_SETTER);
            // index
            update(start, getQueryToCreatePasswordResetTokenExpiryIndex(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getEmailVerificationTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, getQueryToCreateEmailVerificationTable(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getEmailVerificationTokensTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, getQueryToCreateEmailVerificationTokensTable(start), NO_OP_SETTER);
            // index
            update(start, getQueryToCreateEmailVerificationTokenExpiryIndex(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getThirdPartyUsersTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, ThirdPartyQueries.getQueryToCreateUsersTable(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getJWTSigningKeysTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, getQueryToCreateJWTSigningTable(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getPasswordlessUsersTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, PasswordlessQueries.getQueryToCreateUsersTable(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getPasswordlessDevicesTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, getQueryToCreateDevicesTable(start), NO_OP_SETTER);
            // index
            update(start, getQueryToCreateDeviceEmailIndex(start), NO_OP_SETTER);
            update(start, getQueryToCreateDevicePhoneNumberIndex(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getPasswordlessCodesTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, getQueryToCreateCodesTable(start), NO_OP_SETTER);
            // index
            update(start, getQueryToCreateCodeCreatedAtIndex(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getUserMetadataTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, getQueryToCreateUserMetadataTable(start), NO_OP_SETTER);
        }
        if (!doesTableExists(start, Config.getConfig(start).getRolesTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, UserRoleQueries.getQueryToCreateRolesTable(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getUserRolesPermissionsTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, UserRoleQueries.getQueryToCreateRolePermissionsTable(start), NO_OP_SETTER);
            // index
            update(start, UserRoleQueries.getQueryToCreateRolePermissionsPermissionIndex(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getUserRolesTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, UserRoleQueries.getQueryToCreateUserRolesTable(start), NO_OP_SETTER);
            // index
            update(start, UserRoleQueries.getQueryToCreateUserRolesRoleIndex(start), NO_OP_SETTER);
        }

        if (!doesTableExists(start, Config.getConfig(start).getUserIdMappingTable())) {
            getInstance(main).addState(CREATING_NEW_TABLE, null);
            update(start, UserIdMappingQueries.getQueryToCreateUserIdMappingTable(start), NO_OP_SETTER);
        }

    }

    public static void setKeyValue_Transaction(Start start, Connection con, String key, KeyValueInfo info)
            throws SQLException, StorageQueryException {

        String QUERY = "INSERT INTO " + getConfig(start).getKeyValueTable()
                + "(name, value, created_at_time) VALUES(?, ?, ?) "
                + "ON CONFLICT(name) DO UPDATE SET value = ?, created_at_time = ?";

        update(con, QUERY, pst -> {
            pst.setString(1, key);
            pst.setString(2, info.value);
            pst.setLong(3, info.createdAtTime);
            pst.setString(4, info.value);
            pst.setLong(5, info.createdAtTime);
        });
    }

    public static void setKeyValue(Start start, String key, KeyValueInfo info)
            throws SQLException, StorageQueryException {
        try (Connection con = ConnectionPool.getConnection(start)) {
            setKeyValue_Transaction(start, con, key, info);
        }
    }

    public static void deleteKeyValue_Transaction(Start start, Connection con, String key)
            throws SQLException, StorageQueryException {

        String QUERY = "DELETE FROM " + getConfig(start).getKeyValueTable() + " WHERE name = ?";

        update(con, QUERY, pst -> pst.setString(1, key));
    }

    public static void deleteKeyValue(Start start, String key) throws SQLException, StorageQueryException {
        try (Connection con = ConnectionPool.getConnection(start)) {
            deleteKeyValue_Transaction(start, con, key);
        }
    }

    public static KeyValueInfo getKeyValue(Start start, String key) throws SQLException, StorageQueryException {
        String QUERY = "SELECT value, created_at_time FROM " + getConfig(start).getKeyValueTable() + " WHERE name = ?";

        return execute(start, QUERY, pst -> pst.setString(1, key), result -> {
            if (result.next()) {
                return KeyValueInfoRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static KeyValueInfo getKeyValue_Transaction(Start start, Connection con, String key)
            throws SQLException, StorageQueryException {

        ((ConnectionWithLocks) con).lock(key);

        String QUERY = "SELECT value, created_at_time FROM " + getConfig(start).getKeyValueTable() + " WHERE name = ?";

        return execute(con, QUERY, pst -> {
            pst.setString(1, key);
        }, result -> {
            if (result.next()) {
                return KeyValueInfoRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
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

    public static long getUsersCount(Start start, RECIPE_ID[] includeRecipeIds)
            throws SQLException, StorageQueryException {
        StringBuilder QUERY = new StringBuilder("SELECT COUNT(*) as total FROM " + getConfig(start).getUsersTable());
        if (includeRecipeIds != null && includeRecipeIds.length > 0) {
            QUERY.append(" WHERE recipe_id IN (");
            for (int i = 0; i < includeRecipeIds.length; i++) {
                String recipeId = includeRecipeIds[i].toString();
                QUERY.append("'").append(recipeId).append("'");
                if (i != includeRecipeIds.length - 1) {
                    // not the last element
                    QUERY.append(",");
                }
            }
            QUERY.append(")");
        }

        return execute(start, QUERY.toString(), NO_OP_SETTER, result -> {
            if (result.next()) {
                return result.getLong("total");
            }
            return 0L;
        });
    }

    public static AuthRecipeUserInfo[] getUsers(Start start, @NotNull Integer limit, @NotNull String timeJoinedOrder,
            @Nullable RECIPE_ID[] includeRecipeIds, @Nullable String userId, @Nullable Long timeJoined)
            throws SQLException, StorageQueryException {

        // This list will be used to keep track of the result's order from the db
        List<UserInfoPaginationResultHolder> usersFromQuery;

        {
            StringBuilder RECIPE_ID_CONDITION = new StringBuilder();
            if (includeRecipeIds != null && includeRecipeIds.length > 0) {
                RECIPE_ID_CONDITION.append("recipe_id IN (");
                for (int i = 0; i < includeRecipeIds.length; i++) {
                    String recipeId = includeRecipeIds[i].toString();
                    RECIPE_ID_CONDITION.append("'").append(recipeId).append("'");
                    if (i != includeRecipeIds.length - 1) {
                        // not the last element
                        RECIPE_ID_CONDITION.append(",");
                    }
                }
                RECIPE_ID_CONDITION.append(")");
            }

            if (timeJoined != null && userId != null) {
                String recipeIdCondition = RECIPE_ID_CONDITION.toString();
                if (!recipeIdCondition.equals("")) {
                    recipeIdCondition = recipeIdCondition + " AND";
                }
                String timeJoinedOrderSymbol = timeJoinedOrder.equals("ASC") ? ">" : "<";
                String QUERY = "SELECT user_id, recipe_id FROM " + getConfig(start).getUsersTable() + " WHERE "
                        + recipeIdCondition + " (time_joined " + timeJoinedOrderSymbol
                        + " ? OR (time_joined = ? AND user_id <= ?)) ORDER BY time_joined " + timeJoinedOrder
                        + ", user_id DESC LIMIT ?";
                usersFromQuery = execute(start, QUERY, pst -> {
                    pst.setLong(1, timeJoined);
                    pst.setLong(2, timeJoined);
                    pst.setString(3, userId);
                    pst.setInt(4, limit);
                }, result -> {
                    List<UserInfoPaginationResultHolder> temp = new ArrayList<>();
                    while (result.next()) {
                        temp.add(new UserInfoPaginationResultHolder(result.getString("user_id"),
                                result.getString("recipe_id")));
                    }
                    return temp;
                });

            } else {
                String recipeIdCondition = RECIPE_ID_CONDITION.toString();
                if (!recipeIdCondition.equals("")) {
                    recipeIdCondition = " WHERE " + recipeIdCondition;
                }
                String QUERY = "SELECT user_id, recipe_id FROM " + getConfig(start).getUsersTable() + recipeIdCondition
                        + " ORDER BY time_joined " + timeJoinedOrder + ", user_id DESC LIMIT ?";
                usersFromQuery = execute(start, QUERY, pst -> pst.setInt(1, limit), result -> {
                    List<UserInfoPaginationResultHolder> temp = new ArrayList<>();
                    while (result.next()) {
                        temp.add(new UserInfoPaginationResultHolder(result.getString("user_id"),
                                result.getString("recipe_id")));
                    }
                    return temp;
                });
            }
        }

        // we create a map from recipe ID -> userId[]
        Map<RECIPE_ID, List<String>> recipeIdToUserIdListMap = new HashMap<>();
        for (UserInfoPaginationResultHolder user : usersFromQuery) {
            RECIPE_ID recipeId = RECIPE_ID.getEnumFromString(user.recipeId);
            if (recipeId == null) {
                throw new SQLException("Unrecognised recipe ID in database: " + user.recipeId);
            }
            List<String> userIdList = recipeIdToUserIdListMap.get(recipeId);
            if (userIdList == null) {
                userIdList = new ArrayList<>();
            }
            userIdList.add(user.userId);
            recipeIdToUserIdListMap.put(recipeId, userIdList);
        }

        AuthRecipeUserInfo[] finalResult = new AuthRecipeUserInfo[usersFromQuery.size()];

        // we give the userId[] for each recipe to fetch all those user's details
        for (RECIPE_ID recipeId : recipeIdToUserIdListMap.keySet()) {
            List<? extends AuthRecipeUserInfo> users = getUserInfoForRecipeIdFromUserIds(start, recipeId,
                    recipeIdToUserIdListMap.get(recipeId));

            // we fill in all the slots in finalResult based on their position in usersFromQuery
            Map<String, AuthRecipeUserInfo> userIdToInfoMap = new HashMap<>();
            for (AuthRecipeUserInfo user : users) {
                userIdToInfoMap.put(user.id, user);
            }
            for (int i = 0; i < usersFromQuery.size(); i++) {
                if (finalResult[i] == null) {
                    finalResult[i] = userIdToInfoMap.get(usersFromQuery.get(i).userId);
                }
            }
        }

        return finalResult;
    }

    public static boolean doesUserIdExist(Start start, String userId) throws SQLException, StorageQueryException {

        String QUERY = "SELECT 1 FROM " + getConfig(start).getUsersTable() + " WHERE user_id = ?";
        return execute(start, QUERY, pst -> pst.setString(1, userId), ResultSet::next);

    }

    private static List<? extends AuthRecipeUserInfo> getUserInfoForRecipeIdFromUserIds(Start start, RECIPE_ID recipeId,
            List<String> userIds) throws StorageQueryException, SQLException {
        if (recipeId == RECIPE_ID.EMAIL_PASSWORD) {
            return EmailPasswordQueries.getUsersInfoUsingIdList(start, userIds);
        } else if (recipeId == RECIPE_ID.THIRD_PARTY) {
            return ThirdPartyQueries.getUsersInfoUsingIdList(start, userIds);
        } else if (recipeId == RECIPE_ID.PASSWORDLESS) {
            return PasswordlessQueries.getUsersByIdList(start, userIds);
        } else {
            throw new IllegalArgumentException("No implementation of get users for recipe: " + recipeId.toString());
        }
    }

    private static class UserInfoPaginationResultHolder {
        String userId;
        String recipeId;

        UserInfoPaginationResultHolder(String userId, String recipeId) {
            this.userId = userId;
            this.recipeId = recipeId;
        }
    }
}
