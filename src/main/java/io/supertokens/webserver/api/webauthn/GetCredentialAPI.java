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
import io.supertokens.Main;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.webauthn.WebAuthNStoredCredential;
import io.supertokens.pluginInterface.webauthn.exceptions.WebauthNCredentialNotExistsException;
import io.supertokens.webauthn.WebAuthN;
import io.supertokens.webauthn.utils.WebauthMapper;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class GetCredentialAPI extends WebserverAPI {


    public GetCredentialAPI(Main main) {
        super(main, "webauthn");
    }

    @Override
    public String getPath() {
        return "/recipe/webauthn/user/credential/";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = getTenantStorage(req);

            String recipeUserId = InputParser.getQueryParamOrThrowError(req, "recipeUserId", false);
            String credentialId = InputParser.getQueryParamOrThrowError(req, "webauthnCredentialId", false);

            WebAuthNStoredCredential credential = WebAuthN.loadCredentialByIdForUser(storage, tenantIdentifier, credentialId, recipeUserId);
            if (credential == null) {
                throw new WebauthNCredentialNotExistsException();
            }

            JsonObject result = WebauthMapper.mapStoredCredentialToResponse(credential);
            result.addProperty("status", "OK");

            sendJsonResponse(200, result, resp);
        } catch (TenantOrAppNotFoundException | StorageQueryException e) {
            throw new ServletException(e);
        } catch (WebauthNCredentialNotExistsException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "CREDENTIAL_NOT_FOUND_ERROR");
            sendJsonResponse(200, result, resp);
        }
    }
}
