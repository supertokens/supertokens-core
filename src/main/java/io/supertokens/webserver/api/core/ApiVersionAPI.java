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

package io.supertokens.webserver.api.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.WebserverAPI;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ApiVersionAPI extends WebserverAPI {
    private static final long serialVersionUID = -5175334869851577653L;

    public ApiVersionAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/apiversion";
    }

    @Override
    protected boolean versionNeeded(HttpServletRequest req) {
        return false;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject result = new JsonObject();
        JsonArray versions = new JsonArray();

        try {
            SemVer maxCDIVersion = getLatestCDIVersionForRequest(req);

            for (SemVer s : WebserverAPI.supportedVersions) {
                if (s.lesserThan(maxCDIVersion) || s.equals(maxCDIVersion)) {
                    versions.add(new JsonPrimitive(s.get()));
                }
            }
            result.add("versions", versions);
            super.sendJsonResponse(200, result, resp);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }
}
