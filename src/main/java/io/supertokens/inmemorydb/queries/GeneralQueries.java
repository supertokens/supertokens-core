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
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public static void createTablesIfNotExists(Start start, Main main) throws SQLException {
        if (!doesTableExists(start, Config.getConfig(start).getKeyValueTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pst = con.prepareStatement(getQueryToCreateKeyValueTable(start))) {
                pst.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getUsersTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pst = con.prepareStatement(getQueryToCreateUsersTable(start))) {
                pst.executeUpdate();
            }

            // index
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pstIndex = con.prepareStatement(getQueryToCreateUserPaginationIndex(start))) {
                pstIndex.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getAccessTokenSigningKeysTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pst = con
                            .prepareStatement(SessionQueries.getQueryToCreateAccessTokenSigningKeysTable(start))) {
                pst.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getSessionInfoTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pst = con
                            .prepareStatement(SessionQueries.getQueryToCreateSessionInfoTable(start))) {
                pst.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getEmailPasswordUsersTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pst = con
                            .prepareStatement(EmailPasswordQueries.getQueryToCreateUsersTable(start))) {
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
                    PreparedStatement pstIndex = con.prepareStatement(
                            EmailPasswordQueries.getQueryToCreatePasswordResetTokenExpiryIndex(start))) {
                pstIndex.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getEmailVerificationTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pst = con
                            .prepareStatement(EmailVerificationQueries.getQueryToCreateEmailVerificationTable(start))) {
                pst.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getEmailVerificationTokensTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pst = con.prepareStatement(
                            EmailVerificationQueries.getQueryToCreateEmailVerificationTokensTable(start))) {
                pst.executeUpdate();
            }
            // index
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pstIndex = con.prepareStatement(
                            EmailVerificationQueries.getQueryToCreateEmailVerificationTokenExpiryIndex(start))) {
                pstIndex.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getThirdPartyUsersTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pst = con.prepareStatement(ThirdPartyQueries.getQueryToCreateUsersTable(start))) {
                pst.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getJWTSigningKeysTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pst = con
                            .prepareStatement(JWTSigningQueries.getQueryToCreateJWTSigningTable(start))) {
                pst.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getPasswordlessUsersTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pst = con
                            .prepareStatement(PasswordlessQueries.getQueryToCreateUsersTable(start))) {
                pst.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getPasswordlessDevicesTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pst = con
                            .prepareStatement(PasswordlessQueries.getQueryToCreateDevicesTable(start))) {
                pst.executeUpdate();
            }
            // index
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pstIndex = con
                            .prepareStatement(PasswordlessQueries.getQueryToCreateDeviceEmailIndex(start))) {
                pstIndex.executeUpdate();
            }
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pstIndex = con
                            .prepareStatement(PasswordlessQueries.getQueryToCreateDevicePhoneNumberIndex(start))) {
                pstIndex.executeUpdate();
            }
        }

        if (!doesTableExists(start, Config.getConfig(start).getPasswordlessCodesTable())) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CREATING_NEW_TABLE, null);
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pst = con
                            .prepareStatement(PasswordlessQueries.getQueryToCreateCodesTable(start))) {
                pst.executeUpdate();
            }
            // index
            try (Connection con = ConnectionPool.getConnection(start);
                    PreparedStatement pstIndex = con
                            .prepareStatement(PasswordlessQueries.getQueryToCreateCodeCreatedAtIndex(start))) {
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

    public static void setKeyValue(Start start, String key, KeyValueInfo info) throws SQLException {
        try (Connection con = ConnectionPool.getConnection(start)) {
            setKeyValue_Transaction(start, con, key, info);
        }
    }

    public static void deleteKeyValue_Transaction(Start start, Connection con, String key) throws SQLException {

        String QUERY = "DELETE FROM " + Config.getConfig(start).getKeyValueTable() + " WHERE name = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, key);
            pst.executeUpdate();
        }
    }

    public static void deleteKeyValue(Start start, String key) throws SQLException {
        try (Connection con = ConnectionPool.getConnection(start)) {
            deleteKeyValue_Transaction(start, con, key);
        }
    }

    public static KeyValueInfo getKeyValue(Start start, String key) throws SQLException, StorageQueryException {
        String QUERY = "SELECT value, created_at_time FROM " + Config.getConfig(start).getKeyValueTable()
                + " WHERE name = ?";

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

        String QUERY = "SELECT value, created_at_time FROM " + Config.getConfig(start).getKeyValueTable()
                + " WHERE name = ?";

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

    public static long getUsersCount(Start start, RECIPE_ID[] includeRecipeIds) throws SQLException {
        StringBuilder QUERY = new StringBuilder(
                "SELECT COUNT(*) as total FROM " + Config.getConfig(start).getUsersTable());
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

        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY.toString())) {
            ResultSet result = pst.executeQuery();
            if (result.next()) {
                return result.getLong("total");
            }
            return 0;
        }
    }

    public static AuthRecipeUserInfo[] getUsers(Start start, @NotNull Integer limit, @NotNull String timeJoinedOrder,
            @Nullable RECIPE_ID[] includeRecipeIds, @Nullable String userId, @Nullable Long timeJoined)
            throws SQLException, StorageQueryException {

        // This list will be used to keep track of the result's order from the db
        List<UserInfoPaginationResultHolder> usersFromQuery = new ArrayList<>();

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

            ResultSet result;
            if (timeJoined != null && userId != null) {
                String recipeIdCondition = RECIPE_ID_CONDITION.toString();
                if (!recipeIdCondition.equals("")) {
                    recipeIdCondition = recipeIdCondition + " AND";
                }
                String timeJoinedOrderSymbol = timeJoinedOrder.equals("ASC") ? ">" : "<";
                String QUERY = "SELECT user_id, recipe_id FROM " + Config.getConfig(start).getUsersTable() + " WHERE "
                        + recipeIdCondition + " (time_joined " + timeJoinedOrderSymbol
                        + " ? OR (time_joined = ? AND user_id <= ?)) ORDER BY time_joined " + timeJoinedOrder
                        + ", user_id DESC LIMIT ?";
                try (Connection con = ConnectionPool.getConnection(start);
                        PreparedStatement pst = con.prepareStatement(QUERY)) {
                    pst.setLong(1, timeJoined);
                    pst.setLong(2, timeJoined);
                    pst.setString(3, userId);
                    pst.setInt(4, limit);
                    result = pst.executeQuery();
                    while (result.next()) {
                        usersFromQuery.add(new UserInfoPaginationResultHolder(result.getString("user_id"),
                                result.getString("recipe_id")));
                    }
                }
            } else {
                String recipeIdCondition = RECIPE_ID_CONDITION.toString();
                if (!recipeIdCondition.equals("")) {
                    recipeIdCondition = " WHERE " + recipeIdCondition;
                }
                String QUERY = "SELECT user_id, recipe_id FROM " + Config.getConfig(start).getUsersTable()
                        + recipeIdCondition + " ORDER BY time_joined " + timeJoinedOrder + ", user_id DESC LIMIT ?";
                try (Connection con = ConnectionPool.getConnection(start);
                        PreparedStatement pst = con.prepareStatement(QUERY)) {
                    pst.setInt(1, limit);
                    result = pst.executeQuery();
                    while (result.next()) {
                        usersFromQuery.add(new UserInfoPaginationResultHolder(result.getString("user_id"),
                                result.getString("recipe_id")));
                    }
                }
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
