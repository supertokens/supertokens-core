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

import io.supertokens.inmemorydb.ConnectionPool;
import io.supertokens.inmemorydb.ConnectionWithLocks;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.Utils;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
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
import static io.supertokens.pluginInterface.RECIPE_ID.THIRD_PARTY;

public class ThirdPartyQueries {

    static String getQueryToCreateUsersTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getThirdPartyUsersTable() + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "third_party_id VARCHAR(28) NOT NULL,"
                + "third_party_user_id VARCHAR(256) NOT NULL,"
                + "user_id CHAR(36) NOT NULL,"
                + "email VARCHAR(256) NOT NULL,"
                + "time_joined BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY (app_id, user_id),"
                + "FOREIGN KEY (app_id, user_id) REFERENCES " + Config.getConfig(start).getAppIdToUserIdTable()
                + " (app_id, user_id) ON DELETE CASCADE"
                + ");";
    }

    public static String getQueryToThirdPartyUserEmailIndex(Start start) {
        return "CREATE INDEX IF NOT EXISTS thirdparty_users_email_index ON "
                + Config.getConfig(start).getThirdPartyUsersTable() + " (app_id, email);";
    }

    public static String getQueryToThirdPartyUserIdIndex(Start start) {
        return "CREATE INDEX IF NOT EXISTS thirdparty_users_thirdparty_user_id_index ON "
                + Config.getConfig(start).getThirdPartyUsersTable() + " (app_id, third_party_id, third_party_user_id);";
    }

    static String getQueryToCreateThirdPartyUserToTenantTable(Start start) {
        String thirdPartyUserToTenantTable = Config.getConfig(start).getThirdPartyUserToTenantTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + thirdPartyUserToTenantTable + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "tenant_id VARCHAR(64) DEFAULT 'public',"
                + "user_id CHAR(36) NOT NULL,"
                + "third_party_id VARCHAR(28) NOT NULL,"
                + "third_party_user_id VARCHAR(256) NOT NULL,"
                + "UNIQUE (app_id, tenant_id, third_party_id, third_party_user_id),"
                + "PRIMARY KEY (app_id, tenant_id, user_id),"
                + "FOREIGN KEY (app_id, tenant_id, user_id)"
                + " REFERENCES " + Config.getConfig(start).getUsersTable() +
                "(app_id, tenant_id, user_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    public static AuthRecipeUserInfo signUp(Start start, TenantIdentifier tenantIdentifier, String id, String email,
                                            LoginMethod.ThirdParty thirdParty, long timeJoined)
            throws StorageQueryException, StorageTransactionLogicException {
        return start.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try {
                { // app_id_to_user_id
                    String QUERY = "INSERT INTO " + getConfig(start).getAppIdToUserIdTable()
                            + "(app_id, user_id, primary_or_recipe_user_id, recipe_id)" + " VALUES(?, ?, ?, ?)";
                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, tenantIdentifier.getAppId());
                        pst.setString(2, id);
                        pst.setString(3, id);
                        pst.setString(4, THIRD_PARTY.toString());
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
                        pst.setString(3, id);
                        pst.setString(4, id);
                        pst.setString(5, THIRD_PARTY.toString());
                        pst.setLong(6, timeJoined);
                        pst.setLong(7, timeJoined);
                    });
                }

                { // thirdparty_users
                    String QUERY = "INSERT INTO " + getConfig(start).getThirdPartyUsersTable()
                            + "(app_id, third_party_id, third_party_user_id, user_id, email, time_joined)"
                            + " VALUES(?, ?, ?, ?, ?, ?)";
                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, tenantIdentifier.getAppId());
                        pst.setString(2, thirdParty.id);
                        pst.setString(3, thirdParty.userId);
                        pst.setString(4, id);
                        pst.setString(5, email);
                        pst.setLong(6, timeJoined);
                    });
                }

                { // thirdparty_user_to_tenant
                    String QUERY = "INSERT INTO " + getConfig(start).getThirdPartyUserToTenantTable()
                            + "(app_id, tenant_id, user_id, third_party_id, third_party_user_id)"
                            + " VALUES(?, ?, ?, ?, ?)";
                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, tenantIdentifier.getAppId());
                        pst.setString(2, tenantIdentifier.getTenantId());
                        pst.setString(3, id);
                        pst.setString(4, thirdParty.id);
                        pst.setString(5, thirdParty.userId);
                    });
                }

                UserInfoPartial userInfo = new UserInfoPartial(id, email, thirdParty, timeJoined);
                fillUserInfoWithTenantIds_transaction(start, sqlCon, tenantIdentifier.toAppIdentifier(), userInfo);
                fillUserInfoWithVerified_transaction(start, sqlCon, tenantIdentifier.toAppIdentifier(), userInfo);
                sqlCon.commit();
                return AuthRecipeUserInfo.create(id, false, userInfo.toLoginMethod());

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
                String QUERY = "DELETE FROM " + getConfig(start).getThirdPartyUsersTable()
                        + " WHERE app_id = ? AND user_id = ?";
                update(sqlCon, QUERY, pst -> {
                    pst.setString(1, appIdentifier.getAppId());
                    pst.setString(2, userId);
                });
            }
        }
    }

    public static List<String> lockEmail_Transaction(Start start, Connection con,
                                                     AppIdentifier appIdentifier,
                                                     String email) throws SQLException, StorageQueryException {
        // normally the query below will use a for update, but sqlite doesn't support it.
        ((ConnectionWithLocks) con).lock(
                appIdentifier.getAppId() + "~" + email +
                        Config.getConfig(start).getThirdPartyUsersTable());

        String QUERY = "SELECT tp.user_id as user_id "
                + "FROM " + getConfig(start).getThirdPartyUsersTable() + " AS tp" +
                " WHERE tp.app_id = ? AND tp.email = ?";

        return execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, email);
        }, result -> {
            List<String> finalResult = new ArrayList<>();
            while (result.next()) {
                finalResult.add(result.getString("user_id"));
            }
            return finalResult;
        });
    }

    public static List<String> lockThirdPartyInfo_Transaction(Start start, Connection con,
                                                              AppIdentifier appIdentifier,
                                                              String thirdPartyId, String thirdPartyUserId)
            throws SQLException, StorageQueryException {
        // normally the query below will use a for update, but sqlite doesn't support it.
        ((ConnectionWithLocks) con).lock(
                appIdentifier.getAppId() + "~" + thirdPartyId + thirdPartyUserId +
                        Config.getConfig(start).getThirdPartyUsersTable());

        // in psql / mysql dbs, this will lock the rows that are in both the tables that meet the ON criteria only.
        String QUERY = "SELECT user_id " +
                " FROM " + getConfig(start).getThirdPartyUsersTable() +
                " WHERE app_id = ? AND third_party_id = ? AND third_party_user_id = ?";

        return execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, thirdPartyId);
            pst.setString(3, thirdPartyUserId);
        }, result -> {
            List<String> finalResult = new ArrayList<>();
            while (result.next()) {
                finalResult.add(result.getString("user_id"));
            }
            return finalResult;
        });
    }

    public static List<LoginMethod> getUsersInfoUsingIdList(Start start, Set<String> ids,
                                                            AppIdentifier appIdentifier)
            throws SQLException, StorageQueryException {
        if (ids.size() > 0) {
            String QUERY = "SELECT user_id, third_party_id, third_party_user_id, email, time_joined "
                    + "FROM " + getConfig(start).getThirdPartyUsersTable() + " WHERE user_id IN (" +
                    Utils.generateCommaSeperatedQuestionMarks(ids.size()) + ") AND app_id = ?";

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

            try (Connection con = ConnectionPool.getConnection(start)) {
                fillUserInfoWithTenantIds_transaction(start, con, appIdentifier, userInfos);
                fillUserInfoWithVerified_transaction(start, con, appIdentifier, userInfos);
            }
            return userInfos.stream().map(UserInfoPartial::toLoginMethod).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public static List<LoginMethod> getUsersInfoUsingIdList_Transaction(Start start, Connection con, Set<String> ids,
                                                                        AppIdentifier appIdentifier)
            throws SQLException, StorageQueryException {
        if (ids.size() > 0) {
            String QUERY = "SELECT user_id, third_party_id, third_party_user_id, email, time_joined "
                    + "FROM " + getConfig(start).getThirdPartyUsersTable() + " WHERE user_id IN (" +
                    Utils.generateCommaSeperatedQuestionMarks(ids.size()) + ") AND app_id = ?";

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
            return userInfos.stream().map(UserInfoPartial::toLoginMethod).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }


    public static List<String> listUserIdsByThirdPartyInfo(Start start, AppIdentifier appIdentifier,
                                                           String thirdPartyId, String thirdPartyUserId)
            throws SQLException, StorageQueryException {

        String QUERY = "SELECT DISTINCT all_users.primary_or_recipe_user_id AS user_id "
                + "FROM " + getConfig(start).getThirdPartyUsersTable() + " AS tp" +
                " JOIN " + getConfig(start).getUsersTable() + " AS all_users" +
                " ON tp.app_id = all_users.app_id AND tp.user_id = all_users.user_id" +
                " WHERE tp.app_id = ? AND tp.third_party_id = ? AND tp.third_party_user_id = ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, thirdPartyId);
            pst.setString(3, thirdPartyUserId);
        }, result -> {
            List<String> userIds = new ArrayList<>();
            while (result.next()) {
                userIds.add(result.getString("user_id"));
            }
            return userIds;
        });
    }

    public static List<String> listUserIdsByThirdPartyInfo_Transaction(Start start, Connection con,
                                                                       AppIdentifier appIdentifier,
                                                                       String thirdPartyId, String thirdPartyUserId)
            throws SQLException, StorageQueryException {

        String QUERY = "SELECT DISTINCT all_users.primary_or_recipe_user_id AS user_id "
                + "FROM " + getConfig(start).getThirdPartyUsersTable() + " AS tp" +
                " JOIN " + getConfig(start).getUsersTable() + " AS all_users" +
                " ON tp.app_id = all_users.app_id AND tp.user_id = all_users.user_id" +
                " WHERE tp.app_id = ? AND tp.third_party_id = ? AND tp.third_party_user_id = ?";

        return execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, thirdPartyId);
            pst.setString(3, thirdPartyUserId);
        }, result -> {
            List<String> userIds = new ArrayList<>();
            while (result.next()) {
                userIds.add(result.getString("user_id"));
            }
            return userIds;
        });
    }

    public static String getUserIdByThirdPartyInfo(Start start, TenantIdentifier tenantIdentifier,
                                                   String thirdPartyId, String thirdPartyUserId)
            throws SQLException, StorageQueryException {

        String QUERY = "SELECT DISTINCT all_users.primary_or_recipe_user_id AS user_id "
                + "FROM " + getConfig(start).getThirdPartyUserToTenantTable() + " AS tp" +
                " JOIN " + getConfig(start).getUsersTable() + " AS all_users" +
                " ON tp.app_id = all_users.app_id AND tp.user_id = all_users.user_id" +
                " WHERE tp.app_id = ? AND tp.tenant_id = ? AND tp.third_party_id = ? AND tp.third_party_user_id = ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, thirdPartyId);
            pst.setString(4, thirdPartyUserId);
        }, result -> {
            if (result.next()) {
                return result.getString("user_id");
            }
            return null;
        });
    }

    public static void updateUserEmail_Transaction(Start start, Connection con, AppIdentifier appIdentifier,
                                                   String thirdPartyId, String thirdPartyUserId, String newEmail)
            throws SQLException, StorageQueryException {
        String QUERY = "UPDATE " + getConfig(start).getThirdPartyUsersTable()
                + " SET email = ? WHERE app_id = ? AND third_party_id = ? AND third_party_user_id = ?";

        update(con, QUERY, pst -> {
            pst.setString(1, newEmail);
            pst.setString(2, appIdentifier.getAppId());
            pst.setString(3, thirdPartyId);
            pst.setString(4, thirdPartyUserId);
        });
    }

    private static UserInfoPartial getUserInfoUsingUserId_Transaction(Start start, Connection con,
                                                                      AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {

        // we don't need a LOCK here because this is already part of a transaction, and locked on app_id_to_user_id
        // table
        String QUERY = "SELECT user_id, third_party_id, third_party_user_id, email, time_joined FROM "
                + getConfig(start).getThirdPartyUsersTable()
                + " WHERE app_id = ?  AND user_id = ?";
        return execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, result -> {
            if (result.next()) {
                return UserInfoRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
    }

    public static List<String> getPrimaryUserIdUsingEmail(Start start,
                                                          TenantIdentifier tenantIdentifier, String email)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT DISTINCT all_users.primary_or_recipe_user_id AS user_id "
                + "FROM " + getConfig(start).getThirdPartyUsersTable() + " AS tp" +
                " JOIN " + getConfig(start).getUsersTable() + " AS all_users" +
                " ON tp.app_id = all_users.app_id AND tp.user_id = all_users.user_id" +
                " JOIN " + getConfig(start).getThirdPartyUserToTenantTable() + " AS tp_tenants" +
                " ON tp_tenants.app_id = all_users.app_id AND tp_tenants.user_id = all_users.user_id" +
                " WHERE tp.app_id = ? AND tp_tenants.tenant_id = ? AND tp.email = ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, email);
        }, result -> {
            List<String> finalResult = new ArrayList<>();
            while (result.next()) {
                finalResult.add(result.getString("user_id"));
            }
            return finalResult;
        });
    }

    public static List<String> getPrimaryUserIdUsingEmail_Transaction(Start start, Connection con,
                                                                      AppIdentifier appIdentifier, String email)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT DISTINCT all_users.primary_or_recipe_user_id AS user_id "
                + "FROM " + getConfig(start).getThirdPartyUsersTable() + " AS tp" +
                " JOIN " + getConfig(start).getAppIdToUserIdTable() + " AS all_users" +
                " ON tp.app_id = all_users.app_id AND tp.user_id = all_users.user_id" +
                " WHERE tp.app_id = ? AND tp.email = ?";

        return execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, email);
        }, result -> {
            List<String> finalResult = new ArrayList<>();
            while (result.next()) {
                finalResult.add(result.getString("user_id"));
            }
            return finalResult;
        });
    }

    public static boolean addUserIdToTenant_Transaction(Start start, Connection sqlCon,
                                                        TenantIdentifier tenantIdentifier, String userId)
            throws SQLException, StorageQueryException, UnknownUserIdException {
        UserInfoPartial userInfo = ThirdPartyQueries.getUserInfoUsingUserId_Transaction(start, sqlCon,
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
            update(sqlCon, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, userInfo.id);
                pst.setString(4, accountLinkingInfo.primaryUserId);
                pst.setBoolean(5, accountLinkingInfo.isLinked);
                pst.setString(6, THIRD_PARTY.toString());
                pst.setLong(7, userInfo.timeJoined);
                pst.setLong(8, userInfo.timeJoined);
            });

            GeneralQueries.updateTimeJoinedForPrimaryUser_Transaction(start, sqlCon, tenantIdentifier.toAppIdentifier(),
                    accountLinkingInfo.primaryUserId);
        }

        { // thirdparty_user_to_tenant
            String QUERY = "INSERT INTO " + getConfig(start).getThirdPartyUserToTenantTable()
                    + "(app_id, tenant_id, user_id, third_party_id, third_party_user_id)"
                    + " VALUES(?, ?, ?, ?, ?)" + " ON CONFLICT (app_id, tenant_id, user_id) DO NOTHING";
            int numRows = update(sqlCon, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, userInfo.id);
                pst.setString(4, userInfo.thirdParty.id);
                pst.setString(5, userInfo.thirdParty.userId);
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
                pst.setString(4, THIRD_PARTY.toString());
            });

            return numRows > 0;
        }

        // automatically deleted from thirdparty_user_to_tenant because of foreign key constraint
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

    private static UserInfoPartial fillUserInfoWithTenantIds_transaction(Start start, Connection sqlCon,
                                                                         AppIdentifier appIdentifier,
                                                                         UserInfoPartial userInfo)
            throws SQLException, StorageQueryException {
        if (userInfo == null) return null;
        return fillUserInfoWithTenantIds_transaction(start, sqlCon, appIdentifier, List.of(userInfo)).get(0);
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
        for (UserInfoPartial userInfo : userInfos) {
            userInfo.tenantIds = tenantIdsForUserIds.get(userInfo.id).toArray(new String[0]);
        }
        return userInfos;
    }

    private static class UserInfoPartial {
        public final String id;
        public final String email;
        public final LoginMethod.ThirdParty thirdParty;
        public final long timeJoined;
        public String[] tenantIds;
        public Boolean verified;
        public Boolean isPrimary;

        public UserInfoPartial(String id, String email, LoginMethod.ThirdParty thirdParty, long timeJoined) {
            this.id = id.trim();
            this.email = email;
            this.thirdParty = thirdParty;
            this.timeJoined = timeJoined;
        }

        public LoginMethod toLoginMethod() {
            assert (tenantIds != null);
            assert (verified != null);
            return new LoginMethod(id, timeJoined, verified, email,
                    new LoginMethod.ThirdParty(thirdParty.id, thirdParty.userId), tenantIds);
        }
    }

    private static class UserInfoRowMapper implements RowMapper<UserInfoPartial, ResultSet> {
        private static final UserInfoRowMapper INSTANCE = new UserInfoRowMapper();

        private UserInfoRowMapper() {
        }

        private static UserInfoRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public UserInfoPartial map(ResultSet result) throws Exception {
            return new UserInfoPartial(result.getString("user_id"), result.getString("email"),
                    new LoginMethod.ThirdParty(result.getString("third_party_id"),
                            result.getString("third_party_user_id")),
                    result.getLong("time_joined"));
        }
    }
}
