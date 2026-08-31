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

import io.supertokens.auditlog.lifecycle.LastActiveFoldEvents;
import io.supertokens.inmemorydb.PreparedStatementValueSetter;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.Utils;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

public class ActivityLogQueries {

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

    private static String getQueryToInsertActivityLogEntry(Start start) {
        return "INSERT INTO " + Config.getConfig(start).getActivityLogTable()
                + " (app_id, tenant_id, recipe_user_id, primary_or_recipe_user_id, event_type, status,"
                + " auth_principal, identifier, created_at, payload)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    private static PreparedStatementValueSetter activityLogEntrySetter(TenantIdentifier tenantIdentifier,
                                                                       AuditLogEvent event) {
        return pst -> {
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
        };
    }

    public static void createActivityLogEntry(Start start, TenantIdentifier tenantIdentifier, AuditLogEvent event)
            throws SQLException, StorageQueryException {
        update(start, getQueryToInsertActivityLogEntry(start), activityLogEntrySetter(tenantIdentifier, event));
    }

    /**
     * Same insert as {@link #createActivityLogEntry}, but on the caller's transaction connection, so the
     * entry commits or rolls back atomically with the surrounding mutation.
     */
    public static void createActivityLogEntry_Transaction(Connection con, Start start,
                                                          TenantIdentifier tenantIdentifier, AuditLogEvent event)
            throws SQLException, StorageQueryException {
        update(con, getQueryToInsertActivityLogEntry(start), activityLogEntrySetter(tenantIdentifier, event));
    }

    /**
     * Cheap existence check for rollup-relevant activity newer than {@code sinceMillis} — the rows the
     * last-active rollup would fold or reconcile (the {@code LastActiveFoldEvents} set, which includes
     * {@code account_linking}, the reconcile trigger). Storage-wide, no app predicate; lets the rollup cron
     * skip work when there is nothing new.
     */
    public static boolean hasUnfoldedActivitySince(Start start, long sinceMillis)
            throws SQLException, StorageQueryException {
        String QUERY = "SELECT EXISTS (SELECT 1 FROM " + Config.getConfig(start).getActivityLogTable()
                + " WHERE event_type IN (" + LastActiveFoldEvents.sqlInList() + ") AND created_at > ?)"
                + " AS has_activity";
        return execute(start, QUERY, pst -> pst.setLong(1, sinceMillis), result -> {
            if (result.next()) {
                return result.getBoolean("has_activity");
            }
            return false;
        });
    }

    /**
     * App-scoped, window-bounded read of the activity log so callers can fold lifecycle events in Java.
     * Returns the {@code appIdentifier} events (across all its tenants, {@code tenant_id} preserved) whose
     * {@code event_type} is in {@code eventTypes} and whose {@code created_at} lies in
     * {@code (fromExclusiveMillis, toInclusiveMillis]}, ordered by {@code created_at} ascending and capped
     * at {@code limit} rows in SQL (so {@code cap + 1} detects an over-cap window). Payload is returned as
     * the stored text (null stays null). See {@code ActivityLogStorage#getActivityLogEntriesForApp}.
     */
    public static List<AuditLogEvent> getActivityLogEntriesForApp(Start start, AppIdentifier appIdentifier,
                                                                  Set<String> eventTypes, long fromExclusiveMillis,
                                                                  long toInclusiveMillis, int limit)
            throws SQLException, StorageQueryException {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException("eventTypes must be non-empty");
        }
        String QUERY = "SELECT app_id, tenant_id, recipe_user_id, primary_or_recipe_user_id, event_type, status,"
                + " auth_principal, identifier, created_at, payload FROM "
                + Config.getConfig(start).getActivityLogTable()
                + " WHERE app_id = ? AND event_type IN (" + Utils.generateCommaSeperatedQuestionMarks(eventTypes.size())
                + ") AND created_at > ? AND created_at <= ? ORDER BY created_at ASC LIMIT ?";
        return execute(start, QUERY, pst -> {
            int index = 1;
            pst.setString(index++, appIdentifier.getAppId());
            for (String eventType : eventTypes) {
                pst.setString(index++, eventType);
            }
            pst.setLong(index++, fromExclusiveMillis);
            pst.setLong(index++, toInclusiveMillis);
            pst.setInt(index, limit);
        }, result -> {
            List<AuditLogEvent> events = new ArrayList<>();
            while (result.next()) {
                events.add(auditLogEventFromRow(result));
            }
            return events;
        });
    }

    private static AuditLogEvent auditLogEventFromRow(ResultSet result) throws SQLException {
        return new AuditLogEvent(
                result.getString("app_id"),
                result.getString("tenant_id"),
                result.getString("recipe_user_id"),
                result.getString("primary_or_recipe_user_id"),
                result.getString("event_type"),
                result.getString("status"),
                result.getString("auth_principal"),
                result.getString("identifier"),
                result.getLong("created_at"),
                result.getString("payload"));
    }
}
