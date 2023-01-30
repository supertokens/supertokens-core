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

package io.supertokens.cronjobs.deleteExpiredSessions;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

public class DeleteExpiredSessions extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.deleteExpiredSessions.DeleteExpiredSessions";

    private DeleteExpiredSessions(Main main, List<ResourceDistributor.KeyClass> tenantsInfo) {
        super("RemoveOldSessions", main, tenantsInfo);
    }

    public static DeleteExpiredSessions init(Main main, List<ResourceDistributor.KeyClass> tenantsInfo) {
        return (DeleteExpiredSessions) main.getResourceDistributor()
                .setResource(null, null, RESOURCE_KEY, new DeleteExpiredSessions(main, tenantsInfo));
    }

    @TestOnly
    public static DeleteExpiredSessions getInstance(Main main) {
        ResourceDistributor.SingletonResource instance = main.getResourceDistributor()
                .getResource(null, null, RESOURCE_KEY);
        if (instance == null) {
            throw new QuitProgramException("Please call init() before calling getInstance");
        }
        return (DeleteExpiredSessions) instance;
    }

    @Override
    protected void doTask(String connectionUriDomain, String tenantId) throws Exception {
        StorageLayer.getSessionStorage(connectionUriDomain, tenantId, this.main).deleteAllExpiredSessions();
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        return (12 * 3600); // twice a day.
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
