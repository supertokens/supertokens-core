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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class CronTask extends ResourceDistributor.SingletonResource implements Runnable {

    protected final Main main;
    private final String jobName;

    // this is a 2d list where the 1st dimension is per use pool ID
    // and the 2nd dimension is all tenants in that same user pool ID
    protected List<List<TenantIdentifier>> tenantsInfo;
    private final Object lock = new Object();

    private final TenantIdentifier targetTenant;

    private final boolean isPerApp;

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
                // we create one thread per unique storage and run the query based on that.
                ExecutorService service = Executors.newFixedThreadPool(copied.size());
                AtomicBoolean threwQuitProgramException = new AtomicBoolean(false);
                for (List<TenantIdentifier> t : copied) {
                    service.execute(() -> {
                        try {
                            doTaskPerStorage(StorageLayer.getStorage(t.get(0), main));
                        } catch (Exception e) {
                            ProcessState.getInstance(main)
                                    .addState(ProcessState.PROCESS_STATE.CRON_TASK_ERROR_LOGGING, e);
                            Logging.error(main, t.get(0), "Cronjob threw an exception: " + this.jobName, Main.isTesting,
                                    e);
                            if (e instanceof QuitProgramException) {
                                threwQuitProgramException.set(true);
                            }
                        }

                        for (TenantIdentifier tenant : t) {
                            try {
                                doTaskPerTenant(tenant);
                            } catch (Exception e) {
                                ProcessState.getInstance(main)
                                        .addState(ProcessState.PROCESS_STATE.CRON_TASK_ERROR_LOGGING, e);
                                Logging.error(main, tenant, "Cronjob threw an exception: " + this.jobName,
                                        Main.isTesting, e);
                                if (e instanceof QuitProgramException) {
                                    threwQuitProgramException.set(true);
                                }
                            }
                        }
                    });
                }
                service.shutdown();
                boolean didShutdown = false;
                try {
                    didShutdown = service.awaitTermination(this.getIntervalTimeSeconds(), TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
                if (!didShutdown) {
                    service.shutdownNow();
                }
                if (threwQuitProgramException.get()) {
                    main.wakeUpMainThreadToShutdown();
                }
            }
        }
        Logging.info(main, this.targetTenant, "Cronjob finished: " + jobName, false);
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

    // the list belongs to tenants that are a part of the same user pool ID
    protected void doTaskPerStorage(Storage storage) throws Exception {

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
