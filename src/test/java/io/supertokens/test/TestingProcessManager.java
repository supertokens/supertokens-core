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

package io.supertokens.test;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ProcessState.EventAndException;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.storageLayer.StorageLayer;

import java.util.ArrayList;

import static org.junit.Assert.assertNotNull;

public class TestingProcessManager {

    private static final ArrayList<TestingProcess> alive = new ArrayList<>();

    static void deleteAllInformation() throws Exception {
        System.out.println("----------DELETE ALL INFORMATION----------");
        String[] args = {"../"};
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

    public static TestingProcess start(String[] args, boolean startProcess) throws InterruptedException {
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
        public Main main;
        boolean waitToStartNotified = false;
        private boolean killed = false;

        TestingProcess(String[] args) {
            this.args = args;
        }

        public void startProcess() {
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

        public void kill() throws InterruptedException {
            kill(true);
        }

        public void kill(boolean removeAllInfo) throws InterruptedException {
            if (killed) {
                return;
            }
            if (removeAllInfo) {
                try {
                    main.deleteAllInformationForTesting();
                } catch (Exception e) {
                    if (!e.getMessage().contains("Please call initPool before getConnection")) {
                        // we ignore this type of message because it's due to tests in which the init failed
                        // and here we try and delete assuming that init had succeeded.
                        throw new RuntimeException(e);
                    }
                }
            }
            main.killForTestingAndWaitForShutdown();
            killed = true;
        }

        public void killWithoutDeletingData() throws InterruptedException {
            if (killed) {
                return;
            }
            main.killForTestingAndWaitForShutdown();
            killed = true;
        }

        public EventAndException checkOrWaitForEvent(PROCESS_STATE state) throws InterruptedException {
            return checkOrWaitForEvent(state, 15000);
        }

        public EventAndException checkOrWaitForEvent(PROCESS_STATE state, long timeToWaitMS)
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

    /**
     * Utility function to wrap tests with, as they require TestingProcess
     */
    public static void withProcess(ProcessConsumer consumer) throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        consumer.accept(process);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
