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

package io.supertokens.cronjobs.deletePastOrphanedTokens;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.licenseKey.LicenseKey;
import io.supertokens.storageLayer.StorageLayer;

public class DeletePastOrphanedTokens extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.deletePastOrphanedTokens" +
            ".DeletePastOrphanedTokens";
    private long timeInMSForHowLongToKeepThePastTokensForTesting = 1000 * 60 * 60 * 24 * 7;   // 7 days.

    private DeletePastOrphanedTokens(Main main) {
        super("RemoveOldOrphanedSessions", main);
    }

    public static DeletePastOrphanedTokens getInstance(Main main) {
        ResourceDistributor.SingletonResource instance = main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance == null) {
            instance = main.getResourceDistributor()
                    .setResource(RESOURCE_KEY, new DeletePastOrphanedTokens(main));
        }
        return (DeletePastOrphanedTokens) instance;
    }

    public void setTimeInMSForHowLongToKeepThePastTokensForTesting(long time) {
        timeInMSForHowLongToKeepThePastTokensForTesting = time;
    }

    @Override
    protected void doTask() throws Exception {
        long createdBefore = System.currentTimeMillis() - getTimeInMSForHowLongToKeepThePastTokens();
        StorageLayer.getStorageLayer(this.main).deletePastOrphanedTokens(createdBefore);
    }

    private long getTimeInMSForHowLongToKeepThePastTokens() {
        if (Main.isTesting && LicenseKey.get(main).getMode() == LicenseKey.MODE.DEV) {
            return timeInMSForHowLongToKeepThePastTokensForTesting;
        }
        return 1000 * 60 * 60 * 24 * 7;   // 7 days.
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting && LicenseKey.get(main).getMode() == LicenseKey.MODE.DEV) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        return (12 * 3600); // twice a day.
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        if (LicenseKey.get(main).getMode() == LicenseKey.MODE.PRODUCTION) {
            return getIntervalTimeSeconds();
        } else {
            return 0;
        }
    }
}