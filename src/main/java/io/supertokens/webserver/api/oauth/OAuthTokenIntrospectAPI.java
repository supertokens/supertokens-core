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
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOAuthProvider;
import io.supertokens.oauth.OAuth;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class OAuthTokenIntrospectAPI extends WebserverAPI {

    public OAuthTokenIntrospectAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/introspect";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String token = InputParser.parseStringOrThrowError(input, "token", false);

        if (token.startsWith("st_rt_")) {
            Map<String, String> formFields = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : input.entrySet()) {
                formFields.put(entry.getKey(), entry.getValue().getAsString());
            }

            try {
                AppIdentifier appIdentifier = getAppIdentifier(req);
                Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

                token = OAuth.getOAuthProviderRefreshToken(main, appIdentifier, storage, token);
                formFields.put("token", token);

                HttpRequestForOAuthProvider.Response response = OAuthProxyHelper.proxyFormPOST(
                    main, req, resp,
                    appIdentifier,
                    storage,
                    null, // clientIdToCheck
                    "/admin/oauth2/introspect", // pathProxy
                    true, // proxyToAdmin
                    false, // camelToSnakeCaseConversion
                    formFields,
                    new HashMap<>() // headers
                );

                if (response != null) {
                    JsonObject finalResponse = response.jsonResponse.getAsJsonObject();

                    try {
                        OAuth.verifyAndUpdateIntrospectRefreshTokenPayload(main, appIdentifier, storage, finalResponse, token);
                    } catch (StorageQueryException | TenantOrAppNotFoundException |
                                FeatureNotEnabledException | InvalidConfigException e) {
                        throw new ServletException(e);
                    }

                    finalResponse.addProperty("status", "OK");
                    super.sendJsonResponse(200, finalResponse, resp);
                }
            } catch (IOException | StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
                throw new ServletException(e);
            }
        } else {
            try {
                AppIdentifier appIdentifier = getAppIdentifier(req);
                Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);
                JsonObject response = OAuth.introspectAccessToken(main, appIdentifier, storage, token);
                response.addProperty("status", "OK");
                super.sendJsonResponse(200, response, resp);

            } catch (IOException | TenantOrAppNotFoundException | BadPermissionException | StorageQueryException | StorageTransactionLogicException | UnsupportedJWTSigningAlgorithmException e) {
                throw new ServletException(e);
            }
        }
    }
}
