/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.inmemorydb.Utils;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.webauthn.WebAuthNOptions;
import io.supertokens.pluginInterface.webauthn.WebAuthNStoredCredential;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;
import static io.supertokens.inmemorydb.config.Config.getConfig;
import static io.supertokens.pluginInterface.RECIPE_ID.WEBAUTHN;

public class WebAuthNQueries {

    public static String getQueryToCreateWebAuthNUsersTable(Start start){
        return  "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getWebAuthNUsersTable() + "(" +
                " app_id VARCHAR(64) DEFAULT 'public' NOT NULL," +
                " user_id CHAR(36) NOT NULL," +
                " email VARCHAR(256) NOT NULL," +
                " rp_id VARCHAR(256) NOT NULL," +
                " time_joined BIGINT UNSIGNED NOT NULL," +
                " CONSTRAINT webauthn_users_pkey PRIMARY KEY (app_id, user_id), " +
                " CONSTRAINT webauthn_users_to_app_id_fkey " +
                " FOREIGN KEY (app_id, user_id) REFERENCES " + Config.getConfig(start).getAppIdToUserIdTable() +
                " (app_id, user_id) ON DELETE CASCADE " +
                ");";
    }

    public static String getQueryToCreateWebAuthNUsersToTenantTable(Start start){
        return  "CREATE TABLE IF NOT EXISTS  " + Config.getConfig(start).getWebAuthNUserToTenantTable() +" (" +
                " app_id VARCHAR(64) DEFAULT 'public' NOT NULL," +
                " tenant_id VARCHAR(64) DEFAULT 'public' NOT NULL," +
                " user_id CHAR(36) NOT NULL," +
                " email VARCHAR(256) NOT NULL," +
                " CONSTRAINT webauthn_user_to_tenant_email_key UNIQUE (app_id, tenant_id, email)," +
                " CONSTRAINT webauthn_user_to_tenant_pkey PRIMARY KEY (app_id, tenant_id, user_id)," +
                " CONSTRAINT webauthn_user_to_tenant_user_id_fkey FOREIGN KEY (app_id, tenant_id, user_id) " +
                " REFERENCES "+ Config.getConfig(start).getUsersTable()+" (app_id, tenant_id, user_id) on delete CASCADE" +
                ");";
    }

    public static String getQueryToCreateWebAuthNGeneratedOptionsTable(Start start){
        return  "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getWebAuthNGeneratedOptionsTable() + "(" +
                " app_id VARCHAR(64) DEFAULT 'public' NOT NULL," +
                " tenant_id VARCHAR(64) DEFAULT 'public' NOT NULL," +
                " id CHAR(36) NOT NULL," +
                " challenge VARCHAR(256) NOT NULL," +
                " email VARCHAR(256)," +
                " rp_id VARCHAR(256) NOT NULL," +
                " rp_name VARCHAR(256) NOT NULL," +
                " origin VARCHAR(256) NOT NULL," +
                " expires_at BIGINT UNSIGNED NOT NULL," +
                " created_at BIGINT UNSIGNED NOT NULL," +
                " CONSTRAINT webauthn_user_challenges_pkey PRIMARY KEY (app_id, tenant_id, id)," +
                " CONSTRAINT webauthn_user_challenges_tenant_id_fkey FOREIGN KEY (app_id, tenant_id) " +
                "  REFERENCES " + Config.getConfig(start).getTenantsTable() + " (app_id, tenant_id) ON DELETE CASCADE" +
                ");";
    }

    public static String getQueryToCreateWebAuthNChallengeExpiresIndex(Start start) {
        return  "CREATE INDEX webauthn_user_challenges_expires_at_index ON " +
                Config.getConfig(start).getWebAuthNGeneratedOptionsTable() +
                " (app_id, tenant_id, expires_at);";
    }

    public static String getQueryToCreateWebAuthNCredentialsTable(Start start){
        return  "CREATE TABLE IF NOT EXISTS "+ Config.getConfig(start).getWebAuthNCredentialsTable() + "(" +
                " id VARCHAR(256) NOT NULL," +
                " app_id VARCHAR(64) DEFAULT 'public'," +
                " rp_id VARCHAR(256)," +
                " user_id CHAR(36)," +
                " counter BIGINT NOT NULL," +
                " public_key BLOB NOT NULL," + //planned as bytea, which is not supported by sqlite
                " transports TEXT NOT NULL," + // planned as TEXT[], which is not supported by sqlite
                " created_at BIGINT NOT NULL," +
                " updated_at BIGINT NOT NULL," +
                " CONSTRAINT webauthn_user_credentials_pkey PRIMARY KEY (app_id, rp_id, id)," +
                " CONSTRAINT webauthn_user_credentials_webauthn_user_id_fkey FOREIGN KEY (app_id, user_id) REFERENCES " +
                Config.getConfig(start).getWebAuthNUsersTable() + " (app_id, user_id) ON DELETE CASCADE" +
                ");";
    }

    public static WebAuthNOptions saveOptions(Start start, TenantIdentifier tenantIdentifier, WebAuthNOptions options)
            throws SQLException, StorageQueryException {
        String INSERT = "INSERT INTO " + Config.getConfig(start).getWebAuthNGeneratedOptionsTable()
                + " (app_id, tenant_id, id, challenge, email, rp_id, origin, expires_at, created_at, rp_name) "
                + " VALUES (?,?,?,?,?,?,?,?,?, ?);";

        update(start, INSERT, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, options.generatedOptionsId);
            pst.setString(4, options.challenge);
            pst.setString(5, options.userEmail);
            pst.setString(6, options.relyingPartyId);
            pst.setString(7, options.origin);
            pst.setLong(8, options.expiresAt);
            pst.setLong(9, options.createdAt);
            pst.setString(10, options.relyingPartyName);
        });

        return options;
    }

