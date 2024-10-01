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
import com.google.gson.JsonParser;
import io.supertokens.ProcessState;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.webserver.Webserver;
import io.supertokens.webserver.WebserverAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.*;

public class HttpRequestTest {

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
    public void jsonResponseTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // Json Response API
        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {
            private static final long serialVersionUID = -7347714438908490973L;

            @Override
            protected boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/jsonResponse";
            }

            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                String jsonInput = "{" + "\"key\": \"value\"" + "}";
                super.sendJsonResponse(200, new JsonParser().parse(jsonInput).getAsJsonObject(), resp);
            }

            @Override
            protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                String jsonInput = "{" + "\"key2\": \"value2\"" + "}";
                super.sendJsonResponse(200, new JsonParser().parse(jsonInput).getAsJsonObject(), resp);

            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                String jsonInput = "{" + "\"key1\": \"value1\"" + "}";
                super.sendJsonResponse(200, new JsonParser().parse(jsonInput).getAsJsonObject(), resp);
            }

            @Override
            protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                String jsonInput = "{\n" + "\"key3\": \"value3\"" + "}";
                super.sendJsonResponse(200, new JsonParser().parse(jsonInput).getAsJsonObject(), resp);
            }

        });

        // jsonResponse with post request

        JsonObject response = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/jsonResponse", null, 1000, 1000, null);

        assertEquals(response.get("key").getAsString(), "value");

        // jsonResponse with get request

        response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/jsonResponse", null,
                1000, 1000, null);
        assertEquals(response.get("key1").getAsString(), "value1");

        // jsonResponse with put request
        response = HttpRequest.sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/jsonResponse", null,
                1000, 1000, null);
        assertEquals(response.get("key2").getAsString(), "value2");

        // jsonResponse with delete request
        response = HttpRequest.sendJsonDELETERequest(process.getProcess(), "", "http://localhost:3567/jsonResponse",
                null, 1000, 1000, null);
        assertEquals(response.get("key3").getAsString(), "value3");

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void nonJsonResponseTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {
            private static final long serialVersionUID = -5953383281218376801L;

            @Override
            protected boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/nonJsonResponse";
            }

            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

                super.sendTextResponse(200, "Non JSON Response", resp);
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

                super.sendTextResponse(200, "Non JSON Response", resp);
            }

            @Override
            protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {

                super.sendTextResponse(200, "Non JSON Response", resp);
            }

            @Override
            protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {

                super.sendTextResponse(200, "Non JSON Response", resp);
            }
        });

        // nonJson Post request
        String response = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                "http://localhost:3567/nonJsonResponse", null, 1000, 1000, null);
        assertEquals("Non JSON Response", response);

        // nonJson Get request
        response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/nonJsonResponse", null,
                1000, 1000, null);
        assertEquals("Non JSON Response", response);

        // nonJson Put request
        response = HttpRequest.sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/nonJsonResponse",
                null, 1000, 1000, null);
        assertEquals("Non JSON Response", response);

        // nonJson Delete request
        response = HttpRequest.sendJsonDELETERequest(process.getProcess(), "", "http://localhost:3567/nonJsonResponse",
                null, 1000, 1000, null);
        assertEquals("Non JSON Response", response);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void errorRequestTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // error request api
        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {
            private static final long serialVersionUID = -9210034480396407612L;

            @Override
            protected boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/errorRequest";
            }

            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

                super.sendTextResponse(500, "ERROR", resp);
            }

            @Override
            public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                super.sendTextResponse(500, "ERROR", resp);

            }

            @Override
            public void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                super.sendTextResponse(500, "ERROR", resp);
            }

            @Override
            public void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                super.sendTextResponse(500, "ERROR", resp);
            }
        });

        // Post error request
        try {
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/errorRequest", null, 1000,
                    1000, null);
            fail();
        } catch (HttpResponseException e) {
            assertTrue((e.getMessage().equals("Http error. Status Code: 500. Message: ERROR") && e.statusCode == 500));
        }

        // Get error Request
        try {
            HttpRequest.sendJsonPOSTRequest(process.getProcess(), "", "http://localhost:3567/errorRequest", null, 1000,
                    1000, null);
            fail();
        } catch (HttpResponseException e) {
            assertTrue((e.getMessage().equals("Http error. Status Code: 500. Message: ERROR") && e.statusCode == 500));
        }

        // Put error Request
        try {
            HttpRequest.sendJsonPUTRequest(process.getProcess(), "", "http://localhost:3567/errorRequest", null, 1000,
                    1000, null);
            fail();
        } catch (HttpResponseException e) {
            assertTrue((e.getMessage().equals("Http error. Status Code: 500. Message: ERROR") && e.statusCode == 500));
        }

        // Delete error Request
        try {
            HttpRequest.sendJsonDELETERequest(process.getProcess(), "", "http://localhost:3567/errorRequest", null,
                    1000, 1000, null);
            fail();
        } catch (HttpResponseException e) {
            assertTrue((e.getMessage().equals("Http error. Status Code: 500. Message: ERROR") && e.statusCode == 500));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void withAndWithoutBodyTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // api to check with Body
        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {
            private static final long serialVersionUID = 6527072853102511509L;

            @Override
            protected boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/withBody";
            }

            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = req.getReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                }

                if (body.length() > 0) {
                    super.sendJsonResponse(200, new JsonParser().parse(body.toString()).getAsJsonObject(), resp);
                } else {
                    super.sendTextResponse(500, "No Body Found", resp);
                }
            }

            @Override
            protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = req.getReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                }

                if (body.length() > 0) {
                    super.sendJsonResponse(200, new JsonParser().parse(body.toString()).getAsJsonObject(), resp);
                } else {
                    super.sendTextResponse(500, "No Body Found", resp);
                }
            }

            @Override
            protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = req.getReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                }

                if (body.length() > 0) {
                    super.sendJsonResponse(200, new JsonParser().parse(body.toString()).getAsJsonObject(), resp);
                } else {
                    super.sendTextResponse(500, "No Body Found", resp);
                }
            }
        });

        String body = "{\n" + "\t\"message\": \"Body Found\"\n" + "}";
        JsonObject jsonBody = new JsonParser().parse(body).getAsJsonObject();

        // Post Request with Body
        {
            JsonObject response = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/withBody", jsonBody, 1000, 1000, null);

            assertEquals(response.get("message").getAsString(), "Body Found");
        }
        // Put Request with Body
        {
            JsonObject response = HttpRequest.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/withBody", jsonBody, 1000, 1000, null);

            assertEquals(response.get("message").getAsString(), "Body Found");

        }

        // Delete Request with Body
        {
            JsonObject response = HttpRequest.sendJsonDELETERequest(process.getProcess(), "",
                    "http://localhost:3567/withBody", jsonBody, 1000, 1000, null);

            assertEquals(response.get("message").getAsString(), "Body Found");

        }

        // api to check without Body
        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {
            private static final long serialVersionUID = 5264933962074907258L;

            @Override
            public String getPath() {
                return "/withoutBody";
            }

            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = req.getReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                }

                if (body.length() == 0) {
                    body.append("{" + "\"message\": \"No Body Found\"" + "}");
                    super.sendJsonResponse(200, new JsonParser().parse(body.toString()).getAsJsonObject(), resp);

                } else {
                    super.sendTextResponse(500, "Body Found", resp);
                }
            }

            @Override
            protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = req.getReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                }

                if (body.length() == 0) {
                    body.append("{\n" + "\"message\": \"No Body Found\"" + "}");
                    super.sendJsonResponse(200, new JsonParser().parse(body.toString()).getAsJsonObject(), resp);
                } else {
                    super.sendTextResponse(500, "Body Found", resp);
                }
            }

            @Override
            protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = req.getReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                }

                if (body.length() == 0) {
                    body.append("{" + "\"message\": \"No Body Found\"" + "}");
                    super.sendJsonResponse(200, new JsonParser().parse(body.toString()).getAsJsonObject(), resp);

                } else {
                    super.sendTextResponse(500, "Body Found", resp);
                }
            }
        });

        // post request without body
        {
            JsonObject response = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/withoutBody", null, 1000, 1000, null);

            assertEquals(response.get("message").getAsString(), "No Body Found");

        }

        // put request without body
        {
            JsonObject response = HttpRequest.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/withoutBody", null, 1000, 1000, null);

            assertEquals(response.get("message").getAsString(), "No Body Found");

        }

        // Delete request without body
        {
            JsonObject response = HttpRequest.sendJsonDELETERequest(process.getProcess(), "",
                    "http://localhost:3567/withoutBody", null, 1000, 1000, null);

            assertEquals(response.get("message").getAsString(), "No Body Found");

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void withAndWithoutVersionTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // api to check withVersion
        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            protected boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/withVersion";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                if (req.getHeader("api-version") != null) {
                    super.sendTextResponse(200, req.getHeader("api-version"), resp);
                } else {
                    super.sendTextResponse(500, "No Version was sent", resp);
                }
            }

            @Override
            public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                if (req.getHeader("api-version") != null) {
                    super.sendTextResponse(200, req.getHeader("api-version"), resp);
                } else {
                    super.sendTextResponse(500, "No Version was sent", resp);
                }
            }

            @Override
            public void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                if (req.getHeader("api-version") != null) {
                    super.sendTextResponse(200, req.getHeader("api-version"), resp);
                } else {
                    super.sendTextResponse(500, "No Version was sent", resp);
                }
            }

            @Override
            public void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                if (req.getHeader("api-version") != null) {
                    super.sendTextResponse(200, req.getHeader("api-version"), resp);
                } else {
                    super.sendTextResponse(500, "No Version was sent", resp);
                }
            }

        });

        // Get Request
        {
            String response = HttpRequest.sendGETRequest(process.getProcess(), "", "http://localhost:3567/withVersion",
                    null, 1000, 1000, 0);
            assertEquals(response, "0");

        }

        // Post Request
        {
            String response = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/withVersion", null, 1000, 1000, 0);
            assertEquals(response, "0");

        }

        // Put Request
        {
            String response = HttpRequest.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/withVersion", null, 1000, 1000, 0);
            assertEquals(response, "0");

        }

        // Delete Request
        {
            String response = HttpRequest.sendJsonDELETERequest(process.getProcess(), "",
                    "http://localhost:3567/withVersion", null, 1000, 1000, 0);
            assertEquals(response, "0");

        }

        // api to check without Version
        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public String getPath() {
                return "/withoutVersion";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                if (req.getHeader("api-version") == null) {
                    super.sendTextResponse(200, "No Version was sent", resp);
                } else {
                    super.sendTextResponse(500, req.getHeader("api-version"), resp);
                }
            }

            @Override
            public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                if (req.getHeader("api-version") == null) {
                    super.sendTextResponse(200, "No Version was sent", resp);
                } else {
                    super.sendTextResponse(500, req.getHeader("api-version"), resp);
                }
            }

            @Override
            public void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                if (req.getHeader("api-version") == null) {
                    super.sendTextResponse(200, "No Version was sent", resp);
                } else {
                    super.sendTextResponse(500, req.getHeader("api-version"), resp);
                }
            }

            @Override
            public void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                if (req.getHeader("api-version") == null) {
                    super.sendTextResponse(200, "No Version was sent", resp);
                } else {
                    super.sendTextResponse(500, req.getHeader("api-version"), resp);
                }
            }

        });

        // Get Request
        {

            String response = HttpRequest.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/withoutVersion", null, 1000, 1000, null);
            assertEquals(response, "No Version was sent");

        }

        // Post Request
        {
            String response = HttpRequest.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/withoutVersion", null, 1000, 1000, null);
            assertEquals(response, "No Version was sent");

        }

        // Put Request
        {
            String response = HttpRequest.sendJsonPUTRequest(process.getProcess(), "",
                    "http://localhost:3567/withoutVersion", null, 1000, 1000, null);
            assertEquals(response, "No Version was sent");

        }

        // Delete Request
        {
            String response = HttpRequest.sendJsonDELETERequest(process.getProcess(), "",
                    "http://localhost:3567/withoutVersion", null, 1000, 1000, null);
            assertEquals(response, "No Version was sent");

        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));

    }

    @Test
    public void getRequestTest() throws Exception {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // api to check getRequestWithParams
        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            protected boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            public String getPath() {
                return "/getTestWithParams";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

                if (req.getParameterNames().hasMoreElements()) {
                    if (req.getParameter("key").equals("value")) {
                        super.sendTextResponse(200, "200", resp);
                    } else {
                        super.sendTextResponse(500, "bad input in body", resp);
                    }
                } else {
                    super.sendTextResponse(500, "No parameters were found", resp);
                }
            }

        });

        HashMap<String, String> map = new HashMap<>();
        map.put("key", "value");

        {
            String response = HttpRequest.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/getTestWithParams", map, 1000, 1000, null);
            assertEquals(response, "200");
        }

        // api to check getRequestWithoutParams

        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {

            private static final long serialVersionUID = 1L;

            @Override
            public String getPath() {
                return "/getTestWithoutParams";
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

                if (!(req.getParameterNames().hasMoreElements())) {
                    super.sendTextResponse(200, "200", resp);
                } else {
                    super.sendTextResponse(500, "Parameters were found", resp);
                }
            }
        });

        {
            String response = HttpRequest.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/getTestWithoutParams", null, 1000, 1000, null);
            assertEquals(response, "200");
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
