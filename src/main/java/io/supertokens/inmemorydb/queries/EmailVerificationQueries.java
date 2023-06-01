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

import io.supertokens.inmemorydb.ConnectionWithLocks;
import io.supertokens.inmemorydb.QueryExecutorTemplate;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.emailverification.EmailVerificationTokenInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;
import static io.supertokens.inmemorydb.config.Config.getConfig;
import static java.lang.System.currentTimeMillis;

public class EmailVerificationQueries {

    static String getQueryToCreateEmailVerificationTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getEmailVerificationTable() + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "user_id VARCHAR(128) NOT NULL,"
                + "email VARCHAR(256) NOT NULL,"
                + "PRIMARY KEY (app_id, user_id, email),"
                + "FOREIGN KEY (app_id) REFERENCES " + Config.getConfig(start).getAppsTable()
                + " (app_id) ON DELETE CASCADE"
                + ");";
    }

    static String getQueryToCreateEmailVerificationTokensTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getEmailVerificationTokensTable() + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "tenant_id VARCHAR(64) DEFAULT 'public',"
                + "user_id VARCHAR(128) NOT NULL,"
                + "email VARCHAR(256) NOT NULL,"
                + "token VARCHAR(128) NOT NULL UNIQUE,"
                + "token_expiry BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY (app_id, tenant_id, user_id, email, token),"
                + "FOREIGN KEY (app_id, tenant_id) REFERENCES " + Config.getConfig(start).getTenantsTable()
                + " (app_id, tenant_id) ON DELETE CASCADE"
                + ")";
    }

    static String getQueryToCreateEmailVerificationTokenExpiryIndex(Start start) {
        return "CREATE INDEX emailverification_tokens_index ON "
                + Config.getConfig(start).getEmailVerificationTokensTable() + "(token_expiry);";
    }


    public static void deleteExpiredEmailVerificationTokens(Start start) throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getEmailVerificationTokensTable() + " WHERE token_expiry < ?";

        update(start, QUERY, pst -> pst.setLong(1, currentTimeMillis()));
    }

    public static void updateUsersIsEmailVerified_Transaction(Start start, Connection con, AppIdentifier appIdentifier,
                                                              String userId, String email,
                                                              boolean isEmailVerified) throws SQLException, StorageQueryException {

        if (isEmailVerified) {
            String QUERY = "INSERT INTO " + getConfig(start).getEmailVerificationTable()
                    + "(app_id, user_id, email) VALUES(?, ?, ?)";

            update(con, QUERY, pst -> {
                pst.setString(1, appIdentifier.getAppId());
                pst.setString(2, userId);
                pst.setString(3, email);
            });
        } else {
            String QUERY = "DELETE FROM " + getConfig(start).getEmailVerificationTable()
                    + " WHERE app_id = ? AND user_id = ? AND email = ?";

            update(con, QUERY, pst -> {
                pst.setString(1, appIdentifier.getAppId());
                pst.setString(2, userId);
                pst.setString(3, email);
            });
        }
    }

    public static void deleteAllEmailVerificationTokensForUser_Transaction(Start start, Connection con,
                                                                           TenantIdentifier tenantIdentifier, String userId,
                                                                           String email) throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getEmailVerificationTokensTable()
                + " WHERE app_id = ? AND tenant_id = ? AND user_id = ? AND email = ?";

        update(con, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
            pst.setString(4, email);
        });
    }

    public static EmailVerificationTokenInfo getEmailVerificationTokenInfo(Start start, TenantIdentifier tenantIdentifier,
                                                                           String token)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT user_id, token, token_expiry, email FROM "
                + getConfig(start).getEmailVerificationTokensTable()
                + " WHERE app_id = ? AND tenant_id = ? AND token = ?";
        return execute(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, token);
        }, result -> {
            if (result.next()) {
                return EmailVerificationTokenInfoRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static void addEmailVerificationToken(Start start, TenantIdentifier tenantIdentifier, String userId, String tokenHash, long expiry,
                                                 String email) throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + getConfig(start).getEmailVerificationTokensTable()
                + "(app_id, tenant_id, user_id, token, token_expiry, email)" + " VALUES(?, ?, ?, ?, ?, ?)";

        update(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
            pst.setString(4, tokenHash);
            pst.setLong(5, expiry);
            pst.setString(6, email);
        });
    }

    public static EmailVerificationTokenInfo[] getAllEmailVerificationTokenInfoForUser_Transaction(Start start,
                                                                                                   Connection con,
                                                                                                   TenantIdentifier tenantIdentifier,
                                                                                                   String userId, String email) throws SQLException, StorageQueryException {

        ((ConnectionWithLocks) con).lock(tenantIdentifier.getAppId() + "~" + tenantIdentifier.getTenantId() + "~" + userId + "~" + email + Config.getConfig(start).getEmailVerificationTokensTable());

        String QUERY = "SELECT user_id, token, token_expiry, email FROM "
                + getConfig(start).getEmailVerificationTokensTable() + " WHERE app_id = ? AND tenant_id = ? AND user_id = ? AND email = ?";

        return execute(con, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
            pst.setString(4, email);
        }, result -> {
            List<EmailVerificationTokenInfo> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(EmailVerificationTokenInfoRowMapper.getInstance().mapOrThrow(result));
            }
            EmailVerificationTokenInfo[] finalResult = new EmailVerificationTokenInfo[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        });
    }

    public static EmailVerificationTokenInfo[] getAllEmailVerificationTokenInfoForUser(Start start,
                                                                                       TenantIdentifier tenantIdentifier,
                                                                                       String userId,
                                                                                       String email) throws SQLException, StorageQueryException {
        String QUERY = "SELECT user_id, token, token_expiry, email FROM "
                + getConfig(start).getEmailVerificationTokensTable() + " WHERE app_id = ? AND tenant_id = ? AND user_id = ? AND email = ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
            pst.setString(4, email);
        }, result -> {
            List<EmailVerificationTokenInfo> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(EmailVerificationTokenInfoRowMapper.getInstance().mapOrThrow(result));
            }
            EmailVerificationTokenInfo[] finalResult = new EmailVerificationTokenInfo[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        });
    }

    public static boolean isEmailVerified(Start start, AppIdentifier appIdentifier, String userId, String email)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + getConfig(start).getEmailVerificationTable()
                + " WHERE app_id = ? AND user_id = ? AND email = ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
            pst.setString(3, email);
        }, result -> result.next());
    }

    public static void deleteUserInfo(Start start, AppIdentifier appIdentifier, String userId)
            throws StorageQueryException, StorageTransactionLogicException {
        start.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try {
                {
                    String QUERY = "DELETE FROM " + getConfig(start).getEmailVerificationTable()
                            + " WHERE app_id = ? AND user_id = ?";
                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, appIdentifier.getAppId());
                        pst.setString(2, userId);
                    });
                }

                {
                    String QUERY = "DELETE FROM " + getConfig(start).getEmailVerificationTokensTable()
                            + " WHERE app_id = ? AND user_id = ?";

                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, appIdentifier.getAppId());
                        pst.setString(2, userId);
                    });
                }

                sqlCon.commit();
            } catch (SQLException throwables) {
                throw new StorageTransactionLogicException(throwables);
            }
            return null;
        });
    }

    public static boolean deleteUserInfo(Start start, TenantIdentifier tenantIdentifier, String userId)
            throws StorageQueryException, SQLException {
        String QUERY = "DELETE FROM " + getConfig(start).getEmailVerificationTokensTable()
                + " WHERE app_id = ? AND tenant_id = ? AND user_id = ?";

        int numRows = update(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
        });
        return numRows > 0;
    }

    public static void unverifyEmail(Start start, AppIdentifier appIdentifier, String userId, String email)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getEmailVerificationTable()
                + " WHERE app_id = ? AND user_id = ? AND email = ?";

        update(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
            pst.setString(3, email);
        });
    }

    public static void revokeAllTokens(Start start, TenantIdentifier tenantIdentifier, String userId, String email)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getEmailVerificationTokensTable()
                + " WHERE app_id = ? AND tenant_id = ? AND user_id = ? AND email = ?";

        update(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
            pst.setString(4, email);
        });
    }

    public static boolean isUserIdBeingUsedForEmailVerification(Start start, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + getConfig(start).getEmailVerificationTokensTable()
                + " WHERE app_id = ? AND user_id = ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, ResultSet::next);
    }

    private static class EmailVerificationTokenInfoRowMapper
            implements RowMapper<EmailVerificationTokenInfo, ResultSet> {
        private static final EmailVerificationTokenInfoRowMapper INSTANCE = new EmailVerificationTokenInfoRowMapper();

        private EmailVerificationTokenInfoRowMapper() {
        }

        private static EmailVerificationTokenInfoRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public EmailVerificationTokenInfo map(ResultSet result) throws Exception {
            return new EmailVerificationTokenInfo(result.getString("user_id"), result.getString("token"),
                    result.getLong("token_expiry"), result.getString("email"));
        }
    }
}
