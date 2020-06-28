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
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.ProcessState.EventAndException;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.backendAPI.Ping;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpRequestMocking;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.RPMCalculator;
import io.supertokens.webserver.Webserver;
import io.supertokens.webserver.WebserverAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mockito;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * TODO:
 * - Give unsupported version and make sure it fails
 * - Give all supported versions and make sure it passes
 * - Give no version and makes sure it treats it as 1.0
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

    // * - Give all supported versions and make sure it passes
// * - Give no version and makes sure it treats it as 1.0
    @Test
    public void testVersionSupport() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess()) {
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

        Object[] supportedVersions = WebserverAPI.supportedVersions.toArray();
        for (int i = 0; i < supportedVersions.length; i++) {
            String response = io.supertokens.test.httpRequest.HttpRequest.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/testSupportedVersions", null, 1000,
                    1000, null, supportedVersions[i]
                            .toString());
            assertEquals(response, "version supported");
        }

        String unsupportedCdiVersion = "234243";
        try {
            io.supertokens.test.httpRequest.HttpRequest
                    .sendGETRequest(process.getProcess(), "", "http://localhost:3567/testSupportedVersions", null, 1000,
                            1000, null, unsupportedCdiVersion);
            fail();
        } catch (io.supertokens.test.httpRequest.HttpResponseException e) {
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: cdi-version " + unsupportedCdiVersion + " not supported");
        }

    }

    // * - Give no version and makes sure it treats it as 1.0
    @Test
    public void testNoVersionGiven() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess()) {
            private static final long serialVersionUID = 2132771458741821984L;

            @Override
            public String getPath() {
                return "/defaultVersion";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws IOException, ServletException {
                sendTextResponse(200, super.getVersionFromRequest(req), resp);
            }
        });

        String response = io.supertokens.test.httpRequest.HttpRequest
                .sendGETRequest(process.getProcess(), "", "http://localhost:3567/defaultVersion", null, 1000, 1000,
                        null, null);
        assertEquals(response, "1.0");
    }

    @Test
    public void testInvalidJSONBadInput() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess()) {
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
            HttpRequest
                    .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/testJsonInput", null, 1000,
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

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess()) {
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

        String response = HttpRequest
                .sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/validJsonInput", new JsonObject(),
                        1000, 1000, null);
        assertEquals(response, "validJsonBody");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

    @Test
    public void testInvalidGetInput() throws Exception {
        String[] args = {"../"};

        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess()) {
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
            HttpRequest
                    .sendGETRequest(process.getProcess(), "", "http://localhost:3567/invalidGetInput", null, 1000,
                            1000,
                            null);
            fail();
        } catch (HttpResponseException e) {
            assertEquals(e.getMessage(),
                    "Http error. Status Code: 400. Message: Field name 'key' is missing in GET request");
            assertEquals(e.statusCode, 400);
        }

        //typo in params field/missing field
        HashMap<String, String> map = new HashMap<>();
        map.put("keyy", "value");

        try {
            HttpRequest
                    .sendGETRequest(process.getProcess(), "", "http://localhost:3567/invalidGetInput", map, 1000,
                            1000,
                            null);
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

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess()) {
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
        String response = HttpRequest
                .sendGETRequest(process.getProcess(), "", "http://localhost:3567/validInput", map, 1000, 1000,
                        null);
        assertEquals(response, "validGetInput");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRPMCalculator() throws Exception {
        String[] args = {"../"};
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args, false);

        final HttpURLConnection mockCon = mock(HttpURLConnection.class);
        InputStream inputStrm = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        when(mockCon.getInputStream()).thenReturn(inputStrm);
        when(mockCon.getResponseCode()).thenReturn(200);
        when(mockCon.getOutputStream()).thenReturn(new OutputStream() {
            @Override
            public void write(int b) {
                output.write(b);
            }
        });

        RPMCalculator.getInstance(process.getProcess()).RPM_MIN_DELTA = 0.033; //2 seconds
        RPMCalculator.getInstance(process.getProcess()).RPM_HOUR_DELTA = 5;

        HttpRequestMocking.getInstance(process.getProcess()).setMockURL(
                Ping.REQUEST_ID, new HttpRequestMocking.URLGetter() {

                    @Override
                    public URL getUrl(String url) throws MalformedURLException {
                        URLStreamHandler stubURLStreamHandler = new URLStreamHandler() {
                            @Override
                            protected URLConnection openConnection(URL u) {
                                return mockCon;
                            }
                        };
                        return new URL(null, url, stubURLStreamHandler);
                    }
                });


        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.SERVER_PING));
        int lastIndexOf = output.toString().length();
        {
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/hello", new JsonObject(),
                    1000, 1000, null);
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/hello", new JsonObject(),
                    1000, 1000, null);
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/hello", new JsonObject(),
                    1000, 1000, null);

            Thread.sleep(2000);

            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/hello", new JsonObject(),
                    1000, 1000, null);
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/hello", new JsonObject(),
                    1000, 1000, null);


            Thread.sleep(3000);
            Ping.getInstance(process.getProcess()).doPing();

            assertEquals(
                    new JsonParser().parse(output.toString().substring(lastIndexOf)).getAsJsonObject()
                            .get("requestsPerMin")
                            .getAsJsonArray().get(0)
                            .getAsJsonObject().get("value").getAsInt(), 3);
        }
        lastIndexOf = output.toString().length();
        Thread.sleep(1000);
        {
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/hello", new JsonObject(),
                    1000, 1000, null);
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/hello", new JsonObject(),
                    1000, 1000, null);

            Thread.sleep(2000);

            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/hello", new JsonObject(),
                    1000, 1000, null);


            Thread.sleep(3000);
            Ping.getInstance(process.getProcess()).doPing();

            assertEquals(
                    new JsonParser().parse(output.toString().substring(lastIndexOf)).getAsJsonObject()
                            .get("requestsPerMin")
                            .getAsJsonArray().get(1)
                            .getAsJsonObject().get("value").getAsInt(), 2);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }


    @Test
    public void serverHello()
            throws InterruptedException, IOException, HttpResponseException {
        hello("localhost", "3567");
    }

    private void hello(String hostName, String port)
            throws InterruptedException, IOException, HttpResponseException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));
        try {

            String response = HttpRequest.sendGETRequest(process.getProcess(), "",
                    "http://" + hostName + ":" + port + "/hello", null, 1000, 1000, null);
            assertEquals("Hello", response);

            response = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://" + hostName + ":" + port + "/hello", null, 1000, 1000, null);
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

    @Test
    public void serverQuitProgramException()
            throws InterruptedException, IOException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess()) {

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
                "Error while starting webserver. Possible reasons:\n- Another instance of SuperTokens is already " +
                        "running on the same port. If you want to run another instance, please pass a new config " +
                        "file to it with a different port or specify the port via CLI options. \n- If you are " +
                        "running this on port 80 or 443, make " +
                        "sure to give the right permission to SuperTokens.\n- The provided host is not available" +
                        " on this server"));

        assertNotNull(process2.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

        process.kill();
        process2.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));

    }

    @Test
    public void differentHostNameTest() throws InterruptedException,
            IOException, HttpResponseException {

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
                "Error while starting webserver. Possible reasons:\n- Another instance of SuperTokens is already " +
                        "running on the same port. If you want to run another instance, please pass a new config " +
                        "file to it with a different port or specify the port via CLI options. \n- If you are " +
                        "running this on port 80 or 443, make " +
                        "sure to give the right permission to SuperTokens.\n- The provided host is not available" +
                        " on this server"));
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    @Test
    public void differentPorts() throws InterruptedException,
            IOException, HttpResponseException {
        Utils.setValueInConfig("port", "8081");
        hello("localhost", "8081");
        try {
            hello("localhost", "3567");
            fail();
        } catch (ConnectException ignored) {
        }
    }

    @Test
    public void serverThreadPoolSizeOne() throws InterruptedException,
            IOException {
        Utils.setValueInConfig("max_server_pool_size", "1");

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess()) {

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
    public void serverThreadPoolSizeTwo() throws InterruptedException,
            IOException {
        Utils.setValueInConfig("max_server_pool_size", "2");

        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess()) {

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
    public void notFoundTest()
            throws InterruptedException, IOException {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess()) {

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
