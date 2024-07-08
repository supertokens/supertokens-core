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

import io.supertokens.ProcessState.EventAndException;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.config.Config;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import static org.junit.Assert.*;

public class CLIOptionsTest {

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    // different CPI arguments failure and pass cases
    @Test
    public void cli0ArgsTest() throws TestingProcessManagerException, InterruptedException {
        String[] args = {};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertTrue(e != null
                && e.exception.getMessage().equals("Please provide installation path location for SuperTokens"));
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        process.kill();
    }

    @Test
    public void cli1ArgsTest() throws TestingProcessManagerException, InterruptedException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(e);
        process.kill();
    }

    @Test
    public void cli2ArgsTest() throws Exception {
        // testing that when badInput is given to second cli argument, default values for host and port are used
        String[] args = {"../", "random"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        assertEquals(Config.getConfig(process.getProcess()).getHost(process.getProcess()), "localhost");
        assertEquals(Config.getConfig(process.getProcess()).getPort(process.getProcess()), 3567);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        // custom host and port
        args = new String[]{"../", "host=127.0.0.1", "port=8081"};

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        assertEquals(Config.getConfig(process.getProcess()).getHost(process.getProcess()), "127.0.0.1");
        assertEquals(Config.getConfig(process.getProcess()).getPort(process.getProcess()), 8081);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

    @Test
    public void testMultipleInstancesAtTheSameTime() throws Exception {
        String[] args = {"../"};

        try {
            // Create 2 custom config files
            ProcessBuilder pb = new ProcessBuilder("cp", "config.yaml", "temp/new1Config.yaml");
            pb.directory(new File(args[0]));
            Process p1 = pb.start();
            p1.waitFor();

            pb = new ProcessBuilder("cp", "config.yaml", "temp/new2Config.yaml");
            pb.directory(new File(args[0]));
            p1 = pb.start();
            p1.waitFor();

            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            args = new String[]{"../", "port=8081",
                    "configFile=" + new File("../temp/new1Config.yaml").getAbsolutePath()};

            TestingProcess process1 = TestingProcessManager.start(args);
            assertNotNull(process1.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            args = new String[]{"../", "port=8082",
                    "configFile=" + new File("../temp/new2Config.yaml").getAbsolutePath()};

            TestingProcess process2 = TestingProcessManager.start(args);
            assertNotNull(process2.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            assertEquals(Config.getConfig(process.getProcess()).getPort(process.getProcess()), 3567);
            assertEquals(Config.getConfig(process1.getProcess()).getPort(process1.getProcess()), 8081);
            assertEquals(Config.getConfig(process2.getProcess()).getPort(process2.getProcess()), 8082);

            assertEquals(CLIOptions.get(process1.getProcess()).getConfigFilePath(),
                    new File("../temp/new1Config.yaml").getAbsolutePath());
            assertEquals(CLIOptions.get(process2.getProcess()).getConfigFilePath(),
                    new File("../temp/new2Config.yaml").getAbsolutePath());

            // check infoLogPath is the same for all 3 processes
            assertEquals(Config.getConfig(process.getProcess()).getInfoLogPath(process.getProcess()),
                    Config.getConfig(process1.getProcess()).getInfoLogPath(process1.getProcess()));
            assertEquals(Config.getConfig(process1.getProcess()).getInfoLogPath(process1.getProcess()),
                    Config.getConfig(process2.getProcess()).getInfoLogPath(process2.getProcess()));

            // check errorLogPath is the same for all 3 processes
            assertEquals(Config.getConfig(process.getProcess()).getErrorLogPath(process.getProcess()),
                    Config.getConfig(process1.getProcess()).getErrorLogPath(process1.getProcess()));
            assertEquals(Config.getConfig(process1.getProcess()).getErrorLogPath(process1.getProcess()),
                    Config.getConfig(process2.getProcess()).getErrorLogPath(process2.getProcess()));

            // clear log files
            {
                FileWriter f = new FileWriter(
                        Config.getConfig(process.getProcess()).getInfoLogPath(process.getProcess()));
                f.flush();
                f.close();
            }
            {
                FileWriter f = new FileWriter(
                        Config.getConfig(process.getProcess()).getErrorLogPath(process.getProcess()));
                f.flush();
                f.close();
            }

            Logging.debug(process.getProcess(), TenantIdentifier.BASE_TENANT, "debugunique1");
            Logging.debug(process1.getProcess(), TenantIdentifier.BASE_TENANT, "debugunique2");
            Logging.debug(process2.getProcess(), TenantIdentifier.BASE_TENANT, "debugunique3");

            Logging.info(process.getProcess(), TenantIdentifier.BASE_TENANT, "infounique1", false);
            Logging.info(process1.getProcess(), TenantIdentifier.BASE_TENANT, "infounique2", false);
            Logging.info(process2.getProcess(), TenantIdentifier.BASE_TENANT, "infounique3", false);

            Logging.error(process.getProcess(), TenantIdentifier.BASE_TENANT, "errorunique1", false);
            Logging.error(process1.getProcess(), TenantIdentifier.BASE_TENANT, "errorunique2", false);
            Logging.error(process2.getProcess(), TenantIdentifier.BASE_TENANT, "errorunique3", false);

            boolean processInfoLog = false;
            boolean process1InfoLog = false;
            boolean process2InfoLog = false;

            boolean processErrorLog = false;
            boolean process1ErrorLog = false;
            boolean process2ErrorLog = false;

            int infoTest1Count = 0;
            int infoTest2Count = 0;
            int infoTest3Count = 0;

            int errrorTest1Count = 0;
            int errrorTest2Count = 0;
            int errrorTest3Count = 0;

            try (BufferedReader reader = new BufferedReader(
                    new FileReader(Config.getConfig(process.getProcess()).getInfoLogPath(process.getProcess())))) {
                String currentReadingLine = reader.readLine();
                while (currentReadingLine != null) {
                    if (currentReadingLine.contains(process1.getProcess().getProcessId())) {
                        process1InfoLog = true;
                    }

                    if (currentReadingLine.contains(process2.getProcess().getProcessId())) {
                        process2InfoLog = true;
                    }
                    if (currentReadingLine.contains(process.getProcess().getProcessId())) {
                        processInfoLog = true;
                    }

                    if (currentReadingLine.contains("infounique1")) {
                        infoTest1Count++;
                    }
                    if (currentReadingLine.contains("infounique2")) {
                        infoTest2Count++;
                    }
                    if (currentReadingLine.contains("infounique3")) {
                        infoTest3Count++;
                    }

                    currentReadingLine = reader.readLine();
                }
            }

            try (BufferedReader reader = new BufferedReader(
                    new FileReader(Config.getConfig(process.getProcess()).getErrorLogPath(process.getProcess())))) {
                String currentReadingLine = reader.readLine();
                while (currentReadingLine != null) {
                    if (currentReadingLine.contains(process1.getProcess().getProcessId())) {
                        process1ErrorLog = true;
                    }
                    if (currentReadingLine.contains(process2.getProcess().getProcessId())) {
                        process2ErrorLog = true;
                    }
                    if (currentReadingLine.contains(process.getProcess().getProcessId())) {
                        processErrorLog = true;
                    }

                    if (currentReadingLine.contains("errorunique1")) {
                        errrorTest1Count++;
                    }
                    if (currentReadingLine.contains("errorunique2")) {
                        errrorTest2Count++;
                    }
                    if (currentReadingLine.contains("errorunique3")) {
                        errrorTest3Count++;
                    }

                    currentReadingLine = reader.readLine();
                }
            }

            assertTrue("processes do not log error to the same location",
                    process1ErrorLog && process2ErrorLog && processErrorLog);
            assertTrue("processes do not log info to the same location",
                    processInfoLog && process2InfoLog && process1InfoLog);

            assertEquals(3, infoTest1Count);
            assertEquals(3, infoTest2Count);
            assertEquals(3, infoTest3Count);
            assertEquals(3, errrorTest1Count);
            assertEquals(3, errrorTest2Count);
            assertEquals(3, errrorTest3Count);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

            process1.kill();
            assertNotNull(process1.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

            process2.kill();
            assertNotNull(process2.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        } finally {

            ProcessBuilder pb = new ProcessBuilder("rm", "temp/new1Config.yaml");
            pb.directory(new File(args[0]));
            Process p1 = pb.start();
            p1.waitFor();

            pb = new ProcessBuilder("rm", "temp/new2Config.yaml");
            pb.directory(new File(args[0]));
            p1 = pb.start();
            p1.waitFor();
        }

    }

}
