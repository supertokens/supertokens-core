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

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.saml.SAMLClaimsInfo;
import io.supertokens.pluginInterface.saml.SAMLClient;
import io.supertokens.pluginInterface.saml.SAMLRelayStateInfo;

public class SAMLQueries {
    public static String getQueryToCreateSAMLClientsTable(Start start) {
        String table = Config.getConfig(start).getSAMLClientsTable();
        String tenantsTable = Config.getConfig(start).getTenantsTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "app_id VARCHAR(64) NOT NULL DEFAULT 'public',"
                + "tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',"
                + "client_id VARCHAR(255) NOT NULL,"
                + "client_secret TEXT,"
                + "sso_login_url TEXT NOT NULL,"
                + "redirect_uris TEXT NOT NULL," // store JsonArray.toString()
                + "default_redirect_uri VARCHAR(1024) NOT NULL,"
                + "metadata_url VARCHAR(1024),"
                + "sp_entity_id VARCHAR(1024),"
                + "idp_entity_id VARCHAR(1024),"
                + "idp_signing_certificate TEXT,"
                + "allow_idp_initiated_login BOOLEAN NOT NULL DEFAULT FALSE,"
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
                + "created_at BIGINT NOT NULL,"
                + "expires_at BIGINT NOT NULL,"
                + "PRIMARY KEY (relay_state)," // relayState must be unique
                + "FOREIGN KEY (app_id, tenant_id) REFERENCES " + tenantsTable + " (app_id, tenant_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    public static String getQueryToCreateSAMLRelayStateAppIdTenantIdIndex(Start start) {
        String table = Config.getConfig(start).getSAMLRelayStateTable();
        return "CREATE INDEX IF NOT EXISTS saml_relay_state_app_tenant_index ON " + table + "(app_id, tenant_id);";
    }

    public static String getQueryToCreateSAMLClaimsTable(Start start) {
        String table = Config.getConfig(start).getSAMLClaimsTable();
        String tenantsTable = Config.getConfig(start).getTenantsTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "app_id VARCHAR(64) NOT NULL DEFAULT 'public',"
                + "tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',"
                + "client_id VARCHAR(255) NOT NULL,"
                + "code VARCHAR(255) NOT NULL,"
                + "claims TEXT NOT NULL,"
                + "created_at BIGINT NOT NULL,"
                + "expires_at BIGINT NOT NULL,"
                + "PRIMARY KEY (code),"
                + "FOREIGN KEY (app_id, tenant_id) REFERENCES " + tenantsTable + " (app_id, tenant_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    public static String getQueryToCreateSAMLClaimsAppIdTenantIdIndex(Start start) {
        String table = Config.getConfig(start).getSAMLClaimsTable();
        return "CREATE INDEX IF NOT EXISTS saml_claims_app_tenant_index ON " + table + "(app_id, tenant_id);";
    }

    public static void saveRelayStateInfo(Start start, TenantIdentifier tenantIdentifier,
                                          String relayState, String clientId, String state, String redirectURI)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLRelayStateTable();
        String QUERY = "INSERT INTO " + table +
                " (app_id, tenant_id, relay_state, client_id, state, redirect_uri, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

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
                pst.setLong(7, System.currentTimeMillis());
                pst.setLong(8, System.currentTimeMillis() + 300000);
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static SAMLRelayStateInfo getRelayStateInfo(Start start, TenantIdentifier tenantIdentifier, String relayState)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLRelayStateTable();
        String QUERY = "SELECT client_id, state, redirect_uri, expires_at FROM " + table
                + " WHERE app_id = ? AND tenant_id = ? AND relay_state = ? AND expires_at >= ?";

        try {
            return execute(start, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, relayState);
                pst.setLong(4, System.currentTimeMillis());
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

    public static void saveSAMLClaims(Start start, TenantIdentifier tenantIdentifier, String clientId, String code, String claimsJson)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLClaimsTable();
        String QUERY = "INSERT INTO " + table +
                " (app_id, tenant_id, client_id, code, claims, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try {
            update(start, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, clientId);
                pst.setString(4, code);
                pst.setString(5, claimsJson);
                pst.setLong(6, System.currentTimeMillis());
                pst.setLong(7, System.currentTimeMillis() + 300000);
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static SAMLClaimsInfo getSAMLClaimsAndRemoveCode(Start start, TenantIdentifier tenantIdentifier, String code)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLClaimsTable();
        String QUERY = "SELECT client_id, claims FROM " + table + " WHERE app_id = ? AND tenant_id = ? AND code = ? AND expires_at >= ?";
        try {
            SAMLClaimsInfo claimsInfo = execute(start, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, code);
                pst.setLong(4, System.currentTimeMillis());
            }, result -> {
                if (result.next()) {
                    String clientId = result.getString("client_id");
                    JsonObject claims = com.google.gson.JsonParser.parseString(result.getString("claims")).getAsJsonObject();
                    return new SAMLClaimsInfo(clientId, claims);
                }
                return null;
            });

