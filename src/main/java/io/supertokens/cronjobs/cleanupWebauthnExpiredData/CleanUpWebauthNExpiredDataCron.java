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

package io.supertokens.cronjobs.cleanupWebauthnExpiredData;

import io.supertokens.Main;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.webauthn.WebAuthNStorage;

import java.util.List;

public class CleanUpWebauthNExpiredDataCron extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.cleanupWebauthnExpiredData" +
            ".CleanUpWebauthnExpiredDataCron";

    private CleanUpWebauthNExpiredDataCron(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("CleanUpWebauthnExpiredDataCron", main, tenantsInfo, true);
    }

    public static CleanUpWebauthNExpiredDataCron init(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        return (CleanUpWebauthNExpiredDataCron) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new CleanUpWebauthNExpiredDataCron(main, tenantsInfo));
    }

    @Override
    protected void doTaskPerStorage(Storage storage) throws Exception {
        if (storage.getType() != STORAGE_TYPE.SQL) {
            return;
        }

        WebAuthNStorage webAuthNStorage = StorageUtils.getWebAuthNStorage(storage);
        webAuthNStorage.deleteExpiredAccountRecoveryTokens();
        webAuthNStorage.deleteExpiredGeneratedOptions();
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        // Every 24 hours.
        return 24 * 3600;
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
