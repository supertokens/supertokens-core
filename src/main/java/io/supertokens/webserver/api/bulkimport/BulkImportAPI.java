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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.supertokens.Main;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.bulkimport.BulkImportUserPaginationContainer;
import io.supertokens.bulkimport.BulkImportUserPaginationToken;
import io.supertokens.bulkimport.BulkImportUserUtils;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage.BULK_IMPORT_USER_STATUS;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class BulkImportAPI extends WebserverAPI {
    public BulkImportAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/bulk-import/users";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // API is app specific
        String statusString = InputParser.getQueryParamOrThrowError(req, "status", true);
        String paginationToken = InputParser.getQueryParamOrThrowError(req, "paginationToken", true);
        Integer limit = InputParser.getIntQueryParamOrThrowError(req, "limit", true);

        if (limit != null) {
            if (limit > BulkImport.GET_USERS_PAGINATION_LIMIT) {
                throw new ServletException(
                        new BadRequestException("Max limit allowed is " + BulkImport.GET_USERS_PAGINATION_LIMIT));
            } else if (limit < 1) {
                throw new ServletException(new BadRequestException("limit must a positive integer with min value 1"));
            }
        } else {
            limit = BulkImport.GET_USERS_DEFAULT_LIMIT;
        }

        BULK_IMPORT_USER_STATUS status = null;
        if (statusString != null) {
            try {
                status = BULK_IMPORT_USER_STATUS.valueOf(statusString);
            } catch (IllegalArgumentException e) {
                throw new ServletException(new BadRequestException("Invalid value for status. Pass one of NEW, PROCESSING, or FAILED!"));
            }
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
            BulkImportUserPaginationContainer users = BulkImport.getUsers(appIdentifier, storage, limit, status, paginationToken);
            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");

            JsonArray usersJson = new JsonArray();
            for (BulkImportUser user : users.users) {
                usersJson.add(user.toJsonObject());
            }
            result.add("users", usersJson);

            if (users.nextPaginationToken != null) {
                result.addProperty("nextPaginationToken", users.nextPaginationToken);
            }
            super.sendJsonResponse(200, result, resp);
        } catch (BulkImportUserPaginationToken.InvalidTokenException e) {
            Logging.debug(main, null, Utils.exceptionStacktraceToString(e));
            throw new ServletException(new BadRequestException("invalid pagination token"));
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        JsonArray users = InputParser.parseArrayOrThrowError(input, "users", false);

        if (users.size() <= 0 || users.size() > BulkImport.MAX_USERS_TO_ADD) {
            JsonObject errorResponseJson = new JsonObject();
            String errorMsg = users.size() <= 0 ? "You need to add at least one user."
                    : "You can only add " + BulkImport.MAX_USERS_TO_ADD + " users at a time.";
            errorResponseJson.addProperty("error", errorMsg);
            throw new ServletException(new WebserverAPI.BadRequestException(errorResponseJson.toString()));
        }

        AppIdentifier appIdentifier = null;
        Storage storage = null;

        try {
            appIdentifier = getAppIdentifier(req);
            storage = enforcePublicTenantAndGetPublicTenantStorage(req);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }

        String[] allUserRoles = null;

        try {
            allUserRoles = StorageUtils.getUserRolesStorage(storage).getRoles(appIdentifier);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }

        JsonArray errorsJson = new JsonArray();
        List<BulkImportUser> usersToAdd = new ArrayList<>();

        BulkImportUserUtils bulkImportUserUtils = new BulkImportUserUtils(allUserRoles);
        for (int i = 0; i < users.size(); i++) {
            try {
                BulkImportUser user = bulkImportUserUtils.createBulkImportUserFromJSON(main, appIdentifier, users.get(i).getAsJsonObject(), Utils.getUUID());
                usersToAdd.add(user);
            } catch (io.supertokens.bulkimport.exceptions.InvalidBulkImportDataException e) {
                JsonObject errorObj = new JsonObject();

                JsonArray errors = e.errors.stream()
                        .map(JsonPrimitive::new)
                        .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);

                errorObj.addProperty("index", i);
                errorObj.add("errors", errors);
                errorsJson.add(errorObj);
            } catch (StorageQueryException | TenantOrAppNotFoundException e) {
                throw new ServletException(e);
            }
        }

        if (errorsJson.size() > 0) {
            JsonObject errorResponseJson = new JsonObject();
            errorResponseJson.addProperty("error",
                    "Data has missing or invalid fields. Please check the users field for more details.");
            errorResponseJson.add("users", errorsJson);
            throw new ServletException(new WebserverAPI.BadRequestException(errorResponseJson.toString()));
        }

        try {
            BulkImport.addUsers(appIdentifier, storage, usersToAdd);
        } catch (TenantOrAppNotFoundException | StorageQueryException e) {
            throw new ServletException(e);
        }

        JsonObject result = new JsonObject();
        result.addProperty("status", "OK");
        super.sendJsonResponse(200, result, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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
