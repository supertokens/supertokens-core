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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.cronjobs.telemetry.Telemetry;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.KeyValueInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class TelemetryAPI extends WebserverAPI {
    private static final long serialVersionUID = -5175334869851577653L;

    public TelemetryAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/telemetry";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        try {
            KeyValueInfo telemetryId = Telemetry.getTelemetryId(main,
                    this.getAppIdentifierWithStorageFromRequestAndEnforcePublicTenant(req));

            JsonObject result = new JsonObject();
            if (telemetryId == null) {
                result.addProperty("exists", false);
            } else {
                result.addProperty("exists", true);
                result.addProperty("telemetryId", telemetryId.value);
            }
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
