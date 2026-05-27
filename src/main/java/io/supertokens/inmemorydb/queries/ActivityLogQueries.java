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

/**
 * In-memory (SQLite) variant of the activity log used by tests.
 *
 * SQLite has no declarative partitioning, BRIN, JSONB or identity columns, so this is a plain
 * non-partitioned table with a btree index on created_at and TEXT for the payload. The Postgres
 * plugin carries the production shape (see {@code postgresql.queries.ActivityLogQueries}).
 */
public class ActivityLogQueries {

    static String getQueryToCreateActivityLogTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getActivityLogTable() + " ("
                + "id INTEGER,"
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
                + " );";
    }

    static String getQueryToCreateCreatedAtIndex(Start start) {
        return "CREATE INDEX IF NOT EXISTS activity_log_created_at_index ON "
                + Config.getConfig(start).getActivityLogTable() + "(created_at);";
    }
}
