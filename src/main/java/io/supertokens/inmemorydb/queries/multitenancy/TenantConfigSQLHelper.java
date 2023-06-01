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
import io.supertokens.pluginInterface.multitenancy.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class TenantConfigSQLHelper {
    public static class TenantConfigRowMapper implements RowMapper<TenantConfig, ResultSet> {
        ThirdPartyConfig.Provider[] providers;

        private TenantConfigRowMapper(ThirdPartyConfig.Provider[] providers) {
            this.providers = providers;
        }

        public static TenantConfigRowMapper getInstance(ThirdPartyConfig.Provider[] providers) {
            return new TenantConfigRowMapper(providers);
        }

        @Override
        public TenantConfig map(ResultSet result) throws StorageQueryException {
            try {
                return new TenantConfig(
                        new TenantIdentifier(result.getString("connection_uri_domain"), result.getString("app_id"), result.getString("tenant_id")),
                        new EmailPasswordConfig(result.getBoolean("email_password_enabled")),
                        new ThirdPartyConfig(result.getBoolean("third_party_enabled"), this.providers),
                        new PasswordlessConfig(result.getBoolean("passwordless_enabled")),
                        JsonUtils.stringToJsonObject(result.getString("core_config"))
                );
            } catch (Exception e) {
                throw new StorageQueryException(e);
            }
        }
    }

    public static TenantConfig[] selectAll(Start start, HashMap<TenantIdentifier, HashMap<String, ThirdPartyConfig.Provider>> providerMap)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT connection_uri_domain, app_id, tenant_id, core_config, email_password_enabled, passwordless_enabled, third_party_enabled FROM "
                + Config.getConfig(start).getTenantConfigsTable() + ";";

        TenantConfig[] tenantConfigs = execute(start, QUERY, pst -> {}, result -> {
            List<TenantConfig> temp = new ArrayList<>();
            while (result.next()) {
                TenantIdentifier tenantIdentifier = new TenantIdentifier(result.getString("connection_uri_domain"), result.getString("app_id"), result.getString("tenant_id"));
                ThirdPartyConfig.Provider[] providers = null;
                if (providerMap.containsKey(tenantIdentifier)) {
                    providers = providerMap.get(tenantIdentifier).values().toArray(new ThirdPartyConfig.Provider[0]);
                }
                temp.add(TenantConfigRowMapper.getInstance(providers).mapOrThrow(result));
            }
            TenantConfig[] finalResult = new TenantConfig[temp.size()];
            for (int i = 0; i < temp.size(); i++) {
                finalResult[i] = temp.get(i);
            }
            return finalResult;
        });
        return tenantConfigs;
    }

    public static void create(Start start, Connection sqlCon, TenantConfig tenantConfig)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getTenantConfigsTable()
                + "(connection_uri_domain, app_id, tenant_id, core_config, email_password_enabled, passwordless_enabled, third_party_enabled)" + " VALUES(?, ?, ?, ?, ?, ?, ?)";

        update(sqlCon, QUERY, pst -> {
            pst.setString(1, tenantConfig.tenantIdentifier.getConnectionUriDomain());
            pst.setString(2, tenantConfig.tenantIdentifier.getAppId());
            pst.setString(3, tenantConfig.tenantIdentifier.getTenantId());
            pst.setString(4, tenantConfig.coreConfig.toString());
            pst.setBoolean(5, tenantConfig.emailPasswordConfig.enabled);
            pst.setBoolean(6, tenantConfig.passwordlessConfig.enabled);
            pst.setBoolean(7, tenantConfig.thirdPartyConfig.enabled);
        });
    }

}
