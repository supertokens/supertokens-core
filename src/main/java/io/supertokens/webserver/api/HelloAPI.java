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

package io.supertokens.webserver.api;

import io.supertokens.Main;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

// the point of this API is only to test that the server is up and running.

public class HelloAPI extends WebserverAPI {

    private static final long serialVersionUID = 1L;

    public HelloAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/hello";
    }

    @Override
    protected boolean checkAPIKey() {
        return false;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        super.sendTextResponse(200, getMessage(), resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        super.sendTextResponse(200, getMessage(), resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        super.sendTextResponse(200, getMessage(), resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        super.sendTextResponse(200, getMessage(), resp);
    }

    private String getMessage() {
        return "Hello";
    }
}
