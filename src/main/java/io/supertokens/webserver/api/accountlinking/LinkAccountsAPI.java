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
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.StorageAndUserIdMapping;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.pluginInterface.authRecipe.exceptions.AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException;
import io.supertokens.pluginInterface.authRecipe.exceptions.InputUserIdIsNotAPrimaryUserException;
import io.supertokens.pluginInterface.authRecipe.exceptions.CannotLinkSinceRecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class LinkAccountsAPI extends WebserverAPI {

    public LinkAccountsAPI(Main main) {
        super(main, RECIPE_ID.ACCOUNT_LINKING.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/accountlinking/user/link";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String inputRecipeUserId = InputParser.parseStringOrThrowError(input, "recipeUserId", false);
        String inputPrimaryUserId = InputParser.parseStringOrThrowError(input, "primaryUserId", false);

        AppIdentifier appIdentifier = null;
        try {
            appIdentifier = getAppIdentifier(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
        Storage primaryUserIdStorage = null;
        Storage recipeUserIdStorage = null;
        try {
            String recipeUserId = inputRecipeUserId;
            {
                StorageAndUserIdMapping mappingAndStorage =
                        enforcePublicTenantAndGetStorageAndUserIdMappingForAppSpecificApi(
                                req, inputRecipeUserId, UserIdType.ANY, true);
                if (mappingAndStorage.userIdMapping != null) {
                    recipeUserId = mappingAndStorage.userIdMapping.superTokensUserId;
                }
                recipeUserIdStorage = mappingAndStorage.storage;
            }
            String primaryUserId = inputPrimaryUserId;
            {
                StorageAndUserIdMapping mappingAndStorage =
                        enforcePublicTenantAndGetStorageAndUserIdMappingForAppSpecificApi(
                                req, inputPrimaryUserId, UserIdType.ANY, true);
                if (mappingAndStorage.userIdMapping != null) {
                    primaryUserId = mappingAndStorage.userIdMapping.superTokensUserId;
                }
                primaryUserIdStorage = mappingAndStorage.storage;
            }

            // we do a check based on user pool ID and not instance reference checks cause the user
            // could be in the same db, but their storage layers may just have different
            if (!primaryUserIdStorage.getUserPoolId().equals(
                    recipeUserIdStorage.getUserPoolId())) {
                throw new ServletException(
                        new BadRequestException(
                                "Cannot link users that are parts of different databases. Different pool IDs: " +
                                        primaryUserIdStorage.getUserPoolId() + " AND " +
                                        recipeUserIdStorage.getUserPoolId()));
            }

            AuthRecipe.LinkAccountsResult linkAccountsResult = AuthRecipe.linkAccounts(main,
                    appIdentifier, primaryUserIdStorage,
                    recipeUserId, primaryUserId);

            UserIdMapping.populateExternalUserIdForUsers(appIdentifier, primaryUserIdStorage,
                    new AuthRecipeUserInfo[]{linkAccountsResult.user});
            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.addProperty("accountsAlreadyLinked", linkAccountsResult.wasAlreadyLinked);
            response.add("user", linkAccountsResult.user.toJson(getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_3)));

            if (!linkAccountsResult.wasAlreadyLinked) {
                try {
                    ActiveUsers.updateLastActiveAfterLinking(
                            main, appIdentifier, primaryUserId, recipeUserId);
                } catch (Exception e) {
                    // ignore
                }
            }

            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException |
                 BadPermissionException e) {
            throw new ServletException(e);
        } catch (UnknownUserIdException e) {
            throw new ServletException(new BadRequestException("Unknown user ID provided"));
        } catch (AccountInfoAlreadyAssociatedWithAnotherPrimaryUserIdException e) {
            try {
                JsonObject response = new JsonObject();
                response.addProperty("status", "ACCOUNT_INFO_ALREADY_ASSOCIATED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR");
                io.supertokens.pluginInterface.useridmapping.UserIdMapping result = UserIdMapping.getUserIdMapping(
                        appIdentifier, primaryUserIdStorage, e.primaryUserId,
                        UserIdType.SUPERTOKENS);
                if (result != null) {
                    response.addProperty("primaryUserId", result.externalUserId);
                } else {
                    response.addProperty("primaryUserId", e.primaryUserId);
                }
                response.addProperty("description", e.getMessage());
                super.sendJsonResponse(200, response, resp);
            } catch (StorageQueryException ex) {
                throw new ServletException(ex);
            }
        } catch (CannotLinkSinceRecipeUserIdAlreadyLinkedWithAnotherPrimaryUserIdException e) {
            try {
                JsonObject response = new JsonObject();
                response.addProperty("status", "RECIPE_USER_ID_ALREADY_LINKED_WITH_ANOTHER_PRIMARY_USER_ID_ERROR");
                UserIdMapping.populateExternalUserIdForUsers(appIdentifier, recipeUserIdStorage,
                        new AuthRecipeUserInfo[]{e.recipeUser});
                response.addProperty("primaryUserId", e.recipeUser.getSupertokensOrExternalUserId());
                response.addProperty("description", e.getMessage());
                response.add("user", e.recipeUser.toJson(getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_3)));
                super.sendJsonResponse(200, response, resp);
            } catch (StorageQueryException ex) {
                throw new ServletException(ex);
            }
        } catch (InputUserIdIsNotAPrimaryUserException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "INPUT_USER_IS_NOT_A_PRIMARY_USER");
            super.sendJsonResponse(200, response, resp);
        }
    }
}
