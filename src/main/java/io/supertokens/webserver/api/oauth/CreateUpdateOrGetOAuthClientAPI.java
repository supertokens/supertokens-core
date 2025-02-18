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
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOAuthProvider;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.Transformations;
import io.supertokens.oauth.exceptions.OAuthAPIException;

import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.OAuthClient;
import io.supertokens.pluginInterface.oauth.exception.OAuthClientNotFoundException;
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
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);
            HttpRequestForOAuthProvider.Response response = OAuthProxyHelper.proxyGET(
                main, req, resp,
                appIdentifier,
                storage,
                null, // clientIdToCheck
                "/admin/clients/" + clientId, // proxyPath
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                OAuthProxyHelper.defaultGetQueryParamsFromRequest(req),
                new HashMap<>()
            );
            if (response != null) {
                OAuthClient client = OAuth.getOAuthClientById(main, appIdentifier, storage, clientId);
                Transformations.applyClientPropsWhiteList(response.jsonResponse.getAsJsonObject());
                if (client.clientSecret != null) {
                    response.jsonResponse.getAsJsonObject().addProperty("clientSecret", client.clientSecret);
                }
                response.jsonResponse.getAsJsonObject().addProperty("enableRefreshTokenRotation", client.enableRefreshTokenRotation);
                response.jsonResponse.getAsJsonObject().addProperty("status", "OK");
                super.sendJsonResponse(200, response.jsonResponse, resp);
            }
        } catch (OAuthClientNotFoundException e) {
            OAuthProxyHelper.handleOAuthClientNotFoundException(resp);
        } catch (IOException | TenantOrAppNotFoundException | BadPermissionException | InvalidKeyException | 
                NoSuchAlgorithmException | InvalidKeySpecException | InvalidAlgorithmParameterException | 
                NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException | 
                StorageQueryException | InvalidConfigException e) {
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

        if (!input.has("clientId")) {
            input.addProperty("clientId", "stcl_" + UUID.randomUUID());
        }

        boolean enableRefreshTokenRotation = false;
        if (input.has("enableRefreshTokenRotation")) {
            enableRefreshTokenRotation = InputParser.parseBooleanOrThrowError(input, "enableRefreshTokenRotation", false);
            input.remove("enableRefreshTokenRotation");
        }

        boolean isClientCredentialsOnly = input.has("grantTypes") &&
            input.get("grantTypes").isJsonArray() &&
            input.get("grantTypes").getAsJsonArray().size() == 1 &&
            input.get("grantTypes").getAsJsonArray().get(0).getAsString().equals("client_credentials");

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            input.addProperty("owner", appIdentifier.getConnectionUriDomain() + "_" + appIdentifier.getAppId());

            HttpRequestForOAuthProvider.Response response = OAuthProxyHelper.proxyJsonPOST(
                main, req, resp, 
                appIdentifier,
                storage,
                null, // clientIdToCheck
                "/admin/clients", // proxyPath
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                input, // jsonBody
                new HashMap<>() // headers
            );
            if (response != null) {
                String clientId = response.jsonResponse.getAsJsonObject().get("clientId").getAsString();
                String clientSecret = null;
                if (response.jsonResponse.getAsJsonObject().has("clientSecret")) {
                    clientSecret = response.jsonResponse.getAsJsonObject().get("clientSecret").getAsString();
                }
                Transformations.applyClientPropsWhiteList(response.jsonResponse.getAsJsonObject());

                try {
                    OAuth.addOrUpdateClient(main, getAppIdentifier(req), enforcePublicTenantAndGetPublicTenantStorage(req), clientId, clientSecret, isClientCredentialsOnly, enableRefreshTokenRotation);
                } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException | InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException | InvalidConfigException e) {
                    throw new ServletException(e);
                }
                response.jsonResponse.getAsJsonObject().addProperty("enableRefreshTokenRotation", enableRefreshTokenRotation);

                response.jsonResponse.getAsJsonObject().addProperty("status", "OK");
                super.sendJsonResponse(200, response.jsonResponse, resp);
            }
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
            HttpRequestForOAuthProvider.Response response = OAuth.doOAuthProxyGET(
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
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);
            OAuthClient client = OAuth.getOAuthClientById(main, appIdentifier, storage, clientId);

            if (input.has("grantTypes")) {
                boolean isClientCredentialsOnly = input.has("grantTypes") &&
                    input.get("grantTypes").isJsonArray() &&
                    input.get("grantTypes").getAsJsonArray().size() == 1 &&
                    input.get("grantTypes").getAsJsonArray().get(0).getAsString().equals("client_credentials");
                client = new OAuthClient(clientId, client.clientSecret, isClientCredentialsOnly, client.enableRefreshTokenRotation);
            }

            if (input.has("clientSecret")) {
                String clientSecret = InputParser.parseStringOrThrowError(input, "clientSecret", false);
                client = new OAuthClient(clientId, clientSecret, client.isClientCredentialsOnly, client.enableRefreshTokenRotation);
            }

            if (input.has("enableRefreshTokenRotation")) {
                boolean enableRefreshTokenRotation = InputParser.parseBooleanOrThrowError(input, "enableRefreshTokenRotation", false);
                client = new OAuthClient(clientId, client.clientSecret, client.isClientCredentialsOnly, enableRefreshTokenRotation);
            }

            HttpRequestForOAuthProvider.Response response = OAuthProxyHelper.proxyJsonPUT(
                main, req, resp,
                appIdentifier,
                storage,
                clientId, // clientIdToCheck
                "/admin/clients/" + clientId,
                true, // proxyToAdmin
                true, // camelToSnakeCaseConversion
                new HashMap<>(), // queryParams
                input, // jsonBody
                new HashMap<>() // headers
            );

            if (response != null) {
                try {
                    OAuth.addOrUpdateClient(main, appIdentifier, storage, clientId, client.clientSecret, client.isClientCredentialsOnly, client.enableRefreshTokenRotation);
                } catch (StorageQueryException | TenantOrAppNotFoundException | InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException | InvalidConfigException e) {
                    throw new ServletException(e);
                }

                Transformations.applyClientPropsWhiteList(response.jsonResponse.getAsJsonObject());

                if (!response.jsonResponse.getAsJsonObject().has("clientSecret")) {
                    response.jsonResponse.getAsJsonObject().addProperty("clientSecret", client.clientSecret);
                }
                response.jsonResponse.getAsJsonObject().addProperty("enableRefreshTokenRotation", client.enableRefreshTokenRotation);

                response.jsonResponse.getAsJsonObject().addProperty("status", "OK");
                super.sendJsonResponse(200, response.jsonResponse, resp);
            }
        } catch (IOException | StorageQueryException | TenantOrAppNotFoundException | BadPermissionException | InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException | InvalidConfigException e) {
            throw new ServletException(e);
        } catch (OAuthClientNotFoundException e) {
            OAuthProxyHelper.handleOAuthClientNotFoundException(resp);
        }
    }
}
