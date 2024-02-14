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

import io.supertokens.Main;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.bulkimport.BulkImportStorage;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.exceptions.InvalidBulkImportDataException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class AddBulkImportUsers extends WebserverAPI {
    private static final int MAX_USERS_TO_ADD = 10000;

    public AddBulkImportUsers(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/bulk-import/add-users";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        JsonArray users = InputParser.parseArrayOrThrowError(input, "users", false);

        if (users.size() > MAX_USERS_TO_ADD) {
            JsonObject errorResponseJson = new JsonObject();
            errorResponseJson.addProperty("error", "You can only add 1000 users at a time.");
            super.sendJsonResponse(400, errorResponseJson, resp);
            return;
        }

        AppIdentifier appIdentifier = null;
        try {
            appIdentifier = getTenantIdentifierFromRequest(req).toAppIdentifier();
        } catch (ServletException e) {
            throw new ServletException(e);
        }

        TenantConfig[] allTenantConfigs = Multitenancy.getAllTenantsForApp(appIdentifier, main);
        ArrayList<String> validTenantIds = new ArrayList<>();
        Arrays.stream(allTenantConfigs)
                .forEach(tenantConfig -> validTenantIds.add(tenantConfig.tenantIdentifier.getTenantId()));

        JsonArray errorsJson = new JsonArray();
        ArrayList<BulkImportUser> usersToAdd = new ArrayList<>();

        for (int i = 0; i < users.size(); i++) {
            try {
                usersToAdd.add(new BulkImportUser(users.get(i).getAsJsonObject(), validTenantIds, null));
            } catch (InvalidBulkImportDataException e) {
                JsonObject errorObj = new JsonObject();

                JsonArray errors = e.errors.stream()
                        .map(JsonPrimitive::new)
                        .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);

                errorObj.addProperty("index", i);
                errorObj.add("errors", errors);

                errorsJson.add(errorObj);
            } catch (Exception e) {
                JsonObject errorObj = new JsonObject();
                errorObj.addProperty("index", i);
                errorObj.addProperty("errors", "An unknown error occurred");
                errorsJson.add(errorObj);
            }
        }

        if (errorsJson.size() > 0) {
            JsonObject errorResponseJson = new JsonObject();
            errorResponseJson.addProperty("error",
                    "Data has missing or invalid fields. Please check the users field for more details.");
            errorResponseJson.add("users", errorsJson);
            super.sendJsonResponse(400, errorResponseJson, resp);
            return;
        }

        try {
            AppIdentifierWithStorage appIdentifierWithStorage = getAppIdentifierWithStorage(req);
            BulkImportStorage storage = appIdentifierWithStorage.getBulkImportStorage();
            storage.addBulkImportUsers(appIdentifierWithStorage, usersToAdd);
        } catch (Exception e) {
            throw new ServletException(e);
        }

        JsonObject result = new JsonObject();
        result.addProperty("status", "OK");
        super.sendJsonResponse(200, result, resp);

    }
}
