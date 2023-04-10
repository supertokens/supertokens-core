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
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.Utils;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class DashboardSignInAPI extends WebserverAPI {

    public DashboardSignInAPI(Main main) {
        super(main, RECIPE_ID.DASHBOARD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/dashboard/signin";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String email = InputParser.parseStringOrThrowError(input, "email", false);

        // normalize email
        email = Utils.normalizeAndValidateStringParam(email, "email");
        email = io.supertokens.utils.Utils.normaliseEmail(email);

        String password = InputParser.parseStringOrThrowError(input, "password", false);

        // normalize password
        password = Utils.normalizeAndValidateStringParam(password, "password");

        try {
            String sessionId = Dashboard.signInDashboardUser(
                    super.getAppIdentifierWithStorageFromRequestAndEnforcePublicTenant(req), main, email, password);
            if (sessionId == null) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "INVALID_CREDENTIALS_ERROR");
                super.sendJsonResponse(200, response, resp);
                return;
            }
            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.addProperty("sessionId", sessionId);
            super.sendJsonResponse(200, response, resp);
        } catch (UserSuspendedException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "USER_SUSPENDED_ERROR");
            // TODO: update message
            response.addProperty("message",
                    "User is currently suspended, please sign in with another account, or reactivate the SuperTokens " +
                            "core license key");
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }

    }
}
