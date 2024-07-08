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
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class MfaSqlHelper {
    public static HashMap<TenantIdentifier, String[]> selectAllFirstFactors(Start start)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT connection_uri_domain, app_id, tenant_id, factor_id FROM "
                + Config.getConfig(start).getTenantFirstFactorsTable() + ";";
        return execute(start, QUERY, pst -> {
        }, result -> {
            HashMap<TenantIdentifier, List<String>> firstFactors = new HashMap<>();

            while (result.next()) {
                TenantIdentifier tenantIdentifier = new TenantIdentifier(result.getString("connection_uri_domain"),
                        result.getString("app_id"), result.getString("tenant_id"));
                if (!firstFactors.containsKey(tenantIdentifier)) {
                    firstFactors.put(tenantIdentifier, new ArrayList<>());
                }

                firstFactors.get(tenantIdentifier).add(result.getString("factor_id"));
            }

            HashMap<TenantIdentifier, String[]> finalResult = new HashMap<>();
            for (TenantIdentifier tenantIdentifier : firstFactors.keySet()) {
                finalResult.put(tenantIdentifier, firstFactors.get(tenantIdentifier).toArray(new String[0]));
            }

            return finalResult;
        });
    }

    public static HashMap<TenantIdentifier, String[]> selectAllRequiredSecondaryFactors(Start start)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT connection_uri_domain, app_id, tenant_id, factor_id FROM "
                + Config.getConfig(start).getTenantRequiredSecondaryFactorsTable() + ";";
        return execute(start, QUERY, pst -> {
        }, result -> {
            HashMap<TenantIdentifier, List<String>> defaultRequiredFactors = new HashMap<>();

            while (result.next()) {
                TenantIdentifier tenantIdentifier = new TenantIdentifier(result.getString("connection_uri_domain"),
                        result.getString("app_id"), result.getString("tenant_id"));
                if (!defaultRequiredFactors.containsKey(tenantIdentifier)) {
                    defaultRequiredFactors.put(tenantIdentifier, new ArrayList<>());
                }

                defaultRequiredFactors.get(tenantIdentifier).add(result.getString("factor_id"));
            }

            HashMap<TenantIdentifier, String[]> finalResult = new HashMap<>();
            for (TenantIdentifier tenantIdentifier : defaultRequiredFactors.keySet()) {
                finalResult.put(tenantIdentifier, defaultRequiredFactors.get(tenantIdentifier).toArray(new String[0]));
            }

            return finalResult;
        });
    }

    public static void createFirstFactors(Start start, Connection sqlCon, TenantIdentifier tenantIdentifier,
                                          String[] firstFactors)
            throws SQLException, StorageQueryException {
        if (firstFactors == null || firstFactors.length == 0) {
            return;
        }

        String QUERY = "INSERT INTO " + Config.getConfig(start).getTenantFirstFactorsTable() +
                "(connection_uri_domain, app_id, tenant_id, factor_id) VALUES (?, ?, ?, ?);";
        for (String factorId : new HashSet<>(Arrays.asList(firstFactors))) {
            update(sqlCon, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getConnectionUriDomain());
                pst.setString(2, tenantIdentifier.getAppId());
                pst.setString(3, tenantIdentifier.getTenantId());
                pst.setString(4, factorId);
            });
        }
    }

    public static void createRequiredSecondaryFactors(Start start, Connection sqlCon, TenantIdentifier tenantIdentifier,
                                                      String[] requiredSecondaryFactors)
            throws SQLException, StorageQueryException {
        if (requiredSecondaryFactors == null || requiredSecondaryFactors.length == 0) {
            return;
        }

        String QUERY = "INSERT INTO " + Config.getConfig(start).getTenantRequiredSecondaryFactorsTable() +
                "(connection_uri_domain, app_id, tenant_id, factor_id) VALUES (?, ?, ?, ?);";
        for (String factorId : requiredSecondaryFactors) {
            update(sqlCon, QUERY, pst -> {
                pst.setString(1, tenantIdentifier.getConnectionUriDomain());
                pst.setString(2, tenantIdentifier.getAppId());
                pst.setString(3, tenantIdentifier.getTenantId());
                pst.setString(4, factorId);
            });
        }
    }
}
