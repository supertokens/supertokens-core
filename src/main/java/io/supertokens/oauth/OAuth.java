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
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlag;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
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
import io.supertokens.session.jwt.JWT;
import io.supertokens.session.jwt.JWT.JWTException;
import io.supertokens.signingkeys.JWTSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.utils.Utils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

public class OAuth {

    private static final String LOCATION_HEADER_NAME = "Location";
    private static final String COOKIES_HEADER_NAME = "Set-Cookie";
    private static final String ERROR_LITERAL = "error=";
    private static final String ERROR_DESCRIPTION_LITERAL = "error_description=";

    private static final String HYDRA_AUTH_ENDPOINT = "/oauth2/auth";
    private static final String HYDRA_TOKEN_ENDPOINT = "/oauth2/token";
    private static final String HYDRA_CLIENTS_ENDPOINT = "/admin/clients";
    private static final String HYDRA_JWKS_PATH = "/.well-known/jwks.json"; // New constant for JWKS path

    private static Map<String, Map<String, JsonObject>> jwksCache = new HashMap<>(); // Cache for JWKS keys
    private static final int MAX_RETRIES = 3; // Maximum number of retries for fetching JWKS

    private static void checkForOauthFeature(AppIdentifier appIdentifier, Main main)
            throws StorageQueryException, TenantOrAppNotFoundException, FeatureNotEnabledException {
        EE_FEATURES[] features = FeatureFlag.getInstance(main, appIdentifier).getEnabledFeatures();
        for (EE_FEATURES f : features) {
            if (f == EE_FEATURES.OAUTH) {
                return;
            }
        }
        // throw new FeatureNotEnabledException(
        //         "OAuth feature is not enabled. Please subscribe to a SuperTokens core license key to enable this " +
        //                 "feature.");
    }

    public static OAuthAuthResponse getAuthorizationUrl(Main main, AppIdentifier appIdentifier, Storage storage, JsonObject paramsFromSdk, String inputCookies)
            throws InvalidConfigException, HttpResponseException, IOException, OAuthAPIException, StorageQueryException,
            TenantOrAppNotFoundException, FeatureNotEnabledException {

        checkForOauthFeature(appIdentifier, main);

        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        String redirectTo = null;
        List<String> cookies = null;

        String publicOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        String hydraInternalAddress = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOauthProviderUrlConfiguredInHydra();
        String hydraBaseUrlForConsentAndLogin = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOauthProviderConsentLoginBaseUrl();

        String clientId = paramsFromSdk.get("client_id").getAsString();

        if (inputCookies != null) {
            inputCookies = inputCookies.replaceAll("st_oauth_", "ory_hydra_");
        }

        if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
            throw new OAuthAPIException("invalid_client", "Client authentication failed (e.g., unknown client, no client authentication included, or unsupported authentication method). The requested OAuth 2.0 Client does not exist.", 400);
        }

        // we query hydra
        Map<String, String> queryParamsForHydra = constructHydraRequestParamsForAuthorizationGETAPICall(paramsFromSdk);
        Map<String, String> headers = new HashMap<>();
        Map<String, List<String>> responseHeaders = new HashMap<>();

        if (inputCookies != null) {
            headers.put("Cookie", inputCookies);
        }

        HttpRequest.sendGETRequestWithResponseHeaders(main, "", publicOAuthProviderServiceUrl + HYDRA_AUTH_ENDPOINT, queryParamsForHydra, headers, 10000, 10000, null, responseHeaders, false);

