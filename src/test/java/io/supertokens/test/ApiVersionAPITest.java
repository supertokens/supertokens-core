/*
 *    Copyright (c) 2023, SuperTokens, Inc. All rights reserved.
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.WebserverAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Set;

import static org.junit.Assert.*;

public class ApiVersionAPITest {

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

    // * - add a test to read the value of coreDriverInterfaceSupported.json and make sure the versions listed there are
    // * being returned by this API.
    @Test
    public void testThatCoreDriverInterfaceSupportedVersionsAreBeingReturnedByTheAPI() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        StringBuilder fileContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new FileReader("../supertokens-core/coreDriverInterfaceSupported.json"))) {
            String currentLine = reader.readLine();
            while (currentLine != null) {
                fileContent.append(currentLine).append(System.lineSeparator());
                currentLine = reader.readLine();
            }
        }
        JsonObject cdiSupported = new JsonParser().parse(fileContent.toString()).getAsJsonObject();
        JsonArray cdiVersions = cdiSupported.get("versions").getAsJsonArray();

        JsonObject apiVersionResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/apiversion", null, 1000, 1000, null, null, "");
        JsonArray apiVersions = apiVersionResponse.get("versions").getAsJsonArray();

        for (int i = 0; i < apiVersions.size(); i++) {
            assertTrue(cdiVersions.contains(apiVersions.get(i)));
        }
        assertEquals(cdiVersions.size(), apiVersions.size());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // - no version needed for this API.
    @Test
    public void testThatNoVersionIsNeededForThisAPI() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // without setting cdi-version header
        JsonObject apiVersionResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/apiversion", null, 1000, 1000, null, null, "");
        assertNotNull(apiVersionResponse.getAsJsonArray("versions"));
        assertTrue(apiVersionResponse.getAsJsonArray("versions").size() >= 1);

        // with setting cdi-version header
        apiVersionResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/apiversion", null, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(), "");
        assertNotNull(apiVersionResponse.getAsJsonArray("versions"));
        assertTrue(apiVersionResponse.getAsJsonArray("versions").size() >= 1);

        process.kill();

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // - test that all returned versions are correct based on WebserverAPI's supportedVersions set
    @Test
    public void testThatApiVersionsAreBasedOnWebserverAPIsSupportedVersions() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject apiVersionResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/apiversion", null, 1000, 1000, null, null, "");

        Set<SemVer> supportedVersions = WebserverAPI.supportedVersions;
        JsonArray apiSupportedVersions = apiVersionResponse.getAsJsonArray("versions");
        for (int i = 0; i < supportedVersions.size(); i++) {
            assertTrue(supportedVersions.contains(new SemVer(apiSupportedVersions.get(i).getAsString())));
        }
        assertEquals(supportedVersions.size(), apiSupportedVersions.size());
        process.kill();

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    // - check that all returned versions have X.Y format
    @Test
    public void testThatAllReturnedVersionsHaveXYFormat() throws Exception {
        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        JsonObject apiVersionResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/apiversion", null, 1000, 1000, null, null, "");

        for (JsonElement i : apiVersionResponse.get("versions").getAsJsonArray()) {
            assertTrue(i.getAsString().matches("\\d+\\.\\d+"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
