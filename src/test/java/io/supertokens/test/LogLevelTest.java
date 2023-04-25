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

import io.supertokens.ProcessState;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.config.Config;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.LOG_LEVEL;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.Set;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;

public class LogLevelTest {
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

    @Test
    public void testLogLevels() throws Exception {
        {
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            Set<LOG_LEVEL> logLevels = Config.getConfig(process.getProcess()).getLogLevels(process.getProcess());
            // default log level should be info
            assertEquals(3, logLevels.size());
            assertTrue(logLevels.contains(LOG_LEVEL.INFO));
            assertTrue(logLevels.contains(LOG_LEVEL.WARN));
            assertTrue(logLevels.contains(LOG_LEVEL.ERROR));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        Utils.reset();

        {
            Utils.setValueInConfig("log_level", "NONE");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            Set<LOG_LEVEL> logLevels = Config.getConfig(process.getProcess()).getLogLevels(process.getProcess());
            // default log level should be info
            assertEquals(0, logLevels.size());

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("log_level", "ERROR");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            Set<LOG_LEVEL> logLevels = Config.getConfig(process.getProcess()).getLogLevels(process.getProcess());
            // default log level should be info
            assertEquals(1, logLevels.size());
            assertTrue(logLevels.contains(LOG_LEVEL.ERROR));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("log_level", "WARN");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            Set<LOG_LEVEL> logLevels = Config.getConfig(process.getProcess()).getLogLevels(process.getProcess());
            // default log level should be info
            assertEquals(2, logLevels.size());
            assertTrue(logLevels.contains(LOG_LEVEL.ERROR));
            assertTrue(logLevels.contains(LOG_LEVEL.WARN));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("log_level", "INFO");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            Set<LOG_LEVEL> logLevels = Config.getConfig(process.getProcess()).getLogLevels(process.getProcess());
            // default log level should be info
            assertEquals(3, logLevels.size());
            assertTrue(logLevels.contains(LOG_LEVEL.ERROR));
            assertTrue(logLevels.contains(LOG_LEVEL.WARN));
            assertTrue(logLevels.contains(LOG_LEVEL.INFO));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("log_level", "DEBUG");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            Set<LOG_LEVEL> logLevels = Config.getConfig(process.getProcess()).getLogLevels(process.getProcess());
            // default log level should be info
            assertEquals(4, logLevels.size());
            assertTrue(logLevels.contains(LOG_LEVEL.ERROR));
            assertTrue(logLevels.contains(LOG_LEVEL.WARN));
            assertTrue(logLevels.contains(LOG_LEVEL.INFO));
            assertTrue(logLevels.contains(LOG_LEVEL.DEBUG));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testLogLevelNoneOutput() throws Exception {
        {
            Utils.setValueInConfig("log_level", "NONE");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            File infoLog = new File(Config.getConfig(process.getProcess()).getInfoLogPath(process.getProcess()));
            File errorLog = new File(Config.getConfig(process.getProcess()).getErrorLogPath(process.getProcess()));
            boolean didOutput = false;
            Logging.error(process.getProcess(), TenantIdentifier.BASE_TENANT, "some message", false);
            Logging.warn(process.getProcess(), TenantIdentifier.BASE_TENANT, "some message");
            Logging.info(process.getProcess(), TenantIdentifier.BASE_TENANT, "some message", true);
            Logging.debug(process.getProcess(), TenantIdentifier.BASE_TENANT, "some message");

            try (Scanner scanner = new Scanner(infoLog, StandardCharsets.UTF_8)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains(process.getProcess().getProcessId())) {
                        didOutput = true;
                        break;
                    }
                }
            }

            try (Scanner errorScanner = new Scanner(errorLog, StandardCharsets.UTF_8)) {
                while (errorScanner.hasNextLine()) {
                    String line = errorScanner.nextLine();
                    if (line.contains(process.getProcess().getProcessId())) {
                        didOutput = true;
                        break;
                    }
                }
            }

            assertFalse(didOutput);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testLogLevelErrorOutput() throws Exception {
        {
            Utils.setValueInConfig("log_level", "ERROR");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            File infoLog = new File(Config.getConfig(process.getProcess()).getInfoLogPath(process.getProcess()));
            File errorLog = new File(Config.getConfig(process.getProcess()).getErrorLogPath(process.getProcess()));
            boolean errorOutput = false;
            boolean warnOutput = false;
            boolean infoOutput = false;
            boolean debugOutput = false;

            Logging.error(process.getProcess(), TenantIdentifier.BASE_TENANT, "some error", false);
            Logging.warn(process.getProcess(), TenantIdentifier.BASE_TENANT, "some warn");
            Logging.info(process.getProcess(), TenantIdentifier.BASE_TENANT, "some info", true);
            Logging.debug(process.getProcess(), TenantIdentifier.BASE_TENANT, "some debug");

            try (Scanner scanner = new Scanner(infoLog, StandardCharsets.UTF_8)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains(process.getProcess().getProcessId())) {
                        if (line.contains("some info")) {
                            infoOutput = true;
                        } else if (line.contains("some debug")) {
                            debugOutput = true;
                        }
                    }
                }
            }

            try (Scanner errorScanner = new Scanner(errorLog, StandardCharsets.UTF_8)) {
                while (errorScanner.hasNextLine()) {
                    String line = errorScanner.nextLine();
                    if (line.contains(process.getProcess().getProcessId())) {
                        if (line.contains("some error")) {
                            errorOutput = true;
                        } else if (line.contains("some warn")) {
                            warnOutput = true;
                        }
                    }
                }
            }

            assertTrue(errorOutput && !warnOutput && !infoOutput && !debugOutput);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testLogLevelWarnOutput() throws Exception {
        {
            Utils.setValueInConfig("log_level", "WARN");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            File infoLog = new File(Config.getConfig(process.getProcess()).getInfoLogPath(process.getProcess()));
            File errorLog = new File(Config.getConfig(process.getProcess()).getErrorLogPath(process.getProcess()));
            boolean errorOutput = false;
            boolean warnOutput = false;
            boolean infoOutput = false;
            boolean debugOutput = false;

            Logging.error(process.getProcess(), TenantIdentifier.BASE_TENANT, "some error", false);
            Logging.warn(process.getProcess(), TenantIdentifier.BASE_TENANT, "some warn");
            Logging.info(process.getProcess(), TenantIdentifier.BASE_TENANT, "some info", true);
            Logging.debug(process.getProcess(), TenantIdentifier.BASE_TENANT, "some debug");

            try (Scanner scanner = new Scanner(infoLog, StandardCharsets.UTF_8)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains(process.getProcess().getProcessId())) {
                        if (line.contains("some info")) {
                            infoOutput = true;
                        } else if (line.contains("some debug")) {
                            debugOutput = true;
                        }
                    }
                }
            }

            try (Scanner errorScanner = new Scanner(errorLog, StandardCharsets.UTF_8)) {
                while (errorScanner.hasNextLine()) {
                    String line = errorScanner.nextLine();
                    if (line.contains(process.getProcess().getProcessId())) {
                        if (line.contains("some error")) {
                            errorOutput = true;
                        } else if (line.contains("some warn")) {
                            warnOutput = true;
                        }
                    }
                }
            }

            assertTrue(errorOutput && warnOutput && !infoOutput && !debugOutput);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testLogLevelInfoOutput() throws Exception {
        {
            Utils.setValueInConfig("log_level", "INFO");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            File infoLog = new File(Config.getConfig(process.getProcess()).getInfoLogPath(process.getProcess()));
            File errorLog = new File(Config.getConfig(process.getProcess()).getErrorLogPath(process.getProcess()));
            boolean errorOutput = false;
            boolean warnOutput = false;
            boolean infoOutput = false;
            boolean debugOutput = false;

            Logging.error(process.getProcess(), TenantIdentifier.BASE_TENANT, "some error", false);
            Logging.warn(process.getProcess(), TenantIdentifier.BASE_TENANT, "some warn");
            Logging.info(process.getProcess(), TenantIdentifier.BASE_TENANT, "some info", true);
            Logging.debug(process.getProcess(), TenantIdentifier.BASE_TENANT, "some debug");

            try (Scanner scanner = new Scanner(infoLog, StandardCharsets.UTF_8)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains(process.getProcess().getProcessId())) {
                        if (line.contains("some info")) {
                            infoOutput = true;
                        } else if (line.contains("some debug")) {
                            debugOutput = true;
                        }
                    }
                }
            }

            try (Scanner errorScanner = new Scanner(errorLog, StandardCharsets.UTF_8)) {
                while (errorScanner.hasNextLine()) {
                    String line = errorScanner.nextLine();
                    if (line.contains(process.getProcess().getProcessId())) {
                        if (line.contains("some error")) {
                            errorOutput = true;
                        } else if (line.contains("some warn")) {
                            warnOutput = true;
                        }
                    }
                }
            }

            assertTrue(errorOutput && warnOutput && infoOutput && !debugOutput);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testLogLevelDebugOutput() throws Exception {
        {
            Utils.setValueInConfig("log_level", "DEBUG");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            File infoLog = new File(Config.getConfig(process.getProcess()).getInfoLogPath(process.getProcess()));
            File errorLog = new File(Config.getConfig(process.getProcess()).getErrorLogPath(process.getProcess()));
            boolean errorOutput = false;
            boolean warnOutput = false;
            boolean infoOutput = false;
            boolean debugOutput = false;

            Logging.error(process.getProcess(), TenantIdentifier.BASE_TENANT, "some error", false);
            Logging.warn(process.getProcess(), TenantIdentifier.BASE_TENANT, "some warn");
            Logging.info(process.getProcess(), TenantIdentifier.BASE_TENANT, "some info", true);
            Logging.debug(process.getProcess(), TenantIdentifier.BASE_TENANT, "some debug");

            try (Scanner scanner = new Scanner(infoLog, StandardCharsets.UTF_8)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains(process.getProcess().getProcessId())) {
                        if (line.contains("some info")) {
                            infoOutput = true;
                        } else if (line.contains("some debug")) {
                            debugOutput = true;
                        }
                    }
                }
            }

            try (Scanner errorScanner = new Scanner(errorLog, StandardCharsets.UTF_8)) {
                while (errorScanner.hasNextLine()) {
                    String line = errorScanner.nextLine();
                    if (line.contains(process.getProcess().getProcessId())) {
                        if (line.contains("some error")) {
                            errorOutput = true;
                        } else if (line.contains("some warn")) {
                            warnOutput = true;
                        }
                    }
                }
            }

            assertTrue(errorOutput && warnOutput && infoOutput && debugOutput);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testLogLevelNoneOutputWithConfigErrorShouldLog() throws Exception {
        try {
            ByteArrayOutputStream stdOutput = new ByteArrayOutputStream();
            ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
            System.setOut(new PrintStream(stdOutput));
            System.setErr(new PrintStream(errorOutput));

            Utils.setValueInConfig("log_level", "NONE");
            Utils.setValueInConfig("access_token_validity", "-1");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE));

            assertFalse(fileContainsString(stdOutput, "access_token_validity"));
            assertTrue(fileContainsString(errorOutput, "access_token_validity"));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        } finally {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err)));
        }
    }

    @Test
    public void testLogLevelsUpperLowerCase() throws Exception {
        {
            Utils.setValueInConfig("log_level", "NonE");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            Set<LOG_LEVEL> logLevels = Config.getConfig(process.getProcess()).getLogLevels(process.getProcess());
            // default log level should be info
            assertEquals(0, logLevels.size());

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("log_level", "error");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            Set<LOG_LEVEL> logLevels = Config.getConfig(process.getProcess()).getLogLevels(process.getProcess());
            // default log level should be info
            assertEquals(1, logLevels.size());
            assertTrue(logLevels.contains(LOG_LEVEL.ERROR));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("log_level", "wArN");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            Set<LOG_LEVEL> logLevels = Config.getConfig(process.getProcess()).getLogLevels(process.getProcess());
            // default log level should be info
            assertEquals(2, logLevels.size());
            assertTrue(logLevels.contains(LOG_LEVEL.ERROR));
            assertTrue(logLevels.contains(LOG_LEVEL.WARN));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("log_level", "info");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            Set<LOG_LEVEL> logLevels = Config.getConfig(process.getProcess()).getLogLevels(process.getProcess());
            // default log level should be info
            assertEquals(3, logLevels.size());
            assertTrue(logLevels.contains(LOG_LEVEL.ERROR));
            assertTrue(logLevels.contains(LOG_LEVEL.WARN));
            assertTrue(logLevels.contains(LOG_LEVEL.INFO));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("log_level", "debug");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);

            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            Set<LOG_LEVEL> logLevels = Config.getConfig(process.getProcess()).getLogLevels(process.getProcess());
            // default log level should be info
            assertEquals(4, logLevels.size());
            assertTrue(logLevels.contains(LOG_LEVEL.ERROR));
            assertTrue(logLevels.contains(LOG_LEVEL.WARN));
            assertTrue(logLevels.contains(LOG_LEVEL.INFO));
            assertTrue(logLevels.contains(LOG_LEVEL.DEBUG));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void testIncorrectLogLevel() throws Exception {
        Utils.setValueInConfig("log_level", "random");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);

        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals(e.exception.getCause().getMessage(),
                "'log_level' config must be one of \"NONE\",\"DEBUG\", \"INFO\", \"WARN\" or \"ERROR\".");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    private static boolean fileContainsString(ByteArrayOutputStream log, String value) throws IOException {
        boolean containsString = false;
        try (BufferedReader reader = new BufferedReader(new StringReader(log.toString()))) {
            String currentReadingLine = reader.readLine();
            while (currentReadingLine != null) {
                if (currentReadingLine.contains(value)) {
                    containsString = true;
                    break;
                }
                currentReadingLine = reader.readLine();
            }
        }
        return containsString;
    }

}
