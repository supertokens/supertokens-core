/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.bulkimport.BulkImportUserUtils;
import io.supertokens.bulkimport.exceptions.InvalidBulkImportDataException;
import io.supertokens.config.Config;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.exceptions.BulkImportBatchInsertException;
import io.supertokens.pluginInterface.bulkimport.exceptions.BulkImportTransactionRolledBackException;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportSQLStorage;
import io.supertokens.pluginInterface.exceptions.DbInitException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.pluginInterface.sqlStorage.TransactionConnection;
import io.supertokens.storageLayer.StorageLayer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import io.supertokens.auditlog.UnauditedTransaction;

public class ProcessBulkUsersImportWorker implements Callable<Boolean> {

    private final Map<String, SQLStorage> userPoolToStorageMap = new HashMap<>();
    private final Main main;
    private final AppIdentifier app;
    private final BulkImportSQLStorage bulkImportSQLStorage;
    private final String[] allUserRoles;
    private final int chunkSize;

    ProcessBulkUsersImportWorker(Main main, AppIdentifier app, int chunkSize,
                                 BulkImportSQLStorage bulkImportSQLStorage,
                                 String[] allUserRoles) {
        this.main = main;
        this.app = app;
        this.chunkSize = chunkSize;
        this.bulkImportSQLStorage = bulkImportSQLStorage;
        this.allUserRoles = allUserRoles;
    }

