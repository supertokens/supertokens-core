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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.inmemorydb.queries.utils.JsonUtils;
import io.supertokens.pluginInterface.RowMapper;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;

import java.sql.*;
import java.util.HashMap;
import java.util.Objects;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class ThirdPartyProviderClientSQLHelper {
    public static class TenantThirdPartyProviderClientRowMapper implements
            RowMapper<ThirdPartyConfig.ProviderClient, ResultSet> {
        public static final TenantThirdPartyProviderClientRowMapper INSTANCE =
                new TenantThirdPartyProviderClientRowMapper();

        private TenantThirdPartyProviderClientRowMapper() {
        }

        public static TenantThirdPartyProviderClientRowMapper getInstance() {
            return INSTANCE;
        }

        @Override
        public ThirdPartyConfig.ProviderClient map(ResultSet result) throws StorageQueryException {
            try {
                String scopeArrayStr = result.getString("scope");
                String[] scopeStringArray;
                if (scopeArrayStr.isEmpty()) {
                    scopeStringArray = null;
                } else {
                    JsonArray scopeArray = new Gson().fromJson(scopeArrayStr, JsonArray.class);
                    scopeStringArray = new String[scopeArray.size()];
                    for (int i = 0; i < scopeArray.size(); i++) {
                        scopeStringArray[i] = scopeArray.get(i).getAsString();
                    }
                }
                String clientType = result.getString("client_type");
                if (clientType.equals("")) {
                    clientType = null;
                }

                Boolean forcePkce = result.getBoolean("force_pkce");
                if (result.wasNull()) forcePkce = null;

                return new ThirdPartyConfig.ProviderClient(
                        clientType,
                        result.getString("client_id"),
                        result.getString("client_secret"),
                        scopeStringArray,
                        forcePkce,
                        JsonUtils.stringToJsonObject(result.getString("additional_config"))
                );
            } catch (Exception e) {
                throw new StorageQueryException(e);
            }
        }
    }

    public static HashMap<TenantIdentifier, HashMap<String, HashMap<String, ThirdPartyConfig.ProviderClient>>> selectAll(
            Start start)
            throws SQLException, StorageQueryException {
        HashMap<TenantIdentifier, HashMap<String, HashMap<String, ThirdPartyConfig.ProviderClient>>> providerClientsMap = new HashMap<>();

        String QUERY =
                "SELECT connection_uri_domain, app_id, tenant_id, third_party_id, client_type, client_id, " +
                        "client_secret, scope, force_pkce, additional_config FROM "
                        + Config.getConfig(start).getTenantThirdPartyProviderClientsTable() + ";";

        execute(start, QUERY, pst -> {
        }, result -> {
            while (result.next()) {
                TenantIdentifier tenantIdentifier = new TenantIdentifier(result.getString("connection_uri_domain"),
                        result.getString("app_id"), result.getString("tenant_id"));
                ThirdPartyConfig.ProviderClient providerClient = TenantThirdPartyProviderClientRowMapper.getInstance()
                        .mapOrThrow(result);
                if (!providerClientsMap.containsKey(tenantIdentifier)) {
                    providerClientsMap.put(tenantIdentifier, new HashMap<>());
                }

                if (!providerClientsMap.get(tenantIdentifier).containsKey(result.getString("third_party_id"))) {
                    providerClientsMap.get(tenantIdentifier).put(result.getString("third_party_id"), new HashMap<>());
                }

                providerClientsMap.get(tenantIdentifier).get(result.getString("third_party_id"))
                        .put(providerClient.clientType, providerClient);
            }
            return null;
        });
        return providerClientsMap;
    }

    public static void create(Start start, Connection sqlCon, TenantConfig tenantConfig,
                              ThirdPartyConfig.Provider provider, ThirdPartyConfig.ProviderClient providerClient)
            throws SQLException, StorageQueryException {

        String QUERY = "INSERT INTO " + Config.getConfig(start).getTenantThirdPartyProviderClientsTable()
                +
                "(connection_uri_domain, app_id, tenant_id, third_party_id, client_type, client_id, client_secret, " +
                "scope, force_pkce, additional_config)"
                + " VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String scopeArrayStr;
        if (providerClient.scope != null) {
            JsonArray scopeArray = new JsonArray();
            for (String scope : providerClient.scope) {
                scopeArray.add(new JsonPrimitive(scope));
            }
            scopeArrayStr = scopeArray.toString();
        } else {
            scopeArrayStr = "";
        }

        update(sqlCon, QUERY, pst -> {
            pst.setString(1, tenantConfig.tenantIdentifier.getConnectionUriDomain());
            pst.setString(2, tenantConfig.tenantIdentifier.getAppId());
            pst.setString(3, tenantConfig.tenantIdentifier.getTenantId());
            pst.setString(4, provider.thirdPartyId);
            pst.setString(5, Objects.requireNonNullElse(providerClient.clientType, ""));
            pst.setString(6, providerClient.clientId);
            pst.setString(7, providerClient.clientSecret);
            pst.setString(8, scopeArrayStr);
            if (providerClient.forcePKCE == null) {
                pst.setNull(9, Types.BOOLEAN);
            } else {
                pst.setBoolean(9, providerClient.forcePKCE.booleanValue());
            }
            pst.setString(10, JsonUtils.jsonObjectToString(providerClient.additionalConfig));
        });
    }
}
