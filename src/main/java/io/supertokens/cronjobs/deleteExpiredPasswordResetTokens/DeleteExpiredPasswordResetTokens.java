/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.cronjobs.deleteExpiredPasswordResetTokens;

import io.supertokens.Main;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

public class DeleteExpiredPasswordResetTokens extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.deleteExpiredPasswordResetTokens"
            + ".DeleteExpiredPasswordResetTokens";

    private DeleteExpiredPasswordResetTokens(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("RemoveOldPasswordResetTokens", main, tenantsInfo, false);
    }

    public static DeleteExpiredPasswordResetTokens init(Main main,
                                                        List<List<TenantIdentifier>> tenantsInfo) {
        return (DeleteExpiredPasswordResetTokens) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new DeleteExpiredPasswordResetTokens(main, tenantsInfo));
    }

    @TestOnly
    public static DeleteExpiredPasswordResetTokens getInstance(Main main) {
        return (DeleteExpiredPasswordResetTokens) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    @Override
    protected void doTaskPerStorage(Storage storage) throws Exception {
        if (storage.getType() != STORAGE_TYPE.SQL) {
            return;
        }
        ((EmailPasswordSQLStorage) storage).deleteExpiredPasswordResetTokens();
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        return 3600; // every hour.
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        if (!Main.isTesting) {
            return getIntervalTimeSeconds();
        } else {
            return 0;
        }
    }
}
