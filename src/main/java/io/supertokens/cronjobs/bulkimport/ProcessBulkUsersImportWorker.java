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

import io.supertokens.Main;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.bulkimport.BulkImportProxyStoragePools;
import io.supertokens.bulkimport.BulkImportUserUtils;
import io.supertokens.bulkimport.BulkImportWorkerStorages;
import io.supertokens.bulkimport.exceptions.InvalidBulkImportDataException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.exceptions.BulkImportBatchInsertException;
import io.supertokens.pluginInterface.bulkimport.exceptions.BulkImportTransactionRolledBackException;
import io.supertokens.pluginInterface.bulkimport.sqlStorage.BulkImportProxySQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.sqlStorage.TransactionConnection;

import java.sql.Savepoint;
import java.util.*;
import java.util.concurrent.Callable;
import io.supertokens.auditlog.UnauditedTransaction;

/**
 * One bulk import worker. Each invocation claims a chunk of {@code bulk_import_users} rows with
 * {@code SELECT ... FOR UPDATE SKIP LOCKED}, imports the users, and deletes (or error-marks) the claimed rows
 * — all on a single connection from the app's dedicated bulk import pool, inside one transaction:
 *
 * <pre>
 *   BEGIN
 *     claim rows (locked, status = PROCESSING)
 *     for each storage partition:
 *       SAVEPOINT
 *       import users; delete their rows            -- or: ROLLBACK TO SAVEPOINT; mark rows ERROR
 *   COMMIT                                          -- locks released only now
 * </pre>
 *
 * <p>The row locks are therefore held from claim until the rows are in a terminal state, so several core
 * instances can drain the same queue without ever importing a user twice, and a failed import can be undone
 * without surrendering the claim. The live connection pool serving API traffic is not touched.
 *
 * @return true if a chunk was claimed, false if the queue was empty
 */
public class ProcessBulkUsersImportWorker implements Callable<Boolean> {

    /**
     * How many times a partition is re-imported straight away after the database rolled it back (deadlock,
     * serialization failure). Beyond that the rows are left {@code PROCESSING} for a later round.
     */
    static final int MAX_IMMEDIATE_RETRIES = 5;

    private final Main main;
    private final AppIdentifier app;
    private final int chunkSize;
    private final BulkImportProxyStoragePools pools;
    private final String[] allUserRoles;

    ProcessBulkUsersImportWorker(Main main, AppIdentifier app, int chunkSize, BulkImportProxyStoragePools pools,
                                 String[] allUserRoles) {
        this.main = main;
        this.app = app;
        this.chunkSize = chunkSize;
        this.pools = pools;
        this.allUserRoles = allUserRoles;
    }

    @Override
    @UnauditedTransaction(justification = "Legacy unaudited transaction (PLAN-012 backlog); pending conversion to startAuditedTransaction or read-only exemption.")
    public Boolean call() {
        // Fresh instance per invocation: allExternalUserIds must not bleed across retry rounds.
        BulkImportUserUtils bulkImportUserUtils = new BulkImportUserUtils(allUserRoles);

        try (BulkImportWorkerStorages storages = pools.createStoragesForWorker()) {
            BulkImportProxySQLStorage claimStorage = storages.forPublicTenant();
            return claimStorage.startTransaction(con -> {
                try {
                    List<BulkImportUser> users = claimStorage
                            .getBulkImportUsersAndChangeStatusToProcessing_Transaction(app, chunkSize, con);
                    if (users == null || users.isEmpty()) {
                        claimStorage.commitTransactionForBulkImportProxyStorage();
                        return false;
                    }
                    processClaimedUsers(users, bulkImportUserUtils, storages, claimStorage, con);
                    claimStorage.commitTransactionForBulkImportProxyStorage();
                    return true;
                } catch (TenantOrAppNotFoundException | StorageQueryException e) {
                    // the transaction is rolled back when the storages are closed below
                    throw new StorageTransactionLogicException(e);
                }
            });
        } catch (StorageTransactionLogicException | StorageQueryException e) {
            throw new RuntimeException(e);
        }
    }

    private void processClaimedUsers(List<BulkImportUser> users, BulkImportUserUtils bulkImportUserUtils,
                                     BulkImportWorkerStorages storages, BulkImportProxySQLStorage claimStorage,
                                     TransactionConnection claimCon)
            throws TenantOrAppNotFoundException, StorageQueryException {
        Logging.debug(main, app.getAsPublicTenantIdentifier(), "Processing bulk import users: " + users.size());

        List<BulkImportUser> validUsers = new ArrayList<>();
        Map<String, Exception> validationErrors = new HashMap<>();
        for (BulkImportUser user : users) {
            if (Main.isTesting && Main.isTesting_skipBulkImportUserValidationInCronJob) {
                validUsers.add(user);
                continue;
            }
            try {
                validUsers.add(bulkImportUserUtils.createBulkImportUserFromJSON(main, app, user.toJsonObject(),
                        BulkImportUserUtils.IDMode.READ_STORED));
            } catch (InvalidBulkImportDataException e) {
                validationErrors.put(user.id, new Exception(String.valueOf(e.errors)));
            }
        }
        if (!validationErrors.isEmpty()) {
            // Only the invalid rows are marked; valid ones stay PROCESSING and are re-claimed by a later round.
            markFailed(users, new BulkImportBatchInsertException("Invalid input data", validationErrors),
                    claimStorage, claimCon);
            return;
        }

        // Since all the tenants of a user must share the storage, partition by the storage of the first
        // tenantId of the first loginMethod.
        Map<BulkImportProxySQLStorage, List<BulkImportUser>> partitions = partitionUsersByStorage(storages, validUsers);
        for (Map.Entry<BulkImportProxySQLStorage, List<BulkImportUser>> partition : partitions.entrySet()) {
            importPartition(partition.getKey(), partition.getValue(), storages, claimStorage, claimCon);
        }
    }

