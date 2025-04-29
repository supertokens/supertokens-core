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
import io.supertokens.storageLayer.StorageLayer;

import java.io.IOException;
import java.util.*;

public class ProcessBulkUsersImportWorker implements Runnable {

    private final Map<String, SQLStorage> userPoolToStorageMap = new HashMap<>();
    private final Main main;
    private final AppIdentifier app;
    private final BulkImportSQLStorage bulkImportSQLStorage;
    private final BulkImportUserUtils bulkImportUserUtils;
    private final List<BulkImportUser> usersToProcess;

    ProcessBulkUsersImportWorker(Main main, AppIdentifier app, List<BulkImportUser> usersToProcess, BulkImportSQLStorage bulkImportSQLStorage, BulkImportUserUtils bulkImportUserUtils){
        this.main = main;
        this.app = app;
        this.usersToProcess = usersToProcess;
        this.bulkImportSQLStorage = bulkImportSQLStorage;
        this.bulkImportUserUtils = bulkImportUserUtils;
    }

    @Override
    public void run() {
        try {
            processMultipleUsers(app, usersToProcess, bulkImportUserUtils, bulkImportSQLStorage);
        } catch (TenantOrAppNotFoundException | DbInitException | IOException | StorageQueryException e) {
            throw new RuntimeException(e);
        }
    }

    private void processMultipleUsers(AppIdentifier appIdentifier, List<BulkImportUser> users,
                                      BulkImportUserUtils bulkImportUserUtils,
                                      BulkImportSQLStorage baseTenantStorage)
            throws TenantOrAppNotFoundException, StorageQueryException, IOException,
            DbInitException {
        BulkImportUser user = null;
        try {
            final Storage[] allStoragesForApp = getAllProxyStoragesForApp(main, appIdentifier);
            int userIndexPointer = 0;
            List<BulkImportUser> validUsers = new ArrayList<>();
            Map<String, Exception> validationErrorsBeforeActualProcessing = new HashMap<>();
            while(userIndexPointer < users.size()) {
                user = users.get(userIndexPointer);
                if (Main.isTesting && Main.isTesting_skipBulkImportUserValidationInCronJob) {
                    // Skip validation when the flag is enabled during testing
                    // Skip validation if it's a retry run. This already passed validation. A revalidation triggers
                    // an invalid external user id already exists validation error - which is not true!
                    validUsers.add(user);
                } else {
                    // Validate the user
                    try {
                        validUsers.add(bulkImportUserUtils.createBulkImportUserFromJSON(main, appIdentifier,
                                user.toJsonObject(), BulkImportUserUtils.IDMode.READ_STORED));
                    } catch (InvalidBulkImportDataException exception) {
                        validationErrorsBeforeActualProcessing.put(user.id, new Exception(
                                String.valueOf(exception.errors)));
                    }
                }
                userIndexPointer+=1;
            }

            if(!validationErrorsBeforeActualProcessing.isEmpty()) {
                throw new BulkImportBatchInsertException("Invalid input data", validationErrorsBeforeActualProcessing);
            }
            // Since all the tenants of a user must share the storage, we will just use the
            // storage of the first tenantId of the first loginMethod

            Map<SQLStorage, List<BulkImportUser>> partitionedUsers = partitionUsersByStorage(appIdentifier, validUsers);
            for(SQLStorage bulkImportProxyStorage : partitionedUsers.keySet()) {
                boolean shouldRetryImmediatley = true;
                while (shouldRetryImmediatley) {
                    shouldRetryImmediatley = bulkImportProxyStorage.startTransaction(con -> {
                        try {
                            BulkImport.processUsersImportSteps(main, appIdentifier, bulkImportProxyStorage,
                                    partitionedUsers.get(bulkImportProxyStorage),
                                    allStoragesForApp);

                            bulkImportProxyStorage.commitTransactionForBulkImportProxyStorage();

                            String[] toDelete = new String[validUsers.size()];
                            for (int i = 0; i < validUsers.size(); i++) {
                                toDelete[i] = validUsers.get(i).id;
                            }

                            while (true){
                                try {
                                    baseTenantStorage.deleteBulkImportUsers(appIdentifier, toDelete);
                                    break;
                                } catch (Exception e) {
                                    // ignore and retry delete. The import transaction is already committed, the delete should happen no matter what
                                    Logging.debug(main, app.getAsPublicTenantIdentifier(), "Exception while deleting bulk import users: " + e.getMessage());
                                }
                            }
                        } catch (StorageTransactionLogicException | StorageQueryException e) {
                            // We need to rollback the transaction manually because we have overridden that in the proxy
                            // storage
                            bulkImportProxyStorage.rollbackTransactionForBulkImportProxyStorage();
                            if (isBulkImportTransactionRolledBackIsTheRealCause(e)) {
                                return true;
                                //@see BulkImportTransactionRolledBackException for explanation
                            }
                            handleProcessUserExceptions(app, validUsers, e, baseTenantStorage);
                        }
                        return false;
                    });
                }
            }
        } catch (StorageTransactionLogicException | InvalidConfigException e) {
            throw new RuntimeException(e);
        } catch (BulkImportBatchInsertException insertException) {
            handleProcessUserExceptions(app, users, insertException, baseTenantStorage);
        } finally {
            closeAllProxyStorages(); //closing it here to reuse the existing connection with all the users
        }
    }

