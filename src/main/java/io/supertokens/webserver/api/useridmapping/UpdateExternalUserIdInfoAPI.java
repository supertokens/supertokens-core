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
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.AppIdentifierWithStorageAndUserIdMapping;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serial;

public class UpdateExternalUserIdInfoAPI extends WebserverAPI {
    @Serial
    private static final long serialVersionUID = -6840794570968339459L;

    public UpdateExternalUserIdInfoAPI(Main main) {
        super(main, RECIPE_ID.USER_ID_MAPPING.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/userid/external-user-id-info";
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // this API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        // normalize userId
        userId = userId.trim();

        if (userId.length() == 0) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name 'userId' cannot be an empty String"));
        }

        String userIdTypeString = InputParser.parseStringOrThrowError(input, "userIdType", true);

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

        String externalUserIdInfo = InputParser.parseStringOrJSONNullOrThrowError(input, "externalUserIdInfo", false);

        // We do this check because it's possible that the function returns null when the user have given a JSON Null
        if (externalUserIdInfo != null) {
            // normalize externalUserIdInfo
            externalUserIdInfo = externalUserIdInfo.trim();

            if (externalUserIdInfo.length() == 0) {
                throw new ServletException(new WebserverAPI.BadRequestException(
                        "Field name 'externalUserIdInfo' cannot be an empty String"));
            }
        }

        try {
            AppIdentifierWithStorageAndUserIdMapping appIdentifierWithStorageAndUserIdMapping =
                    this.getAppIdentifierWithStorageAndUserIdMappingFromRequest(req, userId, userIdType);

            if (UserIdMapping.updateOrDeleteExternalUserIdInfo(
                    appIdentifierWithStorageAndUserIdMapping.appIdentifierWithStorage, userId, userIdType, externalUserIdInfo)) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "OK");
                super.sendJsonResponse(200, response, resp);
                return;
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "UNKNOWN_MAPPING_ERROR");
            super.sendJsonResponse(200, response, resp);

        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);

        } catch (UnknownUserIdException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "UNKNOWN_MAPPING_ERROR");
            super.sendJsonResponse(200, response, resp);
        }
    }
}
