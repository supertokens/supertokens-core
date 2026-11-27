/*
 *    Copyright (c) 2026, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.test.oauth;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.supertokens.Main;
import io.supertokens.oauth.OAuth;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.test.oauth.api.OAuthAPIHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An in-process stand-in for the OAuth provider, enough for the token and auth APIs to run their flows: the token
 * endpoint issues an access token for {@link #gid} (carrying {@link #sessionHandle} when set) and, while {@link
 * #issueRefreshToken} is on, a new refresh token; introspection reports every token as {@link #active}; the auth
 * endpoint redirects with an implicit-flow access token in the fragment. Use {@link #coreConfig()} as the core
 * config of the app that uses it.
 */
public final class MockOAuthProvider implements AutoCloseable {

    public static final String CLIENT_ID = "mock-provider-client";
    public static final String CLIENT_SECRET = "mock-provider-secret";

    public volatile String gid = "gid-1";
    public volatile String sessionHandle = null;
    public volatile boolean issueRefreshToken = true;
    public volatile boolean active = true;

    private final AtomicInteger refreshTokens = new AtomicInteger();
    private final HttpServer server;

    public MockOAuthProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public JsonObject coreConfig() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        JsonObject coreConfig = new JsonObject();
        coreConfig.addProperty("oauth_provider_public_service_url", url);
        coreConfig.addProperty("oauth_provider_admin_service_url", url);
        coreConfig.addProperty("oauth_provider_consent_login_base_url", "http://localhost:3001/auth");
        return coreConfig;
    }

    // Registers the client the requests below use, in core's storage only (the provider accepts any client).
    public static void addClient(Main main, AppIdentifier app, Storage storage, boolean enableRefreshTokenRotation)
            throws Exception {
        OAuth.addOrUpdateClient(main, app, storage, CLIENT_ID, CLIENT_SECRET, false, enableRefreshTokenRotation);
    }

    public static JsonObject authorizationCodeRequest() {
        JsonObject inputBody = new JsonObject();
        inputBody.addProperty("grant_type", "authorization_code");
        inputBody.addProperty("code", "some-code");
        inputBody.addProperty("redirect_uri", "http://localhost.com:3000/auth/callback/supertokens");
        return tokenRequest(inputBody);
    }

    public static JsonObject refreshRequest(String refreshToken) {
        JsonObject inputBody = new JsonObject();
        inputBody.addProperty("grant_type", "refresh_token");
        inputBody.addProperty("refresh_token", refreshToken);
        return tokenRequest(inputBody);
    }

    public static JsonObject clientCredentialsRequest() {
        JsonObject inputBody = new JsonObject();
        inputBody.addProperty("grant_type", "client_credentials");
        return tokenRequest(inputBody);
    }

    public static JsonObject implicitAuthRequest() {
        JsonObject params = new JsonObject();
        params.addProperty("client_id", CLIENT_ID);
        params.addProperty("redirect_uri", "http://localhost.com:3000/auth/callback/supertokens");
        params.addProperty("response_type", "token");
        params.addProperty("scope", "openid");
        params.addProperty("state", "some-state");
        JsonObject body = new JsonObject();
        body.add("params", params);
        body.add("access_token", new JsonObject());
        body.add("id_token", new JsonObject());
        body.addProperty("iss", "http://localhost:3001/auth");
        return body;
    }

    public static JsonObject token(Main main, JsonObject request) throws Exception {
        return OAuthAPIHelper.token(main, request);
    }

    public static JsonObject auth(Main main, JsonObject request) throws Exception {
        return OAuthAPIHelper.auth(main, request);
    }

    private static JsonObject tokenRequest(JsonObject inputBody) {
        inputBody.addProperty("client_id", CLIENT_ID);
        inputBody.addProperty("client_secret", CLIENT_SECRET);
        JsonObject body = new JsonObject();
        body.add("inputBody", inputBody);
        body.add("access_token", new JsonObject());
        body.add("id_token", new JsonObject());
        body.addProperty("iss", "http://localhost:3001/auth");
        return body;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            in.readAllBytes();
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/oauth2/auth")) {
            exchange.getResponseHeaders().add("Location", "http://localhost.com:3000/auth/callback/supertokens"
                    + "#access_token=" + URLEncoder.encode(accessToken(), StandardCharsets.UTF_8)
                    + "&expires_in=3600&scope=openid&state=some-state&token_type=bearer");
            respond(exchange, 302, new JsonObject());
            return;
        }
        JsonObject json = new JsonObject();
        if (path.equals("/oauth2/token")) {
            json.addProperty("access_token", accessToken());
            if (issueRefreshToken) {
                json.addProperty("refresh_token", "ory_rt_mock_" + refreshTokens.incrementAndGet());
            }
            json.addProperty("expires_in", 3600);
            json.addProperty("token_type", "bearer");
            json.addProperty("scope", "openid offline_access");
        } else {
            // introspection: gid without jti, so the revocation check reads by gid
            json.addProperty("active", active);
            json.addProperty("gid", gid);
            json.addProperty("exp", System.currentTimeMillis() / 1000L + 3600);
            json.addProperty("client_id", CLIENT_ID);
            json.addProperty("scope", "openid offline_access");
        }
        respond(exchange, 200, json);
    }

    private static void respond(HttpExchange exchange, int status, JsonObject json) throws IOException {
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    // An access token as the provider issues it. Core re-signs it with its own keys, so the signature is arbitrary.
    private String accessToken() {
        long now = System.currentTimeMillis() / 1000L;
        JsonObject header = new JsonObject();
        header.addProperty("typ", "JWT");
        header.addProperty("alg", "RS256");
        header.addProperty("kid", "mock-kid");
        JsonObject payload = new JsonObject();
        payload.addProperty("gid", gid);
        payload.addProperty("jti", "jti-" + System.nanoTime());
        payload.addProperty("client_id", CLIENT_ID);
        payload.addProperty("sub", "someuserid");
        payload.addProperty("iat", now);
        payload.addProperty("exp", now + 3600);
        payload.addProperty("scp", "openid offline_access");
        String sessionHandle = this.sessionHandle;
        if (sessionHandle != null) {
            payload.addProperty("sessionHandle", sessionHandle);
        }
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8)) + "."
                + enc.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8)) + "."
                + enc.encodeToString("not-verified".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
