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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthClientRegisterException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serial;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class OAuthRegisterClientAPI extends WebserverAPI {

    @Serial
    private static final long serialVersionUID = -4482427281337641246L;

    private static final List<String> ALLOWED_INPUT_FIELDS = Arrays.asList(new String[]{"clientName","scope", "redirectUris", "allowedCorsOrigins", "authorizationCodeGrantAccessTokenLifespan", "authorizationCodeGrantIdTokenLifespan", "authorizationCodeGrantRefreshTokenLifespan",
        "clientCredentialsGrantAccessTokenLifespan","implicitGrantAccessTokenLifespan","implicitGrantIdTokenLifespan","refreshTokenGrantAccessTokenLifespan","refreshTokenGrantIdTokenLifespan","refreshTokenGrantRefreshTokenLifespan","tokenEndpointAuthMethod","audience",
        "grantTypes","responseTypes","clientUri","logoUri","policyUri","tosUri","metadata"});
    private static final List<String> REQUIRED_INPUT_FIELDS = Arrays.asList(new String[]{"clientName", "scope"});

    @Override
    public String getPath() {
        return "/recipe/oauth/registerclient";
    }
    public OAuthRegisterClientAPI(Main main){
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        boolean containsAllRequired = containsAllRequiredFields(input);
        boolean containsMoreThanAllowed = containsMoreThanAllowed(input);
        if(!containsAllRequired || containsMoreThanAllowed){
            throw new ServletException(new WebserverAPI.BadRequestException("Invalid Json Input"));
        }

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            JsonObject response = OAuth.registerOAuthClient(super.main, appIdentifier, storage, input);
            sendJsonResponse(200, response, resp);

        } catch (OAuthClientRegisterException registerException) {

            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("error", registerException.error);
            errorResponse.addProperty("error_description", registerException.errorDescription);

            sendJsonResponse(400, errorResponse, resp);

        } catch (TenantOrAppNotFoundException | InvalidConfigException | BadPermissionException
                 | NoSuchAlgorithmException | StorageQueryException e) {
            throw new ServletException(e);
        }
    }

    private boolean containsAllRequiredFields(JsonObject input){
        boolean foundMissing = false;
        for(String requiredField : OAuthRegisterClientAPI.REQUIRED_INPUT_FIELDS){
            if(input.get(requiredField) == null || input.get(requiredField).isJsonNull() ||
                    input.get(requiredField).getAsString().isEmpty()){
                foundMissing = true;
                break;
            }
        }
        return  !foundMissing;
    }

    private boolean containsMoreThanAllowed(JsonObject input) {
        boolean containsMore = false;
        for(Map.Entry<String, JsonElement> jsonEntry : input.entrySet()){
            if(!OAuthRegisterClientAPI.ALLOWED_INPUT_FIELDS.contains(jsonEntry.getKey())){
                containsMore = true;
                break;
            }
        }
        return containsMore;
    }
}
