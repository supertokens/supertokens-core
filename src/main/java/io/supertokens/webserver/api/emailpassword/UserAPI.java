/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.emailpassword;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.StorageAndUserIdMapping;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.EmailChangeNotAllowedException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class UserAPI extends WebserverAPI {

    private static final long serialVersionUID = -2225750492558064634L;

    public UserAPI(Main main) {
        super(main, RECIPE_ID.EMAIL_PASSWORD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/user";
    }

    @Deprecated
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific for get by Email and app specific for get by UserId
        String userId = InputParser.getQueryParamOrThrowError(req, "userId", true);
        String email = InputParser.getQueryParamOrThrowError(req, "email", true);

        // logic according to https://github.com/supertokens/supertokens-core/issues/111

        if (userId != null && email != null) {
            throw new ServletException(new BadRequestException("Please provide only one of userId or email"));
        }

        if (userId == null && email == null) {
            throw new ServletException(new BadRequestException("Please provide one of userId or email"));
        }

        try {
            // API is app specific for get by UserId
            AuthRecipeUserInfo user = null;
            AppIdentifier appIdentifier = getAppIdentifier(req);

            try {
                if (userId != null) {
                    // Query by userId
                    StorageAndUserIdMapping storageAndUserIdMapping =
                            this.enforcePublicTenantAndGetStorageAndUserIdMappingForAppSpecificApi(req, userId,
                                    UserIdType.ANY, true);
                    // if a userIdMapping exists, pass the superTokensUserId to the getUserUsingId function
                    if (storageAndUserIdMapping.userIdMapping != null) {
                        userId = storageAndUserIdMapping.userIdMapping.superTokensUserId;
                    }

                    user = EmailPassword.getUserUsingId(
                            appIdentifier,
                            storageAndUserIdMapping.storage, userId);
                    if (user != null) {
                        UserIdMapping.populateExternalUserIdForUsers(appIdentifier,
                                storageAndUserIdMapping.storage,
                                new AuthRecipeUserInfo[]{user});
                    }

                } else {
                    // API is tenant specific for get by Email
                    // Query by email
                    String normalisedEmail = Utils.normaliseEmail(email);
                    TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
                    Storage storage = this.getTenantStorage(req);
                    user = EmailPassword.getUserUsingEmail(tenantIdentifier, storage, normalisedEmail);

                    // if a userIdMapping exists, set the userId in the response to the externalUserId
                    if (user != null) {
                        UserIdMapping.populateExternalUserIdForUsers(appIdentifier, storage,
                                new AuthRecipeUserInfo[]{user});
                    }
                }
            } catch (UnknownUserIdException e) {
                // ignore the error so that the use can remain a null
            }

            if (user == null) {
                JsonObject result = new JsonObject();
                result.addProperty("status", userId != null ? "UNKNOWN_USER_ID_ERROR" : "UNKNOWN_EMAIL_ERROR");
                super.sendJsonResponse(200, result, resp);

            } else {
                JsonObject result = new JsonObject();
                result.addProperty("status", "OK");
                JsonObject userJson =
                        getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v4_0) ? user.toJson() :
                                user.toJsonWithoutAccountLinking();

                if (getVersionFromRequest(req).lesserThan(SemVer.v3_0)) {
                    userJson.remove("tenantIds");
                }

                result.add("user", userJson);
                super.sendJsonResponse(200, result, resp);
            }

        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }

    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String userId;

        if (getVersionFromRequest(req).lesserThan(SemVer.v4_0)) {
            userId = InputParser.parseStringOrThrowError(input, "userId", false);
        } else {
            userId = InputParser.parseStringOrThrowError(input, "recipeUserId", false);
        }
        String email = InputParser.parseStringOrThrowError(input, "email", true);
        String password = InputParser.parseStringOrThrowError(input, "password", true);

        assert userId != null;

        if (email == null && password == null) {
            throw new ServletException(new BadRequestException("You have to provide either email or password."));
        }

        email = Utils.normaliseEmail(email);
        AppIdentifier appIdentifier = null;
        try {
            appIdentifier = this.getAppIdentifier(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        try {
            StorageAndUserIdMapping storageAndUserIdMapping =
                    this.enforcePublicTenantAndGetStorageAndUserIdMappingForAppSpecificApi(req, userId,
                            UserIdType.ANY, true);
            // if a userIdMapping exists, pass the superTokensUserId to the updateUsersEmailOrPassword
            if (storageAndUserIdMapping.userIdMapping != null) {
                userId = storageAndUserIdMapping.userIdMapping.superTokensUserId;
            }

            EmailPassword.updateUsersEmailOrPassword(
                    appIdentifier,
                    storageAndUserIdMapping.storage,
                    main, userId, email, password);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException | StorageTransactionLogicException | TenantOrAppNotFoundException |
                 BadPermissionException e) {
            throw new ServletException(e);

        } catch (UnknownUserIdException e) {
            Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "UNKNOWN_USER_ID_ERROR");
            super.sendJsonResponse(200, result, resp);

        } catch (DuplicateEmailException e) {
            Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "EMAIL_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (EmailChangeNotAllowedException e) {
            Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "EMAIL_CHANGE_NOT_ALLOWED_ERROR");
            result.addProperty("reason", "New email is associated with another primary user ID");
            super.sendJsonResponse(200, result, resp);
        }
    }
}
