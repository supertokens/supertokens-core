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
import io.supertokens.ProcessState;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.config.Config;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import org.junit.*;
import org.junit.rules.TestRule;
import org.mockito.Mockito;

import java.io.*;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;

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
        String[] args = {"../"};
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
            String[] args = {"../"};
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
            String[] args = {"../"};
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
            String[] args = {"../"};
            Utils.setValueInConfig("ip_allow_regex", "\"*\"");
            TestingProcess process = TestingProcessManager.start(args);
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertTrue(e.exception.getMessage()
                    .contains("Provided regular expression is invalid for ip_allow_regex config"));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void InvalidRegexErrorForIpDeny() throws InterruptedException, IOException {
        {
            String[] args = {"../"};
            Utils.setValueInConfig("ip_deny_regex", "\"*\"");
            TestingProcess process = TestingProcessManager.start(args);
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertTrue(e.exception.getMessage()
                    .contains("Provided regular expression is invalid for ip_deny_regex config"));

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void CheckAllowRegexWorks() throws Exception {
        {
            String[] args = {"../"};
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
            String[] args = {"../"};
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
            String[] args = {"../"};
            Utils.setValueInConfig("ip_allow_regex", "127\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+");
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
            String[] args = {"../"};
            Utils.setValueInConfig("ip_allow_regex", "'127\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+'");
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
            String[] args = {"../"};
            Utils.setValueInConfig("ip_allow_regex",
                    "\"127\\\\\\\\.\\\\\\\\d+\\\\\\\\.\\\\\\\\d+\\\\\\\\.\\\\\\\\d+\"");
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            String response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/hello", null,
                    1000, 1000, null);
            Assert.assertEquals("Hello", response);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    @Test
    public void CheckDenyLocalhostWorks() throws Exception {
        {
            String[] args = {"../"};
            Utils.setValueInConfig("ip_deny_regex", "127\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+|::1|0:0:0:0:0:0:0:1");
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

        Utils.reset();

        {
            String[] args = {"../"};
            Utils.setValueInConfig("ip_deny_regex", "127\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+");
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

        Utils.reset();

        {
            String[] args = {"../"};
            Utils.setValueInConfig("ip_deny_regex", "'127\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+'");
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

        Utils.reset();

        {
            String[] args = {"../"};
            Utils.setValueInConfig("ip_deny_regex", "\"127\\\\\\\\.\\\\\\\\d+\\\\\\\\.\\\\\\\\d+\\\\\\\\.\\\\\\\\d+\"");
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
    public void CheckAllowAndDenyLocalhostWorks() throws Exception {
        {
            String[] args = {"../"};
            Utils.setValueInConfig("ip_deny_regex", "127\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+|::1|0:0:0:0:0:0:0:1");
            Utils.setValueInConfig("ip_allow_regex", "127\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+|::1|0:0:0:0:0:0:0:1");
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
    public void CheckNoLoggingForNotAllowedAPIRoutes() throws Exception {
        ByteArrayOutputStream stdOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();

        try {
            String[] args = {"../"};
            Utils.setValueInConfig("ip_deny_regex", "127\\\\.\\\\d+\\\\.\\\\d+\\\\.\\\\d+|::1|0:0:0:0:0:0:0:1");
            Utils.setValueInConfig("info_log_path", "\"null\"");
            Utils.setValueInConfig("error_log_path", "\"null\"");

            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            System.setOut(new PrintStream(stdOutput));
            System.setErr(new PrintStream(errorOutput));

            try {
                HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/hello", null, 1000, 1000,
                        null);
                throw new Exception("test failed");
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 403);
            }

            assertEquals(stdOutput.toByteArray().length, 0);
            assertEquals(errorOutput.toByteArray().length, 0);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        } finally {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err)));
        }
    }

    @Test
    public void CheckThatIPFiltersAreTenantSpecific() throws Exception {
        String[] args = {"../"};

        { // test allow works
            Utils.reset();
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
                return;
            }

            JsonObject coreConfig = new JsonObject();
            coreConfig.addProperty("ip_allow_regex", "192.123.3.4");

            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    new TenantIdentifier(null, null, "t1"),
                    new EmailPasswordConfig(true), new ThirdPartyConfig(true, null), new PasswordlessConfig(true),
                    null, null,
                    coreConfig
            ), false);
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    new TenantIdentifier(null, null, "t2"),
                    new EmailPasswordConfig(true), new ThirdPartyConfig(true, null), new PasswordlessConfig(true),
                    null, null,
                    new JsonObject()
            ), false);

            // this should pass
            HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/hello", null, 1000, 1000,
                    null);
            HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/t2/hello", null, 1000, 1000,
                    null);

            try {
                HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/t1/hello", null, 1000, 1000,
                        null);
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 403);
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        { // test deny works
            Utils.reset();
            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
            FeatureFlagTestContent.getInstance(process.getProcess())
                    .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            JsonObject coreConfig = new JsonObject();
            coreConfig.addProperty("ip_deny_regex", "127\\.\\d+\\.\\d+\\.\\d+|::1|0:0:0:0:0:0:0:1");

            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    new TenantIdentifier(null, null, "t1"),
                    new EmailPasswordConfig(true), new ThirdPartyConfig(true, null), new PasswordlessConfig(true),
                    null, null,
                    coreConfig
            ), false);
            Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(), new TenantConfig(
                    new TenantIdentifier(null, null, "t2"),
                    new EmailPasswordConfig(true), new ThirdPartyConfig(true, null), new PasswordlessConfig(true),
                    null, null,
                    new JsonObject()
            ), false);

            // this should pass
            HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/hello", null, 1000, 1000,
                    null);
            HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/t2/hello", null, 1000, 1000,
                    null);

            try {
                HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/t1/hello", null, 1000, 1000,
                        null);
                fail();
            } catch (HttpResponseException e) {
                assertEquals(e.statusCode, 403);
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }
}
