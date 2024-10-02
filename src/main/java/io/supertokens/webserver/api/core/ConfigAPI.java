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

package io.supertokens.webserver.api.core;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.cliOptions.CLIOptions;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;

public class ConfigAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public ConfigAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/config";
    }

    @Override
    protected boolean checkAPIKey(HttpServletRequest req) {
        return false;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String pid = InputParser.getQueryParamOrThrowError(req, "pid", false);

        TenantIdentifier tenantIdentifier = null;
        try {
            tenantIdentifier = getTenantIdentifier(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
        if (!tenantIdentifier.equals(new TenantIdentifier(null, null, null))) {
            throw new ServletException(new BadPermissionException(
                    "you can call this only from the base connection uri domain, public app and tenant"));
        }

        if ((ProcessHandle.current().pid() + "").equals(pid)) {
            String path = CLIOptions.get(main).getConfigFilePath() == null
                    ? CLIOptions.get(main).getInstallationPath() + "config.yaml"
                    : CLIOptions.get(main).getConfigFilePath();
            File f = new File(path);
            path = f.getAbsolutePath();
            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("path", path);
            super.sendJsonResponse(200, result, resp);
        } else {
            JsonObject result = new JsonObject();
            result.addProperty("status", "NOT_ALLOWED");
            super.sendJsonResponse(200, result, resp);
        }
    }
}
