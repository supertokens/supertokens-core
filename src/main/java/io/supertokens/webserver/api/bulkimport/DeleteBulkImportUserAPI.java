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

package io.supertokens.webserver.api.bulkimport;

import java.io.IOException;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.supertokens.Main;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class DeleteBulkImportUserAPI extends WebserverAPI {
    public DeleteBulkImportUserAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/bulk-import/users/remove";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        JsonArray arr = InputParser.parseArrayOrThrowError(input, "ids", false);

        if (arr.size() == 0) {
            throw new ServletException(new WebserverAPI.BadRequestException("Field name 'ids' cannot be an empty array"));
        }

        if (arr.size() > BulkImport.DELETE_USERS_LIMIT) {
            throw new ServletException(new WebserverAPI.BadRequestException("Field name 'ids' cannot contain more than "
                    + BulkImport.DELETE_USERS_LIMIT + " elements"));
        }

        String[] userIds = new String[arr.size()];

        for (int i = 0; i < userIds.length; i++) {
            String userId = InputParser.parseStringFromElementOrThrowError(arr.get(i), "ids", false);
            if (userId.isEmpty()) {
                throw new ServletException(new WebserverAPI.BadRequestException("Field name 'ids' cannot contain an empty string"));
            }
            userIds[i] = userId;
        }

        AppIdentifier appIdentifier = null;
        Storage storage = null;
    
        try {
            appIdentifier = getAppIdentifier(req);
            storage = enforcePublicTenantAndGetPublicTenantStorage(req);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }

        try {
            List<String> deletedIds = BulkImport.deleteUsers(appIdentifier, storage, userIds);

            JsonArray deletedIdsJson = new JsonArray();
            JsonArray invalidIds = new JsonArray();
    
            for (String userId : userIds) {
                if (deletedIds.contains(userId)) {
                    deletedIdsJson.add(new JsonPrimitive(userId));
                } else {
                    invalidIds.add(new JsonPrimitive(userId));
                }
            }
    
            JsonObject result = new JsonObject();
            result.add("deletedIds", deletedIdsJson);
            result.add("invalidIds", invalidIds);

            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }
    }
}
