/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.cronjobs;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;

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

    private static Cronjobs getInstance(Main main) {
        return (Cronjobs) main.getResourceDistributor().getResource(RESOURCE_KEY);
    }

    public static void init(Main main) {
        if (getInstance(main) != null) {
            return;
        }
        main.getResourceDistributor().setResource(RESOURCE_KEY, new Cronjobs());
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

    public static void addCronjob(Main main, CronTask task) {
        if (getInstance(main) == null) {
            init(main);
        }
        Cronjobs instance = getInstance(main);
        synchronized (instance.lock) {
            instance.executor.scheduleWithFixedDelay(task, task.getInitialWaitTimeSeconds(),
                    task.getIntervalTimeSeconds(), TimeUnit.SECONDS);
            instance.tasks.add(task);
        }
    }

}
