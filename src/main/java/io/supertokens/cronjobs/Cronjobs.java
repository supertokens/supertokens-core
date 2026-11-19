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
import io.supertokens.config.Config;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Cronjobs extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.cronjobs.Cronjobs";
    final Object lock = new Object();
    // Longest random offset added to a job's first run (see addCronjob).
    public static final int MAX_INITIAL_JITTER_SECONDS = 3600;
    // How long shutdown waits for interrupted storage work to stop before storages are closed.
    private static final long STORAGE_WORK_STOP_WAIT_SECONDS = 5;

    private final ScheduledExecutorService executor;
    // Each job's per-storage work runs on that job's own pool of this many threads (see CronTask), so one job's
    // pass connects to at most this many storages at once, and a slow job only delays itself.
    private final int storageParallelism;
    private List<CronTask> tasks = new ArrayList<>();

    private Cronjobs(int storageParallelism) {
        this.executor = Executors.newScheduledThreadPool(5);
        this.storageParallelism = storageParallelism;
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
        main.getResourceDistributor().setResource(new TenantIdentifier(null, null, null), RESOURCE_KEY,
                new Cronjobs(Config.getBaseConfig(main).getCronStorageParallelism()));
    }

    public int getStorageParallelism() {
        return this.storageParallelism;
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
                // Storages are closed right after this returns, so per-storage work must stop now: queued tasks
                // are dropped instead of starting and opening connections, running ones are interrupted, and
                // their Futures are cancelled so scheduler threads waiting on them return.
                for (CronTask t : instance.tasks) {
                    t.stopStorageWork();
                }
                for (CronTask t : instance.tasks) {
                    t.awaitStorageWorkStopped(STORAGE_WORK_STOP_WAIT_SECONDS);
                }
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
                int interval = task.getIntervalTimeSeconds();
                long initialWait = task.getInitialWaitTimeSeconds();
                if (!Main.isTesting) {
                    initialWait += initialJitterSeconds(initialWait, interval);
                }
                instance.executor.scheduleWithFixedDelay(task, initialWait, interval, TimeUnit.SECONDS);
                instance.tasks.add(task);
            }
        }
    }

    /*
     * Jobs whose first run is a full interval after start would otherwise fire in the same instant as every other
     * job with that interval, and as the same job on cores started together, for the life of the process. A random
     * offset of up to one interval (capped at an hour) spreads them out. Jobs that start sooner on purpose (the
     * license check, telemetry, bulk import, the backfill) keep their own timing: their start is set relative to
     * boot and to each other, and an offset would break that.
     */
    public static long initialJitterSeconds(long initialWaitSeconds, int intervalSeconds) {
        if (intervalSeconds <= 0 || initialWaitSeconds < intervalSeconds) {
            return 0;
        }
        return ThreadLocalRandom.current().nextInt(Math.min(intervalSeconds, MAX_INITIAL_JITTER_SECONDS));
    }

    public static boolean isCronjobLoaded(Main main, CronTask task) {
        if (getInstance(main) == null) {
            init(main);
        }
        Cronjobs instance = getInstance(main);
        synchronized (instance.lock) {
            return instance.tasks.contains(task);
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
