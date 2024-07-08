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
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Deprecated
public class ListAppsAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public ListAppsAPI(Main main) {
        super(main, RECIPE_ID.MULTITENANCY.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/multitenancy/app/list";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = this.getTenantStorage(req);

            if (!tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)
                    || !tenantIdentifier.getAppId().equals(TenantIdentifier.DEFAULT_APP_ID)) {
                throw new BadPermissionException("Only the public tenantId and public appId is allowed to list " +
                        "all apps associated with this connection uri domain");
            }

            TenantConfig[] tenantConfigs = Multitenancy.getAllAppsAndTenantsForConnectionUriDomain(
                    tenantIdentifier.getConnectionUriDomain(), main);

            Map<String, List<TenantConfig>> appsToTenants = new HashMap<>();
            for (TenantConfig tenantConfig : tenantConfigs) {
                if (!appsToTenants.containsKey(tenantConfig.tenantIdentifier.getAppId())) {
                    appsToTenants.put(tenantConfig.tenantIdentifier.getAppId(), new ArrayList<>());
                }
                appsToTenants.get(tenantConfig.tenantIdentifier.getAppId()).add(tenantConfig);
            }

            boolean shouldProtect = shouldProtectProtectedConfig(req);
            JsonArray appsArray = new JsonArray();
            for (Map.Entry<String, List<TenantConfig>> entry : appsToTenants.entrySet()) {
                String appId = entry.getKey();
                JsonObject appObject = new JsonObject();
                appObject.addProperty("appId", appId);
                JsonArray tenantsArray = new JsonArray();
                for (TenantConfig tenantConfig : entry.getValue()) {
                    JsonObject tenantConfigJson;

                    if (getVersionFromRequest(req).lesserThan(SemVer.v5_0)) {
                        tenantConfigJson = tenantConfig.toJsonLesserThanOrEqualTo4_0(shouldProtect, storage,
                                CoreConfig.PROTECTED_CONFIGS);
                    } else {
                        tenantConfigJson = tenantConfig.toJson5_0(shouldProtect, storage, CoreConfig.PROTECTED_CONFIGS);
                    }

                    tenantsArray.add(tenantConfigJson);
                }
                appObject.add("tenants", tenantsArray);
                appsArray.add(appObject);
            }

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.add("apps", appsArray);

            super.sendJsonResponse(200, result, resp);

        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
