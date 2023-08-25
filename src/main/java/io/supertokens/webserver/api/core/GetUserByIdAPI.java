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

import com.google.gson.JsonObject;
import io.supertokens.AppIdentifierWithStorageAndUserIdMapping;
import io.supertokens.Main;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class GetUserByIdAPI extends WebserverAPI {

    public GetUserByIdAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/user/id";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        String userId = InputParser.getQueryParamOrThrowError(req, "userId", false);

        try {
            AuthRecipeUserInfo user = null;

            try {
                AppIdentifierWithStorageAndUserIdMapping appIdentifierWithStorageAndUserIdMapping =
                        this.getAppIdentifierWithStorageAndUserIdMappingFromRequest(req, userId, UserIdType.ANY);
                // if a userIdMapping exists, pass the superTokensUserId to the getUserUsingId function
                if (appIdentifierWithStorageAndUserIdMapping.userIdMapping != null) {
                    userId = appIdentifierWithStorageAndUserIdMapping.userIdMapping.superTokensUserId;
                }

                user = AuthRecipe.getUserById(appIdentifierWithStorageAndUserIdMapping.appIdentifierWithStorage,
                        userId);

                // if a userIdMapping exists, set the userId in the response to the externalUserId
                if (user != null) {
                    UserIdMapping.populateExternalUserIdForUsers(appIdentifierWithStorageAndUserIdMapping.appIdentifierWithStorage, new AuthRecipeUserInfo[]{user});
                }

            } catch (UnknownUserIdException e) {
                // ignore the error so that the use can remain a null
            }

            if (user == null) {
                JsonObject result = new JsonObject();
                result.addProperty("status", "UNKNOWN_USER_ID_ERROR");
                super.sendJsonResponse(200, result, resp);

            } else {
                JsonObject result = new JsonObject();
                result.addProperty("status", "OK");
                JsonObject userJson = user.toJson();

                result.add("user", userJson);
                super.sendJsonResponse(200, result, resp);
            }

        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

    }
}
