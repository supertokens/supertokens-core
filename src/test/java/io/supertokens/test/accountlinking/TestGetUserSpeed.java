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

package io.supertokens.test.accountlinking;

import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.version.Version;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertNotNull;

public class TestGetUserSpeed {
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

    public void testUserCreationLinkingAndGetByIdSpeedsCommon(TestingProcessManager.TestingProcess process,
                                                              long createTime, long linkingTime, long getTime)
            throws Exception {

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        int numberOfUsers = 10000;

        List<String> userIds = new ArrayList<>();
        List<String> userIds2 = new ArrayList<>();
        Lock lock = new ReentrantLock();
        {
            ExecutorService es = Executors.newFixedThreadPool(32);
            long start = System.currentTimeMillis();
            for (int i = 0; i < numberOfUsers; i++) {
                int finalI = i;
                es.execute(() -> {
                    try {
                        String email = "user" + finalI + "@example.com";
                        AuthRecipeUserInfo user = ThirdParty.signInUp(
                                process.getProcess(), "google", "googleid" + finalI, email).user;
                        lock.lock();
                        userIds.add(user.getSupertokensUserId());
                        userIds2.add(user.getSupertokensUserId());
                        lock.unlock();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            es.shutdown();
            es.awaitTermination(5, TimeUnit.MINUTES);

            long end = System.currentTimeMillis();
            System.out.println("Created users " + numberOfUsers + " in " + (end - start) + "ms");
            assert end - start < createTime; // 25 sec
        }

        Thread.sleep(10000); // wait for index

        {
            // Randomly link accounts
            long start = System.currentTimeMillis();
            ExecutorService es = Executors.newFixedThreadPool(32);
            AtomicInteger numberOflinks = new AtomicInteger(0);

            while (userIds.size() > 0) {
                int numUsersToLink = new Random().nextInt(3) + 1;
                if (numUsersToLink > userIds.size()) {
                    numUsersToLink = userIds.size();
                }
                List<String> usersToLink = new ArrayList<>();
                for (int i = 0; i < numUsersToLink; i++) {
                    int index = new Random().nextInt(userIds.size());
                    usersToLink.add(userIds.get(index));
                    userIds.remove(index);
                }
                numberOflinks.incrementAndGet();

                es.execute(() -> {
                    try {
                        AuthRecipe.createPrimaryUser(process.getProcess(), usersToLink.get(0));
                        for (int i = 1; i < usersToLink.size(); i++) {
                            AuthRecipe.linkAccounts(process.getProcess(), usersToLink.get(i), usersToLink.get(0));
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

            }
            es.shutdown();
            es.awaitTermination(5, TimeUnit.MINUTES);
            long end = System.currentTimeMillis();
            System.out.println("Accounts linked in " + (end - start) + "ms");
            assert end - start < linkingTime; // 50 sec
        }

        Thread.sleep(10000); // wait for index

        {
            ExecutorService es = Executors.newFixedThreadPool(32);
            long start = System.currentTimeMillis();
            for (String userId : userIds2) {
                es.execute(() -> {
                    try {
                        AuthRecipe.getUserById(process.getProcess(), userId);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            es.shutdown();
            es.awaitTermination(5, TimeUnit.MINUTES);
            long end = System.currentTimeMillis();
            System.out.println("Time taken for " + numberOfUsers + " users: " + (end - start) + "ms");
            assert end - start < getTime; // 20 sec
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUserCreationLinkingAndGetByIdSpeedsWithoutMinIdle() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        Utils.setValueInConfig("postgresql_connection_pool_size", "100");
        Utils.setValueInConfig("mysql_connection_pool_size", "100");

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        testUserCreationLinkingAndGetByIdSpeedsCommon(process, 25000, 50000, 20000);
    }

    @Test
    public void testUserCreationLinkingAndGetByIdSpeedsWithMinIdle() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        Utils.setValueInConfig("postgresql_connection_pool_size", "100");
        Utils.setValueInConfig("mysql_connection_pool_size", "100");
        Utils.setValueInConfig("postgresql_minimum_idle_connections", "1");
        Utils.setValueInConfig("mysql_minimum_idle_connections", "1");

        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{
                        EE_FEATURES.ACCOUNT_LINKING, EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        testUserCreationLinkingAndGetByIdSpeedsCommon(process, 60000, 50000, 20000);
    }
}
