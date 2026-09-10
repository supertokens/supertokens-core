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
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager.TestingProcess;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.test.httpRequest.HttpResponseException;
import io.supertokens.webserver.WebserverAPI;
import io.supertokens.webserver.api.core.LivezAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

/**
 * Tests for the DB-free {@code /livez} liveness endpoint served on the admin connector. See issue #1428 /
 * PLAN-014. The key property under test is that {@code /livez} returns 200 without touching the database or
 * taking any shared lock, so it stays responsive under data-plane thread saturation and DB-pool exhaustion
 * (unlike {@code /hello}, which does a storage round-trip and is a readiness check).
 */
public class LivezTest {

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

    // GET with no cdi-version header (cdiVersion == null) and no api-key header.
    private static String getNoHeaders(TestingProcess process, String url, int timeoutMs) throws Exception {
        return HttpRequestForTesting.sendGETRequest(process.getProcess(), "", url, null, timeoutMs, timeoutMs, null,
                null, "");
    }

    // /livez returns 200 "OK" on the admin port with no api-key and no cdi-version, even when an api-key is
    // configured (checkAPIKey is false for this route, so the credential is never required).
    @Test
    public void testLivezReturns200OnAdminPortWithoutApiKeyOrCdiVersion() throws Exception {
        Utils.setValueInConfig("admin_port", ADMIN_PORT + "");
        Utils.setValueInConfig("api_keys", "abctijenbogweg=-2438243u98");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        assertEquals("OK", getNoHeaders(process, ADMIN + "/livez", 2000));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // /livez is ADMIN_ONLY: it is rejected with 404 on the main (data-plane) port.
    @Test
    public void testLivezReturns404OnMainPort() throws Exception {
        Utils.setValueInConfig("admin_port", ADMIN_PORT + "");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        try {
            getNoHeaders(process, MAIN + "/livez", 2000);
            fail("ADMIN_ONLY /livez should be 404 on the main port");
        } catch (HttpResponseException e) {
            assertEquals(404, e.statusCode);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // The route carries the ADMIN_ONLY classification mandated by PLAN-014.
    @Test
    public void testLivezRouteClassification() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        assertEquals(WebserverAPI.RouteScope.ADMIN_ONLY, new LivezAPI(process.getProcess()).getRouteScope());
        assertEquals("/livez", new LivezAPI(process.getProcess()).getPath());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // The key property: /livez does NOT touch the database. With the storage layer disabled (simulating the DB
    // being unavailable / its pool exhausted), /hello — which does a storage.getKeyValue round-trip — fails with
    // 500, while /livez still returns 200 promptly.
    @Test
    public void testLivezIsDbFreeWhenStorageIsDown() throws Exception {
        Utils.setValueInConfig("admin_port", ADMIN_PORT + "");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // Sanity: /livez is up on the admin port before we break storage.
        assertEquals("OK", getNoHeaders(process, ADMIN + "/livez", 2000));

        StorageLayer.getStorage(process.getProcess()).setStorageLayerEnabled(false);
        try {
            // /hello (ADMIN_PREFERRED) is served on the admin port too, but does a DB round-trip, so it fails.
            try {
                getNoHeaders(process, ADMIN + "/hello", 2000);
                fail("expected /hello to fail with the storage layer disabled");
            } catch (HttpResponseException e) {
                assertEquals(500, e.statusCode);
            }

            // /livez does no storage access, so it still returns 200 while the DB is unavailable.
            assertEquals("OK", getNoHeaders(process, ADMIN + "/livez", 2000));
        } finally {
            StorageLayer.getStorage(process.getProcess()).setStorageLayerEnabled(true);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // /livez stays responsive on the admin connector's isolated pool while the data-plane pool is fully
    // saturated (a request on the saturated main port cannot be served in time, proving the saturation).
    @Test
    public void testLivezResponsiveWhileDataPlanePoolSaturated() throws Exception {
        Utils.setValueInConfig("admin_port", ADMIN_PORT + "");
        Utils.setValueInConfig("max_server_pool_size", "2");
        Utils.setValueInConfig("admin_max_server_pool_size", "2");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // A DATA_PLANE route that blocks its worker thread for a while.
        io.supertokens.webserver.Webserver.getInstance(process.getProcess())
                .addAPI(new WebserverAPI(process.getProcess(), "") {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public String getPath() {
                        return "/blockingDataPlane";
                    }

                    @Override
                    protected boolean versionNeeded(jakarta.servlet.http.HttpServletRequest req) {
                        return false;
                    }

                    @Override
                    protected boolean checkAPIKey(jakarta.servlet.http.HttpServletRequest req) {
                        return false;
                    }

                    @Override
                    protected void doGet(jakarta.servlet.http.HttpServletRequest req,
                                         jakarta.servlet.http.HttpServletResponse resp) throws java.io.IOException {
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

        // The admin connector's own pool serves /livez promptly despite the saturated data-plane pool.
        assertEquals("OK", getNoHeaders(process, ADMIN + "/livez", 2000));

        // Meanwhile a request on the saturated main port cannot be served in time (proves the pool was saturated).
        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "", MAIN + "/blockingDataPlane",
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
