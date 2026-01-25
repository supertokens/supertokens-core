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

package io.supertokens.inmemorydb;

import io.supertokens.pluginInterface.useridmapping.LockedUser;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.lang.ref.WeakReference;

/**
 * InMemoryDB (SQLite) implementation of LockedUser.
 * Tracks the connection to validate the lock is still active.
 *
 * Note: SQLite doesn't support FOR UPDATE, but since InMemoryDB is single-threaded,
 * the lock semantics are automatically serialized.
 */
public class LockedUserImpl implements LockedUser {

    @Nonnull
    private final String recipeUserId;

    @Nullable
    private final String primaryUserId;

    // WeakReference so we don't prevent connection from being garbage collected
    private final WeakReference<Connection> connectionRef;

    public LockedUserImpl(@Nonnull String recipeUserId, @Nullable String primaryUserId,
                          @Nonnull Connection connection) {
        this.recipeUserId = recipeUserId;
        this.primaryUserId = primaryUserId;
        this.connectionRef = new WeakReference<>(connection);
    }

    @Override
    @Nonnull
    public String getRecipeUserId() {
        return recipeUserId;
    }

    @Override
    @Nullable
    public String getPrimaryUserId() {
        return primaryUserId;
    }

    @Override
    public boolean isValidForConnection(Object connection) {
        Connection originalCon = connectionRef.get();
        if (originalCon == null) {
            return false;
        }
        // Check that the provided connection is the same instance as the one used to acquire the lock
        if (originalCon != connection) {
            return false;
        }
        try {
            return !originalCon.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String toString() {
        Connection con = connectionRef.get();
        boolean connectionAlive = false;
        try {
            connectionAlive = con != null && !con.isClosed();
        } catch (Exception ignored) {
        }
        return "LockedUser{" +
               "recipeUserId='" + recipeUserId + '\'' +
               ", primaryUserId='" + primaryUserId + '\'' +
               ", isLinked=" + isLinked() +
               ", isPrimary=" + isPrimary() +
               ", connectionAlive=" + connectionAlive +
               '}';
    }
}
