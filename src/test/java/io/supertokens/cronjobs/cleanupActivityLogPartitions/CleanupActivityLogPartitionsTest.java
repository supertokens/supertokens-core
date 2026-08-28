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

package io.supertokens.cronjobs.cleanupActivityLogPartitions;

import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.Assert.assertNotNull;

/**
 * Unit tests for the cleanup cron's dispatch: they lock the wiring that resolves the retention window
 * from the representative tenant's config and threads it into the storage, independently of the
 * plugin-side partition behaviour (covered by ActivityLogRetentionTest / the plugin's own tests).
 */
public class CleanupActivityLogPartitionsTest {
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

    // The cron must pass the storage's *resolved* retention (from config), not a hardcoded constant, into
    // maintainActivityLogPartitions. Set a non-default value and assert exactly that value reaches the store.
    @Test
    public void passesResolvedRetentionFromConfigIntoStorage() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("activity_log_retention_days", "9"); // deliberately not the default (31)
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        CleanupActivityLogPartitions cron = CleanupActivityLogPartitions.init(process.getProcess(),
                Collections.singletonList(Collections.singletonList(TenantIdentifier.BASE_TENANT)));

        ActivityLogStorage storage = Mockito.mock(ActivityLogStorage.class);
        cron.doTaskPerStorage(TenantIdentifier.BASE_TENANT, storage);

        Mockito.verify(storage).maintainActivityLogPartitions(9);
        Mockito.verifyNoMoreInteractions(storage);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // A storage that isn't an ActivityLogStorage (e.g. a store that doesn't keep an activity log) is skipped
    // by the instanceof guard rather than erroring.
    @Test
    public void skipsStorageWithoutActivityLog() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        process.getProcess().setForceInMemoryDB();
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        CleanupActivityLogPartitions cron = CleanupActivityLogPartitions.init(process.getProcess(),
                Collections.singletonList(Collections.singletonList(TenantIdentifier.BASE_TENANT)));

        Storage plainStorage = Mockito.mock(Storage.class);
        cron.doTaskPerStorage(TenantIdentifier.BASE_TENANT, plainStorage);

        Mockito.verifyNoInteractions(plainStorage);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
