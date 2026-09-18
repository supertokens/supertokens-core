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

package io.supertokens.test.webserver;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.multitenancy.EmailPasswordConfig;
import io.supertokens.pluginInterface.multitenancy.PasswordlessConfig;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.ThirdPartyConfig;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.webserver.ConcurrencyLimiter;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the per-CUD in-flight concurrency cap: a connection URI domain over its
 * {@code max_concurrent_requests_per_cud} is 429'd, but only while the process-wide pool is saturated (past
 * {@code max_server_pool_size} minus {@code concurrency_cap_reserved_pool_percent}).
 *
 * <p>Every test uses a slow servlet ({@code /concurrencytest?ms=}) so requests hold their thread long enough to
 * observe in-flight state. The "fair-share" tests run on a single CUD (base), where the per-CUD and process-wide
 * counters coincide, so the saturation semantics are exercised without needing a second user pool — these run on
 * every storage. The cross-CUD tests (isolation, live reload) need real CUDs on separate user pools, so they only
 * run on a SQL (non in-memory) storage; on in-memory they early-return and are exercised by the CI postgres job.
 */
public class ConcurrencyLimiterTest {

    private static final String SLOW_PATH = "/concurrencytest";

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

    // ----- test 0: the cap alone never rejects while the pool is below the saturation threshold -----
    @Test
    public void testCapDoesNotRejectWhenNotSaturated() throws Exception {
        Utils.setValueInConfig("host", "\"0.0.0.0\"");
        Utils.setValueInConfig("max_server_pool_size", "10");
        Utils.setValueInConfig("concurrency_cap_reserved_pool_percent", "25"); // threshold = 8
        Utils.setValueInConfig("max_concurrent_requests_per_cud", "2");
        TestingProcessManager.TestingProcess process = startAndRegisterSlowServlet();
        int port = corePort();

        ExecutorService ex = Executors.newFixedThreadPool(4);
        List<Future<Resp>> futures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            futures.add(ex.submit(() -> get(port, SLOW_PATH + "?ms=3000")));
        }
        // wait for all three to be in flight (global = 3, well under the threshold of 8)
        waitForGlobalInFlight(process, 3, 15000);

        // a fourth concurrent request on the same (capped) CUD is still served, because the pool is not saturated,
        // even though four > the cap of two
        Resp fourth = get(port, SLOW_PATH + "?ms=100");
        assertEquals(200, fourth.status);

        for (Future<Resp> f : futures) {
            assertEquals(200, f.get(20, TimeUnit.SECONDS).status);
        }
        ex.shutdown();
        assertNull(ProcessState.getInstance(process.getProcess())
                .getLastEventByName(ProcessState.PROCESS_STATE.CONCURRENT_REQUEST_LIMIT_HIT));

