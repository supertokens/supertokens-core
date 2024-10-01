/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;

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

    public static String[] getCommaSeparatedStringArrayQueryParamOrThrowError(HttpServletRequest request,
                                                                              String fieldName, boolean nullable)
            throws ServletException {
        String[] value = null;
        // expect val1,val2,val3 and so on...
        String queryParamValue = getQueryParamOrThrowError(request, fieldName, nullable);
        if (queryParamValue != null) {
            value = Arrays.stream(queryParamValue.trim().split(",")).map(String::trim).filter(s -> !s.equals(""))
                    .toArray(String[]::new);
        }
        if (!nullable && value == null) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name '" + fieldName + "' is missing in GET request"));
        }
        return value;
    }

    public static Integer getIntQueryParamOrThrowError(HttpServletRequest request, String fieldName, boolean nullable)
            throws ServletException {
        String key = getQueryParamOrThrowError(request, fieldName, nullable);
        if (key == null && nullable) {
            return null;
        }
        try {
            assert key != null;
            return Integer.parseInt(key);
        } catch (Exception e) {
            throw new ServletException(new WebserverAPI.BadRequestException(
                    "Field name '" + fieldName + "' must be an int in the GET request"));
        }
    }

    public static Long getLongQueryParamOrThrowError(HttpServletRequest request, String fieldName, boolean nullable)
            throws ServletException {
        String key = getQueryParamOrThrowError(request, fieldName, nullable);
        if (key == null && nullable) {
            return null;
        }
        try {
            assert key != null;
            return Long.parseLong(key);
        } catch (Exception e) {
            throw new ServletException(new WebserverAPI.BadRequestException(
                    "Field name '" + fieldName + "' must be a long in the GET request"));
        }
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

    public static String parseStringOrJSONNullOrThrowError(JsonObject element, String fieldName, boolean nullable)
            throws ServletException {
        try {
            if (element.get(fieldName) != null && element.get(fieldName).isJsonNull()) {
                return null;
            }
        } catch (Exception e) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name '" + fieldName + "' is invalid in JSON input"));
        }
        return parseStringOrThrowError(element, fieldName, nullable);
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
                                                            boolean nullable) throws ServletException {
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
            throw new ServletException(new WebserverAPI.BadRequestException(
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

    public static Long parseLongOrThrowError(JsonObject element, String fieldName, boolean nullable)
            throws ServletException {
        try {
            if (nullable && element.get(fieldName) == null) {
                return null;
            }
            String stringified = element.toString();
            if (!stringified.contains("\"")) {
                throw new Exception();

            }
            return element.get(fieldName).getAsLong();

        } catch (Exception e) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name '" + fieldName + "' is invalid in JSON input"));

        }

    }


    public static Integer parseIntOrThrowError(JsonObject element, String fieldName, boolean nullable)
            throws ServletException {
        try {
            if (nullable && element.get(fieldName) == null) {
                return null;

            }
            String stringified = element.toString();
            if (!stringified.contains("\"")) {
                throw new Exception();

            }
            return element.get(fieldName).getAsInt();

        } catch (Exception e) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name '" + fieldName + "' is invalid in JSON input"));

        }

    }

}
