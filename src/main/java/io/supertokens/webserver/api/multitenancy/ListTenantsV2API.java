/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.multitenancy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.config.CoreConfig;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class ListTenantsV2API extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public ListTenantsV2API(Main main) {
        super(main, RECIPE_ID.MULTITENANCY.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/multitenancy/tenant/list/v2";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = getTenantStorage(req);

            if (!tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
                throw new BadPermissionException("Only the public tenantId is allowed to list all tenants " +
                        "associated with this app");
            }

            TenantConfig[] tenantConfigs = Multitenancy.getAllTenantsForApp(tenantIdentifier.toAppIdentifier(), main);
            JsonArray tenantsArray = new JsonArray();

            boolean shouldProtect = shouldProtectProtectedConfig(req);
            for (TenantConfig tenantConfig : tenantConfigs) {

                JsonObject tenantConfigJson = tenantConfig.toJson_v2_5_1(shouldProtect, storage,
                        CoreConfig.PROTECTED_CONFIGS);

                tenantsArray.add(tenantConfigJson);
            }

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.add("tenants", tenantsArray);

            super.sendJsonResponse(200, result, resp);

        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
