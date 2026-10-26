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
import io.supertokens.inmemorydb.ConnectionPool;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.sql.Connection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The connection pool must reject a call chain that holds one connection (inside a startTransaction) and
 * borrows a SECOND from the same pool — the hold-and-wait exhaustion pattern behind the OAuth non-rotating
 * refresh regression and the WebAuthN.updateUserEmail issue. This exercises the in-memory copy of the guard;
 * the postgresql plugin carries the identical mechanism.
 */
public class NestedConnectionAcquisitionGuardTest {

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
    public void nestedSamePoolBorrowInsideTransactionIsRejected() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());
        if (!(storage instanceof io.supertokens.inmemorydb.Start)) {
            // Only the in-memory pool is exercised here; the postgresql plugin has the identical guard.
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
            return;
        }
        io.supertokens.inmemorydb.Start inMem = (io.supertokens.inmemorydb.Start) storage;
        SQLStorage sqlStorage = (SQLStorage) storage;

        // Positive control: a transaction that only uses its own `con` completes normally.
        Object ok = sqlStorage.startTransaction(con -> "ok");
        assertEquals("ok", ok);

        // Negative: a helper that ignores `con` and borrows a SECOND connection from the same pool.
        // The guard throws by default, so no arming is needed for the detection to surface as a throw.
        Exception caught = null;
        try {
            sqlStorage.startTransaction(con -> {
                try {
                    ConnectionPool.getConnection(inMem);
                } catch (Exception e) {
                    throw new StorageTransactionLogicException(e);
                }
                return null;
            });
            fail("expected the nested same-pool acquisition guard to fire");
        } catch (Exception e) {
            caught = e;
        }
        assertTrue("guard message not found in exception chain: " + messageChain(caught),
                messageChain(caught).contains("Nested same-pool connection acquisition"));

        // The guard must clean up its per-thread state: a subsequent normal transaction still works.
        assertEquals("ok-again", sqlStorage.startTransaction(con -> "ok-again"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void warnModeRecordsTheNestedAcquisitionSiteWithoutThrowing() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());
        if (!(storage instanceof io.supertokens.inmemorydb.Start)) {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
            return;
        }
        io.supertokens.inmemorydb.Start inMem = (io.supertokens.inmemorydb.Start) storage;
        SQLStorage sqlStorage = (SQLStorage) storage;

        // Warn-mode opt-out: the nested borrow is allowed, but its call site must be recorded so it can be
        // reported — the warning line alone is not enough to act on. Restore the throwing default afterwards.
        ConnectionPool.setThrowOnNestedAcquisition(false);
        try {
            assertEquals("borrowed", sqlStorage.startTransaction(con -> {
                try (Connection second = ConnectionPool.getConnection(inMem)) {
                    return "borrowed";
                } catch (Exception e) {
                    throw new StorageTransactionLogicException(e);
                }
            }));
        } finally {
            ConnectionPool.setThrowOnNestedAcquisition(true);
        }
        assertTrue("nested acquisition site not recorded: " + ConnectionPool.getNestedAcquisitionSites(),
                ConnectionPool.getNestedAcquisitionSites().stream()
                        .anyMatch(site -> site.startsWith(getClass().getSimpleName() + ".")));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void foreignKeyConflictInsideTransactionIsClassifiedOnTheTransactionConnection() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());
        if (!(storage instanceof io.supertokens.inmemorydb.Start)) {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
            return;
        }
        SQLStorage sqlStorage = (SQLStorage) storage;
        TenantIdentifier missingTenant = new TenantIdentifier(null, null, "nonexistent");

        // A real FK conflict inside a transaction, with the guard in its default throw mode: classifying it must
        // probe the tenants table on the transaction's own connection. Borrowing a second one would surface as the
        // guard's IllegalStateException instead of TenantOrAppNotFoundException.
        Exception actual = null;
        try {
            sqlStorage.startTransaction(con -> {
                try {
                    sqlStorage.setKeyValue_Transaction(missingTenant, con, "key", new KeyValueInfo("value"));
                } catch (TenantOrAppNotFoundException e) {
                    throw new StorageTransactionLogicException(e);
                }
                return null;
            });
            fail("expected the FK conflict to be classified as TenantOrAppNotFoundException");
        } catch (StorageTransactionLogicException e) {
            actual = e.actualException;
        }
        assertTrue("expected TenantOrAppNotFoundException, got: " + messageChain(actual),
                actual instanceof TenantOrAppNotFoundException);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static String messageChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null && c != c.getCause(); c = c.getCause()) {
            sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage()).append(" | ");
        }
        return sb.toString();
    }
}
