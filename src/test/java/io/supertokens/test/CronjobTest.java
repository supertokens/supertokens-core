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
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.cronjobs.bulkimport.ProcessBulkImportUsers;
import io.supertokens.cronjobs.cleanupOAuthSessionsAndChallenges.CleanupOAuthSessionsAndChallenges;
import io.supertokens.cronjobs.cleanupWebauthnExpiredData.CleanUpWebauthNExpiredDataCron;
import io.supertokens.cronjobs.syncCoreConfigWithDb.SyncCoreConfigWithDb;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.MultitenancyHelper;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.OAuthStorage;
import io.supertokens.pluginInterface.webauthn.WebAuthNOptions;
import io.supertokens.pluginInterface.webauthn.WebAuthNStorage;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.reflections.Reflections;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class CronjobTest {

    static int normalCronjobCounter = 0;
    static int errorCronjobCounter = 0;

    static class QuitProgramExceptionCronjob extends CronTask {

        private static final String RESOURCE_ID = "io.supertokens.test.CronjobTest" + ".QuitProgramExceptionCronjob";

        private QuitProgramExceptionCronjob(Main main, List<List<TenantIdentifier>> tenants) {
            super("QuitProgramExceptionCronjob", main, tenants, false);
        }

        public static QuitProgramExceptionCronjob getInstance(Main main) {
            try {
                return (QuitProgramExceptionCronjob) main.getResourceDistributor()
                        .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
            } catch (TenantOrAppNotFoundException e) {
                List<TenantIdentifier> tenants = new ArrayList<>();
                tenants.add(new TenantIdentifier(null, null, null));
                List<List<TenantIdentifier>> finalList = new ArrayList<>();
                finalList.add(tenants);
                return (QuitProgramExceptionCronjob) main.getResourceDistributor()
                        .setResource(new TenantIdentifier(null, null, null), RESOURCE_ID,
                                new QuitProgramExceptionCronjob(main, finalList));
            }
        }

        @Override
        protected void doTaskPerStorage(Storage storage) {
            throw new QuitProgramException("Cronjob Threw QuitProgramException");

        }

        @Override
        public int getIntervalTimeSeconds() {
            return 1;
        }

        @Override
        public int getInitialWaitTimeSeconds() {
            return 0;
        }
    }

    static class ErrorCronjob extends CronTask {

        private static final String RESOURCE_ID = "io.supertokens.test.CronjobTest.ErrorCronjob";

        private ErrorCronjob(Main main, List<List<TenantIdentifier>> tenants) {
            super("ErrorCronjob", main, tenants, false);
        }

        public static ErrorCronjob getInstance(Main main) {
            try {
                return (ErrorCronjob) main.getResourceDistributor()
                        .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
            } catch (TenantOrAppNotFoundException e) {
                List<TenantIdentifier> tenants = new ArrayList<>();
                tenants.add(new TenantIdentifier(null, null, null));
                List<List<TenantIdentifier>> finalList = new ArrayList<>();
                finalList.add(tenants);
                return (ErrorCronjob) main.getResourceDistributor()
                        .setResource(RESOURCE_ID, new ErrorCronjob(main, finalList));
            }
        }

        @Override
        protected void doTaskPerStorage(Storage s) throws Exception {
            errorCronjobCounter++;
            throw new Exception("ERROR thrown from ErrorCronjobTest");

        }

        @Override
        public int getIntervalTimeSeconds() {
            return 1;
        }

        @Override
        public int getInitialWaitTimeSeconds() {
            return 0;
        }
    }

    static class NormalCronjob extends CronTask {

        private static final String RESOURCE_ID = "io.supertokens.test.CronjobTest.NormalCronjob";

        private NormalCronjob(Main main, List<List<TenantIdentifier>> tenants) {
            super("NormalCronjob", main, tenants, false);
        }

        public static NormalCronjob getInstance(Main main) {
            try {
                return (NormalCronjob) main.getResourceDistributor()
                        .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
            } catch (TenantOrAppNotFoundException e) {
                List<TenantIdentifier> tenants = new ArrayList<>();
                tenants.add(new TenantIdentifier(null, null, null));
                List<List<TenantIdentifier>> finalList = new ArrayList<>();
                finalList.add(tenants);
                return (NormalCronjob) main.getResourceDistributor()
                        .setResource(new TenantIdentifier(null, null, null), RESOURCE_ID,
                                new NormalCronjob(main, finalList));
            }
        }

        @Override
        protected void doTaskPerStorage(Storage s) {
            normalCronjobCounter++;
        }

        @Override
        public int getIntervalTimeSeconds() {
            return 1;
        }

        @Override
        public int getInitialWaitTimeSeconds() {
            return 0;
        }
    }

    static class TargetTenantCronjob extends CronTask {
        private static final String RESOURCE_ID = "io.supertokens.test.CronjobTest.NormalCronjob";

        private TargetTenantCronjob(Main main, TenantIdentifier tenantIdentifier) {
            super("TargetTenantCronjob", main, tenantIdentifier);
        }

        private boolean wasCalled = false;

        public static TargetTenantCronjob getInstance(Main main, TenantIdentifier tenantIdentifier) {
            try {
                return (TargetTenantCronjob) main.getResourceDistributor().getResource(tenantIdentifier, RESOURCE_ID);
            } catch (TenantOrAppNotFoundException e) {
                return (TargetTenantCronjob) main.getResourceDistributor()
                        .setResource(tenantIdentifier, RESOURCE_ID, new TargetTenantCronjob(main, tenantIdentifier));
            }
        }

        @Override
        public int getIntervalTimeSeconds() {
            return 1;
        }

        @Override
        public int getInitialWaitTimeSeconds() {
            return 0;
        }

        @Override
        protected void doTaskForTargetTenant(TenantIdentifier targetTenant) throws Exception {
            wasCalled = true;
        }

    }

    static class PerTenantCronjob extends CronTask {
        private static final String RESOURCE_ID = "io.supertokens.test.CronjobTest.NormalCronjob";

        private PerTenantCronjob(Main main, List<List<TenantIdentifier>> tenantsInfo) {
            super("PerTenantCronjob", main, tenantsInfo, false);
        }

        Set<TenantIdentifier> tenantIdentifiers = new HashSet<>();

        public static PerTenantCronjob getInstance(Main main, List<List<TenantIdentifier>> tenantsInfo) {
            try {
                return (PerTenantCronjob) main.getResourceDistributor()
                        .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
            } catch (TenantOrAppNotFoundException e) {
                return (PerTenantCronjob) main.getResourceDistributor()
                        .setResource(new TenantIdentifier(null, null, null), RESOURCE_ID,
                                new PerTenantCronjob(main, tenantsInfo));
            }
        }

        @Override
        public int getIntervalTimeSeconds() {
            return 1;
        }

        @Override
        public int getInitialWaitTimeSeconds() {
            return 0;
        }

        @Override
        protected void doTaskPerTenant(TenantIdentifier tenant) throws Exception {
            tenantIdentifiers.add(tenant);
        }

    }

    static class PerAppCronjob extends CronTask {
        private static final String RESOURCE_ID = "io.supertokens.test.CronjobTest.NormalCronjob";

        private PerAppCronjob(Main main, List<List<TenantIdentifier>> tenantsInfo) {
            super("PerTenantCronjob", main, tenantsInfo, true);
        }

        Set<AppIdentifier> appIdentifiers = new HashSet<>();

        public static PerAppCronjob getInstance(Main main, List<List<TenantIdentifier>> tenantsInfo) {
            try {
                return (PerAppCronjob) main.getResourceDistributor()
                        .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
            } catch (TenantOrAppNotFoundException e) {
                return (PerAppCronjob) main.getResourceDistributor()
                        .setResource(new TenantIdentifier(null, null, null), RESOURCE_ID,
                                new PerAppCronjob(main, tenantsInfo));
            }
        }

        @Override
        public int getIntervalTimeSeconds() {
            return 1;
        }

        @Override
        public int getInitialWaitTimeSeconds() {
            return 0;
        }

        @Override
        protected void doTaskPerApp(AppIdentifier app) throws Exception {
            appIdentifiers.add(app);
        }
    }

    static class PerUserPoolCronjob extends CronTask {
        private static final String RESOURCE_ID = "io.supertokens.test.CronjobTest.NormalCronjob";

        private PerUserPoolCronjob(Main main, List<List<TenantIdentifier>> tenantsInfo) {
            super("PerTenantCronjob", main, tenantsInfo, false);
        }

        Set<Storage> storages = new HashSet<>();

        public static PerUserPoolCronjob getInstance(Main main, List<List<TenantIdentifier>> tenantsInfo) {
            try {
                return (PerUserPoolCronjob) main.getResourceDistributor()
                        .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
            } catch (TenantOrAppNotFoundException e) {
                return (PerUserPoolCronjob) main.getResourceDistributor()
                        .setResource(new TenantIdentifier(null, null, null), RESOURCE_ID,
                                new PerUserPoolCronjob(main, tenantsInfo));
            }
        }

        @Override
        public int getIntervalTimeSeconds() {
            return 1;
        }

        @Override
        public int getInitialWaitTimeSeconds() {
            return 0;
        }

        @Override
        protected void doTaskPerStorage(Storage storage) throws Exception {
            storages.add(storage);
        }
    }

    /* Per-storage work that sleeps and records concurrency in counters that can be shared between jobs. Each
       user pool is tagged (see taggedPools) so a test can see which pools ran. */
    static class ConcurrencyProbeCronjob extends CronTask {
        final AtomicInteger running;
        final AtomicInteger maxRunning;
        final AtomicInteger finished = new AtomicInteger();
        final List<String> ran = Collections.synchronizedList(new ArrayList<>());
        private final int intervalSeconds;
        private final long workMillis;

        ConcurrencyProbeCronjob(String name, Main main, List<List<TenantIdentifier>> tenantsInfo,
                                int intervalSeconds, long workMillis, AtomicInteger running,
                                AtomicInteger maxRunning) {
            super(name, main, tenantsInfo, false);
            this.intervalSeconds = intervalSeconds;
            this.workMillis = workMillis;
            this.running = running;
            this.maxRunning = maxRunning;
        }

        @Override
        public int getIntervalTimeSeconds() {
            return intervalSeconds;
        }

        @Override
        public int getInitialWaitTimeSeconds() {
            return 0;
        }

        @Override
        protected void doTaskPerStorage(TenantIdentifier representative, Storage storage) throws Exception {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(workMillis);
                ran.add(representative.getConnectionUriDomain());
                finished.incrementAndGet();
            } finally {
                running.decrementAndGet();
            }
        }
    }

    /* Per-tenant work that sleeps, recording the tenants that finished. The sleep is cut off when a pass times out. */
    static class TenantProbeCronjob extends CronTask {
        final List<String> finished = Collections.synchronizedList(new ArrayList<>());
        private final long workMillis;

        TenantProbeCronjob(String name, Main main, List<List<TenantIdentifier>> tenantsInfo, long workMillis) {
            super(name, main, tenantsInfo, false);
            this.workMillis = workMillis;
        }

        @Override
        public int getIntervalTimeSeconds() {
            return 1;
        }

        @Override
        public int getInitialWaitTimeSeconds() {
            return 0;
        }

        @Override
        protected void doTaskPerTenant(TenantIdentifier tenant) throws Exception {
            Thread.sleep(workMillis);
            finished.add(tenant.getTenantId());
        }

        Set<String> finishedSoFar() {
            synchronized (finished) {
                return new HashSet<>(finished);
            }
        }
    }

    /* A job whose storage work does not respond to interrupts until released, like one blocked in a JDBC read. */
    static class IgnoresInterruptCronjob extends CronTask {
        private final CountDownLatch started;
        private final CountDownLatch release;

        IgnoresInterruptCronjob(String name, Main main, List<List<TenantIdentifier>> tenantsInfo,
                                CountDownLatch started, CountDownLatch release) {
            super(name, main, tenantsInfo, false);
            this.started = started;
            this.release = release;
        }

        @Override
        public int getIntervalTimeSeconds() {
            return 600;
        }

        @Override
        public int getInitialWaitTimeSeconds() {
            return 0;
        }

        @Override
        protected void doTaskPerStorage(TenantIdentifier representative, Storage storage) {
            started.countDown();
            while (true) {
                try {
                    release.await();
                    return;
                } catch (InterruptedException e) {
                    // keep blocking, as a socket read would
                }
            }
        }
    }

    /*
     * Distinct user pools that all resolve to the base storage: an unconfigured connectionUriDomain falls back to
     * the base tenant in StorageLayer.getStorage, while each pool keeps its own identity (the domain is its tag).
     */
    /* Copies probe.ran under its lock: iterating a synchronizedList while workers still add to it can throw. */
    private static Set<String> ranSoFar(ConcurrencyProbeCronjob probe) {
        synchronized (probe.ran) {
            return new HashSet<>(probe.ran);
        }
    }

    private static List<List<TenantIdentifier>> taggedPools(int count) {
        List<List<TenantIdentifier>> pools = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pools.add(List.of(new TenantIdentifier("t" + i, null, null)));
        }
        return pools;
    }

    static class CounterCronJob extends CronTask {
        private static final String RESOURCE_ID = "io.supertokens.test.CronjobTest.CounterCronJob";
        private static AtomicInteger count = new AtomicInteger();

        private CounterCronJob(Main main, List<List<TenantIdentifier>> tenantsInfo) {
            super("CounterCronJob", main, tenantsInfo, false);
        }

        public static CounterCronJob getInstance(Main main) {
            try {
                return (CounterCronJob) main.getResourceDistributor()
                        .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
            } catch (TenantOrAppNotFoundException e) {
                List<TenantIdentifier> tenants = new ArrayList<>();
                tenants.add(new TenantIdentifier(null, null, null));
                List<List<TenantIdentifier>> finalList = new ArrayList<>();
                finalList.add(tenants);
                return (CounterCronJob) main.getResourceDistributor()
                        .setResource(new TenantIdentifier(null, null, null), RESOURCE_ID,
                                new CounterCronJob(main, finalList));
            }
        }

        @Override
        public int getIntervalTimeSeconds() {
            return 1;
        }

        @Override
        public int getInitialWaitTimeSeconds() {
            return 0;
        }

        @Override
        protected void doTaskPerStorage(Storage storage) throws Exception {
            count.incrementAndGet();
        }

        public int getCount() {
            return count.get();
        }
    }

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

    @Test
    public void testThatCronjobThrowsQuitProgramExceptionAndQuits() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        Cronjobs.addCronjob(process.getProcess(), QuitProgramExceptionCronjob.getInstance(process.getProcess()));

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        process.kill();
    }

    @Test
    public void testThatCronjobThrowsError() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);

        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Cronjobs.addCronjob(process.getProcess(), ErrorCronjob.getInstance(process.getProcess()));

        ProcessState.EventAndException e = process
                .checkOrWaitForEvent(ProcessState.PROCESS_STATE.CRON_TASK_ERROR_LOGGING);
        assertNotNull(e);
        assertEquals(e.exception.getMessage(), "ERROR thrown from ErrorCronjobTest");

        Thread.sleep(5000);

        assertTrue(errorCronjobCounter >= 4 && errorCronjobCounter <= 8);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testNormalCronjob() throws Exception {
        String[] args = {"../"};

        normalCronjobCounter = 0;

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        assertEquals(normalCronjobCounter, 0);
        Cronjobs.addCronjob(process.getProcess(), NormalCronjob.getInstance(process.getProcess()));

        Thread.sleep(5000);
        assertTrue(normalCronjobCounter > 3 && normalCronjobCounter < 8);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testAddingCronJobTwice() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        int initialSize = Cronjobs.getInstance(process.getProcess()).getTasks().size();

        Cronjobs.addCronjob(process.getProcess(), NormalCronjob.getInstance(process.getProcess()));
        assertEquals(initialSize + 1, Cronjobs.getInstance(process.getProcess()).getTasks().size());

        Cronjobs.addCronjob(process.getProcess(), NormalCronjob.getInstance(process.getProcess()));
        assertEquals(initialSize + 1, Cronjobs.getInstance(process.getProcess()).getTasks().size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAddingTenantsDoesNotIncreaseCronJobs() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        int initialSize = Cronjobs.getInstance(process.getProcess()).getTasks().size();

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a1", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a1", "t1"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a2", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a2", "t2"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);

        assertEquals(initialSize, Cronjobs.getInstance(process.getProcess()).getTasks().size());

        JsonObject config = new JsonObject();
        StorageLayer.getStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a3", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                config
        ), false);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a3", "t1"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                config
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a4", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                config
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a4", "t2"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                config
        ), false);

        assertEquals(initialSize, Cronjobs.getInstance(process.getProcess()).getTasks().size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testTargetTenantCronTask() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a1", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a1", "t1"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a2", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a2", "t2"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);

        Cronjobs.addCronjob(process.getProcess(),
                TargetTenantCronjob.getInstance(process.getProcess(), new TenantIdentifier(null, "a1", "t1")));

        Thread.sleep(1100);
        assertTrue(TargetTenantCronjob.getInstance(process.getProcess(),
                new TenantIdentifier(null, "a1", "t1")).wasCalled);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testPerTenantCronTask() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a1", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a1", "t1"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a2", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a2", "t2"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);

        List<List<TenantIdentifier>> uniqueUserPoolIdsTenants = StorageLayer.getTenantsWithUniqueUserPoolId(
                process.getProcess());
        Cronjobs.addCronjob(process.getProcess(),
                PerTenantCronjob.getInstance(process.getProcess(), uniqueUserPoolIdsTenants));

        Thread.sleep(1100);
        assertEquals(5,
                PerTenantCronjob.getInstance(process.getProcess(), uniqueUserPoolIdsTenants).tenantIdentifiers.size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testPerAppCronTask() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a1", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a1", "t1"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a2", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a2", "t2"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);

        List<List<TenantIdentifier>> uniqueUserPoolIdsTenants = StorageLayer.getTenantsWithUniqueUserPoolId(
                process.getProcess());
        Cronjobs.addCronjob(process.getProcess(),
                PerAppCronjob.getInstance(process.getProcess(), uniqueUserPoolIdsTenants));

        Thread.sleep(1100);
        assertEquals(3,
                PerAppCronjob.getInstance(process.getProcess(), uniqueUserPoolIdsTenants).appIdentifiers.size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testPerUserPoolCronTask() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a1", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a1", "t1"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                new JsonObject()
        ), false);
        JsonObject config = new JsonObject();
        StorageLayer.getBaseStorage(process.getProcess()).modifyConfigToAddANewUserPoolForTesting(config, 1);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a2", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                config
        ), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier(null, "a2", "t2"),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                config
        ), false);

        List<List<TenantIdentifier>> uniqueUserPoolIdsTenants = StorageLayer.getTenantsWithUniqueUserPoolId(
                process.getProcess());
        Cronjobs.addCronjob(process.getProcess(),
                PerUserPoolCronjob.getInstance(process.getProcess(), uniqueUserPoolIdsTenants));

        Thread.sleep(1100);
        assertEquals(2, PerUserPoolCronjob.getInstance(process.getProcess(), uniqueUserPoolIdsTenants).storages.size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testPerStorageWorkIsBoundedByCronStorageParallelismPerJob() throws Exception {
        Utils.setValueInConfig("cron_storage_parallelism", "3");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        assertEquals(3, Cronjobs.getInstance(process.getProcess()).getStorageParallelism());

        // two jobs of twelve user pools each, running at the same time, each with its own counter: the bound is
        // per job, so each stays within 3 while the two together may run up to 6
        ConcurrencyProbeCronjob a = new ConcurrencyProbeCronjob("ProbeA", process.getProcess(), taggedPools(12), 60,
                100, new AtomicInteger(), new AtomicInteger());
        ConcurrencyProbeCronjob b = new ConcurrencyProbeCronjob("ProbeB", process.getProcess(), taggedPools(12), 60,
                100, new AtomicInteger(), new AtomicInteger());
        Cronjobs.addCronjob(process.getProcess(), a);
        Cronjobs.addCronjob(process.getProcess(), b);

        long deadline = System.currentTimeMillis() + 15_000;
        while ((a.finished.get() < 12 || b.finished.get() < 12) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(12, a.finished.get());
        assertEquals(12, b.finished.get());
        for (ConcurrencyProbeCronjob job : List.of(a, b)) {
            assertTrue(job.maxRunning.get() > 1);
            assertTrue("ran " + job.maxRunning.get() + " storages at once", job.maxRunning.get() <= 3);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testAStuckJobDoesNotDelayAnotherJob() throws Exception {
        Utils.setValueInConfig("cron_storage_parallelism", "2");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // the stuck job holds every one of its workers for a minute; the quick job must still finish all its
        // storages long before that, on its own workers
        ConcurrencyProbeCronjob stuck = new ConcurrencyProbeCronjob("ProbeStuck", process.getProcess(),
                taggedPools(4), 600, 60_000, new AtomicInteger(), new AtomicInteger());
        ConcurrencyProbeCronjob quick = new ConcurrencyProbeCronjob("ProbeQuick", process.getProcess(),
                taggedPools(6), 600, 50, new AtomicInteger(), new AtomicInteger());
        Cronjobs.addCronjob(process.getProcess(), stuck);
        Thread.sleep(300);
        Cronjobs.addCronjob(process.getProcess(), quick);

        long deadline = System.currentTimeMillis() + 10_000;
        while (quick.finished.get() < 6 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(6, quick.finished.get());
        assertEquals(0, stuck.finished.get());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testShutdownWaitsOnceForAllStuckStoragePools() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // four jobs, each with a worker that ignores the shutdown interrupt
        int jobs = 4;
        CountDownLatch started = new CountDownLatch(jobs);
        CountDownLatch release = new CountDownLatch(1);
        for (int i = 0; i < jobs; i++) {
            Cronjobs.addCronjob(process.getProcess(), new IgnoresInterruptCronjob("ProbeStuckAtShutdown" + i,
                    process.getProcess(), taggedPools(1), started, release));
        }
        long tookMillis;
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS));

            long start = System.currentTimeMillis();
            Cronjobs.shutdownAndAwaitTermination(process.getProcess());
            tookMillis = System.currentTimeMillis() - start;
        } finally {
            // the workers ignore interrupts: without this a failed assertion leaves them blocked for the whole run
            release.countDown();
        }

        // the four pools share one 5s wait; waiting 5s for each in turn would take 20s
        assertTrue("shutdown took " + tookMillis + "ms", tookMillis < 12_000);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testATimedOutPassResumesAtTheStoragesItDidNotReach() throws Exception {
        Utils.setValueInConfig("cron_storage_parallelism", "1");
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // one storage at a time, 400 ms each, 1 s per pass: a pass reaches only the first two or three of ten
        // pools. Without resuming, every pass would start again at t0 and t9 would never run.
        ConcurrencyProbeCronjob probe = new ConcurrencyProbeCronjob("ProbeResume", process.getProcess(),
                taggedPools(10), 1, 400, new AtomicInteger(), new AtomicInteger());
        Cronjobs.addCronjob(process.getProcess(), probe);

        long deadline = System.currentTimeMillis() + 20_000;
        while (!ranSoFar(probe).contains("t9") && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        Set<String> ran = ranSoFar(probe);
        for (int i = 0; i < 10; i++) {
            assertTrue("t" + i + " never ran: " + ran, ran.contains("t" + i));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testATimedOutPassResumesAtTheTenantsItDidNotReach() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // one storage with four tenants, 700 ms each, 1 s per pass: a pass finishes one tenant and is cut off in the
        // next. Restarting at a0 every pass would only ever finish a0; skipping the cut-off tenant would only ever
        // finish a0 and a2.
        List<TenantIdentifier> tenants = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tenants.add(new TenantIdentifier("tenantresume", null, "a" + i));
        }
        TenantProbeCronjob probe = new TenantProbeCronjob("ProbeTenantResume", process.getProcess(),
                List.of(tenants), 700);
        Cronjobs.addCronjob(process.getProcess(), probe);

        long deadline = System.currentTimeMillis() + 20_000;
        while (probe.finishedSoFar().size() < 4 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertEquals(Set.of("a0", "a1", "a2", "a3"), probe.finishedSoFar());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testEveryCronJobOverridesTheHookItsModeCalls() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // run() calls only doTaskPerApp for a per-app job and never doTaskPerStorage / doTaskPerTenant, so a job
        // that overrides the other mode's hook runs on schedule and does nothing
        for (CronTask task : Cronjobs.getInstance(process.getProcess()).getTasks()) {
            Set<String> hooks = new HashSet<>();
            for (Class<?> c = task.getClass(); c != CronTask.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (m.getName().startsWith("doTask")) {
                        hooks.add(m.getName());
                    }
                }
            }
            String name = task.getClass().getName();
            if (task.isPerApp()) {
                assertTrue(name + " is per app but overrides " + hooks, hooks.equals(Set.of("doTaskPerApp")));
            } else {
                assertFalse(name + " is per storage but overrides doTaskPerApp", hooks.contains("doTaskPerApp"));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testWebauthnAndOAuthCleanupJobsDeleteExpiredRows() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        CronTaskTest.getInstance(process.getProcess())
                .setIntervalInSeconds(CleanUpWebauthNExpiredDataCron.RESOURCE_KEY, 1);
        CronTaskTest.getInstance(process.getProcess())
                .setIntervalInSeconds(CleanupOAuthSessionsAndChallenges.RESOURCE_KEY, 1);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        TenantIdentifier tenant = process.getAppForTesting();
        Storage storage = StorageLayer.getStorage(tenant, process.getProcess());

        long now = System.currentTimeMillis();
        WebAuthNOptions options = new WebAuthNOptions();
        options.generatedOptionsId = "expired-options";
        options.relyingPartyId = "example.com";
        options.relyingPartyName = "example";
        options.userEmail = "user@example.com";
        options.timeout = 60_000L;
        options.challenge = "Y2hhbGxlbmdl";
        options.origin = "https://example.com";
        options.createdAt = now - 120_000;
        options.expiresAt = now - 60_000;
        options.userPresenceRequired = true;
        options.userVerification = "preferred";
        WebAuthNStorage webauthn = StorageUtils.getWebAuthNStorage(storage);
        webauthn.saveGeneratedOptions(tenant, options);

        OAuthStorage oauth = StorageUtils.getOAuthStorage(storage);
        oauth.addOrUpdateOauthClient(tenant.toAppIdentifier(), "cleanup-client", "secret", false, false);
        oauth.addOAuthLogoutChallenge(tenant.toAppIdentifier(), "expired-challenge", "cleanup-client", null, null,
                null, 0);

        long deadline = System.currentTimeMillis() + 10_000;
        while ((webauthn.loadOptionsById(tenant, "expired-options") != null
                || oauth.getOAuthLogoutChallenge(tenant.toAppIdentifier(), "expired-challenge") != null)
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertNull(webauthn.loadOptionsById(tenant, "expired-options"));
        assertNull(oauth.getOAuthLogoutChallenge(tenant.toAppIdentifier(), "expired-challenge"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCronStorageParallelismDefaultsTo4AndMustBePositive() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        assertEquals(4, Config.getBaseConfig(process.getProcess()).getCronStorageParallelism());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

        Utils.setValueInConfig("cron_storage_parallelism", "0");
        process = TestingProcessManager.startIsolatedProcess(args);
        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("Provided cron_storage_parallelism must be >= 1", e.exception.getCause().getMessage());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testInitialJitterOnlyForJobsWhoseFirstRunIsAFullIntervalAway() {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            // first run a full interval after start: offset within one interval, capped at an hour
            long hourly = Cronjobs.initialJitterSeconds(3600, 3600);
            assertTrue(hourly >= 0 && hourly < 3600);
            seen.add(hourly);
            long daily = Cronjobs.initialJitterSeconds(24 * 3600, 24 * 3600);
            assertTrue(daily >= 0 && daily < Cronjobs.MAX_INITIAL_JITTER_SECONDS);
        }
        // jobs scheduled together end up at different offsets
        assertTrue(seen.size() > 100);

        // jobs that start sooner on purpose keep their own timing: the license check (30-60s, daily interval),
        // telemetry (60-120s), bulk import (0s, 5 min) and the backfill (10s, 5 min)
        assertEquals(0, Cronjobs.initialJitterSeconds(45, 24 * 3600));
        assertEquals(0, Cronjobs.initialJitterSeconds(90, 24 * 3600));
        assertEquals(0, Cronjobs.initialJitterSeconds(0, 300));
        assertEquals(0, Cronjobs.initialJitterSeconds(10, 300));
        assertEquals(0, Cronjobs.initialJitterSeconds(0, 0));
    }

    @Test
    public void testThatCoreAutomaticallySyncsToConfigChangesInDb() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        CronTaskTest.getInstance(process.getProcess()).setIntervalInSeconds(SyncCoreConfigWithDb.RESOURCE_KEY,
                3);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                t1,
                new EmailPasswordConfig(false),
                new ThirdPartyConfig(false, null),
                new PasswordlessConfig(false),
                null, null,
                new JsonObject()
        ), false);

        boolean found = false;

        TenantConfig[] allTenants = MultitenancyHelper.getInstance(process.getProcess()).getAllTenants();
        for (TenantConfig tenant : allTenants) {
            if (tenant.tenantIdentifier.equals(t1)) {
                assertFalse(tenant.emailPasswordConfig.enabled);
                found = true;
            }
        }
        assertTrue(found);

        MultitenancyStorage storage = (MultitenancyStorage) StorageLayer.getStorage(process.getProcess());
        storage.overwriteTenantConfig(new TenantConfig(
                t1,
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(false, null),
                new PasswordlessConfig(false),
                null, null,
                new JsonObject()
        ));

        // Check that it was not updated in memory yet
        found = false;
        allTenants = MultitenancyHelper.getInstance(process.getProcess()).getAllTenants();
        for (TenantConfig tenant : allTenants) {
            if (tenant.tenantIdentifier.equals(t1)) {
                assertFalse(tenant.emailPasswordConfig.enabled);
                found = true;
            }
        }
        assertTrue(found);

        // Wait for the cronjob to run
        Thread.sleep(3100);

        // Check that it was updated in memory by now
        found = false;
        allTenants = MultitenancyHelper.getInstance(process.getProcess()).getAllTenants();
        for (TenantConfig tenant : allTenants) {
            if (tenant.tenantIdentifier.equals(t1)) {
                assertTrue(tenant.emailPasswordConfig.enabled);
                found = true;
            }
        }
        assertTrue(found);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatReAddingSameCronTaskDoesNotScheduleMoreExecutors() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        for (int i = 0; i < 10; i++) {
            Cronjobs.addCronjob(process.getProcess(), CounterCronJob.getInstance(process.getProcess()));
            Thread.sleep(50);
        }

        Thread.sleep(5000);
        assertTrue(CounterCronJob.getInstance(process.getProcess()).getCount() > 3 &&
                CounterCronJob.getInstance(process.getProcess()).getCount() < 10);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatNoCronJobIntervalIsMoreThanADay() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // ensure none of the tasks have an interval more than a day
        for (CronTask task : Cronjobs.getInstance(process.getProcess()).getTasks()) {
            assertTrue(task.getIntervalTimeSeconds() <= 3600 * 24);
            assertTrue(task.getInitialWaitTimeSeconds() <= 3600 * 24);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatCronJobsHaveTenantsInfoAfterRestart() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (StorageLayer.isInMemDb(process.getProcess())) {
            return;
        }

        JsonObject coreConfig = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(coreConfig, 1);

        // create CUD and apps
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier("127.0.0.1", null, null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                coreConfig
        ), false, false, true);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier("127.0.0.1", "a1", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                coreConfig
        ), false, false, true);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier("127.0.0.1", "a2", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                coreConfig
        ), false, false, true);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                new TenantIdentifier("127.0.0.1", "a3", null),
                new EmailPasswordConfig(true),
                new ThirdPartyConfig(true, null),
                new PasswordlessConfig(true),
                null, null,
                coreConfig
        ), false, false, true);

        {
            List<List<List<TenantIdentifier>>> tenantsInfos = Cronjobs.getInstance(process.getProcess())
                    .getTenantInfos();
            assertEquals(17, tenantsInfos.size());
            int count = 0;
            for (List<List<TenantIdentifier>> tenantsInfo : tenantsInfos) {
                if (tenantsInfo != null) {
                    assertEquals(2, tenantsInfo.size());
                    assertTrue(
                        (tenantsInfo.get(0).size() == 1 && tenantsInfo.get(1).size() == 4) ||
                        (tenantsInfo.get(1).size() == 1 && tenantsInfo.get(0).size() == 4)
                    );
                    count++;
                }
            }
            assertEquals(16, count);
        }

        process.kill(false);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));


        process = TestingProcessManager.startIsolatedProcess(args, false);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // we expect the state of the tenantsInfo to be same after core restart
        {
            List<List<List<TenantIdentifier>>> tenantsInfos = Cronjobs.getInstance(process.getProcess())
                    .getTenantInfos();
            assertEquals(17, tenantsInfos.size());
            int count = 0;
            for (List<List<TenantIdentifier>> tenantsInfo : tenantsInfos) {
                if (tenantsInfo != null) {
                    assertEquals(2, tenantsInfo.size());
                    assertTrue(
                        (tenantsInfo.get(0).size() == 1 && tenantsInfo.get(1).size() == 4) ||
                        (tenantsInfo.get(0).size() == 4 && tenantsInfo.get(1).size() == 1)
                    );
                    count++;
                }
            }
            assertEquals(16, count);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatThereAreTasksOfAllCronTaskClassesAndHaveCorrectIntervals() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Reflections reflections = new Reflections("io.supertokens");
        Set<Class<? extends CronTask>> classes = reflections.getSubTypesOf(CronTask.class);

        Set<String> classNames = new HashSet<>();

        for (Class cls : classes) {
            if (!cls.getName().contains("io.supertokens.test")) {
                classNames.add(cls.getName());
            }
        }

        // Note that the time is in seconds
        Map<String, Integer> intervals = new HashMap<>();
        intervals.put("io.supertokens.ee.cronjobs.EELicenseCheck", 86400);
        intervals.put("io.supertokens.cronjobs.syncCoreConfigWithDb.SyncCoreConfigWithDb", 60);
        intervals.put("io.supertokens.cronjobs.deleteExpiredSessions.DeleteExpiredSessions", 43200);
        intervals.put("io.supertokens.cronjobs.deleteExpiredPasswordResetTokens.DeleteExpiredPasswordResetTokens",
                3600);
        intervals.put(
                "io.supertokens.cronjobs.deleteExpiredEmailVerificationTokens.DeleteExpiredEmailVerificationTokens",
                43200);
        intervals.put("io.supertokens.cronjobs.deleteExpiredPasswordlessDevices.DeleteExpiredPasswordlessDevices",
                3600);
        intervals.put("io.supertokens.cronjobs.deleteExpiredTotpTokens.DeleteExpiredTotpTokens", 3600);
        intervals.put("io.supertokens.cronjobs.deleteExpiredDashboardSessions.DeleteExpiredDashboardSessions", 43200);
        intervals.put("io.supertokens.cronjobs.telemetry.Telemetry", 86400);
        intervals.put("io.supertokens.cronjobs.deleteExpiredAccessTokenSigningKeys.DeleteExpiredAccessTokenSigningKeys",
                86400);
        intervals.put("io.supertokens.cronjobs.bulkimport.ProcessBulkImportUsers", 300);
        intervals.put("io.supertokens.cronjobs.cleanupOAuthSessionsAndChallenges.CleanupOAuthSessionsAndChallenges",
                86400);
        intervals.put("io.supertokens.cronjobs.cleanupWebauthnExpiredData.CleanUpWebauthNExpiredDataCron", 86400);
        intervals.put("io.supertokens.cronjobs.deleteExpiredSAMLData.DeleteExpiredSAMLData", 3600);
        intervals.put("io.supertokens.cronjobs.backfill.BackfillReservationTables", 300);
        intervals.put(
                "io.supertokens.cronjobs.cleanupActivityLogPartitions.CleanupActivityLogPartitions", 86400);
        intervals.put("io.supertokens.cronjobs.rollupUserLastActive.RollupUserLastActive", 600);

        Map<String, Integer> delays = new HashMap<>();
        // EELicenseCheck now performs the startup license sync itself (it used to run synchronously in
        // EEFeatureFlag's constructor), so its initial delay is a short jittered value in [30, 60) seconds
        // instead of a full day. It is asserted as a range below rather than an exact value.
        String eeLicenseCheckClassName = "io.supertokens.ee.cronjobs.EELicenseCheck";
        delays.put("io.supertokens.cronjobs.syncCoreConfigWithDb.SyncCoreConfigWithDb", 0);
        delays.put("io.supertokens.cronjobs.deleteExpiredSessions.DeleteExpiredSessions", 0);
        delays.put("io.supertokens.cronjobs.deleteExpiredPasswordResetTokens.DeleteExpiredPasswordResetTokens", 0);
        delays.put("io.supertokens.cronjobs.deleteExpiredEmailVerificationTokens.DeleteExpiredEmailVerificationTokens",
                0);
        delays.put("io.supertokens.cronjobs.deleteExpiredPasswordlessDevices.DeleteExpiredPasswordlessDevices", 0);
        delays.put("io.supertokens.cronjobs.deleteExpiredTotpTokens.DeleteExpiredTotpTokens", 0);
        delays.put("io.supertokens.cronjobs.deleteExpiredDashboardSessions.DeleteExpiredDashboardSessions", 0);
        delays.put("io.supertokens.cronjobs.telemetry.Telemetry", 0);
        delays.put("io.supertokens.cronjobs.deleteExpiredAccessTokenSigningKeys.DeleteExpiredAccessTokenSigningKeys",
                0);
        delays.put("io.supertokens.cronjobs.bulkimport.ProcessBulkImportUsers", 0);
        delays.put("io.supertokens.cronjobs.cleanupOAuthSessionsAndChallenges.CleanupOAuthSessionsAndChallenges",
                0);
        delays.put("io.supertokens.cronjobs.cleanupWebauthnExpiredData.CleanUpWebauthNExpiredDataCron", 0);
        delays.put("io.supertokens.cronjobs.deleteExpiredSAMLData.DeleteExpiredSAMLData", 0);
        delays.put("io.supertokens.cronjobs.backfill.BackfillReservationTables", 10);
        delays.put("io.supertokens.cronjobs.cleanupActivityLogPartitions.CleanupActivityLogPartitions", 0);
        delays.put("io.supertokens.cronjobs.rollupUserLastActive.RollupUserLastActive", 0);

        List<CronTask> allTasks = Cronjobs.getInstance(process.getProcess()).getTasks();
        assertEquals(17, allTasks.size());

        for (CronTask task : allTasks) {
            assertEquals(intervals.get(task.getClass().getName()).intValue(), task.getIntervalTimeSeconds());
            if (task.getClass().getName().equals(eeLicenseCheckClassName)) {
                int delay = task.getInitialWaitTimeSeconds();
                assertTrue("EELicenseCheck initial delay should be jittered in [30, 60), was " + delay,
                        delay >= 30 && delay < 60);
            } else {
                assertEquals(delays.get(task.getClass().getName()).intValue(), task.getInitialWaitTimeSeconds());
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatIsCronJobLoadedReturnsTheGoodValues() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        boolean isLoaded = Cronjobs.isCronjobLoaded(process.getProcess(), CounterCronJob.getInstance(process.getProcess()));

        assertFalse(isLoaded);

        Cronjobs.addCronjob(process.getProcess(), CounterCronJob.getInstance(process.getProcess()));
        isLoaded = Cronjobs.isCronjobLoaded(process.getProcess(), CounterCronJob.getInstance(process.getProcess()));

        assertTrue(isLoaded);

        Cronjobs.removeCronjob(process.getProcess(), CounterCronJob.getInstance(process.getProcess()));
        isLoaded = Cronjobs.isCronjobLoaded(process.getProcess(), CounterCronJob.getInstance(process.getProcess()));

        assertFalse(isLoaded);

        //removing twice doesn't do anything funky
        Cronjobs.removeCronjob(process.getProcess(), CounterCronJob.getInstance(process.getProcess()));
        isLoaded = Cronjobs.isCronjobLoaded(process.getProcess(), CounterCronJob.getInstance(process.getProcess()));

        assertFalse(isLoaded);

        //adding twice doesn't do anything funky
        Cronjobs.addCronjob(process.getProcess(), CounterCronJob.getInstance(process.getProcess()));
        isLoaded = Cronjobs.isCronjobLoaded(process.getProcess(), CounterCronJob.getInstance(process.getProcess()));

        assertTrue(isLoaded);
        Cronjobs.addCronjob(process.getProcess(), CounterCronJob.getInstance(process.getProcess()));
        isLoaded = Cronjobs.isCronjobLoaded(process.getProcess(), CounterCronJob.getInstance(process.getProcess()));

        assertTrue(isLoaded);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatBulkMigrationCronJobLoadedWhenNoEnvVarSet() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Main main = process.getProcess();
        ResourceDistributor resourceDistributor = main.getResourceDistributor();
        ResourceDistributor.SingletonResource bulkImportCron = resourceDistributor.getResource(ProcessBulkImportUsers.RESOURCE_KEY);
        assertNull(System.getenv("BULK_MIGRATION_CRON_ENABLED"));
        assertNotNull(bulkImportCron);
        assertTrue(bulkImportCron instanceof ProcessBulkImportUsers);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatBulkMigrationCronJobLoadedWhenEnvVarSetToTrue() throws Exception {
        String[] args = {"../"};
        Map<String, String> originalValues = setEnv(Map.of("BULK_MIGRATION_CRON_ENABLED", "true"));
        try {
            TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            Main main = process.getProcess();
            ResourceDistributor resourceDistributor = main.getResourceDistributor();
            ResourceDistributor.SingletonResource bulkImportCron = resourceDistributor.getResource(ProcessBulkImportUsers.RESOURCE_KEY);
            assertEquals("true", System.getenv("BULK_MIGRATION_CRON_ENABLED"));
            assertNotNull(bulkImportCron);
            assertTrue(bulkImportCron instanceof ProcessBulkImportUsers);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        } finally {
            restoreEnv(originalValues);
        }
    }

    @Test
    public void testThatBulkMigrationCronJobNotLoadedWhenEnvVarSetToFalse() throws Exception {
        String[] args = {"../"};
        Map<String, String> originalValues = setEnv(Map.of("BULK_MIGRATION_CRON_ENABLED", "false"));
        try {
            TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            Main main = process.getProcess();
            ResourceDistributor resourceDistributor = main.getResourceDistributor();
            ResourceDistributor.SingletonResource bulkImportCron = resourceDistributor.getResource(ProcessBulkImportUsers.RESOURCE_KEY);
            assertEquals("false", System.getenv("BULK_MIGRATION_CRON_ENABLED"));
            assertNull(bulkImportCron);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        } finally {
            restoreEnv(originalValues);
        }
    }

    private static Map<String, String> getWritableEnv() {
        try {
            Map<String, String> env = System.getenv();
            Class<?> cl = env.getClass();
            Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            return (Map<String, String>) field.get(env);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get writable environment", e);
        }
    }

    protected static Map<String, String> setEnv(Map<String, String> newenv) {
        Map<String, String> originalValues = new HashMap<>();
        Map<String, String> writableEnv = getWritableEnv();
        for (String key : newenv.keySet()) {
            originalValues.put(key, writableEnv.get(key));
            writableEnv.put(key, newenv.get(key));
        }
        return originalValues;
    }

    protected static void restoreEnv(Map<String, String> originalValues) {
        Map<String, String> writableEnv = getWritableEnv();
        for (Map.Entry<String, String> entry : originalValues.entrySet()) {
            if (entry.getValue() == null) {
                writableEnv.remove(entry.getKey());
            } else {
                writableEnv.put(entry.getKey(), entry.getValue());
            }
        }
    }

}
