/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.cronjobs.deadlocklogger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;

public class DeadlockLogger {

    private static final DeadlockLogger INSTANCE = new DeadlockLogger();

    private DeadlockLogger() {
    }

    public static DeadlockLogger getInstance() {
        return INSTANCE;
    }

    public void start(){
        Thread deadlockLoggerThread = new Thread(deadlockDetector, "DeadlockLoggerThread");
        deadlockLoggerThread.setDaemon(true);
        deadlockLoggerThread.start();
    }

    private final Runnable deadlockDetector = new Runnable() {
        @Override
        public void run() {
            System.out.println("DeadlockLogger started!");
            while (true) {
                System.out.println("DeadlockLogger - checking");
                ThreadMXBean bean = ManagementFactory.getThreadMXBean();
                long[] threadIds = bean.findDeadlockedThreads(); // Returns null if no threads are deadlocked.
                System.out.println("DeadlockLogger - DeadlockedThreads: " + Arrays.toString(threadIds));
                if (threadIds != null) {
                    ThreadInfo[] infos = bean.getThreadInfo(threadIds);
                    boolean deadlockFound = false;
                    System.out.println("DEADLOCK found!");
                    for (ThreadInfo info : infos) {
                        System.out.println("ThreadName: " + info.getThreadName());
                        System.out.println("Thread ID: " + info.getThreadId());
                        System.out.println("LockName: " + info.getLockName());
                        System.out.println("LockOwnerName: " + info.getLockOwnerName());
                        System.out.println("LockedMonitors: " + Arrays.toString(info.getLockedMonitors()));
                        System.out.println("LockInfo: " + info.getLockInfo());
                        System.out.println("Stack: " + Arrays.toString(info.getStackTrace()));
                        System.out.println();
                        deadlockFound = true;
                    }
                    System.out.println("*******************************");
                    if(deadlockFound) {
                        System.out.println(" ==== ALL THREAD INFO ===");
                        ThreadInfo[] allThreads = bean.dumpAllThreads(true, true, 100);
                        for (ThreadInfo threadInfo : allThreads) {
                            System.out.println("THREAD: " + threadInfo.getThreadName());
                            System.out.println("StackTrace: " + Arrays.toString(threadInfo.getStackTrace()));
                        }
                        break;
                    }
                }
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    };
}
