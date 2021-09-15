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

package io.supertokens.cronjobs.deleteExpiredAccessTokenSigningKeys;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.session.accessToken.AccessTokenSigningKey;

public class DeleteExpiredAccessTokenSigningKeys extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.deleteExpiredAccessTokenSigningKeys.DeleteExpiredAccessTokenSigningKeys";

    private DeleteExpiredAccessTokenSigningKeys(Main main) {
        super("DeleteExpiredAccessTokenSigningKeys", main);
    }

    public static DeleteExpiredAccessTokenSigningKeys getInstance(Main main) {
        ResourceDistributor.SingletonResource instance = main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance == null) {
            instance = main.getResourceDistributor().setResource(RESOURCE_KEY, new DeleteExpiredAccessTokenSigningKeys(main));
        }
        return (DeleteExpiredAccessTokenSigningKeys) instance;
    }

    @Override
    protected void doTask() throws Exception {
        AccessTokenSigningKey.getInstance(main).cleanExpiredAccessTokenSigningKeys();
    }

    @Override
    public int getIntervalTimeSeconds() {
        CoreConfig config = Config.getConfig(main);
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }

        // This can get out of sync with the keys expiring, but this shouldn't be an issue. We never use the expired keys anyway.
        return Math.max((int) (config.getAccessTokenSigningKeyUpdateInterval() / 1000), 1);
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
