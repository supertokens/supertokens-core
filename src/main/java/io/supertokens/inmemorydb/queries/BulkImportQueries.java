/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;

import static io.supertokens.inmemorydb.PreparedStatementValueSetter.NO_OP_SETTER;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;

import java.sql.SQLException;
import java.util.ArrayList;

import io.supertokens.inmemorydb.Start;

public class BulkImportQueries {
    static String getQueryToCreateBulkImportUsersTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getBulkImportUsersTable() + " ("
                + "id CHAR(36) PRIMARY KEY,"
                + "raw_data TEXT NOT NULL,"
                + "status VARCHAR(128) NOT NULL DEFAULT 'NEW',"
                + "error_msg TEXT,"
                + "created_at TIMESTAMP DEFAULT (strftime('%s', 'now')),"
                + "updated_at TIMESTAMP DEFAULT (strftime('%s', 'now'))"
                + " );";
    }

    public static String getQueryToCreateStatusUpdatedAtIndex(Start start) {
        return "CREATE INDEX IF NOT EXISTS bulk_import_users_status_updated_at_index ON "
                + Config.getConfig(start).getBulkImportUsersTable() + " (status, updated_at)";
    }

        public static void insertBulkImportUsers(Start start, ArrayList<BulkImportUser> users)
            throws SQLException, StorageQueryException {
        StringBuilder queryBuilder = new StringBuilder(
                "INSERT INTO " + Config.getConfig(start).getBulkImportUsersTable() + " (id, raw_data) VALUES ");
        for (BulkImportUser user : users) {
            queryBuilder.append("('")
                    .append(user.id)
                    .append("', '")
                    .append(user.toString())
                    .append("')");

            if (user != users.get(users.size() - 1)) {
                queryBuilder.append(",");
            }
        }
        queryBuilder.append(";");
        update(start, queryBuilder.toString(), NO_OP_SETTER);
    }
}

