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

package io.supertokens.webserver;

import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounds how many request threads a single connection URI domain (CUD) may hold at once, enforced only while the
 * shared Tomcat pool is saturated. There is one instance per CUD (keyed by the CUD's base tenant so all its
 * apps/tenants share a single in-flight counter) and one process-wide counter of all requests in flight on this
 * core, both tied to the {@link Main} instance via the {@link ResourceDistributor} so they die with it.
 *
 * <p>The cap and the saturation threshold are passed in per request (read from the live config), so a config
 * reload applies to the next request with no state migration; a {@code Semaphore} sized at construction could not
 * be resized while permits are out, which is why plain {@link AtomicInteger}s are used instead.
 */
public class ConcurrencyLimiter extends ResourceDistributor.SingletonResource {

    private static final String RESOURCE_KEY = "io.supertokens.webserver.ConcurrencyLimiter";
    private static final String GLOBAL_RESOURCE_KEY = "io.supertokens.webserver.ConcurrencyLimiter.global";

    // a saturated CUD is rejected on every request; log the WARN at most once per this window so it cannot flood
    private static final long WARN_INTERVAL_MILLIS = 60 * 1000;

    // requests from this CUD currently in flight on this core
    private final AtomicInteger inFlight = new AtomicInteger();

    // last time (epoch millis) this CUD's rejection WARN was logged; rate-limits the WARN to one per window
    private volatile long lastWarnAtMillis = 0;

    // all requests currently in flight on this core; shared by every CUD's limiter of this Main
    private final AtomicInteger globalInFlight;

    private ConcurrencyLimiter(AtomicInteger globalInFlight) {
        this.globalInFlight = globalInFlight;
    }

    // process-wide in-flight counter holder, one per Main, keyed at the base tenant
    private static class GlobalCounter extends ResourceDistributor.SingletonResource {
        private final AtomicInteger count = new AtomicInteger();
    }

    private static AtomicInteger getGlobalCounter(Main main) {
        TenantIdentifier baseTenant = new TenantIdentifier(null, null, null);
        try {
            return ((GlobalCounter) main.getResourceDistributor().getResource(baseTenant, GLOBAL_RESOURCE_KEY)).count;
        } catch (TenantOrAppNotFoundException e) {
            return ((GlobalCounter) main.getResourceDistributor()
                    .setResource(baseTenant, GLOBAL_RESOURCE_KEY, new GlobalCounter())).count;
        }
    }

    public static ConcurrencyLimiter getInstance(Main main, TenantIdentifier tenantIdentifier)
            throws TenantOrAppNotFoundException {
        // one limiter per connection URI domain: key by the CUD's base tenant so all apps/tenants of the CUD
        // share one in-flight counter
        TenantIdentifier cud = new TenantIdentifier(tenantIdentifier.getConnectionUriDomain(), null, null);
        try {
            return (ConcurrencyLimiter) main.getResourceDistributor().getResource(cud, RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            // Only create a limiter for a CUD that actually exists, otherwise a flood of requests for unknown
            // CUDs could fill memory (mirrors RequestStats.getInstance). setResource is idempotent
            // (putIfAbsent), so racing threads share the instance that actually got stored.
            if (Multitenancy.getTenantInfo(main, cud) == null) {
                throw e;
            }
            return (ConcurrencyLimiter) main.getResourceDistributor()
                    .setResource(cud, RESOURCE_KEY, new ConcurrencyLimiter(getGlobalCounter(main)));
        }
    }

    /**
     * Increments both the per-CUD and the process-wide in-flight counters, then decides whether this request may
     * proceed. The counters are bumped first so the check is made against a state that already includes this
     * request; a rejected request rolls both back so it never leaves a stale increment. The caller MUST call
     * {@link #release()} in a finally when (and only when) this returns true.
     *
     * @param limit               this CUD's {@code max_concurrent_requests_per_cud} (0 = unlimited)
     * @param saturationThreshold process-wide in-flight count above which caps are enforced
     * @return true if the request may proceed, false to reject with 429
     */
    public boolean tryAcquire(int limit, int saturationThreshold) {
        int global = globalInFlight.incrementAndGet();
        int mine = inFlight.incrementAndGet();
        if (limit > 0 && mine > limit && global > saturationThreshold) {
            inFlight.decrementAndGet();
            globalInFlight.decrementAndGet();
            return false;
        }
        return true;
    }

    public void release() {
        inFlight.decrementAndGet();
        globalInFlight.decrementAndGet();
    }

    public int inFlight() {
        return inFlight.get();
    }

    /**
     * Records a request that was just rejected with 429 for this CUD, for observability. It bumps the app's
     * {@code concurrentRequestsRejected} counter in {@link RequestStats} (scraped by the SaaS via
     * {@code /requests/stats}), adds the {@link ProcessState.PROCESS_STATE#CONCURRENT_REQUEST_LIMIT_HIT} test
     * state, and logs a single WARN — but at most once per CUD per {@link #WARN_INTERVAL_MILLIS}, because a
     * saturated tenant is rejected on every request and would otherwise flood the log. This is best-effort and
     * never throws, so it can never interfere with sending the 429 to the client.
     */
    public void onRejected(Main main, TenantIdentifier tenantIdentifier) {
        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.CONCURRENT_REQUEST_LIMIT_HIT, null);
        try {
            RequestStats.getInstance(main, tenantIdentifier.toAppIdentifier()).incrementConcurrentRequestsRejected();
        } catch (TenantOrAppNotFoundException e) {
            // the app was removed between acquiring and rejecting; nothing to count for it
        }
        if (shouldWarnNow(System.currentTimeMillis())) {
            int limit;
            try {
                limit = Config.getConfig(tenantIdentifier, main).getMaxConcurrentRequestsPerCud();
            } catch (TenantOrAppNotFoundException e) {
                limit = 0; // config vanished mid-request; still worth a WARN, just without the exact cap
            }
            String cud = tenantIdentifier.getConnectionUriDomain();
            Logging.warn(main, tenantIdentifier, "Concurrent request cap (" + limit + ") reached for "
                    + (cud == null ? "" : cud) + "; rejecting with 429");
        }
    }

    // returns true (and records the time) at most once per WARN_INTERVAL_MILLIS; synchronized so concurrent
    // rejections on the same CUD produce exactly one WARN per window rather than racing on the check-then-set
    private synchronized boolean shouldWarnNow(long now) {
        if (now - lastWarnAtMillis >= WARN_INTERVAL_MILLIS) {
            lastWarnAtMillis = now;
            return true;
        }
        return false;
    }

    /**
     * Current number of requests in flight for the CUD of {@code tenantIdentifier}, or 0 if no limiter exists yet
     * for that CUD. Read-only: unlike {@link #getInstance}, it never creates a limiter, so reporting stats has no
     * side effects.
     */
    public static int getInFlightForCud(Main main, TenantIdentifier tenantIdentifier) {
        TenantIdentifier cud = new TenantIdentifier(tenantIdentifier.getConnectionUriDomain(), null, null);
        try {
            return ((ConcurrencyLimiter) main.getResourceDistributor().getResource(cud, RESOURCE_KEY)).inFlight();
        } catch (TenantOrAppNotFoundException e) {
            return 0;
        }
    }

    @TestOnly
    public static int getGlobalInFlightForTesting(Main main) {
        return getGlobalCounter(main).get();
    }
}
