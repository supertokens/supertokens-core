/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.cronjobs.appInfoCheck;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.ResourceDistributor.SingletonResource;
import io.supertokens.cliOptions.CLIOptions;
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

        String devProductionMode = CLIOptions.get(main).getUserDevProductionMode();

        if (appIdInStorage == null || !ranOnce) {
            storage.setAppId(appId);
            storage.setUserDevProductionMode(devProductionMode);
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
