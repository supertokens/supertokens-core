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

package io.supertokens.cronjobs.memoryWatcher;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor.SingletonResource;
import io.supertokens.backendAPI.Ping;
import io.supertokens.cronjobs.CronTask;
import io.supertokens.cronjobs.CronTaskTest;
import io.supertokens.licenseKey.LicenseKey;
import io.supertokens.licenseKey.LicenseKey.MODE;

public class MemoryWatcher extends CronTask {

    public static final String RESOURCE_KEY = "io.supertokens.cronjobs.memoryWatcher.MemoryWatcher";

    private long hourStartTime = System.currentTimeMillis();
    private int numberOfTimesCalledWithinHour = 0;

    private double maxTotalMemWithinAnHour = 0;
    private double minTotalMemWithinAnHour = Double.MAX_VALUE;
    private double sumTotalMemWithinAnHour = 0;

    private double maxMaxMemWithinAnHour = 0;
    private double minMaxMemWithinAnHour = Double.MAX_VALUE;
    private double sumMaxMemWithinAnHour = 0;

    public int HOUR_DELTA = 3600;   // public, so that it can change for testing purposes

    private final Object lock = new Object();

    private MemoryWatcher(Main main) {
        super("MemoryWatcher", main);
    }

    public static MemoryWatcher getInstance(Main main) {
        SingletonResource instance = main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance == null) {
            instance = main.getResourceDistributor().setResource(RESOURCE_KEY, new MemoryWatcher(main));
        }
        return (MemoryWatcher) instance;
    }

    public Ping.MemoryInfo updateMemoryInfo() {
        Ping.MemoryInfo result = null;
        try {
            long currTime = System.currentTimeMillis();
            Runtime runtime = Runtime.getRuntime();
            double totalMem = runtime.totalMemory();
            double maxMem = runtime.maxMemory();

            synchronized (lock) {
                long deltaHour = currTime - hourStartTime;
                if (deltaHour < 0) {
                    return null;
                }
                if (deltaHour > (HOUR_DELTA * 1000)) {
                    // we have crossed the last hour.
                    result = new Ping.MemoryInfo(
                            minTotalMemWithinAnHour == Double.MAX_VALUE ? 0 : minTotalMemWithinAnHour,
                            maxTotalMemWithinAnHour,
                            sumTotalMemWithinAnHour / numberOfTimesCalledWithinHour,
                            minMaxMemWithinAnHour == Double.MAX_VALUE ? 0 : minMaxMemWithinAnHour,
                            maxMaxMemWithinAnHour,
                            sumMaxMemWithinAnHour / numberOfTimesCalledWithinHour, hourStartTime);

                    // we do this in another thread as to not have to take another lock (Ping lock) while holding
                    // this lock
                    Ping.MemoryInfo finalResult = result;
                    new Thread(() -> Ping.getInstance(main).addToMemory(finalResult)).start();
                    numberOfTimesCalledWithinHour = 1;
                    maxTotalMemWithinAnHour = totalMem;
                    minTotalMemWithinAnHour = totalMem;
                    sumTotalMemWithinAnHour = totalMem;
                    maxMaxMemWithinAnHour = maxMem;
                    minMaxMemWithinAnHour = maxMem;
                    sumMaxMemWithinAnHour = maxMem;

                    hourStartTime = currTime;
                } else {
                    numberOfTimesCalledWithinHour++;
                    maxTotalMemWithinAnHour = Math.max(totalMem, maxTotalMemWithinAnHour);
                    minTotalMemWithinAnHour = Math.min(totalMem, minTotalMemWithinAnHour);
                    sumTotalMemWithinAnHour += totalMem;
                    maxMaxMemWithinAnHour = Math.max(maxMem, maxMaxMemWithinAnHour);
                    minMaxMemWithinAnHour = Math.min(maxMem, maxMaxMemWithinAnHour);
                    sumMaxMemWithinAnHour += maxMem;
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    @Override
    protected void doTask() {
        updateMemoryInfo();
    }

    @Override
    public int getIntervalTimeSeconds() {
        if (Main.isTesting && LicenseKey.get(main).getMode() == MODE.DEV) {
            Integer interval = CronTaskTest.getInstance(main).getIntervalInSeconds(RESOURCE_KEY);
            if (interval != null) {
                return interval;
            }
        }
        return 60;
    }

    @Override
    public int getInitialWaitTimeSeconds() {
        if (LicenseKey.get(main).getMode() == MODE.PRODUCTION) {
            return getIntervalTimeSeconds();
        } else {
            return 0;
        }
    }

}
