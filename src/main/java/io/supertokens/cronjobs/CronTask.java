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
import io.supertokens.licenseKey.LicenseKey;
import io.supertokens.licenseKey.LicenseKey.MODE;
import io.supertokens.output.Logging;

public abstract class CronTask extends ResourceDistributor.SingletonResource implements Runnable {

    protected final Main main;
    private final String jobName;

    protected CronTask(String jobName, Main main) {
        this.jobName = jobName;
        this.main = main;
        Logging.info(main, "Starting task: " + jobName);
    }

    void shutdownIsGoingToBeCalled() {
        Logging.info(main, "Stopping task: " + jobName);
    }

    @Override
    public void run() {
        try {
            Logging.debug(main, "Cronjob started: " + jobName);
            doTask();
        } catch (Exception e) {
            ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CRON_TASK_ERROR_LOGGING, e);
            Logging.error(main, "Cronjob threw an exception: " + this.jobName,
                    LicenseKey.get(main).getMode() != MODE.PRODUCTION, e);
            if (e instanceof QuitProgramException) {
                main.wakeUpMainThreadToShutdown();
            }
        }
        Logging.debug(main, "Cronjob finished: " + jobName);
    }

    protected abstract void doTask() throws Exception;

    public abstract int getIntervalTimeSeconds();

    public abstract int getInitialWaitTimeSeconds();
}
