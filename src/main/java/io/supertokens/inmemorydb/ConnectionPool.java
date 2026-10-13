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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    // exhaustion pattern behind the OAuth-refresh regression. Keyed per pool; active only under
    // Start.isTesting. While the pre-existing instances are cleaned up it WARNS by default (so the suite stays
    // green): each offending call site is recorded and printed once to stdout, which the gradle test logging
    // shows in the CI log (stderr is not shown). Flip throwOnNestedAcquisition to fail fast — used by the
    // guard's own regression test, and intended to become the default once the cleanup lands.
    private static final ThreadLocal<Map<String, Integer>> TXN_DEPTH_BY_POOL =
            ThreadLocal.withInitial(HashMap::new);

    private static volatile boolean throwOnNestedAcquisition = false;

    // Warn-mode record: every distinct call site that borrowed a nested same-pool connection in this JVM.
    private static final Set<String> NESTED_ACQUISITION_SITES = ConcurrentHashMap.newKeySet();

    // Test hook: when true, a nested same-pool acquisition throws instead of only warning.
    public static void setThrowOnNestedAcquisition(boolean value) {
        throwOnNestedAcquisition = value;
    }

    // Test hook: the distinct call sites recorded by the warn-mode guard so far in this JVM.
    public static Set<String> getNestedAcquisitionSites() {
        return Collections.unmodifiableSet(NESTED_ACQUISITION_SITES);
    }

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
        if (depth == null || depth <= 0) {
            return;
        }
        String site = nestedAcquisitionSite();
        String message = "Nested same-pool connection acquisition on pool '" + poolKey(start) + "' at "
                + site + ": a helper borrows a SECOND connection while a startTransaction on"
                + " this pool is open — the hold-and-wait pool-exhaustion (OAuth-refresh deadlock) class. Thread"
                + " the transaction's connection through the helper (use its *_Transaction overload), or resolve"
                + " the value before opening the transaction.";
        if (throwOnNestedAcquisition) {
            throw new IllegalStateException(message);
        }
        // Warn-mode default during the cleanup: record the site and surface it once, without failing the suite.
        if (NESTED_ACQUISITION_SITES.add(site)) {
            System.out.println("[nested-conn-guard][WARN] " + message);
        }
    }

    // The nearest application frame that borrowed the second connection — for locating the site in warn-mode.
    private static String nestedAcquisitionSite() {
        for (StackTraceElement f : Thread.currentThread().getStackTrace()) {
            String cn = f.getClassName();
            if (!cn.startsWith("io.supertokens.")) {
                continue;
            }
            if (cn.endsWith(".ConnectionPool") || cn.endsWith(".QueryExecutorTemplate")
                    || (cn.endsWith(".Start") && f.getMethodName().startsWith("startTransaction"))) {
                continue;
            }
            return cn.substring(cn.lastIndexOf('.') + 1) + "." + f.getMethodName()
                    + "(" + f.getFileName() + ":" + f.getLineNumber() + ")";
        }
        return "unknown";
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
