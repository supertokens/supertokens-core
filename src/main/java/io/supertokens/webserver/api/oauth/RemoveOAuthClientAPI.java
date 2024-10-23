/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.oauth;

import java.io.IOException;
import java.util.HashMap;


import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOAuthProvider;
import io.supertokens.oauth.OAuth;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RemoveOAuthClientAPI extends WebserverAPI {

    public RemoveOAuthClientAPI(Main main){
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/clients/remove";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String clientId = InputParser.parseStringOrThrowError(input, "clientId", false);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            try {
                boolean didExist = OAuth.removeClient(main, getAppIdentifier(req), enforcePublicTenantAndGetPublicTenantStorage(req), clientId);
                if (!didExist) {
                    JsonObject response = new JsonObject();
                    response.addProperty("status", "OK");
                    response.addProperty("didExist", false);
                    super.sendJsonResponse(200, response, resp);
                    return;
                }
            } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
                throw new ServletException(e);
            }


            HttpRequestForOAuthProvider.Response response = OAuthProxyHelper.proxyJsonDELETE(
                main, req, resp,
                appIdentifier,
                storage,
                null, // clientIdToCheck
                "/admin/clients/" + clientId, // proxyPath
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                new HashMap<>(), // queryParams
                new JsonObject(), // jsonBody
                new HashMap<>() // headers
            );

            if (response != null) {
                JsonObject finalResponse = new JsonObject();
                finalResponse.addProperty("status", "OK");
                finalResponse.addProperty("didExist", true);

                super.sendJsonResponse(200, finalResponse, resp);
            }

        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
