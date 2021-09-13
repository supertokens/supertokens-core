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
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.List;
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
     *
     * For NoSQL
     * - Test that when trying to set with different information (key string in this case) but same algorithm returns false. This is because findOneAndUpdate
     *   will not throw an exception in this case, but we should treat it as a failure and retry
     *
     * - Test that when trying to set with a duplicate key but different algorithm throws a DuplicateKeyIdException.
     */
    @Test
    public void testThatWhenSettingKeysWithSameIdDuplicateExceptionIsThrown() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JWTRecipeStorage storage = StorageLayer.getJWTRecipeStorage(process.getProcess());

        if (storage.getType() == STORAGE_TYPE.SQL) {
            JWTRecipeSQLStorage sqlStorage = (JWTRecipeSQLStorage) storage;
            JWTSigningKeyInfo keyToSet = new JWTSymmetricSigningKeyInfo("keyId-1234", 1000, "RSA", "somekeystring");

            boolean success = sqlStorage.startTransaction(con -> {
                try {
                    sqlStorage.setJWTSigningKey_Transaction(con, keyToSet);
                } catch (DuplicateKeyIdException e) {
                    return false;
                }

                try {
                    sqlStorage.setJWTSigningKey_Transaction(con, keyToSet);
                    return false;
                } catch (DuplicateKeyIdException e) {
                    return true;
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
             * When using the same algorithm but different information (key string in this case), setting the key to storage should return
             * false because an older key for the same algorithm is found in storage
             */
            JWTSigningKeyInfo keyWithDifferentKeyString = new JWTSymmetricSigningKeyInfo("keyId-1234", 1000, "RSA", "someDifferentKeyString");
            boolean successForSameObj = noSQLStorage_1.setJWTSigningKeyInfoIfNoKeyForAlgorithmExists_Transaction(keyWithDifferentKeyString);
            assert !successForSameObj;

            try {
                /*
                 * When setting with the same key id but different algorithm, findOneAndUpdate will try to insert but Mongo
                 * should throw an error because the _id of the new document already exists in storage
                 */
                JWTSigningKeyInfo keyToSet2 = new JWTSymmetricSigningKeyInfo("keyId-1234", 1000, "EC", "somekeystring");
                noSQLStorage_1.setJWTSigningKeyInfoIfNoKeyForAlgorithmExists_Transaction(keyToSet2);
            } catch (DuplicateKeyIdException e) {
                // Do nothing
            }
        }
    }

    /**
     * For NoSQL only
     *
     * Simulate a race condition for parallel threads trying to read from the database (which results in no rows being found initially)
     * and writing to storage and make sure that at the end of execution both threads used the same key and only one thread was
     * able to write to storage
     */
    @Test
    public void testThatSettingJWTKeysInParallelWorksAsExpectedForNoSQL() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        for (int i = 0; i < 10; i++) {
            String algorithm = "alg" + i;
            String keyId = "key" + i;

            JWTRecipeStorage storage = StorageLayer.getJWTRecipeStorage(process.getProcess());

            if (storage.getType() != STORAGE_TYPE.NOSQL_1) {
                return;
            }

            JWTRecipeNoSQLStorage_1 noSQLStorage_1 = (JWTRecipeNoSQLStorage_1) storage;

            AtomicReference<String> endValueOfCon1 = new AtomicReference<>("c1");
            AtomicReference<String> endValueOfCon2 = new AtomicReference<>("c2");
            AtomicInteger numberOfIterations = new AtomicInteger(0);

            Runnable runnable1 = new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            numberOfIterations.getAndIncrement();
                            List<JWTSigningKeyInfo> keysFromStorage = noSQLStorage_1.getJWTSigningKeys_Transaction();

                            JWTSigningKeyInfo matchingKeyInfo = null;

                            for (int j = 0; j < keysFromStorage.size(); j ++) {
                                if (keysFromStorage.get(j).algorithm.equals(algorithm)) {
                                    matchingKeyInfo = keysFromStorage.get(j);
                                    break;
                                }
                            }

                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                // Ignore
                            }

                            try {
                                if (matchingKeyInfo == null) {
                                    boolean success = noSQLStorage_1.setJWTSigningKeyInfoIfNoKeyForAlgorithmExists_Transaction(new JWTSymmetricSigningKeyInfo(keyId + "-1", 1000, algorithm, "SomeKeyString"));

                                    if (!success) {
                                        fail();
                                    }

                                    endValueOfCon1.set(keyId + "-1");
                                } else {
                                    endValueOfCon1.set(matchingKeyInfo.keyId);
                                }

                                break;
                            } catch (DuplicateKeyIdException e) {
                                continue;
                            }
                        }
                    } catch (Exception ignored) {

                    }
                }
            };

            Runnable runnable2 = new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            numberOfIterations.getAndIncrement();
                            List<JWTSigningKeyInfo> keysFromStorage = noSQLStorage_1.getJWTSigningKeys_Transaction();

                            JWTSigningKeyInfo matchingKeyInfo = null;

                            for (int j = 0; j < keysFromStorage.size(); j ++) {
                                if (keysFromStorage.get(j).algorithm.equals(algorithm)) {
                                    matchingKeyInfo = keysFromStorage.get(j);
                                    break;
                                }
                            }

                            if (numberOfIterations.get() <= 2) {
                                assert matchingKeyInfo == null;
                            } else {
                                assert matchingKeyInfo != null;
                            }

                            try {
                                Thread.sleep(700);
                            } catch (InterruptedException e) {
                                // Ignore
                            }

                            try {
                                if (matchingKeyInfo == null) {
                                    boolean success = noSQLStorage_1.setJWTSigningKeyInfoIfNoKeyForAlgorithmExists_Transaction(new JWTSymmetricSigningKeyInfo(keyId + "-2", 1000, algorithm, "SomeKeyString"));

                                    if (!success) {
                                        continue;
                                    }

                                    endValueOfCon2.set(keyId + "-2");
                                } else {
                                    endValueOfCon2.set(matchingKeyInfo.keyId);
                                }

                                break;
                            } catch (DuplicateKeyIdException e) {
                                continue;
                            }
                        }
                    } catch (Exception ignored) {

                    }
                }
            };

            Thread thread1 = new Thread(runnable1);
            Thread thread2 = new Thread(runnable2);

            thread1.start();
            thread2.start();

            thread1.join();
            thread2.join();

            assertEquals(numberOfIterations.get(), 3);
            assertEquals(endValueOfCon1.get(), endValueOfCon2.get());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
