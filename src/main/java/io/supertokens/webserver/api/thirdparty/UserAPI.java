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

package io.supertokens.webserver.api.thirdparty;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.AppIdentifierWithStorageAndUserIdMapping;
import io.supertokens.Main;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.thirdparty.UserInfo;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class UserAPI extends WebserverAPI {

    private static final long serialVersionUID = -2225750492558064634L;

    public UserAPI(Main main) {
        super(main, RECIPE_ID.THIRD_PARTY.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/user";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific for get by thirdPartyUserId
        // API is app specific for get by userId
        String userId = InputParser.getQueryParamOrThrowError(req, "userId", true);
        String thirdPartyId = InputParser.getQueryParamOrThrowError(req, "thirdPartyId", true);
        String thirdPartyUserId = InputParser.getQueryParamOrThrowError(req, "thirdPartyUserId", true);

        // logic according to https://github.com/supertokens/supertokens-core/issues/190#issuecomment-774671924

        if (userId != null && (thirdPartyId != null || thirdPartyUserId != null)) {
            throw new ServletException(
                    new BadRequestException("Please provide only one of userId or (thirdPartyId & thirdPartyUserId)"));
        }

        if (userId == null && (thirdPartyId == null || thirdPartyUserId == null)) {
            throw new ServletException(
                    new BadRequestException("Please provide one of userId or (thirdPartyId & thirdPartyUserId)"));
        }

        try {
            UserInfo user = null;
            if (userId != null) {
                // Query by userId
                try {
                    AppIdentifierWithStorageAndUserIdMapping appIdentifierWithStorageAndUserIdMapping =
                            this.getAppIdentifierWithStorageAndUserIdMappingFromRequest(req, userId, UserIdType.ANY);
                    // if a userIdMapping exists, pass the superTokensUserId to the getUserUsingId function
                    if (appIdentifierWithStorageAndUserIdMapping.userIdMapping != null) {
                        userId = appIdentifierWithStorageAndUserIdMapping.userIdMapping.superTokensUserId;
                    }

                    user = ThirdParty.getUser(appIdentifierWithStorageAndUserIdMapping.appIdentifierWithStorage, userId);
                    if (user != null && appIdentifierWithStorageAndUserIdMapping.userIdMapping != null) {
                        user.id = appIdentifierWithStorageAndUserIdMapping.userIdMapping.externalUserId;
                    }
                } catch (UnknownUserIdException e) {
                    // let the user be null
                }
            } else {
                user = ThirdParty.getUser(this.getTenantIdentifierWithStorageFromRequest(req), thirdPartyId,
                        thirdPartyUserId);
                if (user != null) {
                    io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                            .getUserIdMapping(this.getAppIdentifierWithStorage(req), user.id, UserIdType.SUPERTOKENS);
                    if (userIdMapping != null) {
                        user.id = userIdMapping.externalUserId;
                    }
                }
            }

            if (user == null) {
                JsonObject result = new JsonObject();
                result.addProperty("status",
                        userId != null ? "UNKNOWN_USER_ID_ERROR" : "UNKNOWN_THIRD_PARTY_USER_ERROR");
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
}
