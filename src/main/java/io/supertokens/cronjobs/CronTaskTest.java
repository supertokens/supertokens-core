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

package io.supertokens.cronjobs;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor.SingletonResource;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import org.jetbrains.annotations.TestOnly;

import java.util.HashMap;
import java.util.Map;

public class CronTaskTest extends SingletonResource {
    private static final String RESOURCE_ID = "io.supertokens.cronjobs.CronTaskTest";
    private Map<String, Integer> cronTaskToInterval = new HashMap<String, Integer>();

    private CronTaskTest() {

    }

    public static CronTaskTest getInstance(Main main) {
        try {
            return (CronTaskTest) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_ID);
        } catch (TenantOrAppNotFoundException ignored) {
            return (CronTaskTest) main.getResourceDistributor()
                    .setResource(new TenantIdentifier(null, null, null), RESOURCE_ID, new CronTaskTest());
        }
    }

    @TestOnly
    public void setIntervalInSeconds(String resourceId, int interval) {
        cronTaskToInterval.put(resourceId, interval);
    }

    public Integer getIntervalInSeconds(String resourceId) {
        return cronTaskToInterval.get(resourceId);
    }
}
