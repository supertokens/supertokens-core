/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.saml.SAMLClient;
import io.supertokens.pluginInterface.saml.SAMLRelayStateInfo;

import java.sql.SQLException;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class SAMLQueries {
    public static String getQueryToCreateSAMLClientsTable(Start start) {
        String table = Config.getConfig(start).getSAMLClientsTable();
        String tenantsTable = Config.getConfig(start).getTenantsTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "app_id VARCHAR(64) NOT NULL DEFAULT 'public',"
                + "tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',"
                + "client_id VARCHAR(255) NOT NULL,"
                + "sso_login_url TEXT NOT NULL,"
                + "redirect_uris TEXT NOT NULL," // store JsonArray.toString()
                + "default_redirect_uri VARCHAR(1024) NOT NULL,"
                + "sp_entity_id VARCHAR(1024),"
                + "PRIMARY KEY (app_id, tenant_id, client_id),"
                + "FOREIGN KEY (app_id, tenant_id) REFERENCES " + tenantsTable + " (app_id, tenant_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    public static String getQueryToCreateSAMLClientsAppIdTenantIdIndex(Start start) {
        String table = Config.getConfig(start).getSAMLClientsTable();
        return "CREATE INDEX IF NOT EXISTS saml_clients_app_tenant_index ON " + table + "(app_id, tenant_id);";
    }

    public static String getQueryToCreateSAMLRelayStateTable(Start start) {
        String table = Config.getConfig(start).getSAMLRelayStateTable();
        String tenantsTable = Config.getConfig(start).getTenantsTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "app_id VARCHAR(64) NOT NULL DEFAULT 'public',"
                + "tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',"
                + "relay_state VARCHAR(255) NOT NULL,"
                + "client_id VARCHAR(255) NOT NULL,"
                + "state TEXT," // nullable
                + "redirect_uri VARCHAR(1024) NOT NULL,"
                + "created_at_time BIGINT NOT NULL DEFAULT (strftime('%s','now') * 1000),"
                + "PRIMARY KEY (relay_state)," // relayState must be unique
                + "FOREIGN KEY (app_id, tenant_id) REFERENCES " + tenantsTable + " (app_id, tenant_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    public static String getQueryToCreateSAMLRelayStateAppIdTenantIdIndex(Start start) {
        String table = Config.getConfig(start).getSAMLRelayStateTable();
        return "CREATE INDEX IF NOT EXISTS saml_relay_state_app_tenant_index ON " + table + "(app_id, tenant_id);";
    }

    public static void saveRelayStateInfo(Start start, TenantIdentifier tenantIdentifier,
                                          String relayState, String clientId, String state, String redirectURI)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLRelayStateTable();
        String QUERY = "INSERT INTO " + table +
                " (app_id, tenant_id, relay_state, client_id, state, redirect_uri) VALUES (?, ?, ?, ?, ?, ?)";

        try {
            update(start, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, relayState);
                pst.setString(4, clientId);
                if (state != null) {
                    pst.setString(5, state);
                } else {
                    pst.setNull(5, java.sql.Types.VARCHAR);
                }
                pst.setString(6, redirectURI);
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static SAMLRelayStateInfo getRelayStateInfo(Start start, TenantIdentifier tenantIdentifier, String relayState)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLRelayStateTable();
        String QUERY = "SELECT client_id, state, redirect_uri FROM " + table
                + " WHERE app_id = ? AND tenant_id = ? AND relay_state = ?";

        try {
            return execute(start, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, relayState);
            }, result -> {
                if (result.next()) {
                    String clientId = result.getString("client_id");
                    String state = result.getString("state"); // may be null
                    String redirectURI = result.getString("redirect_uri");
                    return new SAMLRelayStateInfo(relayState, clientId, state, redirectURI);
                }
                return null;
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static void createOrUpdateSAMLClient(
            Start start,
            TenantIdentifier tenantIdentifier,
            String clientId,
            String ssoLoginURL,
            String redirectURIsJson,
            String defaultRedirectURI,
            String spEntityId)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLClientsTable();
        String QUERY = "INSERT INTO " + table +
                " (app_id, tenant_id, client_id, sso_login_url, redirect_uris, default_redirect_uri, sp_entity_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (app_id, tenant_id, client_id) DO UPDATE SET " +
                "sso_login_url = ?, redirect_uris = ?, default_redirect_uri = ?, sp_entity_id = ?";

        try {
            update(start, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, clientId);
                pst.setString(4, ssoLoginURL);
                pst.setString(5, redirectURIsJson);
                pst.setString(6, defaultRedirectURI);
                if (spEntityId != null) {
                    pst.setString(7, spEntityId);
                } else {
                    pst.setNull(7, java.sql.Types.VARCHAR);
                }

                pst.setString(8, ssoLoginURL);
                pst.setString(9, redirectURIsJson);
                pst.setString(10, defaultRedirectURI);
                if (spEntityId != null) {
                    pst.setString(11, spEntityId);
                } else {
                    pst.setNull(11, java.sql.Types.VARCHAR);
                }
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static SAMLClient getSAMLClient(Start start, TenantIdentifier tenantIdentifier, String clientId)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLClientsTable();
        String QUERY = "SELECT client_id, sso_login_url, redirect_uris, default_redirect_uri, sp_entity_id FROM " + table
                + " WHERE app_id = ? AND tenant_id = ? AND client_id = ?";

        try {
            return execute(start, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, clientId);
            }, result -> {
                if (result.next()) {
                    String fetchedClientId = result.getString("client_id");
                    String ssoLoginURL = result.getString("sso_login_url");
                    String redirectUrisJson = result.getString("redirect_uris");
                    String defaultRedirectURI = result.getString("default_redirect_uri");
                    String spEntityId = result.getString("sp_entity_id");

                    JsonArray redirectURIs = JsonParser.parseString(redirectUrisJson).getAsJsonArray();
                    return new SAMLClient(fetchedClientId, ssoLoginURL, redirectURIs, defaultRedirectURI, spEntityId);
                }
                return null;
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }
}
