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

package io.supertokens;

import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.cronjobs.Cronjobs;
import io.supertokens.cronjobs.deleteExpiredAccessTokenSigningKeys.DeleteExpiredAccessTokenSigningKeys;
import io.supertokens.cronjobs.deleteExpiredEmailVerificationTokens.DeleteExpiredEmailVerificationTokens;
import io.supertokens.cronjobs.deleteExpiredPasswordResetTokens.DeleteExpiredPasswordResetTokens;
import io.supertokens.cronjobs.deleteExpiredPasswordlessDevices.DeleteExpiredPasswordlessDevices;
import io.supertokens.cronjobs.deleteExpiredSessions.DeleteExpiredSessions;
import io.supertokens.cronjobs.telemetry.Telemetry;
import io.supertokens.emailpassword.PasswordHashing;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.inmemorydb.Start;
import io.supertokens.jwt.JWTSigningKey;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.session.accessToken.AccessTokenSigningKey;
import io.supertokens.session.refreshToken.RefreshTokenKey;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.version.Version;
import io.supertokens.webserver.Webserver;
import org.jetbrains.annotations.TestOnly;
import sun.misc.Unsafe;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;

public class Main {

    // this is a special variable that will be set to true by TestingProcessManager
    public static boolean isTesting = false;

    // this is a special variable that will be set to true by TestingProcessManager
    public static boolean makeConsolePrintSilent = false;
    private final Object mainThreadWakeUpMonitor = new Object();
    // will be unique every time the server has started.
    private String processId = UUID.randomUUID().toString();
    private boolean mainThreadWokenUpToShutdown = false;

    private Thread mainThread = Thread.currentThread();

    private Thread shutdownHook;
    private final Object shutdownHookLock = new Object();
    private boolean programEnded = false;

    private long PROCESS_START_TIME = System.currentTimeMillis();

    private ResourceDistributor resourceDistributor = new ResourceDistributor();

    private String startedFileName = null;

    private boolean waitToInitStorageModule = false;
    private final Object waitToInitStorageModuleLock = new Object();

    private boolean waitToEnableFeatureFlag = false;
    private final Object waitToEnableFeatureFlagLock = new Object();


    private boolean forceInMemoryDB = false;

    public static void main(String[] args) {
        new Main().start(args);
    }

    private static void assertIsTesting() {
        if (!isTesting) {
            throw new QuitProgramException("Testing function called in non testing env");
        }
    }

    public void start(String[] args) {
        int exitCode = 0;
        try {
            suppressIllegalAccessWarning();
            if (Main.isTesting) {
                System.out.println("Process ID: " + this.getProcessId());
            }
            ProcessState.getInstance(this).addState(ProcessState.PROCESS_STATE.INIT, null);
            try {
                try {
                    CLIOptions.load(this, args);
                    init();
                } catch (Exception e) {
                    ProcessState.getInstance(this).addState(ProcessState.PROCESS_STATE.INIT_FAILURE, e);
                    throw e;
                }
                ProcessState.getInstance(this).addState(ProcessState.PROCESS_STATE.STARTED, null);
                putMainThreadToSleep();

                ProcessState.getInstance(this).addState(ProcessState.PROCESS_STATE.SHUTTING_DOWN, null);
                stopApp();

                Logging.info(this, "Goodbye", true);
            } catch (Exception e) {

                ProcessState.getInstance(this).addState(ProcessState.PROCESS_STATE.SHUTTING_DOWN, null);
                stopApp();
                Logging.error(this, "What caused the crash: " + e.getMessage(), true, e);
                exitCode = 1;
            }
            ProcessState.getInstance(this).addState(ProcessState.PROCESS_STATE.STOPPED, null);
        } finally {
            synchronized (shutdownHookLock) {
                programEnded = true;
                shutdownHookLock.notifyAll();
            }
        }
        if (exitCode != 0 && !Main.isTesting) {
            System.exit(exitCode);
        }
    }

