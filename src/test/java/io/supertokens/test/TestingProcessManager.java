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

package io.supertokens.test;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ProcessState.EventAndException;
import io.supertokens.ProcessState.PROCESS_STATE;

import java.util.ArrayList;

class TestingProcessManager {

    private static final ArrayList<TestingProcess> alive = new ArrayList<>();

    static void deleteAllInformation() throws Exception {
        System.out.println("----------DELETE ALL INFORMATION----------");
        String[] args = {"../", "DEV"};
        TestingProcess process = TestingProcessManager.start(args);
        process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        process.main.deleteAllInformationForTesting();
        process.kill();
        System.out.println("----------DELETE ALL INFORMATION----------");
    }

    static void killAll() {
        synchronized (alive) {
            for (TestingProcess testingProcess : alive) {
                try {
                    testingProcess.kill();
                } catch (InterruptedException ignored) {
                }
            }
            alive.clear();
        }
    }

    public static TestingProcess start(String[] args, boolean startProcess)
            throws InterruptedException {
        final Object waitForInit = new Object();
        synchronized (alive) {
            TestingProcess mainProcess = new TestingProcess(args) {

                @Override
                public void run() {
                    try {

                        this.main = new Main();
                        synchronized (waitForInit) {
                            waitForInit.notifyAll();
                        }

                        if (startProcess) {
                            this.getProcess().start(getArgs());
                        } else {
                            synchronized (waitToStart) {
                                if (!waitToStartNotified) {
                                    waitToStart.wait();
                                }
                            }
                            this.getProcess().start(getArgs());
                        }

                    } catch (Exception ignored) {
                    }
                }
            };
            synchronized (waitForInit) {
                mainProcess.start();
                waitForInit.wait();
            }
            alive.add(mainProcess);
            return mainProcess;
        }
    }

    public static TestingProcess start(String[] args) throws InterruptedException {
        return start(args, true);
    }

    public static abstract class TestingProcess extends Thread {

        final Object waitToStart = new Object();
        private final String[] args;
        Main main;
        boolean waitToStartNotified = false;
        private boolean killed = false;

        TestingProcess(String[] args) {
            this.args = args;
        }

        void startProcess() {
            synchronized (waitToStart) {
                waitToStartNotified = true;
                waitToStart.notify();
            }
        }

        public Main getProcess() {
            return main;
        }

        String[] getArgs() {
            return args;
        }

        void kill() throws InterruptedException {
            if (killed) {
                return;
            }
            main.killForTestingAndWaitForShutdown();
            killed = true;
        }

        EventAndException checkOrWaitForEvent(PROCESS_STATE state) throws InterruptedException {
            return checkOrWaitForEvent(state, 15000);
        }

        EventAndException checkOrWaitForEvent(PROCESS_STATE state, long timeToWaitMS)
                throws InterruptedException {
            EventAndException e = ProcessState.getInstance(main).getLastEventByName(state);
            if (e == null) {
                // we shall now wait until some time as passed.
                final long startTime = System.currentTimeMillis();
                while (e == null && (System.currentTimeMillis() - startTime) < timeToWaitMS) {
                    Thread.sleep(100);
                    e = ProcessState.getInstance(main).getLastEventByName(state);
                }
            }
            return e;
        }
    }
}
