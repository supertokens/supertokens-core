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
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.AppIdentifierStorageAndUserIdMapping;
import io.supertokens.useridmapping.TenantIdentifierStorageAndUserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public abstract class WebserverAPI extends HttpServlet {

    private static final long serialVersionUID = 1L;
    protected final Main main;
    public static final Set<String> supportedVersions = new HashSet<>();
    private String rid;

    static {
        supportedVersions.add("2.7");
        supportedVersions.add("2.8");
        supportedVersions.add("2.9");
        supportedVersions.add("2.10");
        supportedVersions.add("2.11");
        supportedVersions.add("2.12");
        supportedVersions.add("2.13");
        supportedVersions.add("2.14");
        supportedVersions.add("2.15");
        supportedVersions.add("2.16");
        supportedVersions.add("2.17");
        supportedVersions.add("2.18");
    }

    public static String getLatestCDIVersion() {
        return "2.18";
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

    private void assertThatVersionIsCompatible(String version) throws ServletException {
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
        String[] keys = Config.getConfig(getTenantIdentifier(req), this.main).getAPIKeys();
        if (keys != null) {
            if (apiKey == null) {
                throw new ServletException(new APIKeyUnauthorisedException());
            }
            apiKey = apiKey.trim();
            boolean isAuthorised = false;
            for (String key : keys) {
                isAuthorised = isAuthorised || key.equals(apiKey);
            }
            if (!isAuthorised) {
                throw new ServletException(new APIKeyUnauthorisedException());
            }
        }
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

    private String getConnectionUriDomain(HttpServletRequest req) {
        String connectionUriDomain = req.getServerName() + ":" + req.getServerPort();
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

    protected TenantIdentifier getTenantIdentifier(HttpServletRequest req) {
        TenantIdentifier tenantIdentifier = new TenantIdentifier(this.getConnectionUriDomain(req), this.getAppId(req), this.getTenantId(req));
        try {
            Storage storage = StorageLayer.getStorage(tenantIdentifier, main);
            tenantIdentifier = tenantIdentifier.withStorage(storage);
        } catch (TenantOrAppNotFoundException e) {
            // TODO ignore only for now, this function should throw this exception
        }
        return tenantIdentifier;
    }

    protected TenantIdentifierStorageAndUserIdMapping getTenantIdentifierStorageAndUserIdMappingFromRequest(HttpServletRequest req, String userId, UserIdType userIdType)
            throws StorageQueryException, TenantOrAppNotFoundException, UnknownUserIdException {
        TenantIdentifier tenantIdentifier = new TenantIdentifier(this.getConnectionUriDomain(req), this.getAppId(req), this.getTenantId(req));
        return StorageLayer.getTenantIdentifierStorageAndUserIdMappingForUser(main, tenantIdentifier, userId, userIdType);
    }

    protected AppIdentifierStorageAndUserIdMapping getAppIdentifierStorageAndUserIdMapping(HttpServletRequest req, String userId, UserIdType userIdType)
            throws StorageQueryException, TenantOrAppNotFoundException, UnknownUserIdException {
        AppIdentifier appIdentifier = new AppIdentifier(this.getConnectionUriDomain(req), this.getAppId(req));
        return StorageLayer.getAppIdentifierStorageAndUserIdMappingForUser(main, appIdentifier, userId, userIdType);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            if (this.checkAPIKey(req)) {
                assertThatAPIKeyCheckPasses(req);
            }
            if (this.versionNeeded(req)) {
                String version = getVersionFromRequest(req);
                assertThatVersionIsCompatible(version);
                Logging.info(main,
                        "API called: " + req.getRequestURI() + ". Method: " + req.getMethod() + ". Version: " + version,
                        false);
            } else {
                Logging.info(main, "API called: " + req.getRequestURI() + ". Method: " + req.getMethod(), false);
            }
            super.service(req, resp);
        } catch (Exception e) {
            Logging.error(main, "API threw an exception: " + req.getMethod() + " " + req.getRequestURI(),
                    Main.isTesting, e);

            if (e instanceof QuitProgramException) {
                main.wakeUpMainThreadToShutdown();
            } else if (e instanceof TenantOrAppNotFoundException) {
                sendTextResponse(400,
                        "AppId or tenantId not found => appId: " +
                                ((TenantOrAppNotFoundException) e).getTenantIdentifier().getAppId() + ", tenantId: " +
                                ((TenantOrAppNotFoundException) e).getTenantIdentifier().getTenantId(), resp);
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
                            "AppId or tenantId not found => appId: " +
                                    ((TenantOrAppNotFoundException) rootCause).getTenantIdentifier().getAppId() +
                                    ", tenantId: " +
                                    ((TenantOrAppNotFoundException) rootCause).getTenantIdentifier().getTenantId(),
                            resp);
                } else if (rootCause instanceof BadPermissionException) {
                    sendTextResponse(403, e.getMessage(), resp);
                } else {
                    sendTextResponse(500, "Internal Error", resp);
                }
            } else {
                sendTextResponse(500, "Internal Error", resp);
            }
        }
        Logging.info(main, "API ended: " + req.getRequestURI() + ". Method: " + req.getMethod(), false);
    }

    protected String getRIDFromRequest(HttpServletRequest req) {
        return req.getHeader("rId");
    }

    protected String getVersionFromRequest(HttpServletRequest req) {
        String version = req.getHeader("cdi-version");
        if (version == null) {
            version = getLatestCDIVersion();
        }
        return version;
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
