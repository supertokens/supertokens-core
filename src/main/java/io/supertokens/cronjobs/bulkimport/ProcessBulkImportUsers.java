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
import io.supertokens.bulkimport.BulkImportUserUtils;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        Logging.debug(main, app.getAsPublicTenantIdentifier(), "CronTask starts. Instance: " + this);
        Logging.debug(main, app.getAsPublicTenantIdentifier(), "CronTask starts. Processing bulk import users with " + bulkMigrationBatchSize
                + " batch size, one batch split into " + numberOfBatchChunks + " chunks");

        executorService = Executors.newFixedThreadPool(numberOfBatchChunks);
        String[] allUserRoles = StorageUtils.getUserRolesStorage(bulkImportSQLStorage).getRoles(app);
        BulkImportUserUtils bulkImportUserUtils = new BulkImportUserUtils(allUserRoles);

        long newUsers = bulkImportSQLStorage.getBulkImportUsersCount(app, BulkImportStorage.BULK_IMPORT_USER_STATUS.NEW);
        long processingUsers = bulkImportSQLStorage.getBulkImportUsersCount(app, BulkImportStorage.BULK_IMPORT_USER_STATUS.PROCESSING);
        long failedUsers = 0;
        //taking a "snapshot" here and processing in this round as many users as there are uploaded now. After this the processing will go on
        //with another app and gets back here when all the apps had a chance.
        long usersProcessed = 0;

        Logging.debug(main, app.getAsPublicTenantIdentifier(), "Found " + (newUsers + processingUsers) + " waiting for processing"
                + " (" + newUsers + " new, " + processingUsers + " processing)");;

        while(usersProcessed < (newUsers + processingUsers)) {

            List<BulkImportUser> users = bulkImportSQLStorage.getBulkImportUsersAndChangeStatusToProcessing(app,
                    bulkMigrationBatchSize);

            Logging.debug(main, app.getAsPublicTenantIdentifier(), "Loaded " + users.size() + " users to process");

            if (users == null || users.isEmpty()) {
                // "No more users to process!"
                break;
            }

            List<List<BulkImportUser>> loadedUsersChunks = makeChunksOf(users, numberOfBatchChunks);
            for (List<BulkImportUser> chunk : loadedUsersChunks) {
                Logging.debug(main, app.getAsPublicTenantIdentifier(), "Chunk size: " + chunk.size());
            }

            try {
                List<Future<?>> tasks = new ArrayList<>();
                for (int i = 0; i < numberOfBatchChunks && i < loadedUsersChunks.size(); i++) {
                    tasks.add(
                            executorService.submit(new ProcessBulkUsersImportWorker(main, app, loadedUsersChunks.get(i),
                                    bulkImportSQLStorage, bulkImportUserUtils)));
                }

                for (Future<?> task : tasks) {
                    while (!task.isDone()) {
                        Logging.debug(main, app.getAsPublicTenantIdentifier(), "Waiting for task " + task + " to finish");
                        Thread.sleep(1000);
                    }
                    Logging.debug(main, app.getAsPublicTenantIdentifier(), "Task " + task + " finished");
                    try {
                        Void result = (Void) task.get(); //to know if there were any errors while executing and for
                        // waiting in this thread for all the other threads to finish up
                        Logging.debug(main, app.getAsPublicTenantIdentifier(),
                                "Task " + task + " finished with result: " + result);
                    } catch (ExecutionException executionException) {
                        Logging.error(main, app.getAsPublicTenantIdentifier(),
                                "Error while processing bulk import users", true,
                                executionException);
                        throw new RuntimeException(executionException);
                    }
                    usersProcessed += loadedUsersChunks.get(tasks.indexOf(task)).size();
                    failedUsers = bulkImportSQLStorage.getBulkImportUsersCount(app, BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED);
                    Logging.debug(main, app.getAsPublicTenantIdentifier(), "Chunk " + tasks.indexOf(task) + " finished processing, all chunks processed: "
                            + usersProcessed + " users (" + failedUsers + " failed)");
                }
                Logging.debug(main, app.getAsPublicTenantIdentifier(), "Processing round finished");
            } catch (InterruptedException e) {
                Logging.error(main, app.getAsPublicTenantIdentifier(), "Error while processing bulk import users", true,
                        e);
                throw new RuntimeException(e);
            }
        }

        executorService.shutdownNow();

        // Signal completion for tests that wait on this event
        long remaining = bulkImportSQLStorage.getBulkImportUsersCount(app, BulkImportStorage.BULK_IMPORT_USER_STATUS.NEW)
                + bulkImportSQLStorage.getBulkImportUsersCount(app, BulkImportStorage.BULK_IMPORT_USER_STATUS.PROCESSING);
        if (remaining == 0) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.BULK_IMPORT_COMPLETE, null);
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

    private List<List<BulkImportUser>> makeChunksOf(List<BulkImportUser> users, int numberOfChunks) {
        List<List<BulkImportUser>> chunks = new ArrayList<>();
        if (users != null && !users.isEmpty() && numberOfChunks > 0) {
            AtomicInteger index = new AtomicInteger(0);
            int chunkSize = users.size() / numberOfChunks + 1;
            Stream<List<BulkImportUser>> listStream = users.stream()
                    .collect(Collectors.groupingBy(x -> index.getAndIncrement() / chunkSize))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue);

            listStream.forEach(chunks::add);
        }
        return chunks;
    }

}
