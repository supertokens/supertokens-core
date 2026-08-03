/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class ActivityLogQueries {

    /** Same retention window as the PostgreSQL implementation. */
    private static final int RETENTION_DAYS = 31;

    private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;

    static String getQueryToCreateActivityLogTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getActivityLogTable() + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "app_id VARCHAR(64) NOT NULL DEFAULT 'public',"
                + "tenant_id VARCHAR(64) NOT NULL DEFAULT 'public',"
                + "recipe_user_id VARCHAR(128),"
                + "primary_or_recipe_user_id VARCHAR(128),"
                + "event_type VARCHAR(64) NOT NULL,"
                + "status VARCHAR(128),"
                + "auth_principal VARCHAR(256),"
                + "identifier VARCHAR(256),"
                + "created_at BIGINT NOT NULL,"
                + "payload TEXT"
                + ");";
    }

    static String getQueryToCreateCreatedAtIndex(Start start) {
        return "CREATE INDEX IF NOT EXISTS activity_log_created_at_index ON "
                + Config.getConfig(start).getActivityLogTable() + "(created_at);";
    }

    public static void createActivityLogEntry(Start start, TenantIdentifier tenantIdentifier, AuditLogEvent event)
            throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getActivityLogTable()
                + " (app_id, tenant_id, recipe_user_id, primary_or_recipe_user_id, event_type, status,"
                + " auth_principal, identifier, created_at, payload)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        update(start, QUERY, pst -> {
            pst.setString(1, tenantIdentifier.getAppId());
            pst.setString(2, tenantIdentifier.getTenantId());
            pst.setString(3, event.recipeUserId);
            pst.setString(4, event.primaryOrRecipeUserId);
            pst.setString(5, event.eventType);
            pst.setString(6, event.status);
            pst.setString(7, event.authPrincipal);
            pst.setString(8, event.identifier);
            pst.setLong(9, event.createdAt);
            pst.setString(10, event.payload);
        });
    }

    /**
     * The unpartitioned table has no partitions to drop, so retention is enforced with a direct
     * delete — same cutoff semantics as the PostgreSQL implementation's DEFAULT-partition purge.
     */
    public static void deleteEntriesOlderThanRetention(Start start) throws SQLException, StorageQueryException {
        long cutoffMillis = LocalDate.now(ZoneOffset.UTC).minusDays(RETENTION_DAYS).toEpochDay() * MILLIS_PER_DAY;
        String QUERY = "DELETE FROM " + Config.getConfig(start).getActivityLogTable() + " WHERE created_at < ?";
        update(start, QUERY, pst -> pst.setLong(1, cutoffMillis));
    }
}
