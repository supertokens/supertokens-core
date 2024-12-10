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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.Main;
import io.supertokens.bulkimport.BulkImport;
import io.supertokens.bulkimport.BulkImportUserUtils;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.bulkimport.BulkImportUser;
import io.supertokens.pluginInterface.bulkimport.exceptions.BulkImportBatchInsertException;
import io.supertokens.pluginInterface.exceptions.DbInitException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class ImportUserAPI extends WebserverAPI {
    public ImportUserAPI(Main main) {
        super(main, "bulkimport");
    }

    @Override
    public String getPath() {
        return "/bulk-import/import";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // API is app specific

        if (StorageLayer.isInMemDb(main)) {
            throw new ServletException(new BadRequestException("This API is not supported in the in-memory database."));
        }

        JsonObject jsonUser = InputParser.parseJsonObjectOrThrowError(req);

        AppIdentifier appIdentifier = null;
        Storage storage = null;
        String[] allUserRoles = null;

        try {
            appIdentifier = getAppIdentifier(req);
            storage = enforcePublicTenantAndGetPublicTenantStorage(req);
            allUserRoles = StorageUtils.getUserRolesStorage(storage).getRoles(appIdentifier);
        } catch (TenantOrAppNotFoundException | BadPermissionException | StorageQueryException e) {
            throw new ServletException(e);
        }

        BulkImportUserUtils bulkImportUserUtils = new BulkImportUserUtils(allUserRoles);

        try {
            BulkImportUser user = bulkImportUserUtils.createBulkImportUserFromJSON(main, appIdentifier, jsonUser,
                    Utils.getUUID());

            AuthRecipeUserInfo importedUser = BulkImport.importUser(main, appIdentifier, user);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.add("user", importedUser.toJson());
            super.sendJsonResponse(200, result, resp);
        } catch (BulkImportBatchInsertException e) {
            JsonArray errors = new JsonArray();
            BulkImportBatchInsertException insertException = (BulkImportBatchInsertException) e.getCause();
            errors.addAll(
            insertException.exceptionByUserId.values().stream().map(exc -> exc.getMessage()).map(JsonPrimitive::new)
                    .collect(JsonArray::new, JsonArray::add, JsonArray::addAll)
            );
            JsonObject errorResponseJson = new JsonObject();
            errorResponseJson.add("errors", errors);
            throw new ServletException(new WebserverAPI.BadRequestException(errorResponseJson.toString()));
        } catch (io.supertokens.bulkimport.exceptions.InvalidBulkImportDataException e) {
            JsonArray errors = e.errors.stream()
                    .map(JsonPrimitive::new)
                    .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
            JsonObject errorResponseJson = new JsonObject();
            errorResponseJson.add("errors", errors);
            throw new ServletException(new WebserverAPI.BadRequestException(errorResponseJson.toString()));
        } catch (StorageQueryException storageQueryException){
            JsonArray errors = new JsonArray();
            errors.add(new JsonPrimitive(storageQueryException.getMessage()));
            JsonObject errorResponseJson = new JsonObject();
            errorResponseJson.add("errors", errors);
            throw new ServletException(new WebserverAPI.BadRequestException(errorResponseJson.toString()));
        } catch (TenantOrAppNotFoundException | InvalidConfigException | DbInitException e) {
            throw new ServletException(e);
        }
    }
}
