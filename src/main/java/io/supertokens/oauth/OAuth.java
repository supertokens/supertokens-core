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

package io.supertokens.oauth;

import com.auth0.jwt.exceptions.JWTCreationException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.jwt.JWTSigningFunctions;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.oauth.exceptions.*;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.OAuthStorage;
import io.supertokens.pluginInterface.oauth.exceptions.OAuth2ClientAlreadyExistsForAppException;
import io.supertokens.session.jwt.JWT.JWTException;
import io.supertokens.signingkeys.JWTSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.utils.Utils;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.Map.Entry;

public class OAuth {
    private static final String HYDRA_JWKS_PATH = "/.well-known/jwks.json";

    private static void checkForOauthFeature(AppIdentifier appIdentifier, Main main)
            throws StorageQueryException, TenantOrAppNotFoundException, FeatureNotEnabledException {
        EE_FEATURES[] features = FeatureFlag.getInstance(main, appIdentifier).getEnabledFeatures();
        for (EE_FEATURES f : features) {
            if (f == EE_FEATURES.OAUTH) {
                return;
            }
        }

        throw new FeatureNotEnabledException(
                "OAuth feature is not enabled. Please subscribe to a SuperTokens core license key to enable this " +
                        "feature.");
    }

    public static HttpRequest.Response handleOAuthProxyGET(Main main, AppIdentifier appIdentifier, Storage storage, String path, boolean proxyToAdmin, Map<String, String> queryParams, Map<String, String> headers) throws StorageQueryException, OAuthClientNotFoundException, TenantOrAppNotFoundException, FeatureNotEnabledException, InvalidConfigException, IOException, OAuthAPIException {
        checkForOauthFeature(appIdentifier, main);
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        if (queryParams != null && queryParams.containsKey("client_id")) {
            String clientId = queryParams.get("client_id");
            if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
                throw new OAuthClientNotFoundException();
            }
        }

        // Request transformations
        queryParams = Transformations.transformQueryParamsForHydra(queryParams);
        headers = Transformations.transformRequestHeadersForHydra(headers);

        String baseURL;
        if (proxyToAdmin) {
            baseURL = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderAdminServiceUrl();
        } else {
            baseURL = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        }
        String fullUrl = baseURL + path;

        HttpRequest.Response response = HttpRequest.doGet(fullUrl, headers, queryParams);

        // Response transformations
        response.jsonResponse = Transformations.transformJsonResponseFromHydra(response.jsonResponse);
        response.headers = Transformations.transformResponseHeadersFromHydra(main, appIdentifier, response.headers);

        checkNonSuccessResponse(response);

