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
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.thirdparty.UserInfo;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

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
                + " REFERENCES " + Config.getConfig(start).getUsersTable() + "(app_id, tenant_id, user_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    public static UserInfo signUp(Start start, TenantIdentifier tenantIdentifier, String id, String email, UserInfo.ThirdParty thirdParty, long timeJoined)
            throws StorageQueryException, StorageTransactionLogicException {
        return start.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try {
                { // app_id_to_user_id
                    String QUERY = "INSERT INTO " + getConfig(start).getAppIdToUserIdTable()
                            + "(app_id, user_id, recipe_id)" + " VALUES(?, ?, ?)";
                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, tenantIdentifier.getAppId());
                        pst.setString(2, id);
                        pst.setString(3, THIRD_PARTY.toString());
                    });
                }

                { // all_auth_recipe_users
                    String QUERY = "INSERT INTO " + getConfig(start).getUsersTable()
                            + "(app_id, tenant_id, user_id, recipe_id, time_joined)" + " VALUES(?, ?, ?, ?, ?)";
                    update(sqlCon, QUERY, pst -> {
                        pst.setString(1, tenantIdentifier.getAppId());
                        pst.setString(2, tenantIdentifier.getTenantId());
                        pst.setString(3, id);
                        pst.setString(4, THIRD_PARTY.toString());
                        pst.setLong(5, timeJoined);
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

                UserInfo userInfo = userInfoWithTenantIds_transaction(start, sqlCon, tenantIdentifier.toAppIdentifier(), new UserInfoPartial(id, email, thirdParty, timeJoined));
                sqlCon.commit();
                return userInfo;

            } catch (SQLException throwables) {
                throw new StorageTransactionLogicException(throwables);
            }
        });
    }

    public static void deleteUser(Start start, AppIdentifier appIdentifier, String userId)
            throws StorageQueryException, StorageTransactionLogicException {
        start.startTransaction(con -> {
            Connection sqlCon = (Connection) con.getConnection();
            try {
                {
                    String QUERY = "DELETE FROM " + getConfig(start).getAppIdToUserIdTable()
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

    public static UserInfo getThirdPartyUserInfoUsingId(Start start, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT user_id, third_party_id, third_party_user_id, email, time_joined FROM "
                + getConfig(start).getThirdPartyUsersTable() + " WHERE app_id = ? AND user_id = ?";

        UserInfoPartial userInfo = execute(start, QUERY.toString(), pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, result -> {
            if (result.next()) {
                return UserInfoRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
        return userInfoWithTenantIds(start, appIdentifier, userInfo);
    }

    public static List<UserInfo> getUsersInfoUsingIdList(Start start, AppIdentifier appIdentifier, List<String> ids)
            throws SQLException, StorageQueryException {
        if (ids.size() > 0) {
            // No need to filter based on tenantId because the id list is already filtered for a tenant
            StringBuilder QUERY = new StringBuilder(
                    "SELECT user_id, third_party_id, third_party_user_id, email, time_joined "
                            + "FROM " + getConfig(start).getThirdPartyUsersTable());
            QUERY.append(" WHERE app_id = ? AND user_id IN (");
            for (int i = 0; i < ids.size(); i++) {

                QUERY.append("?");
                if (i != ids.size() - 1) {
                    // not the last element
                    QUERY.append(",");
                }
            }
            QUERY.append(")");

            List<UserInfoPartial> userInfos = execute(start, QUERY.toString(), pst -> {
                pst.setString(1, appIdentifier.getAppId());
                for (int i = 0; i < ids.size(); i++) {
                    // i+2 cause this starts with 1 and not 0, and 1 is appId
                    pst.setString(i + 2, ids.get(i));
                }
            }, result -> {
                List<UserInfoPartial> finalResult = new ArrayList<>();
                while (result.next()) {
                    finalResult.add(UserInfoRowMapper.getInstance().mapOrThrow(result));
                }
                return finalResult;
            });
            return userInfoWithTenantIds(start, appIdentifier, userInfos);
        }
        return Collections.emptyList();
    }

    public static UserInfo getThirdPartyUserInfoUsingId(Start start, TenantIdentifier tenantIdentifier,
                                                        String thirdPartyId, String thirdPartyUserId)
            throws SQLException, StorageQueryException {

        String QUERY = "SELECT tp_users.user_id as user_id, tp_users.third_party_id as third_party_id, "
                + "tp_users.third_party_user_id as third_party_user_id, tp_users.email as email, "
                + "tp_users.time_joined as time_joined "
                + "FROM " + getConfig(start).getThirdPartyUserToTenantTable() + " AS tp_users_to_tenant "
                + "JOIN " + getConfig(start).getThirdPartyUsersTable() + " AS tp_users "
                + "ON tp_users.app_id = tp_users_to_tenant.app_id AND tp_users.user_id = tp_users_to_tenant.user_id "
                + "WHERE tp_users_to_tenant.app_id = ? AND tp_users_to_tenant.tenant_id = ? "
                + "AND tp_users_to_tenant.third_party_id = ? AND tp_users_to_tenant.third_party_user_id = ?";

        UserInfoPartial userInfo = execute(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, thirdPartyId);
            pst.setString(4, thirdPartyUserId);
        }, result -> {
            if (result.next()) {
                return UserInfoRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
        return userInfoWithTenantIds(start, tenantIdentifier.toAppIdentifier(), userInfo);
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

    public static UserInfo getUserInfoUsingId_Transaction(Start start, Connection con,
                                                          AppIdentifier appIdentifier, String thirdPartyId,
                                                          String thirdPartyUserId)
            throws SQLException, StorageQueryException {

        ((ConnectionWithLocks) con).lock(appIdentifier.getAppId() + "~" + thirdPartyId + "~" + thirdPartyUserId + Config.getConfig(start).getThirdPartyUsersTable());

        String QUERY = "SELECT user_id, third_party_id, third_party_user_id, email, time_joined FROM "
                + getConfig(start).getThirdPartyUsersTable()
                + " WHERE app_id = ? AND third_party_id = ? AND third_party_user_id = ?";
        UserInfoPartial userInfo = execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, thirdPartyId);
            pst.setString(3, thirdPartyUserId);
        }, result -> {
            if (result.next()) {
                return UserInfoRowMapper.getInstance().mapOrThrow(result);
            }
            return null;
        });
        return userInfoWithTenantIds_transaction(start, con, appIdentifier, userInfo);
    }

    private static UserInfoPartial getUserInfoUsingUserId(Start start, Connection con,
                                                          AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {

        // we don't need a LOCK here because this is already part of a transaction, and locked on app_id_to_user_id table
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

    public static UserInfo[] getThirdPartyUsersByEmail(Start start, TenantIdentifier tenantIdentifier,
                                                       @NotNull String email)
            throws SQLException, StorageQueryException {

        String QUERY = "SELECT tp_users.user_id as user_id, tp_users.third_party_id as third_party_id, "
                + "tp_users.third_party_user_id as third_party_user_id, tp_users.email as email, "
                + "tp_users.time_joined as time_joined "
                + "FROM " + getConfig(start).getThirdPartyUsersTable() + " AS tp_users "
                + "JOIN " + getConfig(start).getThirdPartyUserToTenantTable() + " AS tp_users_to_tenant "
                + "ON tp_users.app_id = tp_users_to_tenant.app_id AND tp_users.user_id = tp_users_to_tenant.user_id "
                + "WHERE tp_users_to_tenant.app_id = ? AND tp_users_to_tenant.tenant_id = ? AND tp_users.email = ? "
                + "ORDER BY time_joined";

        List<UserInfoPartial> userInfos = execute(start, QUERY.toString(), pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, email);
        }, result -> {
            List<UserInfoPartial> finalResult = new ArrayList<>();
            while (result.next()) {
                finalResult.add(UserInfoRowMapper.getInstance().mapOrThrow(result));
            }
            return finalResult;
        });
        return userInfoWithTenantIds(start, tenantIdentifier.toAppIdentifier(), userInfos).toArray(new UserInfo[0]);
    }

    public static boolean addUserIdToTenant_Transaction(Start start, Connection sqlCon, TenantIdentifier tenantIdentifier, String userId)
            throws SQLException, StorageQueryException {
        UserInfoPartial userInfo = ThirdPartyQueries.getUserInfoUsingUserId(start, sqlCon,
                tenantIdentifier.toAppIdentifier(), userId);

        { // all_auth_recipe_users
            String QUERY = "INSERT INTO " + getConfig(start).getUsersTable()
                    + "(app_id, tenant_id, user_id, recipe_id, time_joined)"
                    + " VALUES(?, ?, ?, ?, ?)" + " ON CONFLICT DO NOTHING";
            update(sqlCon, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, userInfo.id);
                pst.setString(4, THIRD_PARTY.toString());
                pst.setLong(5, userInfo.timeJoined);
            });
        }

        { // thirdparty_user_to_tenant
            String QUERY = "INSERT INTO " + getConfig(start).getThirdPartyUserToTenantTable()
                    + "(app_id, tenant_id, user_id, third_party_id, third_party_user_id)"
                    + " VALUES(?, ?, ?, ?, ?)" + " ON CONFLICT DO NOTHING";
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

    public static boolean removeUserIdFromTenant_Transaction(Start start, Connection sqlCon, TenantIdentifier tenantIdentifier, String userId)
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

    private static UserInfo userInfoWithTenantIds(Start start, AppIdentifier appIdentifier, UserInfoPartial userInfo)
            throws SQLException, StorageQueryException {
        if (userInfo == null) return null;
        try (Connection con = ConnectionPool.getConnection(start)) {
            return userInfoWithTenantIds_transaction(start, con, appIdentifier, Arrays.asList(userInfo)).get(0);
        }
    }

    private static List<UserInfo> userInfoWithTenantIds(Start start, AppIdentifier appIdentifier, List<UserInfoPartial> userInfos)
            throws SQLException, StorageQueryException {
        try (Connection con = ConnectionPool.getConnection(start)) {
            return userInfoWithTenantIds_transaction(start, con, appIdentifier, userInfos);
        }
    }

    private static UserInfo userInfoWithTenantIds_transaction(Start start, Connection sqlCon, AppIdentifier appIdentifier, UserInfoPartial userInfo)
            throws SQLException, StorageQueryException {
        if (userInfo == null) return null;
        return userInfoWithTenantIds_transaction(start, sqlCon, appIdentifier, Arrays.asList(userInfo)).get(0);
    }

    private static List<UserInfo> userInfoWithTenantIds_transaction(Start start, Connection sqlCon, AppIdentifier appIdentifier, List<UserInfoPartial> userInfos)
            throws SQLException, StorageQueryException {
        String[] userIds = new String[userInfos.size()];
        for (int i = 0; i < userInfos.size(); i++) {
            userIds[i] = userInfos.get(i).id;
        }

        Map<String, List<String>> tenantIdsForUserIds = GeneralQueries.getTenantIdsForUserIds_transaction(start, sqlCon, appIdentifier, userIds);
        List<UserInfo> result = new ArrayList<>();
        for (UserInfoPartial userInfo : userInfos) {
            result.add(new UserInfo(userInfo.id, userInfo.email, userInfo.thirdParty, userInfo.timeJoined,
                    tenantIdsForUserIds.get(userInfo.id).toArray(new String[0])));
        }

        return result;
    }

    private static class UserInfoPartial {
        public final String id;
        public final String email;
        public final UserInfo.ThirdParty thirdParty;
        public final long timeJoined;

        public UserInfoPartial(String id, String email, UserInfo.ThirdParty thirdParty, long timeJoined) {
            this.id = id.trim();
            this.email = email;
            this.thirdParty = thirdParty;
            this.timeJoined = timeJoined;
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
                    new UserInfo.ThirdParty(result.getString("third_party_id"),
                            result.getString("third_party_user_id")),
                    result.getLong("time_joined"));
        }
    }
}
