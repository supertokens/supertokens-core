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

import io.supertokens.inmemorydb.ConnectionWithLocks;
import io.supertokens.inmemorydb.PreparedStatementValueSetter;
import io.supertokens.inmemorydb.QueryExecutorTemplate;
import io.supertokens.inmemorydb.ResultSetValueExtractor;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.supertokens.inmemorydb.PreparedStatementValueSetter.NO_OP_SETTER;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;
import static io.supertokens.inmemorydb.config.Config.getConfig;
import static io.supertokens.pluginInterface.RECIPE_ID.EMAIL_PASSWORD;
import static java.lang.System.currentTimeMillis;

public class EmailPasswordQueries {

    static String getQueryToCreateUsersTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getEmailPasswordUsersTable() + " ("
                + "user_id CHAR(36) NOT NULL," + "email VARCHAR(256) NOT NULL UNIQUE,"
                + "password_hash VARCHAR(256) NOT NULL," + "time_joined BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY (user_id));";
    }

    static String getQueryToCreatePasswordResetTokensTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getPasswordResetTokensTable() + " ("
                + "user_id CHAR(36) NOT NULL," + "token VARCHAR(128) NOT NULL UNIQUE,"
                + "token_expiry BIGINT UNSIGNED NOT NULL," + "PRIMARY KEY (user_id, token),"
                + "FOREIGN KEY (user_id) REFERENCES " + Config.getConfig(start).getEmailPasswordUsersTable()
                + "(user_id) ON DELETE CASCADE ON UPDATE CASCADE);";
    }

    static String getQueryToCreatePasswordResetTokenExpiryIndex(Start start) {
        return "CREATE INDEX emailpassword_password_reset_token_expiry_index ON "
                + Config.getConfig(start).getPasswordResetTokensTable() + "(token_expiry);";
    }

    public static void deleteExpiredPasswordResetTokens(Start start) throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getPasswordResetTokensTable() + " WHERE token_expiry < ?";

        update(start, QUERY, pst -> pst.setLong(1, currentTimeMillis()));
    }

    public static void deleteExpiredEmailVerificationTokens(Start start) throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getEmailVerificationTokensTable() + " WHERE token_expiry < ?";

        update(start, QUERY, pst -> pst.setLong(1, currentTimeMillis()));
    }

    public static void updateUsersPassword_Transaction(Start start, Connection con, String userId, String newPassword)
            throws SQLException, StorageQueryException {
        String QUERY = "UPDATE " + getConfig(start).getEmailPasswordUsersTable()
                + " SET password_hash = ? WHERE user_id = ?";

        update(con, QUERY, pst -> {
            pst.setString(1, newPassword);
            pst.setString(2, userId);
        });
    }

    public static void deleteAllPasswordResetTokensForUser_Transaction(Start start, Connection con, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getPasswordResetTokensTable() + " WHERE user_id = ?";

        update(con, QUERY, pst -> pst.setString(1, userId));
    }

    public static PasswordResetTokenInfo[] getAllPasswordResetTokenInfoForUser(Start start, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT user_id, token, token_expiry FROM "
                + Config.getConfig(start).getPasswordResetTokensTable() + " WHERE user_id = ?";

        return execute(start, QUERY, pst -> pst.setString(1, userId), EmailPasswordQueries::getPasswordResetTokenInfos);
    }

    @NotNull
    private static PasswordResetTokenInfo[] getPasswordResetTokenInfos(ResultSet result)
            throws SQLException, StorageQueryException {
        List<PasswordResetTokenInfo> temp = new ArrayList<>();
        while (result.next()) {
            temp.add(PasswordResetTokenInfoRowMapper.getInstance().mapOrThrow(result));
        }
        PasswordResetTokenInfo[] finalResult = new PasswordResetTokenInfo[temp.size()];
        for (int i = 0; i < temp.size(); i++) {
            finalResult[i] = temp.get(i);
        }
        return finalResult;
    }

    public static PasswordResetTokenInfo[] getAllPasswordResetTokenInfoForUser_Transaction(Start start, Connection con,
            String userId) throws SQLException, StorageQueryException {

        ((ConnectionWithLocks) con).lock(userId + getConfig(start).getPasswordResetTokensTable());

        String QUERY = "SELECT user_id, token, token_expiry FROM " + getConfig(start).getPasswordResetTokensTable()
                + " WHERE user_id = ?";

        return execute(con, QUERY, pst -> pst.setString(1, userId), EmailPasswordQueries::getPasswordResetTokenInfos);
    }

    public static PasswordResetTokenInfo getPasswordResetTokenInfo(Start start, String token)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT user_id, token, token_expiry FROM " + getConfig(start).getPasswordResetTokensTable()
                + " WHERE token = ?";
        return execute(start, QUERY, pst -> pst.setString(1, token), result -> {
            if (result.next()) {
                return PasswordResetTokenInfoRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static void addPasswordResetToken(Start start, String userId, String tokenHash, long expiry)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + getConfig(start).getPasswordResetTokensTable()
                + "(user_id, token, token_expiry)" + " VALUES(?, ?, ?)";

        update(start, QUERY, pst -> {
            pst.setString(1, userId);
            pst.setString(2, tokenHash);
            pst.setLong(3, expiry);
        });
    }

    public static void signUp(Start start, String userId, String email, String passwordHash, long timeJoined)
            throws StorageQueryException, StorageTransactionLogicException {
        start.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try {
                {
                    String QUERY = "INSERT INTO " + getConfig(start).getUsersTable()
                            + "(user_id, recipe_id, time_joined)" + " VALUES(?, ?, ?)";
                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, userId);
                        pst.setString(2, EMAIL_PASSWORD.toString());
                        pst.setLong(3, timeJoined);
                    });
                }

                {
                    String QUERY = "INSERT INTO " + getConfig(start).getEmailPasswordUsersTable()
                            + "(user_id, email, password_hash, time_joined)" + " VALUES(?, ?, ?, ?)";

                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, userId);
                        pst.setString(2, email);
                        pst.setString(3, passwordHash);
                        pst.setLong(4, timeJoined);
                    });
                }

                sqlCon.commit();
            } catch (SQLException throwables) {
                throw new StorageTransactionLogicException(throwables);
            }
            return null;
        });
    }

    public static void deleteUser(Start start, String userId)
            throws StorageQueryException, StorageTransactionLogicException {
        start.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try {
                {
                    String QUERY = "DELETE FROM " + getConfig(start).getUsersTable()
                            + " WHERE user_id = ? AND recipe_id = ?";

                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, userId);
                        pst.setString(2, EMAIL_PASSWORD.toString());
                    });
                }
                // Since SQLite does not enforce foreign key constraints we have to manually delete the mapping for the
                // user.
                {
                    String QUERY = "DELETE FROM " + getConfig(start).getUserIdMappingTable()
                            + " WHERE supertokens_user_id = ?";

                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, userId);
                    });
                }

                {
                    String QUERY = "DELETE FROM " + getConfig(start).getEmailPasswordUsersTable()
                            + " WHERE user_id = ?";

                    update(sqlCon, QUERY, pst -> pst.setString(1, userId));
                }

                {
                    // This will be done by cascading delete where it's supported
                    String QUERY = "DELETE FROM " + getConfig(start).getPasswordResetTokensTable()
                            + " WHERE user_id = ?";

                    update(sqlCon, QUERY, pst -> pst.setString(1, userId));
                }
                sqlCon.commit();
            } catch (SQLException throwables) {
                throw new StorageTransactionLogicException(throwables);
            }
            return null;
        });
    }

    public static UserInfo getUserInfoUsingId(Start start, String id) throws SQLException, StorageQueryException {
        List<String> input = new ArrayList<>();
        input.add(id);
        List<UserInfo> result = getUsersInfoUsingIdList(start, input);
        if (result.size() == 1) {
            return result.get(0);
        }
        return null;
    }

    public static List<UserInfo> getUsersInfoUsingIdList(Start start, List<String> ids)
            throws SQLException, StorageQueryException {

        if (ids.size() > 0) {
            StringBuilder QUERY = new StringBuilder("SELECT user_id, email, password_hash, time_joined FROM "
                    + getConfig(start).getEmailPasswordUsersTable());
            QUERY.append(" WHERE user_id IN (");
            for (int i = 0; i < ids.size(); i++) {

                QUERY.append("?");
                if (i != ids.size() - 1) {
                    // not the last element
                    QUERY.append(",");
                }
            }
            QUERY.append(")");

            return execute(start, QUERY.toString(), pst -> {
                for (int i = 0; i < ids.size(); i++) {
                    // i+1 cause this starts with 1 and not 0
                    pst.setString(i + 1, ids.get(i));
                }
            }, result -> {
                List<UserInfo> finalResult = new ArrayList<>();
                while (result.next()) {
                    finalResult.add(UserInfoRowMapper.getInstance().mapOrThrow(result));
                }
                return finalResult;
            });
        }
        return Collections.emptyList();
    }

    public static UserInfo getUserInfoUsingId_Transaction(Start start, Connection con, String id)
            throws SQLException, StorageQueryException {

        ((ConnectionWithLocks) con).lock(id + getConfig(start).getEmailPasswordUsersTable());

        String QUERY = "SELECT user_id, email, password_hash, time_joined FROM "
                + getConfig(start).getEmailPasswordUsersTable() + " WHERE user_id = ?";
        return execute(con, QUERY, pst -> {
            pst.setString(1, id);
        }, result -> {
            if (result.next()) {
                return UserInfoRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static UserInfo getUserInfoUsingEmail(Start start, String email) throws SQLException, StorageQueryException {
        String QUERY = "SELECT user_id, email, password_hash, time_joined FROM "
                + getConfig(start).getEmailPasswordUsersTable() + " WHERE email = ?";
        return execute(start, QUERY, pst -> pst.setString(1, email), result -> {
            if (result.next()) {
                return UserInfoRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    @Deprecated
    public static UserInfo[] getUsersInfo(Start start, @NotNull Integer limit, @NotNull String timeJoinedOrder)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT user_id, email, password_hash, time_joined FROM "
                + getConfig(start).getEmailPasswordUsersTable() + " ORDER BY time_joined " + timeJoinedOrder
                + ", user_id DESC LIMIT ?";
        return execute(start, QUERY, pst -> pst.setInt(1, limit), new UserInfoResultExtractor());
    }

    @Deprecated
    public static UserInfo[] getUsersInfo(Start start, @NotNull String userId, @NotNull Long timeJoined,
            @NotNull Integer limit, @NotNull String timeJoinedOrder) throws SQLException, StorageQueryException {
        String timeJoinedOrderSymbol = timeJoinedOrder.equals("ASC") ? ">" : "<";
        String QUERY = "SELECT user_id, email, password_hash, time_joined FROM "
                + getConfig(start).getEmailPasswordUsersTable() + " WHERE time_joined " + timeJoinedOrderSymbol
                + " ? OR (time_joined = ? AND user_id <= ?) ORDER BY time_joined " + timeJoinedOrder
                + ", user_id DESC LIMIT ?";
        final PreparedStatementValueSetter preparedStatementValueSetter = pst -> {
            pst.setLong(1, timeJoined);
            pst.setLong(2, timeJoined);
            pst.setString(3, userId);
            pst.setInt(4, limit);
        };

        return execute(start, QUERY, preparedStatementValueSetter, new UserInfoResultExtractor());
    }

    @Deprecated
    public static long getUsersCount(Start start) throws SQLException, StorageQueryException {
        String QUERY = "SELECT COUNT(*) as total FROM " + getConfig(start).getEmailPasswordUsersTable();
        return execute(start, QUERY, NO_OP_SETTER, result -> {
            if (result.next()) {
                return result.getLong("total");
            }
            return 0L;
        });
    }

    public static void updateUsersEmail_Transaction(Start start, Connection conn, String userId, String email)
            throws SQLException, StorageQueryException {
        String QUERY = "UPDATE " + getConfig(start).getEmailPasswordUsersTable() + " SET email = ? WHERE user_id = ?";

        update(conn, QUERY, pst -> {
            pst.setString(1, email);
            pst.setString(2, userId);
        });
    }

    private static class PasswordResetTokenInfoRowMapper implements RowMapper<PasswordResetTokenInfo, ResultSet> {
        private static final PasswordResetTokenInfoRowMapper INSTANCE = new PasswordResetTokenInfoRowMapper();

        private PasswordResetTokenInfoRowMapper() {
        }

        private static PasswordResetTokenInfoRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public PasswordResetTokenInfo map(ResultSet result) throws Exception {
            return new PasswordResetTokenInfo(result.getString("user_id"), result.getString("token"),
                    result.getLong("token_expiry"));
        }
    }

    private static class UserInfoRowMapper implements RowMapper<UserInfo, ResultSet> {
        private static final UserInfoRowMapper INSTANCE = new UserInfoRowMapper();

        private UserInfoRowMapper() {
        }

        private static UserInfoRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public UserInfo map(ResultSet result) throws Exception {
            return new UserInfo(result.getString("user_id"), result.getString("email"),
                    result.getString("password_hash"), result.getLong("time_joined"));
        }
    }

    private static class UserInfoResultExtractor implements ResultSetValueExtractor<UserInfo[]> {
        @Override
        public UserInfo[] extract(ResultSet result) throws SQLException, StorageQueryException {
            List<UserInfo> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(UserInfoRowMapper.getInstance().mapOrThrow(result));
            }
            return temp.toArray(UserInfo[]::new);
        }
    }

}
