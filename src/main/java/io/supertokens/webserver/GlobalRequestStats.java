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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ResourceDistributor;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;

import java.util.concurrent.atomic.LongAdder;

/**
 * Process-global, in-memory request counts bucketed by HTTP response-status class ({@code 2xx}/{@code 4xx}/
 * {@code 5xx}) plus a total, cumulative since process start. Exposed on the admin-only
 * {@link io.supertokens.webserver.api.core.GlobalRequestStatsAPI}.
 *
 * <p>This is deliberately separate from the per-app {@link RequestStats}: that one is keyed per app and tracks
 * a rolling per-second window; this one is a single process-wide view across all apps. It is a
 * {@link ResourceDistributor.SingletonResource} keyed at {@link TenantIdentifier#BASE_TENANT}, so there is
 * exactly one instance per running core process and it resets when the process restarts.
 *
 * <p>Scope: this counts <em>every</em> request that reaches the end-of-{@code service()} hook on <em>either</em>
 * Tomcat connector, so it deliberately includes admin-plane traffic — liveness ({@code /livez}) and stats
 * ({@code /global-request-stats}) polls land in {@code 2xx}/{@code total} alongside data-plane requests. An
 * orchestrator polling those endpoints once a second will therefore show up here; a consumer gauging data-plane
 * load or error rate should account for its own poll cadence. Two categories of request are <em>not</em> counted,
 * because both are answered before control reaches the end-of-{@code service()} hook: requests that fail tenant
 * resolution (the hook's {@code tenantIdentifier == null} path), and requests 404'd by the {@link PathRouter}
 * port gate (a route hitting the wrong connector for its {@code RouteScope}, e.g. a data-plane path probed on the
 * admin port). So {@code total} will read below the raw connector request count when misrouted probes are present.
 *
 * <p>Held in memory only — never persisted to the database.
 */
public class GlobalRequestStats extends ResourceDistributor.SingletonResource {
    public static final String RESOURCE_KEY = "io.supertokens.webserver.GlobalRequestStats";

    private final LongAdder count2xx = new LongAdder();
    private final LongAdder count4xx = new LongAdder();
    private final LongAdder count5xx = new LongAdder();
    private final LongAdder total = new LongAdder();

    // Milliseconds since epoch at which this counter started accumulating (≈ process start). Lets a consumer
    // compute rates from the cumulative counts.
    private final long since;

    private GlobalRequestStats() {
        this.since = System.currentTimeMillis();
    }

    public static GlobalRequestStats getInstance(Main main) {
        ResourceDistributor resourceDistributor = main.getResourceDistributor();
        try {
            return (GlobalRequestStats) resourceDistributor.getResource(TenantIdentifier.BASE_TENANT, RESOURCE_KEY);
        } catch (TenantOrAppNotFoundException e) {
            // BASE_TENANT always exists, so this only happens on first use — create the single instance lazily.
            return (GlobalRequestStats) resourceDistributor.setResource(TenantIdentifier.BASE_TENANT, RESOURCE_KEY,
                    new GlobalRequestStats());
        }
    }

    /**
     * Records one completed request by its HTTP status code. Every recorded request counts toward {@code total};
     * it additionally lands in the {@code 2xx}/{@code 4xx}/{@code 5xx} bucket matching its status class. Other
     * classes (e.g. 1xx/3xx) count toward the total only — the core webserver does not use them in practice.
     */
    public void incrementForStatus(int statusCode) {
        total.increment();
        if (statusCode >= 200 && statusCode < 300) {
            count2xx.increment();
        } else if (statusCode >= 400 && statusCode < 500) {
            count4xx.increment();
        } else if (statusCode >= 500 && statusCode < 600) {
            count5xx.increment();
        }
    }

    public JsonObject getStats() {
        JsonObject result = new JsonObject();
        result.addProperty("2xx", count2xx.sum());
        result.addProperty("4xx", count4xx.sum());
        result.addProperty("5xx", count5xx.sum());
        result.addProperty("total", total.sum());
        result.addProperty("since", since);
        return result;
    }
}