    public static WebAuthNOptions loadOptionsById(Start start, TenantIdentifier tenantIdentifier, String optionsId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getWebAuthNGeneratedOptionsTable()
                + " WHERE app_id = ? AND tenant_id = ? and id = ?";
        return execute(start, QUERY, pst -> {
           pst.setString(1, tenantIdentifier.getAppId());
           pst.setString(2, tenantIdentifier.getTenantId());
           pst.setString(3, optionsId);
        }, result -> {
            if(result.next()){
                return WebAuthNOptionsRowMapper.getInstance().mapOrThrow(result); // we are expecting one or zero results
            }
            return null;
        });
    }

    public static WebAuthNStoredCredential loadCredential(Start start, TenantIdentifier tenantIdentifier, String credentialId)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT * FROM " + Config.getConfig(start).getWebAuthNCredentialsTable()
                + " WHERE app_id = ? AND id = ?";
        return execute(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, credentialId);
        }, result -> {
            if(result.next()){
                return WebAuthnStoredCredentialRowMapper.getInstance().mapOrThrow(result); // we are expecting one or zero results
            }
            return null;
        });
    }

    public static WebAuthNStoredCredential saveCredential(Start start, TenantIdentifier tenantIdentifier, WebAuthNStoredCredential credential)
            throws SQLException, StorageQueryException {
        String INSERT = "INSERT INTO " + Config.getConfig(start).getWebAuthNCredentialsTable()
                + " (id, app_id, rp_id, user_id, counter, public_key, transports, created_at, updated_at) "
                + " VALUES (?,?,?,?,?,?,?,?,?);";

        update(start, INSERT, pst -> {
            pst.setString(1, credential.id);
            pst.setString(2, credential.appId);
            pst.setString(3, credential.rpId);
            pst.setString(4, credential.userId);
            pst.setLong(5, credential.counter);
            pst.setBytes(6, credential.publicKey);
            pst.setString(7, credential.transports);
            pst.setLong(8, credential.createdAt);
            pst.setLong(9, credential.updatedAt);
        });

        return credential;
    }

    public static WebAuthNStoredCredential saveCredential_Transaction(Start start, Connection connection, TenantIdentifier tenantIdentifier, WebAuthNStoredCredential credential)
            throws SQLException, StorageQueryException {
        String INSERT = "INSERT INTO " + Config.getConfig(start).getWebAuthNCredentialsTable()
                + " (id, app_id, rp_id, user_id, counter, public_key, transports, created_at, updated_at) "
                + " VALUES (?,?,?,?,?,?,?,?,?);";

        update(connection, INSERT, pst -> {
            pst.setString(1, credential.id);
            pst.setString(2, credential.appId);
            pst.setString(3, credential.rpId);
            pst.setString(4, credential.userId);
            pst.setLong(5, credential.counter);
            pst.setBytes(6, credential.publicKey);
            pst.setString(7, credential.transports);
            pst.setLong(8, credential.createdAt);
            pst.setLong(9, credential.updatedAt);
        });

        return credential;
    }

    public static AuthRecipeUserInfo signUp_Transaction(Start start, Connection sqlCon, TenantIdentifier tenantIdentifier, String userId, String email,
                                            String relyingPartyId)
            throws StorageTransactionLogicException, StorageQueryException {
        long timeJoined = System.currentTimeMillis();

            try {
                // app_id_to_user_id
                String insertAppIdToUserId = "INSERT INTO " + getConfig(start).getAppIdToUserIdTable()
                        + "(app_id, user_id, primary_or_recipe_user_id, recipe_id)" + " VALUES(?, ?, ?, ?)";
                update(sqlCon, insertAppIdToUserId, pst -> {
                    pst.setString(1, tenantIdentifier.getAppId());
                    pst.setString(2, userId);
                    pst.setString(3, userId);
                    pst.setString(4, WEBAUTHN.toString());
                });

                // all_auth_recipe_users
                String insertAllAuthRecipeUsers = "INSERT INTO " + getConfig(start).getUsersTable()
                        +
                        "(app_id, tenant_id, user_id, primary_or_recipe_user_id, recipe_id, time_joined, " +
                        "primary_or_recipe_user_time_joined)" +
                        " VALUES(?, ?, ?, ?, ?, ?, ?)";
                update(sqlCon, insertAllAuthRecipeUsers, pst -> {
                    pst.setString(1, tenantIdentifier.getAppId());
                    pst.setString(2, tenantIdentifier.getTenantId());
                    pst.setString(3, userId);
                    pst.setString(4, userId);
                    pst.setString(5, WEBAUTHN.toString());
                    pst.setLong(6, timeJoined);
                    pst.setLong(7, timeJoined);
                });

                // webauthn_user_to_tenant
                String insertWebauthNUsersToTenant =
                        "INSERT INTO " + Config.getConfig(start).getWebAuthNUserToTenantTable()
                                + " (app_id, tenant_id, user_id, email) "
                                + " VALUES (?,?,?,?);";

                update(sqlCon, insertWebauthNUsersToTenant, pst -> {
                    pst.setString(1, tenantIdentifier.getAppId());
                    pst.setString(2, tenantIdentifier.getTenantId());
                    pst.setString(3, userId);
                    pst.setString(4, email);
                });

                // webauthn_users
                String insertWebauthNUsers = "INSERT INTO " + Config.getConfig(start).getWebAuthNUsersTable()
                        + " (app_id, user_id, email, rp_id, time_joined) "
                        + " VALUES (?,?,?,?,?);";

                update(sqlCon, insertWebauthNUsers, pst -> {
                    pst.setString(1, tenantIdentifier.getAppId());
                    pst.setString(2, userId);
                    pst.setString(3, email);
                    pst.setString(4, relyingPartyId);
                    pst.setLong(5, timeJoined);
                });

                sqlCon.commit();

                // TODO return AuthRecipeUserInfo.create(userId, false, );
                return null;
            } catch (SQLException throwables) {
                throw new StorageTransactionLogicException(throwables);
            }
    }

    public static String getPrimaryUserIdUsingEmail(Start start, TenantIdentifier tenantIdentifier, String email)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT DISTINCT all_users.primary_or_recipe_user_id AS user_id "
                + "FROM " + getConfig(start).getWebAuthNUserToTenantTable() + " AS ep" +
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

    public static Collection<? extends LoginMethod> getUsersInfoUsingIdList(Start start, Set<String> ids, AppIdentifier appIdentifier)
            throws SQLException, StorageQueryException {
        if (ids.size() > 0) {

            String webauthnUsersTable = getConfig(start).getWebAuthNUsersTable();
            String credentialTable = getConfig(start).getWebAuthNCredentialsTable();
            String usersTable = getConfig(start).getUsersTable();
            String userIdMappingTable = getConfig(start).getUserIdMappingTable();
            String emailVerificationTable = getConfig(start).getEmailVerificationTable();

            String queryAll = "SELECT webauthn.user_id as user_id, webauthn.email as email, webauthn.time_joined as time_joined, " +
                    "credentials.id as credential_id, email_verification.email_verified as email_verified, user_id_mapping.external_user_id as external_user_id," +
                    "all_users.tenant_id as tenant_id " +
                    "FROM " + webauthnUsersTable + " as webauthn " +
                    "JOIN " + credentialTable + " as credentials ON webauthn.user_id = credentials.user_id " +
                    "JOIN " + usersTable + " as all_users ON webauthn.app_id = all_users.app_id AND webauthn.user_id = all_users.user_id " +
                    "JOIN " + userIdMappingTable + " as user_id_mapping ON webauthn.user_id = user_id_mapping.supertokens_user_id " +
                    "JOIN " + emailVerificationTable + " as email_verification ON webauthn.app_id = email_verification.app_id AND user_id_mapping.external_user_id = email_verification.user_id " +
                    "WHERE webauthn.app_id = ? AND webauthn.user_id IN (" + Utils.generateCommaSeperatedQuestionMarks(ids.size()) + ")";

            return execute(start, queryAll, pst -> {
                pst.setString(1, appIdentifier.getAppId());
                int index = 2;
                for (String id : ids) {
                    pst.setString(index++, id);
                }
            }, result -> {
                Map<String, LoginMethod> users = new HashMap<>();
                while (result.next()) {
                    String userId = result.getString("user_id");
                    String email = result.getString("email");
                    long timeJoined = result.getLong("time_joined");
                    String credentialId = result.getString("credential_id");
                    boolean emailVerified = result.getBoolean("email_verified");
                    String externalUserId = result.getString("external_user_id");
                    String tenantId = result.getString("tenant_id");
                    if(users.containsKey(userId)) {
                        users.get(userId).webauthN.addCredentialId(credentialId);
                        users.get(userId).tenantIds.add(tenantId);
                    } else {
                        LoginMethod loginMethod = new LoginMethod(userId, timeJoined, emailVerified, email, new LoginMethod.WebAuthN(Collections.singletonList(credentialId)), new String[]{tenantId});
                        loginMethod.setExternalUserId(externalUserId);
                        users.put(userId, loginMethod);
                    }
                }
                return users.values();
            });

        }
        return Collections.emptyList();
    }

    private static class WebAuthnStoredCredentialRowMapper implements RowMapper<WebAuthNStoredCredential, ResultSet> {
        private static final WebAuthnStoredCredentialRowMapper INSTANCE = new WebAuthnStoredCredentialRowMapper();

        public static WebAuthnStoredCredentialRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public WebAuthNStoredCredential map(ResultSet rs) throws Exception {
            WebAuthNStoredCredential result = new WebAuthNStoredCredential();
            result.id = rs.getString("id");
            result.appId = rs.getString("app_id");
            result.rpId = rs.getString("rp_id");
            result.userId = rs.getString("user_id");
            result.counter = rs.getLong("counter");
            result.publicKey = rs.getBytes("public_key");
            result.transports = rs.getString("transports");
            result.createdAt = rs.getLong("created_at");
            result.updatedAt = rs.getLong("updated_at");
            return result;
        }
    }

    private static class WebAuthNOptionsRowMapper implements RowMapper<WebAuthNOptions, ResultSet> {
        private static final WebAuthNOptionsRowMapper INSTANCE = new WebAuthNOptionsRowMapper();

        public static WebAuthNOptionsRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public WebAuthNOptions map(ResultSet rs) throws Exception {
            WebAuthNOptions result = new WebAuthNOptions();
            result.timeout = rs.getLong("timeout");
            result.expiresAt = rs.getLong("expires_at");
            result.createdAt = rs.getLong("created_at");
            result.relyingPartyId = rs.getString("rp_id");
            result.origin = rs.getString("origin");
            result.challenge = rs.getString("challenge");
            result.userEmail = rs.getString("email");
            result.generatedOptionsId = rs.getString("id");
            result.relyingPartyName = rs.getString("rp_name");
            return result;
        }
    }
}
