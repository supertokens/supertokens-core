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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.KeyValueInfoWithLastUpdated;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.noSqlStorage.NoSQLStorage_1;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.utils.SemVer;
import io.supertokens.version.Version;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

public class StorageTest {

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

    @Rule
    public Retry retry = new Retry(3);

    @Test
    public void transactionIsolationWithoutAnInitialRowTesting() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        for (int i = 0; i < 10; i++) {
            String key = "Key" + i;

            Storage storage = StorageLayer.getStorage(process.getProcess());
            if (storage.getType() == STORAGE_TYPE.SQL
                    && !Version.getVersion(process.getProcess()).getPluginName().equals("sqlite")) {
                SQLStorage sqlStorage = (SQLStorage) storage;

                AtomicReference<String> endValueOfCon1 = new AtomicReference<>("c1");
                AtomicReference<String> endValueOfCon2 = new AtomicReference<>("c2");
                AtomicInteger numberOfIterations = new AtomicInteger(-2);

                /**
                 * We start two transactions in parallel that will read from storage
                 * We put the threads to sleep for different durations to simulate a race condition
                 * Thread 1 should write to the database successfully but when Thread 2 tried to insert it should face a
                 * deadlock
                 * Because of the way deadlocks are handled in startTransaction Thread 2's transaction will retry and
                 * when getting it will get an entry in the table and wont try to insert
                 * When both threads are done we want to make sure:
                 * - That the value in storage was set to "Value1" because Thread 2 should not be able to write
                 * - That the counter for the number of iterations is 3 (Thread 1 + Thread 2 deadlock + Thread 2 retry)
                 *
                 * NOTE: In this test we use FOR UPDATE with a WHERE clause but it would work the same way without a
                 * WHERE clause as well
                 */
                Runnable r1 = () -> {
                    try {
                        sqlStorage.startTransaction(con -> {
                            numberOfIterations.getAndIncrement();

                            KeyValueInfo info = sqlStorage.getKeyValue_Transaction(
                                    new TenantIdentifier(null, null, null), con, key);

                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                            }

                            if (info == null) {
                                try {
                                    sqlStorage.setKeyValue_Transaction(new TenantIdentifier(null, null, null), con, key,
                                            new KeyValueInfo("Value1"));
                                } catch (TenantOrAppNotFoundException e) {
                                    throw new IllegalStateException(e);
                                }
                            } else {
                                endValueOfCon1.set(info.value);
                                return null;
                            }

                            endValueOfCon1.set("Value1");
                            return null;
                        });
                    } catch (Exception ignored) {
                    }
                };

                Runnable r2 = () -> {
                    try {
                        sqlStorage.startTransaction(con -> {
                            numberOfIterations.getAndIncrement();

                            KeyValueInfo info = sqlStorage.getKeyValue_Transaction(
                                    new TenantIdentifier(null, null, null), con, key);

                            if (numberOfIterations.get() != 1) {
                                assert (info == null);
                            } else {
                                assert (info != null);
                            }

                            try {
                                Thread.sleep(700);
                            } catch (InterruptedException e) {
                            }

                            if (info == null) {
                                try {
                                    sqlStorage.setKeyValue_Transaction(new TenantIdentifier(null, null, null), con, key,
                                            new KeyValueInfo("Value2"));
                                } catch (TenantOrAppNotFoundException e) {
                                    throw new IllegalStateException(e);
                                }
                            } else {
                                endValueOfCon2.set(info.value);
                                return null;
                            }

                            endValueOfCon2.set("Value2");
                            return null;
                        });
                    } catch (Exception ignored) {
                    }
                };

                Thread t1 = new Thread(r1);
                Thread t2 = new Thread(r2);

                t1.start();
                t2.start();

                t1.join();
                t2.join();

                assertEquals(endValueOfCon1.get(), endValueOfCon2.get());
                assertEquals(numberOfIterations.get(), 1);

            }

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void transactionIsolationWithAnInitialRowTesting()
            throws InterruptedException, StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        for (int i = 0; i < 100; i++) {

            Storage storage = StorageLayer.getStorage(process.getProcess());
            if (storage.getType() == STORAGE_TYPE.SQL) {
                SQLStorage sqlStorage = (SQLStorage) storage;
                sqlStorage.startTransaction(con -> {
                    try {
                        sqlStorage.setKeyValue_Transaction(new TenantIdentifier(null, null, null), con, "Key",
                                new KeyValueInfo("Value"));
                    } catch (TenantOrAppNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                    sqlStorage.commitTransaction(con);
                    return null;
                });

                AtomicReference<String> endValueOfCon1 = new AtomicReference<>("c1");
                AtomicReference<String> endValueOfCon2 = new AtomicReference<>("c2");
                AtomicInteger numberOfIterations = new AtomicInteger(-2);

                Runnable r1 = () -> {
                    try {
                        sqlStorage.startTransaction(con -> {
                            numberOfIterations.getAndIncrement();

                            KeyValueInfo info = sqlStorage.getKeyValue_Transaction(
                                    new TenantIdentifier(null, null, null), con, "Key");

                            if (info.value.equals("Value")) {
                                try {
                                    sqlStorage.setKeyValue_Transaction(new TenantIdentifier(null, null, null), con,
                                            "Key",
                                            new KeyValueInfo("Value1"));
                                } catch (TenantOrAppNotFoundException e) {
                                    throw new IllegalStateException(e);
                                }
                            } else {
                                endValueOfCon1.set(info.value);
                                return null;
                            }

                            endValueOfCon1.set("Value1");
                            return null;
                        });
                    } catch (Exception ignored) {
                    }
                };

                Runnable r2 = () -> {
                    try {
                        sqlStorage.startTransaction(con -> {
                            numberOfIterations.getAndIncrement();

                            KeyValueInfo info = sqlStorage.getKeyValue_Transaction(
                                    new TenantIdentifier(null, null, null), con, "Key");

                            if (info.value.equals("Value")) {
                                try {
                                    sqlStorage.setKeyValue_Transaction(new TenantIdentifier(null, null, null), con,
                                            "Key",
                                            new KeyValueInfo("Value2"));
                                } catch (TenantOrAppNotFoundException e) {
                                    throw new IllegalStateException(e);
                                }
                            } else {
                                endValueOfCon2.set(info.value);
                                return null;
                            }

                            endValueOfCon2.set("Value2");
                            return null;
                        });
                    } catch (Exception ignored) {
                    }
                };

                Thread t1 = new Thread(r1);
                Thread t2 = new Thread(r2);

                t1.start();
                t2.start();

                t1.join();
                t2.join();

                assertEquals(endValueOfCon1.get(), endValueOfCon2.get());
                if (Version.getVersion(process.getProcess()).getPluginName().equals("postgresql")
                        || Version.getVersion(process.getProcess()).getPluginName().equals("sql")) {
                    // Becasue FOR UPDATE does not wait in Postgresql. Instead if throws an error.
                    assert (numberOfIterations.get() == 1 || numberOfIterations.get() == 0);
                } else {
                    assertEquals(numberOfIterations.get(), 0);
                }

            }

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void transactionIsolationTesting()
            throws InterruptedException, StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());
        if (storage.getType() == STORAGE_TYPE.SQL) {
            SQLStorage sqlStorage = (SQLStorage) storage;
            sqlStorage.startTransaction(con -> {
                try {
                    sqlStorage.setKeyValue_Transaction(new TenantIdentifier(null, null, null), con, "Key",
                            new KeyValueInfo("Value"));
                } catch (TenantOrAppNotFoundException e) {
                    throw new IllegalStateException(e);
                }
                sqlStorage.commitTransaction(con);
                return null;
            });

            AtomicReference<String> t1State = new AtomicReference<>("init");
            AtomicReference<String> t2State = new AtomicReference<>("init");
            final Object syncObject = new Object();

            AtomicBoolean t1Failed = new AtomicBoolean(true);
            AtomicBoolean t2Failed = new AtomicBoolean(true);

            Runnable r1 = () -> {
                try {
                    sqlStorage.startTransaction(con -> {

                        sqlStorage.getKeyValue_Transaction(new TenantIdentifier(null, null, null), con, "Key");

                        synchronized (syncObject) {
                            t1State.set("read");
                            syncObject.notifyAll();
                        }

                        try {
                            sqlStorage.setKeyValue_Transaction(new TenantIdentifier(null, null, null), con, "Key",
                                    new KeyValueInfo("Value2"));
                        } catch (TenantOrAppNotFoundException e) {
                            throw new IllegalStateException(e);
                        }

                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException e) {
                        }
                        synchronized (syncObject) {
                            assertEquals("before_read", t2State.get());
                        }

                        sqlStorage.commitTransaction(con);

                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException e) {
                        }
                        synchronized (syncObject) {
                            assertEquals("after_read", t2State.get());
                        }

                        t1Failed.set(false);
                        return null;
                    });
                } catch (Exception ignored) {
                }
            };

