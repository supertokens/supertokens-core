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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ProcessState.EventAndException;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.ResourceDistributor;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.multitenancy.*;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;

public class TestingProcessManager {

    private static final ArrayList<TestingProcess> alive = new ArrayList<>();

    private static TestingProcess singletonProcess = null;

    private static boolean restartedProcess = false;

    static void killAll() {
        synchronized (alive) {
            for (TestingProcess testingProcess : alive) {
                try {
                    testingProcess.kill(true, 1);
                } catch (InterruptedException ignored) {
                }
            }
            alive.clear();
        }
    }

    private static void createAppForTesting() {
        assertNotNull(singletonProcess);

        TenantConfig[] allTenants = Multitenancy.getAllTenants(singletonProcess.getProcess());
        try {
            for (TenantConfig tenant : allTenants) {
                if (!tenant.tenantIdentifier.getTenantId().equals("public")) {
                    Multitenancy.deleteTenant(tenant.tenantIdentifier, singletonProcess.getProcess());
                }
            }
            for (TenantConfig tenant : allTenants) {
                if (!tenant.tenantIdentifier.getAppId().equals("public")) {
                    Multitenancy.deleteApp(tenant.tenantIdentifier.toAppIdentifier(), singletonProcess.getProcess());
                }
            }
            for (TenantConfig tenant : allTenants) {
                if (!tenant.tenantIdentifier.getConnectionUriDomain().equals("")) {
                    Multitenancy.deleteConnectionUriDomain(tenant.tenantIdentifier.getConnectionUriDomain(), singletonProcess.getProcess());
                }
            }
        } catch (Exception e) {
            // ignore
        }

        // Create a new app and use that for testing
        String appId = UUID.randomUUID().toString();

        try {
            Multitenancy.addNewOrUpdateAppOrTenant(singletonProcess.getProcess(), new TenantConfig(
                    new TenantIdentifier(null, appId, null),
                    new EmailPasswordConfig(true),
                    new ThirdPartyConfig(true, null),
                    new PasswordlessConfig(true),
                    null, null, new JsonObject()
            ), false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        singletonProcess.setAppForTesting(new TenantIdentifier(null, appId, null));
        ResourceDistributor.setAppForTesting(new TenantIdentifier(null, appId, null));
        ProcessState.getInstance(singletonProcess.getProcess()).addState(ProcessState.PROCESS_STATE.STARTED, null);
        ProcessState.getInstance(singletonProcess.getProcess()).addState(PROCESS_STATE.CREATED_TEST_APP, null);
    }

    public static TestingProcess start(String[] args, boolean startProcess, boolean killActiveProcesses) throws InterruptedException {
        if (singletonProcess != null) {
            ProcessState.getInstance(singletonProcess.getProcess()).clear();
            createAppForTesting();
            return singletonProcess;
        }

        if (alive.size() > 0 && killActiveProcesses) {
            killAll();
        }

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
            singletonProcess = mainProcess;

            if (startProcess) {
                EventAndException e = singletonProcess.checkOrWaitForEvents(
                        new PROCESS_STATE[]{
                                PROCESS_STATE.STARTED,
                                PROCESS_STATE.INIT_FAILURE}
                );

                if (e != null && e.state == PROCESS_STATE.STARTED) {
                    createAppForTesting();
                }
            } else {
                new Thread(() -> {
                    try {
                        EventAndException e = singletonProcess.checkOrWaitForEvents(
                                new PROCESS_STATE[]{
                                        PROCESS_STATE.STARTED,
                                        PROCESS_STATE.INIT_FAILURE}
                        );

                        if (e != null && e.state == PROCESS_STATE.STARTED) {
                            createAppForTesting();
                        }
                    } catch (Exception e) {}
                }).start();
            }

            return mainProcess;
        }
    }

    public static TestingProcess start(String[] args, boolean startProcess) throws InterruptedException {
        return start(args, startProcess, true);
    }

    public static TestingProcess start(String[] args) throws InterruptedException {
        return start(args, true, true);
    }

    public static TestingProcess restart(String[] args) throws InterruptedException {
        return restart(args, true);
    }

    public static TestingProcess restart(String[] args, boolean startProcess) throws InterruptedException {
        killAll();
        singletonProcess = null;
        restartedProcess = true;
        return start(args, startProcess);
    }

    public static abstract class TestingProcess extends Thread {

        final Object waitToStart = new Object();
        private final String[] args;
        public Main main;
        boolean waitToStartNotified = false;
        private boolean killed = false;
        TenantIdentifier appForTesting = TenantIdentifier.BASE_TENANT;

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
            kill(false, 0);
        }

        public void kill(int confirm) throws InterruptedException {
            kill(true, confirm);
        }

        public void kill(boolean removeAllInfo, int confirm) throws InterruptedException {
            if (!restartedProcess) {
                if (confirm == 0 && !appForTesting.getAppId().equals("public")) {
                    return;
                }
            }

            restartedProcess = false;

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

            if (singletonProcess == this) {
                singletonProcess = null;
            }
        }

        public EventAndException checkOrWaitForEvent(PROCESS_STATE state) throws InterruptedException {
            if (state == PROCESS_STATE.STOPPED && Main.isTesting) {
                return new EventAndException(PROCESS_STATE.STOPPED, null);
            }
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

        public EventAndException checkOrWaitForEvents(PROCESS_STATE[] states)
                throws InterruptedException {
            return checkOrWaitForEvents(states, 15000);
        }

        public EventAndException checkOrWaitForEvents(PROCESS_STATE[] states, long timeToWaitMS)
                throws InterruptedException {

            // we shall now wait until some time as passed.
            final long startTime = System.currentTimeMillis();
            while ((System.currentTimeMillis() - startTime) < timeToWaitMS) {
                for (PROCESS_STATE state : states) {
                    EventAndException e = ProcessState.getInstance(main).getLastEventByName(state);

                    if (e != null) {
                        return e;
                    }
                }

                Thread.sleep(100);
            }
            return null;
        }

        public void setAppForTesting(TenantIdentifier tenantIdentifier) {
            appForTesting = tenantIdentifier;
        }

        public TenantIdentifier getAppForTesting() {
            return appForTesting;
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
