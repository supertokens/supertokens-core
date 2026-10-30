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

public class GetTenantCoreConfigForDashboardAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public static final String MASKED_VALUE = "********";

    // String values longer than this are returned fully masked instead of being inspected.
    public static final int MAX_INSPECTED_VALUE_LENGTH = 8192;

    // Substrings that mark the key of a key=value pair (URI query, connection attributes) as a credential
    private static final String[] SECRET_PARAM_KEY_PARTS = new String[]{
            "password", "passwd", "pwd", "secret", "token", "api_key", "apikey", "api-key"};

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
        if (value.length() > MAX_INSPECTED_VALUE_LENGTH) {
            return MASKED_VALUE;
        }
        return maskSecretParams(maskUserInfoPassword(value));
    }

    // scheme://user:password@host... -> scheme://user:********@host..., scheme://token@host... -> scheme://********@host...
    // The user info is looked for in the authority (after "://", up to the first '/', '?' or '#'). If that part has no
    // '@' but reads "user:<not a port>", the password contains one of those characters, and the user info ends at the
    // last '@' of the value (this may mask more than the password, never less).
    private static String maskUserInfoPassword(String value) {
        int schemeEnd = value.indexOf("://");
        if (schemeEnd < 0) {
            return value;
        }
        int authorityStart = schemeEnd + 3;
        int authorityEnd = value.length();
        for (int i = authorityStart; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                authorityEnd = i;
                break;
            }
        }
        int userInfoEnd = value.lastIndexOf('@', authorityEnd - 1);
        if (userInfoEnd < authorityStart) {
            int colon = value.indexOf(':', authorityStart);
            if (colon < 0 || colon >= authorityEnd || value.charAt(authorityStart) == '['
                    || isPort(value, colon + 1, authorityEnd)) {
                return value;
            }
            userInfoEnd = value.lastIndexOf('@');
            if (userInfoEnd < authorityEnd) {
                return value;
            }
        }
        int colon = value.indexOf(':', authorityStart);
        if (colon < 0 || colon > userInfoEnd) {
            // user info without a password, e.g. a token: all of it is masked
            return value.substring(0, authorityStart) + MASKED_VALUE + value.substring(userInfoEnd);
        }
        return value.substring(0, colon + 1) + MASKED_VALUE + value.substring(userInfoEnd);
    }

    private static boolean isPort(String value, int start, int end) {
        for (int i = start; i < end; i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isParamKeyBoundary(char c) {
        return c == '?' || c == '&' || c == ';' || Character.isWhitespace(c);
    }

    private static boolean isSecretParamKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        for (String part : SECRET_PARAM_KEY_PARTS) {
            if (k.contains(part)) {
                return true;
            }
        }
        return false;
    }

    // ...?password=xyz&... / password=xyz;... -> password=********
    // A key starts at the beginning of the value or after '?', '&', ';' or whitespace. The value of a secret
    // key is masked up to the next '&' (the parameter separator the JDBC drivers use) or the end of the
    // string, so ';' or whitespace inside it does not end the masking. Single linear pass.
    private static String maskSecretParams(String value) {
        StringBuilder result = new StringBuilder(value.length());
        int i = 0;
        int n = value.length();
        boolean atKeyStart = true;
        while (i < n) {
            if (atKeyStart) {
                int keyEnd = i;
                while (keyEnd < n && value.charAt(keyEnd) != '=' && !isParamKeyBoundary(value.charAt(keyEnd))) {
                    keyEnd++;
                }
                if (keyEnd < n && value.charAt(keyEnd) == '=' && isSecretParamKey(value.substring(i, keyEnd))) {
                    int valueEnd = value.indexOf('&', keyEnd + 1);
                    if (valueEnd < 0) {
                        valueEnd = n;
                    }
                    result.append(value, i, keyEnd + 1).append(MASKED_VALUE);
                    i = valueEnd;
                    atKeyStart = false;
                    continue;
                }
                result.append(value, i, keyEnd);
                i = keyEnd;
                atKeyStart = false;
                continue;
            }
            char c = value.charAt(i);
            result.append(c);
            i++;
            atKeyStart = isParamKeyBoundary(c);
        }
        return result.toString();
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
