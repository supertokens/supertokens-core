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

package io.supertokens.test;

import io.supertokens.inmemorydb.TransactionCounters;
import io.supertokens.inmemorydb.TransactionCounters.Snapshot;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Measures the in-memory storage's {@link TransactionCounters} for one HTTP request. An API call runs on a webserver
 * thread, so for the duration of the call this installs a {@link WebserverAPI.RequestObserver} that resets that
 * thread's counts when a request starts and snapshots them when it ends. Nothing is recorded outside a capture.
 */
public final class TransactionCountCapture {

    private static final long REQUEST_WAIT_MS = 10_000;

    private TransactionCountCapture() {
    }

    // Runs one HTTP request against this process's webserver and returns that request's counts. Waits for the
    // request to finish on the server side, since the client can receive the response before the server's
    // service() returns. Fails unless exactly one request completed.
    public static <T> Captured<T> captureRequest(Callable<T> request) throws Exception {
        List<Snapshot> completed = new ArrayList<>();
        WebserverAPI.RequestObserver observer = new WebserverAPI.RequestObserver() {
            @Override
            public void onRequestStart(HttpServletRequest req) {
                TransactionCounters.reset();
            }

            @Override
            public void onRequestEnd(HttpServletRequest req) {
                Snapshot counts = TransactionCounters.snapshot();
                TransactionCounters.reset();
                synchronized (completed) {
                    completed.add(new Snapshot(req.getMethod() + " " + req.getRequestURI(),
                            counts.transactionsOpened, counts.writingCommits, counts.autocommitWrites));
                    completed.notifyAll();
                }
            }
        };
        WebserverAPI.setRequestObserverForTesting(observer);
        try {
            T result = request.call();
            synchronized (completed) {
                long deadline = System.currentTimeMillis() + REQUEST_WAIT_MS;
                while (completed.isEmpty()) {
                    long left = deadline - System.currentTimeMillis();
                    if (left <= 0) {
                        throw new IllegalStateException("no request completed on the webserver within "
                                + REQUEST_WAIT_MS + " ms");
                    }
                    completed.wait(left);
                }
                if (completed.size() != 1) {
                    throw new IllegalStateException("expected exactly one request, got " + completed);
                }
                return new Captured<>(result, completed.get(0));
            }
        } finally {
            WebserverAPI.setRequestObserverForTesting(null);
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
