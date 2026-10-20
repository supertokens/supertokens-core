/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.exceptions.QuitProgramException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PathRouter extends WebserverAPI {
    private static final long serialVersionUID = -3199188474453935983L;

    List<WebserverAPI> apis;

    // Snapshot of the admin-connector routing state (enabled flag + admin port). The base config is immutable for
    // the lifetime of the process — the admin port cannot change without a restart, and a restart builds a fresh
    // Webserver (hence a fresh PathRouter). So we read it from the ResourceDistributor exactly once and reuse it on
    // every request rather than redoing the Config.getInstance lookup and rebuilding the override map per request on
    // the hot path. Computed lazily on first use because config is guaranteed loaded by the time any request is served.
    private volatile AdminGate adminGate;

    private static final class AdminGate {
        final boolean enabled;
        final int adminPort;
        // Operator-configured per-path RouteScope overrides (admin_only_paths / admin_preferred_paths), keyed by the
        // normalized canonical API path. A matched API not present here keeps its compiled-in scope. Empty when the
        // admin connector is disabled.
        final Map<String, RouteScope> scopeOverrides;

        AdminGate(boolean enabled, int adminPort, Map<String, RouteScope> scopeOverrides) {
            this.enabled = enabled;
            this.adminPort = adminPort;
            this.scopeOverrides = scopeOverrides;
        }
    }

    public PathRouter(Main main) {
        super(main, "");
        this.apis = new ArrayList();
    }

    private AdminGate getAdminGate() {
        AdminGate gate = this.adminGate;
        if (gate == null) {
            CoreConfig config = Config.getBaseConfig(main);
            boolean enabled = config.isAdminConnectorEnabled();
            // getAdminPort() dereferences the (nullable) admin_port, so only read it when the connector is enabled.
            gate = new AdminGate(enabled, enabled ? config.getAdminPort() : -1,
                    enabled ? buildScopeOverrides(config) : Collections.emptyMap());
            this.adminGate = gate;
        }
        return gate;
    }

    // Build the effective per-path scope override map from config. admin_only wins over admin_preferred if a path
    // somehow appears in both — but normalizeAndValidate already rejects that overlap before we get here.
    private static Map<String, RouteScope> buildScopeOverrides(CoreConfig config) {
        Map<String, RouteScope> overrides = new HashMap<>();
        for (String path : config.getAdminPreferredPaths()) {
            overrides.put(path, RouteScope.ADMIN_PREFERRED);
        }
        for (String path : config.getAdminOnlyPaths()) {
            overrides.put(path, RouteScope.ADMIN_ONLY);
        }
        return overrides;
    }

    // Fail startup loudly if a configured override path (admin_only_paths / admin_preferred_paths) doesn't match any
    // registered API — a typo would otherwise silently do nothing and leave the operator thinking a route was locked
    // to a port. Called once after all APIs are registered (see Webserver.setupRoutes). Inert when the admin
    // connector is disabled: config validation already rejects overrides without admin_port.
    public void validateRouteScopeOverrides() {
        CoreConfig config = Config.getBaseConfig(main);
        if (!config.isAdminConnectorEnabled()) {
            return;
        }
        Set<String> knownPaths = new HashSet<>();
        for (WebserverAPI api : this.apis) {
            knownPaths.add(CoreConfig.normalizeApiPath(api.getPath()));
        }
        Set<String> overridePaths = new HashSet<>();
        overridePaths.addAll(config.getAdminOnlyPaths());
        overridePaths.addAll(config.getAdminPreferredPaths());
        for (String path : overridePaths) {
            if (!knownPaths.contains(path)) {
                throw new QuitProgramException(
                        "Route-scope override path '" + path + "' in 'admin_only_paths'/'admin_preferred_paths' "
                                + "does not match any known API path.");
            }
        }
    }

    public void addAPI(WebserverAPI newApi) {
        this.apis.add(0, newApi); // add to the front so that the most recent API is checked first
        for (WebserverAPI api : this.apis) {
            for (WebserverAPI api2 : this.apis) {
                if (api != api2 && api.getPath().equals(api2.getPath())) {
                    throw new IllegalStateException("APIs given to the router cannot have the same path");
                }
            }
        }
    }

    @Override
    public String getPath() {
        return "/";
    }

    @Override
    protected boolean versionNeeded(HttpServletRequest req) {
        return getAPIThatMatchesPath(req).versionNeeded(req);
    }

    @Override
    protected boolean checkAPIKey(HttpServletRequest req) {
        return getAPIThatMatchesPath(req).checkAPIKey(req);
    }

    private WebserverAPI getAPIThatMatchesPath(HttpServletRequest req) {
        // getServletPath returns the path without the configured base path.
        String requestPath = req.getServletPath().toLowerCase();

        // first we check for exact match
        for (WebserverAPI api : this.apis) {
            String apiPath = api.getPath().toLowerCase();
            if (!apiPath.startsWith("/")) {
                apiPath = "/" + apiPath;
            }
            if (requestPath.equals(apiPath) || requestPath.equals(apiPath + "/")) {
                return api;
            }
        }

        // then we check if tenantId or appId is embedded in the URL.
        for (WebserverAPI api : this.apis) {
            String apiPath = api.getPath().toLowerCase();
            if (!apiPath.startsWith("/")) {
                apiPath = "/" + apiPath;
            }

            if (apiPath.endsWith("/")) {
                apiPath = apiPath.substring(0, apiPath.length() - 1);
            }

            if (apiPath.isBlank()) {
                String tenantIdStopWords = String.join("$|", Utils.INVALID_WORDS_FOR_TENANTID) + "$"; // Adds an end of string for each entry
                tenantIdStopWords += "|" + String.join("/|", Utils.INVALID_WORDS_FOR_TENANTID) + "/"; // Adds a trailing slash for each entry
                if (requestPath.matches(
                        "^(/appid-[a-z0-9-]*)?(/(?!" + tenantIdStopWords + ")[a-z0-9-]+)?" + "/?$")) {
                    return api;
                }
            } else {
                String tenantIdStopWords = String.join("/|", Utils.INVALID_WORDS_FOR_TENANTID) + "/"; // Adds a trailing slash for each entry
                if (requestPath.matches(
                        "^(/appid-[a-z0-9-]*)?(/(?!" + tenantIdStopWords + ")[a-z0-9-]+)?" + apiPath + "/?$")) {
                    return api;
                }
            }
        }
        for (WebserverAPI api : this.apis) {
            if (api.getPath().equals("/")) {
                return api;
            }
        }
        throw new RuntimeException("Should never come here");
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        WebserverAPI matchedApi = getAPIThatMatchesPath(req);

        // Port-scoped route gate. Only active when the admin connector is enabled; otherwise every route is
        // served on the single main connector exactly as before. getAPIThatMatchesPath has already resolved the
        // canonical API (the /appid-.../<tenant>/ prefix is stripped), so the scope check is clean here.
        AdminGate gate = getAdminGate();
        if (gate.enabled) {
            boolean onAdminPort = req.getLocalPort() == gate.adminPort;
            // Effective scope = operator override for this path (if configured), else the API's compiled-in scope.
            RouteScope scope = gate.scopeOverrides.getOrDefault(
                    CoreConfig.normalizeApiPath(matchedApi.getPath()), matchedApi.getRouteScope());
            // 404 (not 403): reads as "not served here", does not leak the route, and is not confused with an
            // auth failure. ADMIN_PREFERRED is served on both ports.
            if ((scope == RouteScope.ADMIN_ONLY && !onAdminPort)
                    || (scope == RouteScope.DATA_PLANE && onAdminPort)) {
                sendTextResponse(404, "Not found", resp);
                return;
            }
        }

        matchedApi.service(req, resp);
    }
}
