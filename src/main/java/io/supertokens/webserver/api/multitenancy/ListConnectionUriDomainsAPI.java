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
public class ListConnectionUriDomainsAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public ListConnectionUriDomainsAPI(Main main) {
        super(main, RECIPE_ID.MULTITENANCY.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/multitenancy/connectionuridomain/list";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = this.getTenantStorage(req);

            if (!tenantIdentifier.equals(new TenantIdentifier(null, null, null))) {
                throw new BadPermissionException(
                        "Only the public tenantId, public appId and default connectionUriDomain is allowed to list " +
                                "all " +
                                "connectionUriDomains and appIds associated with this " +
                                "core");
            }

            TenantConfig[] tenantConfigs = Multitenancy.getAllTenants(main);

            Map<String, Map<String, List<TenantConfig>>> cudToAppToTenants = new HashMap<>();
            for (TenantConfig tenantConfig : tenantConfigs) {
                if (!cudToAppToTenants.containsKey(tenantConfig.tenantIdentifier.getConnectionUriDomain())) {
                    cudToAppToTenants.put(tenantConfig.tenantIdentifier.getConnectionUriDomain(), new HashMap<>());
                }
                if (!cudToAppToTenants.get(tenantConfig.tenantIdentifier.getConnectionUriDomain())
                        .containsKey(tenantConfig.tenantIdentifier.getAppId())) {
                    cudToAppToTenants.get(tenantConfig.tenantIdentifier.getConnectionUriDomain())
                            .put(tenantConfig.tenantIdentifier.getAppId(), new ArrayList<>());
                }

                cudToAppToTenants.get(tenantConfig.tenantIdentifier.getConnectionUriDomain())
                        .get(tenantConfig.tenantIdentifier.getAppId()).add(tenantConfig);
            }

            JsonArray cudArray = new JsonArray();
            for (Map.Entry<String, Map<String, List<TenantConfig>>> entry : cudToAppToTenants.entrySet()) {
                String cud = entry.getKey();
                JsonObject cudObject = new JsonObject();
                cudObject.addProperty("connectionUriDomain", cud);
                JsonArray appsArray = new JsonArray();

                boolean shouldProtect = shouldProtectProtectedConfig(req);
                for (Map.Entry<String, List<TenantConfig>> entry2 : entry.getValue().entrySet()) {
                    String appId = entry2.getKey();
                    JsonObject appObject = new JsonObject();
                    appObject.addProperty("appId", appId);
                    JsonArray tenantsArray = new JsonArray();
                    for (TenantConfig tenantConfig : entry2.getValue()) {
                        JsonObject tenantConfigJson;

                        if (getVersionFromRequest(req).lesserThan(SemVer.v5_0)) {
                            tenantConfigJson = tenantConfig.toJsonLesserThanOrEqualTo4_0(shouldProtect, storage,
                                    CoreConfig.PROTECTED_CONFIGS);
                        } else {
                            tenantConfigJson = tenantConfig.toJson5_0(shouldProtect, storage,
                                    CoreConfig.PROTECTED_CONFIGS);
                        }

                        tenantsArray.add(tenantConfigJson);
                    }
                    appObject.add("tenants", tenantsArray);
                    appsArray.add(appObject);
                }
                cudObject.add("apps", appsArray);
                cudArray.add(cudObject);
            }

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.add("connectionUriDomains", cudArray);

            super.sendJsonResponse(200, result, resp);

        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }
    }
}
