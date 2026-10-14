/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

// ── Test-only per-thread transaction / commit counters for the in-memory storage ─────────────────────
// Lets a test prove how many transactions and durable commits one call makes. Counts are kept per thread
// (like the nested-acquisition guard's state in ConnectionPool), so background crons never leak into the
// numbers, and are recorded only under Start.isTesting:
// - transactionsOpened: transactions opened by Start.startTransaction (startAuditedTransaction runs on it);
// - writingCommits: commits of a transaction connection that ran at least one write since its last commit;
// - autocommitWrites: write statements run on an auto-commit connection, i.e. outside any transaction.
// Writes are the statements run through QueryExecutorTemplate.update / executeBatch.
//
// A direct call is measured on the calling thread with reset() + snapshot(). An HTTP API call runs on a
// webserver thread, so WebserverAPI brackets each request with beginRequest / endRequest, and
// captureRequest() returns the counts of the one request a test sends.
public final class TransactionCounters {

    private static final long REQUEST_WAIT_MS = 10_000;

    private static final ThreadLocal<int[]> COUNTS = ThreadLocal.withInitial(() -> new int[3]);

    private static final int TRANSACTIONS_OPENED = 0;
    private static final int WRITING_COMMITS = 1;
    private static final int AUTOCOMMIT_WRITES = 2;

    // Counts of the requests completed since the last captureRequest started; guarded by itself.
    private static final List<Snapshot> COMPLETED_REQUESTS = new ArrayList<>();

    private TransactionCounters() {
    }

    // ── recording hooks, called by the in-memory storage ──

    static void transactionOpened() {
        increment(TRANSACTIONS_OPENED);
    }

    static void writingCommit() {
        increment(WRITING_COMMITS);
    }

    // Called for every write statement: on a transaction connection it marks the transaction as writing, on an
    // auto-commit connection it is a write outside any transaction.
    static void write(Connection con) throws SQLException {
        if (!Start.isTesting) {
            return;
        }
        if (con.getAutoCommit()) {
            increment(AUTOCOMMIT_WRITES);
        } else if (con instanceof ConnectionWithLocks) {
            ((ConnectionWithLocks) con).markPendingWrite();
        }
    }

    private static void increment(int counter) {
        if (!Start.isTesting) {
            return;
        }
        COUNTS.get()[counter]++;
    }

    // ── request bracketing, called by WebserverAPI.service ──

    public static void beginRequest() {
        if (!Start.isTesting) {
            return;
        }
        COUNTS.remove();
    }

    public static void endRequest(String request) {
        if (!Start.isTesting) {
            return;
        }
        Snapshot snapshot = currentSnapshot(request);
        COUNTS.remove();
        synchronized (COMPLETED_REQUESTS) {
            COMPLETED_REQUESTS.add(snapshot);
            COMPLETED_REQUESTS.notifyAll();
        }
    }

    // ── test API ──

    // Clears the calling thread's counts.
    @TestOnly
    public static void reset() {
        COUNTS.remove();
    }

    // The calling thread's counts since the last reset().
    @TestOnly
    public static Snapshot snapshot() {
        return currentSnapshot(null);
    }

    // Runs one HTTP request against this process's webserver and returns that request's counts. Waits for the
    // request to finish on the server side, since the client can receive the response before the server's
    // service() returns. Fails unless exactly one request completed.
    @TestOnly
    public static <T> Captured<T> captureRequest(Callable<T> request) throws Exception {
        synchronized (COMPLETED_REQUESTS) {
            COMPLETED_REQUESTS.clear();
        }
        T result = request.call();
        synchronized (COMPLETED_REQUESTS) {
            long deadline = System.currentTimeMillis() + REQUEST_WAIT_MS;
            while (COMPLETED_REQUESTS.isEmpty()) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    throw new IllegalStateException("no request completed on the webserver within "
                            + REQUEST_WAIT_MS + " ms");
                }
                COMPLETED_REQUESTS.wait(left);
            }
            if (COMPLETED_REQUESTS.size() != 1) {
                throw new IllegalStateException("expected exactly one request, got " + COMPLETED_REQUESTS);
            }
            return new Captured<>(result, COMPLETED_REQUESTS.get(0));
        }
    }

    private static Snapshot currentSnapshot(String request) {
        int[] counts = COUNTS.get();
        return new Snapshot(request, counts[TRANSACTIONS_OPENED], counts[WRITING_COMMITS],
                counts[AUTOCOMMIT_WRITES]);
    }

    public static final class Snapshot {
        public final String request;
        public final int transactionsOpened;
        public final int writingCommits;
        public final int autocommitWrites;

        public Snapshot(String request, int transactionsOpened, int writingCommits, int autocommitWrites) {
            this.request = request;
            this.transactionsOpened = transactionsOpened;
            this.writingCommits = writingCommits;
            this.autocommitWrites = autocommitWrites;
        }

        // Counts only, so a test can compare against an expected value built without a request label.
        public static Snapshot of(int transactionsOpened, int writingCommits, int autocommitWrites) {
            return new Snapshot(null, transactionsOpened, writingCommits, autocommitWrites);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Snapshot)) {
                return false;
            }
            Snapshot other = (Snapshot) o;
            return transactionsOpened == other.transactionsOpened && writingCommits == other.writingCommits
                    && autocommitWrites == other.autocommitWrites;
        }

        @Override
        public int hashCode() {
            return Objects.hash(transactionsOpened, writingCommits, autocommitWrites);
        }

        @Override
        public String toString() {
            return (request == null ? "" : request + " ") + "{transactionsOpened=" + transactionsOpened
                    + ", writingCommits=" + writingCommits + ", autocommitWrites=" + autocommitWrites + "}";
        }
    }

    public static final class Captured<T> {
        public final T response;
        public final Snapshot counts;

        Captured(T response, Snapshot counts) {
            this.response = response;
            this.counts = counts;
        }
    }
}
