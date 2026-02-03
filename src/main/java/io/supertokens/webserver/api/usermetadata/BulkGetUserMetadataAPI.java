/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.usermetadata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BulkGetUserMetadataAPI extends WebserverAPI {
    private static final long serialVersionUID = -3475605151671191144L;
    public static final int MAX_USER_IDS = 500;

    public BulkGetUserMetadataAPI(Main main) {
        super(main, RECIPE_ID.USER_METADATA.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/user/metadata/bulk";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        JsonArray userIdsArray = InputParser.parseArrayOrThrowError(input, "userIds", false);

        if (userIdsArray.size() == 0) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.add("users", new JsonArray());
            super.sendJsonResponse(200, result, resp);
            return;
        }

        if (userIdsArray.size() > MAX_USER_IDS) {
            throw new ServletException(new BadRequestException(
                    "You can only query up to " + MAX_USER_IDS + " users at a time."));
        }

        // Extract user IDs from the JSON array
        List<String> userIds = new ArrayList<>();
        for (JsonElement element : userIdsArray) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new ServletException(new BadRequestException("userIds array must contain only strings"));
            }
            userIds.add(element.getAsString());
        }

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            Map<String, JsonObject> metadataMap = UserMetadata.getBulkUserMetadata(appIdentifier, storage, userIds);

            // Build response
            JsonArray usersArray = new JsonArray();
            for (String userId : userIds) {
                JsonObject userObj = new JsonObject();
                userObj.addProperty("userId", userId);
                userObj.add("metadata", metadataMap.get(userId)); // null if user has no metadata
                usersArray.add(userObj);
            }

            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.add("users", usersArray);
            super.sendJsonResponse(200, response, resp);

        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
