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

public class RecipeRouter extends WebserverAPI {
    private static final long serialVersionUID = -3199188474453935983L;

    WebserverAPI[] apis;

    public RecipeRouter(Main main, WebserverAPI... apis) throws Exception {
        super(main, "");
        this.apis = apis;

        if (this.apis.length == 0) {
            throw new Exception("Router has no APIs to route to");
        }

        String path = this.apis[0].getPath();

        for (WebserverAPI api : this.apis) {
            if (!api.getPath().equals(path)) {
                throw new Exception("All APIs given to router do not have the same path");
            }
        }
    }

    @Override
    public String getPath() {
        return this.apis[0].getPath();
    }

    @Override
    protected boolean versionNeeded(HttpServletRequest req) {
        return getAPIThatMatchesRID(req).versionNeeded(req);
    }

    @Override
    protected boolean checkAPIKey(HttpServletRequest req) {
        return getAPIThatMatchesRID(req).checkAPIKey(req);
    }

    private WebserverAPI getAPIThatMatchesRID(HttpServletRequest req) {
        String rid = super.getRIDFromRequest(req);
        for (WebserverAPI api : this.apis) {
            if (api.getRID().equals(rid)) {
                return api;
            }
        }
        return apis[0];
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        getAPIThatMatchesRID(req).doGet(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        getAPIThatMatchesRID(req).doPost(req, resp);
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        getAPIThatMatchesRID(req).doHead(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        getAPIThatMatchesRID(req).doPut(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        getAPIThatMatchesRID(req).doDelete(req, resp);
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        getAPIThatMatchesRID(req).doOptions(req, resp);
    }

    @Override
    protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        getAPIThatMatchesRID(req).doTrace(req, resp);
    }
}
