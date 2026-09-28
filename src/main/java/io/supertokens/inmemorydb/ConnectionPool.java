/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class ConnectionPool extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.inmemorydb.ConnectionPool";
    private static String URL = "jdbc:sqlite:file::memory:?cache=shared";

    // we use this to keep all the information in memory across requests.
    private Connection alwaysAlive = null;
    private Lock lock = new Lock();

    public ConnectionPool() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        this.alwaysAlive = DriverManager.getConnection(URL, config.toProperties());
    }

    static boolean isAlreadyInitialised(Start start) {
        return getInstance(start) != null;
    }

    static void initPool(Start start, boolean ignored) throws SQLException {
        start.getResourceDistributor()
                .setResource(RESOURCE_KEY, new ConnectionPool());
    }

    // ── Test-only guard: two connections from the same pool in one call chain ──────────────────────
    // Mirrors the guard in the postgresql plugin's ConnectionPool: a call chain that holds one connection
    // (inside a startTransaction) and borrows a SECOND from the same pool is the hold-and-wait pool-
    // exhaustion pattern behind the OAuth-refresh regression. Turned into a located test failure at the
    // nested borrow. Keyed per pool; active only under Start.isTesting.
    private static final ThreadLocal<Map<String, Integer>> TXN_DEPTH_BY_POOL =
            ThreadLocal.withInitial(HashMap::new);

    private static String poolKey(Start start) {
        return start.getUserPoolId() + "~" + start.getConnectionPoolId();
    }

    // Called by Start.startTransactionHelper AFTER it has taken its own connection, wrapping the callback.
    static void enterTransaction(Start start) {
        if (!Start.isTesting) {
            return;
        }
        TXN_DEPTH_BY_POOL.get().merge(poolKey(start), 1, Integer::sum);
    }

    static void exitTransaction(Start start) {
        if (!Start.isTesting) {
            return;
        }
        Map<String, Integer> depths = TXN_DEPTH_BY_POOL.get();
        String key = poolKey(start);
        Integer depth = depths.get(key);
        if (depth == null) {
            return;
        }
        if (depth <= 1) {
            depths.remove(key);
        } else {
            depths.put(key, depth - 1);
        }
    }

    private static void assertNoNestedPoolAcquisition(Start start) {
        if (!Start.isTesting) {
            return;
        }
        Integer depth = TXN_DEPTH_BY_POOL.get().get(poolKey(start));
        if (depth != null && depth > 0) {
            throw new IllegalStateException(
                    "Nested same-pool connection acquisition on pool '" + poolKey(start) + "': this thread is"
                    + " already inside a startTransaction on this pool and is borrowing a SECOND connection from"
                    + " it. Under a small pool this causes hold-and-wait exhaustion (the OAuth-refresh deadlock"
                    + " class). Thread the transaction's connection through the helper (use its *_Transaction"
                    + " overload), or resolve the value before opening the transaction. (Guard active only under"
                    + " Start.isTesting.)");
        }
    }

    public static Connection getConnection(Start start) throws SQLException {
        if (!start.enabled) {
            throw new SQLException("Storage layer disabled");
        }
        assertNoNestedPoolAcquisition(start);
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        return new ConnectionWithLocks(DriverManager.getConnection(URL, config.toProperties()),
                ConnectionPool.getInstance(start));
    }

    private static ConnectionPool getInstance(Start start) {
        return (ConnectionPool) start.getResourceDistributor()
                .getResource(RESOURCE_KEY);
    }

    static void close(Start start) {
        if (getInstance(start) == null) {
            return;
        }
        try {
            getInstance(start).alwaysAlive.close();
        } catch (Exception ignored) {
        }
    }

    public void lock(String key) {
        this.lock.lock(key);
    }

    public void unlock(String key) {
        this.lock.unlock(key);
    }

}
