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

import com.google.gson.*;
import io.supertokens.Main;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthAuthException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.oauth.OAuthAuthResponse;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serial;
import java.util.Arrays;
import java.util.List;

public class OAuthAuthAPI extends WebserverAPI {
    @Serial
    private static final long serialVersionUID = -8734479943734920904L;

    public OAuthAuthAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    private static final List<String> REQUIRED_FIELDS_FOR_POST = Arrays.asList(new String[]{"clientId", "responseType"});

    @Override
    public String getPath() {
        return "/recipe/oauth/auth";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        InputParser.collectAllMissingRequiredFieldsOrThrowError(input, REQUIRED_FIELDS_FOR_POST);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            OAuthAuthResponse authResponse = OAuth.getAuthorizationUrl(super.main, appIdentifier, storage,
                    input);
            JsonObject response = new JsonObject();
            response.addProperty("redirectTo", authResponse.redirectTo);

            JsonArray jsonCookies = new JsonArray();
            if (authResponse.cookies != null) {
                for(String cookie : authResponse.cookies){
                    jsonCookies.add(new JsonPrimitive(cookie));
                }
            }
            response.add("cookies", jsonCookies);
            response.addProperty("status", "OK");
            super.sendJsonResponse(200, response, resp);

        } catch (OAuthAuthException authException) {

            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("error", authException.error);
            errorResponse.addProperty("errorDescription", authException.errorDescription);
            errorResponse.addProperty("status", "OAUTH2_AUTH_ERROR");
            super.sendJsonResponse(200, errorResponse, resp);

        } catch (TenantOrAppNotFoundException | InvalidConfigException | HttpResponseException |
                 StorageQueryException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
