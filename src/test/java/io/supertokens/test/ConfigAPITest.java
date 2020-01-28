/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.test;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;


public class ConfigAPITest {
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
        String[] args = {"../", "DEV"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        //null for parameters
        try {
            HttpRequest
                    .sendGETRequest(process.getProcess(), "", "http://localhost:3567/config", null, 1000, 1000, null);
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'pid' is missing in GET request") &&
                    e.statusCode == 400);
        }

        //typo in parameter
        try {
            HashMap<String, String> map = new HashMap<>();
            map.put("pd", ProcessHandle.current().pid() + "");
            HttpRequest
                    .sendGETRequest(process.getProcess(), "", "http://localhost:3567/config", map, 1000, 1000, null);
            fail();
        } catch (HttpResponseException e) {
            assertTrue(e.getMessage()
                    .equals("Http error. Status Code: 400. Message: Field name 'pid' is missing in GET request") &&
                    e.statusCode == 400);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }


    @Test
    public void testCustomConfigPath() throws Exception {
        String path = new File("../temp/config.yaml").getAbsolutePath();
        String[] args = {"../", "DEV", "configFile=" + path};


        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // map to store pid as parameter
        Map<String, String> map = new HashMap<>();
        map.put("pid", ProcessHandle.current().pid() + "");

        //check regular output
        JsonObject response = HttpRequest
                .sendGETRequest(process.getProcess(), "", "http://localhost:3567/config", map, 1000, 1000, null);

        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(response.get("path").getAsString(), path);
        assertEquals(response.entrySet().size(), 2);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void outputPossibilitiesConfigAPITest() throws Exception {
        String[] args = {"../", "DEV"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Main main = process.getProcess();
        String path = CLIOptions.get(main).getConfigFilePath() == null
                ? CLIOptions.get(main).getInstallationPath() + "config.yaml"
                : CLIOptions.get(main).getConfigFilePath();
        File f = new File(path);
        path = f.getAbsolutePath();

        //map to store pid as parameter
        HashMap<String, String> map = new HashMap<>();
        map.put("pid", ProcessHandle.current().pid() + "");

        //check regular output
        JsonObject response = HttpRequest
                .sendGETRequest(process.getProcess(), "", "http://localhost:3567/config", map, 1000, 1000, null);

        assertEquals(response.get("status").getAsString(), "OK");
        assertEquals(response.get("path").getAsString(), path);
        assertEquals(response.entrySet().size(), 2);

        //incorrect PID input in parameter
        map = new HashMap<>();
        map.put("pid", "-1");

        response = HttpRequest
                .sendGETRequest(process.getProcess(), "", "http://localhost:3567/config", map, 1000, 1000, null);

        assertEquals(response.get("status").getAsString(), "NOT ALLOWED");
        assertEquals(response.entrySet().size(), 1);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }


}
