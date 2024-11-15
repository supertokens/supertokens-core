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
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.bulkimport.BulkImportUserUtils;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.StorageUtils;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProcessBulkImportUsers extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.ee.cronjobs.ProcessBulkImportUsers";

    private Integer batchSize;
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

        String[] allUserRoles = StorageUtils.getUserRolesStorage(bulkImportSQLStorage).getRoles(app);
        BulkImportUserUtils bulkImportUserUtils = new BulkImportUserUtils(allUserRoles);

        System.out.println(Thread.currentThread().getName() + " ProcessBulkImportUsers: " + " starting to load users " + this.batchSize);
        List<BulkImportUser> users = bulkImportSQLStorage.getBulkImportUsersAndChangeStatusToProcessing(app,
                this.batchSize);
        System.out.println(Thread.currentThread().getName() + " ProcessBulkImportUsers: " + " loaded users");

        if(users == null || users.isEmpty()) {
            return;
        }

        //split the loaded users list into smaller chunks
        int NUMBER_OF_BATCHES = Config.getConfig(app.getAsPublicTenantIdentifier(), main).getBulkMigrationParallelism();
        List<List<BulkImportUser>> loadedUsersChunks = makeChunksOf(users, NUMBER_OF_BATCHES);

        //pass the chunks for processing for the workers

        executorService = Executors.newFixedThreadPool(NUMBER_OF_BATCHES);


        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i =0; i< NUMBER_OF_BATCHES; i++) {
                tasks.add(executorService.submit(new ProcessBulkUsersImportWorker(main, app, loadedUsersChunks.get(i),
                        bulkImportSQLStorage, bulkImportUserUtils)));
            }

            for (Future<?> task : tasks) {
                while(!task.isDone()) {
                    Thread.sleep(1000);
                }
                Void result = (Void) task.get(); //to know if there were any errors while executing and for waiting in this thread for all the other threads to finish up
                System.out.println("Result: " + result);
            }


            executorService.shutdownNow();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("Pool did not terminate");
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
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

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
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
