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
import jakarta.servlet.ServletException;
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
        this.apis.add(newApi);
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
        String path = req.getServletPath();
        for (WebserverAPI api : this.apis) {
            if (api.getPath().equals(path) || (api.getPath() + "/").equals(path)) {
                return api;
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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        getAPIThatMatchesPath(req).doGet(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        getAPIThatMatchesPath(req).doPost(req, resp);
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        getAPIThatMatchesPath(req).doHead(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        getAPIThatMatchesPath(req).doPut(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        getAPIThatMatchesPath(req).doDelete(req, resp);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        getAPIThatMatchesPath(req).doOptions(req, resp);
    }

    @Override
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        getAPIThatMatchesPath(req).doTrace(req, resp);
    }
}
