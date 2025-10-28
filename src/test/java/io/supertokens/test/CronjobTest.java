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
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.cronjobs.bulkimport.ProcessBulkImportUsers;
import io.supertokens.cronjobs.syncCoreConfigWithDb.SyncCoreConfigWithDb;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.MultitenancyHelper;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.reflections.Reflections;

import java.lang.reflect.Field;
import java.util.*;
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
            assertEquals(14, tenantsInfos.size());
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
            assertEquals(13, count);
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
            assertEquals(14, tenantsInfos.size());
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
            assertEquals(13, count);
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
        intervals.put("io.supertokens.cronjobs.cleanupSAMLCodes.CleanupSAMLCodes", 3600);

        Map<String, Integer> delays = new HashMap<>();
        delays.put("io.supertokens.ee.cronjobs.EELicenseCheck", 86400);
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
        delays.put("io.supertokens.cronjobs.cleanupSAMLCodes.CleanupSAMLCodes", 0);

        List<CronTask> allTasks = Cronjobs.getInstance(process.getProcess()).getTasks();
        assertEquals(14, allTasks.size());

        for (CronTask task : allTasks) {
            System.out.println(task.getClass().getName());
            assertEquals(intervals.get(task.getClass().getName()).intValue(), task.getIntervalTimeSeconds());
            assertEquals(delays.get(task.getClass().getName()).intValue(), task.getInitialWaitTimeSeconds());
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

        setEnv(Collections.emptyMap());

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

        setEnv(Collections.emptyMap());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatBulkMigrationCronJobLoadedWhenEnvVarSetToTrue() throws Exception {
        String[] args = {"../"};
        setEnv(Map.of("BULK_MIGRATION_CRON_ENABLED", "true"));

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

        setEnv(Collections.emptyMap());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatBulkMigrationCronJobNotLoadedWhenEnvVarSetToFalse() throws Exception {
        String[] args = {"../"};
        setEnv(Map.of("BULK_MIGRATION_CRON_ENABLED", "false"));

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

        setEnv(Collections.emptyMap());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    protected static void setEnv(Map<String, String> newenv) throws Exception {
        try {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
            env.putAll(newenv);
            Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
            cienv.putAll(newenv);
        } catch (NoSuchFieldException e) {
            Class[] classes = Collections.class.getDeclaredClasses();
            Map<String, String> env = System.getenv();
            for(Class cl : classes) {
                if("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
                    Field field = cl.getDeclaredField("m");
                    field.setAccessible(true);
                    Object obj = field.get(env);
                    Map<String, String> map = (Map<String, String>) obj;
                    map.clear();
                    map.putAll(newenv);
                }
            }
        }
    }

}
