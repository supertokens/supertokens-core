/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.cronjobs.syncCoreConfigWithDb;

import io.supertokens.Main;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.cronjobs.deleteExpiredSessions.DeleteExpiredSessions;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.MultitenancyHelper;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

import java.util.List;

public class SyncCoreConfigWithDb extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.syncCoreConfigWithDb.SyncCoreConfigWithDb";

    Main main;

    private SyncCoreConfigWithDb(Main main) {
        super("SyncCoreConfigWithDb", main, TenantIdentifier.BASE_TENANT);
        this.main = main;
    }

    public static SyncCoreConfigWithDb init(Main main) {
        return (SyncCoreConfigWithDb) main.getResourceDistributor()
                .setResource(TenantIdentifier.BASE_TENANT, RESOURCE_KEY,
                        new SyncCoreConfigWithDb(main));
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        return 60;
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        if (Main.isTesting) {
            return 0;
        }
        return 60;
    }

    @Override
    protected void doTaskForTargetTenant(TenantIdentifier targetTenant) throws Exception {
        MultitenancyHelper.getInstance(main).refreshTenantsInCoreBasedOnChangesInCoreConfigOrIfTenantListChanged(true);
    }
}
