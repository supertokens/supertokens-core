/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.EmailChangeNotAllowedException;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.ThirdParty;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AuthRecipesParallelTest {
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
    public void timeTakenFor500SignInParallel() throws Exception {
        { // warm up the db with some data
            String[] args = {"../"};
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            Utils.setValueInConfig("postgresql_connection_pool_size", "100");
            Utils.setValueInConfig("mysql_connection_pool_size", "100");

            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                            EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            if (StorageLayer.isInMemDb(process.getProcess())) {
                return;
            }

            int numberOfUsers = 10;

            // Warm up
            ExecutorService es = Executors.newFixedThreadPool(32);

            for (int i = 0; i < numberOfUsers; i++) {
                int finalI = i;
                es.execute(() -> {
                    try {
                        EmailPassword.signUp(process.getProcess(), "test" + finalI + "@example.com", "password123");
                    } catch (Exception e) {
                        // ignore
                    }
                });
            }
            es.shutdown();
            es.awaitTermination(5, TimeUnit.MINUTES);

            process.kill(false);
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        ExecutorService ex = Executors.newFixedThreadPool(1000);
        int numberOfThreads = 500;

        EmailPassword.signUp(process.getProcess(), "test@example.com", "password");
        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger retryCounter = new AtomicInteger(0);

        long st = System.currentTimeMillis();
        for (int i = 0; i < numberOfThreads; i++) {
            ex.execute(() -> {
                while(true) {
                    try {
                        EmailPassword.signIn(process.getProcess(), "test@example.com", "password");
                        counter.incrementAndGet();
                        break;
                    } catch (StorageQueryException e) {
                        retryCounter.incrementAndGet();
                        // continue
                    } catch (WrongCredentialsException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        ex.shutdown();

        ex.awaitTermination(2, TimeUnit.MINUTES);
        System.out.println("Time taken for " + numberOfThreads + " sign in parallel: " + (System.currentTimeMillis() - st) + "ms");
        System.out.println("Retry counter: " + retryCounter.get());
        assertEquals(counter.get(), numberOfThreads);
        assertEquals(0, retryCounter.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void timeTakenFor500SignInUpParallel() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        ExecutorService ex = Executors.newFixedThreadPool(1000);
        int numberOfThreads = 500;

        ThirdParty.signInUp(process.getProcess(), "google", "google-user", "test@example.com");
        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger retryCounter = new AtomicInteger(0);

        ThirdParty.signInUp(process.getProcess(), "google", "google-user", "test@example.com");

        long st = System.currentTimeMillis();
        for (int i = 0; i < numberOfThreads; i++) {
            ex.execute(() -> {
                while(true) {
                    try {
                        ThirdParty.signInUp(process.getProcess(), "google", "google-user", "test@example.com");
                        counter.incrementAndGet();
                        break;
                    } catch (StorageQueryException e) {
                        retryCounter.incrementAndGet();
                        // continue
                    } catch (EmailChangeNotAllowedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        ex.shutdown();

        ex.awaitTermination(2, TimeUnit.MINUTES);
        System.out.println("Time taken for " + numberOfThreads + " sign in parallel: " + (System.currentTimeMillis() - st) + "ms");
        System.out.println("Retry counter: " + retryCounter.get());
        assertEquals (counter.get(), numberOfThreads);
        assertEquals(0, retryCounter.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
