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

package io.supertokens.webserver.api.core;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.webserver.GlobalRequestStats;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Admin-only endpoint exposing the process-global, per-status-class request counts held by
 * {@link GlobalRequestStats}. Served only on the admin connector ({@link RouteScope#ADMIN_ONLY}); returns 404
 * on the main port. Additive to — and independent of — the per-app {@link RequestStatsAPI}. See issue #1429 /
 * PLAN-014.
 *
 * <p>Note for consumers: the counts are process-global across <em>both</em> connectors and include admin-plane
 * traffic (e.g. liveness/stats polls), and {@code total} counts every request while the buckets cover only
 * {@code 2xx}/{@code 4xx}/{@code 5xx} — so {@code total} may exceed {@code 2xx + 4xx + 5xx} and that remainder is
 * not a meaningful "other" bucket. See {@link GlobalRequestStats} for the full counting semantics.
 */
public class GlobalRequestStatsAPI extends WebserverAPI {
    private static final long serialVersionUID = 6798101567879869154L;

    public GlobalRequestStatsAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/global-request-stats";
    }

    @Override
    public RouteScope getRouteScope() {
        // Global, process-wide operational stats: served only on the admin connector.
        return RouteScope.ADMIN_ONLY;
    }

    @Override
    protected boolean versionNeeded(HttpServletRequest req) {
        // Unversioned operational endpoint — it carries no CDI-version-dependent behaviour.
        return false;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject stats = GlobalRequestStats.getInstance(main).getStats();
        stats.addProperty("status", "OK");
        super.sendJsonResponse(200, stats, resp);
    }
}
