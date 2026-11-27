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
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.storageLayer.StorageLayer;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class CronTask extends ResourceDistributor.SingletonResource implements Runnable {

    protected final Main main;
    private final String jobName;

    // this is a 2d list where the 1st dimension is per use pool ID
    // and the 2nd dimension is all tenants in that same user pool ID
    protected List<List<TenantIdentifier>> tenantsInfo;
    private final Object lock = new Object();

    private final TenantIdentifier targetTenant;

    private final boolean isPerApp;

    // Storages (by representative tenant) in the order the next pass queues them. A pass that runs out of time
    // moves every storage it did not finish to the end, so storages it never reached go first next time and one
    // that never finishes cannot hold the front of every pass. Keyed rather than indexed so it stays right when
    // tenants are added or removed between passes. Only touched by this job's scheduler thread.
    private final LinkedHashSet<TenantIdentifier> passOrder = new LinkedHashSet<>();

    // The same for the tenants inside each storage: representative tenant -> the tenant that storage's next pass
    // starts from. Without it a storage whose tenants overrun the pass would restart at its first tenant every
    // time, and the later ones would never be reached. Written by the worker threads.
    private final Map<TenantIdentifier, TenantIdentifier> resumeAt = new ConcurrentHashMap<>();

    // This job's own per-storage pool, cron_storage_parallelism threads, created on its first per-storage pass.
    // Per job rather than shared, so a job stuck on one storage only delays itself, and its time limit only ever
    // covers its own queue.
    private ThreadPoolExecutor storageExecutor;
    // Storage tasks not yet finished, so shutdown can cancel the ones shutdownNow drains or interrupts.
    private final Set<FutureTask<?>> inFlight = ConcurrentHashMap.newKeySet();
    // Set at shutdown; no pool is created after it.
    private boolean stopping = false;

    protected CronTask(String jobName, Main main, List<List<TenantIdentifier>> tenantsInfo, boolean isPerApp) {
        this.jobName = jobName;
        this.main = main;
        this.tenantsInfo = tenantsInfo;
        this.targetTenant = null;
        this.isPerApp = isPerApp;
        Logging.info(main, null, "Starting task: " + jobName, false);
    }

    // this cronjob will only run for the targetTenant if it's in the tenantsInfo list. This is useful for 
    // cronjobs which run on a per app basis like the eelicensecheck cronjob
    protected CronTask(String jobName, Main main, TenantIdentifier targetTenant) {
        this.jobName = jobName;
        this.main = main;
        this.targetTenant = targetTenant;
        this.isPerApp = false;
        Logging.info(main, targetTenant, "Starting task: " + jobName, false);
    }

    void shutdownIsGoingToBeCalled() {
        Logging.info(main, this.targetTenant, "Stopping task: " + jobName, false);
    }

    @Override
    public void run() {
        Logging.info(main, this.targetTenant, "Cronjob started: " + jobName, false);

        if (this.targetTenant != null) {
            try {
                doTaskForTargetTenant(this.targetTenant);
            } catch (Exception e) {
                ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CRON_TASK_ERROR_LOGGING, e);
                Logging.error(main, this.targetTenant, "Cronjob threw an exception: " + this.jobName, Main.isTesting,
                        e);
                if (e instanceof QuitProgramException) {
                    main.wakeUpMainThreadToShutdown();
                }
            }
        } else {
            // first we copy over the array so that if it changes while the cronjob runs, it won't affect
            // this run of the cronjob
            List<List<TenantIdentifier>> copied = null;
            synchronized (lock) {
                copied = new ArrayList<>(tenantsInfo);
            }

            if (this.isPerApp) {
                // we extract all apps..
                List<AppIdentifier> apps = new ArrayList<>();
                Set<AppIdentifier> appsSet = new HashSet<>();
                for (List<TenantIdentifier> t : copied) {
                    for (TenantIdentifier tenant : t) {
                        if (appsSet.contains(tenant.toAppIdentifier())) {
                            continue;
                        }
                        appsSet.add(tenant.toAppIdentifier());
                        apps.add(tenant.toAppIdentifier());
                    }
                }

                for (AppIdentifier app : apps) {
                    try {
                        doTaskPerApp(app);
                    } catch (Exception e) {
                        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CRON_TASK_ERROR_LOGGING, e);
                        Logging.error(main, app.getAsPublicTenantIdentifier(),
                                "Cronjob threw an exception: " + this.jobName, Main.isTesting, e);
                        if (e instanceof QuitProgramException) {
                            main.wakeUpMainThreadToShutdown();
                        }
                    }
                }
            } else {
                // one task per unique storage, on this job's own pool, so the job connects to at most
                // cron_storage_parallelism storages at once and never waits behind another job's work.
                ThreadPoolExecutor pool = storageExecutor();
                if (pool == null) {
                    // the core is shutting down
                    return;
                }
                AtomicBoolean threwQuitProgramException = new AtomicBoolean(false);
                List<Future<?>> pending = new ArrayList<>();
                List<TenantIdentifier> submitted = new ArrayList<>();
                // Each pass records its own starts; a task cancelled at the end of the previous pass may still mark
                // itself started, so this is reset before queueing rather than after the pass.
                Set<TenantIdentifier> started = ConcurrentHashMap.newKeySet();
                for (List<TenantIdentifier> t : orderForThisPass(copied)) {
                    try {
                        pending.add(submit(pool, () -> {
                            started.add(t.get(0));
                            runForStorage(t, threwQuitProgramException);
                        }));
                        submitted.add(t.get(0));
                    } catch (RejectedExecutionException e) {
                        // the core is shutting down
                        break;
                    }
                }
                awaitPerStorageTasks(pending);
                updatePassOrder(pending, submitted, started);
                if (threwQuitProgramException.get()) {
                    main.wakeUpMainThreadToShutdown();
                }
            }
        }
        Logging.info(main, this.targetTenant, "Cronjob finished: " + jobName, false);
    }

    /*
     * One storage's work: the per-storage task, then each of its tenants, starting where the last pass on this
     * storage was cut off. Stops as soon as the worker is interrupted (the pass ran out of time, or the core is
     * stopping), so a cancelled task does not keep going through its tenants untracked or run alongside the next
     * pass on the same storage. An interruption is not an error and is not logged as one.
     */
    private void runForStorage(List<TenantIdentifier> tenants, AtomicBoolean threwQuitProgramException) {
        TenantIdentifier representative = tenants.get(0);
        try {
            doTaskPerStorage(representative, StorageLayer.getStorage(representative, main));
        } catch (Exception e) {
            if (wasInterrupted(e)) {
                return;
            }
            reportError(representative, e, threwQuitProgramException);
        }

        int n = tenants.size();
        // no resume point, or its tenant was removed since: from the top (null-checked, as an immutable list's
        // indexOf throws on null)
        TenantIdentifier resume = resumeAt.get(representative);
        int start = resume == null ? 0 : Math.max(0, tenants.indexOf(resume));
        for (int k = 0; k < n; k++) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            int i = (start + k) % n;
            // Where the next pass starts if this tenant is cut off: at this tenant, so it gets the next pass's full
            // budget, unless it already had one (it is first in this pass), in which case after it, so one tenant
            // that never fits cannot hold every pass.
            resumeAt.put(representative, k == 0 ? tenants.get((i + 1) % n) : tenants.get(i));
            try {
                doTaskPerTenant(tenants.get(i));
            } catch (Exception e) {
                if (wasInterrupted(e)) {
                    return;
                }
                reportError(tenants.get(i), e, threwQuitProgramException);
            }
        }
        // every tenant got its turn: the next pass starts from the top again
        resumeAt.remove(representative);
    }

    /* Job code that sleeps or waits gets an InterruptedException, which clears the flag; restore it and stop. */
    private static boolean wasInterrupted(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        return Thread.currentThread().isInterrupted();
    }

    private void reportError(TenantIdentifier tenant, Exception e, AtomicBoolean threwQuitProgramException) {
        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CRON_TASK_ERROR_LOGGING, e);
        Logging.error(main, tenant, "Cronjob threw an exception: " + this.jobName, Main.isTesting, e);
        if (e instanceof QuitProgramException) {
            threwQuitProgramException.set(true);
        }
    }

    /* Storages this pass queues: those in passOrder first, in that order, then any new ones in their usual order. */
    private List<List<TenantIdentifier>> orderForThisPass(List<List<TenantIdentifier>> copied) {
        List<List<TenantIdentifier>> ordered = new ArrayList<>();
        for (TenantIdentifier key : passOrder) {
            for (List<TenantIdentifier> t : copied) {
                if (t.get(0).equals(key)) {
                    ordered.add(t);
                    break;
                }
            }
        }
        for (List<TenantIdentifier> t : copied) {
            if (!passOrder.contains(t.get(0))) {
                ordered.add(t);
            }
        }
        return ordered;
    }

    /*
     * Next pass order: storages this pass did not finish (timed out, cancelled or never started) first, in the
     * order they were queued, then the ones that finished. A storage that never finishes within the interval
     * therefore drops behind the others instead of staying first and taking every pass. Storages no longer served
     * are not carried over, so the order only ever holds current storages.
     */
    private void updatePassOrder(List<Future<?>> pending, List<TenantIdentifier> submitted,
                                 Set<TenantIdentifier> started) {
        List<TenantIdentifier> notReached = new ArrayList<>();
        List<TenantIdentifier> timedOut = new ArrayList<>();
        List<TenantIdentifier> finished = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            Future<?> f = pending.get(i);
            if (f.isDone() && !f.isCancelled()) {
                finished.add(submitted.get(i));
            } else if (started.contains(submitted.get(i))) {
                timedOut.add(submitted.get(i));
            } else {
                notReached.add(submitted.get(i));
            }
        }
        passOrder.clear();
        passOrder.addAll(notReached);
        passOrder.addAll(finished);
        passOrder.addAll(timedOut);
    }

    /*
     * Waits for this pass's storages, at most one interval in total as before. The pool is this job's own, so the
     * wait only ever covers this job's own queue. Tasks still queued or running after that are cancelled and
     * dropped from the queue, so a slow pass cannot pile up behind the next one.
     */
    private void awaitPerStorageTasks(List<Future<?>> pending) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(this.getIntervalTimeSeconds());
        for (Future<?> f : pending) {
            try {
                f.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (TimeoutException | InterruptedException e) {
                pending.forEach(p -> p.cancel(true));
                ThreadPoolExecutor pool = storageExecutor;
                if (pool != null) {
                    pool.purge();
                }
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                } else {
                    Logging.warn(main, TenantIdentifier.BASE_TENANT, "Cronjob " + this.jobName
                            + " ran out of time; the next run starts with the storages it did not reach");
                }
                return;
            } catch (CancellationException e) {
                // cancelled by shutdown (stopStorageWork)
            } catch (ExecutionException e) {
                // Exceptions are logged inside the task; this is an Error (e.g. a plugin's NoSuchMethodError),
                // which the task does not catch and would otherwise be dropped with its Future.
                ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CRON_TASK_ERROR_LOGGING,
                        e.getCause() instanceof Exception ? (Exception) e.getCause() : e);
                Logging.error(main, TenantIdentifier.BASE_TENANT, "Cronjob threw an error: " + this.jobName,
                        Main.isTesting, e.getCause() instanceof Exception ? (Exception) e.getCause() : e);
            }
        }
    }

    /* Wraps a storage task so it is tracked until it finishes, and so shutdown can cancel it. */
    private FutureTask<?> submit(ThreadPoolExecutor pool, Runnable work) {
        FutureTask<?> task = new FutureTask<Void>(work, null) {
            @Override
            protected void done() {
                inFlight.remove(this);
            }
        };
        inFlight.add(task);
        try {
            pool.execute(task);
        } catch (RuntimeException e) {
            inFlight.remove(task);
            throw e;
        }
        return task;
    }

    /*
     * This job's pool, created on first use: daemon platform threads named after the job (as
     * StorageLayer.newStorageInitExecutor does), so a task stuck in a JDBC read never keeps the JVM alive after
     * stop and thread dumps show which job each thread belongs to. Null once the core is stopping.
     */
    private synchronized ThreadPoolExecutor storageExecutor() {
        if (storageExecutor == null) {
            if (stopping) {
                return null;
            }
            int threads = Cronjobs.getInstance(main).getStorageParallelism();
            AtomicInteger threadNumber = new AtomicInteger();
            storageExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threads, runnable -> {
                Thread thread = new Thread(runnable, "cron-" + jobName + "-" + threadNumber.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
        }
        return storageExecutor;
    }

    /*
     * Called by Cronjobs at shutdown, before storages are closed: drops queued storage work instead of letting it
     * start and open connections, interrupts running work, and cancels every tracked Future, since shutdownNow
     * neither completes the ones it drains nor the ones a worker is in. That releases a scheduler thread waiting
     * on them in awaitPerStorageTasks.
     */
    synchronized void stopStorageWork() {
        stopping = true;
        if (storageExecutor != null) {
            storageExecutor.shutdownNow();
        }
        inFlight.forEach(t -> t.cancel(true));
    }

    /* Waits up to the given time for interrupted storage work to stop, so it is not running when storages close. */
    void awaitStorageWorkStopped(long nanos) throws InterruptedException {
        ThreadPoolExecutor pool;
        synchronized (this) {
            pool = storageExecutor;
        }
        if (pool != null) {
            pool.awaitTermination(nanos, TimeUnit.NANOSECONDS);
        }
    }

    public void setTenantsInfo(List<List<TenantIdentifier>> tenantsInfo) {
        synchronized (lock) {
            if (this.targetTenant != null) {
                boolean found = false;
                for (List<TenantIdentifier> t : tenantsInfo) {
                    for (TenantIdentifier tenant : t) {
                        if (tenant.equals(targetTenant)) {
                            found = true;
                        }
                    }
                }
                if (!found) {
                    Cronjobs.removeCronjob(main, this);
                }
            } else {
                this.tenantsInfo = tenantsInfo;
            }
        }
    }

    @TestOnly
    public List<List<TenantIdentifier>> getTenantsInfo() {
        return this.tenantsInfo;
    }

    // run() calls only doTaskPerApp for a per-app job, and only doTaskPerStorage / doTaskPerTenant otherwise
    @TestOnly
    public boolean isPerApp() {
        return this.isPerApp;
    }

    // the list belongs to tenants that are a part of the same user pool ID
    protected void doTaskPerStorage(Storage storage) throws Exception {

    }

    // same as doTaskPerStorage(Storage) but also receives the representative tenant the dispatcher
    // resolved for this storage (t.get(0)), so tasks that need a TenantIdentifier to read config can
    // get one. Defaults to delegating to the old signature so existing crons keep working unchanged.
    protected void doTaskPerStorage(TenantIdentifier representative, Storage storage) throws Exception {
        doTaskPerStorage(storage);
    }

    protected void doTaskForTargetTenant(TenantIdentifier targetTenant) throws Exception {

    }

    protected void doTaskPerApp(AppIdentifier app) throws Exception {

    }

    protected void doTaskPerTenant(TenantIdentifier tenant) throws Exception {

    }

    public abstract int getIntervalTimeSeconds();

    public abstract int getInitialWaitTimeSeconds();
}
