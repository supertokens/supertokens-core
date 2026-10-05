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

    public static Connection getConnection(Start start) throws SQLException {
        if (!start.enabled) {
            throw new SQLException("Storage layer disabled");
        }
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
