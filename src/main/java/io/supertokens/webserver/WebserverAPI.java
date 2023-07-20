/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

import com.google.gson.JsonElement;
import io.supertokens.AppIdentifierWithStorageAndUserIdMapping;
import io.supertokens.Main;
import io.supertokens.TenantIdentifierWithStorageAndUserIdMapping;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.filters.RemoteAddrFilter;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

public abstract class WebserverAPI extends HttpServlet {

    private static final long serialVersionUID = 1L;
    protected final Main main;
    public static final Set<SemVer> supportedVersions = new HashSet<>();
    private String rid;

    static {
        supportedVersions.add(SemVer.v2_7);
        supportedVersions.add(SemVer.v2_8);
        supportedVersions.add(SemVer.v2_9);
        supportedVersions.add(SemVer.v2_10);
        supportedVersions.add(SemVer.v2_11);
        supportedVersions.add(SemVer.v2_12);
        supportedVersions.add(SemVer.v2_13);
        supportedVersions.add(SemVer.v2_14);
        supportedVersions.add(SemVer.v2_15);
        supportedVersions.add(SemVer.v2_16);
        supportedVersions.add(SemVer.v2_17);
        supportedVersions.add(SemVer.v2_18);
        supportedVersions.add(SemVer.v2_19);
        supportedVersions.add(SemVer.v2_20);
        supportedVersions.add(SemVer.v2_21);
        supportedVersions.add(SemVer.v3_0);
    }

    public static SemVer getLatestCDIVersion() {
        return SemVer.v3_0;
    }

    public SemVer getLatestCDIVersionForRequest(HttpServletRequest req)
            throws ServletException, TenantOrAppNotFoundException {
        SemVer maxCDIVersion = getLatestCDIVersion();
        String maxCDIVersionStr = Config.getConfig(
                getAppIdentifierWithStorage(req).getAsPublicTenantIdentifier(), main).getMaxCDIVersion();
        if (maxCDIVersionStr != null) {
            maxCDIVersion = new SemVer(maxCDIVersionStr);
        }
        return maxCDIVersion;
    }

    public WebserverAPI(Main main, String rid) {
        super();
        this.main = main;
        this.rid = rid;
    }

    public String getRID() {
        return this.rid;
    }

    public abstract String getPath();

    protected void sendTextResponse(int statusCode, String message, HttpServletResponse resp) throws IOException {
        resp.setStatus(statusCode);
        resp.setHeader("Content-Type", "text/html; charset=UTF-8");
        resp.getWriter().println(message);
    }

