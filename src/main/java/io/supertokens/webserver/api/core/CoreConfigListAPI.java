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

package io.supertokens.webserver.api.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.supertokens.Main;
import io.supertokens.config.CoreConfig;
import io.supertokens.pluginInterface.ConfigFieldInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.webserver.WebserverAPI;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class CoreConfigListAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public CoreConfigListAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/core-config/list";
    }

    @Override
    protected boolean checkAPIKey(HttpServletRequest req) {
        return false;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        ArrayList<ConfigFieldInfo> config = CoreConfig.getConfigFieldsInfo(main);
        JsonObject result = new JsonObject();

        JsonArray configJson = new JsonArray();
        for (ConfigFieldInfo field : config) {
            JsonObject fieldJson = new JsonObject();
            fieldJson.addProperty("name", field.name);
            fieldJson.addProperty("description", field.description);
            fieldJson.addProperty("isDifferentAcrossTenants", field.isDifferentAcrossTenants);
            fieldJson.addProperty("type", field.type);
            if (field.options != null) {
                JsonArray options = new JsonArray();
                for (String option : field.options) {
                    options.add(new JsonPrimitive(option));
                }
                fieldJson.add("options", options);
            }
            configJson.add(fieldJson);
        }

        try {
            if (shouldProtectProtectedConfig(req)) {
                JsonArray configWithouProtectedFields = new JsonArray();
                String[] protectedFields = StorageLayer.getBaseStorage(main)
                        .getProtectedConfigsFromSuperTokensSaaSUsers();
                for (JsonElement field : configJson) {
                    String fieldName = field.getAsJsonObject().get("name").getAsString();
                    if (!Arrays.asList(protectedFields).contains(fieldName)) {
                        configWithouProtectedFields.add(field);
                    }
                }
                configJson = configWithouProtectedFields;
            }
        } catch (Exception e) {
        }

        result.addProperty("status", "OK");
        result.add("config", configJson);
        super.sendJsonResponse(200, result, resp);

    }
}
