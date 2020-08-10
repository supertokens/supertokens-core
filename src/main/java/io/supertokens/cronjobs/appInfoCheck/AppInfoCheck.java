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

package io.supertokens.cronjobs.appInfoCheck;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.ResourceDistributor.SingletonResource;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.licenseKey.LicenseKey;
import io.supertokens.licenseKey.LicenseKey.MODE;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.storageLayer.StorageLayer;

/**
 * This task's purpose is to make sure that one app is only using one set of
 * licenseKeys - all of them should have the same appId. Failure of this results
 * in the shutting down of the service.
 */
public class AppInfoCheck extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.appInfoCheck.AppInfoCheck";

    private boolean ranOnce = false;

    private AppInfoCheck(Main main) {
        super("AppInfoCheck", main);
    }

    public static AppInfoCheck getInstance(Main main) {
        SingletonResource instance = main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance == null) {
            instance = main.getResourceDistributor().setResource(RESOURCE_KEY, new AppInfoCheck(main));
        }
        return (AppInfoCheck) instance;
    }

    @Override
    protected void doTask() throws StorageQueryException {
        /*
         * Normally we would do the following in a transaction manner, but since the
         * purpose of this cron job is to catch misuse eventually, we can afford to not
         * do a transaction.
         *
         * The appId, once set, should never change for this app.
         */

        Storage storage = StorageLayer.getStorageLayer(main);

        String appId = LicenseKey.get(main).getAppId(main);

        String appIdInStorage = storage.getAppId();

        if (appIdInStorage == null || !ranOnce) {
            storage.setAppId(appId);
        } else {
            if (!appId.equals(appIdInStorage)) {
                ProcessState.getInstance(main).addState(PROCESS_STATE.APP_ID_MISMATCH, null);
                Logging.error(main,
                        "Using different license keys from different apps for the same app. Quiting service!", true);
                main.wakeUpMainThreadToShutdown();
            }
        }
        ranOnce = true;
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting && LicenseKey.get(main).getMode() == MODE.DEV) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        return 5 * 60;
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        return 0;
    }

}
