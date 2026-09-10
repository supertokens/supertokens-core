/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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

import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.webserver.Webserver;
import io.supertokens.webserver.WebserverAPI;
import io.supertokens.webserver.api.core.EEFeatureFlagAPI;
import io.supertokens.webserver.api.core.HelloAPI;
import io.supertokens.webserver.api.core.LicenseKeyAPI;
import io.supertokens.webserver.api.session.SessionAPI;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

/**
 * Tests for the opt-in admin connector (a second Tomcat connector on a separate port with its own thread pool)
 * and the port-scoped route classification enforced by PathRouter. See issue #1427 / PLAN-014.
 */
public class AdminConnectorTest {

    private static final int ADMIN_PORT = 3599;
    private static final String MAIN = "http://localhost:3567";
    private static final String ADMIN = "http://localhost:" + ADMIN_PORT;

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @Rule
    public TestRule retryFlaky = Utils.retryFlakyTest();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    // A minimal route stub with a configurable scope; no api-key / cdi-version needed so we can focus on the gate.
    private static WebserverAPI stub(TestingProcess process, String path, WebserverAPI.RouteScope scope,
                                     String body) {
        return new WebserverAPI(process.getProcess(), "") {
            private static final long serialVersionUID = 1L;

            @Override
            public String getPath() {
                return path;
            }

            @Override
            public RouteScope getRouteScope() {
                return scope;
            }

            @Override
            protected boolean versionNeeded(HttpServletRequest req) {
                return false;
            }

            @Override
            protected boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                sendTextResponse(200, body, resp);
            }
        };
    }

    private static String get(TestingProcess process, String url) throws Exception {
        return HttpRequestForTesting.sendGETRequest(process.getProcess(), "", url, null, 2000, 2000, null,
                Utils.getCdiVersionStringLatestForTests(), "");
    }

    // When the admin connector is disabled (no admin_port), every route behaves exactly as before on the main
    // port and there is no second port listening.
    @Test
    public void testAdminConnectorDisabledBehavesAsBefore() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // /hello (ADMIN_PREFERRED) is served normally on the main port; the classification is inert when disabled.
        assertTrue(get(process, MAIN + "/hello").contains("Hello"));

        // A DATA_PLANE route is served on the main port.
        Webserver.getInstance(process.getProcess())
                .addAPI(stub(process, "/dataPlaneStub", WebserverAPI.RouteScope.DATA_PLANE, "data-plane"));
        assertEquals("data-plane", get(process, MAIN + "/dataPlaneStub"));

        // Nothing is listening on the admin port.
        try {
            get(process, ADMIN + "/hello");
            fail("expected the admin port to not be listening");
        } catch (IOException e) {
            // connection refused / no route to host — the admin connector was never created
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // DATA_PLANE routes: 200 on the main port, 404 on the admin port.
    @Test
    public void testDataPlaneRouteRejectedOnAdminPort() throws Exception {
        Utils.setValueInConfig("admin_port", ADMIN_PORT + "");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess())
                .addAPI(stub(process, "/dataPlaneStub", WebserverAPI.RouteScope.DATA_PLANE, "data-plane"));

        assertEquals("data-plane", get(process, MAIN + "/dataPlaneStub"));

        try {
            get(process, ADMIN + "/dataPlaneStub");
            fail("DATA_PLANE route should be 404 on the admin port");
        } catch (HttpResponseException e) {
            assertEquals(404, e.statusCode);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // ADMIN_ONLY routes: 404 on the main port, 200 on the admin port.
    @Test
    public void testAdminOnlyRouteRejectedOnMainPort() throws Exception {
        Utils.setValueInConfig("admin_port", ADMIN_PORT + "");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess())
                .addAPI(stub(process, "/adminOnlyStub", WebserverAPI.RouteScope.ADMIN_ONLY, "admin-only"));

        assertEquals("admin-only", get(process, ADMIN + "/adminOnlyStub"));

        try {
            get(process, MAIN + "/adminOnlyStub");
            fail("ADMIN_ONLY route should be 404 on the main port");
        } catch (HttpResponseException e) {
            assertEquals(404, e.statusCode);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // ADMIN_PREFERRED routes are served on both ports (e.g. /hello).
    @Test
    public void testAdminPreferredRouteServedOnBothPorts() throws Exception {
        Utils.setValueInConfig("admin_port", ADMIN_PORT + "");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        assertTrue(get(process, MAIN + "/hello").contains("Hello"));
        assertTrue(get(process, ADMIN + "/hello").contains("Hello"));

        // A custom ADMIN_PREFERRED stub is also served on both.
        Webserver.getInstance(process.getProcess())
                .addAPI(stub(process, "/dualStub", WebserverAPI.RouteScope.ADMIN_PREFERRED, "dual"));
        assertEquals("dual", get(process, MAIN + "/dualStub"));
        assertEquals("dual", get(process, ADMIN + "/dualStub"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // The real control-plane / liveness routes carry the classification specified by PLAN-014.
    @Test
    public void testRealRouteClassification() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        assertEquals(WebserverAPI.RouteScope.ADMIN_PREFERRED,
                new HelloAPI(process.getProcess()).getRouteScope());
        assertEquals(WebserverAPI.RouteScope.ADMIN_PREFERRED,
                new EEFeatureFlagAPI(process.getProcess()).getRouteScope());
        assertEquals(WebserverAPI.RouteScope.ADMIN_PREFERRED,
                new LicenseKeyAPI(process.getProcess()).getRouteScope());
        // A representative data-plane route keeps the default scope.
        assertEquals(WebserverAPI.RouteScope.DATA_PLANE,
                new SessionAPI(process.getProcess()).getRouteScope());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // Setting admin_port equal to the main port is rejected at startup.
    @Test
    public void testAdminPortSameAsMainPortIsRejected() throws Exception {
        Utils.setValueInConfig("admin_port", "3567");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        io.supertokens.ProcessState.EventAndException e =
                process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("'admin_port' must be different from 'port'.", e.exception.getCause().getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // admin_port == 0 (and any value outside [1, 65535]) is rejected at startup: 0 would make Tomcat bind an
    // ephemeral port that the PathRouter gate can never match, silently breaking the route classification.
    @Test
    public void testAdminPortOutOfRangeIsRejected() throws Exception {
        Utils.setValueInConfig("admin_port", "0");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        io.supertokens.ProcessState.EventAndException e =
                process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("'admin_port' must be between 1 and 65535 inclusive.", e.exception.getCause().getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // admin_max_server_pool_size must be >= 1 when the admin connector is enabled.
    @Test
    public void testAdminMaxServerPoolSizeMustBePositive() throws Exception {
        Utils.setValueInConfig("admin_port", ADMIN_PORT + "");
        Utils.setValueInConfig("admin_max_server_pool_size", "0");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        io.supertokens.ProcessState.EventAndException e =
                process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("'admin_max_server_pool_size' must be >= 1.", e.exception.getCause().getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // Pool isolation: when the data-plane pool is fully saturated, liveness/admin traffic on the admin port is
    // still served promptly because the admin connector has its own thread pool.
    @Test
    public void testAdminPoolIsolatedFromSaturatedDataPlanePool() throws Exception {
        Utils.setValueInConfig("admin_port", ADMIN_PORT + "");
        Utils.setValueInConfig("max_server_pool_size", "2");
        Utils.setValueInConfig("admin_max_server_pool_size", "2");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // A DATA_PLANE route that blocks its worker thread for a while.
        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {
            private static final long serialVersionUID = 1L;

            @Override
            public String getPath() {
                return "/blockingDataPlane";
            }

            @Override
            protected boolean versionNeeded(HttpServletRequest req) {
                return false;
            }

            @Override
            protected boolean checkAPIKey(HttpServletRequest req) {
                return false;
            }

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                }
                sendTextResponse(200, "unblocked", resp);
            }
        });

        // Saturate the 2-thread data-plane pool (plus a couple queued) with blocking requests.
        ExecutorService executor = Executors.newFixedThreadPool(6);
        for (int i = 0; i < 6; i++) {
            executor.submit(() -> {
                try {
                    HttpRequestForTesting.sendGETRequest(process.getProcess(), "", MAIN + "/blockingDataPlane",
                            null, 10000, 10000, null, Utils.getCdiVersionStringLatestForTests(), "");
                } catch (Exception ignored) {
                }
            });
        }

        // Give the blockers time to occupy the data-plane workers.
        Thread.sleep(1000);

        // The admin connector's own pool serves /hello promptly despite the saturated data-plane pool.
        String adminHello = HttpRequestForTesting.sendGETRequest(process.getProcess(), "", ADMIN + "/hello",
                null, 2000, 2000, null, Utils.getCdiVersionStringLatestForTests(), "");
        assertTrue(adminHello.contains("Hello"));

        // Meanwhile /hello on the saturated main port cannot be served in time (proves the pool was saturated).
        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "", MAIN + "/hello",
                    null, 2000, 1500, null, Utils.getCdiVersionStringLatestForTests(), "");
            fail("expected the main-port request to time out under a saturated data-plane pool");
        } catch (SocketTimeoutException expected) {
            // data-plane pool is saturated; the request queues and times out
        }

        executor.shutdownNow();
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
