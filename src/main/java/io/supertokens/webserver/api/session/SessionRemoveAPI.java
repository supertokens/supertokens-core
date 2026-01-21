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
import io.supertokens.StorageAndUserIdMapping;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        // API is tenant specific, but ignores tenantId from the request if revoking from all tenants
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
            throw new ServletException(new BadRequestException(
                    "Invalid JSON input - revokeAcrossAllTenants can only be set if userId is set"));
        }
        if (revokeAcrossAllTenants == null) {
            revokeAcrossAllTenants = true;
        }

        Boolean revokeSessionsForLinkedAccounts = InputParser.parseBooleanOrThrowError(input,
                "revokeSessionsForLinkedAccounts", true);
        if (userId == null && revokeSessionsForLinkedAccounts != null) {
            throw new ServletException(new BadRequestException(
                    "Invalid JSON input - revokeSessionsForLinkedAccounts can only be set if userId is set"));
        }
        if (revokeSessionsForLinkedAccounts == null) {
            revokeSessionsForLinkedAccounts = true;
        }

        if (userId != null) {
            Storage storage = null;
            try {
                String[] sessionHandlesRevoked;

                if (revokeAcrossAllTenants) {
                    AppIdentifier appIdentifier = getAppIdentifier(req);
                    try {
                        StorageAndUserIdMapping storageAndUserIdMapping =
                                enforcePublicTenantAndGetStorageAndUserIdMappingForAppSpecificApi(
                                        req, userId, UserIdType.ANY, false);
                        storage = storageAndUserIdMapping.storage;
                    } catch (UnknownUserIdException e) {
                        throw new IllegalStateException("should never happen");
                    }
                    sessionHandlesRevoked = Session.revokeAllSessionsForUser(
                            main, appIdentifier, storage, userId, revokeSessionsForLinkedAccounts);
                } else {
                    TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
                    storage = getTenantStorage(req);

                    sessionHandlesRevoked = Session.revokeAllSessionsForUser(
                            main, tenantIdentifier, storage, userId, revokeSessionsForLinkedAccounts);
                }

                if (storage.getType() == STORAGE_TYPE.SQL) {
                    try {
                        AppIdentifier appIdentifier = getAppIdentifier(req);
                        UserIdMapping userIdMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                                appIdentifier, storage, userId, UserIdType.ANY);
                        if (userIdMapping != null) {
                            ActiveUsers.updateLastActive(appIdentifier, main,
                                    userIdMapping.superTokensUserId);
                        } else {
                            ActiveUsers.updateLastActive(appIdentifier, main, userId);
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
            } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
                throw new ServletException(e);
            }
        } else {
            try {
                AppIdentifier appIdentifier = getAppIdentifier(req);
                Map<String, List<String>> sessionHandlesByTenantId = new HashMap<>();

                for (String sessionHandle : sessionHandles) {
                    String tenantId = Session.getTenantIdFromSessionHandle(sessionHandle);
                    if (!sessionHandlesByTenantId.containsKey(tenantId)) {
                        sessionHandlesByTenantId.put(tenantId, new ArrayList<>());
                    }
                    sessionHandlesByTenantId.get(tenantId).add(sessionHandle);
                }
                List<String> allSessionHandlesRevoked = new ArrayList<>();
                for (Map.Entry<String, List<String>> entry : sessionHandlesByTenantId.entrySet()) {
                    String tenantId = entry.getKey();
                    List<String> sessionHandlesForTenant = entry.getValue();
                    Storage storage = StorageLayer.getStorage(
                            new TenantIdentifier(
                                    appIdentifier.getConnectionUriDomain(), appIdentifier.getAppId(), tenantId),
                            main);

                    String[] sessionHandlesRevoked = Session.revokeSessionUsingSessionHandles(main,
                            appIdentifier, storage, sessionHandlesForTenant.toArray(new String[0]));
                    allSessionHandlesRevoked.addAll(List.of(sessionHandlesRevoked));
                }

                JsonObject result = new JsonObject();
                result.addProperty("status", "OK");
                JsonArray sessionHandlesRevokedJSON = new JsonArray();
                for (String sessionHandle : allSessionHandlesRevoked) {
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
