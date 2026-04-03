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

package io.supertokens.cronjobs.backfill;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.MigrationMode;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.migration.MigrationBackfillStorage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;

import java.util.List;

public class BackfillReservationTables extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.BackfillReservationTables";

    private static final int BATCH_SIZE = 1000;

    private BackfillReservationTables(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("BackfillReservationTables", main, tenantsInfo, true); // per-app
    }

    public static BackfillReservationTables init(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        return (BackfillReservationTables) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new BackfillReservationTables(main, tenantsInfo));
    }

    @Override
    protected void doTaskPerApp(AppIdentifier app)
            throws TenantOrAppNotFoundException, StorageQueryException {

        Storage storage = StorageLayer.getStorage(app.getAsPublicTenantIdentifier(), main);

        if (storage.getType() != STORAGE_TYPE.SQL) {
            return;
        }

        if (!(storage instanceof MigrationBackfillStorage)) {
            return;
        }

        MigrationBackfillStorage backfillStorage = (MigrationBackfillStorage) storage;

        // Skip if in LEGACY mode — no backfill needed
        MigrationMode mode = backfillStorage.getMigrationMode();
        if (mode == MigrationMode.LEGACY) {
            return;
        }

        TenantIdentifier tenantId = app.getAsPublicTenantIdentifier();

        // Check if backfill is needed
        int pending = backfillStorage.getBackfillPendingUsersCount(app);
        if (pending == 0) {
            ProcessState.getInstance(main).addState(
                    ProcessState.PROCESS_STATE.BACKFILL_COMPLETE, null);
            return;
        }

        Logging.info(main, tenantId,
                "Backfill starting: " + pending + " users pending for app " + app.getAppId(), true);

        // Process in batches
        int totalProcessed = 0;
        while (true) {
            int processed = backfillStorage.backfillUsersBatch(app, BATCH_SIZE);
            totalProcessed += processed;

            if (totalProcessed > 0 && totalProcessed % 10000 < BATCH_SIZE) {
                Logging.info(main, tenantId,
                        "Backfill progress: " + totalProcessed + "/" + pending + " users processed", true);
            }

            if (processed < BATCH_SIZE) {
                break;
            }
        }

        // Verify completeness
        int inconsistencies = backfillStorage.verifyBackfillCompleteness(app);
        if (inconsistencies > 0) {
            Logging.error(main, tenantId,
                    "Backfill verification: " + inconsistencies + " users still missing data", true);
        } else {
            Logging.info(main, tenantId,
                    "Backfill complete and verified: " + totalProcessed + " users processed", true);
            ProcessState.getInstance(main).addState(
                    ProcessState.PROCESS_STATE.BACKFILL_COMPLETE, null);
        }
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        return 5 * 60; // 5 minutes
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        if (Main.isTesting) {
            Integer waitTime = CronTaskTest.getInstance(main).getInitialWaitTimeInSeconds(RESOURCE_KEY);
            if (waitTime != null) {
                return waitTime;
            }
        }
        return 10; // Small delay to let startup complete
    }
}
