/*
 *    Copyright (c) 2024. VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.cronjobs.bulkimport;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ProcessBulkImportUsers extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.ProcessBulkImportUsers";

    private ExecutorService executorService;

    private ProcessBulkImportUsers(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("ProcessBulkImportUsers", main, tenantsInfo, true);
    }

    public static ProcessBulkImportUsers init(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        return (ProcessBulkImportUsers) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new ProcessBulkImportUsers(main, tenantsInfo));
    }

    @Override
    protected void doTaskPerApp(AppIdentifier app)
            throws TenantOrAppNotFoundException, StorageQueryException {

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        BulkImportSQLStorage bulkImportSQLStorage = (BulkImportSQLStorage) StorageLayer
                .getStorage(app.getAsPublicTenantIdentifier(), main);

        //split the loaded users list into smaller chunks
        int numberOfBatchChunks = Config.getConfig(app.getAsPublicTenantIdentifier(), main)
                .getBulkMigrationParallelism();
        int bulkMigrationBatchSize = Config.getConfig(app.getAsPublicTenantIdentifier(), main)
                .getBulkMigrationBatchSize();

        // Cheap pre-check: workers create a throwaway HikariCP pool (plus a createTablesIfNotExists
        // pass) per user pool BEFORE they discover the queue is empty. For apps that never use bulk
        // import - the overwhelming majority, every 5 minutes, forever - that is pure waste. Both
        // counts below are index-backed (bulk_import_users_status_updated_at_index on
        // (app_id, status, updated_at)).
        // A user moving NEW->PROCESSING between the two counts may be counted twice, which only
        // inflates the sum - the safe direction (we run the workers and they find nothing).
        long pendingUsers = bulkImportSQLStorage.getBulkImportUsersCount(app, BulkImportStorage.BULK_IMPORT_USER_STATUS.NEW)
                + bulkImportSQLStorage.getBulkImportUsersCount(app, BulkImportStorage.BULK_IMPORT_USER_STATUS.PROCESSING);
        if (pendingUsers == 0) {
            Logging.debug(main, app.getAsPublicTenantIdentifier(),
                    "CronTask skipped: no bulk import users to process.");
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.BULK_IMPORT_SKIPPED_EMPTY_QUEUE, null);
            return;
        }

        Logging.debug(main, app.getAsPublicTenantIdentifier(), "CronTask starts. Instance: " + this);
        Logging.debug(main, app.getAsPublicTenantIdentifier(), "CronTask starts. Processing bulk import users with " + bulkMigrationBatchSize
                + " batch size, one batch split into " + numberOfBatchChunks + " chunks");

        executorService = Executors.newFixedThreadPool(numberOfBatchChunks);

        String[] allUserRoles = StorageUtils.getUserRolesStorage(bulkImportSQLStorage).getRoles(app);

        // Each worker self-selects its own chunk using SELECT FOR UPDATE SKIP LOCKED inside a transaction,
        // which it keeps open until it deletes (or error-marks) those same rows. Workers stop when
        // all return false (nothing left in the queue for this round).
        int chunkSize = Math.max(1, bulkMigrationBatchSize / numberOfBatchChunks);
        Logging.debug(main, app.getAsPublicTenantIdentifier(),
                "CronTask starts. batch=" + bulkMigrationBatchSize + " parallelism=" + numberOfBatchChunks
                        + " chunkSize=" + chunkSize);

        boolean anyProcessed = false;
        try {
            while (true) {
                List<Future<Boolean>> tasks = new ArrayList<>();
                for (int i = 0; i < numberOfBatchChunks; i++) {
                    tasks.add(executorService.submit(
                            new ProcessBulkUsersImportWorker(main, app, chunkSize, bulkImportSQLStorage,
                                    allUserRoles)));
                }

                boolean roundHadWork = false;
                for (Future<Boolean> task : tasks) {
                    try {
                        if (task.get()) {
                            roundHadWork = true;
                            anyProcessed = true;
                        }
                    } catch (ExecutionException executionException) {
                        Logging.error(main, app.getAsPublicTenantIdentifier(),
                                "Error while processing bulk import users", true, executionException);
                        throw new RuntimeException(executionException);
                    }
                }

                Logging.debug(main, app.getAsPublicTenantIdentifier(),
                        "Processing round finished, hadWork=" + roundHadWork);
                if (!roundHadWork) {
                    break;
                }
                Integer sleepBetweenRounds = Config.getConfig(app.getAsPublicTenantIdentifier(), main)
                        .getBulkMigrationSleepBetweenRoundsInBatchMs();
                if (null != sleepBetweenRounds) {
                    Thread.sleep(sleepBetweenRounds);
                }
            }
        } catch (InterruptedException e) {
            Logging.error(main, app.getAsPublicTenantIdentifier(), "Error while processing bulk import users", true, e);
            throw new RuntimeException(e);
        } finally {
            executorService.shutdownNow();
        }

        // Signal completion for tests that wait on this event.
        // Only fire when users were actually processed to avoid spurious events
        // when the cron runs before any users are uploaded.
        if (anyProcessed) {
            long remaining = bulkImportSQLStorage.getBulkImportUsersCount(app, BulkImportStorage.BULK_IMPORT_USER_STATUS.NEW)
                    + bulkImportSQLStorage.getBulkImportUsersCount(app, BulkImportStorage.BULK_IMPORT_USER_STATUS.PROCESSING);
            if (remaining == 0) {
                ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.BULK_IMPORT_COMPLETE, null);
            }
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
        return BulkImport.PROCESS_USERS_INTERVAL_SECONDS;
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        if (Main.isTesting) {
            Integer waitTime = CronTaskTest.getInstance(main).getInitialWaitTimeInSeconds(RESOURCE_KEY);
            if (waitTime != null) {
                return waitTime;
            }
        }
        return 0;
    }


}
