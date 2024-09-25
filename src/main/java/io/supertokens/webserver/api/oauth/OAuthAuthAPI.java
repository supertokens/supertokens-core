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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOry;
import io.supertokens.oauth.OAuth;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OAuthAuthAPI extends WebserverAPI {
    public OAuthAuthAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/auth";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        JsonObject params = InputParser.parseJsonObjectOrThrowError(input, "params", false);
        String cookies = InputParser.parseStringOrThrowError(input, "cookies", true);

        // These optional stuff will be used in case of implicit flow
        JsonObject accessTokenUpdate = InputParser.parseJsonObjectOrThrowError(input, "access_token", true);
        JsonObject idTokenUpdate = InputParser.parseJsonObjectOrThrowError(input, "id_token", true);
        String iss = InputParser.parseStringOrThrowError(input, "iss", true);
        Boolean useStaticKeyInput = InputParser.parseBooleanOrThrowError(input, "useStaticSigningKey", true);
        boolean useDynamicKey = Boolean.FALSE.equals(useStaticKeyInput);

        Map<String, String> queryParams = params.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getAsString()
        ));

        Map<String, String> headers = new HashMap<>();

        if (cookies != null) {
            headers.put("Cookie", cookies);
        }

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            HttpRequestForOry.Response response = OAuthProxyHelper.proxyGET(
                main, req, resp,
                appIdentifier,
                storage,
                queryParams.get("client_id"), // clientIdToCheck
                "/oauth2/auth", // proxyPath
                false, // proxyToAdmin
                false, // camelToSnakeCaseConversion
                queryParams,
                headers
            );

            if (response != null) {
                if (response.headers == null || !response.headers.containsKey("Location")) {
                    throw new IllegalStateException("Invalid response from hydra");
                }
        
                String redirectTo = response.headers.get("Location").get(0);

                redirectTo = OAuth.transformTokensInAuthRedirect(main, appIdentifier, storage, redirectTo, iss, accessTokenUpdate, idTokenUpdate, useDynamicKey);
                List<String> responseCookies = response.headers.get("Set-Cookie");

                JsonObject finalResponse = new JsonObject();
                finalResponse.addProperty("redirectTo", redirectTo);

                JsonArray jsonCookies = new JsonArray();
                if (responseCookies != null) {
                    for (String cookie : responseCookies) {
                        jsonCookies.add(new JsonPrimitive(cookie));
                    }
                }
        
                finalResponse.add("cookies", jsonCookies);
                finalResponse.addProperty("status", "OK");
                
                super.sendJsonResponse(200, finalResponse, resp);
            }

        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
