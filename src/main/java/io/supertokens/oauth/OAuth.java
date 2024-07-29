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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.oauth.exceptions.OAuthException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.OAuthAuthResponse;
import io.supertokens.pluginInterface.oauth.OAuthStorage;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OAuth {

    private static final String LOCATION_HEADER_NAME = "Location";
    private static final String COOKIES_HEADER_NAME = "Set-Cookie";

    public static OAuthAuthResponse getAuthorizationUrl(Main main, AppIdentifier appIdentifier, Storage storage, String clientId,
                                                        String redirectURI, String responseType, String scope, String state)
            throws InvalidConfigException, HttpResponseException, IOException, OAuthException, StorageQueryException,
            TenantOrAppNotFoundException {
        // TODO:
        // - validate that client_id is present for this tenant

        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        String redirectTo = null;
        List<String> cookies = null;

        String publicOAuthProviderServiceUrl = Config.getBaseConfig(main).getOAuthProviderPublicServiceUrl();

        if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
            redirectTo =  publicOAuthProviderServiceUrl +
                    "/oauth2/fallbacks/error?error=invalid_client&error_description=Client+authentication+failed+%28e" +
                    ".g.%2C+unknown+client%2C+no+client+authentication+included%2C+or+unsupported+authentication" +
                    "+method%29.+The+requested+OAuth+2.0+Client+does+not+exist.";
        } else {
            // we query hydra
            Map<String, String> queryParamsForHydra = constructHydraRequestParams(clientId, redirectURI, responseType, scope, state);
            Map<String, String> responseHeaders = new HashMap<>();
            HttpRequest.sendGETRequestWithResponseHeaders(main, null, Config.getBaseConfig(main).getOAuthProviderPublicServiceUrl(), queryParamsForHydra, 20, 400, null, responseHeaders); // TODO is there some kind of config for the timeouts?

            if(!responseHeaders.isEmpty() && responseHeaders.containsKey(LOCATION_HEADER_NAME)) {
                String locationHeaderValue = responseHeaders.get(LOCATION_HEADER_NAME);

                if (locationHeaderValue.equals(publicOAuthProviderServiceUrl)){
                    throw new OAuthException();
                }

                if (locationHeaderValue.equals("localhost:3000")) {
                    redirectTo = Multitenancy.getAPIDomain(storage, appIdentifier);
                } else {
                    redirectTo = locationHeaderValue;
                }
            }
            if(responseHeaders.containsKey(COOKIES_HEADER_NAME)){
                String allCookies = responseHeaders.get(COOKIES_HEADER_NAME);
                cookies = Arrays.asList(allCookies.split("; "));
            }
        }

        return new OAuthAuthResponse(redirectTo, cookies);
    }

    private static Map<String, String> constructHydraRequestParams(String clientId,
                                                            String redirectURI, String responseType, String scope, String state) {
        Map<String, String> queryParamsForHydra = new HashMap<>();
        queryParamsForHydra.put("clientId", clientId);
        queryParamsForHydra.put("redirectURI", redirectURI);
        queryParamsForHydra.put("scope", scope);
        queryParamsForHydra.put("responseType", responseType);
        queryParamsForHydra.put("state", state);
        return  queryParamsForHydra;
    }
}