            Runnable r2 = () -> {
                try {
                    sqlStorage.startTransaction(con -> {

                        synchronized (syncObject) {
                            while (!t1State.get().equals("read")) {
                                try {
                                    syncObject.wait();
                                } catch (InterruptedException e) {
                                }
                            }
                        }

                        synchronized (syncObject) {
                            t2State.set("before_read");
                        }

                        KeyValueInfo val = sqlStorage.getKeyValue_Transaction(new TenantIdentifier(null, null, null),
                                con, "Key");

                        synchronized (syncObject) {
                            t2State.set("after_read");
                        }

                        assertEquals(val.value, "Value2");

                        t2Failed.set(false);
                        return null;
                    });
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

        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
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
        } else {
            throw new UnsupportedOperationException();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void transactionTest() throws InterruptedException, StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());
        if (storage.getType() == STORAGE_TYPE.SQL) {
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
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            NoSQLStorage_1 noSqlStorage = (NoSQLStorage_1) storage;
            {
                noSqlStorage.setKeyValue_Transaction("Key", new KeyValueInfoWithLastUpdated("Value", null));
                KeyValueInfo value = noSqlStorage.getKeyValue(new TenantIdentifier(null, null, null), "Key");
                assertEquals(value.value, "Value");
            }
            {
                KeyValueInfoWithLastUpdated newKey = noSqlStorage.getKeyValue_Transaction("Key");
                noSqlStorage.setKeyValue_Transaction("Key",
                        new KeyValueInfoWithLastUpdated("Value2", newKey.lastUpdatedSign));
                KeyValueInfo value = noSqlStorage.getKeyValue(new TenantIdentifier(null, null, null), "Key");
                assertEquals(value.value, "Value2");
            }
            {
                KeyValueInfoWithLastUpdated newKey = noSqlStorage.getKeyValue_Transaction("Key");
                noSqlStorage.setKeyValue_Transaction("Key",
                        new KeyValueInfoWithLastUpdated("Value3", "someRandomLastUpdatedSign"));
                KeyValueInfo value = noSqlStorage.getKeyValue(new TenantIdentifier(null, null, null), "Key");
                assertEquals(value.value, "Value2");
            }
        } else {
            throw new UnsupportedOperationException();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void transactionDoNotCommitButStillCommitsTest()
            throws InterruptedException, StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());
        if (storage.getType() == STORAGE_TYPE.SQL) {
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
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            // not applicable
        } else {
            throw new UnsupportedOperationException();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void transactionDoNotInsertIfAlreadyExistsForNoSQL() throws InterruptedException, StorageQueryException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());
        if (storage.getType() == STORAGE_TYPE.SQL) {
            // not applicable
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            NoSQLStorage_1 noSqlStorage = (NoSQLStorage_1) storage;

            boolean success = noSqlStorage.setKeyValue_Transaction("Key",
                    new KeyValueInfoWithLastUpdated("Value", null));

            assert (success);

            success = noSqlStorage.setKeyValue_Transaction("Key", new KeyValueInfoWithLastUpdated("Value2", null));

            assert (!success);

        } else {
            throw new UnsupportedOperationException();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void transactionThrowCompileTimeErrorAndExpectRollbackTest()
            throws InterruptedException, StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());
        if (storage.getType() == STORAGE_TYPE.SQL) {
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
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            // not applicable
        } else {
            throw new UnsupportedOperationException();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void transactionThrowRunTimeErrorAndExpectRollbackTest()
            throws InterruptedException, StorageQueryException, StorageTransactionLogicException {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());
        if (storage.getType() == STORAGE_TYPE.SQL) {
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
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            // not applicable
        } else {
            throw new UnsupportedOperationException();
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void storageDeadAndAlive() throws InterruptedException, IOException, HttpResponseException {
        String[] args = {"../"};

        String userId = "userId";
        JsonObject userDataInJWT = new JsonObject();
        userDataInJWT.addProperty("key", "value");
        JsonObject userDataInDatabase = new JsonObject();
        userDataInDatabase.addProperty("key", "value");

        JsonObject request = new JsonObject();
        request.addProperty("userId", userId);
        request.add("userDataInJWT", userDataInJWT);
        request.add("userDataInDatabase", userDataInDatabase);
        request.addProperty("enableAntiCsrf", false);
        request.addProperty("useStaticKey", false);

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Storage storage = StorageLayer.getStorage(process.getProcess());
        storage.setStorageLayerEnabled(false);

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/recipe/session",
                    request, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "session");
            fail();
        } catch (HttpResponseException ex) {
            assertEquals(ex.statusCode, 500);
            assertTrue(ex.getMessage().contains("Storage layer disabled"));
        }

        storage.setStorageLayerEnabled(true);

        JsonObject sessionCreated = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session", request, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(),
                "session");

        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("refreshToken",
                sessionCreated.get("refreshToken").getAsJsonObject().get("token").getAsString());
        jsonBody.addProperty("enableAntiCsrf", false);
        jsonBody.addProperty("useDynamicSigningKey", true);

        storage.setStorageLayerEnabled(false);

        try {
            HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/session/refresh", jsonBody, 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "session");
            fail();
        } catch (HttpResponseException ex) {
            assertEquals(ex.statusCode, 500);
            assertTrue(ex.getMessage().contains("Storage layer disabled"));
        }

        storage.setStorageLayerEnabled(true);

        HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/recipe/session/refresh", jsonBody, 1000, 1000, null,
                Utils.getCdiVersionStringLatestForTests(), "session");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void multipleParallelTransactionTest() throws InterruptedException, IOException {
        String[] args = {"../"};
        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "0.00005");
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        int numberOfThreads = 1000;
        ArrayList<Thread> threads = new ArrayList<>();
        ArrayList<ParallelTransactions> runnables = new ArrayList<>();
        for (int i = 0; i < numberOfThreads; i++) {
            ParallelTransactions p = new ParallelTransactions(process);
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

    static class ParallelTransactions implements Runnable {

        TestingProcessManager.TestingProcess process;
        boolean success = false;

        ParallelTransactions(TestingProcessManager.TestingProcess process) {
            this.process = process;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    String jsonInput = "{" + "\"deviceDriverInfo\": {" + "\"frontendSDK\": [{" + "\"name\": \"hName\","
                            + "\"version\": \"hVersion\"" + "}]," + "\"driver\": {" + "\"name\": \"hDName\","
                            + "\"version\": \"nDVersion\"" + "}" + "}" + "}";
                    HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                            "http://localhost:3567/recipe/handshake", new JsonParser().parse(jsonInput), 10000, 20000,
                            null, SemVer.v2_18.get(), "session");
                    success = true;
                    break;
                } catch (Exception error) {
                    if (error instanceof HttpResponseException && ((HttpResponseException) error).statusCode == 500) {
                        continue;
                    }
                    if (!(error instanceof SocketException)) {
                        break;
                    }
                }
            }
        }
    }
}