        return response;
    }

    public static HttpRequest.Response handleOAuthProxyFormPOST(Main main, AppIdentifier appIdentifier, Storage storage, String path, boolean proxyToAdmin, Map<String, String> formFields, Map<String, String> headers) throws StorageQueryException, OAuthClientNotFoundException, TenantOrAppNotFoundException, FeatureNotEnabledException, InvalidConfigException, IOException, OAuthAPIException {
        checkForOauthFeature(appIdentifier, main);
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        if (formFields.containsKey("client_id")) {
            String clientId = formFields.get("client_id");
            if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
                throw new OAuthClientNotFoundException();
            }
        }

        // Request transformations
        formFields = Transformations.transformFormFieldsForHydra(formFields);
        headers = Transformations.transformRequestHeadersForHydra(headers);

        String publicOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        String fullUrl = publicOAuthProviderServiceUrl + path;

        HttpRequest.Response response = HttpRequest.doFormPost(fullUrl, headers, formFields);

        // Response transformations
        response.jsonResponse = Transformations.transformJsonResponseFromHydra(response.jsonResponse);
        response.headers = Transformations.transformResponseHeadersFromHydra(main, appIdentifier, response.headers);

        checkNonSuccessResponse(response);

        return response;
    }

    public static HttpRequest.Response handleOAuthProxyJsonPOST(Main main, AppIdentifier appIdentifier, Storage storage, String path, boolean proxyToAdmin, JsonObject jsonInput, Map<String, String> headers) throws StorageQueryException, OAuthClientNotFoundException, TenantOrAppNotFoundException, FeatureNotEnabledException, InvalidConfigException, IOException, OAuthAPIException {
        checkForOauthFeature(appIdentifier, main);
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        if (jsonInput.has("client_id")) {
            String clientId = jsonInput.get("client_id").getAsString();
            if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
                throw new OAuthClientNotFoundException();
            }
        }

        // Request transformations
        jsonInput = Transformations.transformJsonForHydra(jsonInput);
        headers = Transformations.transformRequestHeadersForHydra(headers);

        String baseURL;
        if (proxyToAdmin) {
            baseURL = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderAdminServiceUrl();
        } else {
            baseURL = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        }
        String fullUrl = baseURL + path;

        HttpRequest.Response response = HttpRequest.doJsonPost(fullUrl, headers, jsonInput);

        // Response transformations
        response.jsonResponse = Transformations.transformJsonResponseFromHydra(response.jsonResponse);
        response.headers = Transformations.transformResponseHeadersFromHydra(main, appIdentifier, response.headers);

        checkNonSuccessResponse(response);

        return response;
    }

    public static HttpRequest.Response handleOAuthProxyJsonPUT(Main main, AppIdentifier appIdentifier, Storage storage, String path, boolean proxyToAdmin, JsonObject jsonInput, Map<String, String> headers) throws StorageQueryException, OAuthClientNotFoundException, TenantOrAppNotFoundException, FeatureNotEnabledException, InvalidConfigException, IOException, OAuthAPIException {
        checkForOauthFeature(appIdentifier, main);
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        if (jsonInput.has("client_id")) {
            String clientId = jsonInput.get("client_id").getAsString();
            if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
                throw new OAuthClientNotFoundException();
            }
        }

        // Request transformations
        jsonInput = Transformations.transformJsonForHydra(jsonInput);
        headers = Transformations.transformRequestHeadersForHydra(headers);

        String baseURL;
        if (proxyToAdmin) {
            baseURL = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderAdminServiceUrl();
        } else {
            baseURL = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        }
        String fullUrl = baseURL + path;

        HttpRequest.Response response = HttpRequest.doJsonPut(fullUrl, headers, jsonInput);

        // Response transformations
        response.jsonResponse = Transformations.transformJsonResponseFromHydra(response.jsonResponse);
        response.headers = Transformations.transformResponseHeadersFromHydra(main, appIdentifier, response.headers);

        checkNonSuccessResponse(response);

        return response;
    }

    public static HttpRequest.Response handleOAuthProxyJsonDELETE(Main main, AppIdentifier appIdentifier, Storage storage, String path, boolean proxyToAdmin, JsonObject jsonInput, Map<String, String> headers) throws StorageQueryException, OAuthClientNotFoundException, TenantOrAppNotFoundException, FeatureNotEnabledException, InvalidConfigException, IOException, OAuthAPIException {
        checkForOauthFeature(appIdentifier, main);
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        if (jsonInput.has("client_id")) {
            String clientId = jsonInput.get("client_id").getAsString();
            if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
                throw new OAuthClientNotFoundException();
            }
        }

        // Request transformations
        jsonInput = Transformations.transformJsonForHydra(jsonInput);
        headers = Transformations.transformRequestHeadersForHydra(headers);

        String baseURL;
        if (proxyToAdmin) {
            baseURL = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderAdminServiceUrl();
        } else {
            baseURL = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        }
        String fullUrl = baseURL + path;

        HttpRequest.Response response = HttpRequest.doJsonDelete(fullUrl, headers, jsonInput);

        // Response transformations
        response.jsonResponse = Transformations.transformJsonResponseFromHydra(response.jsonResponse);
        response.headers = Transformations.transformResponseHeadersFromHydra(main, appIdentifier, response.headers);

        checkNonSuccessResponse(response);

        return response;
    }

    private static void checkNonSuccessResponse(HttpRequest.Response response) throws OAuthAPIException, OAuthClientNotFoundException {
        if (response.statusCode == 404) {
            throw new OAuthClientNotFoundException();
        }
        if (response.statusCode >= 400) {
            String error = response.jsonResponse.get("error").getAsString();
            String errorDebug = null;
            if (response.jsonResponse.has("error_debug")) {
                errorDebug = response.jsonResponse.get("error_debug").getAsString();
            }
            String errorDescription = null;
            if (response.jsonResponse.has("error_description")) {
                errorDescription = response.jsonResponse.get("error_description").getAsString();
            }
            String errorHint = null;
            if (response.jsonResponse.has("error_hint")) {
                errorHint = response.jsonResponse.get("error_hint").getAsString();
            }
            throw new OAuthAPIException(error, errorDebug, errorDescription, errorHint, response.statusCode);
        }
    }

    public static JsonObject transformTokens(Main main, AppIdentifier appIdentifier, Storage storage, JsonObject jsonBody, String iss, boolean useDynamicKey) throws IOException, JWTException, InvalidKeyException, NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException, UnsupportedJWTSigningAlgorithmException, TenantOrAppNotFoundException, InvalidKeySpecException, JWTCreationException, InvalidConfigException {
        if (jsonBody.has("access_token")) {
            String accessToken = jsonBody.get("access_token").getAsString();
            accessToken = reSignToken(appIdentifier, main, accessToken, iss, SessionTokenType.ACCESS_TOKEN, useDynamicKey, 0);
            jsonBody.addProperty("access_token", accessToken);
        }

        if (jsonBody.has("id_token")) {
            String idToken = jsonBody.get("id_token").getAsString();
            idToken = reSignToken(appIdentifier, main, idToken, iss, SessionTokenType.ID_TOKEN, useDynamicKey, 0);
            jsonBody.addProperty("id_token", idToken);
        }

        if (jsonBody.has("refresh_token")) {
            String refreshToken = jsonBody.get("refresh_token").getAsString();
            refreshToken = refreshToken.replace("ory_rt_", "st_rt_");
            jsonBody.addProperty("refresh_token", refreshToken);
        }

        return jsonBody;
    }

    private static String reSignToken(AppIdentifier appIdentifier, Main main, String token, String iss, SessionTokenType tokenType, boolean useDynamicSigningKey, int retryCount) throws IOException, JWTException, InvalidKeyException, NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException, UnsupportedJWTSigningAlgorithmException, TenantOrAppNotFoundException, InvalidKeySpecException, JWTCreationException, InvalidConfigException {
        // Load the JWKS from the specified URL
        String publicOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        String jwksUrl = publicOAuthProviderServiceUrl + HYDRA_JWKS_PATH;

        // Validate the JWT and extract claims using the fetched public signing keys
        JsonObject payload = JWTVerification.verifyJWTAndGetPayload(main, token, jwksUrl);

        // move keys in ext to root
        if (tokenType == SessionTokenType.ACCESS_TOKEN && payload.has("ext")) {
            JsonObject ext = payload.getAsJsonObject("ext");
            for (Map.Entry<String, JsonElement> entry : ext.entrySet()) {
                payload.add(entry.getKey(), entry.getValue());
            }
            payload.remove("ext");
        }
        payload.addProperty("iss", iss);
        payload.addProperty("stt", tokenType.getValue());

        JWTSigningKeyInfo keyToUse;
        if (useDynamicSigningKey) {
            keyToUse = Utils.getJWTSigningKeyInfoFromKeyInfo(
                    SigningKeys.getInstance(appIdentifier, main).getLatestIssuedDynamicKey());
        } else {
            keyToUse = SigningKeys.getInstance(appIdentifier, main)
                    .getStaticKeyForAlgorithm(JWTSigningKey.SupportedAlgorithms.RS256);
        }

        token = JWTSigningFunctions.createJWTToken(JWTSigningKey.SupportedAlgorithms.RS256, new HashMap<>(),
                    payload, null, payload.get("exp").getAsLong(), payload.get("iat").getAsLong(), keyToUse);
        return token;
    }

    public static void addClientId(Main main, AppIdentifier appIdentifier, Storage storage, String clientId) throws StorageQueryException, OAuth2ClientAlreadyExistsForAppException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        oauthStorage.addClientForApp(appIdentifier, clientId);
    }

    public static void removeClientId(Main main, AppIdentifier appIdentifier, Storage storage, String clientId) throws StorageQueryException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        oauthStorage.removeAppClientAssociation(appIdentifier, clientId);
    }

    public static Map<String, String> convertCamelToSnakeCase(Map<String, String> queryParams) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            result.put(Utils.camelCaseToSnakeCase(entry.getKey()), entry.getValue());
        }
        return result;
    }

    public static JsonObject convertCamelToSnakeCase(JsonObject queryParams) {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : queryParams.entrySet()) {
            result.add(Utils.camelCaseToSnakeCase(entry.getKey()), entry.getValue());
        }
        return result;
    }

    public static JsonObject convertSnakeCaseToCamelCaseRecursively(JsonObject jsonResponse) {
        if (jsonResponse == null) {
            return null;
        }

        JsonObject result = new JsonObject();
        for (Entry<String, JsonElement> entry: jsonResponse.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                value = convertSnakeCaseToCamelCaseRecursively(value.getAsJsonObject());
            }
            result.add(Utils.snakeCaseToCamelCase(key), value);
        }
        return result;
    }

    public static enum SessionTokenType {
        ACCESS_TOKEN(1),
        ID_TOKEN(2);

        private final int value;

        SessionTokenType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
