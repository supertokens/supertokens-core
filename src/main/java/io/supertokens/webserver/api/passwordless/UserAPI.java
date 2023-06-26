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

package io.supertokens.webserver.api.passwordless;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.AppIdentifierWithStorageAndUserIdMapping;
import io.supertokens.Main;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.Passwordless.FieldUpdate;
import io.supertokens.passwordless.exceptions.UserWithoutContactInfoException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.passwordless.UserInfo;
import io.supertokens.pluginInterface.passwordless.exception.DuplicatePhoneNumberException;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Objects;
import java.util.stream.Stream;

public class UserAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public UserAPI(Main main) {
        super(main, RECIPE_ID.PASSWORDLESS.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/user";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific for get by email or phone
        // API is app specific for get by id
        // logic based on: https://app.code2flow.com/flowcharts/617a9aafdc97ee415448db74
        String userId = InputParser.getQueryParamOrThrowError(req, "userId", true);
        String email = InputParser.getQueryParamOrThrowError(req, "email", true);
        String phoneNumber = InputParser.getQueryParamOrThrowError(req, "phoneNumber", true);

        if (Stream.of(userId, email, phoneNumber).filter(Objects::nonNull).count() != 1) {
            throw new ServletException(
                    new BadRequestException("Please provide exactly one of userId, email or phoneNumber"));
        }

        try {
            UserInfo user;
            if (userId != null) {
                try {
                    AppIdentifierWithStorageAndUserIdMapping appIdentifierWithStorageAndUserIdMapping =
                            this.getAppIdentifierWithStorageAndUserIdMappingFromRequest(req, userId, UserIdType.ANY);
                    if (appIdentifierWithStorageAndUserIdMapping.userIdMapping != null) {
                        userId = appIdentifierWithStorageAndUserIdMapping.userIdMapping.superTokensUserId;
                    }
                    user = Passwordless.getUserById(appIdentifierWithStorageAndUserIdMapping.appIdentifierWithStorage,
                            userId);

                    // if the userIdMapping exists set the userId in the response to the externalUserId
                    if (user != null && appIdentifierWithStorageAndUserIdMapping.userIdMapping != null) {
                        user.id = appIdentifierWithStorageAndUserIdMapping.userIdMapping.externalUserId;
                    }
                } catch (UnknownUserIdException e) {
                    user = null;
                }
            } else if (email != null) {
                email = Utils.normaliseEmail(email);
                user = Passwordless.getUserByEmail(this.getTenantIdentifierWithStorageFromRequest(req), email);
                if (user != null) {
                    UserIdMapping userIdMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                            this.getAppIdentifierWithStorage(req),
                            user.id, UserIdType.SUPERTOKENS);
                    if (userIdMapping != null) {
                        user.id = userIdMapping.externalUserId;
                    }
                }
            } else {
                user = Passwordless.getUserByPhoneNumber(this.getTenantIdentifierWithStorageFromRequest(req), phoneNumber);
                if (user != null) {
                    UserIdMapping userIdMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                            this.getAppIdentifierWithStorage(req),
                            user.id, UserIdType.SUPERTOKENS);
                    if (userIdMapping != null) {
                        user.id = userIdMapping.externalUserId;
                    }
                }
            }

            if (user == null) {
                JsonObject result = new JsonObject();
                result.addProperty("status", userId != null ? "UNKNOWN_USER_ID_ERROR"
                        : (email != null ? "UNKNOWN_EMAIL_ERROR" : "UNKNOWN_PHONE_NUMBER_ERROR"));
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
        // logic based on: https://app.code2flow.com/TXloWHJOwWKg
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String userId = InputParser.parseStringOrThrowError(input, "userId", false);

        FieldUpdate emailUpdate = !input.has("email") ? null
                : new FieldUpdate(input.get("email").isJsonNull() ? null
                        : Utils.normaliseEmail(InputParser.parseStringOrThrowError(input, "email", false)));

        FieldUpdate phoneNumberUpdate = !input.has("phoneNumber") ? null
                : new FieldUpdate(input.get("phoneNumber").isJsonNull() ? null
                        : InputParser.parseStringOrThrowError(input, "phoneNumber", false));

        try {
            AppIdentifierWithStorageAndUserIdMapping appIdentifierWithStorageAndUserIdMapping =
                    this.getAppIdentifierWithStorageAndUserIdMappingFromRequest(req, userId, UserIdType.ANY);
            // if a userIdMapping exists, pass the superTokensUserId to the updateUser
            if (appIdentifierWithStorageAndUserIdMapping.userIdMapping != null) {
                userId = appIdentifierWithStorageAndUserIdMapping.userIdMapping.superTokensUserId;
            }

            Passwordless.updateUser(appIdentifierWithStorageAndUserIdMapping.appIdentifierWithStorage,
                    userId, emailUpdate, phoneNumberUpdate);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        } catch (UnknownUserIdException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "UNKNOWN_USER_ID_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (DuplicateEmailException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "EMAIL_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (DuplicatePhoneNumberException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "PHONE_NUMBER_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (UserWithoutContactInfoException e) {
            throw new ServletException(
                    new BadRequestException("You cannot clear both email and phone number of a user"));
        }
    }
}
