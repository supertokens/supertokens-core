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
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import org.junit.*;
import org.junit.rules.TestRule;
import org.mockito.Mockito;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotNull;

public class IpAllowDenyRegexTest extends Mockito {

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
    public void defaultIpDenyAllowIsNull() throws InterruptedException {
        String[] args = { "../" };
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        assert (Config.getConfig(process.getProcess()).getIpAllowRegex() == null);
        assert (Config.getConfig(process.getProcess()).getIpDenyRegex() == null);

        assert (process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.ADDING_REMOTE_ADDRESS_FILTER, 1000) == null);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void EmptyStringIpDenyOrAllowIsNull() throws InterruptedException, IOException {
        {
            String[] args = { "../" };
            Utils.setValueInConfig("ip_allow_regex", "\"  \"");
            Utils.setValueInConfig("ip_deny_regex", "\"\"");
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            assert (Config.getConfig(process.getProcess()).getIpAllowRegex() == null);
            assert (Config.getConfig(process.getProcess()).getIpDenyRegex() == null);

            assert (process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.ADDING_REMOTE_ADDRESS_FILTER, 1000) == null);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void EmptyConfigIpDenyOrAllowIsNull() throws InterruptedException, IOException {
        {
            String[] args = { "../" };
            Utils.setValueInConfig("ip_allow_regex", "");
            Utils.setValueInConfig("ip_deny_regex", "");
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            assert (Config.getConfig(process.getProcess()).getIpAllowRegex() == null);
            assert (Config.getConfig(process.getProcess()).getIpDenyRegex() == null);

            assert (process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.ADDING_REMOTE_ADDRESS_FILTER, 1000) == null);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void InvalidRegexErrorForIpAllow() throws InterruptedException, IOException {
        {
            String[] args = { "../" };
            Utils.setValueInConfig("ip_allow_regex", "\"*\"");
            TestingProcess process = TestingProcessManager.start(args);
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertEquals(e.exception.getMessage(), "Provided regular expression is invalid for ip_allow_regex config");

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void InvalidRegexErrorForIpDeby() throws InterruptedException, IOException {
        {
            String[] args = { "../" };
            Utils.setValueInConfig("ip_deny_regex", "\"*\"");
            TestingProcess process = TestingProcessManager.start(args);
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertEquals(e.exception.getMessage(), "Provided regular expression is invalid for ip_deny_regex config");

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void CheckAllowRegexWorks() throws InterruptedException, IOException, Exception {
        {
            String[] args = { "../" };
            Utils.setValueInConfig("ip_allow_regex", "192.123.3.4");
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            try {
                HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/hello", null, 1000, 1000,
                        null);
                throw new Exception("test failed");
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 403);
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void CheckAllowLocalhostWorks() throws InterruptedException, IOException, HttpResponseException {
        {
            String[] args = { "../" };
            Utils.setValueInConfig("ip_allow_regex", "127\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+|::1|0:0:0:0:0:0:0:1");
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            String response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/hello", null,
                    1000, 1000, null);
            Assert.assertEquals("Hello", response);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        Utils.reset();

        {
            String[] args = { "../" };
            Utils.setValueInConfig("ip_allow_regex", "'127\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+|::1|0:0:0:0:0:0:0:1'");
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            String response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/hello", null,
                    1000, 1000, null);
            Assert.assertEquals("Hello", response);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }
}
