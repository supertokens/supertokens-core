/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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
import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.Webserver;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.HashMap;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;

public class CDIVersionTest {
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
    public void testThatAPIUsesLatestVersionByDefault() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/version-test";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                SemVer version = getVersionFromRequest(req);

                super.sendTextResponse(200, version.toString(), resp);
            }
        });

        String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/version-test", new HashMap<>(), 1000, 1000, null,
                null, "");
        assertEquals(WebserverAPI.getLatestCDIVersion().toString(), response);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatAPIUsesVersionSpecifiedInTheRequest() throws Exception {
        String[] args = {"../"};
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/version-test";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                SemVer version = getVersionFromRequest(req);

                super.sendTextResponse(200, version.toString(), resp);
            }
        });

        String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/version-test", new HashMap<>(), 1000, 1000, null,
                "2.21", "");
        assertEquals("2.21", response);

        response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/version-test", new HashMap<>(), 1000, 1000, null,
                "2.10", "");
        assertEquals("2.10", response);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatAPIUsesVersionSpecifiedInConfigAsDefault() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        Utils.setValueInConfig("supertokens_default_cdi_version", "2.21");
        process.startProcess();

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/version-test";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                SemVer version = getVersionFromRequest(req);

                super.sendTextResponse(200, version.toString(), resp);
            }
        });

        String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/version-test", new HashMap<>(), 1000, 1000, null,
                null, "");
        assertEquals("2.21", response);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatAPIUsesVersionSpecifiedInRequestWhenTheConfigIsPresent() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        Utils.setValueInConfig("supertokens_default_cdi_version", "2.21");
        process.startProcess();

        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/version-test";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                SemVer version = getVersionFromRequest(req);

                super.sendTextResponse(200, version.toString(), resp);
            }
        });

        String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/version-test", new HashMap<>(), 1000, 1000, null,
                "2.10", "");
        assertEquals("2.10", response);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testJWKSEndpointWorksInAllCases() throws Exception {
        {
            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            {
                // check regular output
                JsonObject response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/.well-known/jwks.json", null,
                        1000, 1000, null);

                assertEquals(response.entrySet().size(), 1);

                assertTrue(response.has("keys"));
                JsonArray keys = response.get("keys").getAsJsonArray();
                assertEquals(keys.size(), 2);
            }

            {
                JsonObject oldResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/jwt/jwks", null, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(),
                        "jwt");

                JsonArray oldKeys = oldResponse.getAsJsonArray("keys");
                assertEquals(oldKeys.size(), 2); // 1 static + 1 dynamic key
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        {
            String[] args = {"../"};

            TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
            Utils.setValueInConfig("supertokens_default_cdi_version", "2.9");
            process.startProcess();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

            {
                // check regular output
                JsonObject response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/.well-known/jwks.json", null,
                        1000, 1000, null);

                assertEquals(response.entrySet().size(), 1);

                assertTrue(response.has("keys"));
                JsonArray keys = response.get("keys").getAsJsonArray();
                assertEquals(keys.size(), 2);
            }

            {
                JsonObject oldResponse = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/recipe/jwt/jwks", null, 1000, 1000, null, Utils.getCdiVersionStringLatestForTests(),
                        "jwt");

                JsonArray oldKeys = oldResponse.getAsJsonArray("keys");
                assertEquals(oldKeys.size(), 2); // 1 static + 1 dynamic key
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }
    }
    
    @Test
    public void testInvalidSemanticVersion() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        Utils.setValueInConfig("supertokens_default_cdi_version", "2.x");
        process.startProcess();

        ProcessState.EventAndException state = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(state);

        assertEquals("supertokens_default_cdi_version is not a valid semantic version", state.exception.getCause().getMessage());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUnsupportedVersion() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);
        Utils.setValueInConfig("supertokens_default_cdi_version", "2.1");
        process.startProcess();

        ProcessState.EventAndException state = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(state);

        assertEquals("supertokens_default_cdi_version is not a supported version", state.exception.getCause().getMessage());
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
