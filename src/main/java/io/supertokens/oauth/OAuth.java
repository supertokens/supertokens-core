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

import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.oauth.exceptions.OAuthAuthException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.OAuthStorage;
import io.supertokens.utils.Utils;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class OAuth {

    private static final String LOCATION_HEADER_NAME = "Location";
    private static final String COOKIES_HEADER_NAME = "Set-Cookie";
    private static final String ERROR_LITERAL = "error=";
    private static final String ERROR_DESCRIPTION_LITERAL = "error_description=";

    private static final String HYDRA_AUTH_ENDPOINT = "/oauth2/auth";


    public static OAuthAuthResponse getAuthorizationUrl(Main main, AppIdentifier appIdentifier, Storage storage, String clientId,
                                                        String redirectURI, String responseType, String scope, String state)
            throws InvalidConfigException, HttpResponseException, IOException, OAuthAuthException, StorageQueryException,
            TenantOrAppNotFoundException {

        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        String redirectTo = null;
        List<String> cookies = null;

        String publicOAuthProviderServiceUrl = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOAuthProviderPublicServiceUrl();
        String hydraInternalAddress = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOauthProviderUrlConfiguredInHydra();
        String hydraBaseUrlForConsentAndLogin = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main).getOauthProviderConsentLoginBaseUrl();

        if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
            throw new OAuthAuthException("invalid_client", "Client authentication failed (e.g., unknown client, no client authentication included, or unsupported authentication method). The requested OAuth 2.0 Client does not exist.");
        } else {
            // we query hydra
            Map<String, String> queryParamsForHydra = constructHydraRequestParamsForAuthorizationGETAPICall(clientId, redirectURI, responseType, scope, state);
            Map<String, List<String>> responseHeaders = new HashMap<>();

            //TODO maybe check response status code? Have to modify sendGetRequest.. for that
            HttpRequest.sendGETRequestWithResponseHeaders(main, "", Config.getBaseConfig(main).getOAuthProviderPublicServiceUrl() + HYDRA_AUTH_ENDPOINT, queryParamsForHydra, 10000, 10000, null, responseHeaders, false);

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
            }
            if(responseHeaders.containsKey(COOKIES_HEADER_NAME)){
                cookies = responseHeaders.get(COOKIES_HEADER_NAME);
            }
        }

        return new OAuthAuthResponse(redirectTo, cookies);
    }

    private static Map<String, String> constructHydraRequestParamsForAuthorizationGETAPICall(String clientId,
                                                                                             String redirectURI, String responseType, String scope, String state) {
        Map<String, String> queryParamsForHydra = new HashMap<>();
        queryParamsForHydra.put("client_id", clientId);
        queryParamsForHydra.put("redirect_uri", redirectURI);
        queryParamsForHydra.put("scope", scope);
        queryParamsForHydra.put("response_type", responseType);
        queryParamsForHydra.put("state", state);
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