            if (claimsInfo != null) {
                String DELETE = "DELETE FROM " + table + " WHERE app_id = ? AND tenant_id = ? AND code = ?";
                update(start, DELETE, pst -> {
                    pst.setString(1, tenantIdentifier.getAppId());
                    pst.setString(2, tenantIdentifier.getTenantId());
                    pst.setString(3, code);
                });
            }
            return claimsInfo;
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static void createOrUpdateSAMLClient(
            Start start,
            TenantIdentifier tenantIdentifier,
            String clientId,
            String clientSecret,
            String ssoLoginURL,
            String redirectURIsJson,
            String defaultRedirectURI,
            String metadataURL,
            String spEntityId,
            String idpEntityId,
            String idpSigningCertificate,
            boolean allowIDPInitiatedLogin)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLClientsTable();
        String QUERY = "INSERT INTO " + table +
                " (app_id, tenant_id, client_id, client_secret, sso_login_url, redirect_uris, default_redirect_uri, metadata_url, sp_entity_id, idp_entity_id, idp_signing_certificate, allow_idp_initiated_login) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (app_id, tenant_id, client_id) DO UPDATE SET " +
                "client_secret = ?, sso_login_url = ?, redirect_uris = ?, default_redirect_uri = ?, metadata_url = ?, sp_entity_id = ?, idp_entity_id = ?, idp_signing_certificate = ?, allow_idp_initiated_login = ?";

