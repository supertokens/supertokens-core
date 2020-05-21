/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.test;

import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.KeyValueInfoWithLastUpdated;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.noSqlStorage.NoSQLStorage_1;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotNull;

public class InMemoryDBStorageTest {
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
    public void transactionIsolationTesting()
            throws InterruptedException, StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../", "DEV"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorageLayer(process.getProcess());
        NoSQLStorage_1 noSqlStorage = (NoSQLStorage_1) storage;

        noSqlStorage.setKeyValue_Transaction("Key", new KeyValueInfoWithLastUpdated("Value", null));

        AtomicReference<String> t1State = new AtomicReference<>("init");
        AtomicReference<String> t2State = new AtomicReference<>("init");
        final Object syncObject = new Object();

        AtomicBoolean t1Failed = new AtomicBoolean(true);
        AtomicBoolean t2Failed = new AtomicBoolean(true);

        Runnable r1 = () -> {
            try {
                int numberOfLoops = 0;
                while (true) {
                    KeyValueInfoWithLastUpdated k1 = noSqlStorage.getKeyValue_Transaction("Key");

                    synchronized (syncObject) {
                        t1State.set("read");
                        syncObject.notifyAll();
                    }


                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                    }

                    synchronized (syncObject) {
                        assertEquals("before_read", t2State.get());
                    }

                    boolean success = noSqlStorage.setKeyValue_Transaction("Key",
                            new KeyValueInfoWithLastUpdated("Value2", k1.lastUpdatedSign));

                    if (!success) {
                        numberOfLoops++;
                        continue;
                    }

                    synchronized (syncObject) {
                        t1State.set("after_set");
                        syncObject.notifyAll();
                    }


                    t1Failed.set(numberOfLoops != 1);
                    break;
                }
            } catch (Exception ignored) {
            }
        };

        Runnable r2 = () -> {
            try {

                synchronized (syncObject) {
                    while (!t1State.get().equals("read")) {
                        try {
                            syncObject.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }

                KeyValueInfoWithLastUpdated val = noSqlStorage.getKeyValue_Transaction("Key");

                boolean success = noSqlStorage.setKeyValue_Transaction("Key",
                        new KeyValueInfoWithLastUpdated("Value1", val.lastUpdatedSign));

                assert (success);

                synchronized (syncObject) {
                    t2State.set("before_read");
                }


                synchronized (syncObject) {
                    while (!t1State.get().equals("after_set")) {
                        try {
                            syncObject.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }

                val = noSqlStorage.getKeyValue_Transaction("Key");

                assertEquals(val.value, "Value2");

                t2Failed.set(false);
            } catch (Exception ignored) {
            }
        };

        Thread t1 = new Thread(r1);
        Thread t2 = new Thread(r2);

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        assertTrue(!t1Failed.get() && !t2Failed.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void transactionTest() throws InterruptedException, StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../", "DEV"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorageLayer(process.getProcess());
        NoSQLStorage_1 noSqlStorage = (NoSQLStorage_1) storage;
        {
            noSqlStorage.setKeyValue_Transaction("Key", new KeyValueInfoWithLastUpdated("Value", null));
            KeyValueInfo value = noSqlStorage.getKeyValue("Key");
            assertEquals(value.value, "Value");
        }
        {
            KeyValueInfoWithLastUpdated newKey = noSqlStorage.getKeyValue_Transaction("Key");
            noSqlStorage
                    .setKeyValue_Transaction("Key",
                            new KeyValueInfoWithLastUpdated("Value2", newKey.lastUpdatedSign));
            KeyValueInfo value = noSqlStorage.getKeyValue("Key");
            assertEquals(value.value, "Value2");
        }
        {
            KeyValueInfoWithLastUpdated newKey = noSqlStorage.getKeyValue_Transaction("Key");
            noSqlStorage
                    .setKeyValue_Transaction("Key",
                            new KeyValueInfoWithLastUpdated("Value3", "someRandomLastUpdatedSign"));
            KeyValueInfo value = noSqlStorage.getKeyValue("Key");
            assertEquals(value.value, "Value2");
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void transactionDoNotInsertIfAlreadyExistsForNoSQL()
            throws InterruptedException, StorageQueryException {
        String[] args = {"../", "DEV"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorageLayer(process.getProcess());
        NoSQLStorage_1 noSqlStorage = (NoSQLStorage_1) storage;

        boolean success = noSqlStorage
                .setKeyValue_Transaction("Key", new KeyValueInfoWithLastUpdated("Value", null));

        assert (success);

        success = noSqlStorage.setKeyValue_Transaction("Key", new KeyValueInfoWithLastUpdated("Value2", null));

        assert (!success);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void multipleParallelTransactionTest() throws InterruptedException, IOException {
        String[] args = {"../", "DEV"};
        Utils.setValueInConfig("access_token_signing_key_update_interval", "0.00005");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        int numberOfThreads = 1000;
        ArrayList<Thread> threads = new ArrayList<>();
        ArrayList<StorageTest.ParallelTransactions> runnables = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            StorageTest.ParallelTransactions p = new StorageTest.ParallelTransactions(process);
            Thread t = new Thread(p);
            threads.add(t);
            runnables.add(p);
            t.start();
        }
        threads.forEach(thread -> {
            while (true) {
                try {
                    thread.join();
                    return;
                } catch (InterruptedException e) {
                }
            }
        });
        for (int i = 0; i < numberOfThreads; i++) {
            assertTrue(runnables.get(i).success);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
