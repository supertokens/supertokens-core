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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.AppIdentifierWithStorageAndUserIdMapping;
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
            UserInfo user = null;

            try {
                if (userId != null) {
                    // Query by userId
                    AppIdentifierWithStorageAndUserIdMapping appIdentifierWithStorageAndUserIdMapping =
                            this.getAppIdentifierWithStorageAndUserIdMappingFromRequest(req, userId, UserIdType.ANY);
                    // if a userIdMapping exists, pass the superTokensUserId to the getUserUsingId function
                    if (appIdentifierWithStorageAndUserIdMapping.userIdMapping != null) {
                        userId = appIdentifierWithStorageAndUserIdMapping.userIdMapping.superTokensUserId;
                    }

                    user = EmailPassword.getUserUsingId(
                            appIdentifierWithStorageAndUserIdMapping.appIdentifierWithStorage, userId);

                    // if the userIdMapping exists set the userId in the response to the externalUserId
                    if (user != null && appIdentifierWithStorageAndUserIdMapping.userIdMapping != null) {
                        user.id = appIdentifierWithStorageAndUserIdMapping.userIdMapping.externalUserId;
                    }

                } else {
                    // API is tenant specific for get by Email
                    // Query by email
                    String normalisedEmail = Utils.normaliseEmail(email);
                    TenantIdentifierWithStorage tenantIdentifierWithStorage = this.getTenantIdentifierWithStorageFromRequest(req);
                    user = EmailPassword.getUserUsingEmail(tenantIdentifierWithStorage, normalisedEmail);

                    // if a userIdMapping exists, set the userId in the response to the externalUserId
                    if (user != null) {
                        io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping =
                                UserIdMapping.getUserIdMapping(
                                        getAppIdentifierWithStorage(req), user.id, UserIdType.SUPERTOKENS);
                        if (userIdMapping != null) {
                            user.id = userIdMapping.externalUserId;
                        }
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
                JsonObject userJson = new JsonParser().parse(new Gson().toJson(user)).getAsJsonObject();

                if (getVersionFromRequest(req).lesserThan(SemVer.v3_0)) {
                    userJson.remove("tenantIds");
                }

                result.add("user", userJson);
                super.sendJsonResponse(200, result, resp);
            }

        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        String email = InputParser.parseStringOrThrowError(input, "email", true);
        String password = InputParser.parseStringOrThrowError(input, "password", true);

        assert userId != null;

        if (email == null && password == null) {
            throw new ServletException(new BadRequestException("You have to provide either email or password."));
        }

        email = Utils.normaliseEmail(email);
        AppIdentifier appIdentifier = null;
        try {
            appIdentifier = this.getAppIdentifierWithStorage(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        try {
            AppIdentifierWithStorageAndUserIdMapping appIdentifierWithStorageAndUserIdMapping =
                    this.getAppIdentifierWithStorageAndUserIdMappingFromRequest(req, userId, UserIdType.ANY);
            // if a userIdMapping exists, pass the superTokensUserId to the updateUsersEmailOrPassword
            if (appIdentifierWithStorageAndUserIdMapping.userIdMapping != null) {
                userId = appIdentifierWithStorageAndUserIdMapping.userIdMapping.superTokensUserId;
            }

            EmailPassword.updateUsersEmailOrPassword(
                    appIdentifierWithStorageAndUserIdMapping.appIdentifierWithStorage,
                    main, userId, email, password);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException | StorageTransactionLogicException | TenantOrAppNotFoundException e) {
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
        }
    }
}
