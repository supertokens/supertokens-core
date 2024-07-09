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
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.oauth.OAuthStorage;

public class OAuth {

    public static void getAuthorizationUrl(Main main, AppIdentifier appIdentifier, Storage storage, String clientId,
                                           String redirectURI, String responseType, String scope, String state)
            throws InvalidConfigException {
        // TODO:
        // - validate that client_id is present for this tenant
        // - call hydra
        // - if location header is:
        //     - localhost:3000, then we redirect to apiDomain
        //     - public url for hydra, then we throw a 400 error with the right json
        //     - else we redirect back to the client

        OAuthStorage oauthStorage = StorageUtils.getOAuthStorage(storage);

        String redirectTo = null;

        if (!oauthStorage.doesClientIdExistForThisApp(appIdentifier, clientId)) {
            redirectTo = Config.getBaseConfig(main).getOAuthProviderPublicServiceUrl() +
                    "/oauth2/fallbacks/error?error=invalid_client&error_description=Client+authentication+failed+%28e" +
                    ".g.%2C+unknown+client%2C+no+client+authentication+included%2C+or+unsupported+authentication" +
                    "+method%29.+The+requested+OAuth+2.0+Client+does+not+exist.";
        } else {
            // we query hydra
        }

        // TODO: parse url resposne and send appropriate reply from this API.
    }
}
