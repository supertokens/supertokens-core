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
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOry;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthAPIException;
import io.supertokens.oauth.exceptions.OAuthClientNotFoundException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.exceptions.OAuth2ClientAlreadyExistsForAppException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CreateUpdateOrGetOAuthClientAPI extends WebserverAPI {
    @Override
    public String getPath() {
        return "/recipe/oauth/clients";
    }

    public CreateUpdateOrGetOAuthClientAPI(Main main){
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String clientId = InputParser.getQueryParamOrThrowError(req, "clientId", false);
        
        try {
            OAuthProxyHelper.proxyGET(
                main, req, resp,
                getAppIdentifier(req),
                enforcePublicTenantAndGetPublicTenantStorage(req),
                clientId, // clientIdToCheck
                "/admin/clients/" + clientId, // proxyPath
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                OAuthProxyHelper.defaultGetQueryParamsFromRequest(req),
                new HashMap<>(), // getHeadersForProxy
                (statusCode, headers, rawBody, jsonBody) -> { // getJsonResponse
                    return jsonBody.getAsJsonObject();
                }
            );
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        // Defaults that we require
        input.addProperty("accessTokenStrategy", "jwt");
        input.addProperty("skipConsent", true);
        input.addProperty("subjectType", "public");

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            input.addProperty("owner", appIdentifier.getAppId());

            OAuthProxyHelper.proxyJsonPOST(
                main, req, resp, 
                appIdentifier,
                storage,
                null, // clientIdToCheck
                "/admin/clients", // proxyPath
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                input, // jsonBody
                new HashMap<>(), // headers
                (statusCode, headers, rawBody, jsonBody) -> { // getJsonResponse
                    String clientId = jsonBody.getAsJsonObject().get("clientId").getAsString();

                    try {
                        OAuth.addClientId(main, getAppIdentifier(req), enforcePublicTenantAndGetPublicTenantStorage(req), clientId);
                    } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
                        throw new ServletException(e);
                    } catch (OAuth2ClientAlreadyExistsForAppException e) {
                        // ignore
                    }
                    return jsonBody.getAsJsonObject();
                }
            );
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String clientId = InputParser.parseStringOrThrowError(input, "clientId", false);

        // Apply existing client config on top of input
        try {
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("clientId", clientId);
            HttpRequestForOry.Response response = OAuth.doOAuthProxyGET(
                    main,
                    getAppIdentifier(req),
                    enforcePublicTenantAndGetPublicTenantStorage(req),
                    clientId,
                    "/admin/clients/" + clientId,
                    true, true, queryParams, null);

            JsonObject existingConfig = response.jsonResponse.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : existingConfig.entrySet()) {
                String key = entry.getKey();
                if (!input.has(key)) {
                    input.add(key, entry.getValue());
                }
            }
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException | InvalidConfigException | BadPermissionException e) {
            throw new ServletException(e);
        } catch (OAuthClientNotFoundException | OAuthAPIException e) {
            // ignore since the PUT API will throw one of this error later on
        }

        try {
            OAuthProxyHelper.proxyJsonPUT(
                main, req, resp,
                getAppIdentifier(req),
                enforcePublicTenantAndGetPublicTenantStorage(req),
                clientId, // clientIdToCheck
                "/admin/clients/" + clientId,
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                new HashMap<>(), // queryParams
                input, // jsonBody
                new HashMap<>(), // headers
                (statusCode, headers, rawBody, jsonBody) -> { // getJsonResponse
                    return jsonBody.getAsJsonObject();
                }
            );
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