    private void init() throws IOException, StorageQueryException {

        // Handle kill signal gracefully
        handleKillSignalForWhenItHappens();

        // loading configs for core from config.yaml file.
        try {
            Config.loadBaseConfig(this);
        } catch (InvalidConfigException e) {
            throw new QuitProgramException(e);
        }

        Logging.info(this, "Completed config.yaml loading.", true);

        // loading storage layer
        try {
            StorageLayer.initPrimary(this, CLIOptions.get(this).getInstallationPath() + "plugin/",
                    Config.getBaseConfigAsJsonObject(this));
        } catch (InvalidConfigException e) {
            throw new QuitProgramException(e);
        }

        // loading version file
        Version.loadVersion(this, CLIOptions.get(this).getInstallationPath() + "version.yaml");

        // init file logging
        Logging.initFileLogging(this);

        // initialise cron job handler
        Cronjobs.init(this);

        // initialise storage module
        synchronized (waitToInitStorageModuleLock) {
            ProcessState.getInstance(this).addState(ProcessState.PROCESS_STATE.WAITING_TO_INIT_STORAGE_MODULE, null);
            while (waitToInitStorageModule) {
                try {
                    waitToInitStorageModuleLock.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
        StorageLayer.getStorage(this).initStorage();

        // enable ee features if license key is provided.
        synchronized (waitToEnableFeatureFlagLock) {
            while (waitToEnableFeatureFlag) {
                try {
                    waitToEnableFeatureFlagLock.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
        FeatureFlag.init(this, CLIOptions.get(this).getInstallationPath() + "ee/");

        if (Arrays.stream(FeatureFlag.getInstance(this).getEnabledFeatures())
                .anyMatch(ee_features -> ee_features == EE_FEATURES.MULTI_TENANCY)) {
            try {
                // load all configs for each of the tenants.
                Config.loadAllTenantConfig(this);

                // init storage layers for each unique db connection based on unique (user pool ID, connection pool ID).
                StorageLayer.loadAllTenantStorage(this);
            } catch (InvalidConfigException e) {
                throw new QuitProgramException(e);
            }
        }

        // init signing keys
        // TODO: add this for all tenants based on db separation only (and not tenant id)
        AccessTokenSigningKey.init(this);
        RefreshTokenKey.init(this);
        JWTSigningKey.init(this);

        // starts removing old session cronjob
        // TODO: start as many instances of the cronjob as required based on number of tenants. It might be per db or
        //  per db & some config based on what the cronjob needs.
        Cronjobs.addCronjob(this, DeleteExpiredSessions.getInstance(this));

        // starts removing old password reset tokens
        Cronjobs.addCronjob(this, DeleteExpiredPasswordResetTokens.getInstance(this));

        // starts removing expired email verification tokens
        Cronjobs.addCronjob(this, DeleteExpiredEmailVerificationTokens.getInstance(this));

        // removes passwordless devices with only expired codes
        Cronjobs.addCronjob(this, DeleteExpiredPasswordlessDevices.getInstance(this));

        // starts Telemetry cronjob if the user has not disabled it
        if (!Config.getConfig(this).isTelemetryDisabled()) {
            Cronjobs.addCronjob(this, Telemetry.getInstance(this));
        }

        // starts DeleteExpiredAccessTokenSigningKeys cronjob if the access token signing keys can change
        // TODO: make this in a loop per tenant per db
        if (Config.getConfig(this).getAccessTokenSigningKeyDynamic()) {
            Cronjobs.addCronjob(this, DeleteExpiredAccessTokenSigningKeys.getInstance(this));
        }

        // TODO: need to determine how many pools combinations to make.
        // creates password hashing pool
        PasswordHashing.init(this);

        // start web server to accept incoming traffic
        Webserver.getInstance(this).start();

        // this is a sign to the controlling script that this process has started.
        createDotStartedFileForThisProcess();

        // NOTE: If the message below is changed, make sure to also change the corresponding check in the CLI program
        // for start command
        Logging.info(this, "Started SuperTokens on " + Config.getConfig(this).getHost(this) + ":"
                + Config.getConfig(this).getPort(this) + " with PID: " + ProcessHandle.current().pid(), true);
    }

    @TestOnly
    public void setForceInMemoryDB() {
        if (Main.isTesting) {
            // if we had the storage layer initialised from a previous test, we close it.
            StorageLayer.close();
            this.forceInMemoryDB = true;
        } else {
            throw new RuntimeException("Calling testing method in non-testing env");
        }
    }

    public boolean isForceInMemoryDB() {
        return this.forceInMemoryDB;
    }

    @TestOnly
    public void waitToInitStorageModule() {
        if (Main.isTesting) {
            // if we had the storage layer initialised from a previous test, we close it.
            StorageLayer.close();
            synchronized (waitToInitStorageModuleLock) {
                waitToInitStorageModule = true;
            }
        } else {
            throw new RuntimeException("Calling testing method in non-testing env");
        }
    }

    @TestOnly
    public void proceedWithInitingStorageModule() {
        if (Main.isTesting) {
            synchronized (waitToInitStorageModuleLock) {
                waitToInitStorageModule = false;
                waitToInitStorageModuleLock.notifyAll();
            }
        } else {
            throw new RuntimeException("Calling testing method in non-testing env");
        }
    }

    @TestOnly
    public void waitToEnableFeatureFlag() {
        if (Main.isTesting) {
            synchronized (waitToEnableFeatureFlagLock) {
                waitToEnableFeatureFlag = true;
            }
        } else {
            throw new RuntimeException("Calling testing method in non-testing env");
        }
    }

    @TestOnly
    public void proceedToEnableFeatureFlag() {
        if (Main.isTesting) {
            synchronized (waitToEnableFeatureFlagLock) {
                waitToEnableFeatureFlag = false;
                waitToEnableFeatureFlagLock.notifyAll();
            }
        } else {
            throw new RuntimeException("Calling testing method in non-testing env");
        }
    }

    private void createDotStartedFileForThisProcess() throws IOException {
        CoreConfig config = Config.getConfig(this);
        String fileName = OperatingSystem.getOS() == OperatingSystem.OS.WINDOWS
                ? CLIOptions.get(this).getInstallationPath() + ".started\\" + config.getHost(this) + "-"
                + config.getPort(this)
                : CLIOptions.get(this).getInstallationPath() + ".started/" + config.getHost(this) + "-"
                + config.getPort(this);
        File dotStarted = new File(fileName);
        if (!dotStarted.exists()) {
            File parent = dotStarted.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            dotStarted.createNewFile();
        }
        boolean ignored = dotStarted.setWritable(true, false);
        this.startedFileName = fileName;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(dotStarted))) { // overwrite mode
            writer.write(ProcessHandle.current().pid() + "\n" + Config.getConfig(this).getBasePath());
        }
    }

    private void removeDotStartedFileForThisProcess() {
        try {
            if (startedFileName != null) {
                Files.deleteIfExists(Paths.get(startedFileName));
            }
        } catch (Exception e) {
            Logging.error(this, "Error while removing .started file", false, e);
        }
    }

    public ResourceDistributor getResourceDistributor() {
        return resourceDistributor;
    }

    @TestOnly
    public void deleteAllInformationForTesting() throws Exception {
        assertIsTesting();
        try {
            StorageLayer.deleteAllInformation(this);
        } catch (StorageQueryException e) {
            throw new Exception(e);
        }
    }

    @TestOnly
    public void killForTestingAndWaitForShutdown() throws InterruptedException {
        assertIsTesting();
        wakeUpMainThreadToShutdown();
        mainThread.join();
    }

    // must not throw any error
    // must wait for everything to finish and only then exit
    private void stopApp() {
        try {
            // We do this first because it was initialized first.
            // so if something else fails below due to config not initialized,
            // then at least this will be cleared.
            if (this.shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
                } catch (IllegalStateException e) {
                    // we are shutting down already.. so doesn't matter
                }
            }

            // Note that logging may throw an error if the config
            // was not loaded due to an error in config. But this is OK
            // since we load config before loading anything else
            // below this, and this whole block is surrounded in a
            // try / catch.
            Logging.info(this, "Stopping SuperTokens...", true);
            Webserver.getInstance(this).stop();
            Cronjobs.shutdownAndAwaitTermination(this);
            if (!Main.isTesting) {
                StorageLayer.close(this);
            } else {
                if (StorageLayer.getStorage(this).getType() == STORAGE_TYPE.NOSQL_1
                        || StorageLayer.getStorage(this) instanceof Start) {
                    // we close mongodb storage and in mem db storage during testing everytime as well cause it
                    // doesn't take time for it to connect during each test.
                    StorageLayer.close(this);
                } else {
                    // We don't close SQL storage layers during testing cause the same storage layer can be resued
                    // across tests to save testing time.

                    /*
                     * Instead, during testing, we close it when:
                     * - We are force using an in mem db
                     * - Any config.yaml info changes across tests via setValueInConfig and commentConfigValue functions
                     * - Between tests, if the test had modified the config file (which then got loaded). So we close
                     * the storage layer in the reset function so that in the next test, the default configs are loaded
                     * again.
                     */
                }
            }
            removeDotStartedFileForThisProcess();
            Logging.stopLogging(this);
            // uncomment this when you want to confirm that processes are actually shut.
            // printRunningThreadNames();

        } catch (Throwable ignored) {

        }
    }

    @SuppressWarnings("unused")
    private void printRunningThreadNames() throws InterruptedException {
        assertIsTesting();
        Thread.sleep(8000);
        Thread[] subThreads = new Thread[Thread.activeCount()];
        Thread.enumerate(subThreads);
        for (Thread t : subThreads) {
            if (t != null && !t.getName().equals(this.mainThread.getName()) && !t.getName().equals("main")) {
                System.out.println(t.getName());
            }
        }
    }

    public long getProcessStartTime() {
        return PROCESS_START_TIME;
    }

    public Thread getMainThread() {
        return mainThread;
    }

    public String getProcessId() {
        return processId;
    }

    public void wakeUpMainThreadToShutdown() {
        synchronized (mainThreadWakeUpMonitor) {
            if (!mainThreadWokenUpToShutdown) {
                mainThreadWokenUpToShutdown = true;
                mainThreadWakeUpMonitor.notifyAll();
            }
        }
    }

    private void putMainThreadToSleep() {
        if (Thread.currentThread() != mainThread) {
            return;
        }
        synchronized (mainThreadWakeUpMonitor) {
            while (!mainThreadWokenUpToShutdown) {
                try {
                    mainThreadWakeUpMonitor.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private void handleKillSignalForWhenItHappens() {
        shutdownHook = new Thread(() -> {
            wakeUpMainThreadToShutdown();
            synchronized (shutdownHookLock) {
                while (!programEnded) {
                    try {
                        shutdownHookLock.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    // TODO: figure out some other way to solve this problem. It is used to not show
    // illegal access warning in Tomcat
    private void suppressIllegalAccessWarning() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Unsafe u = (Unsafe) theUnsafe.get(null);

            Class<?> cls = Class.forName("jdk.internal.module.IllegalAccessLogger");
            Field logger = cls.getDeclaredField("logger");
            u.putObjectVolatile(cls, u.staticFieldOffset(logger), null);
        } catch (Exception ignored) {
        }
    }

}
