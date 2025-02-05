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

package io.supertokens.webserver.api.webauthn;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webauthn.WebAuthN;
import io.supertokens.webauthn.WebauthNCredentialResponse;
import io.supertokens.webauthn.exception.InvalidWebauthNOptionsException;
import io.supertokens.webauthn.exception.WebauthNVerificationFailedException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class CredentialsRegisterAPI extends WebserverAPI {

    public CredentialsRegisterAPI(Main main) {
        super(main, "webauthn");
    }

    @Override
    public String getPath() {
        return "/recipe/webauthn/user/credential/register";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = getTenantStorage(req);

            JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
            String recipeUserId = InputParser.parseStringOrThrowError(input, "recipeUserId", false);
            String webauthnGeneratedOptionsId = InputParser.parseStringOrThrowError(input, "webauthnGeneratedOptionsId", false);
            JsonObject credentialsData = InputParser.parseJsonObjectOrThrowError(input, "credential", false);
            String credentialsDataString = new Gson().toJson(credentialsData);
            String credentialId = InputParser.parseStringOrThrowError(credentialsData, "id", false);

            WebauthNCredentialResponse savedCredential = WebAuthN
                    .registerCredentials(storage, tenantIdentifier, recipeUserId, credentialId,
                            webauthnGeneratedOptionsId, credentialsDataString);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("webauthnCredentialId", savedCredential.webauthnCredentialId);
            result.addProperty("recipeUserId", savedCredential.recipeUserId);
            result.addProperty("email", savedCredential.email);
            result.addProperty("relyingPartyId", savedCredential.relyingPartyId);
            result.addProperty("relyingPartyName", savedCredential.relyingPartyName);

            super.sendJsonResponse(200, result, resp);

        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        } catch (StorageQueryException e) {
            throw new RuntimeException(e);
        } catch (InvalidWebauthNOptionsException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "INVALID_OPTIONS_ERROR");
            result.addProperty("message", e.getMessage());
            sendJsonResponse(200, result, resp);
        } catch (WebauthNVerificationFailedException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "WEBAUTHN_VERIFICATION_FAILED_ERROR");
            result.addProperty("message", e.getMessage());
            sendJsonResponse(200, result, resp);
        }
    }
}
