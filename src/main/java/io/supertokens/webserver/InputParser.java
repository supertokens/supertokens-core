/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.webserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.backendAPI.Ping;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InputParser {
    public static JsonObject parseJsonObjectOrThrowError(HttpServletRequest request)
            throws ServletException, IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        try {
            return new JsonParser().parse(sb.toString()).getAsJsonObject();
        } catch (Exception e) {
            throw new ServletException(new WebserverAPI.BadRequestException("Invalid Json Input"));
        }
    }

    public static String getQueryParamOrThrowError(HttpServletRequest request, String fieldName, boolean nullable)
            throws ServletException {
        String value = request.getParameter(fieldName);
        if (!nullable && value == null) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name '" + fieldName + "' is missing in GET request"));
        }
        return value;
    }

    public static JsonObject parseJsonObjectOrThrowError(JsonObject element, String fieldName, boolean nullable)
            throws ServletException {
        try {
            if (nullable && element.get(fieldName) == null) {
                return null;
            }
            return element.get(fieldName).getAsJsonObject();
        } catch (Exception e) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name '" + fieldName + "' is invalid in JSON input"));
        }
    }

    public static String parseStringOrThrowError(JsonObject element, String fieldName, boolean nullable)
            throws ServletException {
        try {
            if (nullable && element.get(fieldName) == null) {
                return null;
            }
            String stringified = element.get(fieldName).toString();
            if (!stringified.contains("\"")) {
                throw new Exception();
            }
            return ((JsonObject) element).get(fieldName).getAsString();
        } catch (Exception e) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name '" + fieldName + "' is invalid in JSON input"));
        }
    }

    public static String parseStringFromElementOrThrowError(JsonElement element, String parentFieldName,
                                                            boolean nullable)
            throws ServletException {
        try {
            if (nullable && element == null) {
                return null;
            }
            String stringified = element.toString();
            if (!stringified.contains("\"")) {
                throw new Exception();
            }
            return element.getAsString();
        } catch (Exception e) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException(
                            "Field name '" + parentFieldName + "' is invalid in JSON input"));
        }
    }

    public static JsonArray parseArrayOrThrowError(JsonObject element, String fieldName, boolean nullable)
            throws ServletException {
        try {
            if (nullable && element.get(fieldName) == null) {
                return null;
            }
            return element.get(fieldName).getAsJsonArray();
        } catch (Exception e) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name '" + fieldName + "' is invalid in JSON input"));
        }
    }

    public static Boolean parseBooleanOrThrowError(JsonObject element, String fieldName, boolean nullable)
            throws ServletException {
        try {
            if (nullable && element.get(fieldName) == null) {
                return null;
            }
            String stringified = element.get(fieldName).toString();
            if (stringified.contains("\"")) {
                throw new Exception();
            }
            return element.get(fieldName).getAsBoolean();
        } catch (Exception e) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name '" + fieldName + "' is invalid in JSON input"));
        }
    }

    public static DeviceDriverInfo parseDeviceDriverInfo(JsonObject element) throws ServletException {
        try {
            if (!element.has("deviceDriverInfo")) {
                return null;
            }
            List<Ping.NameVersion> frontendSDK = new ArrayList<>();
            Ping.NameVersion driver = null;
            JsonObject deviceDriverInfo = element.getAsJsonObject("deviceDriverInfo");
            if (deviceDriverInfo.has("frontendSDK")) {
                JsonArray info = deviceDriverInfo.getAsJsonArray("frontendSDK");
                info.forEach(jsonElement -> {
                    JsonObject obj = jsonElement.getAsJsonObject();
                    Ping.NameVersion nv = new Ping.NameVersion(obj.get("name").getAsString(),
                            obj.get("version").getAsString());
                    if (!frontendSDK.contains(nv)) {
                        frontendSDK.add(nv);
                    }
                });
            }
            if (deviceDriverInfo.has("driver")) {
                JsonObject info = deviceDriverInfo.getAsJsonObject("driver");
                driver = new Ping.NameVersion(info.get("name").getAsString(), info.get("version").getAsString());
            }
            return new DeviceDriverInfo(frontendSDK, driver);
        } catch (Exception e) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Error while parsing deviceDriverInfo"));
        }
    }

    static class DeviceDriverInfo {
        @Nonnull
        List<Ping.NameVersion> frontendSDK;
        @Nullable
        Ping.NameVersion driver;

        DeviceDriverInfo(@Nonnull List<Ping.NameVersion> frontendSDK, @Nullable Ping.NameVersion driver) {
            this.frontendSDK = frontendSDK;
            this.driver = driver;
        }
    }
}