    private boolean isBulkImportTransactionRolledBackIsTheRealCause(Throwable exception) {
        if(exception instanceof BulkImportTransactionRolledBackException){
            return true;
        } else if(exception.getCause()!=null){
            return isBulkImportTransactionRolledBackIsTheRealCause(exception.getCause());
        }
        return false;
    }

    private void handleProcessUserExceptions(AppIdentifier appIdentifier, List<BulkImportUser> usersBatch, Exception e,
                                             BulkImportSQLStorage baseTenantStorage)
            throws StorageQueryException {
        // Java doesn't allow us to reassign local variables inside a lambda expression
        // so we have to use an array.
        String[] errorMessage = { e.getMessage() };
        Map<String, String> bulkImportUserIdToErrorMessage = new HashMap<>();

        if (e instanceof StorageTransactionLogicException) {
            StorageTransactionLogicException exception = (StorageTransactionLogicException) e;
            // If the exception is due to a StorageQueryException, we want to retry the entry after sometime instead
            // of marking it as FAILED. We will return early in that case.
            if (exception.actualException instanceof StorageQueryException) {
                Logging.error(main, null, "We got an StorageQueryException while processing a bulk import user entry. It will be retried again. Error Message: " + e.getMessage(), true);
                return;
            }
            if(exception.actualException instanceof BulkImportBatchInsertException){
                handleBulkImportException(usersBatch, (BulkImportBatchInsertException) exception.actualException, bulkImportUserIdToErrorMessage);
            } else {
                //fail the whole batch
                errorMessage[0] = exception.actualException.getMessage();
                for(BulkImportUser user : usersBatch){
                    bulkImportUserIdToErrorMessage.put(user.id, errorMessage[0]);
                }
            }

        } else if (e instanceof InvalidBulkImportDataException) {
            errorMessage[0] = ((InvalidBulkImportDataException) e).errors.toString();
        } else if (e instanceof InvalidConfigException) {
            errorMessage[0] = e.getMessage();
        } else if (e instanceof BulkImportBatchInsertException) {
            handleBulkImportException(usersBatch, (BulkImportBatchInsertException) e, bulkImportUserIdToErrorMessage);
        }

        try {
            baseTenantStorage.startTransaction(con -> {
                baseTenantStorage.updateMultipleBulkImportUsersStatusToError_Transaction(appIdentifier, con,
                        bulkImportUserIdToErrorMessage);
                return null;
            });
        } catch (StorageTransactionLogicException e1) {
            throw new StorageQueryException(e1.actualException);
        }
    }

    private static void handleBulkImportException(List<BulkImportUser> usersBatch, BulkImportBatchInsertException exception,
                                                  Map<String, String> bulkImportUserIdToErrorMessage) {
        Map<String, Exception> userIndexToError = exception.exceptionByUserId;
        for(String userid : userIndexToError.keySet()){
            Optional<BulkImportUser> userWithId = usersBatch.stream()
                    .filter(bulkImportUser -> userid.equals(bulkImportUser.id) || userid.equals(bulkImportUser.externalUserId)).findFirst();
            String id = null;
            if(userWithId.isPresent()){
                id =  userWithId.get().id;
            }

            if(id == null) {
                userWithId = usersBatch.stream()
                        .filter(bulkImportUser ->
                                bulkImportUser.loginMethods.stream()
                                        .map(loginMethod -> loginMethod.superTokensUserId)
                                        .anyMatch(s -> s!= null && s.equals(userid))).findFirst();
                if(userWithId.isPresent()){
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

   private Map<SQLStorage, List<BulkImportUser>> partitionUsersByStorage(AppIdentifier appIdentifier, List<BulkImportUser> users)
           throws DbInitException, TenantOrAppNotFoundException, InvalidConfigException, IOException {
        Map<SQLStorage, List<BulkImportUser>> result = new HashMap<>();
        for(BulkImportUser user: users) {
            TenantIdentifier firstTenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                    appIdentifier.getAppId(), user.loginMethods.get(0).tenantIds.get(0));

            SQLStorage bulkImportProxyStorage =  (SQLStorage) getBulkImportProxyStorage(firstTenantIdentifier);
            if(!result.containsKey(bulkImportProxyStorage)){
                result.put(bulkImportProxyStorage, new ArrayList<>());
            }
            result.get(bulkImportProxyStorage).add(user);
        }
        return result;
   }
}
