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
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class CronTask extends ResourceDistributor.SingletonResource implements Runnable {

    protected final Main main;
    private final String jobName;
    protected final List<ResourceDistributor.KeyClass> tenantsInfo;

    protected CronTask(String jobName, Main main, List<ResourceDistributor.KeyClass> tenantsInfo) {
        this.jobName = jobName;
        this.main = main;
        this.tenantsInfo = tenantsInfo;
        Logging.info(main, "Starting task: " + jobName, false);
    }

    void shutdownIsGoingToBeCalled() {
        Logging.info(main, "Stopping task: " + jobName, false);
    }

    @Override
    public void run() {
        Logging.info(main, "Cronjob started: " + jobName, false);

        // first we copy over the array so that if it changes while the cronjob runs, it won't affect
        // this run of the cronjob
        List<ResourceDistributor.KeyClass> copied = new ArrayList<>(tenantsInfo);

        ExecutorService service = Executors.newFixedThreadPool(copied.size());
        AtomicBoolean threwQuitProgramException = new AtomicBoolean(false);
        for (ResourceDistributor.KeyClass keyClass : copied) {
            service.execute(() -> {
                try {
                    doTask(keyClass.getTenantIdentifier());
                } catch (Exception e) {
                    ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CRON_TASK_ERROR_LOGGING, e);
                    Logging.error(main, "Cronjob threw an exception: " + this.jobName, Main.isTesting, e);
                    if (e instanceof QuitProgramException) {
                        threwQuitProgramException.set(true);
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
        Logging.info(main, "Cronjob finished: " + jobName, false);
    }

    protected abstract void doTask(TenantIdentifier tenantIdentifier) throws Exception;

    public abstract int getIntervalTimeSeconds();

    public abstract int getInitialWaitTimeSeconds();
}
