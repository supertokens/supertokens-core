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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.bulkimport.BulkImportUserUtils;
import io.supertokens.bulkimport.exceptions.InvalidBulkImportDataException;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.sqlStorage.AuthRecipeSQLStorage;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage.BULK_IMPORT_USER_STATUS;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser.LoginMethod;
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

public class ProcessBulkImportUsers extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.ee.cronjobs.ProcessBulkImportUsers";
    private Map<String, SQLStorage> userPoolToStorageMap = new HashMap<>();

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
            throws TenantOrAppNotFoundException, StorageQueryException, IOException, DbInitException {

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        BulkImportSQLStorage bulkImportSQLStorage = (BulkImportSQLStorage) StorageLayer
                .getStorage(app.getAsPublicTenantIdentifier(), main);

        List<BulkImportUser> users = bulkImportSQLStorage.getBulkImportUsersAndChangeStatusToProcessing(app,
                BulkImport.PROCESS_USERS_BATCH_SIZE);

        String[] allUserRoles = StorageUtils.getUserRolesStorage(bulkImportSQLStorage).getRoles(app);
        BulkImportUserUtils bulkImportUserUtils = new BulkImportUserUtils(allUserRoles);

        for (BulkImportUser user : users) {
            processUser(app, user, bulkImportUserUtils, bulkImportSQLStorage);
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
        } catch (TenantOrAppNotFoundException | InvalidConfigException | IOException | DbInitException e) {
            throw new StorageTransactionLogicException(e);
        }
    }

    private void closeAllProxyStorages() throws StorageQueryException {
        for (SQLStorage storage : userPoolToStorageMap.values()) {
            storage.closeConnectionForBulkImportProxyStorage();
        }
        userPoolToStorageMap.clear();
    }

    private void processUser(AppIdentifier appIdentifier, BulkImportUser user, BulkImportUserUtils bulkImportUserUtils,
            BulkImportSQLStorage baseTenantStorage)
            throws TenantOrAppNotFoundException, StorageQueryException, IOException,
            DbInitException {

        try {
            if (Main.isTesting && Main.isTesting_skipBulkImportUserValidationInCronJob) {
                // Skip validation when the flag is enabled during testing
            } else {
                // Validate the user
                bulkImportUserUtils.createBulkImportUserFromJSON(main, appIdentifier, user.toJsonObject(), user.id);
            }

            // Since all the tenants of a user must share the storage, we will just use the
            // storage of the first tenantId of the first loginMethod

            TenantIdentifier firstTenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                    appIdentifier.getAppId(), user.loginMethods.get(0).tenantIds.get(0));

            SQLStorage bulkImportProxyStorage = (SQLStorage) getBulkImportProxyStorage(firstTenantIdentifier);

            LoginMethod primaryLM = BulkImport.getPrimaryLoginMethod(user);

            AuthRecipeSQLStorage authRecipeSQLStorage = (AuthRecipeSQLStorage) getBulkImportProxyStorage(
                    firstTenantIdentifier);

            /*
             * We use two separate storage instances: one for importing the user and another for managing bulk_import_users entries.
             * This is necessary because the bulk_import_users entries are always in the public tenant storage,
             * but the actual user data could be in a different storage.
             * 
             * If transactions are committed individually, in this order:
             * 1. Commit the transaction that imports the user.
             * 2. Commit the transaction that deletes the corresponding bulk import entry.
             *
             * There's a risk where the first commit succeeds, but the second fails. This creates a situation where
             * the bulk import entry is re-processed, even though the user has already been imported into the database.
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
             * If step 3 fails, the `primaryUserId` in the bulk import entry is updated, but the user doesn't exist in the database—this results in re-processing on the
             * next run.
             * If step 4 fails, the user exists but the bulk import entry remains; this will be handled by deleting it in the next run.
             *
             * The following code implements this logic.
             */
            if (user.primaryUserId != null) {
                AuthRecipeUserInfo importedUser = authRecipeSQLStorage.getPrimaryUserById(appIdentifier,
                        user.primaryUserId);

                if (importedUser != null && isProcessedUserFromSameBulkImportUserEntry(importedUser, user)) {
                    baseTenantStorage.deleteBulkImportUsers(appIdentifier, new String[] { user.id });
                    return;
                }
            }

            bulkImportProxyStorage.startTransaction(con -> {
                try {
                    for (LoginMethod lm : user.loginMethods) {
                        BulkImport.processUserLoginMethod(main, appIdentifier, bulkImportProxyStorage, lm);
                    }

                    BulkImport.createPrimaryUserAndLinkAccounts(main, appIdentifier, bulkImportProxyStorage, user,
                            primaryLM);

                    Storage[] allStoragesForApp = getAllProxyStoragesForApp(main, appIdentifier);
                    BulkImport.createUserIdMapping(appIdentifier, user, primaryLM, allStoragesForApp);

                    BulkImport.verifyEmailForAllLoginMethods(appIdentifier, con, bulkImportProxyStorage,
                            user.loginMethods);
                    BulkImport.createTotpDevices(main, appIdentifier, bulkImportProxyStorage, user, primaryLM);
                    BulkImport.createUserMetadata(appIdentifier, bulkImportProxyStorage, user, primaryLM);
                    BulkImport.createUserRoles(main, appIdentifier, bulkImportProxyStorage, user);

                    // We are updating the primaryUserId in the bulkImportUser entry. This will help us handle the inconsistent transaction commit.
                    // If this update statement fails then the outer transaction will fail as well and the user will simpl be processed again. No inconsistency will happen in this
                    // case.
                    baseTenantStorage.updateBulkImportUserPrimaryUserId(appIdentifier, user.id,
                            primaryLM.superTokensUserId);

                    // We need to commit the transaction manually because we have overridden that in the proxy storage
                    // If this fails, the primaryUserId will be updated in the bulkImportUser but it wouldn’t actually exist.
                    // When processing the user again, we'll check if primaryUserId exists with the same email. In this case the user won't exist, and we'll simply re-process it.
                    bulkImportProxyStorage.commitTransactionForBulkImportProxyStorage();

                    // NOTE: We need to use the baseTenantStorage as bulkImportProxyStorage could have a different storage than the baseTenantStorage
                    // If this fails, the primaryUserId will be updated in the bulkImportUser and it would exist in the database.
                    // When processing the user again, we'll check if primaryUserId exists with the same email. In this case the user will exist, and we'll simply delete the entry.
                    baseTenantStorage.deleteBulkImportUsers(appIdentifier, new String[] { user.id });
                    return null;
                } catch (StorageTransactionLogicException e) {
                    // We need to rollback the transaction manually because we have overridden that in the proxy storage
                    bulkImportProxyStorage.rollbackTransactionForBulkImportProxyStorage();
                    throw e;
                } finally {
                    closeAllProxyStorages();
                }
            });
        } catch (StorageTransactionLogicException | InvalidBulkImportDataException | InvalidConfigException e) {
            handleProcessUserExceptions(appIdentifier, user, e, baseTenantStorage);
        }
    }

    private void handleProcessUserExceptions(AppIdentifier appIdentifier, BulkImportUser user, Exception e,
            BulkImportSQLStorage baseTenantStorage)
            throws StorageQueryException {

        // Java doesn't allow us to reassign local variables inside a lambda expression
        // so we have to use an array.
        String[] errorMessage = { e.getMessage() };

        if (e instanceof StorageTransactionLogicException) {
            StorageTransactionLogicException exception = (StorageTransactionLogicException) e;
            errorMessage[0] = exception.actualException.getMessage();
        } else if (e instanceof InvalidBulkImportDataException) {
            errorMessage[0] = ((InvalidBulkImportDataException) e).errors.toString();
        } else if (e instanceof InvalidConfigException) {
            errorMessage[0] = e.getMessage();
        }

        try {
            baseTenantStorage.startTransaction(con -> {
                baseTenantStorage.updateBulkImportUserStatus_Transaction(appIdentifier, con, user.id,
                        BULK_IMPORT_USER_STATUS.FAILED, errorMessage[0]);
                return null;
            });
        } catch (StorageTransactionLogicException e1) {
            throw new StorageQueryException(e1.actualException);
        }
    }

    // Checks if the importedUser was processed from the same bulkImportUser entry.
    private boolean isProcessedUserFromSameBulkImportUserEntry(
            AuthRecipeUserInfo importedUser, BulkImportUser bulkImportEntry) {
        if (bulkImportEntry == null || importedUser == null || bulkImportEntry.loginMethods == null ||
                importedUser.loginMethods == null) {
            return false;
        }

        for (LoginMethod lm1 : bulkImportEntry.loginMethods) {
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