        stop(process);
    }

    // ----- test 1: once saturated, a CUD over its cap is 429'd (with Retry-After + ProcessState), and recovers
    // once the pool drains (self-heal) -----
    @Test
    public void testCapEnforcedOnlyWhenSaturatedAndSelfHeals() throws Exception {
        Utils.setValueInConfig("host", "\"0.0.0.0\"");
        Utils.setValueInConfig("max_server_pool_size", "15");
        Utils.setValueInConfig("concurrency_cap_reserved_pool_percent", "50"); // threshold = 15 - 7 = 8
        Utils.setValueInConfig("max_concurrent_requests_per_cud", "2");
        TestingProcessManager.TestingProcess process = startAndRegisterSlowServlet();
        int port = corePort();

        ExecutorService ex = Executors.newFixedThreadPool(10);
        List<Future<Resp>> fillers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            fillers.add(ex.submit(() -> get(port, SLOW_PATH + "?ms=6000")));
        }
        // 8 concurrent requests on the capped CUD are all accepted (cap is 2) because we are exactly at, not past,
        // the threshold of 8 — proving the cap is a fair-share rule, not a quota
        waitForGlobalInFlight(process, 8, 20000);

        // one more pushes global past the threshold; this CUD is over its cap, so it is rejected immediately
        Resp rejected = get(port, SLOW_PATH + "?ms=6000");
        assertEquals(429, rejected.status);
        assertEquals("1", rejected.retryAfter);
        assertEquals("Too many concurrent requests for this tenant", rejected.body);
        assertNotNull(ProcessState.getInstance(process.getProcess())
                .getLastEventByName(ProcessState.PROCESS_STATE.CONCURRENT_REQUEST_LIMIT_HIT));

        // let the fillers drain
        for (Future<Resp> f : fillers) {
            assertEquals(200, f.get(20, TimeUnit.SECONDS).status);
        }
        waitForGlobalInFlight(process, 0, 20000);

        // pool is idle again: the same CUD is served once more (self-heal)
        Resp afterDrain = get(port, SLOW_PATH + "?ms=100");
        assertEquals(200, afterDrain.status);

        ex.shutdown();
        stop(process);
    }

    // ----- test 1 (hard-cap mode): reserved percent 100 => threshold 0 => the cap is always enforced -----
    @Test
    public void testHardCapModeWithFullReserve() throws Exception {
        Utils.setValueInConfig("host", "\"0.0.0.0\"");
        Utils.setValueInConfig("max_server_pool_size", "10");
        Utils.setValueInConfig("concurrency_cap_reserved_pool_percent", "100"); // threshold = 0
        Utils.setValueInConfig("max_concurrent_requests_per_cud", "2");
        TestingProcessManager.TestingProcess process = startAndRegisterSlowServlet();
        int port = corePort();

        ExecutorService ex = Executors.newFixedThreadPool(4);
        List<Future<Resp>> accepted = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            accepted.add(ex.submit(() -> get(port, SLOW_PATH + "?ms=4000")));
        }
        waitForGlobalInFlight(process, 2, 15000);

        // no filler traffic needed: with a 100% reserve the cap is enforced even though only 3 requests exist
        Resp third = get(port, SLOW_PATH + "?ms=4000");
        assertEquals(429, third.status);
        assertEquals("1", third.retryAfter);

        for (Future<Resp> f : accepted) {
            assertEquals(200, f.get(20, TimeUnit.SECONDS).status);
        }
        ex.shutdown();
        stop(process);
    }

    // ----- test 6: the real /hello liveness probe is never capped, even while the CUD is saturated -----
    @Test
    public void testHelloIsExemptFromTheCap() throws Exception {
        Utils.setValueInConfig("host", "\"0.0.0.0\"");
        Utils.setValueInConfig("max_server_pool_size", "10");
        Utils.setValueInConfig("concurrency_cap_reserved_pool_percent", "100"); // threshold = 0
        Utils.setValueInConfig("max_concurrent_requests_per_cud", "1");
        TestingProcessManager.TestingProcess process = startAndRegisterSlowServlet();
        int port = corePort();

        ExecutorService ex = Executors.newSingleThreadExecutor();
        Future<Resp> filler = ex.submit(() -> get(port, SLOW_PATH + "?ms=4000"));
        waitForGlobalInFlight(process, 1, 15000);

        // a second capped request is rejected...
        assertEquals(429, get(port, SLOW_PATH + "?ms=4000").status);
        // ...but /hello still succeeds (exempt from the cap; only counted globally)
        assertEquals(200, get(port, "/hello").status);

        assertEquals(200, filler.get(20, TimeUnit.SECONDS).status);
        ex.shutdown();
        stop(process);
    }

    // ----- test 3: a request that throws still releases both counters on the error path -----
    @Test
    public void testCountersReleasedOnErrorPath() throws Exception {
        Utils.setValueInConfig("host", "\"0.0.0.0\"");
        Utils.setValueInConfig("max_server_pool_size", "10");
        Utils.setValueInConfig("concurrency_cap_reserved_pool_percent", "100"); // threshold = 0
        Utils.setValueInConfig("max_concurrent_requests_per_cud", "1");
        TestingProcessManager.TestingProcess process = startAndRegisterSlowServlet();
        int port = corePort();

        // a request that throws => 500
        assertEquals(500, get(port, SLOW_PATH + "?throwErr=true").status);
        // counters are back to 0 (released in the finally)
        waitForGlobalInFlight(process, 0, 5000);
        // and the next request is accepted (would be 429 if the failed request had leaked an increment)
        assertEquals(200, get(port, SLOW_PATH + "?ms=100").status);

        stop(process);
    }

    // ----- test 5: with no cap configured (default 0), the pool can be filled without any rejection -----
    @Test
    public void testUnlimitedByDefault() throws Exception {
        Utils.setValueInConfig("host", "\"0.0.0.0\"");
        Utils.setValueInConfig("max_server_pool_size", "10");
        // no max_concurrent_requests_per_cud => default 0 => unlimited
        TestingProcessManager.TestingProcess process = startAndRegisterSlowServlet();
        int port = corePort();

        ExecutorService ex = Executors.newFixedThreadPool(10);
        List<Future<Resp>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(ex.submit(() -> get(port, SLOW_PATH + "?ms=3000")));
        }
        waitForGlobalInFlight(process, 8, 15000);
        // even a ninth concurrent request is served: an unlimited CUD is never rejected, only counted
        assertEquals(200, get(port, SLOW_PATH + "?ms=100").status);

        for (Future<Resp> f : futures) {
            assertEquals(200, f.get(20, TimeUnit.SECONDS).status);
        }
        ex.shutdown();
        stop(process);
    }

    // ----- test 9: after a burst that mixes accepts and rejects, both counters return to zero -----
    @Test
    public void testCountersReturnToZeroAfterBurst() throws Exception {
        Utils.setValueInConfig("host", "\"0.0.0.0\"");
        Utils.setValueInConfig("max_server_pool_size", "15");
        Utils.setValueInConfig("concurrency_cap_reserved_pool_percent", "50"); // threshold = 8
        Utils.setValueInConfig("max_concurrent_requests_per_cud", "2");
        TestingProcessManager.TestingProcess process = startAndRegisterSlowServlet();
        int port = corePort();

        ExecutorService ex = Executors.newFixedThreadPool(12);
        List<Future<Resp>> futures = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            futures.add(ex.submit(() -> get(port, SLOW_PATH + "?ms=2000")));
        }

        int ok = 0;
        int rejected = 0;
        for (Future<Resp> f : futures) {
            int status = f.get(30, TimeUnit.SECONDS).status;
            if (status == 200) {
                ok++;
            } else if (status == 429) {
                rejected++;
            } else {
                fail("unexpected status " + status);
            }
        }
        assertTrue("expected some accepted requests", ok > 0);
        assertTrue("expected some rejected requests once saturated", rejected > 0);

        waitForGlobalInFlight(process, 0, 20000);
        assertEquals(0, ConcurrencyLimiter.getGlobalInFlightForTesting(process.getProcess()));

        ex.shutdown();
        stop(process);
    }

    // ----- test 7: config validation -----
    @Test
    public void testNegativeCapRejected() throws Exception {
        Utils.setValueInConfig("max_concurrent_requests_per_cud", "-1");
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(new String[]{"../"});
        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("'max_concurrent_requests_per_cud' must be >= 0", e.exception.getCause().getMessage());
        stop(process);
    }

    @Test
    public void testCapAbovePoolSizeRejected() throws Exception {
        Utils.setValueInConfig("max_server_pool_size", "10");
        Utils.setValueInConfig("max_concurrent_requests_per_cud", "11");
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(new String[]{"../"});
        ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
        assertNotNull(e);
        assertEquals("'max_concurrent_requests_per_cud' must be <= 'max_server_pool_size'",
                e.exception.getCause().getMessage());
        stop(process);
    }

    @Test
    public void testReservedPercentOutOfRangeRejected() throws Exception {
        for (String bad : new String[]{"0", "101"}) {
            Utils.reset();
            Utils.setValueInConfig("concurrency_cap_reserved_pool_percent", bad);
            TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(
                    new String[]{"../"});
            ProcessState.EventAndException e = process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.INIT_FAILURE);
            assertNotNull(e);
            assertEquals("'concurrency_cap_reserved_pool_percent' must be between 1 and 100 inclusive",
                    e.exception.getCause().getMessage());
            stop(process);
        }
    }

    @Test
    public void testLowReservePercentStillReservesAtLeastOneSlot() throws Exception {
        // A reserve percent that rounds down to zero reserved slots (floor(10 * 1 / 100) == 0) must still keep the
        // saturation threshold strictly below the pool size. The process-wide in-flight count is bounded by the
        // Tomcat connector's maxThreads (== max_server_pool_size), so if the threshold equalled the pool size the
        // "global > threshold" test could never be true and the per-CUD cap would be silently never enforced.
        Utils.setValueInConfig("max_server_pool_size", "10");
        Utils.setValueInConfig("concurrency_cap_reserved_pool_percent", "1"); // floor(10 * 1 / 100) == 0 reserved
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(new String[]{"../"});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        // clamped to pool - 1 rather than pool, so a nonzero reserve always reserves at least one slot
        assertEquals(9, Config.getBaseConfig(process.getProcess()).getConcurrencyCapSaturationThreshold());

        stop(process);
    }

    // ----- test 2: one CUD over its cap under saturation does not affect another CUD (SQL only) -----
    @Test
    public void testIsolationBetweenCuds() throws Exception {
        Utils.setValueInConfig("host", "\"0.0.0.0\"");
        Utils.setValueInConfig("max_server_pool_size", "15");
        Utils.setValueInConfig("concurrency_cap_reserved_pool_percent", "50"); // threshold = 8
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(new String[]{"../"});
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL
                || StorageLayer.isInMemDb(process.getProcess())) {
            stop(process);
            return;
        }

        // CUD "localhost" is capped at 2; CUD "127.0.0.1" is uncapped (the saturating traffic source)
        JsonObject cappedConfig = new JsonObject();
        cappedConfig.add("max_concurrent_requests_per_cud", new JsonPrimitive(2));
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(cappedConfig, 2);
        JsonObject uncappedConfig = new JsonObject();
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(uncappedConfig, 3);

        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(),
                new TenantConfig(new TenantIdentifier("localhost", null, null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]), new PasswordlessConfig(false),
                        null, null, cappedConfig), false);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(),
                new TenantConfig(new TenantIdentifier("127.0.0.1", null, null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]), new PasswordlessConfig(false),
                        null, null, uncappedConfig), false);
        registerSlowServlet(process);
        int port = corePort();

        ExecutorService ex = Executors.newFixedThreadPool(12);
        List<Future<Resp>> fillers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            fillers.add(ex.submit(() -> get("127.0.0.1", port, SLOW_PATH + "?ms=8000")));
        }
        waitForGlobalInFlight(process, 8, 20000);

        // two accepted on the capped CUD, taking it to its cap
        List<Future<Resp>> capped = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            capped.add(ex.submit(() -> get("localhost", port, SLOW_PATH + "?ms=8000")));
        }
        waitForGlobalInFlight(process, 10, 20000);

        // a third on the capped CUD is rejected (over cap AND saturated)...
        assertEquals(429, get("localhost", port, SLOW_PATH + "?ms=8000").status);
        // ...but the uncapped CUD is still served under the same saturation (isolation)
        assertEquals(200, get("127.0.0.1", port, SLOW_PATH + "?ms=100").status);

        for (Future<Resp> f : fillers) {
            assertEquals(200, f.get(30, TimeUnit.SECONDS).status);
        }
        for (Future<Resp> f : capped) {
            assertEquals(200, f.get(30, TimeUnit.SECONDS).status);
        }
        ex.shutdown();
        stop(process);
    }

    // ----- test 4: a live config reload changes the cap for the next request (SQL only) -----
    @Test
    public void testLiveReloadOfCap() throws Exception {
        Utils.setValueInConfig("host", "\"0.0.0.0\"");
        Utils.setValueInConfig("max_server_pool_size", "10");
        Utils.setValueInConfig("concurrency_cap_reserved_pool_percent", "100"); // hard cap for determinism
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(new String[]{"../"});
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL
                || StorageLayer.isInMemDb(process.getProcess())) {
            stop(process);
            return;
        }

        JsonObject cudConfig = new JsonObject();
        cudConfig.add("max_concurrent_requests_per_cud", new JsonPrimitive(1));
        StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                .modifyConfigToAddANewUserPoolForTesting(cudConfig, 2);
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(),
                new TenantConfig(new TenantIdentifier("localhost", null, null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]), new PasswordlessConfig(false),
                        null, null, cudConfig), false);
        registerSlowServlet(process);
        int port = corePort();

        ExecutorService ex = Executors.newFixedThreadPool(4);
        Future<Resp> f1 = ex.submit(() -> get("localhost", port, SLOW_PATH + "?ms=4000"));
        waitForGlobalInFlight(process, 1, 15000);
        // cap is 1, hard-cap mode => the second concurrent request is rejected
        assertEquals(429, get("localhost", port, SLOW_PATH + "?ms=4000").status);
        assertEquals(200, f1.get(20, TimeUnit.SECONDS).status);
        waitForGlobalInFlight(process, 0, 15000);

        // raise the cap to 3 via a config reload; the change applies to the next request with no restart. Reuse
        // the CUD's existing config (which carries its user-pool db settings) and only change the cap, otherwise
        // the update would try to remap the CUD to a different user pool.
        cudConfig.add("max_concurrent_requests_per_cud", new JsonPrimitive(3));
        Multitenancy.addNewOrUpdateAppOrTenant(process.getProcess(),
                new TenantConfig(new TenantIdentifier("localhost", null, null), new EmailPasswordConfig(false),
                        new ThirdPartyConfig(false, new ThirdPartyConfig.Provider[0]), new PasswordlessConfig(false),
                        null, null, cudConfig), false);

        List<Future<Resp>> accepted = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            accepted.add(ex.submit(() -> get("localhost", port, SLOW_PATH + "?ms=4000")));
        }
        waitForGlobalInFlight(process, 3, 15000);
        // now three fit under the raised cap, and the fourth is rejected
        assertEquals(429, get("localhost", port, SLOW_PATH + "?ms=4000").status);
        for (Future<Resp> f : accepted) {
            assertEquals(200, f.get(20, TimeUnit.SECONDS).status);
        }
        ex.shutdown();
        stop(process);
    }

    // ---------- helpers ----------

    private TestingProcessManager.TestingProcess startAndRegisterSlowServlet() throws Exception {
        TestingProcessManager.TestingProcess process = TestingProcessManager.startIsolatedProcess(new String[]{"../"});
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));
        registerSlowServlet(process);
        return process;
    }

    private void registerSlowServlet(TestingProcessManager.TestingProcess process) {
        Webserver.getInstance(process.getProcess()).addAPI(new WebserverAPI(process.getProcess(), "") {
            @Override
            public String getPath() {
                return SLOW_PATH;
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
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws IOException, ServletException {
                if ("true".equals(req.getParameter("throwErr"))) {
                    throw new ServletException(new RuntimeException("boom"));
                }
                String ms = req.getParameter("ms");
                if (ms != null) {
                    try {
                        Thread.sleep(Long.parseLong(ms));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                super.sendTextResponse(200, "ok", resp);
            }
        });
    }

    private static int corePort() {
        return HttpRequestForTesting.corePort == null ? 3567 : HttpRequestForTesting.corePort;
    }

    private static void waitForGlobalInFlight(TestingProcessManager.TestingProcess process, int expected,
                                              long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        int last = -1;
        while (System.currentTimeMillis() - start < timeoutMs) {
            last = ConcurrencyLimiter.getGlobalInFlightForTesting(process.getProcess());
            if (last == expected) {
                return;
            }
            Thread.sleep(20);
        }
        fail("timed out waiting for global in-flight to reach " + expected + " (was " + last + ")");
    }

    private static Resp get(int port, String path) throws IOException {
        return get("localhost", port, path);
    }

    private static Resp get(String host, int port, String path) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL("http://" + host + ":" + port + path).openConnection();
        con.setConnectTimeout(3000);
        con.setReadTimeout(30000);
        con.setRequestMethod("GET");
        Resp r = new Resp();
        try {
            r.status = con.getResponseCode();
            r.retryAfter = con.getHeaderField("Retry-After");
            InputStream is = r.status < 400 ? con.getInputStream() : con.getErrorStream();
            if (is != null) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        sb.append(line);
                    }
                }
                r.body = sb.toString();
            }
        } finally {
            con.disconnect();
        }
        return r;
    }

    private void stop(TestingProcessManager.TestingProcess process) throws InterruptedException {
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    private static class Resp {
        int status;
        String retryAfter;
        String body;
    }
}
