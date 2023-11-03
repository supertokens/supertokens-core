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
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.RequestStats;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;

public class RequestStatsAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public RequestStatsAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/requests/stats";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        try {
            AppIdentifier appIdentifier = getAppIdentifierWithStorageFromRequestAndEnforcePublicTenant(req);
            JsonObject stats = RequestStats.getInstance(main, appIdentifier).getStats();
            stats.addProperty("status", "OK");
            super.sendJsonResponse(200, stats, resp);

        } catch (BadPermissionException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }
}
