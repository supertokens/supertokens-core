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
     * Holds user lock data fetched from app_id_to_user_id table.
     */
    private static class UserLockData {
        final String primaryOrRecipeUserId;  // empty string if not linked/primary, null if not found
        final String recipeId;

        UserLockData(String primaryOrRecipeUserId, String recipeId) {
            this.primaryOrRecipeUserId = primaryOrRecipeUserId;
            this.recipeId = recipeId;
        }
    }

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

        // Step 1: Read user lock data for all users
        Map<String, UserLockData> userToLockData = new HashMap<>();
        Set<String> allIdsToLock = new TreeSet<>();  // TreeSet for consistent ordering

        for (String userId : userIds) {
            allIdsToLock.add(userId);
            UserLockData lockData = readUserLockData(start, con, appIdentifier, userId);
            if (lockData == null) {
                throw new UserNotFoundForLockingException(userId);
            }
            userToLockData.put(userId, lockData);
            // Empty string means user exists but is not primary/linked - don't add as additional lock target
            // Non-empty and different from userId means user is linked to a primary
            if (!lockData.primaryOrRecipeUserId.isEmpty() && !lockData.primaryOrRecipeUserId.equals(userId)) {
                allIdsToLock.add(lockData.primaryOrRecipeUserId);
            }
        }

        // Step 2: "Lock" all users in consistent alphabetical order
        // Note: SQLite doesn't support FOR UPDATE, this just verifies users exist
        for (String id : allIdsToLock) {
            lockSingleUser(start, con, appIdentifier, id);
        }

        // Step 3: Re-read user data (may have changed in concurrent scenario)
        List<LockedUser> result = new ArrayList<>();
        for (String userId : userIds) {
            UserLockData confirmedData = readUserLockData(start, con, appIdentifier, userId);
            if (confirmedData == null) {
                throw new UserNotFoundForLockingException(userId);
            }

            // Convert empty string to null for LockedUserImpl (user is not primary or linked)
            String primaryUserIdForLock = confirmedData.primaryOrRecipeUserId.isEmpty() ? null : confirmedData.primaryOrRecipeUserId;

            // If primary changed and is not null/empty, we need to "lock" the new primary too
            if (primaryUserIdForLock != null && !allIdsToLock.contains(primaryUserIdForLock)) {
                lockSingleUser(start, con, appIdentifier, primaryUserIdForLock);
            }

            result.add(new LockedUserImpl(userId, confirmedData.recipeId, primaryUserIdForLock, con));
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
     * Uses app_id_to_user_id table because users may not be in all_auth_recipe_users
     * if they've been removed from all tenants.
     * Note: SQLite doesn't support FOR UPDATE, so this is just a SELECT.
     */
    private static void lockSingleUser(Start start, Connection con, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException, UserNotFoundForLockingException {

        // SQLite doesn't support FOR UPDATE, so we just do a regular SELECT
        String QUERY = "SELECT user_id FROM " + Config.getConfig(start).getAppIdToUserIdTable()
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
     * Reads user lock data (primary_or_recipe_user_id and recipe_id) for a user (without locking).
     * Uses app_id_to_user_id table because users may not be in all_auth_recipe_users
     * if they've been removed from all tenants.
     * Returns null if user doesn't exist.
     * Returns UserLockData with empty string primaryOrRecipeUserId if user exists but is not primary or linked.
     * Returns UserLockData with the primary_or_recipe_user_id if user is primary or linked.
     */
    private static UserLockData readUserLockData(Start start, Connection con, AppIdentifier appIdentifier, String userId)
            throws SQLException, StorageQueryException {

        String QUERY = "SELECT primary_or_recipe_user_id, is_linked_or_is_a_primary_user, recipe_id FROM " + Config.getConfig(start).getAppIdToUserIdTable()
            + " WHERE app_id = ? AND user_id = ?";

        return execute(con, QUERY, pst -> {
            pst.setString(1, appIdentifier.getAppId());
            pst.setString(2, userId);
        }, rs -> {
            if (rs.next()) {
                String recipeId = rs.getString("recipe_id");
                boolean isLinkedOrPrimary = rs.getBoolean("is_linked_or_is_a_primary_user");
                if (isLinkedOrPrimary) {
                    return new UserLockData(rs.getString("primary_or_recipe_user_id"), recipeId);
                } else {
                    // User exists but is not primary or linked - return empty string to distinguish from not found
                    return new UserLockData("", recipeId);
                }
            }
            return null;
        });
    }
}
