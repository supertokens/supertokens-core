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
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthAPIException;
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
import java.io.Serial;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class OAuthTokenAPI extends WebserverAPI {
    @Serial
    private static final long serialVersionUID = -8734479943734920904L;

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
        String iss = InputParser.parseStringOrThrowError(input, "iss", false);

        boolean useDynamicKey = false;
        Boolean useStaticKeyInput = InputParser.parseBooleanOrThrowError(input, "useStaticSigningKey", true);
        // useStaticKeyInput defaults to true, so we check if it has been explicitly set to false
        useDynamicKey = Boolean.FALSE.equals(useStaticKeyInput);

        JsonObject bodyFromSDK = InputParser.parseJsonObjectOrThrowError(input, "body", false);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            JsonObject response = OAuth.getToken(super.main, appIdentifier, storage,
                bodyFromSDK, iss, useDynamicKey);

            response.addProperty("status", "OK");
            super.sendJsonResponse(200, response, resp);

        } catch (OAuthAPIException authException) {

            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("error", authException.error);
            errorResponse.addProperty("error_description", authException.errorDescription);
            errorResponse.addProperty("status_code", authException.statusCode);
            errorResponse.addProperty("status", "OAUTH2_TOKEN_ERROR");
            super.sendJsonResponse(200, errorResponse, resp);

        } catch (TenantOrAppNotFoundException | InvalidConfigException | BadPermissionException | 
                StorageQueryException | InvalidKeyException | NoSuchAlgorithmException | 
                InvalidKeySpecException | JWTCreationException | JWTException | StorageTransactionLogicException | 
                UnsupportedJWTSigningAlgorithmException | FeatureNotEnabledException e) {
            throw new ServletException(e);
        }
    }
}
