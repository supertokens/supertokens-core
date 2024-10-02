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

package io.supertokens.webserver.api.dashboard;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.config.CoreConfig;
import io.supertokens.pluginInterface.ConfigFieldInfo;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GetTenantCoreConfigForDashboardAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public GetTenantCoreConfigForDashboardAPI(Main main) {
        super(main, RECIPE_ID.DASHBOARD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/dashboard/tenant/core-config";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            ArrayList<ConfigFieldInfo> config = new ArrayList<>();
            List<ConfigFieldInfo> coreConfigFields = CoreConfig.getConfigFieldsInfoForDashboard(main,
                    getTenantIdentifier(req));
            List<ConfigFieldInfo> storageFields = StorageUtils.getDashboardStorage(getTenantStorage(req))
                    .getPluginConfigFieldsInfo();

            config.addAll(coreConfigFields);
            config.addAll(storageFields);

            JsonObject result = new JsonObject();

            JsonArray configJson = new JsonArray();
            for (ConfigFieldInfo field : config) {
                JsonObject fieldJson = new GsonBuilder().serializeNulls().create().toJsonTree(field).getAsJsonObject();
                configJson.add(fieldJson);
            }

            if (shouldProtectProtectedConfig(req)) {
                JsonArray configWithoutProtectedFields = new JsonArray();
                String[] protectedPluginFields = getTenantStorage(req)
                        .getProtectedConfigsFromSuperTokensSaaSUsers();
                for (JsonElement field : configJson) {
                    String fieldName = field.getAsJsonObject().get("key").getAsString();
                    if (!Arrays.asList(protectedPluginFields).contains(fieldName) &&
                            !Arrays.asList(CoreConfig.PROTECTED_CONFIGS).contains(fieldName)) {
                        configWithoutProtectedFields.add(field);
                    }
                }
                configJson = configWithoutProtectedFields;
            }

            result.addProperty("status", "OK");
            result.add("config", configJson);
            super.sendJsonResponse(200, result, resp);

        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }
}
