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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthClientNotFoundException;
import io.supertokens.oauth.exceptions.OAuthClientRegisterInvalidInputException;
import io.supertokens.oauth.exceptions.OAuthException;
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

public class OAuthClientsAPI extends WebserverAPI {

    @Serial
    private static final long serialVersionUID = -4482427281337641246L;

    private static final List<String> REQUIRED_INPUT_FIELDS_FOR_POST = Arrays.asList(new String[]{"clientName", "scope"});
    public static final String OAUTH2_CLIENT_NOT_FOUND_ERROR = "OAUTH2_CLIENT_NOT_FOUND_ERROR";

    @Override
    public String getPath() {
        return "/recipe/oauth/clients";
    }
    public OAuthClientsAPI(Main main){
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        InputParser.collectAllMissingRequiredFieldsOrThrowError(input, REQUIRED_INPUT_FIELDS_FOR_POST);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            JsonObject response = OAuth.registerOAuthClient(super.main, appIdentifier, storage, input);
            JsonObject postResponseBody = new JsonObject();
            postResponseBody.addProperty("status", "OK");
            postResponseBody.add("client", response);
            sendJsonResponse(200, postResponseBody, resp);

        } catch (OAuthClientRegisterInvalidInputException registerException) {

            throw new ServletException(new BadRequestException(registerException.error + " - " + registerException.errorDescription));

        } catch (TenantOrAppNotFoundException | InvalidConfigException | BadPermissionException
                 | NoSuchAlgorithmException | StorageQueryException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String clientId = InputParser.getQueryParamOrThrowError(req, "clientId", false);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            JsonObject client = OAuth.loadOAuthClient(main, appIdentifier, storage, clientId);

            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.add("client", client);
            sendJsonResponse(200, response, resp);

        }  catch (OAuthClientNotFoundException e) {
            JsonObject errorResponse = createJsonFromException(e);
            errorResponse.addProperty("status", OAUTH2_CLIENT_NOT_FOUND_ERROR);
            sendJsonResponse(200, errorResponse, resp);

        } catch (TenantOrAppNotFoundException | InvalidConfigException | BadPermissionException
                 | StorageQueryException e){
            throw new ServletException(e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject requestBody = InputParser.parseJsonObjectOrThrowError(req);

        String clientId = InputParser.parseStringOrThrowError(requestBody, "clientId", false);

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            OAuth.deleteOAuthClient(main, appIdentifier, storage, clientId);
            JsonObject responseBody = new JsonObject();
            responseBody.addProperty("status", "OK");
            sendJsonResponse(200, responseBody, resp);

        }  catch (OAuthClientNotFoundException e) {
            JsonObject errorResponse = createJsonFromException(e);
            errorResponse.addProperty("status", OAUTH2_CLIENT_NOT_FOUND_ERROR);
            sendJsonResponse(200, errorResponse, resp);

        } catch (TenantOrAppNotFoundException | InvalidConfigException | BadPermissionException
                 | StorageQueryException e){
            throw new ServletException(e);
        }
    }

    private JsonObject createJsonFromException(OAuthException exception){
        JsonObject errorResponse = new JsonObject();
        errorResponse.addProperty("error", exception.error);
        errorResponse.addProperty("errorDescription", exception.errorDescription);

        return errorResponse;
    }
}
