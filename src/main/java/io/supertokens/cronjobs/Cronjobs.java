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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Cronjobs extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.cronjobs.Cronjobs";
    final Object lock = new Object();
    // Longest random offset added to a job's first run (see addCronjob).
    public static final int MAX_INITIAL_JITTER_SECONDS = 3600;

    private final ScheduledExecutorService executor;
    // Shared by every cron job's per-storage work, so at most cron_storage_parallelism storages are worked on at
    // once across all jobs, instead of one thread (and its database connections) per storage per job.
    private final ThreadPoolExecutor storageExecutor;
    private final int storageParallelism;
    // Storage tasks not yet finished. Shutdown cancels them, so cron threads waiting on them return.
    private final Set<FutureTask<?>> inFlight = ConcurrentHashMap.newKeySet();
    private List<CronTask> tasks = new ArrayList<>();

    private Cronjobs(int storageParallelism) {
        this.executor = Executors.newScheduledThreadPool(5);
        this.storageParallelism = storageParallelism;
        this.storageExecutor = new ThreadPoolExecutor(storageParallelism, storageParallelism, 0L,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
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

    FutureTask<?> submitStorageTask(Runnable work) {
        FutureTask<?> task = new FutureTask<Void>(work, null) {
            @Override
            protected void done() {
                inFlight.remove(this);
            }
        };
        inFlight.add(task);
        try {
            storageExecutor.execute(task);
        } catch (RuntimeException e) {
            inFlight.remove(task);
            throw e;
        }
        return task;
    }

    // drops cancelled tasks from the queue; Future.cancel alone leaves them there until a worker picks them up
    void purgeStorageTasks() {
        storageExecutor.purge();
    }

    @TestOnly
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
                // No new runs, and no new storage tasks (submit is rejected from here on).
                instance.executor.shutdown();
                instance.storageExecutor.shutdown();
                if (!instance.storageExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    // storages are closed right after this returns: drop queued work, interrupt running work
                    instance.storageExecutor.shutdownNow();
                }
                // shutdownNow neither completes the Futures it drains nor ones a worker is still in, so a cron
                // thread waiting on them would block for up to its interval. Cancelling them releases it. This
                // runs before waiting for the scheduler, whose threads may be the ones waiting.
                instance.inFlight.forEach(t -> t.cancel(true));
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
                long initialWait = task.getInitialWaitTimeSeconds();
                if (!Main.isTesting) {
                    initialWait += initialJitterSeconds(task.getIntervalTimeSeconds());
                }
                instance.executor.scheduleWithFixedDelay(task, initialWait, task.getIntervalTimeSeconds(),
                        TimeUnit.SECONDS);
                instance.tasks.add(task);
            }
        }
    }

    /*
     * Most jobs wait one interval before their first run, so jobs with the same interval, and cores started
     * together, would otherwise fire in the same instant for the life of the process. A random offset of up to one
     * interval (capped at an hour) spreads them out. Tests keep their exact timings (addCronjob skips this there).
     */
    public static long initialJitterSeconds(int intervalSeconds) {
        int bound = Math.min(intervalSeconds, MAX_INITIAL_JITTER_SECONDS);
        return bound <= 0 ? 0 : ThreadLocalRandom.current().nextInt(bound);
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
