/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.webauthn;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webauthn.WebAuthN;
import io.supertokens.webauthn.WebAuthNSignInUpResult;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SignInAPI extends WebserverAPI {

    public SignInAPI(Main main) {
        super(main, "webauthn");
    }

    @Override
    public String getPath() {
        return "/recipe/webauthn/signin";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = getTenantStorage(req);

            String webauthGeneratedOptionsId = InputParser.parseStringOrThrowError(input, "webauthGeneratedOptionsId",
                    false);
            JsonObject credentialsData = InputParser.parseJsonObjectOrThrowError(input, "credential", false);
            String credentialsDataString = new Gson().toJson(credentialsData);
            String credentialId = InputParser.parseStringOrThrowError(credentialsData, "id", false);

            WebAuthNSignInUpResult signInResult = WebAuthN.signIn(storage, tenantIdentifier, webauthGeneratedOptionsId,
                    credentialsDataString, credentialId);

            if (signInResult == null) {
                throw new ServletException("WebAuthN sign in failed");
            }

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.add("user", new Gson().fromJson(new Gson().toJson(signInResult.userInfo), JsonObject.class));
            result.add("options", new Gson().fromJson(new Gson().toJson(signInResult.options), JsonObject.class));

            super.sendJsonResponse(200, result, resp);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }
}
