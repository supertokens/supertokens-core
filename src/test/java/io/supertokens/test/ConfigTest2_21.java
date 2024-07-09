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
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import org.junit.*;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ConfigTest2_21 {

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
    public void testThatDeprecatedConfigStillWorks() throws Exception {
        Utils.setValueInConfig("access_token_signing_key_update_interval", "2");

        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);

        EventAndException startEvent = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(startEvent);

        CoreConfig config = Config.getConfig(process.getProcess());

        long refreshValidity = config.getAccessTokenDynamicSigningKeyUpdateIntervalInMillis();

        Assert.assertEquals(refreshValidity, 2 * 60 * 60 * 1000);

        process.kill();
        EventAndException stopEvent = process.checkOrWaitForEvent(PROCESS_STATE.STOPPED);
        assertNotNull(stopEvent);
    }

    @Test
    public void testThatNewConfigWorks() throws Exception {
        Utils.setValueInConfig("access_token_dynamic_signing_key_update_interval", "2");

        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);

        EventAndException startEvent = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
        assertNotNull(startEvent);

        CoreConfig config = Config.getConfig(process.getProcess());

        long refreshValidity = config.getAccessTokenDynamicSigningKeyUpdateIntervalInMillis();

        Assert.assertEquals(refreshValidity, 2 * 60 * 60 * 1000);

        process.kill();
        EventAndException stopEvent = process.checkOrWaitForEvent(PROCESS_STATE.STOPPED);
        assertNotNull(stopEvent);
    }

    @Test
    public void testCoreConfigTypeValidationInConfigYaml() throws Exception {
        Utils.setValueInConfig("access_token_validity", "abcd");

        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);

        EventAndException startEvent = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertNotNull(startEvent);

        assertEquals(
                "io.supertokens.pluginInterface.exceptions.InvalidConfigException: 'access_token_validity' must be of" +
                        " type long",
                startEvent.exception.getMessage());

        process.kill();
        EventAndException stopEvent = process.checkOrWaitForEvent(PROCESS_STATE.STOPPED);
        assertNotNull(stopEvent);
    }
}
