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

import org.jetbrains.annotations.TestOnly;
import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionPool extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.inmemorydb.ConnectionPool";
    private static String URL = "jdbc:sqlite:file::memory:?cache=shared";

    // ── Test-only connection-borrow instrumentation (PLAN-017 / CORE-2) ──────────────────────────────
    // Lets a test assert that a code path borrows at most one pool connection at a time on any single
    // thread, i.e. it never opens a nested borrow while already holding a (transaction) connection. This
    // mirrors what a HikariCP maximumPoolSize=1 pool would surface in production, but deterministically and
    // without a real pool/timeout. Nesting is tracked PER THREAD so unrelated background work (crons on
    // other threads borrowing a single, non-nested connection) cannot perturb the measurement. All of this
    // is inert unless a test calls startBorrowTracking(); normal runs pay a single volatile read.
    private static volatile boolean borrowTracking = false;
    private static volatile boolean enforceSingleConnection = false;
    private static final ThreadLocal<Integer> threadHeld = ThreadLocal.withInitial(() -> 0);
    private static volatile int peakThreadHeld = 0;
    private static final AtomicInteger totalAcquired = new AtomicInteger(0);

    @TestOnly
    public static synchronized void startBorrowTracking(boolean enforceSingleConnectionPerThread) {
        borrowTracking = true;
        enforceSingleConnection = enforceSingleConnectionPerThread;
        threadHeld.remove();
        peakThreadHeld = 0;
        totalAcquired.set(0);
    }

    @TestOnly
    public static synchronized void stopBorrowTracking() {
        borrowTracking = false;
        enforceSingleConnection = false;
    }

    /** Highest number of connections a single thread held simultaneously while tracking was on. */
    @TestOnly
    public static int getPeakConcurrentConnectionsPerThread() {
        return peakThreadHeld;
    }

    /** Total connections acquired (across all threads) while tracking was on. */
    @TestOnly
    public static int getTotalConnectionsAcquired() {
        return totalAcquired.get();
    }

    private static synchronized void bumpPeak(int held) {
        if (held > peakThreadHeld) {
            peakThreadHeld = held;
        }
    }

    // Called at the start of every getConnection() while tracking. With enforcement on, throws instead of
    // handing out a second concurrent connection to the same thread — reproducing pool exhaustion.
    private static void onAcquire() throws SQLException {
        if (!borrowTracking) {
            return;
        }
        int held = threadHeld.get();
        if (enforceSingleConnection && held >= 1) {
            throw new SQLException("Simulated pool exhaustion (maximumPoolSize=1): a connection was requested "
                    + "while this thread already holds one — nested borrow detected (PLAN-017 CORE-2)");
        }
        threadHeld.set(held + 1);
        totalAcquired.incrementAndGet();
        bumpPeak(held + 1);
    }

    // Called from ConnectionWithLocks.close() so a released connection stops counting against the thread.
    static void onRelease() {
        if (!borrowTracking) {
            return;
        }
        int held = threadHeld.get();
        if (held > 0) {
            threadHeld.set(held - 1);
        }
    }

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
    // Start.isTesting. It THROWS by default, so a new instance fails the test that introduces it. Setting
    // throwOnNestedAcquisition to false is a test-only opt-out to warn mode: each offending call site is
    // recorded and printed once to stdout, which the gradle test logging shows in the CI log (stderr is not shown).
    private static final ThreadLocal<Map<String, Integer>> TXN_DEPTH_BY_POOL =
            ThreadLocal.withInitial(HashMap::new);

    private static volatile boolean throwOnNestedAcquisition = true;

    // Warn-mode record: every distinct call site that borrowed a nested same-pool connection in this JVM.
    private static final Set<String> NESTED_ACQUISITION_SITES = ConcurrentHashMap.newKeySet();

    // Test hook: when true (the default), a nested same-pool acquisition throws; false opts out to warn mode.
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
        // Warn-mode opt-out: record the site and surface it once, without failing the test.
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
        onAcquire(); // test-only borrow tracking; throws under size-1 enforcement before any real connection is opened
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
