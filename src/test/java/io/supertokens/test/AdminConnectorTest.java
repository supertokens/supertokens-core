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

import com.google.gson.JsonObject;
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
import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.util.Map;
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

    // The running process inherits the JVM's environment, so to exercise the env-var config path we mutate the
    // backing map of System.getenv() for the duration of a single test (same approach as EnvConfigTest).
    @SuppressWarnings("unchecked")
    private static Map<String, String> getWritableEnv() {
        try {
            Map<String, String> env = System.getenv();
            Field field = env.getClass().getDeclaredField("m");
            field.setAccessible(true);
            return (Map<String, String>) field.get(env);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get writable environment", e);
        }
    }

    private static String setEnv(String key, String value) {
        String originalValue = System.getenv(key);
        getWritableEnv().put(key, value);
        return originalValue;
    }

    private static void restoreEnv(String key, String originalValue) {
        if (originalValue == null) {
            getWritableEnv().remove(key);
        } else {
            getWritableEnv().put(key, originalValue);
        }
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

    // The admin connector can be enabled purely via the SUPERTOKENS_ADMIN_PORT environment variable — the primary
    // deployment path for Docker/k8s — not just via config.yaml. Regression test for admin_port (a boxed Integer)
    // being silently dropped by updateConfigJsonFromEnv, which had no Integer branch: the value was read from the
    // environment but never written to configJson, so the connector never started.
    @Test
    public void testAdminPortLoadedFromEnvVar() throws Exception {
        String originalValue = setEnv("SUPERTOKENS_ADMIN_PORT", ADMIN_PORT + "");
        try {
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            // The connector actually started on the env-configured admin port: ADMIN_PREFERRED /hello is reachable
            // there, which only happens if admin_port made it out of the env var and into the config.
            assertTrue(get(process, ADMIN + "/hello").contains("Hello"));

            // And the port-scoped gate is active: a DATA_PLANE route is 404 on the admin port.
            Webserver.getInstance(process.getProcess())
                    .addAPI(stub(process, "/dataPlaneStub", WebserverAPI.RouteScope.DATA_PLANE, "data-plane"));
            try {
                get(process, ADMIN + "/dataPlaneStub");
                fail("DATA_PLANE route should be 404 on the admin port");
            } catch (HttpResponseException e) {
                assertEquals(404, e.statusCode);
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        } finally {
            restoreEnv("SUPERTOKENS_ADMIN_PORT", originalValue);
        }
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

    // Per-path route-scope override (admin_only_paths): an operator can promote a compiled-in ADMIN_PREFERRED route
    // (here /hello) to ADMIN_ONLY without a code change — the Stage-3 hardening lever. /hello then behaves like an
    // admin-only route: 200 on the admin port, 404 on the main port.
    @Test
    public void testAdminOnlyPathOverridePromotesRealRoute() throws Exception {
        Utils.setValueInConfig("admin_port", ADMIN_PORT + "");
        Utils.setValueInConfig("admin_only_paths", "/hello");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // Overridden to ADMIN_ONLY: served on the admin port, rejected on the main port.
        assertTrue(get(process, ADMIN + "/hello").contains("Hello"));
        try {
            get(process, MAIN + "/hello");
            fail("/hello overridden to admin_only should be 404 on the main port");
        } catch (HttpResponseException e) {
            assertEquals(404, e.statusCode);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // Per-path route-scope override (admin_preferred_paths): an operator can widen a compiled-in DATA_PLANE route
    // (here the public JWKS endpoint) to ADMIN_PREFERRED, so it is also served on the admin port instead of being
    // rejected there. It keeps working on the main port too.
    @Test
    public void testAdminPreferredPathOverrideWidensRealRoute() throws Exception {
        Utils.setValueInConfig("admin_port", ADMIN_PORT + "");
        Utils.setValueInConfig("admin_preferred_paths", "/.well-known/jwks.json");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // Now dual: served on both ports (default DATA_PLANE would 404 on the admin port). This endpoint returns
        // JSON, so fetch it as a JsonObject rather than through the text get() helper.
        JsonObject mainResp = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                MAIN + "/.well-known/jwks.json", null, 2000, 2000, null,
                Utils.getCdiVersionStringLatestForTests(), "");
        assertTrue(mainResp.has("keys"));
        JsonObject adminResp = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                ADMIN + "/.well-known/jwks.json", null, 2000, 2000, null,
                Utils.getCdiVersionStringLatestForTests(), "");
        assertTrue(adminResp.has("keys"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // A typo'd override path that matches no registered API fails startup loudly (QuitProgramException surfaced as
    // INIT_FAILURE) rather than silently doing nothing.
    @Test
    public void testRouteScopeOverrideUnknownPathRejected() throws Exception {
        Utils.setValueInConfig("admin_port", ADMIN_PORT + "");
        Utils.setValueInConfig("admin_only_paths", "/does-not-exist");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        io.supertokens.ProcessState.EventAndException e =
                process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertTrue(e.exception.getMessage().contains("does not match any known API path"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // Overrides are meaningless without the admin connector — configuring them with no admin_port is rejected.
    @Test
    public void testRouteScopeOverrideRequiresAdminPort() throws Exception {
        Utils.setValueInConfig("admin_only_paths", "/hello");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        io.supertokens.ProcessState.EventAndException e =
                process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("'admin_only_paths' and 'admin_preferred_paths' require 'admin_port' to be set.",
                e.exception.getCause().getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // A path cannot be forced to two scopes at once.
    @Test
    public void testRouteScopeOverrideConflictRejected() throws Exception {
        Utils.setValueInConfig("admin_port", ADMIN_PORT + "");
        Utils.setValueInConfig("admin_only_paths", "/hello");
        Utils.setValueInConfig("admin_preferred_paths", "/hello");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        io.supertokens.ProcessState.EventAndException e =
                process.checkOrWaitForEvent(PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("'/hello' cannot be listed in both 'admin_only_paths' and 'admin_preferred_paths'.",
                e.exception.getCause().getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // Overrides are configurable purely via environment variables (the primary Docker/k8s path), not just yaml.
    @Test
    public void testAdminOnlyPathsLoadedFromEnvVar() throws Exception {
        String originalPort = setEnv("SUPERTOKENS_ADMIN_PORT", ADMIN_PORT + "");
        String originalPaths = setEnv("ADMIN_ONLY_PATHS", "/hello");
        try {
            String[] args = {"../"};
            TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

            // The env-configured override took effect: /hello is admin-only (200 on admin, 404 on main).
            assertTrue(get(process, ADMIN + "/hello").contains("Hello"));
            try {
                get(process, MAIN + "/hello");
                fail("/hello overridden to admin_only via env var should be 404 on the main port");
            } catch (HttpResponseException e) {
                assertEquals(404, e.statusCode);
            }

            process.kill();
            assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
        } finally {
            restoreEnv("ADMIN_ONLY_PATHS", originalPaths);
            restoreEnv("SUPERTOKENS_ADMIN_PORT", originalPort);
        }
    }
}
