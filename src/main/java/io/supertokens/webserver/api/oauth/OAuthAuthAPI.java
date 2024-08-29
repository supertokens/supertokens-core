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

package io.supertokens.webserver.api.oauth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.supertokens.Main;
import io.supertokens.webserver.InputParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OAuthAuthAPI extends OAuthProxyBase {
    public OAuthAuthAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/auth";
    }

    @Override
    public OAuthProxyBase.ProxyProps[] getProxyProperties(HttpServletRequest req, JsonObject input) {
        return new OAuthProxyBase.ProxyProps[] {
            new OAuthProxyBase.ProxyProps(
                "POST", // apiMethod
                "GET", // method
                "/oauth2/auth", // path
                false, // proxyToAdmin
                false // camelToSnakeCaseConversion
            )
        };
    }

    @Override
    protected Map<String, String> getQueryParamsForProxy(HttpServletRequest req, JsonObject input) throws IOException, ServletException {
        JsonObject params = InputParser.parseJsonObjectOrThrowError(input, "params", false);

        return params.entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> e.getValue().getAsString()
        ));
    }

    @Override
    protected Map<String, String> getHeadersForProxy(HttpServletRequest req, JsonObject input) throws ServletException, IOException {
        String cookies = InputParser.parseStringOrThrowError(input, "cookies", true);

        Map<String, String> headers = new HashMap<>();

        if (cookies != null) {
            headers.put("Cookie", cookies);
        }

        return headers;
    }

    @Override
    protected void handleResponseFromProxyGET(HttpServletRequest req, HttpServletResponse resp, int statusCode, Map<String, List<String>> headers, String rawBody, JsonObject jsonBody) throws IOException, ServletException {
        if (headers == null || !headers.containsKey("Location")) {
            throw new IllegalStateException("Invalid response from hydra");
        }

        String redirectTo = headers.get("Location").get(0);
        List<String> cookies = headers.get("Set-Cookie");

        JsonObject response = new JsonObject();
        response.addProperty("redirectTo", redirectTo);

        JsonArray jsonCookies = new JsonArray();
        if (cookies != null) {
            for (String cookie : cookies) {
                jsonCookies.add(new JsonPrimitive(cookie));
            }
        }

        response.add("cookies", jsonCookies);
        response.addProperty("status", "OK");
        super.sendJsonResponse(200, response, resp);
    }
}
