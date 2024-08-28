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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.stream.Collectors;

public class OAuth {
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;

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

        throw new FeatureNotEnabledException(
                "OAuth feature is not enabled. Please subscribe to a SuperTokens core license key to enable this " +
                        "feature.");
    }

    public static Response handleOAuthProxyGET(Main main, AppIdentifier appIdentifier, Storage storage, String path, Map<String, String> queryParams, Map<String, String> headers) throws StorageQueryException, OAuthClientNotFoundException, TenantOrAppNotFoundException, FeatureNotEnabledException, InvalidConfigException, IOException, OAuthAPIException {
        checkForOauthFeature(appIdentifier, main);
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        if (queryParams.containsKey("client_id")) {
            String clientId = queryParams.get("client_id");
            if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
                throw new OAuthClientNotFoundException();
            }
        }

        // Request transformations
        queryParams = Transformations.transformQueryParamsForHydra(queryParams);
        headers = Transformations.transformRequestHeadersForHydra(headers);

        String publicOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        String fullUrl = publicOAuthProviderServiceUrl + path;

        Response response = doGet(fullUrl, headers, queryParams);

        // Response transformations
        response.jsonResponse = Transformations.transformJsonResponseFromHydra(response.jsonResponse);
        response.headers = Transformations.transformResponseHeadersFromHydra(main, appIdentifier, response.headers);

        checkNonSuccessResponse(response);

        return response;
    }

    public static Response handleOAuthProxyFormPOST(Main main, AppIdentifier appIdentifier, Storage storage, String path, Map<String, String> formFields, Map<String, String> headers) throws StorageQueryException, OAuthClientNotFoundException, TenantOrAppNotFoundException, FeatureNotEnabledException, InvalidConfigException, IOException, OAuthAPIException {
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

        Response response = doFormPost(fullUrl, headers, formFields);

        // Response transformations
        response.jsonResponse = Transformations.transformJsonResponseFromHydra(response.jsonResponse);
        response.headers = Transformations.transformResponseHeadersFromHydra(main, appIdentifier, response.headers);

        checkNonSuccessResponse(response);

        return response;
    }

    private static void checkNonSuccessResponse(Response response) throws OAuthAPIException {
        if (response.statusCode >= 400) {
            String error = response.jsonResponse.get("error").getAsString();
            String errorDebug = response.jsonResponse.get("error_debug").getAsString();
            String errorDescription = response.jsonResponse.get("error_description").getAsString();
            String errorHint = response.jsonResponse.get("error_hint").getAsString();
            throw new OAuthAPIException(error, errorDebug, errorDescription, errorHint, response.statusCode);
        }
    }

    public static JsonObject transformTokens(Main main, AppIdentifier appIdentifier, Storage storage, JsonObject jsonBody, String iss, boolean useDynamicKey) throws IOException, JWTException, InvalidKeyException, NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException, UnsupportedJWTSigningAlgorithmException, TenantOrAppNotFoundException, InvalidKeySpecException, JWTCreationException, InvalidConfigException {
        if (jsonBody.has("access_token")) {
            String accessToken = jsonBody.get("access_token").getAsString();
            accessToken = reSignToken(appIdentifier, main, accessToken, iss, 1, useDynamicKey, 0);
            jsonBody.addProperty("access_token", accessToken);
        }

        if (jsonBody.has("id_token")) {
            String idToken = jsonBody.get("id_token").getAsString();
            idToken = reSignToken(appIdentifier, main, idToken, iss, 2, useDynamicKey, 0);
            jsonBody.addProperty("id_token", idToken);
        }

        if (jsonBody.has("refresh_token")) {
            String refreshToken = jsonBody.get("refresh_token").getAsString();
            refreshToken = refreshToken.replace("ory_rt_", "st_rt_");
            jsonBody.addProperty("refresh_token", refreshToken);
        }

        return jsonBody;
    }

    private static String reSignToken(AppIdentifier appIdentifier, Main main, String token, String iss, int stt, boolean useDynamicSigningKey, int retryCount) throws IOException, JWTException, InvalidKeyException, NoSuchAlgorithmException, StorageQueryException, StorageTransactionLogicException, UnsupportedJWTSigningAlgorithmException, TenantOrAppNotFoundException, InvalidKeySpecException, JWTCreationException, InvalidConfigException {
        // Load the JWKS from the specified URL
        String publicOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        String jwksUrl = publicOAuthProviderServiceUrl + HYDRA_JWKS_PATH;

        // Check if cached keys are available for the jwksUrl
        Map<String, JsonObject> cachedKeys = jwksCache.get(jwksUrl);
        if (cachedKeys == null) {
            JsonObject jwksResponse;
            try {
                jwksResponse = HttpRequest.sendGETRequest(main, "", jwksUrl, null, 10000, 10000, null);
            } catch (HttpResponseException e) {
                throw new RuntimeException("Could not fetch JWKS keys from hydra for token verification");
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
                throw new RuntimeException("Could not verify token with hydra");
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
            throw new OAuthClientNotFoundException();
        } else {
            try {
                JsonObject hydraResponse = HttpRequest.sendGETRequest(main, "", adminOAuthProviderServiceUrl + HYDRA_CLIENTS_ENDPOINT + "/" + clientId, null, 10000, 10000, null);
                return  formatResponseForSDK(hydraResponse);
            } catch (HttpResponseException e) {
//              throw createCustomExceptionFromHttpResponseException(e, OAuthException.class);
                throw new IllegalStateException("FIXME"); // TODO fixme
            }
        }
    }

    public static void deleteOAuthClient(Main main, AppIdentifier appIdentifier, Storage storage, String clientId)
            throws TenantOrAppNotFoundException, InvalidConfigException, StorageQueryException,
            IOException, OAuthClientNotFoundException {
        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        String adminOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderAdminServiceUrl();

        if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
            throw new OAuthClientNotFoundException();
        } else {
            try {
                oauthStorage.removeAppClientAssociation(appIdentifier, clientId);
                HttpRequest.sendJsonDELETERequest(main, "", adminOAuthProviderServiceUrl + HYDRA_CLIENTS_ENDPOINT + "/" + clientId, null, 10000, 10000, null);
            } catch (HttpResponseException e) {
//                try {
//                    throw createCustomExceptionFromHttpResponseException(e, OAuthException.class);
//                } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
//                         IllegalAccessException ex) {
//                    throw new RuntimeException(ex);
//                }
                throw new IllegalStateException("FIXME"); // TODO fixme
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
            throw new OAuthClientNotFoundException();
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
                    case 404 -> throw new OAuthClientNotFoundException();
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


    public static Map<String, String> convertCamelToSnakeCase(Map<String, String> queryParams) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            result.put(Utils.camelCaseToSnakeCase(entry.getKey()), entry.getValue());
        }
        return result;
    }

    // HTTP Methods

    private static Response doGet(String url, Map<String, String> headers, Map<String, String> queryParams) throws IOException {
        URL obj = new URL(url + "?" + queryParams.entrySet().stream()
            .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&")));
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setInstanceFollowRedirects(false); // Do not follow redirect
        con.setRequestMethod("GET");
        con.setConnectTimeout(CONNECTION_TIMEOUT);
        con.setReadTimeout(READ_TIMEOUT);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                con.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        int responseCode = con.getResponseCode();
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        JsonObject jsonResponse = null;
        if (con.getContentType() != null && con.getContentType().contains("application/json")) {
            Gson gson = new Gson();
            jsonResponse = gson.fromJson(response.toString(), JsonObject.class);
        }
        return new Response(responseCode, response.toString(), jsonResponse, con.getHeaderFields());
    }

    private static Response doFormPost(String url, Map<String, String> headers, Map<String, String> formFields) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout(CONNECTION_TIMEOUT);
        con.setReadTimeout(READ_TIMEOUT);
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                con.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        try (DataOutputStream os = new DataOutputStream(con.getOutputStream())) {
            os.writeBytes(formFields.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&")));
        }
        int responseCode = con.getResponseCode();
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        JsonObject jsonResponse = null;
        if (con.getContentType().contains("application/json")) {
            Gson gson = new Gson();
            jsonResponse = gson.fromJson(response.toString(), JsonObject.class);
        }
        return new Response(responseCode, response.toString(), jsonResponse, con.getHeaderFields());
    }
    public static class Response {
        public int statusCode;
        public String rawResponse;
        public JsonObject jsonResponse;
        public Map<String, List<String>> headers;

        public Response(int statusCode, String rawResponse, JsonObject jsonResponse, Map<String, List<String>> headers) {
            this.statusCode = statusCode;
            this.rawResponse = rawResponse;
            this.jsonResponse = jsonResponse;
            this.headers = headers;
        }
    }
}
