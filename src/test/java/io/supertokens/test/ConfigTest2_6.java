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
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.config.CoreConfigTestContent;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.File;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ConfigTest2_6 {

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
    public void testThatDefaultConfigLoadsCorrectly() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);

        EventAndException startEvent = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(startEvent);

        checkConfigValues(Config.getConfig(process.getProcess()), process);

        process.kill();
        EventAndException stopEvent = process.checkOrWaitForEvent(PROCESS_STATE.STOPPED);
        assertNotNull(stopEvent);
    }

    @Test
    public void testThatCustomValuesInConfigAreLoaded() throws Exception {
        Utils.setValueInConfig("refresh_token_validity", "1");

        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);

        EventAndException startEvent = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(startEvent);

        CoreConfig config = Config.getConfig(process.getProcess());

        long refreshValidity = config.getRefreshTokenValidityInMillis();

        Assert.assertEquals(refreshValidity, 60 * 1000);

        process.kill();
        EventAndException stopEvent = process.checkOrWaitForEvent(PROCESS_STATE.STOPPED);
        assertNotNull(stopEvent);
    }

    @Test
    public void testThatInvalidConfigThrowRightError() throws Exception {
        String[] args = {"../"};

        // out of range core_config_version
        Utils.setValueInConfig("core_config_version", "-1");

        TestingProcess process = TestingProcessManager.start(args);

        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals(e.exception.getCause().getMessage(),
                "'core_config_version' is not set in the config.yaml file. Please redownload and install SuperTokens");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        Utils.reset();

        // out of range value for access_token_validity
        Utils.setValueInConfig("access_token_validity", "-1");
        process = TestingProcessManager.start(args);

        e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals(e.exception.getCause().getMessage(),
                "'access_token_validity' must be between 1 and 86400000 seconds inclusive. The config file can be "
                        + "found here: " + getConfigFileLocation(process.getProcess()));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        Utils.reset();

        Utils.setValueInConfig("max_server_pool_size", "-1");
        process = TestingProcessManager.start(args);

        e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals(e.exception.getCause().getMessage(),
                "'max_server_pool_size' must be >= 1. The config file can be found here: "
                        + getConfigFileLocation(process.getProcess()));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

    @Test
    public void testInvalidTotpConfigThrowsExpectedError() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("totp_max_attempts", "0");

        TestingProcess process = TestingProcessManager.start(args);

        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals(e.exception.getCause().getMessage(),
                "'totp_max_attempts' must be > 0");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        Utils.reset();

        Utils.setValueInConfig("totp_rate_limit_cooldown_sec", "0");
        process = TestingProcessManager.start(args);

        e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals(e.exception.getCause().getMessage(),
                "'totp_rate_limit_cooldown_sec' must be > 0");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    private String getConfigFileLocation(Main main) {
        return new File(CLIOptions.get(main).getConfigFilePath() == null
                ? CLIOptions.get(main).getInstallationPath() + "config.yaml"
                : CLIOptions.get(main).getConfigFilePath()).getAbsolutePath();
    }

    @Test
    public void testThatNonTestingConfigValuesThrowErrors() throws Exception {
        String[] args = {"../"};
        Utils.setValueInConfig("refresh_token_validity", "-1");
        TestingProcess process = TestingProcessManager.start(args, false);
        CoreConfigTestContent.getInstance(process.getProcess()).setKeyValue(CoreConfigTestContent.VALIDITY_TESTING,
                true);
        process.startProcess();
        ProcessState.EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);

        assertEquals(e.exception.getCause().getMessage(),
                "'refresh_token_validity' must be strictly greater than 'access_token_validity'. The config file"
                        + " can be found here: " + getConfigFileLocation(process.getProcess()));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

    @Test
    public void testThatMissingConfigFileThrowsError() throws Exception {
        String[] args = {"../"};

        ProcessBuilder pb = new ProcessBuilder("rm", "config.yaml");
        pb.directory(new File(args[0]));
        Process process1 = pb.start();
        process1.waitFor();

        TestingProcess process = TestingProcessManager.start(args);
        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals(e.exception.getMessage(),
                "../config.yaml (No such file or directory)");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

    @Test
    public void testCustomLocationForConfigLoadsCorrectly() throws Exception {
        // relative file path
        String[] args = {"../", "configFile=../temp/config.yaml"};

        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals(e.exception.getMessage(), "configPath option must be an absolute path only");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        // absolute file path
        File f = new File("../temp/config.yaml");
        args = new String[]{"../", "configFile=" + f.getAbsolutePath()};

        process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        checkConfigValues(Config.getConfig(process.getProcess()), process, true);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

    private static void checkConfigValues(CoreConfig config, TestingProcess process) {
        checkConfigValues(config, process, false);
    }

    private static void checkConfigValues(CoreConfig config, TestingProcess process, boolean telemetryDisabled) {

        assertEquals("Config version did not match default", config.getConfigVersion(), 0);
        assertEquals("Config access token validity did not match default", config.getAccessTokenValidityInMillis(),
                3600 * 1000);
        assertFalse("Config access token blacklisting did not match default", config.getAccessTokenBlacklisting());
        assertEquals("Config refresh token validity did not match default", config.getRefreshTokenValidityInMillis(),
                60 * 2400 * 60 * (long) 1000);
        assertEquals(5, config.getTotpMaxAttempts()); // 5
        assertEquals(900, config.getTotpRateLimitCooldownTimeSec()); // 15 minutes

        assertEquals("Config info log path did not match default", config.getInfoLogPath(process.getProcess()),
                CLIOptions.get(process.getProcess()).getInstallationPath() + "logs/info.log");
        assertEquals("Config error log path did not match default", config.getErrorLogPath(process.getProcess()),
                CLIOptions.get(process.getProcess()).getInstallationPath() + "logs/error.log");
        assertEquals("Config access signing key interval did not match default",
                config.getAccessTokenDynamicSigningKeyUpdateIntervalInMillis(), 7 * 24 * 60 * 60 * 1000);

        assertEquals(config.getHost(process.getProcess()), "localhost");
        assertEquals(config.getPort(process.getProcess()), 3567);
        assertNull(config.getAPIKeys());
        assertEquals(10, config.getMaxThreadPoolSize());
        assertFalse(config.getHttpsEnabled());
        assert (config.isTelemetryDisabled() == telemetryDisabled);

    }

}
