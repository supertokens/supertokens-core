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
                + "idp_entity_id VARCHAR(1024),"
                + "idp_signing_certificate TEXT,"
                + "allow_idp_initiated_login BOOLEAN NOT NULL DEFAULT FALSE,"
                + "enable_request_signing BOOLEAN NOT NULL DEFAULT TRUE,"
                + "acs_urls TEXT,"
                + "created_at BIGINT NOT NULL,"
                + "updated_at BIGINT NOT NULL,"
                + "UNIQUE (app_id, tenant_id, idp_entity_id),"
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
                + "state TEXT,"
                + "redirect_uri VARCHAR(1024) NOT NULL,"
                + "in_response_to VARCHAR(255),"
                + "acs_url VARCHAR(1024),"
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

    public static String getQueryToCreateSAMLRelayStateExpiresAtIndex(Start start) {
        String table = Config.getConfig(start).getSAMLRelayStateTable();
        return "CREATE INDEX IF NOT EXISTS saml_relay_state_expires_at_index ON " + table + "(expires_at);";
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

    public static String getQueryToCreateSAMLClaimsExpiresAtIndex(Start start) {
        String table = Config.getConfig(start).getSAMLClaimsTable();
        return "CREATE INDEX IF NOT EXISTS saml_claims_expires_at_index ON " + table + "(expires_at);";
    }

    public static String getQueryToCreateSAMLConsumedAssertionsTable(Start start) {
        String table = Config.getConfig(start).getSAMLConsumedAssertionsTable();
        String tenantsTable = Config.getConfig(start).getTenantsTable();
        // @formatter:off
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "app_id VARCHAR(64) NOT NULL DEFAULT 'public',"
                + "tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',"
                + "assertion_id VARCHAR(255) NOT NULL,"
                + "expires_at BIGINT NOT NULL,"
                + "PRIMARY KEY (app_id, tenant_id, assertion_id)," // an assertion is consumed at most once per tenant
                + "FOREIGN KEY (app_id, tenant_id) REFERENCES " + tenantsTable + " (app_id, tenant_id) ON DELETE CASCADE"
                + ");";
        // @formatter:on
    }

    public static String getQueryToCreateSAMLConsumedAssertionsExpiresAtIndex(Start start) {
        String table = Config.getConfig(start).getSAMLConsumedAssertionsTable();
        return "CREATE INDEX IF NOT EXISTS saml_consumed_assertions_expires_at_index ON " + table + "(expires_at);";
    }

    public static void saveRelayStateInfo(Start start, TenantIdentifier tenantIdentifier,
                                          String relayState, String clientId, String state, String redirectURI,
                                          String inResponseTo, String acsURL, long relayStateValidity)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLRelayStateTable();
        String QUERY = "INSERT INTO " + table +
                " (app_id, tenant_id, relay_state, client_id, state, redirect_uri, in_response_to, acs_url, created_at, expires_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
                if (inResponseTo != null) {
                    pst.setString(7, inResponseTo);
                } else {
                    pst.setNull(7, java.sql.Types.VARCHAR);
                }
                if (acsURL != null) {
                    pst.setString(8, acsURL);
                } else {
                    pst.setNull(8, java.sql.Types.VARCHAR);
                }
                pst.setLong(9, System.currentTimeMillis());
                pst.setLong(10, System.currentTimeMillis() + relayStateValidity);
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static SAMLRelayStateInfo getRelayStateInfoAndRemove(Start start, TenantIdentifier tenantIdentifier, String relayState)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLRelayStateTable();
        // Delete-on-read in a single atomic statement: a RelayState (and the SAMLResponse submitted
        // alongside it) must be usable at most once, otherwise a captured valid callback request can be
        // replayed to mint further authorization codes. RETURNING makes the read and the delete one atomic
        // operation, so two concurrent replays cannot both observe the row and succeed. See GHSA-3r7x-rhvc-fw28.
        String QUERY = "DELETE FROM " + table
                + " WHERE app_id = ? AND tenant_id = ? AND relay_state = ? AND expires_at >= ?"
                + " RETURNING client_id, state, redirect_uri, in_response_to, acs_url";

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
                    String inResponseTo = result.getString("in_response_to"); // may be null
                    String acsURL = result.getString("acs_url"); // may be null
                    return new SAMLRelayStateInfo(relayState, clientId, state, redirectURI, inResponseTo, acsURL);
                }
                return null;
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static boolean markSAMLAssertionConsumed(Start start, TenantIdentifier tenantIdentifier,
                                                    String assertionId, long expiresAt) throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLConsumedAssertionsTable();
        // Insert-if-absent as one atomic statement: the primary key on (app_id, tenant_id, assertion_id)
        // makes a second insert of the same assertion a no-op. A return of 0 affected rows therefore means
        // the assertion was already consumed (a replay), and two concurrent replays cannot both insert.
        String QUERY = "INSERT OR IGNORE INTO " + table
                + " (app_id, tenant_id, assertion_id, expires_at) VALUES (?, ?, ?, ?)";
        try {
            int inserted = update(start, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
                pst.setString(3, assertionId);
                pst.setLong(4, expiresAt);
            });
            return inserted > 0;
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static void saveSAMLClaims(Start start, TenantIdentifier tenantIdentifier, String clientId, String code, String claimsJson, long claimsValidity)
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
                pst.setLong(7, System.currentTimeMillis() + claimsValidity);
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

    public static SAMLClient createOrUpdateSAMLClient(
            Start start,
            TenantIdentifier tenantIdentifier,
            String clientId,
            String clientSecret,
            String ssoLoginURL,
            String redirectURIsJson,
            String defaultRedirectURI,
            String idpEntityId,
            String idpSigningCertificate,
            boolean allowIDPInitiatedLogin,
            boolean enableRequestSigning,
            String acsURLsJson)
            throws StorageQueryException, SQLException {
        String table = Config.getConfig(start).getSAMLClientsTable();
        String QUERY = "INSERT INTO " + table +
                " (app_id, tenant_id, client_id, client_secret, sso_login_url, redirect_uris, default_redirect_uri, idp_entity_id, idp_signing_certificate, allow_idp_initiated_login, enable_request_signing, created_at, updated_at, acs_urls) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (app_id, tenant_id, client_id) DO UPDATE SET " +
                "client_secret = ?, sso_login_url = ?, redirect_uris = ?, default_redirect_uri = ?, idp_entity_id = ?, idp_signing_certificate = ?, allow_idp_initiated_login = ?, enable_request_signing = ?, acs_urls = ?, updated_at = ?";
        long now = System.currentTimeMillis();
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
            if (idpEntityId != null) {
                pst.setString(8, idpEntityId);
            } else {
                pst.setNull(8, java.sql.Types.VARCHAR);
            }
            if (idpSigningCertificate != null) {
                pst.setString(9, idpSigningCertificate);
            } else {
                pst.setNull(9, Types.VARCHAR);
            }
            pst.setBoolean(10, allowIDPInitiatedLogin);
            pst.setBoolean(11, enableRequestSigning);
            pst.setLong(12, now);
            pst.setLong(13, now);
            if (acsURLsJson != null) {
                pst.setString(14, acsURLsJson);
            } else {
                pst.setNull(14, Types.VARCHAR);
            }

            if (clientSecret != null) {
                pst.setString(15, clientSecret);
            } else {
                pst.setNull(15, Types.VARCHAR);
            }
            pst.setString(16, ssoLoginURL);
            pst.setString(17, redirectURIsJson);
            pst.setString(18, defaultRedirectURI);
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
            pst.setBoolean(22, enableRequestSigning);
            if (acsURLsJson != null) {
                pst.setString(23, acsURLsJson);
            } else {
                pst.setNull(23, Types.VARCHAR);
            }
            pst.setLong(24, now);
        });

        return getSAMLClient(start, tenantIdentifier, clientId);
    }

    public static SAMLClient getSAMLClient(Start start, TenantIdentifier tenantIdentifier, String clientId)
            throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLClientsTable();
        String QUERY = "SELECT client_id, client_secret, sso_login_url, redirect_uris, default_redirect_uri, idp_entity_id, idp_signing_certificate, allow_idp_initiated_login, enable_request_signing, acs_urls FROM " + table
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
                    String idpEntityId = result.getString("idp_entity_id");
                    String idpSigningCertificate = result.getString("idp_signing_certificate");
                    boolean allowIDPInitiatedLogin = result.getBoolean("allow_idp_initiated_login");
                    boolean enableRequestSigning = result.getBoolean("enable_request_signing");
                    String acsUrlsJson = result.getString("acs_urls");

                    JsonArray redirectURIs = JsonParser.parseString(redirectUrisJson).getAsJsonArray();
                    JsonArray acsURLs = acsUrlsJson == null ? null : JsonParser.parseString(acsUrlsJson).getAsJsonArray();
                    return new SAMLClient(fetchedClientId, clientSecret, ssoLoginURL, redirectURIs, defaultRedirectURI, idpEntityId, idpSigningCertificate, allowIDPInitiatedLogin, enableRequestSigning, acsURLs);
                }
                return null;
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static SAMLClient getSAMLClientByIDPEntityId(Start start, TenantIdentifier tenantIdentifier, String idpEntityId) throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLClientsTable();
        String QUERY = "SELECT client_id, client_secret, sso_login_url, redirect_uris, default_redirect_uri, idp_entity_id, idp_signing_certificate, allow_idp_initiated_login, enable_request_signing, acs_urls FROM " + table
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
                    String fetchedIdpEntityId = result.getString("idp_entity_id");
                    String idpSigningCertificate = result.getString("idp_signing_certificate");
                    boolean allowIDPInitiatedLogin = result.getBoolean("allow_idp_initiated_login");
                    boolean enableRequestSigning = result.getBoolean("enable_request_signing");
                    String acsUrlsJson = result.getString("acs_urls");

                    JsonArray redirectURIs = JsonParser.parseString(redirectUrisJson).getAsJsonArray();
                    JsonArray acsURLs = acsUrlsJson == null ? null : JsonParser.parseString(acsUrlsJson).getAsJsonArray();
                    return new SAMLClient(fetchedClientId, clientSecret, ssoLoginURL, redirectURIs, defaultRedirectURI, fetchedIdpEntityId, idpSigningCertificate, allowIDPInitiatedLogin, enableRequestSigning, acsURLs);
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
        String QUERY = "SELECT client_id, client_secret, sso_login_url, redirect_uris, default_redirect_uri, idp_entity_id, idp_signing_certificate, allow_idp_initiated_login, enable_request_signing, acs_urls FROM " + table
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
                    String idpEntityId = result.getString("idp_entity_id");
                    String idpSigningCertificate = result.getString("idp_signing_certificate");
                    boolean allowIDPInitiatedLogin = result.getBoolean("allow_idp_initiated_login");
                    boolean enableRequestSigning = result.getBoolean("enable_request_signing");
                    String acsUrlsJson = result.getString("acs_urls");

                    JsonArray redirectURIs = JsonParser.parseString(redirectUrisJson).getAsJsonArray();
                    JsonArray acsURLs = acsUrlsJson == null ? null : JsonParser.parseString(acsUrlsJson).getAsJsonArray();
                    clients.add(new SAMLClient(fetchedClientId, clientSecret, ssoLoginURL, redirectURIs, defaultRedirectURI, idpEntityId, idpSigningCertificate, allowIDPInitiatedLogin, enableRequestSigning, acsURLs));
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
            {
                String QUERY = "DELETE FROM " + Config.getConfig(start).getSAMLConsumedAssertionsTable() + " WHERE expires_at <= ?";
                update(start, QUERY, pst -> {
                    pst.setLong(1, System.currentTimeMillis());
                });
            }
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }

    public static int countSAMLClients(Start start, TenantIdentifier tenantIdentifier) throws StorageQueryException {
        String table = Config.getConfig(start).getSAMLClientsTable();
        String QUERY = "SELECT COUNT(*) as c FROM " + table
                + " WHERE app_id = ? AND tenant_id = ?";

        try {
            return execute(start, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getAppId());
                pst.setString(2, tenantIdentifier.getTenantId());
            }, result -> {
                if (result.next()) {
                    return result.getInt("c");
                }
                return 0;
            });
        } catch (SQLException e) {
            throw new StorageQueryException(e);
        }
    }
}
