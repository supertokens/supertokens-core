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

import io.supertokens.Main;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

// DB-free liveness endpoint for the ECS container health check. Unlike /hello (which does a
// storage.getKeyValue round-trip and is therefore a readiness check), /livez does NO storage access and
// takes NO shared lock: it returns 200 purely as evidence that the process/JVM is alive and can schedule a
// request thread. It is ADMIN_ONLY, so when the admin connector is enabled (admin_port set) it is reachable only
// on the admin connector's isolated pool and stays responsive under data-plane thread saturation and DB-pool
// exhaustion. In the default single-connector topology (admin_port unset) the port gate is inert and /livez is
// served on the main port instead — ADMIN_ONLY is a port-routing classification, not an auth guarantee. See
// PLAN-014 / issue #1428.
public class LivezAPI extends WebserverAPI {

    private static final long serialVersionUID = 1L;

    public LivezAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/livez";
    }

    @Override
    public RouteScope getRouteScope() {
        // Served only on the admin connector's isolated pool; rejected with 404 on the main (data-plane) port.
        return RouteScope.ADMIN_ONLY;
    }

    @Override
    protected boolean versionNeeded(HttpServletRequest req) {
        // No cdi-version header required (or checked): liveness is independent of the CDI version.
        return false;
    }

    @Override
    protected boolean checkAPIKey(HttpServletRequest req) {
        // No api-key required: the probe must succeed without any credential.
        return false;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // No storage access, no lock — just confirm the process is alive and could schedule this thread.
        super.sendTextResponse(200, "OK", resp);
    }
}
