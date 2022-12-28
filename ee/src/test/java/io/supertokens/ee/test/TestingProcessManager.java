package io.supertokens.ee.test;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ProcessState.EventAndException;
import io.supertokens.ProcessState.PROCESS_STATE;

import java.util.ArrayList;

public class TestingProcessManager {

    private static final ArrayList<TestingProcess> alive = new ArrayList<>();

    static void deleteAllInformation() throws Exception {
        System.out.println("----------DELETE ALL INFORMATION----------");
        String[] args = {"../../"};
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
            if (killed) {
                return;
            }
            main.killForTestingAndWaitForShutdown();
            killed = true;
        }

        public EventAndException checkOrWaitForEvent(PROCESS_STATE state) throws InterruptedException {
            return checkOrWaitForEvent(state, 15000);
        }

        EventAndException checkOrWaitForEvent(PROCESS_STATE state, long timeToWaitMS) throws InterruptedException {
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
