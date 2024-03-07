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

package io.supertokens.webserver.api.userroles;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.userroles.exception.UnknownRoleException;
import io.supertokens.userroles.UserRoles;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serial;

public class RemovePermissionsForRoleAPI extends WebserverAPI {
    @Serial
    private static final long serialVersionUID = 6784464864634266617L;

    public RemovePermissionsForRoleAPI(Main main) {
        super(main, RECIPE_ID.USER_ROLES.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/role/permissions/remove";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String role = InputParser.parseStringOrThrowError(input, "role", false);

        // normalize role
        role = role.trim();
        if (role.length() == 0) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name 'role' cannot be an empty String"));
        }

        JsonArray arr = InputParser.parseArrayOrThrowError(input, "permissions", true);
        String[] permissions = null;
        if (arr != null) {
            permissions = new String[arr.size()];
            for (int i = 0; i < permissions.length; i++) {

                String permission = InputParser.parseStringFromElementOrThrowError(arr.get(i), "permissions", false);

                // normalize permission
                permission = permission.trim();
                if (permission.length() == 0) {
                    throw new ServletException(new WebserverAPI.BadRequestException(
                            "Field name 'permissions' cannot contain an empty string"));
                }
                permissions[i] = permission;
            }
        }

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);

            UserRoles.deletePermissionsFromRole(appIdentifier, storage, role, permissions);
            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException | StorageTransactionLogicException | TenantOrAppNotFoundException |
                 BadPermissionException e) {
            throw new ServletException(e);
        } catch (UnknownRoleException e) {
            JsonObject response = new JsonObject();
            response.addProperty("status", "UNKNOWN_ROLE_ERROR");
            super.sendJsonResponse(200, response, resp);
        }
    }
}
