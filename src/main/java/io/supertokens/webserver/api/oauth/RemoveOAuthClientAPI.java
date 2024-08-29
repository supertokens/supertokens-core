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
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.OAuth;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RemoveOAuthClientAPI extends OAuthProxyBase {
    @Override
    public String getPath() {
        return "/recipe/oauth/clients/remove";
    }

    public RemoveOAuthClientAPI(Main main){
        super(main);
    }

    @Override
    public ProxyProps[] getProxyProperties(HttpServletRequest req, JsonObject input) throws ServletException {
        String clientId = InputParser.parseStringOrThrowError(input, "clientId", false);

        return new ProxyProps[] {
            new ProxyProps(
                "POST", // apiMethod
                "DELETE_JSON", // method
                "/admin/clients/" + clientId, // path
                true, // proxyToAdmin
                true // camelToSnakeCaseConversion
            )
        };
    }

    @Override
    protected JsonObject getJsonBodyForProxyDELETE(HttpServletRequest req, JsonObject input)
            throws IOException, ServletException {

        return new JsonObject();
    }

    @Override
    protected void handleResponseFromProxyDELETE(HttpServletRequest req, HttpServletResponse resp, JsonObject input, int statusCode, Map<String, List<String>> headers, String rawBody, JsonObject jsonBody) throws IOException, ServletException {
        String clientId = InputParser.parseStringOrThrowError(input, "clientId", false);

        try {
            OAuth.removeClientId(main, getAppIdentifier(req), enforcePublicTenantAndGetPublicTenantStorage(req), clientId);
        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }

        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("status", "OK");
        this.sendJsonResponse(200, responseBody, resp);
    }
}
