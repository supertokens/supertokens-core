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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.oauth.exceptions.*;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.OAuthStorage;
import io.supertokens.pluginInterface.oauth.exceptions.OAuth2ClientAlreadyExistsForAppException;
import io.supertokens.session.jwt.JWT.JWTException;
import io.supertokens.utils.Utils;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.Map.Entry;

public class OAuth {
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

    public static HttpRequestForOry.Response doOAuthProxyGET(Main main, AppIdentifier appIdentifier, Storage storage, String clientIdToCheck, String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion, Map<String, String> queryParams, Map<String, String> headers) throws StorageQueryException, OAuthClientNotFoundException, TenantOrAppNotFoundException, FeatureNotEnabledException, InvalidConfigException, IOException, OAuthAPIException {
        checkForOauthFeature(appIdentifier, main);
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        if (camelToSnakeCaseConversion) {
            queryParams = convertCamelToSnakeCase(queryParams);
        }

        if (clientIdToCheck != null) {
            if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientIdToCheck)) {
                throw new OAuthClientNotFoundException();
            }
        }

        // Request transformations
        headers = Transformations.transformRequestHeadersForHydra(headers);

        String baseURL;
        if (proxyToAdmin) {
            baseURL = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderAdminServiceUrl();
        } else {
            baseURL = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        }
        String fullUrl = baseURL + path;

        HttpRequestForOry.Response response = HttpRequestForOry.doGet(fullUrl, headers, queryParams);

        // Response transformations
        response.jsonResponse = Transformations.transformJsonResponseFromHydra(main, appIdentifier, response.jsonResponse);
        response.headers = Transformations.transformResponseHeadersFromHydra(main, appIdentifier, response.headers);

        checkNonSuccessResponse(response);

        if (camelToSnakeCaseConversion) {
            response.jsonResponse = convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
        }


        return response;
    }

    public static HttpRequestForOry.Response doOAuthProxyFormPOST(Main main, AppIdentifier appIdentifier, Storage storage, String clientIdToCheck, String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion, Map<String, String> formFields, Map<String, String> headers) throws StorageQueryException, OAuthClientNotFoundException, TenantOrAppNotFoundException, FeatureNotEnabledException, InvalidConfigException, IOException, OAuthAPIException {
        checkForOauthFeature(appIdentifier, main);
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        if (camelToSnakeCaseConversion) {
            formFields = OAuth.convertCamelToSnakeCase(formFields);
        }

        if (clientIdToCheck != null) {
            if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientIdToCheck)) {
                throw new OAuthClientNotFoundException();
            }
        }

        // Request transformations
        formFields = Transformations.transformFormFieldsForHydra(formFields);
        headers = Transformations.transformRequestHeadersForHydra(headers);

        String baseURL;
        if (proxyToAdmin) {
            baseURL = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderAdminServiceUrl();
        } else {
            baseURL = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        }
        String fullUrl = baseURL + path;

        HttpRequestForOry.Response response = HttpRequestForOry.doFormPost(fullUrl, headers, formFields);

        // Response transformations
        response.jsonResponse = Transformations.transformJsonResponseFromHydra(main, appIdentifier, response.jsonResponse);
        response.headers = Transformations.transformResponseHeadersFromHydra(main, appIdentifier, response.headers);

        checkNonSuccessResponse(response);

        if (camelToSnakeCaseConversion) {
            response.jsonResponse = OAuth.convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
        }

        return response;
    }

    public static HttpRequestForOry.Response doOAuthProxyJsonPOST(Main main, AppIdentifier appIdentifier, Storage storage, String clientIdToCheck, String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion, JsonObject jsonInput, Map<String, String> headers) throws StorageQueryException, OAuthClientNotFoundException, TenantOrAppNotFoundException, FeatureNotEnabledException, InvalidConfigException, IOException, OAuthAPIException {
        checkForOauthFeature(appIdentifier, main);
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        if (camelToSnakeCaseConversion) {
            jsonInput = convertCamelToSnakeCase(jsonInput);
        }

        if (clientIdToCheck != null) {
            if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientIdToCheck)) {
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

        HttpRequestForOry.Response response = HttpRequestForOry.doJsonPost(fullUrl, headers, jsonInput);

        // Response transformations
        response.jsonResponse = Transformations.transformJsonResponseFromHydra(main, appIdentifier, response.jsonResponse);
        response.headers = Transformations.transformResponseHeadersFromHydra(main, appIdentifier, response.headers);

        checkNonSuccessResponse(response);

        if (camelToSnakeCaseConversion) {
            response.jsonResponse = convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
        }

        return response;
    }

    public static HttpRequestForOry.Response doOAuthProxyJsonPUT(Main main, AppIdentifier appIdentifier, Storage storage, String clientIdToCheck, String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion,  Map<String, String> queryParams, JsonObject jsonInput, Map<String, String> headers) throws StorageQueryException, OAuthClientNotFoundException, TenantOrAppNotFoundException, FeatureNotEnabledException, InvalidConfigException, IOException, OAuthAPIException {
        checkForOauthFeature(appIdentifier, main);
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        if (camelToSnakeCaseConversion) {
            queryParams = convertCamelToSnakeCase(queryParams);
            jsonInput = convertCamelToSnakeCase(jsonInput);
        }

        if (clientIdToCheck != null) {
            if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientIdToCheck)) {
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

        HttpRequestForOry.Response response = HttpRequestForOry.doJsonPut(fullUrl, queryParams, headers, jsonInput);

        // Response transformations
        response.jsonResponse = Transformations.transformJsonResponseFromHydra(main, appIdentifier, response.jsonResponse);
        response.headers = Transformations.transformResponseHeadersFromHydra(main, appIdentifier, response.headers);

        checkNonSuccessResponse(response);

        if (camelToSnakeCaseConversion) {
            response.jsonResponse = convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
        }

        return response;
    }

    public static HttpRequestForOry.Response doOAuthProxyJsonDELETE(Main main, AppIdentifier appIdentifier, Storage storage, String clientIdToCheck, String path, boolean proxyToAdmin, boolean camelToSnakeCaseConversion, Map<String, String> queryParams, JsonObject jsonInput, Map<String, String> headers) throws StorageQueryException, OAuthClientNotFoundException, TenantOrAppNotFoundException, FeatureNotEnabledException, InvalidConfigException, IOException, OAuthAPIException {
        checkForOauthFeature(appIdentifier, main);
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        if (camelToSnakeCaseConversion) {
            jsonInput = OAuth.convertCamelToSnakeCase(jsonInput);
        }

        if (clientIdToCheck != null) {
            if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientIdToCheck)) {
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

        HttpRequestForOry.Response response = HttpRequestForOry.doJsonDelete(fullUrl, queryParams, headers, jsonInput);

        // Response transformations
        response.jsonResponse = Transformations.transformJsonResponseFromHydra(main, appIdentifier, response.jsonResponse);
        response.headers = Transformations.transformResponseHeadersFromHydra(main, appIdentifier, response.headers);

        checkNonSuccessResponse(response);

        if (camelToSnakeCaseConversion) {
            response.jsonResponse = OAuth.convertSnakeCaseToCamelCaseRecursively(response.jsonResponse);
        }

        return response;
    }

    private static void checkNonSuccessResponse(HttpRequestForOry.Response response) throws OAuthAPIException, OAuthClientNotFoundException {
        if (response.statusCode == 404) {
            throw new OAuthClientNotFoundException();
        }
        if (response.statusCode >= 400) {
            String error = response.jsonResponse.getAsJsonObject().get("error").getAsString();
            String errorDescription = null;
            if (response.jsonResponse.getAsJsonObject().has("error_description")) {
                errorDescription = response.jsonResponse.getAsJsonObject().get("error_description").getAsString();
            }
            throw new OAuthAPIException(error, errorDescription, response.statusCode);
        }
    }

    public static JsonObject transformTokens(Main main, AppIdentifier appIdentifier, Storage storage, JsonObject jsonBody, String iss, JsonObject accessTokenUpdate, JsonObject idTokenUpdate, boolean useDynamicKey) throws IOException, JWTException, InvalidKeyException, NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException, UnsupportedJWTSigningAlgorithmException, TenantOrAppNotFoundException, InvalidKeySpecException, JWTCreationException, InvalidConfigException {
        String rtHash = null;

        if (jsonBody.has("refresh_token")) {
            String refreshToken = jsonBody.get("refresh_token").getAsString();
            refreshToken = refreshToken.replace("ory_rt_", "st_rt_");
            jsonBody.addProperty("refresh_token", refreshToken);

            rtHash = Utils.hashSHA256(refreshToken);
        }

        if (jsonBody.has("access_token")) {
            String accessToken = jsonBody.get("access_token").getAsString();
            accessToken = OAuthToken.reSignToken(appIdentifier, main, accessToken, iss, accessTokenUpdate, rtHash, OAuthToken.TokenType.ACCESS_TOKEN, useDynamicKey, 0);
            jsonBody.addProperty("access_token", accessToken);
        }

        if (jsonBody.has("id_token")) {
            String idToken = jsonBody.get("id_token").getAsString();
            idToken = OAuthToken.reSignToken(appIdentifier, main, idToken, iss, idTokenUpdate, null, OAuthToken.TokenType.ID_TOKEN, useDynamicKey, 0);
            jsonBody.addProperty("id_token", idToken);
        }

        return jsonBody;
    }

    public static void addClientId(Main main, AppIdentifier appIdentifier, Storage storage, String clientId) throws StorageQueryException, OAuth2ClientAlreadyExistsForAppException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        oauthStorage.addClientForApp(appIdentifier, clientId);
    }

    public static void removeClientId(Main main, AppIdentifier appIdentifier, Storage storage, String clientId) throws StorageQueryException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        oauthStorage.removeAppClientAssociation(appIdentifier, clientId);
    }

    public static List<String> listClientIds(Main main, AppIdentifier appIdentifier, Storage storage) throws StorageQueryException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        return oauthStorage.listClientsForApp(appIdentifier);
    }

    private static Map<String, String> convertCamelToSnakeCase(Map<String, String> queryParams) {
        Map<String, String> result = new HashMap<>();
        if (queryParams != null) {
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                result.put(Utils.camelCaseToSnakeCase(entry.getKey()), entry.getValue());
            }
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

    private static JsonElement convertSnakeCaseToCamelCaseRecursively(JsonElement jsonResponse) {
        if (jsonResponse == null) {
            return null;
        }

        if (jsonResponse.isJsonObject()) {
            JsonObject result = new JsonObject();
            for (Entry<String, JsonElement> entry: jsonResponse.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if (value.isJsonObject()) {
                    value = convertSnakeCaseToCamelCaseRecursively(value.getAsJsonObject());
                }
                result.add(Utils.snakeCaseToCamelCase(key), value);
            }
            return result;
        } else if (jsonResponse.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement element : jsonResponse.getAsJsonArray()) {
                result.add(convertSnakeCaseToCamelCaseRecursively(element));
            }
            return result;
        }
        return jsonResponse;

    }

    public static JsonObject introspectAccessToken(Main main, AppIdentifier appIdentifier, Storage storage,
            String token) throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException, UnsupportedJWTSigningAlgorithmException {
        try {
            OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
            JsonObject payload = OAuthToken.getPayloadFromJWTToken(appIdentifier, main, token);

            if (payload.has("stt") && payload.get("stt").getAsInt() == OAuthToken.TokenType.ACCESS_TOKEN.getValue()) {

                long issuedAt = payload.get("iat").getAsLong();
                String rtHash = payload.get("rt_hash").getAsString();
                String subject = payload.get("sub").getAsString();
                String jti = payload.get("jti").getAsString();
                String clientId = payload.get("client_id").getAsString();
                String sessionHandle = null;
                if (payload.has("sessionHandle")) {
                    sessionHandle = payload.get("sessionHandle").getAsString();
                }

                boolean isRTHashValid = !oauthStorage.isRevoked(appIdentifier, "rt_hash", rtHash, issuedAt);
                boolean isSubjectValid = !oauthStorage.isRevoked(appIdentifier, "sub", subject, issuedAt);
                boolean isJTIValid = !oauthStorage.isRevoked(appIdentifier, "jti", jti, issuedAt);
                boolean isClientIdValid = !oauthStorage.isRevoked(appIdentifier, "client_id_sub", clientId + ":" + subject, issuedAt);
                boolean isSessionHandleValid = true;
                if (sessionHandle != null) {
                    isSessionHandleValid = !oauthStorage.isRevoked(appIdentifier, "sessionHandle", sessionHandle, issuedAt);
                }

                if (isRTHashValid && isSubjectValid && isJTIValid && isClientIdValid && isSessionHandleValid) {
                    payload.addProperty("active", true);
                    payload.addProperty("token_type", "Bearer");
                    payload.addProperty("token_use", "access_token");
    
                    return payload;
                }
            }
            // else fallback to active: false

        } catch (TryRefreshTokenException e) {
            // fallback to active: false
        }

        JsonObject result = new JsonObject();
        result.addProperty("active", false);
        return result;
    }

    public static void revokeAllConsentSessions(Main main, AppIdentifier appIdentifier, Storage storage, String subject, String clientId) throws StorageQueryException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        if (clientId == null) {
            oauthStorage.revoke(appIdentifier, "sub", subject);
        } else {
            oauthStorage.revoke(appIdentifier, "client_id_sub", clientId + ":" + subject);
        }
    }

	public static void revokeRefreshToken(Main main, AppIdentifier appIdentifier, Storage storage, String token) throws StorageQueryException, NoSuchAlgorithmException {
		OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
		String hash = Utils.hashSHA256(token);
		oauthStorage.revoke(appIdentifier, "rt_hash", hash);
	}

    public static void revokeAccessToken(Main main, AppIdentifier appIdentifier,
            Storage storage, String token) throws StorageQueryException, TenantOrAppNotFoundException, UnsupportedJWTSigningAlgorithmException, StorageTransactionLogicException {
        try {
            OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
            JsonObject payload = OAuthToken.getPayloadFromJWTToken(appIdentifier, main, token);

            if (payload.has("stt") && payload.get("stt").getAsInt() == OAuthToken.TokenType.ACCESS_TOKEN.getValue()) {
                String jti = payload.get("jti").getAsString();
                oauthStorage.revoke(appIdentifier, "jti", jti);
            }

        } catch (TryRefreshTokenException e) {
            // the token is already invalid or revoked, so ignore
        }
    }
}
