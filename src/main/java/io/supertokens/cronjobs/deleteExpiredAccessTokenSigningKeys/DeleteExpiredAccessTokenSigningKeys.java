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
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.signingkeys.AccessTokenSigningKey;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

public class DeleteExpiredAccessTokenSigningKeys extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.deleteExpiredAccessTokenSigningKeys" +
            ".DeleteExpiredAccessTokenSigningKeys";

    private DeleteExpiredAccessTokenSigningKeys(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("DeleteExpiredAccessTokenSigningKeys", main, tenantsInfo, true);
    }

    public static DeleteExpiredAccessTokenSigningKeys init(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        return (DeleteExpiredAccessTokenSigningKeys) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new DeleteExpiredAccessTokenSigningKeys(main, tenantsInfo));
    }

    @TestOnly
    public static DeleteExpiredAccessTokenSigningKeys getInstance(Main main) {
        try {
            return (DeleteExpiredAccessTokenSigningKeys) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected void doTaskPerApp(AppIdentifier app) throws Exception {
        AccessTokenSigningKey.getInstance(app, main).cleanExpiredAccessTokenSigningKeys();
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
