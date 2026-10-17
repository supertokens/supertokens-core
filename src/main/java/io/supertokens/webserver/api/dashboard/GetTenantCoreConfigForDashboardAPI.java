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
import com.google.gson.JsonPrimitive;
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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetTenantCoreConfigForDashboardAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public static final String MASKED_VALUE = "********";

    // key=value pairs (URI query strings, connection attribute lists) whose key names a credential
    private static final Pattern SECRET_PARAM_PATTERN = Pattern.compile(
            "(?i)((?:^|[?&;\\s])[^=&;?\\s]*(?:password|passwd|pwd|secret|token|api_key|apikey|api-key)" +
                    "[^=&;?\\s]*=)[^&;\\s]*");

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
                // Secret values (e.g. database passwords, credentials in connection URIs) are never returned,
                // regardless of whether the core runs in SaaS mode.
                maskSecretValue(fieldJson);
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

    public static boolean isSecretConfigKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.endsWith("password") || k.endsWith("passwd") || k.endsWith("pwd") || k.contains("secret")
                || k.endsWith("_key") || k.endsWith("_keys");
    }

    public static String maskCredentialsInString(String value) {
        String result = value;

        // scheme://user:password@host... -> scheme://user:********@host...
        // The last '@' is used as the end of the user info so that a password containing '@' is fully masked.
        int schemeEnd = result.indexOf("://");
        if (schemeEnd >= 0) {
            int userInfoStart = schemeEnd + 3;
            int userInfoEnd = result.lastIndexOf('@');
            if (userInfoEnd > userInfoStart) {
                int colon = result.substring(userInfoStart, userInfoEnd).indexOf(':');
                if (colon >= 0) {
                    result = result.substring(0, userInfoStart + colon + 1) + MASKED_VALUE
                            + result.substring(userInfoEnd);
                }
            }
        }

        // ...?password=xyz&... / password=xyz;... -> password=********
        Matcher matcher = SECRET_PARAM_PATTERN.matcher(result);
        result = matcher.replaceAll("$1" + Matcher.quoteReplacement(MASKED_VALUE));

        return result;
    }

    static void maskSecretValue(JsonObject fieldJson) {
        JsonElement keyElement = fieldJson.get("key");
        JsonElement value = fieldJson.get("value");
        if (keyElement == null || keyElement.isJsonNull() || value == null || value.isJsonNull()) {
            return;
        }

        if (isSecretConfigKey(keyElement.getAsString())) {
            fieldJson.addProperty("value", MASKED_VALUE);
            return;
        }

        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String original = value.getAsString();
            String masked = maskCredentialsInString(original);
            if (!masked.equals(original)) {
                fieldJson.add("value", new JsonPrimitive(masked));
            }
        }
    }
}
