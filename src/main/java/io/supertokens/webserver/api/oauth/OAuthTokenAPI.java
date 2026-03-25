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

import com.auth0.jwt.exceptions.JWTCreationException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.oauth.HttpRequestForOAuthProvider;
import io.supertokens.oauth.OAuth;
import io.supertokens.oauth.OAuthToken;
import io.supertokens.oauth.exceptions.OAuthAPIException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.oauth.OAuthClient;
import io.supertokens.pluginInterface.oauth.exception.OAuthClientNotFoundException;
import io.supertokens.pluginInterface.oauth.sqlStorage.OAuthSQLStorage;
import io.supertokens.pluginInterface.session.SessionInfo;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.session.Session;
import io.supertokens.session.jwt.JWT.JWTException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class OAuthTokenAPI extends WebserverAPI {

    /**
     * Per-external-refresh-token JVM lock (outer layer of a two-layer mutex).
     *
     * <p>Ensures at most one thread per token <em>per JVM instance</em> enters the
     * critical section at a time.  Because threads wait here without holding a DB
     * connection, this prevents connection-pool exhaustion even when the inner DB
     * transaction ({@code SELECT … FOR UPDATE}) spans several Hydra HTTP calls.
     *
     * <p>Entries are never removed; their footprint is negligible compared to the
     * sessions they represent.
     */
    private static final ConcurrentHashMap<String, ReentrantLock> REFRESH_LOCKS =
            new ConcurrentHashMap<>();

    public OAuthTokenAPI(Main main) {
        super(main, RECIPE_ID.OAUTH.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/oauth/token";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String iss = InputParser.parseStringOrThrowError(input, "iss", false); // input validation
        JsonObject bodyFromSDK = InputParser.parseJsonObjectOrThrowError(input, "inputBody", false);

        String grantType = InputParser.parseStringOrThrowError(bodyFromSDK, "grant_type", false);
        JsonObject accessTokenUpdate = InputParser.parseJsonObjectOrThrowError(input, "access_token", "authorization_code".equals(grantType));
        JsonObject idTokenUpdate = InputParser.parseJsonObjectOrThrowError(input, "id_token", "authorization_code".equals(grantType));

        // useStaticKeyInput defaults to true, so we check if it has been explicitly set to false
        Boolean useStaticKeyInput = InputParser.parseBooleanOrThrowError(input, "useStaticSigningKey", true);
        boolean useDynamicKey = Boolean.FALSE.equals(useStaticKeyInput);

        String authorizationHeader = InputParser.parseStringOrThrowError(input, "authorizationHeader", true);

        handle(req, resp, authorizationHeader, bodyFromSDK, grantType, iss, accessTokenUpdate, idTokenUpdate,
                useDynamicKey);
    }

    private void handle(HttpServletRequest req, HttpServletResponse resp, String authorizationHeader,
                        JsonObject bodyFromSDK, String grantType, String iss, JsonObject accessTokenUpdate,
                        JsonObject idTokenUpdate, boolean useDynamicKey) throws ServletException, IOException {
        Map<String, String> headers = new HashMap<>();
        if (authorizationHeader != null) {
            headers.put("Authorization", authorizationHeader);
        }

        Map<String, String> formFields = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : bodyFromSDK.entrySet()) {
            formFields.put(entry.getKey(), entry.getValue().getAsString());
        }

        String clientId;

        if (authorizationHeader != null) {
            String[] parsedHeader = Utils.convertFromBase64(authorizationHeader.replaceFirst("^Basic ", "").trim()).split(":");
            clientId = parsedHeader[0];
        } else {
            clientId = formFields.get("client_id");
        }

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage = enforcePublicTenantAndGetPublicTenantStorage(req);
            OAuthClient oauthClient = OAuth.getOAuthClientById(main, appIdentifier, storage, clientId);

            String inputRefreshToken = null;

            // check if the refresh token is valid
            if (grantType.equals("refresh_token")) {
                String refreshToken = InputParser.parseStringOrThrowError(bodyFromSDK, "refresh_token", false);
                inputRefreshToken = refreshToken;

                // Non-rotating refresh: serialize at the DB level so concurrent requests from
                // multiple core instances all succeed by taking turns on the same row lock.
                if (!oauthClient.enableRefreshTokenRotation) {
                    handleNonRotatingRefreshWithLock(req, resp, appIdentifier, storage, oauthClient,
                            inputRefreshToken, formFields, headers, iss, accessTokenUpdate, idTokenUpdate,
                            useDynamicKey);
                    return;
                }

                String internalRefreshToken = OAuth.getInternalRefreshToken(main, appIdentifier, storage, refreshToken);

                Map<String, String> formFieldsForTokenIntrospect = new HashMap<>();
                formFieldsForTokenIntrospect.put("token", internalRefreshToken);

                HttpRequestForOAuthProvider.Response response = OAuthProxyHelper.proxyFormPOST(
                        main, req, resp,
                        appIdentifier,
                        storage,
                        null, // clientIdToCheck
                        "/admin/oauth2/introspect", // pathProxy
                        true, // proxyToAdmin
                        false, // camelToSnakeCaseConversion
                        formFieldsForTokenIntrospect,
                        new HashMap<>() // headers
                );

                if (response == null) {
                    return;
                }

                JsonObject refreshTokenPayload = response.jsonResponse.getAsJsonObject();

                try {
                    OAuth.verifyAndUpdateIntrospectRefreshTokenPayload(main, appIdentifier, storage, refreshTokenPayload, refreshToken, oauthClient.clientId);
                } catch (StorageQueryException | TenantOrAppNotFoundException |
                         FeatureNotEnabledException | InvalidConfigException e) {
                    throw new ServletException(e);
                }

                if (!refreshTokenPayload.get("active").getAsBoolean()) {
                    // this is what ory would return for an invalid token
                    OAuthProxyHelper.handleOAuthAPIException(resp, new OAuthAPIException(
                            "token_inactive", "Token is inactive because it is malformed, expired or otherwise invalid. Token validation failed.", 401
                    ));
                    return;
                }

                formFields.put("refresh_token", internalRefreshToken);
            }

            HttpRequestForOAuthProvider.Response response = OAuthProxyHelper.proxyFormPOST(
                    main, req, resp,
                    getAppIdentifier(req),
                    enforcePublicTenantAndGetPublicTenantStorage(req),
                    clientId, // clientIdToCheck
                    "/oauth2/token", // proxyPath
                    false, // proxyToAdmin
                    false, // camelToSnakeCaseConversion
                    formFields,
                    headers // headers
            );

            if (response != null) {
                try {
                    response.jsonResponse = OAuth.transformTokens(super.main, appIdentifier, storage, response.jsonResponse.getAsJsonObject(),
                            iss, accessTokenUpdate, idTokenUpdate, useDynamicKey);

                    if (grantType.equals("client_credentials")) {
                        try {
                            OAuth.addM2MToken(main, appIdentifier, storage, response.jsonResponse.getAsJsonObject().get("access_token").getAsString());
                        } catch (TryRefreshTokenException e) {
                            throw new IllegalStateException("should never happen");
                        }
                    }

                    String gid = null;
                    String jti = null;
                    String sessionHandle = null;
                    Long accessTokenExp = null;

                    if(response.jsonResponse.getAsJsonObject().has("access_token")){
                        try {
                            JsonObject accessTokenPayload = OAuthToken.getPayloadFromJWTToken(appIdentifier, main, response.jsonResponse.getAsJsonObject().get("access_token").getAsString());
                            gid = accessTokenPayload.get("gid").getAsString();
                            jti = accessTokenPayload.get("jti").getAsString();
                            accessTokenExp = accessTokenPayload.get("exp").getAsLong();
                            if (accessTokenPayload.has("sessionHandle")) {
                                sessionHandle = accessTokenPayload.get("sessionHandle").getAsString();
                                updateLastActive(appIdentifier, sessionHandle);
                            }
                        } catch (TryRefreshTokenException e) {
                            //ignore, shouldn't happen
                        }
                    }

                    if (response.jsonResponse.getAsJsonObject().has("refresh_token")) {
                        String newRefreshToken = response.jsonResponse.getAsJsonObject().get("refresh_token").getAsString();
                        long refreshTokenExp = 0;
                        {
                            // Introspect the new refresh token to get the expiry
                            Map<String, String> formFieldsForTokenIntrospect = new HashMap<>();
                            formFieldsForTokenIntrospect.put("token", newRefreshToken);

                            HttpRequestForOAuthProvider.Response introspectResponse = OAuthProxyHelper.proxyFormPOST(
                                    main, req, resp,
                                    getAppIdentifier(req),
                                    enforcePublicTenantAndGetPublicTenantStorage(req),
                                    null, // clientIdToCheck
                                    "/admin/oauth2/introspect", // pathProxy
                                    true, // proxyToAdmin
                                    false, // camelToSnakeCaseConversion
                                    formFieldsForTokenIntrospect,
                                    new HashMap<>() // headers
                            );

                            if (introspectResponse != null) {
                                JsonObject refreshTokenPayload = introspectResponse.jsonResponse.getAsJsonObject();
                                refreshTokenExp = refreshTokenPayload.get("exp").getAsLong();
                            } else {
                                throw new IllegalStateException("Should never come here");
                            }
                        }

                        if (inputRefreshToken == null) {
                            // Issuing a new refresh token, always creating a mapping.
                            OAuth.createOrUpdateOauthSession(main, appIdentifier, storage, clientId, gid, newRefreshToken, null, sessionHandle, jti, refreshTokenExp);
                        } else {
                            // Refreshing a token
                            if (!oauthClient.enableRefreshTokenRotation) {
                                OAuth.createOrUpdateOauthSession(main, appIdentifier, storage, clientId, gid, inputRefreshToken, newRefreshToken, sessionHandle, jti, refreshTokenExp);
                                response.jsonResponse.getAsJsonObject().remove("refresh_token");
                            } else {
                                OAuth.createOrUpdateOauthSession(main, appIdentifier, storage, clientId, gid, newRefreshToken, null, sessionHandle, jti, refreshTokenExp);
                            }
                        }
                    } else {
                        OAuth.createOrUpdateOauthSession(main, appIdentifier, storage, clientId, gid, null, null, sessionHandle, jti, accessTokenExp);
                    }

                } catch (IOException | InvalidConfigException | TenantOrAppNotFoundException | StorageQueryException
                         | InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException
                         | JWTCreationException | JWTException | StorageTransactionLogicException
                         | UnsupportedJWTSigningAlgorithmException | OAuthClientNotFoundException e) {
                    throw new ServletException(e);
                }

                response.jsonResponse.getAsJsonObject().addProperty("status", "OK");
                super.sendJsonResponse(200, response.jsonResponse, resp);
            }
        } catch (IOException | StorageQueryException | TenantOrAppNotFoundException | BadPermissionException |
                 InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException |
                 InvalidConfigException e) {
            throw new ServletException(e);
        } catch (OAuthClientNotFoundException e) {
            OAuthProxyHelper.handleOAuthClientNotFoundException(resp);
        }
    }

    private void updateLastActive(AppIdentifier appIdentifier, String sessionHandle) {
        try {
            TenantIdentifier tenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                    appIdentifier.getAppId(), Session.getTenantIdFromSessionHandle(sessionHandle));
            Storage storage = StorageLayer.getStorage(tenantIdentifier, main);
            SessionInfo sessionInfo = Session.getSession(tenantIdentifier, storage, sessionHandle);

            UserIdMapping userIdMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                    appIdentifier, storage, sessionInfo.userId, UserIdType.ANY);
            if (userIdMapping != null) {
                ActiveUsers.updateLastActive(appIdentifier, main, userIdMapping.superTokensUserId);
            } else {
                ActiveUsers.updateLastActive(appIdentifier, main, sessionInfo.userId);
            }
        } catch (Exception e) {
            // ignore
        }
    }


    /**
     * Handles a non-rotating OAuth refresh token exchange with a two-layer mutex:
     *
     * <ol>
     *   <li><b>JVM {@link ReentrantLock}</b> (outer) — serialises concurrent requests within
     *       this JVM instance without holding a DB connection while waiting.</li>
     *   <li><b>DB {@code SELECT … FOR UPDATE}</b> (inner, SQL storage only) — row-level exclusive
     *       lock held for the full Hydra round-trip, serialising requests across multiple
     *       SuperTokens instances that share the same database.</li>
     * </ol>
     *
     * <p>Because the JVM lock ensures at most one thread per instance is inside the DB
     * transaction at a time, holding the connection during Hydra HTTP calls cannot exhaust
     * the connection pool.</p>
     *
     * <p>For non-SQL storage backends the JVM lock alone is used (no DB-level locking).</p>
     */
    private void handleNonRotatingRefreshWithLock(
            HttpServletRequest req, HttpServletResponse resp,
            AppIdentifier appIdentifier, Storage storage, OAuthClient oauthClient,
            String externalRefreshToken, Map<String, String> formFields, Map<String, String> headers,
            String iss, JsonObject accessTokenUpdate, JsonObject idTokenUpdate, boolean useDynamicKey)
            throws IOException, ServletException {

        String lockKey = appIdentifier.getAppId() + ":" + externalRefreshToken;
        ReentrantLock lock = REFRESH_LOCKS.computeIfAbsent(lockKey, k -> new ReentrantLock());
        lock.lock();
        try {
            if (storage instanceof OAuthSQLStorage) {
                handleNonRotatingRefresh(req, resp, appIdentifier, (OAuthSQLStorage) storage, oauthClient,
                        externalRefreshToken, formFields, headers, iss, accessTokenUpdate, idTokenUpdate,
                        useDynamicKey);
            } else {
                throw new IllegalStateException("invalid storage for OAuth");
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * SQL path: DB transaction with {@code SELECT … FOR UPDATE} covering the full Hydra
     * round-trip. The caller must already hold the JVM lock for this token.
     */
    private void handleNonRotatingRefresh(
            HttpServletRequest req, HttpServletResponse resp,
            AppIdentifier appIdentifier, OAuthSQLStorage sqlStorage, OAuthClient oauthClient,
            String externalRefreshToken, Map<String, String> formFields, Map<String, String> headers,
            String iss, JsonObject accessTokenUpdate, JsonObject idTokenUpdate, boolean useDynamicKey)
            throws IOException, ServletException {

        final JsonObject[] finalResponse = {null};

        try {
            sqlStorage.startTransaction(con -> {
                try {
                    // ── 1. SELECT … FOR UPDATE ─────────────────────────────────────
                    String internalToken = sqlStorage.getRefreshTokenMappingForUpdate_Transaction(
                            appIdentifier, con, externalRefreshToken);
                    // Null means no mapping yet (auth-code path stores null); fall back
                    // to using the external token directly — same as getInternalRefreshToken.
                    if (internalToken == null) {
                        internalToken = externalRefreshToken;
                    }

                    // ── 2. Introspect internal token with Hydra ────────────────────
                    Map<String, String> introspectFields = new HashMap<>();
                    introspectFields.put("token", internalToken);
                    HttpRequestForOAuthProvider.Response introspectResp = OAuthProxyHelper.proxyFormPOST(
                            main, req, resp, appIdentifier, sqlStorage,
                            null, "/admin/oauth2/introspect", true, false,
                            introspectFields, new HashMap<>());
                    if (introspectResp == null) return null; // already responded; auto-rollback

                    JsonObject refreshTokenPayload = introspectResp.jsonResponse.getAsJsonObject();
                    OAuth.verifyAndUpdateIntrospectRefreshTokenPayload(main, appIdentifier, sqlStorage,
                            refreshTokenPayload, externalRefreshToken, oauthClient.clientId);

                    if (!refreshTokenPayload.get("active").getAsBoolean()) {
                        OAuthProxyHelper.handleOAuthAPIException(resp, new OAuthAPIException(
                                "token_inactive",
                                "Token is inactive because it is malformed, expired or otherwise invalid. Token validation failed.",
                                401));
                        return null; // already responded; auto-rollback
                    }

                    // ── 3. Exchange with Hydra ─────────────────────────────────────
                    formFields.put("refresh_token", internalToken);
                    HttpRequestForOAuthProvider.Response exchangeResp = OAuthProxyHelper.proxyFormPOST(
                            main, req, resp, appIdentifier, sqlStorage,
                            oauthClient.clientId, "/oauth2/token", false, false,
                            formFields, headers);
                    if (exchangeResp == null) return null; // already responded; auto-rollback

                    // ── 4. Transform tokens ────────────────────────────────────────
                    exchangeResp.jsonResponse = OAuth.transformTokens(main, appIdentifier, sqlStorage,
                            exchangeResp.jsonResponse.getAsJsonObject(),
                            iss, accessTokenUpdate, idTokenUpdate, useDynamicKey);

                    // ── 5. Extract gid / jti / sessionHandle ───────────────────────
                    String gid = null, jti = null, sessionHandle = null;
                    if (exchangeResp.jsonResponse.getAsJsonObject().has("access_token")) {
                        try {
                            JsonObject atPayload = OAuthToken.getPayloadFromJWTToken(appIdentifier, main,
                                    exchangeResp.jsonResponse.getAsJsonObject()
                                            .get("access_token").getAsString());
                            gid = atPayload.get("gid").getAsString();
                            jti = atPayload.get("jti").getAsString();
                            if (atPayload.has("sessionHandle")) {
                                sessionHandle = atPayload.get("sessionHandle").getAsString();
                                updateLastActive(appIdentifier, sessionHandle);
                            }
                        } catch (TryRefreshTokenException e) {
                            // ignore — shouldn't happen
                        }
                    }

                    // ── 6. UPDATE + commit ─────────────────────────────────────────
                    if (exchangeResp.jsonResponse.getAsJsonObject().has("refresh_token")) {
                        String newInternalToken = exchangeResp.jsonResponse.getAsJsonObject()
                                .get("refresh_token").getAsString();

                        Map<String, String> newIntrospectFields = new HashMap<>();
                        newIntrospectFields.put("token", newInternalToken);
                        HttpRequestForOAuthProvider.Response newIntrospectResp = OAuthProxyHelper.proxyFormPOST(
                                main, req, resp, appIdentifier, sqlStorage,
                                null, "/admin/oauth2/introspect", true, false,
                                newIntrospectFields, new HashMap<>());
                        if (newIntrospectResp == null) {
                            throw new IllegalStateException("Should never come here");
                        }
                        long refreshTokenExp = newIntrospectResp.jsonResponse.getAsJsonObject()
                                .get("exp").getAsLong();

                        // UPDATE inside the same transaction — atomically replaces internal token
                        sqlStorage.updateOAuthSessionInternal_Transaction(appIdentifier, con, externalRefreshToken,
                                newInternalToken, sessionHandle, jti, refreshTokenExp);
                    }

                    sqlStorage.commitTransaction(con);

                    exchangeResp.jsonResponse.getAsJsonObject().remove("refresh_token");
                    exchangeResp.jsonResponse.getAsJsonObject().addProperty("status", "OK");
                    finalResponse[0] = exchangeResp.jsonResponse.getAsJsonObject();

                } catch (IOException | TenantOrAppNotFoundException | FeatureNotEnabledException
                         | InvalidConfigException | InvalidKeyException | NoSuchAlgorithmException
                         | InvalidKeySpecException | JWTCreationException | JWTException
                         | UnsupportedJWTSigningAlgorithmException | ServletException e) {
                    throw new StorageTransactionLogicException(e);
                }
                return null;
            });

        } catch (StorageTransactionLogicException e) {
            if (e.actualException instanceof IOException) throw (IOException) e.actualException;
            if (e.actualException instanceof ServletException) throw (ServletException) e.actualException;
            throw new ServletException(e.actualException);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }

        // Send response only if the transaction committed (null = error already sent by proxyFormPOST)
        if (finalResponse[0] != null) {
            super.sendJsonResponse(200, finalResponse[0], resp);
        }
    }
}
