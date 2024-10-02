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
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.Utils;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.emailpassword.PasswordResetTokenInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;
import static io.supertokens.inmemorydb.config.Config.getConfig;
import static io.supertokens.pluginInterface.RECIPE_ID.EMAIL_PASSWORD;
import static java.lang.System.currentTimeMillis;

public class EmailPasswordQueries {

    static String getQueryToCreateUsersTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getEmailPasswordUsersTable() + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "user_id CHAR(36) NOT NULL,"
                + "email VARCHAR(256) NOT NULL,"
                + "password_hash VARCHAR(256) NOT NULL,"
                + "time_joined BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY (app_id, user_id),"
                + "FOREIGN KEY(app_id, user_id)"
                + " REFERENCES " + Config.getConfig(start).getAppIdToUserIdTable() +
                " (app_id, user_id) ON DELETE CASCADE"
                + ");";
    }

    static String getQueryToCreateEmailPasswordUserToTenantTable(Start start) {
        String emailPasswordUserToTenantTable = Config.getConfig(start).getEmailPasswordUserToTenantTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + emailPasswordUserToTenantTable + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "tenant_id VARCHAR(64) DEFAULT 'public',"
                + "user_id CHAR(36) NOT NULL,"
                + "email VARCHAR(256) NOT NULL,"
                + "UNIQUE (app_id, tenant_id, email),"
                + "PRIMARY KEY (app_id, tenant_id, user_id),"
                + "FOREIGN KEY (app_id, tenant_id, user_id)"
                + " REFERENCES " + Config.getConfig(start).getUsersTable() +
                "(app_id, tenant_id, user_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    static String getQueryToCreatePasswordResetTokensTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getPasswordResetTokensTable() + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "user_id CHAR(36) NOT NULL,"
                + "token VARCHAR(128) NOT NULL UNIQUE,"
                + "email VARCHAR(256)," // nullable cause of backwards compatibility.
                + "token_expiry BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY (app_id, user_id, token),"
                + "FOREIGN KEY (app_id, user_id) REFERENCES " + Config.getConfig(start).getAppIdToUserIdTable()
                + " (app_id, user_id) ON DELETE CASCADE ON UPDATE CASCADE"
                + ");";
    }

    static String getQueryToCreatePasswordResetTokenExpiryIndex(Start start) {
        return "CREATE INDEX emailpassword_password_reset_token_expiry_index ON "
                + Config.getConfig(start).getPasswordResetTokensTable() + "(token_expiry);";
    }

    public static void deleteExpiredPasswordResetTokens(Start start) throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getPasswordResetTokensTable() + " WHERE token_expiry < ?";

        update(start, QUERY, pst -> pst.setLong(1, currentTimeMillis()));
    }

    public static void updateUsersPassword_Transaction(Start start, Connection con, AppIdentifier appIdentifier,
                                                       String userId, String newPassword)
            throws SQLException, StorageQueryException {
        String QUERY = "UPDATE " + getConfig(start).getEmailPasswordUsersTable()
                + " SET password_hash = ? WHERE app_id = ? AND user_id = ?";

        update(con, QUERY, pst -> {
            pst.setString(1, newPassword);
            pst.setString(2, appIdentifier.getAppId());
            pst.setString(3, userId);
        });
    }

    public static void updateUsersEmail_Transaction(Start start, Connection con, AppIdentifier appIdentifier,
                                                    String userId, String newEmail)
            throws SQLException, StorageQueryException {
        {
            String QUERY = "UPDATE " + getConfig(start).getEmailPasswordUsersTable()
                    + " SET email = ? WHERE app_id = ? AND user_id = ?";

            update(con, QUERY, pst -> {
                pst.setString(1, newEmail);
                pst.setString(2, appIdentifier.getAppId());
                pst.setString(3, userId);
            });
        }
        {
            String QUERY = "UPDATE " + getConfig(start).getEmailPasswordUserToTenantTable()
                    + " SET email = ? WHERE app_id = ? AND user_id = ?";

            update(con, QUERY, pst -> {
                pst.setString(1, newEmail);
                pst.setString(2, appIdentifier.getAppId());
                pst.setString(3, userId);
            });
        }
    }

    public static void deleteAllPasswordResetTokensForUser_Transaction(Start start, Connection con,
                                                                       AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {

        String QUERY =
                "DELETE FROM " + getConfig(start).getPasswordResetTokensTable() + " WHERE app_id = ? AND user_id = ?";

        update(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        });
    }

    public static PasswordResetTokenInfo[] getAllPasswordResetTokenInfoForUser(Start start, AppIdentifier appIdentifier,
                                                                               String userId)
            throws StorageQueryException, SQLException {
        String QUERY =
                "SELECT user_id, token, token_expiry, email FROM " + getConfig(start).getPasswordResetTokensTable()
                        + " WHERE app_id = ? AND user_id = ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, result -> {
            List<PasswordResetTokenInfo> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(PasswordResetRowMapper.getInstance().mapOrThrow(result));
            }
            PasswordResetTokenInfo[] finalResult = new PasswordResetTokenInfo[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        });
    }

    public static PasswordResetTokenInfo[] getAllPasswordResetTokenInfoForUser_Transaction(Start start, Connection con,
                                                                                           AppIdentifier appIdentifier,
                                                                                           String userId)
            throws SQLException, StorageQueryException {

        ((ConnectionWithLocks) con).lock(
                appIdentifier.getAppId() + "~" + userId + Config.getConfig(start).getPasswordResetTokensTable());


        String QUERY =
                "SELECT user_id, token, token_expiry, email FROM " + getConfig(start).getPasswordResetTokensTable()
                        + " WHERE app_id = ? AND user_id = ?";

        return execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, result -> {
            List<PasswordResetTokenInfo> temp = new ArrayList<>();
            while (result.next()) {
                temp.add(PasswordResetRowMapper.getInstance().mapOrThrow(result));
            }
            PasswordResetTokenInfo[] finalResult = new PasswordResetTokenInfo[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        });
    }

    public static PasswordResetTokenInfo getPasswordResetTokenInfo(Start start, AppIdentifier appIdentifier,
                                                                   String token)
            throws SQLException, StorageQueryException {
        String QUERY =
                "SELECT user_id, token, token_expiry, email FROM " + getConfig(start).getPasswordResetTokensTable()
                        + " WHERE app_id = ? AND token = ?";
        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, token);
        }, result -> {
            if (result.next()) {
                return PasswordResetRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static void addPasswordResetToken(Start start, AppIdentifier appIdentifier, String userId, String tokenHash,
                                             long expiry, String email)
            throws SQLException, StorageQueryException {
        if (email != null) {
            String QUERY = "INSERT INTO " + getConfig(start).getPasswordResetTokensTable()
                    + "(app_id, user_id, token, token_expiry, email)" + " VALUES(?, ?, ?, ?, ?)";

            update(start, QUERY, pst -> {
                pst.setString(1, appIdentifier.getAppId());
                pst.setString(2, userId);
                pst.setString(3, tokenHash);
                pst.setLong(4, expiry);
                pst.setString(5, email);
            });
        } else {
            String QUERY = "INSERT INTO " + getConfig(start).getPasswordResetTokensTable()
                    + "(app_id, user_id, token, token_expiry)" + " VALUES(?, ?, ?, ?)";

            update(start, QUERY, pst -> {
                pst.setString(1, appIdentifier.getAppId());
                pst.setString(2, userId);
                pst.setString(3, tokenHash);
                pst.setLong(4, expiry);
            });
        }
    }

    public static AuthRecipeUserInfo signUp(Start start, TenantIdentifier tenantIdentifier, String userId, String email,
                                            String passwordHash, long timeJoined)
            throws StorageQueryException, StorageTransactionLogicException {
        return start.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try {
                { // app_id_to_user_id
                    String QUERY = "INSERT INTO " + getConfig(start).getAppIdToUserIdTable()
                            + "(app_id, user_id, primary_or_recipe_user_id, recipe_id)" + " VALUES(?, ?, ?, ?)";
                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, tenantIdentifier.getAppId());
                        pst.setString(2, userId);
                        pst.setString(3, userId);
                        pst.setString(4, EMAIL_PASSWORD.toString());
                    });
                }

                { // all_auth_recipe_users
                    String QUERY = "INSERT INTO " + getConfig(start).getUsersTable()
                            +
                            "(app_id, tenant_id, user_id, primary_or_recipe_user_id, recipe_id, time_joined, " +
                            "primary_or_recipe_user_time_joined)" +
                            " VALUES(?, ?, ?, ?, ?, ?, ?)";
                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, tenantIdentifier.getAppId());
                        pst.setString(2, tenantIdentifier.getTenantId());
                        pst.setString(3, userId);
                        pst.setString(4, userId);
                        pst.setString(5, EMAIL_PASSWORD.toString());
                        pst.setLong(6, timeJoined);
                        pst.setLong(7, timeJoined);
                    });
                }

                { // emailpassword_users
                    String QUERY = "INSERT INTO " + getConfig(start).getEmailPasswordUsersTable()
                            + "(app_id, user_id, email, password_hash, time_joined)" + " VALUES(?, ?, ?, ?, ?)";

                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, tenantIdentifier.getAppId());
                        pst.setString(2, userId);
                        pst.setString(3, email);
                        pst.setString(4, passwordHash);
                        pst.setLong(5, timeJoined);
                    });
                }

                { // emailpassword_user_to_tenant
                    String QUERY = "INSERT INTO " + getConfig(start).getEmailPasswordUserToTenantTable()
                            + "(app_id, tenant_id, user_id, email)" + " VALUES(?, ?, ?, ?)";

                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, tenantIdentifier.getAppId());
                        pst.setString(2, tenantIdentifier.getTenantId());
                        pst.setString(3, userId);
                        pst.setString(4, email);
                    });
                }

                UserInfoPartial userInfo = new UserInfoPartial(userId, email, passwordHash, timeJoined);
                fillUserInfoWithTenantIds_transaction(start, sqlCon, tenantIdentifier.toAppIdentifier(), userInfo);
                fillUserInfoWithVerified_transaction(start, sqlCon, tenantIdentifier.toAppIdentifier(), userInfo);
                sqlCon.commit();
                return AuthRecipeUserInfo.create(userId, false, userInfo.toLoginMethod());
            } catch (SQLException throwables) {
                throw new StorageTransactionLogicException(throwables);
            }
        });
    }

    public static void deleteUser_Transaction(Connection sqlCon, Start start, AppIdentifier appIdentifier,
                                              String userId, boolean deleteUserIdMappingToo)
            throws StorageQueryException, SQLException {
        if (deleteUserIdMappingToo) {
            String QUERY = "DELETE FROM " + getConfig(start).getAppIdToUserIdTable()
                    + " WHERE app_id = ? AND user_id = ?";

            update(sqlCon, QUERY, pst -> {
                pst.setString(1, appIdentifier.getAppId());
                pst.setString(2, userId);
            });
        } else {
            {
                String QUERY = "DELETE FROM " + getConfig(start).getUsersTable()
                        + " WHERE app_id = ? AND user_id = ?";
                update(sqlCon, QUERY, pst -> {
                    pst.setString(1, appIdentifier.getAppId());
                    pst.setString(2, userId);
                });
            }

            {
                String QUERY = "DELETE FROM " + getConfig(start).getEmailPasswordUsersTable()
                        + " WHERE app_id = ? AND user_id = ?";
                update(sqlCon, QUERY, pst -> {
                    pst.setString(1, appIdentifier.getAppId());
                    pst.setString(2, userId);
                });
            }

            {
                String QUERY = "DELETE FROM " + getConfig(start).getPasswordResetTokensTable()
                        + " WHERE app_id = ? AND user_id = ?";
                update(sqlCon, QUERY, pst -> {
                    pst.setString(1, appIdentifier.getAppId());
                    pst.setString(2, userId);
                });
            }
        }
    }

    private static UserInfoPartial getUserInfoUsingId_Transaction(Start start, Connection sqlCon,
                                                                  AppIdentifier appIdentifier,
                                                                  String id)
            throws SQLException, StorageQueryException {
        // we don't need a FOR UPDATE here because this is already part of a transaction, and locked on
        // app_id_to_user_id table
        String QUERY = "SELECT user_id, email, password_hash, time_joined FROM "
                + getConfig(start).getEmailPasswordUsersTable() + " WHERE app_id = ? AND user_id = ?";

        return execute(sqlCon, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, id);
        }, result -> {
            if (result.next()) {
                return UserInfoRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static List<LoginMethod> getUsersInfoUsingIdList(Start start, Set<String> ids,
                                                            AppIdentifier appIdentifier)
            throws SQLException, StorageQueryException {
        if (ids.size() > 0) {
            // No need to filter based on tenantId because the id list is already filtered for a tenant
            String QUERY = "SELECT user_id, email,  password_hash, time_joined "
                    + "FROM " + getConfig(start).getEmailPasswordUsersTable()
                    + " WHERE user_id IN (" + Utils.generateCommaSeperatedQuestionMarks(ids.size()) +
                    " ) AND app_id = ?";

            List<UserInfoPartial> userInfos = execute(start, QUERY, pst -> {
                int index = 1;
                for (String id : ids) {
                    pst.setString(index, id);
                    index++;
                }
                pst.setString(index, appIdentifier.getAppId());
            }, result -> {
                List<UserInfoPartial> finalResult = new ArrayList<>();
                while (result.next()) {
                    finalResult.add(UserInfoRowMapper.getInstance().mapOrThrow(result));
                }
                return finalResult;
            });
            fillUserInfoWithTenantIds(start, appIdentifier, userInfos);
            fillUserInfoWithVerified(start, appIdentifier, userInfos);
            return userInfos.stream().map(UserInfoPartial::toLoginMethod)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public static List<LoginMethod> getUsersInfoUsingIdList_Transaction(Start start, Connection con, Set<String> ids,
                                                                        AppIdentifier appIdentifier)
            throws SQLException, StorageQueryException {
        if (ids.size() > 0) {
            // No need to filter based on tenantId because the id list is already filtered for a tenant
            String QUERY = "SELECT user_id, email,  password_hash, time_joined "
                    + "FROM " + getConfig(start).getEmailPasswordUsersTable()
                    + " WHERE user_id IN (" + Utils.generateCommaSeperatedQuestionMarks(ids.size()) +
                    " ) AND app_id = ?";

            List<UserInfoPartial> userInfos = execute(con, QUERY, pst -> {
                int index = 1;
                for (String id : ids) {
                    pst.setString(index, id);
                    index++;
                }
                pst.setString(index, appIdentifier.getAppId());
            }, result -> {
                List<UserInfoPartial> finalResult = new ArrayList<>();
                while (result.next()) {
                    finalResult.add(UserInfoRowMapper.getInstance().mapOrThrow(result));
                }
                return finalResult;
            });
            fillUserInfoWithTenantIds_transaction(start, con, appIdentifier, userInfos);
            fillUserInfoWithVerified_transaction(start, con, appIdentifier, userInfos);
            return userInfos.stream().map(UserInfoPartial::toLoginMethod)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public static String lockEmail_Transaction(Start start, Connection con,
                                               AppIdentifier appIdentifier,
                                               String email)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT user_id FROM " + getConfig(start).getEmailPasswordUsersTable() +
                " WHERE app_id = ? AND email = ?";
        return execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, email);
        }, result -> {
            if (result.next()) {
                return result.getString("user_id");
            }
            return null;
        });
    }

    public static String getPrimaryUserIdUsingEmail(Start start, TenantIdentifier tenantIdentifier,
                                                    String email)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT DISTINCT all_users.primary_or_recipe_user_id AS user_id "
                + "FROM " + getConfig(start).getEmailPasswordUserToTenantTable() + " AS ep" +
                " JOIN " + getConfig(start).getUsersTable() + " AS all_users" +
                " ON ep.app_id = all_users.app_id AND ep.user_id = all_users.user_id" +
                " WHERE ep.app_id = ? AND ep.tenant_id = ? AND ep.email = ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, email);
        }, result -> {
            if (result.next()) {
                return result.getString("user_id");
            }
            return null;
        });
    }

    public static List<String> getPrimaryUserIdsUsingEmail_Transaction(Start start, Connection con,
                                                                       AppIdentifier appIdentifier,
                                                                       String email)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT DISTINCT all_users.primary_or_recipe_user_id AS user_id "
                + "FROM " + getConfig(start).getEmailPasswordUsersTable() + " AS ep" +
                " JOIN " + getConfig(start).getAppIdToUserIdTable() + " AS all_users" +
                " ON ep.app_id = all_users.app_id AND ep.user_id = all_users.user_id" +
                " WHERE ep.app_id = ? AND ep.email = ?";

        return execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, email);
        }, result -> {
            List<String> userIds = new ArrayList<>();
            while (result.next()) {
                userIds.add(result.getString("user_id"));
            }
            return userIds;
        });
    }

    public static boolean addUserIdToTenant_Transaction(Start start, Connection sqlCon,
                                                        TenantIdentifier tenantIdentifier, String userId)
            throws SQLException, StorageQueryException, UnknownUserIdException {
        UserInfoPartial userInfo = EmailPasswordQueries.getUserInfoUsingId_Transaction(start, sqlCon,
                tenantIdentifier.toAppIdentifier(), userId);

        if (userInfo == null) {
            throw new UnknownUserIdException();
        }

        GeneralQueries.AccountLinkingInfo accountLinkingInfo = GeneralQueries.getAccountLinkingInfo_Transaction(start,
                sqlCon, tenantIdentifier.toAppIdentifier(), userId);

        { // all_auth_recipe_users
            String QUERY = "INSERT INTO " + getConfig(start).getUsersTable()
                    +
                    "(app_id, tenant_id, user_id, primary_or_recipe_user_id, is_linked_or_is_a_primary_user, " +
                    "recipe_id, time_joined, primary_or_recipe_user_time_joined)"
                    + " VALUES(?, ?, ?, ?, ?, ?, ?, ?)" + " ON CONFLICT DO NOTHING";
            GeneralQueries.AccountLinkingInfo finalAccountLinkingInfo = accountLinkingInfo;

            update(sqlCon, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, userId);
                pst.setString(4, finalAccountLinkingInfo.primaryUserId);
                pst.setBoolean(5, finalAccountLinkingInfo.isLinked);
                pst.setString(6, EMAIL_PASSWORD.toString());
                pst.setLong(7, userInfo.timeJoined);
                pst.setLong(8, userInfo.timeJoined);
            });

            GeneralQueries.updateTimeJoinedForPrimaryUser_Transaction(start, sqlCon, tenantIdentifier.toAppIdentifier(),
                    finalAccountLinkingInfo.primaryUserId);
        }

        { // emailpassword_user_to_tenant
            String QUERY = "INSERT INTO " + getConfig(start).getEmailPasswordUserToTenantTable()
                    + "(app_id, tenant_id, user_id, email)"
                    + " VALUES(?, ?, ?, ?) " + " ON CONFLICT (app_id, tenant_id, user_id) DO NOTHING";

            int numRows = update(sqlCon, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, userId);
                pst.setString(4, userInfo.email);
            });

            return numRows > 0;
        }
    }

    public static boolean removeUserIdFromTenant_Transaction(Start start, Connection sqlCon,
                                                             TenantIdentifier tenantIdentifier, String userId)
            throws SQLException, StorageQueryException {
        { // all_auth_recipe_users
            String QUERY = "DELETE FROM " + getConfig(start).getUsersTable()
                    + " WHERE app_id = ? AND tenant_id = ? and user_id = ? and recipe_id = ?";
            int numRows = update(sqlCon, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, userId);
                pst.setString(4, EMAIL_PASSWORD.toString());
            });
            return numRows > 0;
        }
        // automatically deleted from emailpassword_user_to_tenant because of foreign key constraint
    }

    private static UserInfoPartial fillUserInfoWithVerified_transaction(Start start, Connection sqlCon,
                                                                        AppIdentifier appIdentifier,
                                                                        UserInfoPartial userInfo)
            throws SQLException, StorageQueryException {
        if (userInfo == null) return null;
        return fillUserInfoWithVerified_transaction(start, sqlCon, appIdentifier, List.of(userInfo)).get(0);
    }

    private static List<UserInfoPartial> fillUserInfoWithVerified_transaction(Start start, Connection sqlCon,
                                                                              AppIdentifier appIdentifier,
                                                                              List<UserInfoPartial> userInfos)
            throws SQLException, StorageQueryException {
        List<EmailVerificationQueries.UserIdAndEmail> userIdsAndEmails = new ArrayList<>();
        for (UserInfoPartial userInfo : userInfos) {
            userIdsAndEmails.add(new EmailVerificationQueries.UserIdAndEmail(userInfo.id, userInfo.email));
        }
        List<String> userIdsThatAreVerified = EmailVerificationQueries.isEmailVerified_transaction(start, sqlCon,
                appIdentifier,
                userIdsAndEmails);
        Set<String> verifiedUserIdsSet = new HashSet<>(userIdsThatAreVerified);
        for (UserInfoPartial userInfo : userInfos) {
            if (verifiedUserIdsSet.contains(userInfo.id)) {
                userInfo.verified = true;
            } else {
                userInfo.verified = false;
            }
        }
        return userInfos;
    }

    private static List<UserInfoPartial> fillUserInfoWithVerified(Start start,
                                                                  AppIdentifier appIdentifier,
                                                                  List<UserInfoPartial> userInfos)
            throws SQLException, StorageQueryException {
        List<EmailVerificationQueries.UserIdAndEmail> userIdsAndEmails = new ArrayList<>();
        for (UserInfoPartial userInfo : userInfos) {
            userIdsAndEmails.add(new EmailVerificationQueries.UserIdAndEmail(userInfo.id, userInfo.email));
        }
        List<String> userIdsThatAreVerified = EmailVerificationQueries.isEmailVerified(start,
                appIdentifier,
                userIdsAndEmails);
        Set<String> verifiedUserIdsSet = new HashSet<>(userIdsThatAreVerified);
        for (UserInfoPartial userInfo : userInfos) {
            if (verifiedUserIdsSet.contains(userInfo.id)) {
                userInfo.verified = true;
            } else {
                userInfo.verified = false;
            }
        }
        return userInfos;
    }

    private static UserInfoPartial fillUserInfoWithTenantIds_transaction(Start start, Connection sqlCon,
                                                                         AppIdentifier appIdentifier,
                                                                         UserInfoPartial userInfo)
            throws SQLException, StorageQueryException {
        if (userInfo == null) return null;
        return fillUserInfoWithTenantIds_transaction(start, sqlCon, appIdentifier, Arrays.asList(userInfo)).get(0);
    }

    private static List<UserInfoPartial> fillUserInfoWithTenantIds_transaction(Start start, Connection sqlCon,
                                                                               AppIdentifier appIdentifier,
                                                                               List<UserInfoPartial> userInfos)
            throws SQLException, StorageQueryException {
        String[] userIds = new String[userInfos.size()];
        for (int i = 0; i < userInfos.size(); i++) {
            userIds[i] = userInfos.get(i).id;
        }

        Map<String, List<String>> tenantIdsForUserIds = GeneralQueries.getTenantIdsForUserIds_transaction(start, sqlCon,
                appIdentifier,
                userIds);
        List<AuthRecipeUserInfo> result = new ArrayList<>();
        for (UserInfoPartial userInfo : userInfos) {
            userInfo.tenantIds = tenantIdsForUserIds.get(userInfo.id).toArray(new String[0]);
        }

        return userInfos;
    }

    private static List<UserInfoPartial> fillUserInfoWithTenantIds(Start start,
                                                                   AppIdentifier appIdentifier,
                                                                   List<UserInfoPartial> userInfos)
            throws SQLException, StorageQueryException {
        String[] userIds = new String[userInfos.size()];
        for (int i = 0; i < userInfos.size(); i++) {
            userIds[i] = userInfos.get(i).id;
        }

        Map<String, List<String>> tenantIdsForUserIds = GeneralQueries.getTenantIdsForUserIds(start,
                appIdentifier,
                userIds);
        for (UserInfoPartial userInfo : userInfos) {
            userInfo.tenantIds = tenantIdsForUserIds.get(userInfo.id).toArray(new String[0]);
        }

        return userInfos;
    }

    private static class UserInfoPartial {
        public final String id;
        public final long timeJoined;
        public final String email;
        public final String passwordHash;
        public String[] tenantIds;
        public Boolean verified;
        public Boolean isPrimary;

        public UserInfoPartial(String id, String email, String passwordHash, long timeJoined) {
            this.id = id.trim();
            this.timeJoined = timeJoined;
            this.email = email;
            this.passwordHash = passwordHash;
        }

        public LoginMethod toLoginMethod() {
            assert (tenantIds != null);
            assert (verified != null);
            return new LoginMethod(id, timeJoined, verified, email,
                    passwordHash, tenantIds);
        }
    }

    private static class PasswordResetRowMapper implements RowMapper<PasswordResetTokenInfo, ResultSet> {
        public static final PasswordResetRowMapper INSTANCE = new PasswordResetRowMapper();

        private PasswordResetRowMapper() {
        }

        private static PasswordResetRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public PasswordResetTokenInfo map(ResultSet result) throws StorageQueryException {
            try {
                return new PasswordResetTokenInfo(result.getString("user_id"), result.getString("token"),
                        result.getLong("token_expiry"), result.getString("email"));
            } catch (Exception e) {
                throw new StorageQueryException(e);
            }
        }
    }

    private static class UserInfoRowMapper implements RowMapper<UserInfoPartial, ResultSet> {
        static final UserInfoRowMapper INSTANCE = new UserInfoRowMapper();

        private UserInfoRowMapper() {
        }

        private static UserInfoRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public UserInfoPartial map(ResultSet result) throws Exception {
            return new UserInfoPartial(result.getString("user_id"), result.getString("email"),
                    result.getString("password_hash"), result.getLong("time_joined"));
        }
    }
}
