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

package io.supertokens.webserver.api.accountlinking;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.StorageAndUserIdMapping;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.authRecipe.exception.InputUserIdIsNotAPrimaryUserException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class UnlinkAccountAPI extends WebserverAPI {

    public UnlinkAccountAPI(Main main) {
        super(main, RECIPE_ID.ACCOUNT_LINKING.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/accountlinking/user/unlink";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String inputRecipeUserId = InputParser.parseStringOrThrowError(input, "recipeUserId", false);

        AppIdentifier appIdentifier = null;
        try {
            appIdentifier = getAppIdentifier(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
        Storage storage = null;
        try {
            String userId = inputRecipeUserId;
            StorageAndUserIdMapping mappingAndStorage =
                    enforcePublicTenantAndGetStorageAndUserIdMappingForAppSpecificApi(
                            req, inputRecipeUserId, UserIdType.ANY, true);
            if (mappingAndStorage.userIdMapping != null) {
                userId = mappingAndStorage.userIdMapping.superTokensUserId;
            }
            storage = mappingAndStorage.storage;

            boolean wasDeleted = AuthRecipe.unlinkAccounts(main, appIdentifier, storage, userId);
            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.addProperty("wasRecipeUserDeleted", wasDeleted);
            response.addProperty("wasLinked", true);
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        } catch (UnknownUserIdException e) {
            throw new ServletException(new BadRequestException("Unknown user ID provided"));
        } catch (InputUserIdIsNotAPrimaryUserException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.addProperty("wasRecipeUserDeleted", false);
            response.addProperty("wasLinked", false);
            super.sendJsonResponse(200, response, resp);
        }
    }
}
