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

package io.supertokens.test;

import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class InMemoryDBStorageTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Rule
    public Retry retry = new Retry(3);

    @Test
    public void transactionTest() throws InterruptedException, StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());
        SQLStorage sqlStorage = (SQLStorage) storage;
        String returnedValue = sqlStorage.startTransaction(con -> {
            try {
                sqlStorage.setKeyValue_Transaction(new TenantIdentifier(null, null, null), con, "Key",
                        new KeyValueInfo("Value"));
            } catch (TenantOrAppNotFoundException e) {
                throw new IllegalStateException(e);
            }
            sqlStorage.commitTransaction(con);
            return "returned value";
        });
        assertEquals(returnedValue, "returned value");
        KeyValueInfo value = storage.getKeyValue(new TenantIdentifier(null, null, null), "Key");
        assertEquals(value.value, "Value");
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void transactionDoNotCommitButStillCommitsTest()
            throws InterruptedException, StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());

        SQLStorage sqlStorage = (SQLStorage) storage;
        sqlStorage.startTransaction(con -> {
            try {
                sqlStorage.setKeyValue_Transaction(new TenantIdentifier(null, null, null), con, "Key",
                        new KeyValueInfo("Value"));
            } catch (TenantOrAppNotFoundException e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
        KeyValueInfo value = storage.getKeyValue(new TenantIdentifier(null, null, null), "Key");
        assertEquals(value.value, "Value");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void transactionThrowCompileTimeErrorAndExpectRollbackTest()
            throws InterruptedException, StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());

        SQLStorage sqlStorage = (SQLStorage) storage;
        try {
            sqlStorage.startTransaction(con -> {
                try {
                    sqlStorage.setKeyValue_Transaction(new TenantIdentifier(null, null, null), con, "Key",
                            new KeyValueInfo("Value"));
                } catch (TenantOrAppNotFoundException e) {
                    throw new IllegalStateException(e);
                }
                throw new StorageTransactionLogicException(new Exception("error message"));
            });
        } catch (StorageTransactionLogicException e) {
            if (!e.actualException.getMessage().equals("error message")) {
                throw e;
            }
        }
        KeyValueInfo value = storage.getKeyValue(new TenantIdentifier(null, null, null), "Key");
        assertNull(value);
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void transactionThrowRunTimeErrorAndExpectRollbackTest()
            throws InterruptedException, StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());

        SQLStorage sqlStorage = (SQLStorage) storage;
        try {
            sqlStorage.startTransaction(con -> {
                try {
                    sqlStorage.setKeyValue_Transaction(new TenantIdentifier(null, null, null), con, "Key",
                            new KeyValueInfo("Value"));
                } catch (TenantOrAppNotFoundException e) {
                    throw new IllegalStateException(e);
                }
                throw new RuntimeException("error message");
            });
        } catch (RuntimeException e) {
            if (!e.getMessage().equals("error message")) {
                throw e;
            }
        }
        KeyValueInfo value = storage.getKeyValue(new TenantIdentifier(null, null, null), "Key");
        assertNull(value);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
