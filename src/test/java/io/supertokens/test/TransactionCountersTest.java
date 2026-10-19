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

import io.supertokens.ProcessState;
import io.supertokens.inmemorydb.Start;
import io.supertokens.inmemorydb.TransactionCounters;
import io.supertokens.inmemorydb.TransactionCounters.Snapshot;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.webserver.WebserverAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The in-memory storage's test-only transaction counters (see {@link TransactionCounters}) must count what the
 * later commit-reduction work is judged by: transactions opened, commits that make a write durable, and writes
 * made outside any transaction.
 */
public class TransactionCountersTest {

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void countsTransactionsWritingCommitsAndAutocommitWrites() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());
        if (!(storage instanceof Start)) {
            // The counters exist only in the in-memory storage.
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
            return;
        }
        Start inMem = (Start) storage;
        TenantIdentifier tenant = process.getAppForTesting();

        // A read-only transaction opens a transaction but commits no write.
        TransactionCounters.reset();
        inMem.startTransaction(con -> inMem.getKeyValue_Transaction(tenant, con, "k"));
        assertEquals(Snapshot.of(1, 0, 0), TransactionCounters.snapshot());

        // A write committed explicitly is one writing commit, however many writes it holds.
        TransactionCounters.reset();
        inMem.startTransaction(con -> {
            inMem.setKeyValue_Transaction(tenant, con, "k", new KeyValueInfo("v1"));
            inMem.setKeyValue_Transaction(tenant, con, "k2", new KeyValueInfo("v1"));
            inMem.commitTransaction(con);
            return null;
        });
        assertEquals(Snapshot.of(1, 1, 0), TransactionCounters.snapshot());

        // A write left uncommitted by the logic is committed when the transaction's connection returns to
        // auto-commit, so it is a writing commit too.
        TransactionCounters.reset();
        inMem.startTransaction(con -> {
            inMem.setKeyValue_Transaction(tenant, con, "k", new KeyValueInfo("v2"));
            return null;
        });
        assertEquals(Snapshot.of(1, 1, 0), TransactionCounters.snapshot());
        assertEquals("v2", inMem.getKeyValue(tenant, "k").value);

        // Two commits with a write each are two writing commits; a trailing commit with no write is not one.
        TransactionCounters.reset();
        inMem.startTransaction(con -> {
            inMem.setKeyValue_Transaction(tenant, con, "k", new KeyValueInfo("v3"));
            inMem.commitTransaction(con);
            inMem.setKeyValue_Transaction(tenant, con, "k", new KeyValueInfo("v4"));
            inMem.commitTransaction(con);
            inMem.commitTransaction(con);
            return null;
        });
        assertEquals(Snapshot.of(1, 2, 0), TransactionCounters.snapshot());

        // A write that is rolled back commits nothing.
        TransactionCounters.reset();
        try {
            inMem.startTransaction(con -> {
                inMem.setKeyValue_Transaction(tenant, con, "k", new KeyValueInfo("rolled-back"));
                throw new StorageTransactionLogicException(new Exception("roll back"));
            });
            fail();
        } catch (StorageTransactionLogicException expected) {
        }
        assertEquals(Snapshot.of(1, 0, 0), TransactionCounters.snapshot());
        assertEquals("v4", inMem.getKeyValue(tenant, "k").value);

        // A write outside any transaction is an auto-commit write; reads outside a transaction count nothing.
        TransactionCounters.reset();
        inMem.setKeyValue(tenant, "k", new KeyValueInfo("v5"));
        inMem.getKeyValue(tenant, "k");
        assertEquals(Snapshot.of(0, 0, 1), TransactionCounters.snapshot());

        // Counts are per thread: work on another thread does not show up on this one.
        TransactionCounters.reset();
        AtomicReference<Snapshot> otherThread = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                inMem.setKeyValue(tenant, "k", new KeyValueInfo("v6"));
                otherThread.set(TransactionCounters.snapshot());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        t.join();
        assertEquals(Snapshot.of(0, 0, 1), otherThread.get());
        assertEquals(Snapshot.of(0, 0, 0), TransactionCounters.snapshot());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void captureRequestReturnsTheCountsOfOneHttpRequest() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (!(StorageLayer.getStorage(process.getProcess()) instanceof Start)) {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
            return;
        }

        // /hello does one storage read and no transaction.
        TransactionCountCapture.Captured<String> hello = TransactionCountCapture.captureRequest(
                () -> HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/hello",
                        null, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), null));
        assertEquals("Hello", hello.response.trim());
        assertEquals(Snapshot.of(0, 0, 0), hello.counts);
        assertTrue(hello.counts.request, hello.counts.request.startsWith("GET /")
                && hello.counts.request.endsWith("/hello"));
        // The capture removes its request observer when it returns, so later requests are not recorded.
        assertNull(WebserverAPI.setRequestObserverForTesting(null));

        // ... and when the request fails.
        try {
            TransactionCountCapture.captureRequest(() -> {
                throw new IllegalStateException("request failed");
            });
            fail();
        } catch (IllegalStateException e) {
            assertEquals("request failed", e.getMessage());
        }
        assertNull(WebserverAPI.setRequestObserverForTesting(null));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