    protected void sendJsonResponse(int statusCode, JsonElement json, HttpServletResponse resp) throws IOException {
        resp.setStatus(statusCode);
        resp.setHeader("Content-Type", "application/json; charset=UTF-8");
        resp.getWriter().println(json.toString());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        this.sendTextResponse(405, "Method not supported", resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        this.sendTextResponse(405, "Method not supported", resp);
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        this.sendTextResponse(405, "Method not supported", resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        this.sendTextResponse(405, "Method not supported", resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        this.sendTextResponse(405, "Method not supported", resp);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        this.sendTextResponse(405, "Method not supported", resp);
    }

    @Override
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        this.sendTextResponse(405, "Method not supported", resp);
    }

    private void assertThatVersionIsCompatible(SemVer version) throws ServletException {
        if (version == null) {
            throw new ServletException(new BadRequestException("cdi-version not provided"));
        }
        if (!supportedVersions.contains(version)) {
            throw new ServletException(new BadRequestException("cdi-version " + version + " not supported"));
        }
    }

    protected boolean versionNeeded(HttpServletRequest req) {
        return true;
    }

    private void assertThatAPIKeyCheckPasses(HttpServletRequest req) throws ServletException,
            TenantOrAppNotFoundException {
        String apiKey = req.getHeader("api-key");

        // first we try the normal API key
        String[] keys = Config.getConfig(
                new TenantIdentifier(getConnectionUriDomain(req), getAppId(req), getTenantId(req)),
                this.main).getAPIKeys();
        if (keys != null) {
            if (apiKey == null) {
                throw new ServletException(new APIKeyUnauthorisedException());
            }
            apiKey = apiKey.trim();
            boolean isAuthorised = false;
            for (String key : keys) {
                isAuthorised = isAuthorised || key.equals(apiKey);
            }
            if (isAuthorised) {
                return;
            }
        }

        // if the normal API key did not exist, or did not match the api key from the header, we try the
        // supertokens_saas_secret
        String superTokensSaaSSecret = Config.getConfig(new TenantIdentifier(null, null, null), this.main)
                .getSuperTokensSaaSSecret();
        if (superTokensSaaSSecret != null) {
            if (apiKey == null) {
                throw new ServletException(new APIKeyUnauthorisedException());
            }
            if (apiKey.equals(superTokensSaaSSecret)) {
                return;
            }
        }

        // if either were defined, and both failed, we throw an exception
        if (superTokensSaaSSecret != null || keys != null) {
            throw new ServletException(new APIKeyUnauthorisedException());
        }
    }

    protected boolean shouldProtectProtectedConfig(HttpServletRequest req) throws TenantOrAppNotFoundException {
        String apiKey = req.getHeader("api-key");
        String superTokensSaaSSecret = Config.getConfig(
                        new TenantIdentifier(null, null, null), this.main)
                .getSuperTokensSaaSSecret();

        if (superTokensSaaSSecret == null) {
            return false;
        }

        if (apiKey != null && apiKey.equals(superTokensSaaSSecret)) {
            return false;
        }

        return true;
    }

    protected boolean checkAPIKey(HttpServletRequest req) {
        return true;
    }

    private String getTenantId(HttpServletRequest req) {
        String path = req.getServletPath().toLowerCase();
        String apiPath = getPath().toLowerCase();
        if (!apiPath.startsWith("/")) {
            apiPath = "/" + apiPath;
        }
        if (apiPath.equals("/")) {
            if (path.equals("") || path.equals("/")) {
                return null;
            }
        } else {
            if (path.matches("^/appid-[a-z0-9-]*/[a-z0-9-]+" + apiPath + "/?$")) {
                String tenantId = path.split("/")[2].toLowerCase();
                if (tenantId.equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
                    return null;
                }
                return tenantId;
            } else if (path.matches("^/appid-[a-z0-9-]*" + apiPath + "/?$")) {
                return null;
            } else if (path.matches("^/[a-z0-9-]+" + apiPath + "/?$")) {
                String tenantId = path.split("/")[1].toLowerCase();
                if (tenantId.equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
                    return null;
                }
                return tenantId;
            } else {
                return null;
            }
        }
        return null;
    }

    private String getAppId(HttpServletRequest req) {
        String path = req.getServletPath().toLowerCase();
        String apiPath = getPath().toLowerCase();
        if (!apiPath.startsWith("/")) {
            apiPath = "/" + apiPath;
        }
        if (apiPath.equals("/")) {
            if (path.equals("") || path.equals("/")) {
                return null;
            }
        } else {
            if (path.matches("^/appid-[a-z0-9-]*(/[a-z0-9-]+)?" + apiPath + "/?$")) {
                String appId = path.split("/")[1].toLowerCase();
                if (appId.equals("appid-public")) {
                    return null;
                }
                return appId.substring(6);
            } else {
                return null;
            }
        }
        return null;
    }

    private String getConnectionUriDomain(HttpServletRequest req) throws ServletException {
        String connectionUriDomain = req.getServerName();
        connectionUriDomain = Utils.normalizeAndValidateConnectionUriDomain(connectionUriDomain, false);

        try {
            if (Config.getConfig(new TenantIdentifier(connectionUriDomain, null, null), main) ==
                    Config.getConfig(new TenantIdentifier(null, null, null), main)) {
                return null;
            }
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
        return connectionUriDomain;
    }

    @TestOnly
    protected TenantIdentifier getTenantIdentifierFromRequest(HttpServletRequest req) throws ServletException {
        return new TenantIdentifier(this.getConnectionUriDomain(req), this.getAppId(req), this.getTenantId(req));
    }

    protected TenantIdentifierWithStorage getTenantIdentifierWithStorageFromRequest(HttpServletRequest req)
            throws TenantOrAppNotFoundException, ServletException {
        TenantIdentifier tenantIdentifier = new TenantIdentifier(this.getConnectionUriDomain(req), this.getAppId(req),
                this.getTenantId(req));
        Storage storage = StorageLayer.getStorage(tenantIdentifier, main);
        return tenantIdentifier.withStorage(storage);
    }

    protected AppIdentifierWithStorage getAppIdentifierWithStorage(HttpServletRequest req)
            throws TenantOrAppNotFoundException, ServletException {
        TenantIdentifier tenantIdentifier = new TenantIdentifier(this.getConnectionUriDomain(req), this.getAppId(req),
                this.getTenantId(req));

        Storage storage = StorageLayer.getStorage(tenantIdentifier, main);
        Storage[] storages = StorageLayer.getStoragesForApp(main, tenantIdentifier.toAppIdentifier());

        return new AppIdentifierWithStorage(tenantIdentifier.getConnectionUriDomain(), tenantIdentifier.getAppId(),
                storage, storages);
    }

    protected AppIdentifierWithStorage getAppIdentifierWithStorageFromRequestAndEnforcePublicTenant(
            HttpServletRequest req)
            throws TenantOrAppNotFoundException, BadPermissionException, ServletException {
        TenantIdentifier tenantIdentifier = new TenantIdentifier(this.getConnectionUriDomain(req), this.getAppId(req),
                this.getTenantId(req));

        if (!tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
            throw new BadPermissionException("Only public tenantId can query across tenants");
        }

        Storage storage = StorageLayer.getStorage(tenantIdentifier, main);
        Storage[] storages = StorageLayer.getStoragesForApp(main, tenantIdentifier.toAppIdentifier());
        return new AppIdentifierWithStorage(tenantIdentifier.getConnectionUriDomain(), tenantIdentifier.getAppId(),
                storage, storages);
    }

    protected TenantIdentifierWithStorageAndUserIdMapping getTenantIdentifierWithStorageAndUserIdMappingFromRequest(
            HttpServletRequest req, String userId, UserIdType userIdType)
            throws StorageQueryException, TenantOrAppNotFoundException, UnknownUserIdException, ServletException {
        TenantIdentifier tenantIdentifier = new TenantIdentifier(this.getConnectionUriDomain(req), this.getAppId(req),
                this.getTenantId(req));
        return StorageLayer.getTenantIdentifierWithStorageAndUserIdMappingForUser(main, tenantIdentifier, userId,
                userIdType);
    }

    protected AppIdentifierWithStorageAndUserIdMapping getAppIdentifierWithStorageAndUserIdMappingFromRequest(
            HttpServletRequest req, String userId, UserIdType userIdType)
            throws StorageQueryException, TenantOrAppNotFoundException, UnknownUserIdException, ServletException {
        // This function uses storage of the tenent from which the request came from as a priorityStorage
        // while searching for the user across all storages for the app
        AppIdentifierWithStorage appIdentifierWithStorage = getAppIdentifierWithStorage(req);
        return StorageLayer.getAppIdentifierWithStorageAndUserIdMappingForUserWithPriorityForTenantStorage(main,
                appIdentifierWithStorage, appIdentifierWithStorage.getStorage(), userId, userIdType);
    }

    protected boolean checkIPAccess(HttpServletRequest req, HttpServletResponse resp)
            throws TenantOrAppNotFoundException, ServletException, IOException {
        CoreConfig config = Config.getConfig(getTenantIdentifierWithStorageFromRequest(req), main);
        String allow = config.getIpAllowRegex();
        String deny = config.getIpDenyRegex();
        if (allow == null && deny == null) {
            return true;
        }
        RemoteAddrFilter filter = new RemoteAddrFilter();
        if (allow != null) {
            try {
                filter.setAllow(allow);
            } catch (PatternSyntaxException e) {
                throw new RuntimeException("should never happen");
            }
        }
        if (deny != null) {
            try {
                filter.setDeny(deny);
            } catch (PatternSyntaxException e) {
                throw new RuntimeException("should never happen");
            }
        }
        filter.setDenyStatus(403);

        final boolean[] isAllowed = {false};
        FilterChain dummyFilterChain = new FilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response)
                    throws IOException, ServletException {
                isAllowed[0] = true;
            }
        };

        filter.doFilter(req, resp, dummyFilterChain);
        return isAllowed[0];
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {


        TenantIdentifier tenantIdentifier = null;
        try {
            try {
                tenantIdentifier = getTenantIdentifierWithStorageFromRequest(req);
            } catch (TenantOrAppNotFoundException e) {
                // we do this so that the logs that are printed out have the right "Tenant(.." info in them,
                // otherwise it will assume the base tenant (with "" CUD), which may not be the one querying
                // this API right now.
                tenantIdentifier = new TenantIdentifier(this.getConnectionUriDomain(req), this.getAppId(req),
                        this.getTenantId(req));
                throw e;
            }

            if (!this.checkIPAccess(req, resp)) {
                // IP access denied and the filter has already sent the response
                return;
            }

            if (this.checkAPIKey(req)) {
                assertThatAPIKeyCheckPasses(req);
            }

            SemVer version = getVersionFromRequest(req);

            // Check for CDI version for multitenancy
            if (version.lesserThan(SemVer.v3_0) &&
                    !tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
                sendTextResponse(404, "Not found", resp);
                return;
            }

            if (this.versionNeeded(req)) {
                assertThatVersionIsCompatible(version);
                Logging.info(main, tenantIdentifier,
                        "API called: " + req.getRequestURI() + ". Method: " + req.getMethod() + ". Version: " + version,
                        false);
            } else {
                Logging.info(main, tenantIdentifier,
                        "API called: " + req.getRequestURI() + ". Method: " + req.getMethod(), false);
            }
            super.service(req, resp);

        } catch (Exception e) {
            Logging.error(main, tenantIdentifier,
                    "API threw an exception: " + req.getMethod() + " " + req.getRequestURI(),
                    Main.isTesting, e);

            if (e instanceof QuitProgramException) {
                main.wakeUpMainThreadToShutdown();
            } else if (e instanceof TenantOrAppNotFoundException) {
                sendTextResponse(400,
                        "AppId or tenantId not found => " + ((TenantOrAppNotFoundException) e).getMessage(),
                        resp);
            } else if (e instanceof FeatureNotEnabledException) {
                sendTextResponse(402, e.getMessage(), resp);
            } else if (e instanceof BadPermissionException) {
                sendTextResponse(403, e.getMessage(), resp);
            } else if (e instanceof ServletException) {
                ServletException se = (ServletException) e;
                Throwable rootCause = se.getRootCause();
                if (rootCause instanceof BadRequestException) {
                    sendTextResponse(400, rootCause.getMessage(), resp);
                } else if (rootCause instanceof FeatureNotEnabledException) {
                    sendTextResponse(402, rootCause.getMessage(), resp);
                } else if (rootCause instanceof APIKeyUnauthorisedException) {
                    sendTextResponse(401, "Invalid API key", resp);
                } else if (rootCause instanceof TenantOrAppNotFoundException) {
                    sendTextResponse(400,
                            "AppId or tenantId not found => " + ((TenantOrAppNotFoundException) rootCause).getMessage(),
                            resp);
                } else if (rootCause instanceof BadPermissionException) {
                    sendTextResponse(403, rootCause.getMessage(), resp);
                } else {
                    sendTextResponse(500, "Internal Error", resp);
                }
            } else {
                sendTextResponse(500, "Internal Error", resp);
            }
        }
        Logging.info(main, tenantIdentifier, "API ended: " + req.getRequestURI() + ". Method: " + req.getMethod(),
                false);
    }

    protected String getRIDFromRequest(HttpServletRequest req) {
        return req.getHeader("rId");
    }

    protected SemVer getVersionFromRequest(HttpServletRequest req) throws ServletException {
        try {
            SemVer maxCDIVersion = getLatestCDIVersionForRequest(req);
            String version = req.getHeader("cdi-version");

            if (version != null) {
                SemVer versionFromRequest = new SemVer(version);

                if (versionFromRequest.greaterThan(maxCDIVersion)) {
                    throw new ServletException(
                            new BadRequestException("cdi-version " + versionFromRequest + " not supported"));
                }

                return versionFromRequest;
            }

            return maxCDIVersion;
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }

    public static class BadRequestException extends Exception {
        private static final long serialVersionUID = -5014892660208978125L;

        public BadRequestException(String msg) {
            super(msg);
        }
    }

    protected static class APIKeyUnauthorisedException extends Exception {

        private static final long serialVersionUID = 6058119187747009809L;

        public APIKeyUnauthorisedException() {
            super();
        }
    }
}
