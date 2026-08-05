/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.config.CoreConfigTestContent;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import org.junit.*;
import org.junit.rules.TestRule;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Tests for the session-related configs introduced for the stateless-verification work
 * (PLAN-002 unit 4). This unit only covers parsing, validation and the testing gates -
 * the behaviour wiring lands with the refresh/verify units.
 */
public class SessionConfigTest {

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    private String getConfigFileLocation(Main main) {
        return new File(CLIOptions.get(main).getConfigFilePath() == null
                ? CLIOptions.get(main).getInstallationPath() + "config.yaml"
                : CLIOptions.get(main).getConfigFilePath()).getAbsolutePath();
    }

    @Test
    public void testDefaultValues() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        CoreConfig config = Config.getConfig(process.getProcess());
        assertEquals(30, config.getRefreshTokenRotationGracePeriodInSeconds());
        assertEquals(30 * 1000L, config.getRefreshTokenRotationGracePeriodInMillis());
        assertEquals(0.05, config.getAccessTokenValidityJitter(), 0);
        assertEquals("TOKEN_THEFT", config.getRecentTokenReuseBehaviour());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCustomValuesAreLoaded() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("refresh_token_rotation_grace_period", "60");
        Utils.setValueInConfig("access_token_validity_jitter", "0.1");
        // enum is case-insensitive on input and normalized to upper case
        Utils.setValueInConfig("recent_token_reuse_behaviour", "unauthorised");

        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        CoreConfig config = Config.getConfig(process.getProcess());
        assertEquals(60, config.getRefreshTokenRotationGracePeriodInSeconds());
        assertEquals(60 * 1000L, config.getRefreshTokenRotationGracePeriodInMillis());
        assertEquals(0.1, config.getAccessTokenValidityJitter(), 0);
        assertEquals("UNAUTHORISED", config.getRecentTokenReuseBehaviour());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testBoundaryValuesAreAccepted() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("refresh_token_rotation_grace_period", "0");
        Utils.setValueInConfig("access_token_validity_jitter", "0.25");

        TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        // the grace-period range check is gated like the other session validity checks
        CoreConfigTestContent.getInstance(process.getProcess())
                .setKeyValue(CoreConfigTestContent.VALIDITY_TESTING, true);
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        CoreConfig config = Config.getConfig(process.getProcess());
        assertEquals(0, config.getRefreshTokenRotationGracePeriodInSeconds());
        assertEquals(0.25, config.getAccessTokenValidityJitter(), 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGracePeriodOutOfRangeThrows() throws Exception {
        String[] args = {"../"};

        // above the maximum
        Utils.setValueInConfig("refresh_token_rotation_grace_period", "301");
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args, false);
        // the check is gated, so enable it explicitly (as the validity checks do)
        CoreConfigTestContent.getInstance(process.getProcess())
                .setKeyValue(CoreConfigTestContent.VALIDITY_TESTING, true);
        process.startProcess();
        ProcessState.EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("'refresh_token_rotation_grace_period' must be between 0 and 300 seconds inclusive. The config"
                + " file can be found here: " + getConfigFileLocation(process.getProcess()),
                e.exception.getCause().getMessage());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        Utils.reset();

        // below the minimum
        Utils.setValueInConfig("refresh_token_rotation_grace_period", "-1");
        process = TestingProcessManager.startIsolatedProcess(args, false);
        CoreConfigTestContent.getInstance(process.getProcess())
                .setKeyValue(CoreConfigTestContent.VALIDITY_TESTING, true);
        process.startProcess();
        e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("'refresh_token_rotation_grace_period' must be between 0 and 300 seconds inclusive. The config"
                + " file can be found here: " + getConfigFileLocation(process.getProcess()),
                e.exception.getCause().getMessage());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGracePeriodOutOfRangeIsAcceptedWhenTestingGateOff() throws Exception {
        String[] args = {"../"};

        // The grace-period range check is gated (like the other session validity checks) so that
        // behaviour units can drive windows past the normal bounds under test. With VALIDITY_TESTING
        // off, an out-of-range value must be accepted (the gate is skipped) rather than rejected.
        Utils.setValueInConfig("refresh_token_rotation_grace_period", "301");
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        CoreConfig config = Config.getConfig(process.getProcess());
        assertEquals(301, config.getRefreshTokenRotationGracePeriodInSeconds());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testJitterOutOfRangeThrows() throws Exception {
        String[] args = {"../"};

        // above the maximum
        Utils.setValueInConfig("access_token_validity_jitter", "0.5");
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        ProcessState.EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("'access_token_validity_jitter' must be between 0 and 0.25 inclusive. The config file can be"
                + " found here: " + getConfigFileLocation(process.getProcess()),
                e.exception.getCause().getMessage());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        Utils.reset();

        // below the minimum
        Utils.setValueInConfig("access_token_validity_jitter", "-0.1");
        process = TestingProcessManager.startIsolatedProcess(args);
        e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("'access_token_validity_jitter' must be between 0 and 0.25 inclusive. The config file can be"
                + " found here: " + getConfigFileLocation(process.getProcess()),
                e.exception.getCause().getMessage());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testInvalidReuseBehaviourThrows() throws Exception {
        String[] args = {"../"};

        Utils.setValueInConfig("recent_token_reuse_behaviour", "SOMETHING_ELSE");
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        ProcessState.EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("recent_token_reuse_behaviour property is not set correctly. It must be one of "
                + "[TOKEN_THEFT, UNAUTHORISED]", e.exception.getCause().getMessage());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
