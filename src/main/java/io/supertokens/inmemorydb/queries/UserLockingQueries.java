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

import io.supertokens.inmemorydb.Utils;

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
     * Locks multiple users (and their primaries) with a single query.
     * ORDER BY user_id maintains consistent ordering with PostgreSQL implementation.
     *
     * Note: SQLite doesn't support FOR UPDATE, so this just reads and verifies users exist.
     */
    public static List<LockedUser> lockUsers(Start start, Connection con, AppIdentifier appIdentifier,
                                              List<String> userIds)
            throws SQLException, StorageQueryException, UserNotFoundForLockingException {

        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }

        String table = Config.getConfig(start).getAppIdToUserIdTable();
        String placeholders = Utils.generateCommaSeperatedQuestionMarks(userIds.size());
        String appId = appIdentifier.getAppId();

        // Two-query approach matching the PostgreSQL optimisation.
        // SQLite doesn't support FOR UPDATE so we just read, but we keep
        // the same two-step structure for consistency.

        // --- Round 1: find primary user IDs for any linked input users ------
        String FIND_PRIMARIES =
                "SELECT primary_or_recipe_user_id FROM " + table
                + " WHERE app_id = ? AND user_id IN (" + placeholders + ")"
                + "   AND is_linked_or_is_a_primary_user = TRUE"
                + "   AND primary_or_recipe_user_id <> user_id";

        Set<String> allIdsToLock = new LinkedHashSet<>(userIds);
        execute(con, FIND_PRIMARIES, pst -> {
            int idx = 1;
            pst.setString(idx++, appId);
            for (String uid : userIds) {
                pst.setString(idx++, uid);
            }
        }, rs -> {
            while (rs.next()) {
                allIdsToLock.add(rs.getString("primary_or_recipe_user_id").trim());
            }
            return null;
        });

        // --- Round 2: read all discovered user IDs --------------------------
        // Post-read validation mirrors the PostgreSQL post-lock validation:
        // if any returned row's primary_or_recipe_user_id is not in the read
        // set, expand and re-read. Capped at 3 attempts.
        // SQLite is single-threaded so this can't actually race, but keeping
        // the structure identical to the PG plugin avoids silent divergence.

        final int MAX_LOCK_EXPANSION_ATTEMPTS = 3;
        Map<String, LockedUser> lockedByUserId = null;

        for (int attempt = 0; attempt < MAX_LOCK_EXPANSION_ATTEMPTS; attempt++) {
            String allPlaceholders = Utils.generateCommaSeperatedQuestionMarks(allIdsToLock.size());
            String READ_QUERY = "SELECT u.user_id, u.primary_or_recipe_user_id, u.is_linked_or_is_a_primary_user, u.recipe_id"
                    + " FROM " + table + " u"
                    + " WHERE u.app_id = ? AND u.user_id IN (" + allPlaceholders + ")"
                    + " ORDER BY u.user_id";

            // Need a copy for the lambda since allIdsToLock may be mutated after
            List<String> idsSnapshot = new ArrayList<>(allIdsToLock);
            lockedByUserId = execute(con, READ_QUERY, pst -> {
                int idx = 1;
                pst.setString(idx++, appId);
                for (String uid : idsSnapshot) {
                    pst.setString(idx++, uid);
                }
            }, rs -> {
                Map<String, LockedUser> map = new HashMap<>();
                while (rs.next()) {
                    String uid = rs.getString("user_id");
                    String recipeId = rs.getString("recipe_id");
                    boolean isLinkedOrPrimary = rs.getBoolean("is_linked_or_is_a_primary_user");
                    String primaryUid = isLinkedOrPrimary ? rs.getString("primary_or_recipe_user_id") : null;
                    map.put(uid, new LockedUserImpl(uid, recipeId, primaryUid, con));
                }
                return map;
            });

            // Post-read validation: check if any user's primary is outside the read set.
            boolean needsExpansion = false;
            for (LockedUser lu : lockedByUserId.values()) {
                String primary = lu.getPrimaryUserId();
                if (primary != null && !allIdsToLock.contains(primary)) {
                    allIdsToLock.add(primary);
                    needsExpansion = true;
                }
            }
            if (!needsExpansion) {
                break;
            }
            if (attempt == MAX_LOCK_EXPANSION_ATTEMPTS - 1) {
                throw new SQLException(
                        "Failed to stabilise user lock set after " + MAX_LOCK_EXPANSION_ATTEMPTS
                        + " attempts — concurrent re-linking is preventing lock convergence");
            }
        }

        // Build result list in the same order as requested, verifying all users were found
        List<LockedUser> result = new ArrayList<>(userIds.size());
        for (String userId : userIds) {
            LockedUser locked = lockedByUserId.get(userId);
            if (locked == null) {
                throw new UserNotFoundForLockingException(userId);
            }
            result.add(locked);
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
}
