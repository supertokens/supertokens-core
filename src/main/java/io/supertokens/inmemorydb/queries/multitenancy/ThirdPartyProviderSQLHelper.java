/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.inmemorydb.queries.multitenancy;

import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.inmemorydb.queries.utils.JsonUtils;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class ThirdPartyProviderSQLHelper {
    public static class TenantThirdPartyProviderRowMapper implements
            RowMapper<ThirdPartyConfig.Provider, ResultSet> {
        final private ThirdPartyConfig.ProviderClient[] clients;

        private TenantThirdPartyProviderRowMapper(ThirdPartyConfig.ProviderClient[] clients) {
            this.clients = clients;
        }

        public static TenantThirdPartyProviderRowMapper getInstance(ThirdPartyConfig.ProviderClient[] clients) {
            return new TenantThirdPartyProviderRowMapper(clients);
        }

        @Override
        public ThirdPartyConfig.Provider map(ResultSet result) throws StorageQueryException {
            try {
                Boolean requireEmail = result.getBoolean("require_email");
                if (result.wasNull()) requireEmail = null;
                return new ThirdPartyConfig.Provider(
                        result.getString("third_party_id"),
                        result.getString("name"),
                        this.clients,
                        result.getString("authorization_endpoint"),
                        JsonUtils.stringToJsonObject(result.getString("authorization_endpoint_query_params")),
                        result.getString("token_endpoint"),
                        JsonUtils.stringToJsonObject(result.getString("token_endpoint_body_params")),
                        result.getString("user_info_endpoint"),
                        JsonUtils.stringToJsonObject(result.getString("user_info_endpoint_query_params")),
                        JsonUtils.stringToJsonObject(result.getString("user_info_endpoint_headers")),
                        result.getString("jwks_uri"),
                        result.getString("oidc_discovery_endpoint"),
                        requireEmail,
                        new ThirdPartyConfig.UserInfoMap(
                                new ThirdPartyConfig.UserInfoMapKeyValue(
                                        result.getString("user_info_map_from_id_token_payload_user_id"),
                                        result.getString("user_info_map_from_id_token_payload_email"),
                                        result.getString("user_info_map_from_id_token_payload_email_verified")
                                ),
                                new ThirdPartyConfig.UserInfoMapKeyValue(
                                        result.getString("user_info_map_from_user_info_endpoint_user_id"),
                                        result.getString("user_info_map_from_user_info_endpoint_email"),
                                        result.getString("user_info_map_from_user_info_endpoint_email_verified")
                                )
                        )
                );
            } catch (Exception e) {
                throw new StorageQueryException(e);
            }
        }
    }

    public static HashMap<TenantIdentifier, HashMap<String, ThirdPartyConfig.Provider>> selectAll(Start start,
                                                                                                  HashMap<TenantIdentifier, HashMap<String, HashMap<String, ThirdPartyConfig.ProviderClient>>> providerClientsMap)
            throws SQLException, StorageQueryException {
        HashMap<TenantIdentifier, HashMap<String, ThirdPartyConfig.Provider>> providerMap = new HashMap<>();

        String QUERY =
                "SELECT connection_uri_domain, app_id, tenant_id, third_party_id, name, authorization_endpoint, " +
                        "authorization_endpoint_query_params, token_endpoint, token_endpoint_body_params, " +
                        "user_info_endpoint, user_info_endpoint_query_params, user_info_endpoint_headers, jwks_uri, " +
                        "oidc_discovery_endpoint, require_email, user_info_map_from_id_token_payload_user_id, " +
                        "user_info_map_from_id_token_payload_email, " +
                        "user_info_map_from_id_token_payload_email_verified, " +
                        "user_info_map_from_user_info_endpoint_user_id, user_info_map_from_user_info_endpoint_email, " +
                        "user_info_map_from_user_info_endpoint_email_verified FROM "
                        + Config.getConfig(start).getTenantThirdPartyProvidersTable() + ";";

        execute(start, QUERY, pst -> {
        }, result -> {
            while (result.next()) {
                TenantIdentifier tenantIdentifier = new TenantIdentifier(result.getString("connection_uri_domain"),
                        result.getString("app_id"), result.getString("tenant_id"));
                ThirdPartyConfig.ProviderClient[] clients = null;
                if (providerClientsMap.containsKey(tenantIdentifier) &&
                        providerClientsMap.get(tenantIdentifier).containsKey(result.getString("third_party_id"))) {
                    clients = providerClientsMap.get(tenantIdentifier).get(result.getString("third_party_id")).values()
                            .toArray(new ThirdPartyConfig.ProviderClient[0]);
                }
                ThirdPartyConfig.Provider provider = TenantThirdPartyProviderRowMapper.getInstance(clients)
                        .mapOrThrow(result);

                if (!providerMap.containsKey(tenantIdentifier)) {
                    providerMap.put(tenantIdentifier, new HashMap<>());
                }
                providerMap.get(tenantIdentifier).put(provider.thirdPartyId, provider);
            }
            return null;
        });
        return providerMap;
    }

    public static void create(Start start, Connection sqlCon, TenantConfig tenantConfig,
                              ThirdPartyConfig.Provider provider)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getTenantThirdPartyProvidersTable()
                +
                "(connection_uri_domain, app_id, tenant_id, third_party_id, name, authorization_endpoint, " +
                "authorization_endpoint_query_params, token_endpoint, token_endpoint_body_params, user_info_endpoint," +
                " user_info_endpoint_query_params, user_info_endpoint_headers, jwks_uri, oidc_discovery_endpoint, " +
                "require_email, user_info_map_from_id_token_payload_user_id, " +
                "user_info_map_from_id_token_payload_email, user_info_map_from_id_token_payload_email_verified, " +
                "user_info_map_from_user_info_endpoint_user_id, user_info_map_from_user_info_endpoint_email, " +
                "user_info_map_from_user_info_endpoint_email_verified)" +
                " VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        update(sqlCon, QUERY, pst -> {
            pst.setString(1, tenantConfig.tenantIdentifier.getConnectionUriDomain());
            pst.setString(2, tenantConfig.tenantIdentifier.getAppId());
            pst.setString(3, tenantConfig.tenantIdentifier.getTenantId());
            pst.setString(4, provider.thirdPartyId);
            pst.setString(5, provider.name);
            pst.setString(6, provider.authorizationEndpoint);
            pst.setString(7, JsonUtils.jsonObjectToString(provider.authorizationEndpointQueryParams));
            pst.setString(8, provider.tokenEndpoint);
            pst.setString(9, JsonUtils.jsonObjectToString(provider.tokenEndpointBodyParams));
            pst.setString(10, provider.userInfoEndpoint);
            pst.setString(11, JsonUtils.jsonObjectToString(provider.userInfoEndpointQueryParams));
            pst.setString(12, JsonUtils.jsonObjectToString(provider.userInfoEndpointHeaders));
            pst.setString(13, provider.jwksURI);
            pst.setString(14, provider.oidcDiscoveryEndpoint);
            if (provider.requireEmail == null) {
                pst.setNull(15, Types.BOOLEAN);
            } else {
                pst.setBoolean(15, provider.requireEmail.booleanValue());
            }
            pst.setString(16, provider.userInfoMap.fromIdTokenPayload.userId);
            pst.setString(17, provider.userInfoMap.fromIdTokenPayload.email);
            pst.setString(18, provider.userInfoMap.fromIdTokenPayload.emailVerified);
            pst.setString(19, provider.userInfoMap.fromUserInfoAPI.userId);
            pst.setString(20, provider.userInfoMap.fromUserInfoAPI.email);
            pst.setString(21, provider.userInfoMap.fromUserInfoAPI.emailVerified);
        });
    }
}
