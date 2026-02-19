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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.webauthn.exceptions.DuplicateCredentialException;
import io.supertokens.pluginInterface.webauthn.exceptions.WebauthNOptionsNotExistsException;
import io.supertokens.webauthn.WebAuthN;
import io.supertokens.webauthn.data.WebauthNCredentialResponse;
import io.supertokens.webauthn.exception.InvalidWebauthNOptionsException;
import io.supertokens.webauthn.exception.WebauthNInvalidFormatException;
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

            // useridmapping is not required to be called separately here because it is handled in the underlying parts
            WebauthNCredentialResponse savedCredential = WebAuthN
                    .registerCredentials(storage, tenantIdentifier, recipeUserId,
                            webauthnGeneratedOptionsId, credentialsData);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("webauthnCredentialId", savedCredential.webauthnCredentialId);
            result.addProperty("recipeUserId", savedCredential.recipeUserId);
            result.addProperty("email", savedCredential.email);
            result.addProperty("relyingPartyId", savedCredential.relyingPartyId);
            result.addProperty("relyingPartyName", savedCredential.relyingPartyName);

            super.sendJsonResponse(200, result, resp);

        } catch (TenantOrAppNotFoundException | StorageQueryException e) {
            throw new ServletException(e);
        } catch (InvalidWebauthNOptionsException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "INVALID_OPTIONS_ERROR");
            result.addProperty("reason", e.getMessage());
            sendJsonResponse(200, result, resp);
        } catch (WebauthNVerificationFailedException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "INVALID_AUTHENTICATOR_ERROR");
            result.addProperty("reason", e.getMessage());
            sendJsonResponse(200, result, resp);
        } catch (WebauthNInvalidFormatException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "INVALID_CREDENTIALS_ERROR");
            result.addProperty("reason", e.getMessage());
            sendJsonResponse(200, result, resp);
        } catch (WebauthNOptionsNotExistsException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "OPTIONS_NOT_FOUND_ERROR");
            sendJsonResponse(200, result, resp);
        } catch (DuplicateCredentialException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "CREDENTIAL_ALREADY_EXISTS_ERROR");
            sendJsonResponse(200, result, resp);
        } catch (UnknownUserIdException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "UNKNOWN_USER_ID_ERROR");
            sendJsonResponse(200, result, resp);
        }
    }
}
