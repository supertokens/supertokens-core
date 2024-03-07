/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.dashboard;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.dashboard.exceptions.UserSuspendedException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.Utils;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serial;

public class VerifyDashboardUserSessionAPI extends WebserverAPI {

    @Serial
    private static final long serialVersionUID = -3243992629116144574L;

    public VerifyDashboardUserSessionAPI(Main main) {
        super(main, RECIPE_ID.DASHBOARD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/dashboard/session/verify";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String sessionId = InputParser.parseStringOrThrowError(input, "sessionId", false);

        sessionId = Utils.normalizeAndValidateStringParam(sessionId, "sessionId");
        try {
            JsonObject invalidSessionResp = new JsonObject();
            invalidSessionResp.addProperty("status", "INVALID_SESSION_ERROR");
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = super.enforcePublicTenantAndGetPublicTenantStorage(req);

            if (Dashboard.isValidUserSession(appIdentifier, storage, main, sessionId)) {
                String email = Dashboard.getEmailFromSessionId(appIdentifier, storage, sessionId);

                if (email == null) {
                    super.sendJsonResponse(200, invalidSessionResp, resp);
                } else {
                    JsonObject response = new JsonObject();
                    response.addProperty("status", "OK");

                    SemVer cdiVersion = getVersionFromRequest(req);

                    // We only add email for CDI version 2.22 and above
                    if (cdiVersion.greaterThanOrEqualTo(SemVer.v3_0)) {
                        response.addProperty("email", email);
                    }

                    super.sendJsonResponse(200, response, resp);
                }
            } else {
                super.sendJsonResponse(200, invalidSessionResp, resp);
            }

        } catch (UserSuspendedException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "USER_SUSPENDED_ERROR");
            response.addProperty("message",
                    "User is suspended.");
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }

    }
}
