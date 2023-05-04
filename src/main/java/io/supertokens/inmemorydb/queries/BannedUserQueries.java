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

import java.sql.SQLException;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;
import static io.supertokens.inmemorydb.QueryExecutorTemplate.update;
import static io.supertokens.inmemorydb.config.Config.getConfig;

public class BannedUserQueries {

    public static String getQueryToCreateBannedUsersTable(Start start) {
        return "CREATE TABLE IF NOT EXISTS " + Config.getConfig(start).getBannedUsersTable() + " ("
                + "user_id VARCHAR(128) NOT NULL,"
                + "banned_at_time BIGINT UNSIGNED NOT NULL,"
                + "PRIMARY KEY (user_id, banned_at_time),"
                + "FOREIGN KEY (user_id) REFERENCES " + Config.getConfig(start).getUsersTable()
                + "(user_id) ON DELETE CASCADE);";
    }

    public static boolean checkUserIsBanned(Start start, String userId) throws SQLException, StorageQueryException {
        String QUERY = "SELECT count(*) FROM " + Config.getConfig(start).getBannedUsersTable()
                + " WHERE user_id = ?";

        return execute(start, QUERY, pst -> pst.setString(1, userId), result -> {
            if (result.next()) {
                return true;
            }
            return false;
        });
    }

    public static void createNewBannedUser(Start start, String userId) throws SQLException, StorageQueryException {
        String QUERY = "INSERT INTO " + Config.getConfig(start).getBannedUsersTable()
                + "(user_id, banned_at) VALUES(?, ?)";

        long now = System.currentTimeMillis();
        update(start, QUERY, pst -> {
            pst.setString(1, userId);
            pst.setLong(2, now);
        });
    }

    public static boolean deleteBannedUser(Start start, String userId)
            throws SQLException, StorageQueryException {
        String QUERY = "DELETE FROM " + getConfig(start).getBannedUsersTable() + " WHERE user_id = ?";

        // store the number of rows updated
        int rowUpdatedCount = update(start, QUERY, pst -> pst.setString(1, userId));

        return rowUpdatedCount > 0;
    }
}
