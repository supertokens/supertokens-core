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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PathRouter extends WebserverAPI {
    private static final long serialVersionUID = -3199188474453935983L;

    List<WebserverAPI> apis;

    public PathRouter(Main main) {
        super(main, "");
        this.apis = new ArrayList();
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
        getAPIThatMatchesPath(req).service(req, resp);
    }
}
