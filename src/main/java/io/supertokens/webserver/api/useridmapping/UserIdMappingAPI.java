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
import io.supertokens.StorageAndUserIdMapping;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.useridmapping.exception.UnknownSuperTokensUserIdException;
import io.supertokens.pluginInterface.useridmapping.exception.UserIdMappingAlreadyExistsException;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
        // this API is specific to app
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

        Boolean force = InputParser.parseBooleanOrThrowError(input, "force", true);
        if (force == null) {
            force = false;
        }

        try {
            UserIdMapping.createUserIdMapping(
                    getAppIdentifier(req), enforcePublicTenantAndGetAllStoragesForApp(req),
                    superTokensUserId, externalUserId, externalUserIdInfo, force,
                    getVersionFromRequest(req).greaterThanOrEqualTo(
                            SemVer.v4_0));

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

        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
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
            // If there exists a situation where 2 users on different user pools point to same external user id,
            // this API tries to be deterministic by considering the storage for the tenant on which this request was
            // made. if the user does not exist on the storage for the tenant on which this request was made,
            // this API will just return the first mapping it can find. It won't be deterministic

            // Example
            // (app1, tenant1, user1) -> externaluserid and (app1, tenant2, user2) -> externaluserid
            // Request from (app1, tenant1) will return user1 and request from (app1, tenant2) will return user2
            // Request from (app1, tenant3) may result in either user1 or user2

            StorageAndUserIdMapping storageAndUserIdMapping =
                    this.enforcePublicTenantAndGetStorageAndUserIdMappingForAppSpecificApi(req, userId, userIdType,
                            true);

            if (storageAndUserIdMapping.userIdMapping == null) {
                JsonObject response = new JsonObject();
                response.addProperty("status", "UNKNOWN_MAPPING_ERROR");
                super.sendJsonResponse(200, response, resp);
                return;
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.addProperty("superTokensUserId",
                    storageAndUserIdMapping.userIdMapping.superTokensUserId);
            response.addProperty("externalUserId",
                    storageAndUserIdMapping.userIdMapping.externalUserId);
            if (storageAndUserIdMapping.userIdMapping.externalUserIdInfo != null) {
                response.addProperty("externalUserIdInfo",
                        storageAndUserIdMapping.userIdMapping.externalUserIdInfo);
            }
            super.sendJsonResponse(200, response, resp);

        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);

        } catch (UnknownUserIdException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "UNKNOWN_MAPPING_ERROR");
            super.sendJsonResponse(200, response, resp);
        }
    }
}
