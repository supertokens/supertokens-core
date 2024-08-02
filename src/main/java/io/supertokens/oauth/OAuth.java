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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.oauth.exceptions.*;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.OAuthStorage;
import io.supertokens.pluginInterface.oauth.exceptions.OAuth2ClientAlreadyExistsForAppException;
import io.supertokens.utils.Utils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

public class OAuth {

    private static final String LOCATION_HEADER_NAME = "Location";
    private static final String COOKIES_HEADER_NAME = "Set-Cookie";
    private static final String ERROR_LITERAL = "error=";
    private static final String ERROR_DESCRIPTION_LITERAL = "error_description=";

    private static final String HYDRA_AUTH_ENDPOINT = "/oauth2/auth";
    private static final String HYDRA_CLIENTS_ENDPOINT = "/admin/clients";

    public static OAuthAuthResponse getAuthorizationUrl(Main main, AppIdentifier appIdentifier, Storage storage, JsonObject paramsFromSdk)
            throws InvalidConfigException, HttpResponseException, IOException, OAuthAuthException, StorageQueryException,
            TenantOrAppNotFoundException {

        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        String redirectTo = null;
        List<String> cookies = null;

        String publicOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        String hydraInternalAddress = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOauthProviderUrlConfiguredInHydra();
        String hydraBaseUrlForConsentAndLogin = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOauthProviderConsentLoginBaseUrl();

        String clientId = paramsFromSdk.get("clientId").getAsString();

        if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
            throw new OAuthAuthException("invalid_client", "Client authentication failed (e.g., unknown client, no client authentication included, or unsupported authentication method). The requested OAuth 2.0 Client does not exist.");
        } else {
            // we query hydra
            Map<String, String> queryParamsForHydra = constructHydraRequestParamsForAuthorizationGETAPICall(paramsFromSdk);
            Map<String, List<String>> responseHeaders = new HashMap<>();

            HttpRequest.sendGETRequestWithResponseHeaders(main, "", publicOAuthProviderServiceUrl + HYDRA_AUTH_ENDPOINT, queryParamsForHydra, 10000, 10000, null, responseHeaders, false);

            if(!responseHeaders.isEmpty() && responseHeaders.containsKey(LOCATION_HEADER_NAME)) {
                String locationHeaderValue = responseHeaders.get(LOCATION_HEADER_NAME).get(0);
                if(Utils.containsUrl(locationHeaderValue, hydraInternalAddress, true)){
                    String error = getValueOfQueryParam(locationHeaderValue, ERROR_LITERAL);
                    String errorDescription = getValueOfQueryParam(locationHeaderValue, ERROR_DESCRIPTION_LITERAL);
                    throw new OAuthAuthException(error, errorDescription);
                }

                if(Utils.containsUrl(locationHeaderValue, hydraBaseUrlForConsentAndLogin, true)){
                    redirectTo = locationHeaderValue.replace(hydraBaseUrlForConsentAndLogin, "{apiDomain}");
                } else {
                    redirectTo = locationHeaderValue;
                }
            } else {
                throw new RuntimeException("Unexpected answer from Oauth Provider");
            }
            if(responseHeaders.containsKey(COOKIES_HEADER_NAME)){
                cookies = responseHeaders.get(COOKIES_HEADER_NAME);
            }
        }

        return new OAuthAuthResponse(redirectTo, cookies);
    }

    //This more or less acts as a pass-through for the sdks, apart from camelCase <-> snake_case key transformation and setting a few default values
    public static JsonObject registerOAuthClient(Main main, AppIdentifier appIdentifier, Storage storage, JsonObject paramsFromSdk)
            throws TenantOrAppNotFoundException, InvalidConfigException, IOException, OAuthClientRegisterException,
            NoSuchAlgorithmException, StorageQueryException {

        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);
        String adminOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderAdminServiceUrl();

        byte[] idBaseBytes = new byte[48];

        while(true){
            new SecureRandom().nextBytes(idBaseBytes);
            String clientId = "supertokens_" + Utils.hashSHA256Base64UrlSafe(idBaseBytes);
            try {
                if(oauthStorage.isClientIdAlreadyExists(clientId)){
                    continue; // restart, don't even try with Id which we know exists in hydra
                }

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
                                e, OAuthClientRegisterException.class);
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
            throw new OAuthClientNotFoundException("Unable to locate the resource", "");
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
            throw new OAuthClientNotFoundException("Unable to locate the resource", "");
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
            queryParamsForHydra.put(Utils.camelCaseToSnakeCase(jsonElement.getKey()), jsonElement.getValue().getAsString());
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
