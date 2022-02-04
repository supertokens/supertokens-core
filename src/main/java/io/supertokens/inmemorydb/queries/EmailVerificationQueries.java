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

import io.supertokens.inmemorydb.ConnectionPool;
import io.supertokens.inmemorydb.ConnectionWithLocks;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.emailverification.EmailVerificationTokenInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class EmailVerificationQueries {

    static String getQueryToCreateEmailVerificationTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getEmailVerificationTable() + " ("
                + "user_id VARCHAR(128) NOT NULL," + "email VARCHAR(256) NOT NULL," + "PRIMARY KEY (user_id, email));";
    }

    static String getQueryToCreateEmailVerificationTokensTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getEmailVerificationTokensTable() + " ("
                + "user_id VARCHAR(128) NOT NULL," + "email VARCHAR(256) NOT NULL,"
                + "token VARCHAR(128) NOT NULL UNIQUE," + "token_expiry BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY (user_id, email, token))";
    }

    static String getQueryToCreateEmailVerificationTokenExpiryIndex(Start start) {
        return "CREATE INDEX emailverification_tokens_index ON "
                + Config.getConfig(start).getEmailVerificationTokensTable() + "(token_expiry);";
    }

    public static void deleteExpiredEmailVerificationTokens(Start start) throws SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getEmailVerificationTokensTable()
                + " WHERE token_expiry < ?";

        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setLong(1, System.currentTimeMillis());
            pst.executeUpdate();
        }
    }

    public static void updateUsersIsEmailVerified_Transaction(Start start, Connection con, String userId, String email,
            boolean isEmailVerified) throws SQLException {

        if (isEmailVerified) {
            String QUERY = "INSERT INTO " + Config.getConfig(start).getEmailVerificationTable()
                    + "(user_id, email) VALUES(?, ?)";

            try (PreparedStatement pst = con.prepareStatement(QUERY)) {
                pst.setString(1, userId);
                pst.setString(2, email);
                pst.executeUpdate();
            }
        } else {
            String QUERY = "DELETE FROM " + Config.getConfig(start).getEmailVerificationTable()
                    + " WHERE user_id = ? AND email = ?";

            try (PreparedStatement pst = con.prepareStatement(QUERY)) {
                pst.setString(1, userId);
                pst.setString(2, email);
                pst.executeUpdate();
            }
        }
    }

    public static void deleteAllEmailVerificationTokensForUser_Transaction(Start start, Connection con, String userId,
            String email) throws SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getEmailVerificationTokensTable()
                + " WHERE user_id = ? AND email = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            pst.setString(2, email);
            pst.executeUpdate();
        }
    }

    public static EmailVerificationTokenInfo getEmailVerificationTokenInfo(Start start, String token)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT user_id, token, token_expiry, email FROM "
                + Config.getConfig(start).getEmailVerificationTokensTable() + " WHERE token = ?";
        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, token);
            try (ResultSet result = pst.executeQuery()) {
                if (result.next()) {
                    return EmailVerificationTokenInfoRowMapper.getInstance().mapOrThrow(result);
                }
            }
        }
        return null;
    }

    public static void addEmailVerificationToken(Start start, String userId, String tokenHash, long expiry,
            String email) throws SQLException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getEmailVerificationTokensTable()
                + "(user_id, token, token_expiry, email)" + " VALUES(?, ?, ?, ?)";

        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            pst.setString(2, tokenHash);
            pst.setLong(3, expiry);
            pst.setString(4, email);
            pst.executeUpdate();
        }
    }

    public static EmailVerificationTokenInfo[] getAllEmailVerificationTokenInfoForUser_Transaction(Start start,
            Connection con, String userId, String email) throws SQLException, StorageQueryException {

        ((ConnectionWithLocks) con).lock(userId + Config.getConfig(start).getEmailVerificationTokensTable());

        String QUERY = "SELECT user_id, token, token_expiry, email FROM "
                + Config.getConfig(start).getEmailVerificationTokensTable() + " WHERE user_id = ? AND email = ?";

        try (PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            pst.setString(2, email);
            try (ResultSet result = pst.executeQuery()) {
                List<EmailVerificationTokenInfo> temp = new ArrayList<>();
                while (result.next()) {
                    temp.add(EmailVerificationTokenInfoRowMapper.getInstance().mapOrThrow(result));
                }
                EmailVerificationTokenInfo[] finalResult = new EmailVerificationTokenInfo[temp.size()];
                for (int i = 0; i < temp.size(); i++) {
                    finalResult[i] = temp.get(i);
                }
                return finalResult;
            }
        }
    }

    public static EmailVerificationTokenInfo[] getAllEmailVerificationTokenInfoForUser(Start start, String userId,
            String email) throws SQLException, StorageQueryException {
        String QUERY = "SELECT user_id, token, token_expiry, email FROM "
                + Config.getConfig(start).getEmailVerificationTokensTable() + " WHERE user_id = ? AND email = ?";

        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            pst.setString(2, email);
            try (ResultSet result = pst.executeQuery()) {
                List<EmailVerificationTokenInfo> temp = new ArrayList<>();
                while (result.next()) {
                    temp.add(EmailVerificationTokenInfoRowMapper.getInstance().mapOrThrow(result));
                }
                EmailVerificationTokenInfo[] finalResult = new EmailVerificationTokenInfo[temp.size()];
                for (int i = 0; i < temp.size(); i++) {
                    finalResult[i] = temp.get(i);
                }
                return finalResult;
            }
        }
    }

    public static boolean isEmailVerified(Start start, String userId, String email)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getEmailVerificationTable()
                + " WHERE user_id = ? AND email = ?";

        try (Connection con = ConnectionPool.getConnection(start);
                PreparedStatement pst = con.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            pst.setString(2, email);
            try (ResultSet result = pst.executeQuery()) {
                return result.next();
            }
        }
    }

    public static void unverifyEmail(Start start, String userId, String email) throws SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getEmailVerificationTable()
                + " WHERE user_id = ? AND email = ?";

        try (Connection conn = ConnectionPool.getConnection(start);
                PreparedStatement pst = conn.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            pst.setString(2, email);
            pst.executeUpdate();
        }
    }

    public static void deleteUserInfo(Start start, String userId)
            throws StorageQueryException, StorageTransactionLogicException {
        start.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try {
                {
                    String QUERY = "DELETE FROM " + Config.getConfig(start).getEmailVerificationTable()
                            + " WHERE user_id = ?";
                    try (PreparedStatement pst = sqlCon.prepareStatement(QUERY)) {
                        pst.setString(1, userId);
                        pst.executeUpdate();
                    }
                }

                {
                    String QUERY = "DELETE FROM " + Config.getConfig(start).getEmailVerificationTokensTable()
                            + " WHERE user_id = ?";

                    try (PreparedStatement pst = sqlCon.prepareStatement(QUERY)) {
                        pst.setString(1, userId);
                        pst.executeUpdate();
                    }
                }

                sqlCon.commit();
            } catch (SQLException throwables) {
                throw new StorageTransactionLogicException(throwables);
            }
            return null;
        });
    }

    public static void revokeAllTokens(Start start, String userId, String email) throws SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getEmailVerificationTokensTable()
                + " WHERE user_id = ? AND email = ?";

        try (Connection conn = ConnectionPool.getConnection(start);
                PreparedStatement pst = conn.prepareStatement(QUERY)) {
            pst.setString(1, userId);
            pst.setString(2, email);
            pst.executeUpdate();
        }
    }

    private static class EmailVerificationTokenInfoRowMapper
            implements RowMapper<EmailVerificationTokenInfo, ResultSet> {
        private static final EmailVerificationQueries.EmailVerificationTokenInfoRowMapper INSTANCE = new EmailVerificationQueries.EmailVerificationTokenInfoRowMapper();

        private EmailVerificationTokenInfoRowMapper() {
        }

        private static EmailVerificationQueries.EmailVerificationTokenInfoRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public EmailVerificationTokenInfo map(ResultSet result) throws Exception {
            return new EmailVerificationTokenInfo(result.getString("user_id"), result.getString("token"),
                    result.getLong("token_expiry"), result.getString("email"));
        }
    }
}
