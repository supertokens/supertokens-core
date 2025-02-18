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

package io.supertokens.test.oauth.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.oauth.HttpRequestForOAuthProvider;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.utils.SemVer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class OAuthAPIHelper {
    public static void resetOAuthProvider() {
        try {
            HttpRequestForOAuthProvider.Response clientsResponse = HttpRequestForOAuthProvider
                    .doGet("http://localhost:4445/admin/clients", new HashMap<>(), new HashMap<>());

            for (JsonElement client : clientsResponse.jsonResponse.getAsJsonArray()) {
                HttpRequestForOAuthProvider.doJsonDelete(
                        "http://localhost:4445/admin/clients/"
                                + client.getAsJsonObject().get("client_id").getAsString(),
                        new HashMap<>(), new HashMap<>(), new JsonObject());
            }

            // We query these in an effort to help with possible warm up issues
            HttpRequestForOAuthProvider.doGet("http://localhost:4444/.well-known/openid-configuration", new HashMap<>(), new HashMap<>());
            HttpRequestForOAuthProvider.doGet("http://localhost:4444/.well-known/jwks.json", new HashMap<>(), new HashMap<>());
            Thread.sleep(1000);
        } catch (Exception e) {
            //ignore error. later on the tests would fail anyway
        }
    }

    public static JsonObject createClient(Main main, JsonObject createClientBody) throws Exception {
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/oauth/clients", createClientBody, 5000, 5000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    public static JsonObject updateClient(Main main, JsonObject updateClientBody) throws Exception {
        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                "http://localhost:3567/recipe/oauth/clients", updateClientBody, 5000, 5000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    public static JsonObject auth(Main main, JsonObject authBody) throws Exception {
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/oauth/auth", authBody, 5000, 5000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    public static JsonObject token(Main main, JsonObject tokenBody) throws Exception {
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/oauth/token", tokenBody, 5000, 5000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    public static JsonObject acceptLoginRequest(Main main, Map<String, String> queryParams,
            JsonObject acceptLoginChallengeBody) throws Exception {
        String url = "http://localhost:3567/recipe/oauth/auth/requests/login/accept";
        if (queryParams != null && !queryParams.isEmpty()) {
            StringBuilder queryString = new StringBuilder("?");
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (queryString.length() > 1) {
                    queryString.append("&");
                }
                String encodedValue = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString());
                queryString.append(entry.getKey()).append("=").append(encodedValue);
            }
            url += queryString.toString();
        }

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                url, acceptLoginChallengeBody, 5000, 5000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    public static JsonObject acceptConsentRequest(Main main, Map<String, String> queryParams,
            JsonObject acceptConsentChallengeBody) throws Exception {
        String url = "http://localhost:3567/recipe/oauth/auth/requests/consent/accept";
        if (queryParams != null && !queryParams.isEmpty()) {
            StringBuilder queryString = new StringBuilder("?");
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (queryString.length() > 1) {
                    queryString.append("&");
                }
                String encodedValue = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString());
                queryString.append(entry.getKey()).append("=").append(encodedValue);
            }
            url += queryString.toString();
        }

        JsonObject response = HttpRequestForTesting.sendJsonPUTRequest(main, "",
                url, acceptConsentChallengeBody, 5000, 5000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    public static JsonObject revoke(Main main, JsonObject revokeRequestBody) throws Exception {
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/oauth/token/revoke", revokeRequestBody, 5000, 5000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    public static JsonObject introspect(Main main, JsonObject introspectRequestBody) throws Exception {
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/oauth/introspect", introspectRequestBody, 5000, 5000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    public static JsonObject revokeClientId(Main main, JsonObject revokeClientIdRequestBody) throws Exception {
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/oauth/tokens/revoke", revokeClientIdRequestBody, 5000, 5000, null,
                SemVer.v5_2.get(), "");
        return response;
    }

    public static JsonObject revokeSessionHandle(Main main, JsonObject revokeSessionHandleRequestBody)
            throws Exception {
        JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(main, "",
                "http://localhost:3567/recipe/oauth/session/revoke", revokeSessionHandleRequestBody, 5000, 5000, null,
                SemVer.v5_2.get(), "");
        return response;
    }
}
