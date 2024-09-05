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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequest;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthAPIException;
import io.supertokens.oauth.exceptions.OAuthClientNotFoundException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.exceptions.OAuth2ClientAlreadyExistsForAppException;
import io.supertokens.webserver.InputParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CreateUpdateOrGetOAuthClientAPI extends OAuthProxyBase {
    @Override
    public String getPath() {
        return "/recipe/oauth/clients";
    }

    public CreateUpdateOrGetOAuthClientAPI(Main main){
        super(main);
    }

    @Override
    public ProxyProps[] getProxyProperties(HttpServletRequest req, JsonObject input) throws ServletException {
        String clientId = "";

        if (req.getMethod().equals("GET")) {
            clientId = InputParser.getQueryParamOrThrowError(req, "clientId", false);
        } else if (req.getMethod().equals("PUT")) {
            clientId = InputParser.parseStringOrThrowError(input, "clientId", false);
        }

        return new ProxyProps[] {
            new ProxyProps(
                "GET", // apiMethod
                "GET", // method
                "/admin/clients/" + clientId, // path
                true, // proxyToAdmin
                true // camelToSnakeCaseConversion
            ),
            new ProxyProps(
                "POST", // apiMethod
                "POST_JSON", // method
                "/admin/clients", // path
                true, // proxyToAdmin
                true // camelToSnakeCaseConversion
            ),
            new ProxyProps(
                "PUT", // apiMethod
                "PUT_JSON", // method
                "/admin/clients/" + clientId, // path
                true, // proxyToAdmin
                true // camelToSnakeCaseConversion
            )
        };
    }

    @Override
    protected Map<String, String> getQueryParamsForProxy(HttpServletRequest req, JsonObject input) throws IOException, ServletException {
        Map<String, String> queryParams = new HashMap<>();

        String queryString = req.getQueryString();
        if (queryString != null) {
            String[] queryParamsParts = queryString.split("&");
            for (String queryParam : queryParamsParts) {
                String[] keyValue = queryParam.split("=");
                if (keyValue.length == 2) {
                    queryParams.put(keyValue[0], URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
                }
            }
        }

        return queryParams;
    }

    @Override
    protected JsonObject getJsonBodyForProxyPOST(HttpServletRequest req, JsonObject input)
            throws IOException, ServletException {

        // Defaults that we require
        input.addProperty("accessTokenStrategy", "jwt");
        input.addProperty("skipConsent", true);
        input.addProperty("subjectType", "public");

        return input;
    }

    @Override
    protected JsonObject getJsonBodyForProxyPUT(HttpServletRequest req, JsonObject input)
            throws IOException, ServletException {
        // fetch existing config and the apply input on top of it
        String clientId = input.get("clientId").getAsString();

        try {
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("client_id", clientId);
            HttpRequest.Response response = OAuth.handleOAuthProxyGET(
                main,
                getAppIdentifier(req),
                enforcePublicTenantAndGetPublicTenantStorage(req),
                "/admin/clients/" + clientId,
                true, queryParams, null);

            JsonObject existingConfig = response.jsonResponse;
            existingConfig = OAuth.convertSnakeCaseToCamelCaseRecursively(existingConfig);
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

        return input;
    }

    @Override
    protected void handleResponseFromProxyGET(HttpServletRequest req, HttpServletResponse resp, int statusCode,
            Map<String, List<String>> headers, String rawBody, JsonObject jsonBody)
            throws IOException, ServletException {
        this.sendJsonResponse(200, jsonBody, resp);
    }

    @Override
    protected void handleResponseFromProxyPOST(HttpServletRequest req, HttpServletResponse resp, JsonObject input, int statusCode, Map<String, List<String>> headers, String rawBody, JsonObject jsonBody) throws IOException, ServletException {
        String clientId = jsonBody.get("clientId").getAsString();

        try {
            OAuth.addClientId(main, getAppIdentifier(req), enforcePublicTenantAndGetPublicTenantStorage(req), clientId);
        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        } catch (OAuth2ClientAlreadyExistsForAppException e) {
            // ignore
        }
        this.sendJsonResponse(200, jsonBody, resp);
    }

    @Override
    protected void handleResponseFromProxyPUT(HttpServletRequest req, HttpServletResponse resp, JsonObject input, int statusCode, Map<String, List<String>> headers, String rawBody, JsonObject jsonBody) throws IOException, ServletException {
        this.sendJsonResponse(200, jsonBody, resp);
    }
}
