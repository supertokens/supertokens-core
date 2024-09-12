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

import com.auth0.jwt.exceptions.JWTCreationException;
import com.google.gson.*;
import io.supertokens.Main;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.OAuth;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.session.jwt.JWT.JWTException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;

public class OAuthTokenAPI extends WebserverAPI {

    public OAuthTokenAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/token";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String iss = InputParser.parseStringOrThrowError(input, "iss", false); // input validation
        JsonObject bodyFromSDK = InputParser.parseJsonObjectOrThrowError(input, "inputBody", false);

        Boolean useStaticKeyInput = InputParser.parseBooleanOrThrowError(input, "useStaticSigningKey", true);

        Map<String, String> formFields = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : bodyFromSDK.entrySet()) {
            formFields.put(entry.getKey(), entry.getValue().getAsString());
        }

        try {
            OAuthProxyHelper.proxyFormPOST(
                main, req, resp,
                getAppIdentifier(req),
                enforcePublicTenantAndGetPublicTenantStorage(req),
                "/oauth2/token", // proxyPath
                false, // proxyToAdmin
                false, // camelToSnakeCaseConversion
                formFields,
                new HashMap<>(), // headers
                (statusCode, headers, rawBody, jsonBody) -> {
                    if (jsonBody == null) {
                        throw new IllegalStateException("unexpected response from hydra");
                    }

                    try {
                        AppIdentifier appIdentifier = getAppIdentifier(req);
                        Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

                        JsonObject accessTokenUpdate = InputParser.parseJsonObjectOrThrowError(input, "access_token", true);
                        JsonObject idTokenUpdate = InputParser.parseJsonObjectOrThrowError(input, "id_token", true);

                        // useStaticKeyInput defaults to true, so we check if it has been explicitly set to false
                        boolean useDynamicKey = Boolean.FALSE.equals(useStaticKeyInput);
                        jsonBody = OAuth.transformTokens(super.main, appIdentifier, storage, jsonBody.getAsJsonObject(), iss, accessTokenUpdate, idTokenUpdate, useDynamicKey);
            
                    } catch (IOException | InvalidConfigException | TenantOrAppNotFoundException | BadPermissionException | StorageQueryException | InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException | JWTCreationException | JWTException | StorageTransactionLogicException | UnsupportedJWTSigningAlgorithmException e) {
                        throw new ServletException(e);
                    }

                    jsonBody.getAsJsonObject().addProperty("status", "OK");
                    super.sendJsonResponse(200, jsonBody, resp);
                }
            );
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
