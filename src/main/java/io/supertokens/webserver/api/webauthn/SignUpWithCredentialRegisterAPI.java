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

import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.webauthn.exceptions.WebauthNOptionsNotExistsException;
import io.supertokens.utils.SemVer;
import io.supertokens.webauthn.WebAuthN;
import io.supertokens.webauthn.data.WebAuthNSignInUpResult;
import io.supertokens.webauthn.exception.InvalidWebauthNOptionsException;
import io.supertokens.webauthn.exception.WebauthNInvalidFormatException;
import io.supertokens.webauthn.exception.WebauthNVerificationFailedException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SignUpWithCredentialRegisterAPI extends WebserverAPI {

    public SignUpWithCredentialRegisterAPI(Main main) {
        super(main, "webauthn");
    }

    @Override
    public String getPath() {
        return "/recipe/webauthn/signup";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = getTenantStorage(req);

            JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
            String webauthnGeneratedOptionsId = InputParser.parseStringOrThrowError(input, "webauthnGeneratedOptionsId",
                    false);
            JsonObject credentialsData = InputParser.parseJsonObjectOrThrowError(input, "credential", false);

            WebAuthNSignInUpResult signUpResult = WebAuthN.signUp(storage, tenantIdentifier, webauthnGeneratedOptionsId,
                    credentialsData);

            ActiveUsers.updateLastActive(tenantIdentifier.toAppIdentifier(), main,
                    signUpResult.userInfo.getSupertokensUserId());

            JsonObject userJson = signUpResult.userInfo.toJson(getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_3));

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.add("user", userJson);
            result.addProperty("webauthnCredentialId", signUpResult.credential.id);
            result.addProperty("relyingPartyId", signUpResult.options.relyingPartyId);
            result.addProperty("relyingPartyName", signUpResult.options.relyingPartyName);
            result.addProperty("recipeUserId", signUpResult.credential.userId);

            super.sendJsonResponse(200, result, resp);
        } catch (
                TenantOrAppNotFoundException | StorageQueryException  e) {
            throw new ServletException(e);
        } catch (InvalidWebauthNOptionsException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "INVALID_OPTIONS_ERROR");
            result.addProperty("reason", e.getMessage());
            sendJsonResponse(200, result, resp);
        } catch (DuplicateEmailException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "EMAIL_ALREADY_EXISTS_ERROR");
            sendJsonResponse(200, result, resp);
        } catch (WebauthNVerificationFailedException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "INVALID_AUTHENTICATOR_ERROR");
            result.addProperty("reason", e.getMessage());
            sendJsonResponse(200, result, resp);
        } catch (WebauthNOptionsNotExistsException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "OPTIONS_NOT_FOUND_ERROR");
            sendJsonResponse(200, result, resp);
        } catch (WebauthNInvalidFormatException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "INVALID_CREDENTIALS_ERROR");
            result.addProperty("reason", e.getMessage());
            sendJsonResponse(200, result, resp);
        }
    }
}
