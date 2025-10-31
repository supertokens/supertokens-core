/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.core;

import java.io.IOException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ListUsersByAccountInfoAPI extends WebserverAPI {

    public ListUsersByAccountInfoAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/users/by-accountinfo";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific.
        String email = InputParser.getQueryParamOrThrowError(req, "email", true);
        String phoneNumber = Utils.normalizeIfPhoneNumber(
                InputParser.getQueryParamOrThrowError(req, "phoneNumber", true));
        String thirdPartyId = InputParser.getQueryParamOrThrowError(req, "thirdPartyId", true);
        String thirdPartyUserId = InputParser.getQueryParamOrThrowError(req, "thirdPartyUserId", true);
        String webauthnCredentialId = InputParser.getQueryParamOrThrowError(req, "webauthnCredentialId", true);

        String doUnionOfAccountInfoStr = InputParser.getQueryParamOrThrowError(req, "doUnionOfAccountInfo", false);
        if (!(doUnionOfAccountInfoStr.equals("false") || doUnionOfAccountInfoStr.equals("true"))) {
            throw new ServletException(new BadRequestException(
                    "'doUnionOfAccountInfo' should be either 'true' or 'false'"));
        }
        boolean doUnionOfAccountInfo = doUnionOfAccountInfoStr.equals("true");

        if (email != null) {
            email = Utils.normaliseEmail(email);
        }
        if (thirdPartyId != null || thirdPartyUserId != null) {
            if (thirdPartyId == null || thirdPartyUserId == null) {
                throw new ServletException(new BadRequestException(
                        "If 'thirdPartyId' is provided, 'thirdPartyUserId' must also be provided, and vice versa"));
            }
        }

        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = this.getTenantStorage(req);
            AuthRecipeUserInfo[] users = AuthRecipe.getUsersByAccountInfo(
                    tenantIdentifier, storage, doUnionOfAccountInfo, email, phoneNumber, thirdPartyId,
                    thirdPartyUserId, webauthnCredentialId);
            UserIdMapping.populateExternalUserIdForUsers(tenantIdentifier.toAppIdentifier(), storage, users);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            JsonArray usersJson = new JsonArray();
            for (AuthRecipeUserInfo userInfo : users) {
                usersJson.add(userInfo.toJson(getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_3)));
            }

            result.add("users", usersJson);
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

    }
}
