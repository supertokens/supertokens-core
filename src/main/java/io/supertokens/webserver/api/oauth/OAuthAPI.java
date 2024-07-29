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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.exceptions.OAuthException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.OAuthAuthResponse;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serial;

public class OAuthAPI extends WebserverAPI {
    @Serial
    private static final long serialVersionUID = -8734479943734920904L;

    public OAuthAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "oauth/auth";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // TODO Work in progress!
        try {
            OAuthAuthResponse authResponse = OAuth.getAuthorizationUrl(null, null,null, "a685663d-1b5d-4a70-b7f7-025ff2e2d7a4", "http://localhost.com:3031/auth/callback/ory", "code", "profile", "%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BDv%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD%EF%BF%BD");
            JsonObject response = new JsonObject();
            response.addProperty("redirectTo", authResponse.redirectTo);

            if(authResponse.cookies != null) {
                Gson gson = new Gson();
                String cookiesAsJson = gson.toJson(authResponse.cookies);
                response.addProperty("cookies", cookiesAsJson);
            }

        } catch (InvalidConfigException e) {
            throw new RuntimeException(e);
        } catch (HttpResponseException e) {
            throw new RuntimeException(e);
        } catch (OAuthException e) {
            throw new RuntimeException(e);
        } catch (StorageQueryException e) {
            throw new RuntimeException(e);
        } catch (TenantOrAppNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
