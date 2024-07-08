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
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ConfigAPITest2_7 {
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
    public void inputErrorConfigAPITest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // null for parameters
        try {
            HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/config", null, 1000, 1000,
                    null);
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'pid' is missing in GET request")
                    && e.statusCode == 400);
        }

        // typo in parameter
        try {
            HashMap<String, String> map = new HashMap<>();
            map.put("pd", ProcessHandle.current().pid() + "");
            HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/config", map, 1000, 1000, null);
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'pid' is missing in GET request")
                    && e.statusCode == 400);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testVersion2InputErrorConfigAPITest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // null for parameters
        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/config", null, 1000,
                    1000, null, SemVer.v2_7.get(), "");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'pid' is missing in GET request")
                    && e.statusCode == 400);
        }

        // typo in parameter
        try {
            HashMap<String, String> map = new HashMap<>();
            map.put("pd", ProcessHandle.current().pid() + "");
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/config", map, 1000,
                    1000, null, SemVer.v2_7.get(), "");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertTrue(e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'pid' is missing in GET request")
                    && e.statusCode == 400);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void testCustomConfigPath() throws Exception {
        String path = new File("../temp/config.yaml").getAbsolutePath();
        String[] args = {"../", "configFile=" + path};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // map to store pid as parameter
        Map<String, String> map = new HashMap<>();
        map.put("pid", ProcessHandle.current().pid() + "");

        // check regular output
        JsonObject response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/config", map,
                1000, 1000, null);

        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(response.get("path").getAsString(), path);
        assertEquals(response.entrySet().size(), 2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testVersion2TestCustomConfigPath() throws Exception {
        String path = new File("../temp/config.yaml").getAbsolutePath();
        String[] args = {"../", "configFile=" + path};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // map to store pid as parameter
        Map<String, String> map = new HashMap<>();
        map.put("pid", ProcessHandle.current().pid() + "");

        // check regular output
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/config", map, 1000, 1000, null, SemVer.v2_7.get(), "");

        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(response.get("path").getAsString(), path);
        assertEquals(response.entrySet().size(), 2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void outputPossibilitiesConfigAPITest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Main main = process.getProcess();
        String path = CLIOptions.get(main).getConfigFilePath() == null
                ? CLIOptions.get(main).getInstallationPath() + "config.yaml"
                : CLIOptions.get(main).getConfigFilePath();
        File f = new File(path);
        path = f.getAbsolutePath();

        // map to store pid as parameter
        HashMap<String, String> map = new HashMap<>();
        map.put("pid", ProcessHandle.current().pid() + "");

        // check regular output
        JsonObject response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/config", map,
                1000, 1000, null);

        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(response.get("path").getAsString(), path);
        assertEquals(response.entrySet().size(), 2);

        // incorrect PID input in parameter
        map = new HashMap<>();
        map.put("pid", "-1");

        response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/config", map, 1000, 1000,
                null);

        assertEquals(response.get("status").getAsString(), "NOT_ALLOWED");
        assertEquals(response.entrySet().size(), 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testVersion2OutputPossibilitiesConfigAPITest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Main main = process.getProcess();
        String path = CLIOptions.get(main).getConfigFilePath() == null
                ? CLIOptions.get(main).getInstallationPath() + "config.yaml"
                : CLIOptions.get(main).getConfigFilePath();
        File f = new File(path);
        path = f.getAbsolutePath();

        // map to store pid as parameter
        HashMap<String, String> map = new HashMap<>();
        map.put("pid", ProcessHandle.current().pid() + "");

        // check regular output
        JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/config", map, 1000, 1000, null, SemVer.v2_7.get(), "");

        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(response.get("path").getAsString(), path);
        assertEquals(response.entrySet().size(), 2);

        // incorrect PID input in parameter
        map = new HashMap<>();
        map.put("pid", "-1");

        response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "", "http://localhost:3567/config", map,
                1000, 1000, null, SemVer.v2_7.get(), "");

        assertEquals(response.get("status").getAsString(), "NOT_ALLOWED");
        assertEquals(response.entrySet().size(), 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
