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
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.session.Session;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdType;
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
        // API is tenant specific. also operates on all tenants in an app if `revokeAcrossAllTenants` is set to true
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

        Boolean revokeAcrossAllTenants = InputParser.parseBooleanOrThrowError(input, "revokeAcrossAllTenants", true);
        if (userId == null && revokeAcrossAllTenants != null) {
            throw new ServletException(new BadRequestException("Invalid JSON input - revokeAcrossAllTenants can only be set if userId is set"));
        }

        if (revokeAcrossAllTenants == null) {
            revokeAcrossAllTenants = true;
        }

        if (userId != null) {
            try {
                String[] sessionHandlesRevoked;
                if (revokeAcrossAllTenants) {
                    sessionHandlesRevoked = Session.revokeAllSessionsForUser(
                            main, this.getAppIdentifierWithStorage(req), userId);
                } else {
                    sessionHandlesRevoked = Session.revokeAllSessionsForUser(
                            main, this.getTenantIdentifierWithStorageFromRequest(req), userId);
                }

                if (StorageLayer.getStorage(this.getTenantIdentifierWithStorageFromRequest(req), main).getType() ==
                        STORAGE_TYPE.SQL) {
                    try {
                        UserIdMapping userIdMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                                this.getAppIdentifierWithStorage(req),
                                userId, UserIdType.ANY);
                        if (userIdMapping != null) {
                            ActiveUsers.updateLastActive(this.getAppIdentifierWithStorage(req), main,
                                    userIdMapping.superTokensUserId);
                        } else {
                            ActiveUsers.updateLastActive(this.getAppIdentifierWithStorage(req), main, userId);
                        }
                    } catch (StorageQueryException ignored) {
                    }
                }
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
                String[] sessionHandlesRevoked = Session.revokeSessionUsingSessionHandles(main,
                        this.getAppIdentifierWithStorage(req), sessionHandles);
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