        if (!responseHeaders.isEmpty() && responseHeaders.containsKey(LOCATION_HEADER_NAME)) {
            String locationHeaderValue = responseHeaders.get(LOCATION_HEADER_NAME).get(0);
            if(Utils.containsUrl(locationHeaderValue, hydraInternalAddress, true)){
                String error = getValueOfQueryParam(locationHeaderValue, ERROR_LITERAL);
                String errorDescription = getValueOfQueryParam(locationHeaderValue, ERROR_DESCRIPTION_LITERAL);
                throw new OAuthAPIException(error, errorDescription, 400);
            }

            if(Utils.containsUrl(locationHeaderValue, hydraBaseUrlForConsentAndLogin, true)){
                redirectTo = locationHeaderValue.replace(hydraBaseUrlForConsentAndLogin, "{apiDomain}");
            } else {
                redirectTo = locationHeaderValue;
            }
            if (redirectTo.contains("code=ory_ac_")) {
                redirectTo = redirectTo.replace("code=ory_ac_", "code=st_ac_");
            }
        } else {
            throw new IllegalStateException("Unexpected answer from Oauth Provider");
        }
        if(responseHeaders.containsKey(COOKIES_HEADER_NAME)){
            cookies = new ArrayList<>(responseHeaders.get(COOKIES_HEADER_NAME));

            for (int i = 0; i < cookies.size(); i++) {
                String cookieStr = cookies.get(i);
                if (cookieStr.startsWith("ory_hydra_")) {
                    cookies.set(i, "st_oauth_" + cookieStr.substring(10));
                }
            }
        }

