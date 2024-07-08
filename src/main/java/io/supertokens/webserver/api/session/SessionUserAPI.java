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
import io.supertokens.StorageAndUserIdMapping;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.session.Session;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SessionUserAPI extends WebserverAPI {

    private static final long serialVersionUID = 3488492313129193443L;

    public SessionUserAPI(Main main) {
        super(main, RECIPE_ID.SESSION.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/session/user";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific, but ignores tenantId if fetchAcrossAllTenants is true
        String userId = InputParser.getQueryParamOrThrowError(req, "userId", false);
        assert userId != null;

        String fetchAcrossAllTenantsString = InputParser.getQueryParamOrThrowError(req, "fetchAcrossAllTenants", true);

        boolean fetchAcrossAllTenants = true;
        if (fetchAcrossAllTenantsString != null) {
            fetchAcrossAllTenants = fetchAcrossAllTenantsString.toLowerCase().equals("true");
        }

        String fetchSessionsForAllLinkedAccountsString = InputParser.getQueryParamOrThrowError(req,
                "fetchSessionsForAllLinkedAccounts", true);
        boolean fetchSessionsForAllLinkedAccounts = true;
        if (fetchSessionsForAllLinkedAccountsString != null) {
            fetchSessionsForAllLinkedAccounts = fetchSessionsForAllLinkedAccountsString.toLowerCase().equals("true");
        }

        try {
            String[] sessionHandles;

            if (fetchAcrossAllTenants) {
                AppIdentifier appIdentifier = getAppIdentifier(req);
                Storage storage;
                try {
                    StorageAndUserIdMapping storageAndUserIdMapping =
                            enforcePublicTenantAndGetStorageAndUserIdMappingForAppSpecificApi(
                                    req, userId, UserIdType.ANY, false);
                    storage = storageAndUserIdMapping.storage;
                } catch (UnknownUserIdException e) {
                    throw new IllegalStateException("should never happen");
                }
                sessionHandles = Session.getAllNonExpiredSessionHandlesForUser(
                        main, appIdentifier, storage, userId,
                        fetchSessionsForAllLinkedAccounts);
            } else {
                TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
                Storage storage = getTenantStorage(req);
                sessionHandles = Session.getAllNonExpiredSessionHandlesForUser(
                        tenantIdentifier, storage, userId, fetchSessionsForAllLinkedAccounts);
            }

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            JsonArray arr = new JsonArray();
            for (String s : sessionHandles) {
                arr.add(new JsonPrimitive(s));
            }
            result.add("sessionHandles", arr);
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }

}
