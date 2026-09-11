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
import io.supertokens.webserver.api.core.GlobalRequestStatsAPI;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Tests for the admin-only, process-global per-status-class request stats endpoint (/global-request-stats).
 * See issue #1429 / PLAN-014. Additive to the per-app /requests/stats — that endpoint is left untouched.
 */
public class GlobalRequestStatsTest {

    // Allocated per test with getFreePort() rather than a constant: the admin URL is used verbatim (only the main
    // port's ":3567" placeholder is rewritten by the harness), so a fixed admin port collides across the parallel
    // test forks build.gradle runs (maxParallelForks = availableProcessors), failing to bind the second Tomcat.
    private int adminPort;
    private String admin;
    private static final String MAIN = "http://localhost:3567";

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
        adminPort = TestingProcessManager.getFreePort();
        admin = "http://localhost:" + adminPort;
    }

    // A minimal data-plane route stub that responds with a fixed status (or throws to produce a 500), with no
    // api-key / cdi-version requirement so the test can drive known statuses without extra plumbing.
    private static WebserverAPI stub(TestingProcess process, String path, int status) {
        return new WebserverAPI(process.getProcess(), "") {
            private static final long serialVersionUID = 1L;

            @Override
            public String getPath() {
                return path;
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
                if (status >= 500) {
                    throw new RuntimeException("boom"); // service() maps this to a 500
                }
                sendTextResponse(status, "body", resp);
            }
        };
    }

    // Drive a GET and swallow the non-2xx HttpResponseException so callers can drive 4xx/5xx requests.
    private static void drive(TestingProcess process, String url) throws Exception {
        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "", url, null, 2000, 2000, null,
                    Utils.getCdiVersionStringLatestForTests(), "");
        } catch (HttpResponseException ignored) {
            // 4xx / 5xx are expected for the error stubs; the request still counts.
        }
    }

    private static JsonObject getStats(TestingProcess process, String url) throws Exception {
        // sendGETRequest returns the parsed JsonElement for a JSON response.
        JsonObject resp = HttpRequestForTesting.sendGETRequest(process.getProcess(), "", url, null, 2000, 2000, null,
                Utils.getCdiVersionStringLatestForTests(), "");
        return resp;
    }

    // After driving requests that return 200/401/500, the endpoint reports the matching 2xx/4xx/5xx counts and
    // total, and includes a `since` timestamp. The endpoint's own read increments happen only after the response
    // is produced, so the counts observed reflect exactly the driven requests.
    @Test
    public void testCountsByStatusClass() throws Exception {
        Utils.setValueInConfig("admin_port", adminPort + "");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(stub(process, "/ok", 200));
        Webserver.getInstance(process.getProcess()).addAPI(stub(process, "/unauth", 401));
        Webserver.getInstance(process.getProcess()).addAPI(stub(process, "/boom", 500));

        // 3x 2xx, 2x 4xx, 1x 5xx
        drive(process, MAIN + "/ok");
        drive(process, MAIN + "/ok");
        drive(process, MAIN + "/ok");
        drive(process, MAIN + "/unauth");
        drive(process, MAIN + "/unauth");
        drive(process, MAIN + "/boom");

        JsonObject stats = getStats(process, admin + "/global-request-stats");
        assertEquals("OK", stats.get("status").getAsString());
        assertEquals(3, stats.get("2xx").getAsLong());
        assertEquals(2, stats.get("4xx").getAsLong());
        assertEquals(1, stats.get("5xx").getAsLong());
        assertEquals(6, stats.get("total").getAsLong());
        assertTrue(stats.has("since"));
        assertTrue(stats.get("since").getAsLong() > 0);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // Counts are global: requests attributed to different apps all land in the single process-wide counter.
    @Test
    public void testCountsAreGlobalAcrossApps() throws Exception {
        Utils.setValueInConfig("admin_port", adminPort + "");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        Webserver.getInstance(process.getProcess()).addAPI(stub(process, "/ok", 200));

        // Same route driven under two different app-id prefixes; the global counter is not keyed per app.
        drive(process, MAIN + "/ok");
        drive(process, MAIN + "/appid-app1/ok");
        drive(process, MAIN + "/appid-app2/ok");

        JsonObject stats = getStats(process, admin + "/global-request-stats");
        assertEquals(3, stats.get("2xx").getAsLong());
        assertEquals(3, stats.get("total").getAsLong());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // The new endpoint is admin-only: 404 on the main port, 200 on the admin port.
    @Test
    public void testEndpointIsAdminOnly() throws Exception {
        Utils.setValueInConfig("admin_port", adminPort + "");
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        // Classified ADMIN_ONLY.
        assertEquals(WebserverAPI.RouteScope.ADMIN_ONLY,
                new GlobalRequestStatsAPI(process.getProcess()).getRouteScope());

        // 200 on the admin port.
        JsonObject stats = getStats(process, admin + "/global-request-stats");
        assertEquals("OK", stats.get("status").getAsString());

        // 404 on the main port.
        try {
            HttpRequestForTesting.sendGETRequest(process.getProcess(), "", MAIN + "/global-request-stats", null,
                    2000, 2000, null, Utils.getCdiVersionStringLatestForTests(), "");
            fail("/global-request-stats should be 404 on the main port");
        } catch (HttpResponseException e) {
            assertEquals(404, e.statusCode);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }

    // The existing per-app /requests/stats response is unchanged: it does not gain the new status-class fields.
    @Test
    public void testPerAppRequestStatsUnchanged() throws Exception {
        String[] args = {"../"};
        TestingProcess process = TestingProcessManager.startIsolatedProcess(args);
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STARTED));

        JsonObject stats = getStats(process, MAIN + "/requests/stats");
        assertEquals("OK", stats.get("status").getAsString());
        // Its released shape is preserved...
        assertTrue(stats.has("averageRequestsPerSecond"));
        assertTrue(stats.has("peakRequestsPerSecond"));
        assertTrue(stats.has("atMinute"));
        // ...and it did NOT gain the global endpoint's fields.
        assertFalse(stats.has("2xx"));
        assertFalse(stats.has("4xx"));
        assertFalse(stats.has("5xx"));
        assertFalse(stats.has("total"));
        assertFalse(stats.has("since"));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(PROCESS_STATE.STOPPED));
    }
}