    /**
     * Imports one storage's share of the chunk and deletes the corresponding claimed rows. On failure the
     * import is undone — via ROLLBACK TO SAVEPOINT when it ran on the claim connection, so the claim itself
     * survives — and the rows are either retried immediately (database rollback) or marked as errored.
     */
    private void importPartition(BulkImportProxySQLStorage storage, List<BulkImportUser> partitionUsers,
                                 BulkImportWorkerStorages storages, BulkImportProxySQLStorage claimStorage,
                                 TransactionConnection claimCon) throws StorageQueryException {
        for (int attempt = 1; ; attempt++) {
            Savepoint beforeImport = claimStorage.createSavepointForBulkImportProxyStorage();
            try {
                BulkImport.processUsersImportSteps(main, app, storage, partitionUsers, storages.all());
                if (storage != claimStorage) {
                    // A different database: its import commits on its own connection; the queue rows are
                    // deleted below on the claim connection, still under the claim's row locks.
                    storage.commitTransactionForBulkImportProxyStorage();
                }
                claimStorage.deleteBulkImportUsers_Transaction(app, idsOf(partitionUsers), claimCon);
                claimStorage.releaseSavepointForBulkImportProxyStorage(beforeImport);
                return;
            } catch (StorageTransactionLogicException | StorageQueryException e) {
                undoImport(storage, claimStorage, beforeImport);
                if (isBulkImportTransactionRolledBackTheRealCause(e) && attempt < MAX_IMMEDIATE_RETRIES) {
                    Logging.debug(main, app.getAsPublicTenantIdentifier(),
                            "Bulk import partition rolled back by the database, retrying (attempt " + attempt + ")");
                    continue;
                }
                markFailed(partitionUsers, e, claimStorage, claimCon);
                return;
            }
        }
    }

    private static void undoImport(BulkImportProxySQLStorage storage, BulkImportProxySQLStorage claimStorage,
                                   Savepoint beforeImport) throws StorageQueryException {
        if (storage != claimStorage) {
            storage.rollbackTransactionForBulkImportProxyStorage();
        }
        // Always rewind the claim connection too: it may hold a partial import (same storage) or already
        // deleted rows (different storage) from this attempt. The claim's locks and PROCESSING status survive.
        claimStorage.rollbackToSavepointForBulkImportProxyStorage(beforeImport);
    }

    private static String[] idsOf(List<BulkImportUser> users) {
        String[] ids = new String[users.size()];
        for (int i = 0; i < users.size(); i++) {
            ids[i] = users.get(i).id;
        }
        return ids;
    }

    private static boolean isBulkImportTransactionRolledBackTheRealCause(Throwable exception) {
        if (exception instanceof BulkImportTransactionRolledBackException) {
            return true;
        } else if (exception.getCause() != null) {
            return isBulkImportTransactionRolledBackTheRealCause(exception.getCause());
        }
        return false;
    }

    /**
     * Writes the ERROR status (and message) for the rows that failed, on the claim connection, so it is
     * committed together with the rest of the chunk while the rows are still locked. Transient storage
     * failures are not marked: those rows stay PROCESSING and are retried by a later round.
     */
    private void markFailed(List<BulkImportUser> usersBatch, Exception e, BulkImportProxySQLStorage claimStorage,
                            TransactionConnection claimCon) throws StorageQueryException {
        Map<String, String> bulkImportUserIdToErrorMessage = new HashMap<>();

        if (e instanceof BulkImportBatchInsertException batchException) {
            mapPerUserErrors(usersBatch, batchException, bulkImportUserIdToErrorMessage);
        } else if (e instanceof StorageTransactionLogicException exception) {
            if (exception.actualException instanceof StorageQueryException) {
                Logging.error(main, null,
                        "We got an StorageQueryException while processing a bulk import user entry. It will be " +
                                "retried again. Error Message: " + e.getMessage(), true);
                return;
            }
            if (exception.actualException instanceof BulkImportBatchInsertException batchException) {
                mapPerUserErrors(usersBatch, batchException, bulkImportUserIdToErrorMessage);
            } else {
                for (BulkImportUser user : usersBatch) {
                    bulkImportUserIdToErrorMessage.put(user.id, exception.actualException.getMessage());
                }
            }
        } else {
            Logging.error(main, null,
                    "We got an error while processing a bulk import user entry. It will be " +
                            "retried again. Error Message: " + e.getMessage(), true);
            return;
        }

        claimStorage.updateMultipleBulkImportUsersStatusToError_Transaction(app, claimCon,
                bulkImportUserIdToErrorMessage);
    }

    private static void mapPerUserErrors(List<BulkImportUser> usersBatch, BulkImportBatchInsertException exception,
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

    private Map<BulkImportProxySQLStorage, List<BulkImportUser>> partitionUsersByStorage(
            BulkImportWorkerStorages storages, List<BulkImportUser> users) throws TenantOrAppNotFoundException {
        Map<BulkImportProxySQLStorage, List<BulkImportUser>> result = new LinkedHashMap<>();
        for (BulkImportUser user : users) {
            TenantIdentifier firstTenantIdentifier = new TenantIdentifier(app.getConnectionUriDomain(),
                    app.getAppId(), user.loginMethods.getFirst().tenantIds.getFirst());
            result.computeIfAbsent(storages.forTenant(firstTenantIdentifier), k -> new ArrayList<>()).add(user);
        }
        return result;
    }
}
