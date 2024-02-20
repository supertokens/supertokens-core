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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.bulkimport.BulkImportUserPaginationContainer;
import io.supertokens.bulkimport.BulkImportUserPaginationToken;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage.BulkImportUserStatus;
import io.supertokens.pluginInterface.bulkimport.BulkImportUserInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class GetBulkImportUsers extends WebserverAPI {
    public GetBulkImportUsers(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/bulk-import/users";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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

        BulkImportUserStatus status = null;
        if (statusString != null) {
            try {
                status = BulkImportUserStatus.valueOf(statusString);
            } catch (IllegalArgumentException e) {
                throw new ServletException(new BadRequestException("Invalid value for status. Pass one of NEW, PROCESSING, or FAILED!"));
            }
        }

        AppIdentifierWithStorage appIdentifierWithStorage = null;

        try {
            appIdentifierWithStorage = getAppIdentifierWithStorageFromRequestAndEnforcePublicTenant(req);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }

        try {
            BulkImportUserPaginationContainer users = BulkImport.getUsers(appIdentifierWithStorage, limit, status, paginationToken);
            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");

            JsonArray usersJson = new JsonArray();
            for (BulkImportUserInfo user : users.users) {
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
}