        try {
            update(start, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, clientId);
                if (clientSecret != null) {
                    pst.setString(4, clientSecret);
                } else {
                    pst.setNull(4, Types.VARCHAR);
                }
                pst.setString(5, ssoLoginURL);
                pst.setString(6, redirectURIsJson);
                pst.setString(7, defaultRedirectURI);
                if (metadataURL != null) {
                    pst.setString(8, metadataURL);
                } else {
                    pst.setNull(8, Types.VARCHAR);
                }
                if (spEntityId != null) {
                    pst.setString(9, spEntityId);
                } else {
                    pst.setNull(9, java.sql.Types.VARCHAR);
                }
                if (idpEntityId != null) {
                    pst.setString(10, idpEntityId);
                } else {
                    pst.setNull(10, java.sql.Types.VARCHAR);
                }
                if (idpSigningCertificate != null) {
                    pst.setString(11, idpSigningCertificate);
                } else {
                    pst.setNull(11, Types.VARCHAR);
                }
                pst.setBoolean(12, allowIDPInitiatedLogin);

                if (clientSecret != null) {
                    pst.setString(13, clientSecret);
                } else {
                    pst.setNull(13, Types.VARCHAR);
                }
                pst.setString(14, ssoLoginURL);
                pst.setString(15, redirectURIsJson);
                pst.setString(16, defaultRedirectURI);
                if (metadataURL != null) {
                    pst.setString(17, metadataURL);
                } else {
                    pst.setNull(17, Types.VARCHAR);
                }
                if (spEntityId != null) {
                    pst.setString(18, spEntityId);
                } else {
                    pst.setNull(18, java.sql.Types.VARCHAR);
                }
                if (idpEntityId != null) {
                    pst.setString(19, idpEntityId);
                } else {
                    pst.setNull(19, java.sql.Types.VARCHAR);
                }
                if (idpSigningCertificate != null) {
                    pst.setString(20, idpSigningCertificate);
                } else {
                    pst.setNull(20, Types.VARCHAR);
                }
                pst.setBoolean(21, allowIDPInitiatedLogin);
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static SAMLClient getSAMLClient(Start start, TenantIdentifier tenantIdentifier, String clientId)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLClientsTable();
        String QUERY = "SELECT client_id, client_secret, sso_login_url, redirect_uris, default_redirect_uri, metadata_url, sp_entity_id, idp_entity_id, idp_signing_certificate, allow_idp_initiated_login FROM " + table
                + " WHERE app_id = ? AND tenant_id = ? AND client_id = ?";

        try {
            return execute(start, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, clientId);
            }, result -> {
                if (result.next()) {
                    String fetchedClientId = result.getString("client_id");
                    String clientSecret = result.getString("client_secret");
                    String ssoLoginURL = result.getString("sso_login_url");
                    String redirectUrisJson = result.getString("redirect_uris");
                    String defaultRedirectURI = result.getString("default_redirect_uri");
                    String metadataURL = result.getString("metadata_url");
                    String spEntityId = result.getString("sp_entity_id");
                    String idpEntityId = result.getString("idp_entity_id");
                    String idpSigningCertificate = result.getString("idp_signing_certificate");
                    boolean allowIDPInitiatedLogin = result.getBoolean("allow_idp_initiated_login");

                    JsonArray redirectURIs = JsonParser.parseString(redirectUrisJson).getAsJsonArray();
                    return new SAMLClient(fetchedClientId, clientSecret, ssoLoginURL, redirectURIs, defaultRedirectURI, metadataURL, spEntityId, idpEntityId, idpSigningCertificate, allowIDPInitiatedLogin);
                }
                return null;
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static SAMLClient getSAMLClientByIDPEntityId(Start start, TenantIdentifier tenantIdentifier, String idpEntityId) throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLClientsTable();
        String QUERY = "SELECT client_id, client_secret, sso_login_url, redirect_uris, default_redirect_uri, metadata_url, sp_entity_id, idp_entity_id, idp_signing_certificate, allow_idp_initiated_login FROM " + table
                + " WHERE app_id = ? AND tenant_id = ? AND idp_entity_id = ?";

        try {
            return execute(start, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, idpEntityId);
            }, result -> {
                if (result.next()) {
                    String fetchedClientId = result.getString("client_id");
                    String clientSecret = result.getString("client_secret");
                    String ssoLoginURL = result.getString("sso_login_url");
                    String redirectUrisJson = result.getString("redirect_uris");
                    String defaultRedirectURI = result.getString("default_redirect_uri");
                    String metadataURL = result.getString("metadata_url");
                    String spEntityId = result.getString("sp_entity_id");
                    String fetchedIdpEntityId = result.getString("idp_entity_id");
                    String idpSigningCertificate = result.getString("idp_signing_certificate");
                    boolean allowIDPInitiatedLogin = result.getBoolean("allow_idp_initiated_login");

                    JsonArray redirectURIs = JsonParser.parseString(redirectUrisJson).getAsJsonArray();
                    return new SAMLClient(fetchedClientId, clientSecret, ssoLoginURL, redirectURIs, defaultRedirectURI, metadataURL, spEntityId, fetchedIdpEntityId, idpSigningCertificate, allowIDPInitiatedLogin);
                }
                return null;
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static List<SAMLClient> getSAMLClients(Start start, TenantIdentifier tenantIdentifier)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLClientsTable();
        String QUERY = "SELECT client_id, client_secret, sso_login_url, redirect_uris, default_redirect_uri, metadata_url, sp_entity_id, idp_entity_id, idp_signing_certificate, allow_idp_initiated_login FROM " + table
                + " WHERE app_id = ? AND tenant_id = ?";

        try {
            return execute(start, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
            }, result -> {
                List<SAMLClient> clients = new ArrayList<>();
                while (result.next()) {
                    String fetchedClientId = result.getString("client_id");
                    String clientSecret = result.getString("client_secret");
                    String ssoLoginURL = result.getString("sso_login_url");
                    String redirectUrisJson = result.getString("redirect_uris");
                    String defaultRedirectURI = result.getString("default_redirect_uri");
                    String metadataURL = result.getString("metadata_url");
                    String spEntityId = result.getString("sp_entity_id");
                    String idpEntityId = result.getString("idp_entity_id");
                    String idpSigningCertificate = result.getString("idp_signing_certificate");
                    boolean allowIDPInitiatedLogin = result.getBoolean("allow_idp_initiated_login");

                    JsonArray redirectURIs = JsonParser.parseString(redirectUrisJson).getAsJsonArray();
                    clients.add(new SAMLClient(fetchedClientId, clientSecret, ssoLoginURL, redirectURIs, defaultRedirectURI, metadataURL, spEntityId, idpEntityId, idpSigningCertificate, allowIDPInitiatedLogin));
                }
                return clients;
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static boolean removeSAMLClient(Start start, TenantIdentifier tenantIdentifier, String clientId)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLClientsTable();
        String QUERY = "DELETE FROM " + table + " WHERE app_id = ? AND tenant_id = ? AND client_id = ?";
        try {
            return update(start, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, clientId);
            }) > 0;

        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static void removeExpiredSAMLCodesAndRelayStates(Start start) throws StorageQueryException {
        try {
            {
                String QUERY = "DELETE FROM " + Config.getConfig(start).getSAMLClaimsTable() + " WHERE expires_at <= ?";
                update(start, QUERY, pst -> {
                    pst.setLong(1, System.currentTimeMillis());
                });
            }
            {
                String QUERY = "DELETE FROM " + Config.getConfig(start).getSAMLRelayStateTable() + " WHERE expires_at <= ?";
                update(start, QUERY, pst -> {
                    pst.setLong(1, System.currentTimeMillis());
                });
            }
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }
}
