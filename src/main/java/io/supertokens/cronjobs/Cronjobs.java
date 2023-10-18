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
import io.supertokens.ResourceDistributor;
import io.supertokens.multitenancy.MultitenancyHelper;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Cronjobs extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.cronjobs.Cronjobs";
    final Object lock = new Object();
    private final ScheduledExecutorService executor;
    private List<CronTask> tasks = new ArrayList<>();

    private Cronjobs() {
        this.executor = Executors.newScheduledThreadPool(5);
    }

    public static Cronjobs getInstance(Main main) {
        try {
            return (Cronjobs) main.getResourceDistributor()
                    .getResource(new TenantIdentifier(null, null, null), RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void init(Main main) {
        main.getResourceDistributor().setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY, new Cronjobs());
    }

    public static void shutdownAndAwaitTermination(Main main) {
        if (getInstance(main) == null) {
            return;
        }
        Cronjobs instance = getInstance(main);
        synchronized (instance.lock) {
            for (CronTask t : instance.tasks) {
                t.shutdownIsGoingToBeCalled();
            }
            try {
                instance.executor.shutdown();
                instance.executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // ignore any error as app is shutting down.
            }
        }
    }

    public void setTenantsInfo(List<List<TenantIdentifier>> tenantsInfo) {
        this.tasks.forEach(cronTask -> {
            cronTask.setTenantsInfo(tenantsInfo);
        });
    }

    public static void removeCronjob(Main main, CronTask task) {
        Cronjobs instance = Cronjobs.getInstance(main);
        if (instance != null) {
            synchronized (instance.lock) {
                instance.tasks.remove(task);
            }
        }
    }

    public static void addCronjob(Main main, CronTask task) {
        if (getInstance(main) == null) {
            init(main);
        }
        Cronjobs instance = getInstance(main);
        synchronized (instance.lock) {
            if (!instance.tasks.contains(task)) {
                instance.executor.scheduleWithFixedDelay(task, task.getInitialWaitTimeSeconds(),
                        task.getIntervalTimeSeconds(), TimeUnit.SECONDS);
                instance.tasks.add(task);
            }
        }
    }

    @TestOnly
    public List<CronTask> getTasks() {
        return this.tasks;
    }

    @TestOnly
    public List<List<List<TenantIdentifier>>> getTenantInfos() {
        List<List<List<TenantIdentifier>>> tenantsInfos = new ArrayList<>();
        for (CronTask task : this.tasks) {
            tenantsInfos.add(task.getTenantsInfo());
        }
        return tenantsInfos;
    }
}
