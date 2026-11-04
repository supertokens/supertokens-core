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

package io.supertokens.test.migration;

import io.supertokens.ProcessState;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.backfill.BackfillReservationTables;
import io.supertokens.pluginInterface.MigrationMode;
import io.supertokens.pluginInterface.migration.MigrationBackfillStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class BackfillReservationTablesTest {
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

    // BACKFILL_COMPLETE is only added after the cron has queried the pending-user count, so its absence
    // shows the cron skipped the app.
    @Test
    public void cronDoesNotCheckForPendingUsersWhenMigrated() throws Exception {
        TestingProcessManager.TestingProcess process = startWithBackfillCronEverySecond(MigrationMode.MIGRATED);
        try {
            MigrationBackfillStorage storage = (MigrationBackfillStorage) StorageLayer.getStorage(
                    process.getProcess());
            assertEquals(MigrationMode.MIGRATED, storage.getMigrationMode());

            assertNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.BACKFILL_COMPLETE, 5000));
        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    // Control for the test above: before MIGRATED the cron still checks for pending users.
    @Test
    public void cronChecksForPendingUsersBeforeMigrated() throws Exception {
        TestingProcessManager.TestingProcess process = startWithBackfillCronEverySecond(
                MigrationMode.DUAL_WRITE_READ_NEW);
        try {
            // The in-memory storage always reports MIGRATED
            if (StorageLayer.isInMemDb(process.getProcess())) {
                return;
            }

            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.BACKFILL_COMPLETE, 5000));
        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }

    private static TestingProcessManager.TestingProcess startWithBackfillCronEverySecond(MigrationMode mode)
            throws Exception {
        Utils.setValueInConfig("migration_mode", mode.name());
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        CronTaskTest.getInstance(process.getProcess())
                .setInitialWaitTimeInSeconds(BackfillReservationTables.RESOURCE_KEY, 0);
        CronTaskTest.getInstance(process.getProcess())
                .setIntervalInSeconds(BackfillReservationTables.RESOURCE_KEY, 1);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        return process;
    }
}
