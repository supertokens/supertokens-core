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
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessBulkUsersImportWorker implements Runnable {

    private final Map<String, SQLStorage> userPoolToStorageMap = new HashMap<>();
    private final Main main;
    private final AppIdentifier app;
    private final List<BulkImportUser> usersToImport;
    private final BulkImportSQLStorage bulkImportSQLStorage;
    private final BulkImportUserUtils bulkImportUserUtils;

    ProcessBulkUsersImportWorker(Main main, AppIdentifier app, List<BulkImportUser> userListToImport, BulkImportSQLStorage bulkImportSQLStorage, BulkImportUserUtils bulkImportUserUtils){
        this.main = main;
        this.app = app;
        this.usersToImport = userListToImport;
        this.bulkImportSQLStorage = bulkImportSQLStorage;
        this.bulkImportUserUtils = bulkImportUserUtils;
    }

    @Override
    public void run() {
        try {
            processMultipleUsers(app, usersToImport, bulkImportUserUtils, bulkImportSQLStorage);
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
            boolean shouldRetryImmediately = false;
            int userIndexPointer = 0;
            while(userIndexPointer < users.size()){
                user = users.get(userIndexPointer);
                if ((Main.isTesting && Main.isTesting_skipBulkImportUserValidationInCronJob) || shouldRetryImmediately) {
                    // Skip validation when the flag is enabled during testing
                    // Skip validation if it's a retry run. This already passed validation. A revalidation triggers
                    // an invalid external user id already exists validation error - which is not true!
                } else {
                    // Validate the user
                    bulkImportUserUtils.createBulkImportUserFromJSON(main, appIdentifier, user.toJsonObject(), user.id);
                }

                // Since all the tenants of a user must share the storage, we will just use the
                // storage of the first tenantId of the first loginMethod
                TenantIdentifier firstTenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                        appIdentifier.getAppId(), user.loginMethods.get(0).tenantIds.get(0));

                SQLStorage bulkImportProxyStorage =  (SQLStorage) getBulkImportProxyStorage(firstTenantIdentifier);
                BulkImportUser.LoginMethod primaryLM = BulkImport.getPrimaryLoginMethod(user);

                AuthRecipeSQLStorage authRecipeSQLStorage = (AuthRecipeSQLStorage) getBulkImportProxyStorage(
                        firstTenantIdentifier);

                /*
                 * We use two separate storage instances: one for importing the user and another for managing
                 * bulk_import_users entries.
                 * This is necessary because the bulk_import_users entries are always in the public tenant storage,
                 * but the actual user data could be in a different storage.
                 *
                 * If transactions are committed individually, in this order:
                 * 1. Commit the transaction that imports the user.
                 * 2. Commit the transaction that deletes the corresponding bulk import entry.
                 *
                 * There's a risk where the first commit succeeds, but the second fails. This creates a situation where
                 * the bulk import entry is re-processed, even though the user has already been imported into the
                 * database.
                 *
                 * To resolve this, we added a `primaryUserId` field to the `bulk_import_users` table.
                 * The processing logic now follows these steps:
                 *
                 * 1. Import the user and get the `primaryUserId` (transaction uncommitted).
                 * 2. Update the `primaryUserId` in the corresponding bulk import entry.
                 * 3. Commit the import transaction from step 1.
                 * 4. Delete the bulk import entry.
                 *
                 * If step 2 or any earlier step fails, nothing is committed, preventing partial state.
                 * If step 3 fails, the `primaryUserId` in the bulk import entry is updated, but the user doesn't
                 * exist in the database—this results in re-processing on the
                 * next run.
                 * If step 4 fails, the user exists but the bulk import entry remains; this will be handled by
                 * deleting it in the next run.
                 *
                 * The following code implements this logic.
                 */
                if (user.primaryUserId != null) {
                    AuthRecipeUserInfo importedUser = authRecipeSQLStorage.getPrimaryUserById(appIdentifier,
                            user.primaryUserId);

                    if (importedUser != null && isProcessedUserFromSameBulkImportUserEntry(importedUser, user)) {
                        baseTenantStorage.deleteBulkImportUsers(appIdentifier, new String[]{user.id});
                        return;
                    }
                }

                BulkImportUser finalUser = user;
                shouldRetryImmediately = bulkImportProxyStorage.startTransaction(con -> {
                    try {
                        Storage[] allStoragesForApp = getAllProxyStoragesForApp(main, appIdentifier);
                        BulkImport.processUserImportSteps(main, con, appIdentifier, bulkImportProxyStorage, finalUser,
                                primaryLM, allStoragesForApp);

                        // We are updating the primaryUserId in the bulkImportUser entry. This will help us handle
                        // the inconsistent transaction commit.
                        // If this update statement fails then the outer transaction will fail as well and the user
                        // will simpl be processed again. No inconsistency will happen in this
                        // case.
                        baseTenantStorage.updateBulkImportUserPrimaryUserId(appIdentifier, finalUser.id,
                                primaryLM.superTokensUserId);

                        // We need to commit the transaction manually because we have overridden that in the proxy
                        // storage
                        // If this fails, the primaryUserId will be updated in the bulkImportUser but it wouldn’t
                        // actually exist.
                        // When processing the user again, we'll check if primaryUserId exists with the same email.
                        // In this case the user won't exist, and we'll simply re-process it.
                        bulkImportProxyStorage.commitTransactionForBulkImportProxyStorage();

                        // NOTE: We need to use the baseTenantStorage as bulkImportProxyStorage could have a
                        // different storage than the baseTenantStorage
                        // If this fails, the primaryUserId will be updated in the bulkImportUser and it would exist
                        // in the database.
                        // When processing the user again, we'll check if primaryUserId exists with the same email.
                        // In this case the user will exist, and we'll simply delete the entry.
                        baseTenantStorage.deleteBulkImportUsers(appIdentifier, new String[]{finalUser.id});
                    } catch (StorageTransactionLogicException e) {
                        // We need to rollback the transaction manually because we have overridden that in the proxy
                        // storage
                        bulkImportProxyStorage.rollbackTransactionForBulkImportProxyStorage();
                        if(isBulkImportTransactionRolledBackIsTheRealCause(e)){
                            return true;
                            //@see BulkImportTransactionRolledBackException for explanation
                        }
                        handleProcessUserExceptions(app, finalUser, e, baseTenantStorage);
                    }
                    return false;
                });

                if(!shouldRetryImmediately){
                    userIndexPointer++;
                }
            }
        } catch (StorageTransactionLogicException | InvalidBulkImportDataException | InvalidConfigException e) {
            handleProcessUserExceptions(appIdentifier, user, e, baseTenantStorage);
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

    private void handleProcessUserExceptions(AppIdentifier appIdentifier, BulkImportUser user, Exception e,
                                             BulkImportSQLStorage baseTenantStorage)
            throws StorageQueryException {
        // Java doesn't allow us to reassign local variables inside a lambda expression
        // so we have to use an array.
        String[] errorMessage = { e.getMessage() };

        if (e instanceof StorageTransactionLogicException) {
            StorageTransactionLogicException exception = (StorageTransactionLogicException) e;
            // If the exception is due to a StorageQueryException, we want to retry the entry after sometime instead
            // of marking it as FAILED. We will return early in that case.
            if (exception.actualException instanceof StorageQueryException) {
                Logging.error(main, null, "We got an StorageQueryException while processing a bulk import user entry. It will be retried again. Error Message: " + e.getMessage(), true);
                return;
            }
            errorMessage[0] = exception.actualException.getMessage();
        } else if (e instanceof InvalidBulkImportDataException) {
            errorMessage[0] = ((InvalidBulkImportDataException) e).errors.toString();
        } else if (e instanceof InvalidConfigException) {
            errorMessage[0] = e.getMessage();
        }

        try {
            baseTenantStorage.startTransaction(con -> {
                baseTenantStorage.updateBulkImportUserStatus_Transaction(appIdentifier, con, user.id,
                        BulkImportStorage.BULK_IMPORT_USER_STATUS.FAILED, errorMessage[0]);
                return null;
            });
        } catch (StorageTransactionLogicException e1) {
            throw new StorageQueryException(e1.actualException);
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

    private Storage[] getAllProxyStoragesForApp(Main main, AppIdentifier appIdentifier)
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

    // Checks if the importedUser was processed from the same bulkImportUser entry.
    private boolean isProcessedUserFromSameBulkImportUserEntry(
            AuthRecipeUserInfo importedUser, BulkImportUser bulkImportEntry) {
        if (bulkImportEntry == null || importedUser == null || bulkImportEntry.loginMethods == null ||
                importedUser.loginMethods == null) {
            return false;
        }

        for (BulkImportUser.LoginMethod lm1 : bulkImportEntry.loginMethods) {
            for (io.supertokens.pluginInterface.authRecipe.LoginMethod lm2 : importedUser.loginMethods) {
                if (lm2.recipeId.toString().equals(lm1.recipeId)) {
                    if (lm1.email != null && !lm1.email.equals(lm2.email)) {
                        return false;
                    }

                    switch (lm1.recipeId) {
                        case "emailpassword":
                            if (lm1.passwordHash != null && !lm1.passwordHash.equals(lm2.passwordHash)) {
                                return false;
                            }
                            break;
                        case "thirdparty":
                            if ((lm1.thirdPartyId != null && !lm1.thirdPartyId.equals(lm2.thirdParty.id))
                                    || (lm1.thirdPartyUserId != null
                                    && !lm1.thirdPartyUserId.equals(lm2.thirdParty.userId))) {
                                return false;
                            }
                            break;
                        case "passwordless":
                            if (lm1.phoneNumber != null && !lm1.phoneNumber.equals(lm2.phoneNumber)) {
                                return false;
                            }
                            break;
                        default:
                            return false;
                    }
                }
            }
        }

        return true;
    }
}
