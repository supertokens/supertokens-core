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

package io.supertokens.inmemorydb.queries;

import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class MfaQueries {
    public static String getQueryToCreateUserFactorsTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getMfaUserFactorsTable() + " ("
                + "app_id VARCHAR(64) DEFAULT 'public',"
                + "tenant_id VARCHAR(64) DEFAULT 'public',"
                + "user_id VARCHAR(255) NOT NULL,"
                + "factor_id VARCHAR(255) NOT NULL,"
                + "PRIMARY KEY (app_id, tenant_id, user_id, factor_id),"
                + "FOREIGN KEY (app_id, tenant_id)"
                + "REFERENCES " + Config.getConfig(start).getTenantsTable() + " (app_id, tenant_id) ON DELETE CASCADE";
    }

    public static int enableFactor(Start start, TenantIdentifier tenantIdentifier, String userId, String factorId)
            throws StorageQueryException, SQLException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getMfaUserFactorsTable() + " (app_id, tenant_id, user_id, factor_id) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";

        return update(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
            pst.setString(4, factorId);
        });
    }


    public static String[] listFactors(Start start, TenantIdentifier tenantIdentifier, String userId)
            throws StorageQueryException, SQLException {
        String QUERY = "SELECT factor_id FROM " + Config.getConfig(start).getMfaUserFactorsTable() + " WHERE app_id = ? AND tenant_id = ? AND user_id = ?";

        return execute(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
        }, result -> {
            List<String> factors = new ArrayList<>();
            while (result.next()) {
                factors.add(result.getString("factor_id"));
            }

            return factors.toArray(String[]::new);
        });
    }

    public static int disableFactor(Start start, TenantIdentifier tenantIdentifier, String userId, String factorId)
            throws StorageQueryException, SQLException {
        String QUERY = "DELETE FROM " + Config.getConfig(start).getMfaUserFactorsTable() + " WHERE app_id = ? AND tenant_id = ? AND user_id = ? AND factor_id = ?";

        return update(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, userId);
            pst.setString(4, factorId);
        });
    }
}
