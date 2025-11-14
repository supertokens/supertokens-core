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
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.httpRequest.HttpRequestForTesting;

import java.util.*;

import static org.junit.Assert.assertNotNull;

public class TestingProcessManager {

    static {
        TestServiceUtils.startServices();
    }

    private static final ArrayList<TestingProcess> isolatedProcesses = new ArrayList<>();

    public static void killAll() throws InterruptedException {
        killAllIsolatedProcesses();
        SharedProcess.end();
    }

    static void killAllIsolatedProcesses() {
        synchronized (isolatedProcesses) {
            for (TestingProcess testingProcess : isolatedProcesses) {
                try {
                    testingProcess.kill(true);
                    testingProcess.endProcess();
                } catch (InterruptedException ignored) {
                }
            }
            isolatedProcesses.clear();
        }
    }

    public static TestingProcess start(String[] args) throws InterruptedException {
        killAllIsolatedProcesses();
        return SharedProcess.start(args);
    }

    public static TestingProcess startIsolatedProcess(String[] args) throws InterruptedException {
        return startIsolatedProcess(args, true);
    }

    public static TestingProcess startIsolatedProcess(String[] args, boolean startProcess) throws InterruptedException {
        SharedProcess.end();

        return IsolatedProcess.start(args, startProcess);
    }


    public static interface TestingProcess {
        public void startProcess();
        public void endProcess() throws InterruptedException;
        public Main getProcess();

        public void kill() throws InterruptedException;
        public void kill(boolean removeData) throws InterruptedException;

        public TenantIdentifier getAppForTesting();

        public EventAndException checkOrWaitForEvent(PROCESS_STATE state) throws InterruptedException;
        public EventAndException checkOrWaitForEvent(PROCESS_STATE state, long timeToWaitMS) throws InterruptedException;
    }

    public static int getFreePort() {
        while (true) {
            int randomPort = 10000 + (int)(Math.random() * (20000 - 10000));
            try {
                java.net.Socket socket = new java.net.Socket("localhost", randomPort);
                socket.close();
                Thread.sleep(100);
            } catch (java.net.ConnectException e1) {
                // confirm again
                try {
                    Thread.sleep(new Random().nextInt(100));
                    java.net.Socket socket = new java.net.Socket("localhost", randomPort);
                    socket.close();
                } catch (java.net.ConnectException e2) {
                    // Port is available
                    return randomPort;
                } catch (Exception e3) {
                    throw new RuntimeException(e3);
                }

            } catch (Exception e4) {
                throw new RuntimeException(e4);
            }
        }
    }

    public static abstract class SharedProcess extends Thread implements TestingProcess {
        final Object waitToStart = new Object();
        private final String[] args;
        public Main main;
        boolean waitToStartNotified = false;

        private static SharedProcess instance = null;
        TenantIdentifier appForTesting = TenantIdentifier.BASE_TENANT;

        public static TestingProcess start(String[] args) throws InterruptedException {
            if (instance != null) {
                ProcessState.getInstance(instance.getProcess()).clear();
                instance.createAppForTesting();
                ProcessState.getInstance(instance.getProcess()).addState(PROCESS_STATE.STARTED, null);
                return instance;
            }

            int port = getFreePort();

            assert args.length == 1;
            args = new String[]{args[0], "port="+port};
            HttpRequestForTesting.corePort = port;

            final Object waitForInit = new Object();
            synchronized (isolatedProcesses) {
                instance = new SharedProcess(args) {

                    @Override
                    public void run() {
                        try {
                            this.main = new Main();
                            synchronized (waitForInit) {
                                waitForInit.notifyAll();
                            }

                            this.getProcess().start(getArgs());

                        } catch (Exception ignored) {
                        }
                    }
                };

                synchronized (waitForInit) {
                    instance.start();
                    waitForInit.wait();
                }

                EventAndException e = instance.checkOrWaitForEvents(
                        new PROCESS_STATE[]{
                                PROCESS_STATE.STARTED,
                                PROCESS_STATE.INIT_FAILURE}
                );

                if (e != null && e.state == PROCESS_STATE.STARTED) {
                    instance.createAppForTesting();
                }

                return instance;
            }
        }

        public static void end() throws InterruptedException {
            if (instance != null) {
                instance.endProcess();
            }
            instance = null;
        }

