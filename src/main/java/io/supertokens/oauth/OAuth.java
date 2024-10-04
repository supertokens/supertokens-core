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
import io.supertokens.pluginInterface.oauth.OAuthLogoutChallenge;
import io.supertokens.pluginInterface.oauth.OAuthRevokeTargetType;
import io.supertokens.pluginInterface.oauth.OAuthStorage;
import io.supertokens.pluginInterface.oauth.exception.DuplicateOAuthLogoutChallengeException;
import io.supertokens.pluginInterface.oauth.exception.OAuthClientNotFoundException;
import io.supertokens.session.jwt.JWT.JWTException;
import io.supertokens.utils.Utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
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
            if (!oauthStorage.doesOAuthClientIdExist(appIdentifier, clientIdToCheck)) {
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
            if (!oauthStorage.doesOAuthClientIdExist(appIdentifier, clientIdToCheck)) {
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
            if (!oauthStorage.doesOAuthClientIdExist(appIdentifier, clientIdToCheck)) {
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
            if (!oauthStorage.doesOAuthClientIdExist(appIdentifier, clientIdToCheck)) {
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
            if (!oauthStorage.doesOAuthClientIdExist(appIdentifier, clientIdToCheck)) {
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

    public static String transformTokensInAuthRedirect(Main main, AppIdentifier appIdentifier, Storage storage, String url, String iss, JsonObject accessTokenUpdate, JsonObject idTokenUpdate, boolean useDynamicKey) {
        if (url.indexOf('#') == -1) {
            return url;
        }

        try {
            // Extract the part after '#'
            String fragment = url.substring(url.indexOf('#') + 1);

            // Parse the fragment as query parameters
            // Create a JsonObject from the params
            JsonObject jsonBody = new JsonObject();
            for (String param : fragment.split("&")) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0];
                    String value = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.toString());
                    jsonBody.addProperty(key, value);
                }
            }

            // Transform the tokens
            JsonObject transformedJson = transformTokens(main, appIdentifier, storage, jsonBody, iss, accessTokenUpdate, idTokenUpdate, useDynamicKey);

            // Reconstruct the query params
            StringBuilder newFragment = new StringBuilder();
            for (Map.Entry<String, JsonElement> entry : transformedJson.entrySet()) {
                if (newFragment.length() > 0) {
                    newFragment.append("&");
                }
                String encodedValue = java.net.URLEncoder.encode(entry.getValue().getAsString(), StandardCharsets.UTF_8.toString());
                newFragment.append(entry.getKey()).append("=").append(encodedValue);
            }

            // Reconstruct the URL
            String baseUrl = url.substring(0, url.indexOf('#'));
            return baseUrl + "#" + newFragment.toString();
        } catch (Exception e) {
            // If any exception occurs, return the original URL
            return url;
        }
    }

    public static JsonObject transformTokens(Main main, AppIdentifier appIdentifier, Storage storage, JsonObject jsonBody, String iss, JsonObject accessTokenUpdate, JsonObject idTokenUpdate, boolean useDynamicKey) throws IOException, JWTException, InvalidKeyException, NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException, UnsupportedJWTSigningAlgorithmException, TenantOrAppNotFoundException, InvalidKeySpecException, JWTCreationException, InvalidConfigException {
        String atHash = null;

        System.out.println("transformTokens: " + jsonBody.toString());

        if (jsonBody.has("refresh_token")) {
            String refreshToken = jsonBody.get("refresh_token").getAsString();
            refreshToken = refreshToken.replace("ory_rt_", "st_rt_");
            jsonBody.addProperty("refresh_token", refreshToken);
        }

        if (jsonBody.has("access_token")) {
            String accessToken = jsonBody.get("access_token").getAsString();
            accessToken = OAuthToken.reSignToken(appIdentifier, main, accessToken, iss, accessTokenUpdate, null, OAuthToken.TokenType.ACCESS_TOKEN, useDynamicKey, 0);
            jsonBody.addProperty("access_token", accessToken);

            // Compute at_hash as per OAuth 2.0 standard
            // 1. Take the access token
            // 2. Hash it with SHA-256
            // 3. Take the left-most half of the hash
            // 4. Base64url encode it
            byte[] accessTokenBytes = accessToken.getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(accessTokenBytes);
            byte[] halfHash = Arrays.copyOf(hash, hash.length / 2);
            atHash = Base64.getUrlEncoder().withoutPadding().encodeToString(halfHash);
        }

        if (jsonBody.has("id_token")) {
            String idToken = jsonBody.get("id_token").getAsString();
            idToken = OAuthToken.reSignToken(appIdentifier, main, idToken, iss, idTokenUpdate, atHash, OAuthToken.TokenType.ID_TOKEN, useDynamicKey, 0);
            jsonBody.addProperty("id_token", idToken);
        }

        return jsonBody;
    }

    public static void addOrUpdateClientId(Main main, AppIdentifier appIdentifier, Storage storage, String clientId, boolean isClientCredentialsOnly)
            throws StorageQueryException, TenantOrAppNotFoundException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        oauthStorage.addOrUpdateOauthClient(appIdentifier, clientId, isClientCredentialsOnly);
    }

    public static void removeClientId(Main main, AppIdentifier appIdentifier, Storage storage, String clientId) throws StorageQueryException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        oauthStorage.deleteOAuthClient(appIdentifier, clientId);
    }

    public static List<String> listClientIds(Main main, AppIdentifier appIdentifier, Storage storage) throws StorageQueryException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        return oauthStorage.listOAuthClients(appIdentifier);
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

    public static void verifyAndUpdateIntrospectRefreshTokenPayload(Main main, AppIdentifier appIdentifier,
            Storage storage, JsonObject payload, String refreshToken) throws StorageQueryException, TenantOrAppNotFoundException, FeatureNotEnabledException, InvalidConfigException, IOException {
        
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        if (!payload.get("active").getAsBoolean()) {
            return; // refresh token is not active
        }

        Transformations.transformExt(payload);
        payload.remove("ext");

        boolean isValid = !isTokenRevokedBasedOnPayload(oauthStorage, appIdentifier, payload);

        if (!isValid) {
            payload.entrySet().clear();
            payload.addProperty("active", false);

            // // ideally we want to revoke the refresh token in hydra, but we can't since we don't have the client secret here
            // refreshToken = refreshToken.replace("st_rt_", "ory_rt_");
            // Map<String, String> formFields = new HashMap<>();
            // formFields.put("token", refreshToken);

            // try {
            //     doOAuthProxyFormPOST(
            //         main, appIdentifier, oauthStorage,
            //         clientId, // clientIdToCheck
            //         "/oauth2/revoke", // path
            //         false, // proxyToAdmin
            //         false, // camelToSnakeCaseConversion
            //         formFields,
            //         new HashMap<>());
            // } catch (OAuthAPIException | OAuthClientNotFoundException e) {
            //     // ignore
            // }
        }
    }

    private static boolean isTokenRevokedBasedOnPayload(OAuthStorage oauthStorage, AppIdentifier appIdentifier, JsonObject payload) throws StorageQueryException {
        long issuedAt = payload.get("iat").getAsLong();
        List<OAuthRevokeTargetType> targetTypes = new ArrayList<>();
        List<String> targetValues = new ArrayList<>();

        targetTypes.add(OAuthRevokeTargetType.CLIENT_ID);
        targetValues.add(payload.get("client_id").getAsString());

        if (payload.has("jti")) {
            targetTypes.add(OAuthRevokeTargetType.JTI);
            targetValues.add(payload.get("jti").getAsString());
        }

        if (payload.has("gid")) {
            targetTypes.add(OAuthRevokeTargetType.GID);
            targetValues.add(payload.get("gid").getAsString());
        }

        if (payload.has("sessionHandle")) {
            targetTypes.add(OAuthRevokeTargetType.SESSION_HANDLE);
            targetValues.add(payload.get("sessionHandle").getAsString());
        }

        return oauthStorage.isOAuthTokenRevokedBasedOnTargetFields(appIdentifier, targetTypes.toArray(new OAuthRevokeTargetType[0]), targetValues.toArray(new String[0]), issuedAt);
    }

    public static JsonObject introspectAccessToken(Main main, AppIdentifier appIdentifier, Storage storage,
            String token) throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException, UnsupportedJWTSigningAlgorithmException {
        try {
            OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
            JsonObject payload = OAuthToken.getPayloadFromJWTToken(appIdentifier, main, token);

            if (payload.has("stt") && payload.get("stt").getAsInt() == OAuthToken.TokenType.ACCESS_TOKEN.getValue()) {

                boolean isValid = !isTokenRevokedBasedOnPayload(oauthStorage, appIdentifier, payload);

                if (isValid) {
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

    public static void revokeTokensForClientId(Main main, AppIdentifier appIdentifier, Storage storage, String clientId)
            throws StorageQueryException, TenantOrAppNotFoundException {
        long exp = System.currentTimeMillis() / 1000 + 3600 * 24 * 183; // 6 month from now
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        oauthStorage.revokeOAuthTokensBasedOnTargetFields(appIdentifier, OAuthRevokeTargetType.CLIENT_ID, clientId, exp);
    }

	public static void revokeRefreshToken(Main main, AppIdentifier appIdentifier, Storage storage, String gid, long exp)
            throws StorageQueryException, NoSuchAlgorithmException, TenantOrAppNotFoundException {
		OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
		oauthStorage.revokeOAuthTokensBasedOnTargetFields(appIdentifier, OAuthRevokeTargetType.GID, gid, exp);
	}

    public static void revokeAccessToken(Main main, AppIdentifier appIdentifier,
            Storage storage, String token) throws StorageQueryException, TenantOrAppNotFoundException, UnsupportedJWTSigningAlgorithmException, StorageTransactionLogicException {
        try {
            OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
            JsonObject payload = OAuthToken.getPayloadFromJWTToken(appIdentifier, main, token);

            long exp = payload.get("exp").getAsLong();

            if (payload.has("stt") && payload.get("stt").getAsInt() == OAuthToken.TokenType.ACCESS_TOKEN.getValue()) {
                String jti = payload.get("jti").getAsString();
                oauthStorage.revokeOAuthTokensBasedOnTargetFields(appIdentifier, OAuthRevokeTargetType.JTI, jti, exp);
            }

        } catch (TryRefreshTokenException e) {
            // the token is already invalid or revoked, so ignore
        }
    }

	public static void revokeSessionHandle(Main main, AppIdentifier appIdentifier, Storage storage,
			String sessionHandle) throws StorageQueryException, TenantOrAppNotFoundException {
        long exp = System.currentTimeMillis() / 1000 + 3600 * 24 * 183; // 6 month from now
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        oauthStorage.revokeOAuthTokensBasedOnTargetFields(appIdentifier, OAuthRevokeTargetType.SESSION_HANDLE, sessionHandle, exp);
	}

    public static JsonObject verifyIdTokenAndGetPayload(Main main, AppIdentifier appIdentifier, Storage storage,
            String idToken) throws StorageQueryException, OAuthAPIException, TenantOrAppNotFoundException, UnsupportedJWTSigningAlgorithmException, StorageTransactionLogicException {
        try {
            return OAuthToken.getPayloadFromJWTToken(appIdentifier, main, idToken);
        } catch (TryRefreshTokenException e) {
            // invalid id token
            throw new OAuthAPIException("invalid_request", "The request is missing a required parameter, includes an invalid parameter value, includes a parameter more than once, or is otherwise malformed.", 400);
        }
    }

    public static void addM2MToken(Main main, AppIdentifier appIdentifier, Storage storage, String accessToken)
            throws StorageQueryException, TenantOrAppNotFoundException, TryRefreshTokenException,
            UnsupportedJWTSigningAlgorithmException, StorageTransactionLogicException, OAuthClientNotFoundException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        JsonObject payload = OAuthToken.getPayloadFromJWTToken(appIdentifier, main, accessToken);
        oauthStorage.addOAuthM2MTokenForStats(appIdentifier, payload.get("client_id").getAsString(), payload.get("iat").getAsLong(), payload.get("exp").getAsLong());
    }

    public static String createLogoutRequestAndReturnRedirectUri(Main main, AppIdentifier appIdentifier, Storage storage, String clientId,
            String postLogoutRedirectionUri, String sessionHandle, String state)
            throws StorageQueryException, OAuthClientNotFoundException {
        
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        while (true) {
            try {
                String logoutChallenge = UUID.randomUUID().toString();
                oauthStorage.addOAuthLogoutChallenge(appIdentifier, logoutChallenge, clientId, postLogoutRedirectionUri, sessionHandle, state, System.currentTimeMillis());

                return "{apiDomain}/oauth/logout?logout_challenge=" + logoutChallenge;
            } catch (DuplicateOAuthLogoutChallengeException e) {
                // retry
            }
        }
    }

    public static String consumeLogoutChallengeAndGetRedirectUri(Main main, AppIdentifier appIdentifier, Storage storage, String challenge)
            throws StorageQueryException, OAuthAPIException, TenantOrAppNotFoundException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        OAuthLogoutChallenge logoutChallenge = oauthStorage.getOAuthLogoutChallenge(appIdentifier, challenge);

        if (logoutChallenge == null) {
            throw new OAuthAPIException("invalid_request", "Logout request not found", 400);
        }

        revokeSessionHandle(main, appIdentifier, oauthStorage, logoutChallenge.sessionHandle);

        if (logoutChallenge.postLogoutRedirectionUri != null) {
            String url = logoutChallenge.postLogoutRedirectionUri;
            if (logoutChallenge.state != null) {
                return url + "?state=" + logoutChallenge.state;
            } else {
                return url;
            }
        } else {
            return "{apiDomain}/fallbacks/logout/callback";
        }
    }

    public static void deleteLogoutChallenge(Main main, AppIdentifier appIdentifier, Storage storage, String challenge) throws StorageQueryException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        oauthStorage.deleteOAuthLogoutChallenge(appIdentifier, challenge);
    }
}
