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
import io.supertokens.ProcessState.EventAndException;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.config.Config;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.RecipeRouter;
import io.supertokens.webserver.Webserver;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * TODO:
 * - Give unsupported version and make sure it fails
 * - Give all supported versions and make sure it passes
 * - Give no version and makes sure it treats it as 1.0
 * - Recipe Router tests
 * - Initialise two routes with the same path, different RID and query each and check that routing is happening
 * properly (for all HTTP methods).
 * - Use RecipeRouter in a way that the sub routes have different paths. This should throw an error
 */

public class WebserverTest extends Mockito {

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

    // Initialise two routes with the same path, different RID and query each and check that routing is happening
    // * properly (for all HTTP methods).
    @Test
    public void testInitializeTwoRoutesAndCheckRouting() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));
        String recipe_1 = "Recipe1";
        String recipe_2 = "Recipe2";

        Webserver.getInstance(process.getProcess()).addAPI(getRecipeRouter(process, recipe_1, recipe_2));

        // Responses when no recipe is given
        {
            // get request
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/testRecipe", new HashMap<>(), 1000, 1000, null,
                    SemVer.v2_7.get(), "");
            assertEquals("get request from Recipe1", response);

            // post request
            response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/testRecipe", new JsonObject(), 1000, 1000, null,
                    SemVer.v2_7.get(), "");
            assertEquals("post request from Recipe1", response);

            // put request
            response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/testRecipe", new JsonObject(), 1000, 1000, null,
                    SemVer.v2_7.get(), "");
            assertEquals("put request from Recipe1", response);

            // delete request
            response = HttpRequestForTesting.sendJsonDELETERequest(process.getProcess(), "",
                    "http://localhost:3567/testRecipe", new JsonObject(), 1000, 1000, null,
                    SemVer.v2_7.get(), "");
            assertEquals("delete request from Recipe1", response);

        }

        // Responses from recipe 1
        {
            // get request
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/testRecipe", new HashMap<>(), 1000, 1000, null,
                    SemVer.v2_7.get(), recipe_1);
            assertEquals("get request from Recipe1", response);

            // post request
            response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/testRecipe", new JsonObject(), 1000, 1000, null,
                    SemVer.v2_7.get(), recipe_1);
            assertEquals("post request from Recipe1", response);

            // put request
            response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/testRecipe", new JsonObject(), 1000, 1000, null,
                    SemVer.v2_7.get(), recipe_1);
            assertEquals("put request from Recipe1", response);

            // delete request
            response = HttpRequestForTesting.sendJsonDELETERequest(process.getProcess(), "",
                    "http://localhost:3567/testRecipe", new JsonObject(), 1000, 1000, null,
                    SemVer.v2_7.get(), recipe_1);
            assertEquals("delete request from Recipe1", response);

        }

        // recipe2 requests
        {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/testRecipe", new HashMap<>(), 1000, 1000, null,
                    SemVer.v2_7.get(), recipe_2);
            assertEquals("get request from Recipe2", response);

            response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/testRecipe", new JsonObject(), 1000, 1000, null,
                    SemVer.v2_7.get(), recipe_2);
            assertEquals("post request from Recipe2", response);

            response = HttpRequestForTesting.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/testRecipe", new JsonObject(), 1000, 1000, null,
                    SemVer.v2_7.get(), recipe_2);
            assertEquals("put request from Recipe2", response);

            response = HttpRequestForTesting.sendJsonDELETERequest(process.getProcess(), "",
                    "http://localhost:3567/testRecipe", new JsonObject(), 1000, 1000, null,
                    SemVer.v2_7.get(), recipe_2);
            assertEquals("delete request from Recipe2", response);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // Use RecipeRouter in a way that the sub routes have different paths. This should throw an error
    @Test
    public void testRecipeRouterWhereSubRoutesHaveDifferentPaths() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));
        String recipe_1 = "Recipe1";
        String recipe_2 = "Recipe2";

        try {
            Webserver.getInstance(process.getProcess())
                    .addAPI(new RecipeRouter(process.getProcess(), new WebserverAPI(process.getProcess(), recipe_1) {
                        private static final long serialVersionUID = -4495971833448004599L;

                        @Override
                        public String getPath() {
                            return "/testRecipe/recipe1";
                        }
                    }, new WebserverAPI(process.getProcess(), recipe_2) {
                        private static final long serialVersionUID = -4495971833448004599L;

                        @Override
                        public String getPath() {
                            return "/testRecipe/recipe2";
                        }
                    }));
            throw new Exception("Should not come here");
        } catch (Exception e) {
            assertEquals("All APIs given to router do not have the same path", e.getMessage());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // * - Give all supported versions and make sure it passes
// * - Give no version and makes sure it treats it as 1.0
    @Test
    public void testVersionSupport() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {
            private static final long serialVersionUID = -1495165001457526749L;

            @Override
            public String getPath() {
                return "/testSupportedVersions";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws IOException, ServletException {
                if (WebserverAPI.supportedVersions.contains(super.getVersionFromRequest(req))) {
                    sendTextResponse(200, "version supported", resp);
                } else {
                    sendTextResponse(500, "should not come here", resp);
                }
            }
        });

        SemVer[] supportedVersions = new SemVer[WebserverAPI.supportedVersions.size()];
        supportedVersions = WebserverAPI.supportedVersions.toArray(supportedVersions);
        for (int i = 0; i < supportedVersions.length; i++) {
            String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/testSupportedVersions", null, 1000, 1000, null,
                    supportedVersions[i].get(), "");
            assertEquals(response, "version supported");
        }

        String unsupportedCdiVersion = "234243";
        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/testSupportedVersions", null, 1000, 1000, null, unsupportedCdiVersion, "");
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: cdi-version " + unsupportedCdiVersion + " not supported");
        }

    }

    // * - Give no version and makes sure it treats it as the latest
    @Test
    public void testNoVersionGiven() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {
            private static final long serialVersionUID = 2132771458741821984L;

            @Override
            public String getPath() {
                return "/defaultVersion";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws IOException, ServletException {
                sendTextResponse(200, super.getVersionFromRequest(req).get(), resp);
            }
        });

        String response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                "http://localhost:3567/defaultVersion", null, 1000, 1000, null, null, "");
        assertEquals(response, Utils.getCdiVersionStringLatestForTests());
    }

    @Test
    public void testInvalidJSONBadInput() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {
            private static final long serialVersionUID = -2958203940142199528L;

            @Override
            public String getPath() {
                return "/testJsonInput";
            }

            @Override
            public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

                InputParser.parseJsonObjectOrThrowError(req);
                sendTextResponse(200, "validJsonBody", resp);
            }

        });

        try {
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/testJsonInput", null, 1000,
                    1000, null);
            fail();
        } catch (HttpResponseException e) {
            assertEquals("Http error. Status Code: 400. Message: Invalid Json Input", e.getMessage());
            assertEquals(e.statusCode, 400);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testValidJsonInput() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {
            private static final long serialVersionUID = -8747880939332229452L;

            @Override
            public String getPath() {
                return "/validJsonInput";
            }

            @Override
            public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

                InputParser.parseJsonObjectOrThrowError(req);
                sendTextResponse(200, "validJsonBody", resp);
            }

        });

        String response = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/validJsonInput", new JsonObject(), 1000, 1000, null);
        assertEquals(response, "validJsonBody");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

    @Test
    public void testInvalidGetInput() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {
            private static final long serialVersionUID = -2308640253232482194L;

            @Override
            public String getPath() {
                return "/invalidGetInput";
            }

            @Override
            public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

                InputParser.getQueryParamOrThrowError(req, "key", false);
                sendTextResponse(200, "validGetInput", resp);
            }

        });

        // null in parameter field
        try {
            HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/invalidGetInput", null, 1000,
                    1000, null);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'key' is missing in GET request");
            assertEquals(e.statusCode, 400);
        }

        // typo in params field/missing field
        HashMap<String, String> map = new HashMap<>();
        map.put("keyy", "value");

        try {
            HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/invalidGetInput", map, 1000,
                    1000, null);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(e.statusCode, 400);
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'key' is missing in GET request");
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testValidGetInput() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {
            private static final long serialVersionUID = 2225187160606405264L;

            @Override
            public String getPath() {
                return "/validInput";
            }

            @Override
            public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

                InputParser.getQueryParamOrThrowError(req, "key", false);
                sendTextResponse(200, "validGetInput", resp);
            }
        });
        HashMap<String, String> map = new HashMap<>();
        map.put("key", "value");
        String response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/validInput", map,
                1000, 1000, null);
        assertEquals(response, "validGetInput");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void serverHello() throws InterruptedException, IOException, HttpResponseException {
        hello("localhost", "3567");
    }

    @Test
    public void serverHelloWithoutDB() throws Exception {
        String hostName = "localhost";
        String port = "3567";
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        StorageLayer.getStorage(process.getProcess()).setStorageLayerEnabled(false);
        try {

            try {
                HttpRequest.sendGETRequest(process.getProcess(), "", "http://" + hostName + ":" + port + "/hello", null,
                        1000, 1000, null);
                throw new Exception("fail");
            } catch (HttpResponseException ex) {
                assert (ex.statusCode == 500);
            }

            try {
                HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://" + hostName + ":" + port + "/hello",
                        null, 1000, 1000, null);
            } catch (HttpResponseException ex) {
                assert (ex.statusCode == 500);
            }

            try {
                HttpRequest.sendJsonPUTRequest(process.getProcess(), "", "http://" + hostName + ":" + port + "/hello",
                        null, 1000, 1000, null);
            } catch (HttpResponseException ex) {
                assert (ex.statusCode == 500);
            }

            try {
                HttpRequest.sendJsonDELETERequest(process.getProcess(), "",
                        "http://" + hostName + ":" + port + "/hello", null, 1000, 1000, null);
            } catch (HttpResponseException ex) {
                assert (ex.statusCode == 500);
            }

        } finally {
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }
    }

    private void hello(String hostName, String port) throws InterruptedException, IOException, HttpResponseException {
        {
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));
            try {

                String response = HttpRequest.sendGETRequest(process.getProcess(), "",
                        "http://" + hostName + ":" + port + "/hello", null, 1000, 1000, null);
                assertEquals("Hello", response);

                response = HttpRequest.sendGETRequest(process.getProcess(), "",
                        "http://" + hostName + ":" + port + "/hello", null, 1000, 1000, null);
                assertEquals("Hello", response);

                response = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                        "http://" + hostName + ":" + port + "/hello/", null, 1000, 1000, null);
                assertEquals("Hello", response);

                response = HttpRequest.sendJsonPUTRequest(process.getProcess(), "",
                        "http://" + hostName + ":" + port + "/hello", null, 1000, 1000, null);
                assertEquals("Hello", response);

                response = HttpRequest.sendJsonDELETERequest(process.getProcess(), "",
                        "http://" + hostName + ":" + port + "/hello", null, 1000, 1000, null);
                assertEquals("Hello", response);

            } finally {
                process.kill();
                assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
            }
        }

        {
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));
            try {

                String response = HttpRequest.sendGETRequest(process.getProcess(), "",
                        "http://" + hostName + ":" + port + "/", null, 1000, 1000, null);
                assertEquals("Hello", response);

                response = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://" + hostName + ":" + port,
                        null, 1000, 1000, null);
                assertEquals("Hello", response);

                response = HttpRequest.sendJsonPUTRequest(process.getProcess(), "",
                        "http://" + hostName + ":" + port + "/", null, 1000, 1000, null);
                assertEquals("Hello", response);

                response = HttpRequest.sendJsonDELETERequest(process.getProcess(), "",
                        "http://" + hostName + ":" + port, null, 1000, 1000, null);
                assertEquals("Hello", response);

            } finally {
                process.kill();
                assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
            }
        }

    }

    @Test
    public void serverQuitProgramException() throws InterruptedException, IOException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public String getPath() {
                return "/testforexception";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                throw new QuitProgramException("Ending from API test");
            }
        });
        try {
            HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/testforexception", null, 1000,
                    1000, null);
        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 500
                    && e.getMessage().equals("Http error. Status Code: 500. Message: Internal Error"));
        }
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        process.kill();
    }

    @Test
    public void samePortTwoServersError() throws InterruptedException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        TestingProcess process2 = TestingProcessManager.start(args);
        EventAndException e = process2.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertTrue(e != null && e.exception instanceof QuitProgramException && e.exception.getMessage().equals(
                "Error while starting webserver. Possible reasons:\n- Another instance of SuperTokens is already "
                        + "running on the same port. If you want to run another instance, please pass a new config "
                        + "file to it with a different port or specify the port via CLI options. \n- If you are "
                        + "running this on port 80 or 443, make "
                        + "sure to give the right permission to SuperTokens.\n- The provided host is not available"
                        + " on this server"));

        assertNotNull(process2.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        process.kill();
        process2.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

    @Test
    public void differentHostNameTest() throws InterruptedException, IOException, HttpResponseException {

        InetAddress inetAddress = InetAddress.getLocalHost();
        if (!inetAddress.getHostAddress().equals("127.0.0.1")) {
            Utils.setValueInConfig("host", "\"" + inetAddress.getHostAddress() + "\"");
            hello(inetAddress.getHostAddress(), "3567");
            try {
                hello("localhost", "3567");
                fail();
            } catch (ConnectException ignored) {
            }
            try {
                hello("127.0.0.1", "3567");
                fail();
            } catch (ConnectException ignored) {
            }

            Utils.reset();
        }

        Utils.setValueInConfig("host", "\"localhost\"");
        hello("localhost", "3567");
        hello("127.0.0.1", "3567");
        try {
            hello(inetAddress.getHostAddress(), "3567");
            if (!inetAddress.getHostAddress().equals("127.0.0.1")) {
                fail();
            }
        } catch (ConnectException ignored) {
        }

        Utils.reset();

        Utils.setValueInConfig("host", "\"127.0.0.1\"");
        hello("localhost", "3567");
        hello("127.0.0.1", "3567");
        try {
            hello(inetAddress.getHostAddress(), "3567");
            if (!inetAddress.getHostAddress().equals("127.0.0.1")) {
                fail();
            }
        } catch (ConnectException ignored) {
        }

        Utils.reset();

        Utils.setValueInConfig("host", "\"0.0.0.0\"");
        hello("localhost", "3567");
        hello("127.0.0.1", "3567");
        hello(inetAddress.getHostAddress(), "3567");

        Utils.reset();

        Utils.setValueInConfig("host", "\"182.168.29.69\"");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        EventAndException e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertTrue(e != null && e.exception instanceof QuitProgramException && e.exception.getMessage().equals(
                "Error while starting webserver. Possible reasons:\n- Another instance of SuperTokens is already "
                        + "running on the same port. If you want to run another instance, please pass a new config "
                        + "file to it with a different port or specify the port via CLI options. \n- If you are "
                        + "running this on port 80 or 443, make "
                        + "sure to give the right permission to SuperTokens.\n- The provided host is not available"
                        + " on this server"));
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void differentPorts() throws InterruptedException, IOException, HttpResponseException {
        Utils.setValueInConfig("port", "8081");
        hello("localhost", "8081");
        try {
            hello("localhost", "3567");
            fail();
        } catch (ConnectException ignored) {
        }
    }

    @Test
    public void serverThreadPoolSizeOne() throws InterruptedException, IOException {
        Utils.setValueInConfig("max_server_pool_size", "1");

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public String getPath() {
                return "/testforthreadpool";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                super.sendTextResponse(200, "success", resp);
            }
        });

        // starting two threads.. one of them should take much longer to get the reply
        ThreadPoolTester p1 = new ThreadPoolTester(process);
        Thread t1 = new Thread(p1);
        t1.start();

        ThreadPoolTester p2 = new ThreadPoolTester(process);
        Thread t2 = new Thread(p2);
        t2.start();

        t1.join();
        t2.join();

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        assertTrue(p1.timedout || p2.timedout);
        assertTrue(!p1.timedout || !p2.timedout);

    }

    @Test
    public void serverThreadPoolSizeTwo() throws InterruptedException, IOException {
        Utils.setValueInConfig("max_server_pool_size", "2");

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public String getPath() {
                return "/testforthreadpool";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                super.sendTextResponse(200, "success", resp);
            }
        });

        // starting two threads.. one of them should take much longer to get the reply
        ThreadPoolTester p1 = new ThreadPoolTester(process);
        Thread t1 = new Thread(p1);
        t1.start();

        ThreadPoolTester p2 = new ThreadPoolTester(process);
        Thread t2 = new Thread(p2);
        t2.start();

        t1.join();
        t2.join();

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        assertTrue(!p1.timedout && !p2.timedout);

    }

    @Test
    public void notFoundTest() throws InterruptedException, IOException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public String getPath() {
                return "/notfoundmethodtest";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                super.sendTextResponse(200, "should not be called", resp);
            }
        });
        try {
            HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/randomPath", null, 1000, 1000,
                    null);
        } catch (HttpResponseException e) {
            assertTrue(
                    e.statusCode == 404 && e.getMessage().equals("Http error. Status Code: 404. Message: Not found"));
        }

        try {
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/notfoundmethodtest", null,
                    1000, 1000, null);
        } catch (HttpResponseException e) {
            assertTrue(e.statusCode == 405
                    && e.getMessage().equals("Http error. Status Code: 405. Message: Method not supported"));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void invalidBasePathTest() throws InterruptedException, IOException {
        String[] args = {"../"};
        HashMap<String, String> tests = new HashMap<>();
        tests.put("somepath/", "/somepath");
        tests.put("somepath//", "/somepath");
        tests.put("/somepath/", "/somepath");
        tests.put("//somepath//", "/somepath");
        tests.put("somepath", "/somepath");
        tests.put("/somepath", "/somepath");
        tests.put("some/path", "/some/path");
        tests.put("some/path/", "/some/path");
        tests.put("some/path//", "/some/path");
        tests.put("/some/path", "/some/path");
        tests.put("//some/path", "/some/path");
        tests.put("some//path", "/some/path");
        tests.put("some/////path", "/some/path");

        TestingProcess process;
        EventAndException e;
        for (String base_path : tests.keySet()) {
            String result = tests.get(base_path);
            Utils.setValueInConfig("base_path", base_path);
            process = TestingProcessManager.start(args);
            e = process.checkOrWaitForEvent(PROCESS_STATE.STARTED);
            assertEquals(result, Config.getConfig(process.main).getBasePath());
            process.kill();
            assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
        }

        Utils.setValueInConfig("base_path", "/some path");
        process = TestingProcessManager.start(args);
        e = process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertTrue(e != null && e.exception instanceof QuitProgramException
                && e.exception.getCause().getMessage().equals("Invalid characters in base_path config"));
        Utils.reset();
    }

    @Test
    public void validBasePath() throws InterruptedException, IOException, HttpResponseException {
        {
            Utils.setValueInConfig("base_path", "/");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            String response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/hello", null,
                    1000, 1000, null);
            assertEquals("Hello", response);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
            Utils.reset();
        }

        {
            Utils.setValueInConfig("base_path", "\"\"");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            String response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/hello", null,
                    1000, 1000, null);
            assertEquals("Hello", response);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
            Utils.reset();
        }

        {
            Utils.setValueInConfig("base_path", "\"/test\"");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            String response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/test/hello",
                    null, 1000, 1000, null);
            assertEquals("Hello", response);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
            Utils.reset();
        }

        {
            Utils.setValueInConfig("base_path", "\"/test/path\"");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            String response = HttpRequest.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/test/path/hello", null, 1000, 1000, null);
            assertEquals("Hello", response);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("base_path", "\"/te3st/Pa23th\"");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            {
                String response = HttpRequest.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/te3st/Pa23th/hello", null, 1000, 1000, null);
                assertEquals("Hello", response);
            }

            try {
                HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/hello", null, 1000, 1000,
                        null);
                fail();
            } catch (Exception e) {
                assert (e.getMessage().startsWith("Http error. Status Code: 404"));
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("base_path", "");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            String response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/hello", null,
                    1000, 1000, null);
            assertEquals("Hello", response);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

    }

    @Test
    public void validBasePathWithEmptyHelloPath() throws InterruptedException, IOException, HttpResponseException {
        {
            Utils.setValueInConfig("base_path", "/");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            String response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/", null,
                    1000, 1000, null);
            assertEquals("Hello", response);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
            Utils.reset();
        }

        {
            Utils.setValueInConfig("base_path", "\"\"");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            String response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/", null,
                    1000, 1000, null);
            assertEquals("Hello", response);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
            Utils.reset();
        }

        {
            Utils.setValueInConfig("base_path", "\"/test\"");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            String response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/test",
                    null, 1000, 1000, null);
            assertEquals("Hello", response);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
            Utils.reset();
        }

        {
            Utils.setValueInConfig("base_path", "\"/test/path\"");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            String response = HttpRequest.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/test/path/", null, 1000, 1000, null);
            assertEquals("Hello", response);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("base_path", "\"/te3st/Pa23th\"");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            {
                String response = HttpRequest.sendGETRequest(process.getProcess(), "",
                        "http://localhost:3567/te3st/Pa23th/", null, 1000, 1000, null);
                assertEquals("Hello", response);
            }

            try {
                HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/hello", null, 1000, 1000,
                        null);
                fail();
            } catch (Exception e) {
                assert (e.getMessage().startsWith("Http error. Status Code: 404"));
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

        {
            Utils.setValueInConfig("base_path", "");
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.start(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            String response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567", null,
                    1000, 1000, null);
            assertEquals("Hello", response);

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        }

    }

    private static RecipeRouter getRecipeRouter(TestingProcess process, String recipe_1, String recipe_2)
            throws Exception {
        WebserverAPI recipe_1_api = new WebserverAPI(process.getProcess(), recipe_1) {

            private static final long serialVersionUID = -1495165001457526749L;

            @Override
            public String getPath() {
                return "/testRecipe";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws IOException, ServletException {
                sendTextResponse(200, "get request from Recipe1", resp);
            }

            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                    throws IOException, ServletException {
                sendTextResponse(200, "post request from Recipe1", resp);
            }

            @Override
            protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                sendTextResponse(200, "put request from Recipe1", resp);
            }

            @Override
            protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                sendTextResponse(200, "delete request from Recipe1", resp);
            }

        };

        WebserverAPI recipe_2_api = new WebserverAPI(process.getProcess(), recipe_2) {

            private static final long serialVersionUID = -1495165001457526749L;

            @Override
            public String getPath() {
                return "/testRecipe";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws IOException, ServletException {
                sendTextResponse(200, "get request from Recipe2", resp);
            }

            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                    throws IOException, ServletException {
                sendTextResponse(200, "post request from Recipe2", resp);
            }

            @Override
            protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                sendTextResponse(200, "put request from Recipe2", resp);
            }

            @Override
            protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                sendTextResponse(200, "delete request from Recipe2", resp);
            }
        };

        return new RecipeRouter(process.getProcess(), recipe_1_api, recipe_2_api);
    }

    private static class ThreadPoolTester implements Runnable {

        private final TestingProcess process;
        boolean timedout = false;

        ThreadPoolTester(TestingProcess process) {
            this.process = process;
        }

        @Override
        public void run() {
            try {
                HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/testforthreadpool", null,
                        1000, 1500, null);
            } catch (Exception e) {
                if (e instanceof SocketTimeoutException) {
                    this.timedout = true;
                }
            }

        }
    }
}
