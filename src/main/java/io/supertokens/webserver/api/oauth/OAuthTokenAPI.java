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
import java.util.concurrent.atomic.AtomicInteger;

public class OAuthTokenAPI extends WebserverAPI {

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

        if (grantType.equals("refresh_token")) {
            String refreshTokenForLock = InputParser.parseStringOrThrowError(bodyFromSDK, "refresh_token", false);
            NamedLockObject entry = lockMap.computeIfAbsent(refreshTokenForLock, k -> new NamedLockObject());
            try {
                entry.refCount.incrementAndGet();
                synchronized (entry.obj) {
                    handle(req, resp, authorizationHeader, bodyFromSDK, grantType, iss, accessTokenUpdate,
                            idTokenUpdate,
                            useDynamicKey);
                }
            } finally {
                entry.refCount.decrementAndGet();
                if (entry.refCount.get() == 0) {
                    lockMap.remove(refreshTokenForLock, entry);
                }
            }

        } else {
            handle(req, resp, authorizationHeader, bodyFromSDK, grantType, iss, accessTokenUpdate, idTokenUpdate,
                    useDynamicKey);
        }
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


    private static class NamedLockObject {
        final Object obj = new Object();
        final AtomicInteger refCount = new AtomicInteger(0);
    }
    private static final ConcurrentHashMap<String, NamedLockObject> lockMap = new ConcurrentHashMap<>();
}
