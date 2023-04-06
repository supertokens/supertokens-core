/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.jwt.storage;

import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.jwt.JWTRecipeStorage;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.exceptions.DuplicateKeyIdException;
import io.supertokens.pluginInterface.jwt.nosqlstorage.JWTRecipeNoSQLStorage_1;
import io.supertokens.pluginInterface.jwt.sqlstorage.JWTRecipeSQLStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class JWTKeyStorageTest {
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

    /**
     * Test that if a keyId is set when it already exists in storage, a DuplicateKeyIdException should be thrown
     * <p>
     * For NoSQL
     * - Test that when trying to set with different information (key string in this case) but same algorithm returns
     * false. This is because findOneAndUpdate
     * will not throw an exception in this case, but we should treat it as a failure and retry
     * <p>
     * - Test that when trying to set with a duplicate key but different algorithm throws a DuplicateKeyIdException.
     */
    @Test
    public void testThatWhenSettingKeysWithSameIdDuplicateExceptionIsThrown() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JWTRecipeStorage storage = (JWTRecipeStorage) StorageLayer.getStorage(process.getProcess());

        if (storage.getType() == STORAGE_TYPE.SQL) {
            JWTRecipeSQLStorage sqlStorage = (JWTRecipeSQLStorage) storage;
            JWTSigningKeyInfo keyToSet = new JWTSymmetricSigningKeyInfo("keyId-1234", 1000, "RSA", "somekeystring");

            boolean success = sqlStorage.startTransaction(con -> {
                try {
                    sqlStorage.setJWTSigningKey_Transaction(new AppIdentifier(null, null), con, keyToSet);
                } catch (DuplicateKeyIdException e) {
                    return false;
                } catch (TenantOrAppNotFoundException e) {
                    throw new IllegalStateException(e);
                }

                try {
                    sqlStorage.setJWTSigningKey_Transaction(new AppIdentifier(null, null), con, keyToSet);
                    return false;
                } catch (DuplicateKeyIdException e) {
                    return true;
                } catch (TenantOrAppNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            });

            assert success;
        } else if (storage.getType() == STORAGE_TYPE.NOSQL_1) {
            JWTRecipeNoSQLStorage_1 noSQLStorage_1 = (JWTRecipeNoSQLStorage_1) storage;
            JWTSigningKeyInfo keyToSet = new JWTSymmetricSigningKeyInfo("keyId-1234", 1000, "RSA", "somekeystring");

            try {
                noSQLStorage_1.setJWTSigningKeyInfoIfNoKeyForAlgorithmExists_Transaction(keyToSet);
            } catch (Exception e) {
                fail();
            }

            /*
             * When using the same algorithm but different information (key string in this case), setting the key to
             * storage should return
             * false because an older key for the same algorithm is found in storage
             */
            JWTSigningKeyInfo keyWithDifferentKeyString = new JWTSymmetricSigningKeyInfo("keyId-1234", 1000, "RSA",
                    "someDifferentKeyString");
            boolean successForDifferentKeyString = noSQLStorage_1
                    .setJWTSigningKeyInfoIfNoKeyForAlgorithmExists_Transaction(keyWithDifferentKeyString);
            assert !successForDifferentKeyString;

            try {
                /*
                 * When setting with the same key id but different algorithm, findOneAndUpdate will try to insert but
                 * Mongo
                 * should throw an error because the _id of the new document already exists in storage
                 */
                JWTSigningKeyInfo keyToSet2 = new JWTSymmetricSigningKeyInfo("keyId-1234", 1000, "EC", "somekeystring");
                noSQLStorage_1.setJWTSigningKeyInfoIfNoKeyForAlgorithmExists_Transaction(keyToSet2);
                fail();
            } catch (DuplicateKeyIdException e) {
                // Do nothing
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    /**
     * For NoSQL only
     * <p>
     * Note we do not test for SQL, because for SQL plugins in the case of parallel writes a deadlock is encountered and
     * the current
     * transaction logic handles it (as tested in StorageTest.java) and causes the transaction to be retried
     * <p>
     * Simulate a race condition for parallel threads trying to read from the database (which results in no rows being
     * found initially)
     * and writing to storage and make sure that at the end of execution both threads used the same key and only one
     * thread was
     * able to write to storage
     */
    @Test
    public void testThatSettingJWTKeysInParallelWorksAsExpectedForNoSQL() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        for (int i = 0; i < 10; i++) {
            String algorithm = "alg" + i;

            JWTRecipeStorage storage = (JWTRecipeStorage) StorageLayer.getStorage(process.getProcess());

            if (storage.getType() != STORAGE_TYPE.NOSQL_1) {
                return;
            }

            JWTRecipeNoSQLStorage_1 noSQLStorage_1 = (JWTRecipeNoSQLStorage_1) storage;

            AtomicReference<String> endValueOfCon1 = new AtomicReference<>("c1");
            AtomicReference<String> endValueOfCon2 = new AtomicReference<>("c2");
            AtomicInteger numberOfIterationsForThread2 = new AtomicInteger(0);

            Runnable runnable1 = new Runnable() {
                @Override
                public void run() {
                    try {
                        List<JWTSigningKeyInfo> keysFromStorage = noSQLStorage_1.getJWTSigningKeys_Transaction();

                        JWTSigningKeyInfo matchingKeyInfo = null;

                        // We want to find if there is any key in storage that matches the algorithm. This is because in
                        // the second iteration onwards the keys from storage will not be empty so we cannot rely on
                        // that fact
                        for (int j = 0; j < keysFromStorage.size(); j++) {
                            if (keysFromStorage.get(j).algorithm.equals(algorithm)) {
                                matchingKeyInfo = keysFromStorage.get(j);
                                break;
                            }
                        }

                        // For the first thread we want to make sure that no key for the algorithm was found
                        assert matchingKeyInfo == null;

                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            // Ignore
                        }

                        String keyId = UUID.randomUUID().toString();
                        boolean success = noSQLStorage_1.setJWTSigningKeyInfoIfNoKeyForAlgorithmExists_Transaction(
                                new JWTSymmetricSigningKeyInfo(keyId, 1000, algorithm, keyId));
                        // The first thread should always succeed in writing to storage
                        assert success;

                        endValueOfCon1.set(keyId);
                    } catch (Exception e) {
                        fail();
                    }
                }
            };

            Runnable runnable2 = new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            numberOfIterationsForThread2.getAndIncrement();
                            List<JWTSigningKeyInfo> keysFromStorage = noSQLStorage_1.getJWTSigningKeys_Transaction();

                            JWTSigningKeyInfo matchingKeyInfo = null;

                            for (int j = 0; j < keysFromStorage.size(); j++) {
                                if (keysFromStorage.get(j).algorithm.equals(algorithm)) {
                                    matchingKeyInfo = keysFromStorage.get(j);
                                    break;
                                }
                            }

                            if (numberOfIterationsForThread2.get() == 1) {
                                assert matchingKeyInfo == null;
                            } else {
                                assert matchingKeyInfo != null;
                            }

                            try {
                                Thread.sleep(700);
                            } catch (InterruptedException e) {
                                // Ignore
                            }

                            if (matchingKeyInfo == null) {
                                String keyId = UUID.randomUUID().toString();
                                boolean success = noSQLStorage_1
                                        .setJWTSigningKeyInfoIfNoKeyForAlgorithmExists_Transaction(
                                                new JWTSymmetricSigningKeyInfo(keyId, 1000, algorithm, keyId));

                                if (!success) {
                                    continue;
                                } else {
                                    // When the second thread tries to write it should never succeed
                                    fail();
                                }

                                endValueOfCon2.set(keyId);
                            } else {
                                endValueOfCon2.set(matchingKeyInfo.keyId);
                            }

                            break;
                        }
                    } catch (Exception e) {
                        fail();
                    }
                }
            };

            Thread thread1 = new Thread(runnable1);
            Thread thread2 = new Thread(runnable2);

            thread1.start();
            thread2.start();

            thread1.join();
            thread2.join();

            assertEquals(numberOfIterationsForThread2.get(), 2);
            assertEquals(endValueOfCon1.get(), endValueOfCon2.get());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
