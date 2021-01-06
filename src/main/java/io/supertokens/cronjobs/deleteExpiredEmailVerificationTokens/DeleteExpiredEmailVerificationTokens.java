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

package io.supertokens.cronjobs.deleteExpiredEmailVerificationTokens;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.storageLayer.StorageLayer;

public class DeleteExpiredEmailVerificationTokens extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.deleteExpiredEmailVerificationTokens" +
            ".DeleteExpiredEmailVerificationTokens";

    private DeleteExpiredEmailVerificationTokens(Main main) {
        super("RemoveOldEmailVerificationTokens", main);
    }

    public static DeleteExpiredEmailVerificationTokens getInstance(Main main) {
        ResourceDistributor.SingletonResource instance = main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance == null) {
            instance = main.getResourceDistributor()
                    .setResource(RESOURCE_KEY, new DeleteExpiredEmailVerificationTokens(main));
        }
        return (DeleteExpiredEmailVerificationTokens) instance;
    }

    @Override
    protected void doTask() throws Exception {
        if (StorageLayer.getStorage(this.main).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        StorageLayer.getEmailPasswordStorage(this.main)
                .deleteExpiredEmailVerificationTokens();
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        return 12 * 3600; // twice a day.
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
