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

import io.supertokens.inmemorydb.LockedUserImpl;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.config.Config;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.useridmapping.LockedUser;
import io.supertokens.pluginInterface.useridmapping.LockedUserPair;
import io.supertokens.pluginInterface.useridmapping.UserNotFoundForLockingException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import static io.supertokens.inmemorydb.QueryExecutorTemplate.execute;

/**
 * User locking queries for InMemoryDB (SQLite).
 *
 * Note: SQLite doesn't support FOR UPDATE locking, but since InMemoryDB is single-threaded,
 * the lock semantics are automatically serialized. The queries still follow the same pattern
 * as PostgreSQL for consistency.
 */
public class UserLockingQueries {

    /**
     * Locks a single user and returns LockedUser.
     * Also locks the primary user if the user is linked.
     */
    public static LockedUser lockUser(Start start, Connection con, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException, UserNotFoundForLockingException {
        return lockUsers(start, con, appIdentifier, List.of(userId)).get(0);
    }

    /**
     * Locks multiple users with deadlock prevention (consistent ordering).
     *
     * Note: FOR UPDATE is not used in SQLite as it's not supported,
     * but ordering is still maintained for consistency with PostgreSQL implementation.
     */
    public static List<LockedUser> lockUsers(Start start, Connection con, AppIdentifier appIdentifier,
                                              List<String> userIds)
            throws SQLException, StorageQueryException, UserNotFoundForLockingException {

        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Step 1: Read primary user mappings for all users
        Map<String, String> userToPrimary = new HashMap<>();
        Set<String> allIdsToLock = new TreeSet<>();  // TreeSet for consistent ordering

        for (String userId : userIds) {
            allIdsToLock.add(userId);
            String primaryId = readPrimaryUserId(start, con, appIdentifier, userId);
            if (primaryId == null) {
                throw new UserNotFoundForLockingException(userId);
            }
            userToPrimary.put(userId, primaryId);
            if (!primaryId.equals(userId)) {
                allIdsToLock.add(primaryId);
            }
        }

        // Step 2: "Lock" all users in consistent alphabetical order
        // Note: SQLite doesn't support FOR UPDATE, this just verifies users exist
        for (String id : allIdsToLock) {
            lockSingleUser(start, con, appIdentifier, id);
        }

        // Step 3: Re-read primary mappings (may have changed in concurrent scenario)
        List<LockedUser> result = new ArrayList<>();
        for (String userId : userIds) {
            String confirmedPrimary = readPrimaryUserId(start, con, appIdentifier, userId);
            if (confirmedPrimary == null) {
                throw new UserNotFoundForLockingException(userId);
            }

            // If primary changed, we need to "lock" the new primary too
            if (!allIdsToLock.contains(confirmedPrimary)) {
                lockSingleUser(start, con, appIdentifier, confirmedPrimary);
            }

            result.add(new LockedUserImpl(userId, confirmedPrimary, con));
        }

        return result;
    }

    /**
     * Convenience method for locking two users for linking operations.
     */
    public static LockedUserPair lockUsersForLinking(Start start, Connection con, AppIdentifier appIdentifier,
                                                      String recipeUserId, String primaryUserId)
            throws SQLException, StorageQueryException, UserNotFoundForLockingException {

        List<LockedUser> locked = lockUsers(start, con, appIdentifier, List.of(recipeUserId, primaryUserId));
        return new LockedUserPair(locked.get(0), locked.get(1));
    }

    /**
     * Verifies a user exists (simulates lock acquisition).
     * Note: SQLite doesn't support FOR UPDATE, so this is just a SELECT.
     */
    private static void lockSingleUser(Start start, Connection con, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException, UserNotFoundForLockingException {

        // SQLite doesn't support FOR UPDATE, so we just do a regular SELECT
        String QUERY = "SELECT user_id FROM " + Config.getConfig(start).getUsersTable()
            + " WHERE app_id = ? AND user_id = ?";

        Boolean found = execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, rs -> rs.next());

        if (!found) {
            throw new UserNotFoundForLockingException(userId);
        }
    }

    /**
     * Reads the primary_or_recipe_user_id for a user (without locking).
     */
    private static String readPrimaryUserId(Start start, Connection con, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {

        String QUERY = "SELECT primary_or_recipe_user_id FROM " + Config.getConfig(start).getUsersTable()
            + " WHERE app_id = ? AND user_id = ?";

        return execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, rs -> {
            if (rs.next()) {
                return rs.getString("primary_or_recipe_user_id");
            }
            return null;
        });
    }
}
