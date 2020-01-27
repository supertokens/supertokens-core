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
