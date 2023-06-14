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

import jakarta.servlet.ServletException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class Utils {

    private static final List<String> INVALID_WORDS_FOR_TENANTID = List.of("recipe", "config", "users");

    public static String normalizeAndValidateStringParam(String param, String paramName) throws ServletException {
        param = param.trim();
        if (param.length() == 0) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name '" + paramName + "' cannot be an empty String"));
        }
        return param;
    }

    public static String normalizeAndValidateConnectionUriDomain(String connectionUriDomain) throws ServletException {
        connectionUriDomain = connectionUriDomain.trim();
        connectionUriDomain = connectionUriDomain.toLowerCase();

        if (connectionUriDomain.length() == 0) {
            throw new ServletException(new WebserverAPI.BadRequestException("connectionUriDomain should not be an empty String"));
        }

        try {
            URL url = new URL("http://" + connectionUriDomain);

            if (url.getPath() != null && url.getPath().length() > 0) {
                throw new ServletException(new WebserverAPI.BadRequestException("connectionUriDomain is invalid"));
            }

            if (url.getAuthority() != null && url.getAuthority().length() > 0) {
                throw new ServletException(new WebserverAPI.BadRequestException("connectionUriDomain is invalid"));
            }

            connectionUriDomain = url.getHost();
        } catch (MalformedURLException e) {
            throw new ServletException(new WebserverAPI.BadRequestException("connectionUriDomain is invalid"));
        }

        return connectionUriDomain;
    }

    public static String normalizeAndValidateAppId(String appId) throws ServletException {
        appId = appId.trim();
        appId = appId.toLowerCase();

        if (appId.length() == 0) {
            throw new ServletException(new WebserverAPI.BadRequestException("appId should not be an empty String"));
        }

        if (appId.startsWith("appid-")) {
            throw new ServletException(new WebserverAPI.BadRequestException("appId must not start with 'appid-'"));
        }

        if (!appId.matches("^[a-z0-9-]+$")) {
            throw new ServletException(new WebserverAPI.BadRequestException("appId can only contain letters, numbers and hyphens"));
        }

        return appId;
    }

    public static String normalizeAndValidateTenantId(String tenantId) throws ServletException {
        tenantId = tenantId.trim();
        tenantId = tenantId.toLowerCase();

        if (tenantId.length() == 0) {
            throw new ServletException(new WebserverAPI.BadRequestException("tenantId should not be an empty String"));
        }

        if (INVALID_WORDS_FOR_TENANTID.contains(tenantId)) {
            throw new ServletException(new WebserverAPI.BadRequestException("Cannot use '" + tenantId + "'" + " as a tenantId"));
        }

        if (tenantId.startsWith("appid-")) {
            throw new ServletException(new WebserverAPI.BadRequestException("tenantId must not start with 'appid-'"));
        }

        if (!tenantId.matches("^[a-z0-9-]+$")) {
            throw new ServletException(new WebserverAPI.BadRequestException("tenantId can only contain letters, numbers and hyphens"));
        }

        return tenantId;
    }
}