        return new OAuthAuthResponse(redirectTo, cookies);
    }

    public static JsonObject getToken(Main main, AppIdentifier appIdentifier, Storage storage, JsonObject bodyFromSdk, String iss, boolean useDynamicKey) throws InvalidConfigException, TenantOrAppNotFoundException, OAuthAPIException, StorageQueryException, IOException, InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, JWTCreationException, JWTException, StorageTransactionLogicException, UnsupportedJWTSigningAlgorithmException, FeatureNotEnabledException {
        checkForOauthFeature(appIdentifier, main);
        
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        String clientId = bodyFromSdk.get("client_id").getAsString();

        if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
            throw new OAuthAPIException("invalid_client", "Client authentication failed (e.g., unknown client, no client authentication included, or unsupported authentication method). The requested OAuth 2.0 Client does not exist.", 400);
        }

        String publicOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        try {
            Map<String, String> bodyParams = constructHydraRequestParamsForAuthorizationGETAPICall(bodyFromSdk);
            if (bodyParams.containsKey("code")) {
                bodyParams.put("code", bodyParams.get("code").replace("st_ac_", "ory_ac_"));
            }
            if (bodyParams.containsKey("refresh_token")) {
                bodyParams.put("refresh_token", bodyParams.get("refresh_token").replace("st_rt_", "ory_rt_"));
            }
            JsonObject response = HttpRequest.sendFormPOSTRequest(main, "", publicOAuthProviderServiceUrl + HYDRA_TOKEN_ENDPOINT, bodyParams, 10000, 10000, null);

            // token transformations
            if (response.has("access_token")) {
                String accessToken = response.get("access_token").getAsString();
                accessToken = reSignToken(appIdentifier, main, accessToken, iss, 1, useDynamicKey, 0);
                response.addProperty("access_token", accessToken);
            }

            if (response.has("id_token")) {
                String idToken = response.get("id_token").getAsString();
                idToken = reSignToken(appIdentifier, main, idToken, iss, 2, useDynamicKey, 0);
                response.addProperty("id_token", idToken);
            }

            if (response.has("refresh_token")) {
                String refreshToken = response.get("refresh_token").getAsString();
                refreshToken = refreshToken.replace("ory_rt_", "st_rt_");
                response.addProperty("refresh_token", refreshToken);
            }
            return response;

        } catch (HttpResponseException e) {
            JsonObject errorResponse = new Gson().fromJson(e.rawMessage, JsonObject.class);
            throw new OAuthAPIException(
                errorResponse.get("error").getAsString(),
                errorResponse.get("error_description").getAsString(),
                e.statusCode
            );
        }
    }

    private static String reSignToken(AppIdentifier appIdentifier, Main main, String token, String iss, int stt, boolean useDynamicSigningKey, int retryCount) throws IOException, JWTException, InvalidKeyException, NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException, UnsupportedJWTSigningAlgorithmException, TenantOrAppNotFoundException, InvalidKeySpecException, JWTCreationException, InvalidConfigException, OAuthAPIException {
        // Load the JWKS from the specified URL
        String publicOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        String jwksUrl = publicOAuthProviderServiceUrl + HYDRA_JWKS_PATH; // Use the new constant

        // Check if cached keys are available for the jwksUrl
        Map<String, JsonObject> cachedKeys = jwksCache.get(jwksUrl);
        if (cachedKeys == null) {
            JsonObject jwksResponse;
            try {
                jwksResponse = HttpRequest.sendGETRequest(main, "", jwksUrl, null, 10000, 10000, null);
            } catch (HttpResponseException e) {
                throw new OAuthAPIException("jwks_error", "Could not fetch JWKS keys from hydra for token verification", 500);
            }
            cachedKeys = new HashMap<>();
            JsonArray keysArray = jwksResponse.get("keys").getAsJsonArray();

            // Populate the cache with keys indexed by kid
            for (JsonElement key : keysArray) {
                JsonObject keyObject = key.getAsJsonObject();
                String kid = keyObject.get("kid").getAsString();
                cachedKeys.put(kid, keyObject);
            }
            jwksCache.put(jwksUrl, cachedKeys); // Cache the keys with jwksUrl as the key
        }

        // Validate the JWT and extract claims using the fetched public signing keys
        JWT.JWTPreParseInfo jwtInfo = JWT.preParseJWTInfo(token);
        JWT.JWTInfo jwtResult = null;
        
        // Check if the key for the given kid exists in the cache
        JsonObject keyObject = cachedKeys.get(jwtInfo.kid);
        if (keyObject != null) {
            jwtResult = JWT.verifyJWTAndGetPayload(jwtInfo, keyObject.get("n").getAsString(), keyObject.get("e").getAsString());
        }

        if (jwtResult == null) {
            // If no matching key found and retry count is not exceeded, refetch the keys
            if (retryCount < MAX_RETRIES) {
                jwksCache.remove(jwksUrl); // Invalidate cache
                return reSignToken(appIdentifier, main, token, iss, stt, useDynamicSigningKey, retryCount + 1); // Retry with incremented count
            } else {
                throw new OAuthAPIException("invalid_token", "The token is invalid or has expired.", 400);
            }
        }

        JsonObject payload = jwtResult.payload;
        // move keys in ext to root
        if (payload.has("ext")) {
            JsonObject ext = payload.getAsJsonObject("ext");
            for (Map.Entry<String, JsonElement> entry : ext.entrySet()) {
                payload.add(entry.getKey(), entry.getValue());
            }
            payload.remove("ext");
        }
        payload.addProperty("iss", iss);
        payload.addProperty("stt", stt);

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

    //This more or less acts as a pass-through for the sdks, apart from camelCase <-> snake_case key transformation and setting a few default values
    public static JsonObject registerOAuthClient(Main main, AppIdentifier appIdentifier, Storage storage, JsonObject paramsFromSdk)
            throws TenantOrAppNotFoundException, InvalidConfigException, IOException,
            OAuthAPIInvalidInputException,
            NoSuchAlgorithmException, StorageQueryException {

        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        String adminOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderAdminServiceUrl();

        byte[] idBaseBytes = new byte[48];

        while(true){
            new SecureRandom().nextBytes(idBaseBytes);
            String clientId = "supertokens_" + Utils.hashSHA256Base64UrlSafe(idBaseBytes);
            try {

                JsonObject hydraRequestBody = constructHydraRequestParamsForRegisterClientPOST(paramsFromSdk, clientId);
                JsonObject hydraResponse = HttpRequest.sendJsonPOSTRequest(main, "", adminOAuthProviderServiceUrl + HYDRA_CLIENTS_ENDPOINT, hydraRequestBody, 10000, 10000, null);

                oauthStorage.addClientForApp(appIdentifier, clientId);

                return formatResponseForSDK(hydraResponse); //sdk expects everything from hydra in camelCase
            } catch (HttpResponseException e) {
                try {
                    if (e.statusCode == 409){
                        //no-op
                        //client with id already exists, silently retry with different Id
                    } else {
                        //other error from hydra, like invalid content in json. Throw exception
                        throw createCustomExceptionFromHttpResponseException(
                                e, OAuthAPIInvalidInputException.class);
                    }
                } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                         IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            } catch (OAuth2ClientAlreadyExistsForAppException e) {
                //in theory, this is unreachable. We are registering new clients here, so this should not happen.
                throw new RuntimeException(e);
            }
        }
    }

    public static JsonObject loadOAuthClient(Main main, AppIdentifier appIdentifier, Storage storage, String clientId)
            throws TenantOrAppNotFoundException, InvalidConfigException, StorageQueryException,
            IOException, OAuthClientNotFoundException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        String adminOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderAdminServiceUrl();

        if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
            throw new OAuthClientNotFoundException("invalid_client", "Invalid client_id specified");
        } else {
            try {
                JsonObject hydraResponse = HttpRequest.sendGETRequest(main, "", adminOAuthProviderServiceUrl + HYDRA_CLIENTS_ENDPOINT + "/" + clientId, null, 10000, 10000, null);
                return  formatResponseForSDK(hydraResponse);
            } catch (HttpResponseException e) {
                try {
                    throw createCustomExceptionFromHttpResponseException(e, OAuthClientNotFoundException.class);
                } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                         IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    public static void deleteOAuthClient(Main main, AppIdentifier appIdentifier, Storage storage, String clientId)
            throws TenantOrAppNotFoundException, InvalidConfigException, StorageQueryException,
            IOException, OAuthClientNotFoundException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        String adminOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderAdminServiceUrl();

        if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
            throw new OAuthClientNotFoundException("invalid_client", "Invalid client_id specified");
        } else {
            try {
                oauthStorage.removeAppClientAssociation(appIdentifier, clientId);
                HttpRequest.sendJsonDELETERequest(main, "", adminOAuthProviderServiceUrl + HYDRA_CLIENTS_ENDPOINT + "/" + clientId, null, 10000, 10000, null);
            } catch (HttpResponseException e) {
                try {
                    throw createCustomExceptionFromHttpResponseException(e, OAuthClientNotFoundException.class);
                } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                         IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    public static JsonObject updateOauthClient(Main main, AppIdentifier appIdentifier, Storage storage, JsonObject paramsFromSdk)
            throws TenantOrAppNotFoundException, InvalidConfigException, StorageQueryException,
            InvocationTargetException, NoSuchMethodException, InstantiationException,
            IllegalAccessException, OAuthClientNotFoundException, OAuthAPIInvalidInputException,
            OAuthClientUpdateException {

        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        String adminOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderAdminServiceUrl();

        String clientId = paramsFromSdk.get("clientId").getAsString();

        if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
            throw new OAuthClientNotFoundException("invalid_client", "Invalid client_id specified");
        } else {
            JsonArray hydraInput = translateIncomingDataToHydraUpdateFormat(paramsFromSdk);
            try {
                JsonObject updatedClient = HttpRequest.sendJsonPATCHRequest(main, adminOAuthProviderServiceUrl + HYDRA_CLIENTS_ENDPOINT+ "/" + clientId, hydraInput);
                return formatResponseForSDK(updatedClient);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            } catch (HttpResponseException e) {
                int responseStatusCode = e.statusCode;
                switch (responseStatusCode){
                    case 400 -> throw createCustomExceptionFromHttpResponseException(e, OAuthAPIInvalidInputException.class);
                    case 404 -> throw createCustomExceptionFromHttpResponseException(e, OAuthClientNotFoundException.class);
                    case 500 -> throw createCustomExceptionFromHttpResponseException(e, OAuthClientUpdateException.class); // hydra is not so helpful with the error messages at this endpoint..
                    default -> throw new RuntimeException(e);
                }
            }
        }
    }

    private static JsonArray translateIncomingDataToHydraUpdateFormat(JsonObject input){
        JsonArray hydraPatchFormat = new JsonArray();
        for (Map.Entry<String, JsonElement> changeIt : input.entrySet()) {
            if (changeIt.getKey().equals("clientId")) {
                continue; // we are not updating clientIds!
            }
            hydraPatchFormat.add(translateToHydraPatch(changeIt.getKey(),changeIt.getValue()));
        }

        return hydraPatchFormat;
    }

    private static JsonObject translateToHydraPatch(String elementName, JsonElement newValue){
        JsonObject patchFormat = new JsonObject();
        String hydraElementName = Utils.camelCaseToSnakeCase(elementName);
        patchFormat.addProperty("from", "/" + hydraElementName);
        patchFormat.addProperty("path", "/" + hydraElementName);
        patchFormat.addProperty("op", "replace"); // What was sent by the sdk should be handled as a complete new value for the property
        patchFormat.add("value", newValue);

        return patchFormat;
    }

    private static <T extends OAuthException> T createCustomExceptionFromHttpResponseException(HttpResponseException exception, Class<T> customExceptionClass)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        String errorMessage = exception.rawMessage;
        JsonObject errorResponse = (JsonObject) new JsonParser().parse(errorMessage);
        String error = errorResponse.get("error").getAsString();
        String errorDescription = errorResponse.get("error_description").getAsString();
        return customExceptionClass.getDeclaredConstructor(String.class, String.class).newInstance(error, errorDescription);
    }

    private static JsonObject constructHydraRequestParamsForRegisterClientPOST(JsonObject paramsFromSdk, String generatedClientId){
        JsonObject requestBody = new JsonObject();

        //translating camelCase keys to snakeCase keys
        for (Map.Entry<String, JsonElement> jsonEntry : paramsFromSdk.entrySet()){
            requestBody.add(Utils.camelCaseToSnakeCase(jsonEntry.getKey()), jsonEntry.getValue());
        }

        //add client_id
        requestBody.addProperty("client_id", generatedClientId);

        //setting other non-changing defaults
        requestBody.addProperty("access_token_strategy", "jwt");
        requestBody.addProperty("skip_consent", true);
        requestBody.addProperty("subject_type", "public");

        return requestBody;
    }

    private static JsonObject formatResponseForSDK(JsonObject response) {
        JsonObject formattedResponse = new JsonObject();

        //translating snake_case keys to camelCase keys
        for (Map.Entry<String, JsonElement> jsonEntry : response.entrySet()){
            formattedResponse.add(Utils.snakeCaseToCamelCase(jsonEntry.getKey()), jsonEntry.getValue());
        }

        return formattedResponse;
    }

    private static Map<String, String> constructHydraRequestParamsForAuthorizationGETAPICall(JsonObject inputFromSdk) {
        Map<String, String> queryParamsForHydra = new HashMap<>();
        for(Map.Entry<String, JsonElement> jsonElement : inputFromSdk.entrySet()){
            queryParamsForHydra.put(jsonElement.getKey(), jsonElement.getValue().getAsString());
        }
        return  queryParamsForHydra;
    }

    private static String getValueOfQueryParam(String url, String queryParam){
        String valueOfQueryParam = "";
        if(!queryParam.endsWith("=")){
            queryParam = queryParam + "=";
        }
        int startIndex = url.indexOf(queryParam) + queryParam.length(); // start after the '=' sign
        int endIndex = url.indexOf("&", startIndex);
        if (endIndex == -1){
            endIndex = url.length();
        }
        valueOfQueryParam = url.substring(startIndex, endIndex); // substring the url from the '=' to the next '&' or to the end of the url if there are no more &s
        return URLDecoder.decode(valueOfQueryParam, StandardCharsets.UTF_8);
    }
;}
