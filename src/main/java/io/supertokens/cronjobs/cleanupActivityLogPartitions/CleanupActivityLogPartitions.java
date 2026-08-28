/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.cronjobs.cleanupActivityLogPartitions;

import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.auditlog.ActivityLogStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

import java.util.List;

/**
 * Maintains the monthly, time-based partitioning of the activity_log table: pre-creates the partitions
 * for upcoming months and drops partitions whose entire month is older than the retention window.
 * Runs once per unique storage; a no-op for storages that don't partition (e.g. the in-memory store).
 */
public class CleanupActivityLogPartitions extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.cleanupActivityLogPartitions" +
            ".CleanupActivityLogPartitions";

    private CleanupActivityLogPartitions(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        super("CleanupActivityLogPartitions", main, tenantsInfo, false);
    }

    public static CleanupActivityLogPartitions init(Main main, List<List<TenantIdentifier>> tenantsInfo) {
        return (CleanupActivityLogPartitions) main.getResourceDistributor()
                .setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                        new CleanupActivityLogPartitions(main, tenantsInfo));
    }

    @Override
    protected void doTaskPerStorage(TenantIdentifier representative, Storage storage) throws Exception {
        if (storage instanceof ActivityLogStorage) {
            // A user pool never spans connection URI domains, so the representative tenant's config
            // yields this storage's retention deterministically.
            int retentionDays = Config.getConfig(representative, main).getActivityLogRetentionDays();
            ((ActivityLogStorage) storage).maintainActivityLogPartitions(retentionDays);
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
