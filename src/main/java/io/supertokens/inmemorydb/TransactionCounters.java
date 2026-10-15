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
import java.util.Objects;

// ── Test-only per-thread transaction / commit counters for the in-memory storage ─────────────────────
// Lets a test prove how many transactions and durable commits one call makes. Counts are kept per thread
// (like the nested-acquisition guard's state in ConnectionPool), so background crons never leak into the
// numbers, and are recorded only under Start.isTesting:
// - transactionsOpened: transactions opened by Start.startTransaction (startAuditedTransaction runs on it);
// - writingCommits: commits of a transaction connection that ran at least one write since its last commit;
// - autocommitWrites: write statements run on an auto-commit connection, i.e. outside any transaction.
// Writes are the statements run through QueryExecutorTemplate.update / executeBatch.
//
// A call is measured on the thread that runs it with reset() + snapshot(). An HTTP API call runs on a webserver
// thread, so tests measure it through a WebserverAPI request observer that does the same around the request.
public final class TransactionCounters {

    private static final ThreadLocal<int[]> COUNTS = ThreadLocal.withInitial(() -> new int[3]);

    private static final int TRANSACTIONS_OPENED = 0;
    private static final int WRITING_COMMITS = 1;
    private static final int AUTOCOMMIT_WRITES = 2;

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

    // ── test API ──

    // Clears the calling thread's counts.
    @TestOnly
    public static void reset() {
        COUNTS.remove();
    }

    // The calling thread's counts since the last reset().
    @TestOnly
    public static Snapshot snapshot() {
        int[] counts = COUNTS.get();
        return Snapshot.of(counts[TRANSACTIONS_OPENED], counts[WRITING_COMMITS], counts[AUTOCOMMIT_WRITES]);
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
}
