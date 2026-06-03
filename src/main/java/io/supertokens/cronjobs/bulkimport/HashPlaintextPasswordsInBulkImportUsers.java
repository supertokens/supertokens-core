/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.emailpassword.PasswordHashing;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;

import java.util.List;

/**
 * Periodically finds bulk import users with plaintext passwords, hashes them using the
 * app-level hashing algorithm, and writes the hash back to the raw_data column.
 *
 * After this task runs, those users become eligible for pickup by ProcessBulkImportUsers,
 * which filters out any record whose raw_data still contains a plainTextPassword field.
 */
public class HashPlaintextPasswordsInBulkImportUsers extends CronTask {

    public static final String RESOURCE_KEY =
            "io.supertokens.cronjobs.bulkimport.HashPlaintextPasswordsInBulkImportUsers";

    private HashPlaintextPasswordsInBulkImportUsers(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("HashPlaintextPasswordsInBulkImportUsers", main, tenantsInfo, true);
    }

    public static HashPlaintextPasswordsInBulkImportUsers init(Main main,
            List<List<TenantIdentifier>> tenantsInfo) {
        return (HashPlaintextPasswordsInBulkImportUsers) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new HashPlaintextPasswordsInBulkImportUsers(main, tenantsInfo));
    }

    @Override
    protected void doTaskPerApp(AppIdentifier app)
            throws TenantOrAppNotFoundException, StorageQueryException {

        if (StorageLayer.getBaseStorage(main).getType() != STORAGE_TYPE.SQL || StorageLayer.isInMemDb(main)) {
            return;
        }

        BulkImportStorage bulkImportStorage = (BulkImportStorage) StorageLayer
                .getStorage(app.getAsPublicTenantIdentifier(), main);

        int limit = Config.getConfig(app.getAsPublicTenantIdentifier(), main).getBulkMigrationBatchSize();
        List<BulkImportUser> users = bulkImportStorage.getBulkImportUsersWithPlaintextPasswords(app, limit);

        if (users.isEmpty()) {
            return;
        }

        Logging.debug(main, app.getAsPublicTenantIdentifier(),
                "HashPlaintextPasswordsInBulkImportUsers: hashing passwords for " + users.size() + " users");

        PasswordHashing passwordHashing = PasswordHashing.getInstance(main);

        for (BulkImportUser user : users) {
            boolean modified = false;
            for (BulkImportUser.LoginMethod lm : user.loginMethods) {
                if ("emailpassword".equals(lm.recipeId)
                        && lm.plainTextPassword != null
                        && lm.passwordHash == null) {
                    lm.passwordHash = passwordHashing.createHashWithSalt(app, lm.plainTextPassword);
                    lm.plainTextPassword = null;
                    modified = true;
                }
            }
            if (modified) {
                bulkImportStorage.updateBulkImportUserRawData(app, user.id, user.toRawDataForDbStorage());
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
        return BulkImport.HASH_PASSWORDS_INTERVAL_SECONDS;
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
