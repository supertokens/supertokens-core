/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.useridmapping;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.useridmapping.exception.UnknownSuperTokensUserIdException;
import io.supertokens.pluginInterface.useridmapping.exception.UserIdMappingAlreadyExistsException;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serial;

public class UserIdMappingAPI extends WebserverAPI {

    @Serial
    private static final long serialVersionUID = -7940412104607165068L;

    public UserIdMappingAPI(Main main) {
        super(main, RECIPE_ID.USER_ID_MAPPING.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/userid/map";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String superTokensUserId = InputParser.parseStringOrThrowError(input, "superTokensUserId", false);

        // normalize superTokensUserId
        superTokensUserId = superTokensUserId.trim();

        if (superTokensUserId.length() == 0) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name 'superTokensUserId' cannot be an empty String"));
        }

        String externalUserId = InputParser.parseStringOrThrowError(input, "externalUserId", false);

        // normalize externalUserId
        externalUserId = externalUserId.trim();

        if (externalUserId.length() == 0) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name 'externalUserId' cannot be an empty String"));
        }

        String externalUserIdInfo = InputParser.parseStringOrJSONNullOrThrowError(input, "externalUserIdInfo", true);

        if (externalUserIdInfo != null) {
            // normalize externalUserIdInfo
            externalUserIdInfo = externalUserIdInfo.trim();

            if (externalUserIdInfo.length() == 0) {
                throw new ServletException(new WebserverAPI.BadRequestException(
                        "Field name 'externalUserIdInfo' cannot be an empty String"));
            }
        }

        try {

            UserIdMapping.createUserIdMapping(main, superTokensUserId, externalUserId, externalUserIdInfo);

            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            super.sendJsonResponse(200, response, resp);

        } catch (UnknownSuperTokensUserIdException e) {

            JsonObject response = new JsonObject();
            response.addProperty("status", "UNKNOWN_SUPERTOKENS_USER_ID_ERROR");
            super.sendJsonResponse(200, response, resp);

        } catch (UserIdMappingAlreadyExistsException e) {

            JsonObject response = new JsonObject();
            response.addProperty("status", "USER_ID_MAPPING_ALREADY_EXISTS_ERROR");
            response.addProperty("doesSuperTokensUserIdExist", e.doesSuperTokensUserIdExist);
            response.addProperty("doesExternalUserIdExist", e.doesExternalUserIdExist);
            super.sendJsonResponse(200, response, resp);

        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

        String userId = InputParser.getQueryParamOrThrowError(req, "userId", false);

        // normalize userId
        userId = userId.trim();
        if (userId.length() == 0) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name 'userId' cannot be an empty String"));
        }

        String userIdTypeString = InputParser.getQueryParamOrThrowError(req, "userIdType", true);

        UserIdType userIdType = UserIdType.ANY;

        if (userIdTypeString != null) {
            // normalize userIdTypeString
            userIdTypeString = userIdTypeString.trim();

            if (userIdTypeString.equals("SUPERTOKENS")) {
                userIdType = UserIdType.SUPERTOKENS;
            } else if (userIdTypeString.equals("EXTERNAL")) {
                userIdType = UserIdType.EXTERNAL;
            } else if (!userIdTypeString.equals("ANY")) {
                throw new ServletException(new WebserverAPI.BadRequestException(
                        "Field name 'userIdType' should be one of 'SUPERTOKENS', 'EXTERNAL' or 'ANY'"));
            }
        }

        try {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(main, userId, userIdType);
            if (userIdMapping == null) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "UNKNOWN_MAPPING_ERROR");
                super.sendJsonResponse(200, response, resp);
                return;
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.addProperty("superTokensUserId", userIdMapping.superTokensUserId);
            response.addProperty("externalUserId", userIdMapping.externalUserId);
            if (userIdMapping.externalUserIdInfo != null) {
                response.addProperty("externalUserIdInfo", userIdMapping.externalUserIdInfo);
            }
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }
    }
}
