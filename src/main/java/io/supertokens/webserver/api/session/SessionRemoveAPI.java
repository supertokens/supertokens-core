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

package io.supertokens.webserver.api.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.Main;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.session.Session;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SessionRemoveAPI extends WebserverAPI {
    private static final long serialVersionUID = -2082970815993229316L;

    public SessionRemoveAPI(Main main) {
        super(main, RECIPE_ID.SESSION.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/session/remove";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = InputParser.parseStringOrThrowError(input, "userId", true);

        JsonArray arr = InputParser.parseArrayOrThrowError(input, "sessionHandles", true);
        String[] sessionHandles = null;
        if (arr != null) {
            sessionHandles = new String[arr.size()];
            for (int i = 0; i < sessionHandles.length; i++) {
                String session = InputParser.parseStringFromElementOrThrowError(arr.get(i), "sessionHandles", false);
                sessionHandles[i] = session;
            }
        }

        int numberOfNullItems = 0;
        if (userId != null) {
            numberOfNullItems++;
        }
        if (sessionHandles != null) {
            numberOfNullItems++;
        }
        if (numberOfNullItems == 0 || numberOfNullItems > 1) {
            throw new ServletException(
                    new BadRequestException("Invalid JSON input - use one of userId or sessionHandles array"));
        }

        if (userId != null) {
            try {
                String[] sessionHandlesRevoked = Session.revokeAllSessionsForUser(this.getTenantIdentifierWithStorageFromRequest(req), main,
                        userId);
                JsonObject result = new JsonObject();
                result.addProperty("status", "OK");
                JsonArray sessionHandlesRevokedJSON = new JsonArray();
                for (String sessionHandle : sessionHandlesRevoked) {
                    sessionHandlesRevokedJSON.add(new JsonPrimitive(sessionHandle));
                }
                result.add("sessionHandlesRevoked", sessionHandlesRevokedJSON);
                super.sendJsonResponse(200, result, resp);
            } catch (StorageQueryException | TenantOrAppNotFoundException e) {
                throw new ServletException(e);
            }
        } else {
            try {
                String[] sessionHandlesRevoked = Session.revokeSessionUsingSessionHandles(
                        this.getTenantIdentifierWithStorageFromRequest(req), main, sessionHandles);
                JsonObject result = new JsonObject();
                result.addProperty("status", "OK");
                JsonArray sessionHandlesRevokedJSON = new JsonArray();
                for (String sessionHandle : sessionHandlesRevoked) {
                    sessionHandlesRevokedJSON.add(new JsonPrimitive(sessionHandle));
                }
                result.add("sessionHandlesRevoked", sessionHandlesRevokedJSON);
                super.sendJsonResponse(200, result, resp);
            } catch (StorageQueryException | TenantOrAppNotFoundException e) {
                throw new ServletException(e);
            }
        }
    }
}