        SharedProcess(String[] args) {
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

        private void createAppForTesting() {
            {
                if (StorageLayer.getStorage(this.getProcess()).getType() != STORAGE_TYPE.SQL) {
                    try {
                        StorageLayer.getStorage(this.getProcess()).deleteAllInformation();
                    } catch (StorageQueryException e) {
                        throw new RuntimeException(e);
                    }
                    return;
                }
                TenantConfig[] allTenants = Multitenancy.getAllTenants(getProcess());
                try {
                    for (TenantConfig tenantConfig : allTenants) {
                        if (!tenantConfig.tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
                            Multitenancy.deleteTenant(tenantConfig.tenantIdentifier, getProcess());
                        }
                    }
                    for (TenantConfig tenantConfig : allTenants) {
                        if (!tenantConfig.tenantIdentifier.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID)) {
                            Multitenancy.deleteApp(tenantConfig.tenantIdentifier.toAppIdentifier(), getProcess());
                        }
                    }
                    for (TenantConfig tenantConfig : allTenants) {
                        if (!tenantConfig.tenantIdentifier.getConnectionUriDomain().equals(TenantIdentifier.DEFAULT_CONNECTION_URI)) {
                            Multitenancy.deleteConnectionUriDomain(tenantConfig.tenantIdentifier.getConnectionUriDomain(), getProcess());
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            // Create a new app and use that for testing
            String appId = UUID.randomUUID().toString();

            try {
                Multitenancy.addNewOrUpdateAppOrTenant(this.getProcess(), new TenantConfig(
                        new TenantIdentifier(null, appId, null),
                        new EmailPasswordConfig(true),
                        new ThirdPartyConfig(true, null),
                        new PasswordlessConfig(true),
                        null, null, new JsonObject()
                ), false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            this.setAppForTesting(new TenantIdentifier(null, appId, null));
            ResourceDistributor.setAppForTesting(new TenantIdentifier(null, appId, null));
        }

        public void setAppForTesting(TenantIdentifier tenantIdentifier) {
            appForTesting = tenantIdentifier;
        }

        public TenantIdentifier getAppForTesting() {
            return appForTesting;
        }

        public void kill() throws InterruptedException {
            kill(true);
        }

        public void kill(boolean removeAllInfo) throws InterruptedException {
            assert removeAllInfo;
            ProcessState.getInstance(main).addState(PROCESS_STATE.STOPPED, null);
        }

        public void endProcess() throws InterruptedException {
            for (int i = 0; i < 10; i++) {
                try {
                    main.deleteAllInformationForTesting();
                } catch (Exception e) {
                    if (e.getMessage().contains("Please call initPool before getConnection")) {
                        break;
                        // we ignore this type of message because it's due to tests in which the init failed
                        // and here we try and delete assuming that init had succeeded.
                    } else if (e.getMessage().contains("deadlock")) {
                        Thread.sleep(500);
                        continue; // try again
                    }
                    throw new RuntimeException(e);
                }
            }

            main.killForTestingAndWaitForShutdown();
            instance = null;
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
    }

    public static abstract class IsolatedProcess extends Thread implements TestingProcess {

        final Object waitToStart = new Object();
        private final String[] args;
        public Main main;
        boolean waitToStartNotified = false;
        private boolean killed = false;

        public static TestingProcess start(String[] args) throws InterruptedException {
            return start(args, true);
        }

        public static TestingProcess start(String[] args, boolean startProcess) throws InterruptedException {
            if (args.length == 1) {
                int port = getFreePort();

                args = new String[]{args[0], "port="+port};
                HttpRequestForTesting.corePort = port;
            }

            final Object waitForInit = new Object();
            synchronized (isolatedProcesses) {
                IsolatedProcess mainProcess = new IsolatedProcess(args) {
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
                isolatedProcesses.add(mainProcess);

                return mainProcess;
            }
        }

        IsolatedProcess(String[] args) {
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

        public void endProcess() {
            // no-op
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
                    // ignore
                }
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

        public TenantIdentifier getAppForTesting() {
            return TenantIdentifier.BASE_TENANT;
        }
    }

    /**
     * Utility function to wrap tests with, as they require TestingProcess
     */
    public static void withSharedProcess(ProcessConsumer consumer) throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        consumer.accept(process);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