    /**
     * Claims a chunk of users with FOR UPDATE inside a baseTenantStorage transaction, processes them,
     * then deletes (or marks as error) within the same transaction — so the row-level locks are held
     * from claim through final status update.
     *
     * @return true if any users were found and processed, false if the queue was empty
     */
    @Override
    @UnauditedTransaction(justification = "Legacy unaudited transaction (PLAN-012 backlog); pending conversion to startAuditedTransaction or read-only exemption.")
    public Boolean call() {
        // Fresh instance per invocation: allExternalUserIds must not bleed across retry rounds.
        BulkImportUserUtils bulkImportUserUtils = new BulkImportUserUtils(allUserRoles);

        // Pre-initialize proxy storages BEFORE acquiring the outer transaction connection.
        // getAllProxyStoragesForApp calls Multitenancy.getAllTenantsForApp, which needs a
        // base-pool connection. If done inside startTransaction, workers deadlock: the outer
        // transaction already holds one connection, and with parallelism > pool-size all
        // workers block each other waiting for a second connection from the exhausted pool.
        Storage[] allStoragesForApp;
        try {
            allStoragesForApp = getAllProxyStoragesForApp(main, app);
        } catch (StorageTransactionLogicException e) {
            throw new RuntimeException(e);
        }

        try {
            return bulkImportSQLStorage.startTransaction(baseCon -> {
                try {
                    List<BulkImportUser> users = bulkImportSQLStorage
                            .getBulkImportUsersAndChangeStatusToProcessing_Transaction(app, chunkSize, baseCon);
                    if (users == null || users.isEmpty()) {
                        return false;
                    }
                    processMultipleUsers(app, users, bulkImportUserUtils, allStoragesForApp,
                            bulkImportSQLStorage, baseCon);
                    return true;
                } catch (TenantOrAppNotFoundException | DbInitException | IOException | StorageQueryException e) {
                    throw new StorageTransactionLogicException(e);
                }
            });
        } catch (StorageTransactionLogicException | StorageQueryException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                closeAllProxyStorages();
            } catch (StorageQueryException ignored) {
            }
        }
    }

    @UnauditedTransaction(justification = "Legacy unaudited transaction (PLAN-012 backlog); pending conversion to startAuditedTransaction or read-only exemption.")
    private void processMultipleUsers(AppIdentifier appIdentifier, List<BulkImportUser> users,
                                      BulkImportUserUtils bulkImportUserUtils,
                                      Storage[] allStoragesForApp,
                                      BulkImportSQLStorage baseTenantStorage,
                                      TransactionConnection baseCon)
            throws TenantOrAppNotFoundException, StorageQueryException, IOException, DbInitException {
        try {
            Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(),
                    "Processing bulk import users: " + users.size());
            int userIndexPointer = 0;
            List<BulkImportUser> validUsers = new ArrayList<>();
            Map<String, Exception> validationErrorsBeforeActualProcessing = new HashMap<>();
            while (userIndexPointer < users.size()) {
                BulkImportUser user = users.get(userIndexPointer);
                if (Main.isTesting && Main.isTesting_skipBulkImportUserValidationInCronJob) {
                    validUsers.add(user);
                } else {
                    try {
                        validUsers.add(bulkImportUserUtils.createBulkImportUserFromJSON(main, appIdentifier,
                                user.toJsonObject(), BulkImportUserUtils.IDMode.READ_STORED));
                    } catch (InvalidBulkImportDataException exception) {
                        validationErrorsBeforeActualProcessing.put(user.id, new Exception(
                                String.valueOf(exception.errors)));
                    }
                }
                userIndexPointer += 1;
            }

            if (!validationErrorsBeforeActualProcessing.isEmpty()) {
                throw new BulkImportBatchInsertException("Invalid input data", validationErrorsBeforeActualProcessing);
            }

            Map<SQLStorage, List<BulkImportUser>> partitionedUsers = partitionUsersByStorage(appIdentifier, validUsers);

            for (SQLStorage bulkImportProxyStorage : partitionedUsers.keySet()) {
                boolean shouldRetryImmediately = true;
                while (shouldRetryImmediately) {
                    shouldRetryImmediately = bulkImportProxyStorage.startTransaction(con -> {
                        try {
                            BulkImport.processUsersImportSteps(main, appIdentifier, bulkImportProxyStorage,
                                    partitionedUsers.get(bulkImportProxyStorage),
                                    allStoragesForApp);

                            bulkImportProxyStorage.commitTransactionForBulkImportProxyStorage();

                            // Delete within the outer baseTenantStorage transaction — the FOR UPDATE lock
                            // is held on baseCon until it commits, preventing concurrent re-claiming.
                            String[] toDelete = new String[validUsers.size()];
                            for (int i = 0; i < validUsers.size(); i++) {
                                toDelete[i] = validUsers.get(i).id;
                            }
                            baseTenantStorage.deleteBulkImportUsers_Transaction(appIdentifier, toDelete, baseCon);
                        } catch (StorageTransactionLogicException | StorageQueryException e) {
                            bulkImportProxyStorage.rollbackTransactionForBulkImportProxyStorage();
                            if (isBulkImportTransactionRolledBackIsTheRealCause(e)) {
                                return true;
                            }
                            handleProcessUserExceptions(app, validUsers, e, baseTenantStorage, baseCon);
                        }
                        return false;
                    });
                }
            }
        } catch (StorageTransactionLogicException | InvalidConfigException e) {
            Logging.error(main, app.getAsPublicTenantIdentifier(),
                    "Error while processing bulk import users: " + e.getMessage(), true, e);
            throw new RuntimeException(e);
        } catch (BulkImportBatchInsertException insertException) {
            handleProcessUserExceptions(app, users, insertException, baseTenantStorage, baseCon);
        } catch (Exception e) {
            Logging.error(main, app.getAsPublicTenantIdentifier(),
                    "Error while processing bulk import users: " + e.getMessage(), true, e);
            throw e;
        }
    }

    private boolean isBulkImportTransactionRolledBackIsTheRealCause(Throwable exception) {
        if (exception instanceof BulkImportTransactionRolledBackException) {
            return true;
        } else if (exception.getCause() != null) {
            return isBulkImportTransactionRolledBackIsTheRealCause(exception.getCause());
        }
        return false;
    }

    private void handleProcessUserExceptions(AppIdentifier appIdentifier, List<BulkImportUser> usersBatch,
                                             Exception e, BulkImportSQLStorage baseTenantStorage,
                                             TransactionConnection baseCon)
            throws StorageQueryException {
        String[] errorMessage = { e.getMessage() };
        Map<String, String> bulkImportUserIdToErrorMessage = new HashMap<>();

        switch (e) {
            case StorageTransactionLogicException exception -> {
                if (exception.actualException instanceof StorageQueryException) {
                    Logging.error(main, null,
                            "We got an StorageQueryException while processing a bulk import user entry. It will be " +
                                    "retried again. Error Message: " + e.getMessage(), true);
                    return;
                }
                if (exception.actualException instanceof BulkImportBatchInsertException) {
                    handleBulkImportException(usersBatch,
                            (BulkImportBatchInsertException) exception.actualException,
                            bulkImportUserIdToErrorMessage);
                } else {
                    errorMessage[0] = exception.actualException.getMessage();
                    for (BulkImportUser user : usersBatch) {
                        bulkImportUserIdToErrorMessage.put(user.id, errorMessage[0]);
                    }
                }
            }
            case InvalidBulkImportDataException invalidBulkImportDataException ->
                    errorMessage[0] = invalidBulkImportDataException.errors.toString();
            case InvalidConfigException invalidConfigException -> errorMessage[0] = e.getMessage();
            case BulkImportBatchInsertException bulkImportBatchInsertException ->
                    handleBulkImportException(usersBatch, bulkImportBatchInsertException,
                            bulkImportUserIdToErrorMessage);
            default -> {
                Logging.error(main, null,
                        "We got an error while processing a bulk import user entry. It will be " +
                                "retried again. Error Message: " + e.getMessage(), true);
            }
        }

        // Update error status within the outer baseTenantStorage transaction — no nested startTransaction needed.
        baseTenantStorage.updateMultipleBulkImportUsersStatusToError_Transaction(appIdentifier, baseCon,
                bulkImportUserIdToErrorMessage);
    }

    private static void handleBulkImportException(List<BulkImportUser> usersBatch,
                                                  BulkImportBatchInsertException exception,
                                                  Map<String, String> bulkImportUserIdToErrorMessage) {
        Map<String, Exception> userIndexToError = exception.exceptionByUserId;
        for (String userid : userIndexToError.keySet()) {
            Optional<BulkImportUser> userWithId = usersBatch.stream()
                    .filter(bulkImportUser -> userid.equals(bulkImportUser.id)
                            || userid.equals(bulkImportUser.externalUserId))
                    .findFirst();
            String id = null;
            if (userWithId.isPresent()) {
                id = userWithId.get().id;
            }

            if (id == null) {
                userWithId = usersBatch.stream()
                        .filter(bulkImportUser ->
                                bulkImportUser.loginMethods.stream()
                                        .map(loginMethod -> loginMethod.superTokensUserId)
                                        .anyMatch(s -> s != null && s.equals(userid)))
                        .findFirst();
                if (userWithId.isPresent()) {
                    id = userWithId.get().id;
                }
            }
            bulkImportUserIdToErrorMessage.put(id, userIndexToError.get(userid).getMessage());
        }
    }

    private synchronized Storage getBulkImportProxyStorage(TenantIdentifier tenantIdentifier)
            throws InvalidConfigException, IOException, TenantOrAppNotFoundException, DbInitException {
        String userPoolId = StorageLayer.getStorage(tenantIdentifier, main).getUserPoolId();
        if (userPoolToStorageMap.containsKey(userPoolId)) {
            return userPoolToStorageMap.get(userPoolId);
        }

        TenantConfig[] allTenants = Multitenancy.getAllTenants(main);

        Map<ResourceDistributor.KeyClass, JsonObject> normalisedConfigs = Config.getNormalisedConfigsForAllTenants(
                allTenants,
                Config.getBaseConfigAsJsonObject(main));

        for (ResourceDistributor.KeyClass key : normalisedConfigs.keySet()) {
            if (key.getTenantIdentifier().equals(tenantIdentifier)) {
                SQLStorage bulkImportProxyStorage = (SQLStorage) StorageLayer.getNewBulkImportProxyStorageInstance(main,
                        normalisedConfigs.get(key), tenantIdentifier, true);

                userPoolToStorageMap.put(userPoolId, bulkImportProxyStorage);
                bulkImportProxyStorage.initStorage(false, new ArrayList<>());
                return bulkImportProxyStorage;
            }
        }
        throw new TenantOrAppNotFoundException(tenantIdentifier);
    }

    private synchronized Storage[] getAllProxyStoragesForApp(Main main, AppIdentifier appIdentifier)
            throws StorageTransactionLogicException {
        try {
            List<Storage> allProxyStorages = new ArrayList<>();
            TenantConfig[] tenantConfigs = Multitenancy.getAllTenantsForApp(appIdentifier, main);
            for (TenantConfig tenantConfig : tenantConfigs) {
                allProxyStorages.add(getBulkImportProxyStorage(tenantConfig.tenantIdentifier));
            }
            return allProxyStorages.toArray(new Storage[0]);
        } catch (TenantOrAppNotFoundException e) {
            throw new StorageTransactionLogicException(new Exception("E043: " + e.getMessage()));
        } catch (InvalidConfigException e) {
            throw new StorageTransactionLogicException(new InvalidConfigException("E044: " + e.getMessage()));
        } catch (DbInitException e) {
            throw new StorageTransactionLogicException(new DbInitException("E045: " + e.getMessage()));
        } catch (IOException e) {
            throw new StorageTransactionLogicException(new IOException("E046: " + e.getMessage()));
        }
    }

    private void closeAllProxyStorages() throws StorageQueryException {
        for (SQLStorage storage : userPoolToStorageMap.values()) {
            storage.closeConnectionForBulkImportProxyStorage();
        }
        userPoolToStorageMap.clear();
    }

    private Map<SQLStorage, List<BulkImportUser>> partitionUsersByStorage(AppIdentifier appIdentifier,
                                                                           List<BulkImportUser> users)
            throws DbInitException, TenantOrAppNotFoundException, InvalidConfigException, IOException {
        Map<SQLStorage, List<BulkImportUser>> result = new HashMap<>();
        for (BulkImportUser user : users) {
            TenantIdentifier firstTenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                    appIdentifier.getAppId(), user.loginMethods.getFirst().tenantIds.getFirst());
            
            SQLStorage bulkImportProxyStorage = (SQLStorage) getBulkImportProxyStorage(firstTenantIdentifier);
            if (!result.containsKey(bulkImportProxyStorage)) {
                result.put(bulkImportProxyStorage, new ArrayList<>());
            }
            result.get(bulkImportProxyStorage).add(user);
        }
        return result;
    }
}
