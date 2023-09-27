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

package io.supertokens.session;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.exceptions.AccessTokenPayloadError;
import io.supertokens.exceptions.TokenTheftDetectedException;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.session.noSqlStorage.SessionNoSQLStorage_1;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.accessToken.AccessToken.AccessTokenInfo;
import io.supertokens.session.info.SessionInfo;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.session.info.TokenInfo;
import io.supertokens.session.jwt.JWT;
import io.supertokens.session.refreshToken.RefreshToken;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

public class Session {

    @TestOnly
    public static SessionInformationHolder createNewSession(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                            Main main,
                                                            @Nonnull String recipeUserId,
                                                            @Nonnull JsonObject userDataInJWT,
                                                            @Nonnull JsonObject userDataInDatabase)
            throws NoSuchAlgorithmException, StorageQueryException, InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException, SignatureException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException, UnauthorisedException,
            JWT.JWTException, UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError {
        try {
            return createNewSession(tenantIdentifierWithStorage, main, recipeUserId, userDataInJWT, userDataInDatabase,
                    false,
                    AccessToken.getLatestVersion(), false);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static SessionInformationHolder createNewSession(Main main,
                                                            @Nonnull String recipeUserId,
                                                            @Nonnull JsonObject userDataInJWT,
                                                            @Nonnull JsonObject userDataInDatabase)
            throws NoSuchAlgorithmException, StorageQueryException, InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException, SignatureException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError {
        Storage storage = StorageLayer.getStorage(main);
        try {
            return createNewSession(
                    new TenantIdentifierWithStorage(null, null, null, storage), main,
                    recipeUserId, userDataInJWT, userDataInDatabase, false, AccessToken.getLatestVersion(), false);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static SessionInformationHolder createNewSession(Main main, @Nonnull String recipeUserId,
                                                            @Nonnull JsonObject userDataInJWT,
                                                            @Nonnull JsonObject userDataInDatabase,
                                                            boolean enableAntiCsrf, AccessToken.VERSION version,
                                                            boolean useStaticKey)
            throws NoSuchAlgorithmException, StorageQueryException, InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException, SignatureException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError {
        Storage storage = StorageLayer.getStorage(main);
        try {
            return createNewSession(
                    new TenantIdentifierWithStorage(null, null, null, storage), main,
                    recipeUserId, userDataInJWT, userDataInDatabase, enableAntiCsrf, version, useStaticKey);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static SessionInformationHolder createNewSession(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                            Main main, @Nonnull String recipeUserId,
                                                            @Nonnull JsonObject userDataInJWT,
                                                            @Nonnull JsonObject userDataInDatabase,
                                                            boolean enableAntiCsrf, AccessToken.VERSION version,
                                                            boolean useStaticKey)
            throws NoSuchAlgorithmException, StorageQueryException, InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException, SignatureException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException, AccessTokenPayloadError,
            UnsupportedJWTSigningAlgorithmException, TenantOrAppNotFoundException {
        String sessionHandle = UUID.randomUUID().toString();
        if (!tenantIdentifierWithStorage.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
            sessionHandle += "_" + tenantIdentifierWithStorage.getTenantId();
        }

        String primaryUserId = recipeUserId;
        if (tenantIdentifierWithStorage.getStorage().getType().equals(STORAGE_TYPE.SQL)) {
            primaryUserId = tenantIdentifierWithStorage.getAuthRecipeStorage()
                    .getPrimaryUserIdStrForUserId(tenantIdentifierWithStorage.toAppIdentifier(), recipeUserId);
            if (primaryUserId == null) {
                primaryUserId = recipeUserId;
            }
        }

        String antiCsrfToken = enableAntiCsrf ? UUID.randomUUID().toString() : null;
        final TokenInfo refreshToken = RefreshToken.createNewRefreshToken(tenantIdentifierWithStorage, main,
                sessionHandle, recipeUserId, null,
                antiCsrfToken);

        TokenInfo accessToken = AccessToken.createNewAccessToken(tenantIdentifierWithStorage, main, sessionHandle,
                recipeUserId, primaryUserId, Utils.hashSHA256(refreshToken.token), null, userDataInJWT, antiCsrfToken,
                null, version, useStaticKey);

        tenantIdentifierWithStorage.getSessionStorage()
                .createNewSession(tenantIdentifierWithStorage, sessionHandle, recipeUserId,
                        Utils.hashSHA256(Utils.hashSHA256(refreshToken.token)), userDataInDatabase, refreshToken.expiry,
                        userDataInJWT, refreshToken.createdTime, useStaticKey);

        TokenInfo idRefreshToken = new TokenInfo(UUID.randomUUID().toString(), refreshToken.expiry,
                refreshToken.createdTime);
        return new SessionInformationHolder(
                new SessionInfo(sessionHandle, primaryUserId, recipeUserId, userDataInJWT,
                        tenantIdentifierWithStorage.getTenantId()),
                accessToken,
                refreshToken, idRefreshToken, antiCsrfToken);
    }

    @TestOnly
    public static SessionInformationHolder regenerateToken(Main main,
                                                           @Nonnull String token,
                                                           @Nullable JsonObject userDataInJWT)
            throws StorageQueryException, StorageTransactionLogicException,
            UnauthorisedException, InvalidKeySpecException, SignatureException, NoSuchAlgorithmException,
            InvalidKeyException, JWT.JWTException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError, TryRefreshTokenException {
        try {
            return regenerateToken(new AppIdentifier(null, null), main, token, userDataInJWT);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /*
     * Question: If the incoming access token is expired, do we throw try refresh token error and not update the db?
     * We should update in database, in SDK session object and not throw an error, nor set any cookie. This is
     * because, the user has already verified the session for this API. If it has expired, the refresh API will be
     * called, and the new JWT info will be set in the token then.
     *
     * Question: If the incoming session has been revoked, do we throw an unauthorised error?
     * Yes. It's important that the user knows that this has happened.
     *
     * Question: If this regenerates session tokens, while another API revokes it, then how will that work?
     * This is OK since the other API will cause a clearing of idRefreshToken and this will not set that. This means
     * that next API call, only the access token will go and that will not pass. In fact, it will be removed.
     *
     *
     */
    public static SessionInformationHolder regenerateToken(AppIdentifier appIdentifier, Main main,
                                                           @Nonnull String token,
                                                           @Nullable JsonObject userDataInJWT)
            throws StorageQueryException, StorageTransactionLogicException,
            UnauthorisedException, InvalidKeySpecException, SignatureException, NoSuchAlgorithmException,
            InvalidKeyException, JWT.JWTException, TryRefreshTokenException,
            UnsupportedJWTSigningAlgorithmException,
            AccessTokenPayloadError, TenantOrAppNotFoundException {

        // We assume the token has already been verified at this point. It may be expired or JWT signing key may have
        // changed for it...
        AccessTokenInfo accessToken = AccessToken.getInfoFromAccessTokenWithoutVerifying(appIdentifier, token);
        TenantIdentifierWithStorage tenantIdentifierWithStorage = accessToken.tenantIdentifier.withStorage(
                StorageLayer.getStorage(accessToken.tenantIdentifier, main));
        io.supertokens.pluginInterface.session.SessionInfo sessionInfo = getSession(tenantIdentifierWithStorage,
                accessToken.sessionHandle);
        JsonObject newJWTUserPayload = userDataInJWT == null ? sessionInfo.userDataInJWT
                : userDataInJWT;
        updateSession(tenantIdentifierWithStorage, accessToken.sessionHandle, null, newJWTUserPayload,
                accessToken.version);

        // if the above succeeds but the below fails, it's OK since the client will get server error and will try
        // again. In this case, the JWT data will be updated again since the API will get the old JWT. In case there
        // is a refresh call, the new JWT will get the new data.
        if (accessToken.expiryTime < System.currentTimeMillis()) {
            // in this case, we set the should not set the access token in the response since they will have to call
            // the refresh API anyway.
            return new SessionInformationHolder(
                    new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId, accessToken.recipeUserId,
                            newJWTUserPayload,
                            tenantIdentifierWithStorage.getTenantId()), null, null, null,
                    null);
        }

        TokenInfo newAccessToken = AccessToken.createNewAccessToken(tenantIdentifierWithStorage, main,
                accessToken.sessionHandle, accessToken.recipeUserId, accessToken.primaryUserId,
                accessToken.refreshTokenHash1, accessToken.parentRefreshTokenHash1, newJWTUserPayload,
                accessToken.antiCsrfToken, accessToken.expiryTime, accessToken.version, sessionInfo.useStaticKey);

        return new SessionInformationHolder(
                new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId, accessToken.recipeUserId,
                        newJWTUserPayload,
                        tenantIdentifierWithStorage.getTenantId()),
                new TokenInfo(newAccessToken.token, newAccessToken.expiry, newAccessToken.createdTime), null, null,
                null);
    }

    @Deprecated
    public static SessionInformationHolder regenerateTokenBeforeCDI2_21(AppIdentifier appIdentifier, Main main,
                                                                        @Nonnull String token,
                                                                        @Nullable JsonObject userDataInJWT)
            throws StorageQueryException, StorageTransactionLogicException,
            UnauthorisedException, InvalidKeySpecException, SignatureException, NoSuchAlgorithmException,
            InvalidKeyException, JWT.JWTException, TryRefreshTokenException,
            UnsupportedJWTSigningAlgorithmException,
            AccessTokenPayloadError, TenantOrAppNotFoundException {

        // We assume the token has already been verified at this point. It may be expired or JWT signing key may have
        // changed for it...
        AccessTokenInfo accessToken = AccessToken.getInfoFromAccessTokenWithoutVerifying(appIdentifier, token);
        TenantIdentifierWithStorage tenantIdentifierWithStorage = accessToken.tenantIdentifier.withStorage(
                StorageLayer.getStorage(accessToken.tenantIdentifier, main));
        io.supertokens.pluginInterface.session.SessionInfo sessionInfo = getSession(tenantIdentifierWithStorage,
                accessToken.sessionHandle);
        JsonObject newJWTUserPayload = userDataInJWT == null ? sessionInfo.userDataInJWT
                : userDataInJWT;
        updateSessionBeforeCDI2_21(
                tenantIdentifierWithStorage,
                accessToken.sessionHandle, null, newJWTUserPayload);

        // if the above succeeds but the below fails, it's OK since the client will get server error and will try
        // again. In this case, the JWT data will be updated again since the API will get the old JWT. In case there
        // is a refresh call, the new JWT will get the new data.
        if (accessToken.expiryTime < System.currentTimeMillis()) {
            // in this case, we set the should not set the access token in the response since they will have to call
            // the refresh API anyway.
            return new SessionInformationHolder(
                    new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId, accessToken.recipeUserId,
                            newJWTUserPayload,
                            tenantIdentifierWithStorage.getTenantId()), null, null, null,
                    null);
        }

        TokenInfo newAccessToken = AccessToken.createNewAccessToken(accessToken.tenantIdentifier, main,
                accessToken.sessionHandle,
                accessToken.recipeUserId, accessToken.primaryUserId,
                accessToken.refreshTokenHash1, accessToken.parentRefreshTokenHash1, newJWTUserPayload,
                accessToken.antiCsrfToken, accessToken.expiryTime, accessToken.version, sessionInfo.useStaticKey);

        return new SessionInformationHolder(
                new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId, accessToken.recipeUserId,
                        newJWTUserPayload,
                        tenantIdentifierWithStorage.getTenantId()),
                new TokenInfo(newAccessToken.token, newAccessToken.expiry, newAccessToken.createdTime), null, null,
                null);
    }

    @TestOnly
    public static SessionInformationHolder getSession(Main main, @Nonnull String token, @Nullable String antiCsrfToken,
                                                      boolean enableAntiCsrf, Boolean doAntiCsrfCheck,
                                                      boolean checkDatabase)
            throws StorageQueryException,
            StorageTransactionLogicException, TryRefreshTokenException, UnauthorisedException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError {
        try {
            return getSession(new AppIdentifier(null, null), main, token, antiCsrfToken, enableAntiCsrf,
                    doAntiCsrfCheck, checkDatabase);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // pass antiCsrfToken to disable csrf check for this request
    public static SessionInformationHolder getSession(AppIdentifier appIdentifier, Main main, @Nonnull String token,
                                                      @Nullable String antiCsrfToken,
                                                      boolean enableAntiCsrf, Boolean doAntiCsrfCheck,
                                                      boolean checkDatabase) throws StorageQueryException,
            StorageTransactionLogicException, TryRefreshTokenException, UnauthorisedException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError, TenantOrAppNotFoundException {

        AccessTokenInfo accessToken = AccessToken.getInfoFromAccessToken(appIdentifier, main, token,
                doAntiCsrfCheck && enableAntiCsrf);
        TenantIdentifierWithStorage tenantIdentifierWithStorage = accessToken.tenantIdentifier.withStorage(
                StorageLayer.getStorage(accessToken.tenantIdentifier, main));

        if (enableAntiCsrf && doAntiCsrfCheck
                && (antiCsrfToken == null || !antiCsrfToken.equals(accessToken.antiCsrfToken))) {
            throw new TryRefreshTokenException("anti-csrf check failed");
        }

        io.supertokens.pluginInterface.session.SessionInfo sessionInfoForBlacklisting = null;
        if (checkDatabase) {
            sessionInfoForBlacklisting = tenantIdentifierWithStorage.getSessionStorage()
                    .getSession(tenantIdentifierWithStorage, accessToken.sessionHandle);
            if (sessionInfoForBlacklisting == null) {
                throw new UnauthorisedException("Either the session has ended or has been blacklisted");
            }
        }

        boolean JWTPayloadNeedsUpdating = sessionInfoForBlacklisting != null
                && !accessToken.userData.equals(sessionInfoForBlacklisting.userDataInJWT);
        if (accessToken.parentRefreshTokenHash1 == null && !JWTPayloadNeedsUpdating) {
            // this means that the refresh token associated with this access token is
            // already the parent - and JWT payload doesn't need to be updated.
            return new SessionInformationHolder(
                    new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId, accessToken.recipeUserId,
                            accessToken.userData,
                            tenantIdentifierWithStorage.getTenantId()), null, null,
                    null, null);
        }

        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.GET_SESSION_NEW_TOKENS, null);

        if (tenantIdentifierWithStorage.getSessionStorage().getType() == STORAGE_TYPE.SQL) {
            SessionSQLStorage storage = (SessionSQLStorage) tenantIdentifierWithStorage.getSessionStorage();
            try {
                CoreConfig config = Config.getConfig(tenantIdentifierWithStorage, main);
                return storage.startTransaction(con -> {
                    try {

                        io.supertokens.pluginInterface.session.SessionInfo sessionInfo = storage
                                .getSessionInfo_Transaction(tenantIdentifierWithStorage, con,
                                        accessToken.sessionHandle);

                        if (sessionInfo == null) {
                            storage.commitTransaction(con);
                            throw new UnauthorisedException("Session missing in db");
                        }

                        boolean promote = accessToken.parentRefreshTokenHash1 != null && sessionInfo.refreshTokenHash2
                                .equals(Utils.hashSHA256(accessToken.parentRefreshTokenHash1));
                        if (promote
                                || sessionInfo.refreshTokenHash2.equals(Utils.hashSHA256(accessToken.refreshTokenHash1))
                                || JWTPayloadNeedsUpdating) {
                            if (promote) {
                                storage.updateSessionInfo_Transaction(tenantIdentifierWithStorage, con,
                                        accessToken.sessionHandle,
                                        Utils.hashSHA256(accessToken.refreshTokenHash1),
                                        System.currentTimeMillis() +
                                                config.getRefreshTokenValidity());
                            }
                            storage.commitTransaction(con);

                            TokenInfo newAccessToken;
                            if (AccessToken.getAccessTokenVersion(accessToken) == AccessToken.VERSION.V1) {
                                newAccessToken = AccessToken.createNewAccessTokenV1(tenantIdentifierWithStorage,
                                        main,
                                        accessToken.sessionHandle,
                                        accessToken.recipeUserId, accessToken.refreshTokenHash1, null,
                                        sessionInfo.userDataInJWT, accessToken.antiCsrfToken);
                            } else {
                                newAccessToken = AccessToken.createNewAccessToken(tenantIdentifierWithStorage, main,
                                        accessToken.sessionHandle,
                                        accessToken.recipeUserId, accessToken.primaryUserId,
                                        accessToken.refreshTokenHash1, null,
                                        sessionInfo.userDataInJWT, accessToken.antiCsrfToken, null, accessToken.version,
                                        sessionInfo.useStaticKey);
                            }

                            return new SessionInformationHolder(
                                    new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId,
                                            accessToken.recipeUserId,
                                            sessionInfo.userDataInJWT, tenantIdentifierWithStorage.getTenantId()),
                                    new TokenInfo(newAccessToken.token, newAccessToken.expiry,
                                            newAccessToken.createdTime),
                                    null, null, null);
                        }

                        storage.commitTransaction(con);
                        return new SessionInformationHolder(
                                new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId,
                                        accessToken.recipeUserId, accessToken.userData,
                                        tenantIdentifierWithStorage.getTenantId()),
                                // here we purposely use accessToken.userData instead of sessionInfo.userDataInJWT
                                // because we are not returning a new access token
                                null, null, null, null);
                    } catch (UnauthorisedException | NoSuchAlgorithmException |
                             InvalidKeyException | InvalidKeySpecException | SignatureException |
                             UnsupportedJWTSigningAlgorithmException | AccessTokenPayloadError |
                             TenantOrAppNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                }, SQLStorage.TransactionIsolationLevel.REPEATABLE_READ);
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof UnauthorisedException) {
                    throw (UnauthorisedException) e.actualException;
                } else if (e.actualException instanceof TenantOrAppNotFoundException) {
                    throw (TenantOrAppNotFoundException) e.actualException;
                }
                if (e.actualException instanceof AccessTokenPayloadError) {
                    throw (AccessTokenPayloadError) e.actualException;
                }
                throw e;
            }
        } else if (tenantIdentifierWithStorage.getSessionStorage().getType() ==
                STORAGE_TYPE.NOSQL_1) {
            SessionNoSQLStorage_1 storage = (SessionNoSQLStorage_1) tenantIdentifierWithStorage.getSessionStorage();
            while (true) {
                try {

                    io.supertokens.pluginInterface.session.noSqlStorage.SessionInfoWithLastUpdated sessionInfo = storage
                            .getSessionInfo_Transaction(accessToken.sessionHandle);

                    if (sessionInfo == null) {
                        throw new UnauthorisedException("Session missing in db");
                    }

                    boolean promote = accessToken.parentRefreshTokenHash1 != null && sessionInfo.refreshTokenHash2
                            .equals(Utils.hashSHA256(accessToken.parentRefreshTokenHash1));
                    if (promote || sessionInfo.refreshTokenHash2.equals(Utils.hashSHA256(accessToken.refreshTokenHash1))
                            || JWTPayloadNeedsUpdating) {
                        if (promote) {
                            boolean success = storage.updateSessionInfo_Transaction(accessToken.sessionHandle,
                                    Utils.hashSHA256(accessToken.refreshTokenHash1),
                                    System.currentTimeMillis() + Config.getConfig(tenantIdentifierWithStorage, main)
                                            .getRefreshTokenValidity(),
                                    sessionInfo.lastUpdatedSign);
                            if (!success) {
                                continue;
                            }
                        }

                        TokenInfo newAccessToken;
                        if (accessToken.version == AccessToken.VERSION.V1) {
                            newAccessToken = AccessToken.createNewAccessTokenV1(tenantIdentifierWithStorage, main,
                                    accessToken.sessionHandle,
                                    accessToken.recipeUserId, accessToken.refreshTokenHash1, null,
                                    sessionInfo.userDataInJWT,
                                    accessToken.antiCsrfToken);
                        } else {
                            newAccessToken = AccessToken.createNewAccessToken(tenantIdentifierWithStorage, main,
                                    accessToken.sessionHandle,
                                    accessToken.recipeUserId, accessToken.primaryUserId, accessToken.refreshTokenHash1,
                                    null, sessionInfo.userDataInJWT,
                                    accessToken.antiCsrfToken, null, accessToken.version, sessionInfo.useStaticKey);
                        }

                        return new SessionInformationHolder(
                                new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId,
                                        accessToken.recipeUserId,
                                        sessionInfo.userDataInJWT, tenantIdentifierWithStorage.getTenantId()),
                                new TokenInfo(newAccessToken.token, newAccessToken.expiry, newAccessToken.createdTime),
                                null, null, null);
                    }

                    return new SessionInformationHolder(
                            new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId,
                                    accessToken.recipeUserId, accessToken.userData,
                                    tenantIdentifierWithStorage.getTenantId()),
                            // here we purposely use accessToken.userData instead of sessionInfo.userDataInJWT
                            // because we are not returning a new access token
                            null, null, null, null);
                } catch (NoSuchAlgorithmException | InvalidKeyException
                         | InvalidKeySpecException | SignatureException e) {
                    throw new StorageTransactionLogicException(e);
                }
            }
        } else {
            throw new UnsupportedOperationException("");
        }
    }

    @TestOnly
    public static SessionInformationHolder refreshSession(Main main, @Nonnull String refreshToken,
                                                          @Nullable String antiCsrfToken, boolean enableAntiCsrf,
                                                          AccessToken.VERSION accessTokenVersion)
            throws StorageTransactionLogicException,
            UnauthorisedException, StorageQueryException, TokenTheftDetectedException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError {
        try {
            return refreshSession(new AppIdentifier(null, null), main, refreshToken, antiCsrfToken,
                    enableAntiCsrf, accessTokenVersion);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static SessionInformationHolder refreshSession(AppIdentifier appIdentifier, Main main,
                                                          @Nonnull String refreshToken,
                                                          @Nullable String antiCsrfToken, boolean enableAntiCsrf,
                                                          AccessToken.VERSION accessTokenVersion)
            throws StorageTransactionLogicException,
            UnauthorisedException, StorageQueryException, TokenTheftDetectedException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError, TenantOrAppNotFoundException {
        RefreshToken.RefreshTokenInfo refreshTokenInfo = RefreshToken.getInfoFromRefreshToken(appIdentifier, main,
                refreshToken);

        if (enableAntiCsrf && refreshTokenInfo.antiCsrfToken != null) {
            // anti csrf is enabled, and the refresh token contains an anticsrf token (it's not the older version)
            if (!refreshTokenInfo.antiCsrfToken.equals(antiCsrfToken)) {
                throw new UnauthorisedException("Anti CSRF token missing, or not matching");
            }
        }

        return refreshSessionHelper(refreshTokenInfo.tenantIdentifier.withStorage(
                        StorageLayer.getStorage(refreshTokenInfo.tenantIdentifier, main)),
                main, refreshToken, refreshTokenInfo, enableAntiCsrf, accessTokenVersion);
    }

    private static SessionInformationHolder refreshSessionHelper(
            TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main, String refreshToken,
            RefreshToken.RefreshTokenInfo refreshTokenInfo,
            boolean enableAntiCsrf,
            AccessToken.VERSION accessTokenVersion)
            throws StorageTransactionLogicException, UnauthorisedException, StorageQueryException,
            TokenTheftDetectedException, UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError,
            TenantOrAppNotFoundException {
        ////////////////////////////////////////// SQL/////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        if (tenantIdentifierWithStorage.getSessionStorage().getType() == STORAGE_TYPE.SQL) {
            SessionSQLStorage storage = (SessionSQLStorage) tenantIdentifierWithStorage.getSessionStorage();
            try {
                CoreConfig config = Config.getConfig(tenantIdentifierWithStorage, main);
                return storage.startTransaction(con -> {
                    try {
                        String sessionHandle = refreshTokenInfo.sessionHandle;
                        io.supertokens.pluginInterface.session.SessionInfo sessionInfo = storage
                                .getSessionInfo_Transaction(tenantIdentifierWithStorage, con, sessionHandle);

                        if (sessionInfo == null || sessionInfo.expiry < System.currentTimeMillis()) {
                            storage.commitTransaction(con);
                            throw new UnauthorisedException("Session missing in db or has expired");
                        }

                        if (sessionInfo.refreshTokenHash2.equals(Utils.hashSHA256(Utils.hashSHA256(refreshToken)))) {
                            // at this point, the input refresh token is the parent one.
                            storage.commitTransaction(con);

                            String antiCsrfToken = enableAntiCsrf ? UUID.randomUUID().toString() : null;
                            final TokenInfo newRefreshToken = RefreshToken.createNewRefreshToken(
                                    tenantIdentifierWithStorage, main, sessionHandle,
                                    sessionInfo.recipeUserId, Utils.hashSHA256(refreshToken), antiCsrfToken);

                            TokenInfo newAccessToken = AccessToken.createNewAccessToken(tenantIdentifierWithStorage,
                                    main, sessionHandle,
                                    sessionInfo.recipeUserId, sessionInfo.userId,
                                    Utils.hashSHA256(newRefreshToken.token),
                                    Utils.hashSHA256(refreshToken), sessionInfo.userDataInJWT, antiCsrfToken,
                                    null, accessTokenVersion, sessionInfo.useStaticKey);

                            TokenInfo idRefreshToken = new TokenInfo(UUID.randomUUID().toString(),
                                    newRefreshToken.expiry, newRefreshToken.createdTime);

                            return new SessionInformationHolder(
                                    new SessionInfo(sessionHandle, sessionInfo.userId, sessionInfo.recipeUserId,
                                            sessionInfo.userDataInJWT,
                                            tenantIdentifierWithStorage.getTenantId()),
                                    newAccessToken, newRefreshToken, idRefreshToken, antiCsrfToken);
                        }

                        if ((refreshTokenInfo.type == RefreshToken.TYPE.FREE
                                && refreshTokenInfo.parentRefreshTokenHash2 != null
                                && refreshTokenInfo.parentRefreshTokenHash2.equals(sessionInfo.refreshTokenHash2))
                                || (refreshTokenInfo.parentRefreshTokenHash1 != null
                                && Utils.hashSHA256(refreshTokenInfo.parentRefreshTokenHash1)
                                .equals(sessionInfo.refreshTokenHash2))) {
                            storage.updateSessionInfo_Transaction(tenantIdentifierWithStorage, con, sessionHandle,
                                    Utils.hashSHA256(Utils.hashSHA256(refreshToken)),
                                    System.currentTimeMillis() + config.getRefreshTokenValidity());

                            storage.commitTransaction(con);

                            return refreshSessionHelper(tenantIdentifierWithStorage, main, refreshToken,
                                    refreshTokenInfo, enableAntiCsrf,
                                    accessTokenVersion);
                        }

                        storage.commitTransaction(con);

                        throw new TokenTheftDetectedException(sessionHandle, sessionInfo.recipeUserId,
                                sessionInfo.userId);

                    } catch (UnauthorisedException | NoSuchAlgorithmException | InvalidKeyException
                             | AccessTokenPayloadError | TokenTheftDetectedException | InvalidKeySpecException
                             | SignatureException | NoSuchPaddingException | InvalidAlgorithmParameterException
                             | IllegalBlockSizeException | BadPaddingException |
                             UnsupportedJWTSigningAlgorithmException |
                             TenantOrAppNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof UnauthorisedException) {
                    throw (UnauthorisedException) e.actualException;
                } else if (e.actualException instanceof TokenTheftDetectedException) {
                    throw (TokenTheftDetectedException) e.actualException;
                } else if (e.actualException instanceof AccessTokenPayloadError) {
                    throw (AccessTokenPayloadError) e.actualException;
                } else if (e.actualException instanceof TenantOrAppNotFoundException) {
                    throw (TenantOrAppNotFoundException) e.actualException;
                }
                throw e;
            }

            ////////////////////////////////////////// NOSQL_1/////////////////////////////////////////////
            //////////////////////////////////////////////////////////////////////////////////////////////
            //////////////////////////////////////////////////////////////////////////////////////////////
            //////////////////////////////////////////////////////////////////////////////////////////////
            //////////////////////////////////////////////////////////////////////////////////////////////
            //////////////////////////////////////////////////////////////////////////////////////////////
        } else if (tenantIdentifierWithStorage.getSessionStorage().getType() ==
                STORAGE_TYPE.NOSQL_1) {
            SessionNoSQLStorage_1 storage = (SessionNoSQLStorage_1) tenantIdentifierWithStorage.getSessionStorage();
            while (true) {
                try {
                    String sessionHandle = refreshTokenInfo.sessionHandle;
                    io.supertokens.pluginInterface.session.noSqlStorage.SessionInfoWithLastUpdated sessionInfo = storage
                            .getSessionInfo_Transaction(sessionHandle);

                    if (sessionInfo == null || sessionInfo.expiry < System.currentTimeMillis()) {
                        throw new UnauthorisedException("Session missing in db or has expired");
                    }

                    if (sessionInfo.refreshTokenHash2.equals(Utils.hashSHA256(Utils.hashSHA256(refreshToken)))) {
                        // at this point, the input refresh token is the parent one.
                        String antiCsrfToken = enableAntiCsrf ? UUID.randomUUID().toString() : null;

                        final TokenInfo newRefreshToken = RefreshToken.createNewRefreshToken(
                                tenantIdentifierWithStorage, main, sessionHandle,
                                sessionInfo.recipeUserId, Utils.hashSHA256(refreshToken), antiCsrfToken);
                        TokenInfo newAccessToken = AccessToken.createNewAccessToken(tenantIdentifierWithStorage, main,
                                sessionHandle,
                                sessionInfo.recipeUserId, sessionInfo.userId, Utils.hashSHA256(newRefreshToken.token),
                                Utils.hashSHA256(refreshToken), sessionInfo.userDataInJWT, antiCsrfToken,
                                null, accessTokenVersion, sessionInfo.useStaticKey);

                        TokenInfo idRefreshToken = new TokenInfo(UUID.randomUUID().toString(), newRefreshToken.expiry,
                                newRefreshToken.createdTime);

                        return new SessionInformationHolder(
                                new SessionInfo(sessionHandle, sessionInfo.userId, sessionInfo.recipeUserId,
                                        sessionInfo.userDataInJWT,
                                        tenantIdentifierWithStorage.getTenantId()),
                                newAccessToken, newRefreshToken, idRefreshToken, antiCsrfToken);
                    }

                    if ((refreshTokenInfo.type == RefreshToken.TYPE.FREE
                            && refreshTokenInfo.parentRefreshTokenHash2 != null
                            && refreshTokenInfo.parentRefreshTokenHash2.equals(sessionInfo.refreshTokenHash2))
                            || (refreshTokenInfo.parentRefreshTokenHash1 != null
                            && Utils.hashSHA256(refreshTokenInfo.parentRefreshTokenHash1)
                            .equals(sessionInfo.refreshTokenHash2))) {
                        boolean success = storage.updateSessionInfo_Transaction(sessionHandle,
                                Utils.hashSHA256(Utils.hashSHA256(refreshToken)),
                                System.currentTimeMillis() +
                                        Config.getConfig(tenantIdentifierWithStorage, main).getRefreshTokenValidity(),
                                sessionInfo.lastUpdatedSign);
                        if (!success) {
                            continue;
                        }
                        return refreshSessionHelper(tenantIdentifierWithStorage, main, refreshToken, refreshTokenInfo,
                                enableAntiCsrf,
                                accessTokenVersion);
                    }

                    throw new TokenTheftDetectedException(sessionHandle, sessionInfo.recipeUserId, sessionInfo.userId);

                } catch (NoSuchAlgorithmException | InvalidKeyException
                         | InvalidKeySpecException | SignatureException | NoSuchPaddingException
                         | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
                    throw new StorageTransactionLogicException(e);
                }
            }

        } else {
            throw new UnsupportedOperationException("");
        }
    }

    @TestOnly
    public static String[] revokeSessionUsingSessionHandles(Main main,
                                                            String[] sessionHandles)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return revokeSessionUsingSessionHandles(main,
                new AppIdentifierWithStorage(null, null, storage),
                sessionHandles);
    }

    public static String[] revokeSessionUsingSessionHandles(Main main,
                                                            AppIdentifierWithStorage appIdentifierWithStorage,
                                                            String[] sessionHandles)
            throws StorageQueryException {

        Map<String, List<String>> sessionHandleMap = new HashMap<>();

        for (String sessionHandle : sessionHandles) {
            String tenantId = getTenantIdFromSessionHandle(sessionHandle);
            if (tenantId == null) {
                tenantId = TenantIdentifier.DEFAULT_TENANT_ID;
            }
            if (!sessionHandleMap.containsKey(tenantId)) {
                sessionHandleMap.put(tenantId, new ArrayList<>());
            }

            sessionHandleMap.get(tenantId).add(sessionHandle);
        }

        List<String> revokedSessionHandles = new ArrayList<>();

        for (String tenantId : sessionHandleMap.keySet()) {
            String[] sessionHandlesForTenant = sessionHandleMap.get(tenantId).toArray(new String[0]);

            TenantIdentifier tenantIdentifier = new TenantIdentifier(appIdentifierWithStorage.getConnectionUriDomain(),
                    appIdentifierWithStorage.getAppId(), tenantId);
            TenantIdentifierWithStorage tenantIdentifierWithStorage = null;
            try {
                tenantIdentifierWithStorage = tenantIdentifier.withStorage(
                        StorageLayer.getStorage(tenantIdentifier, main));
            } catch (TenantOrAppNotFoundException e) {
                // ignore as this can happen if the tenant has been deleted after fetching the sessionHandles
                continue;
            }

            String[] sessionHandlesRevokedForTenant = revokeSessionUsingSessionHandles(tenantIdentifierWithStorage,
                    sessionHandlesForTenant);
            revokedSessionHandles.addAll(Arrays.asList(sessionHandlesRevokedForTenant));
        }

        return revokedSessionHandles.toArray(new String[0]);
    }

    private static String[] revokeSessionUsingSessionHandles(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                             String[] sessionHandles)
            throws StorageQueryException {
        Set<String> validHandles = new HashSet<>();

        if (sessionHandles.length > 1) {
            // we need to identify which sessionHandles are valid if there are more than one sessionHandles to revoke
            // if there is only one sessionHandle to revoke, we would know if it was valid by the number of revoked
            // sessions
            for (String sessionHandle : sessionHandles) {
                if (tenantIdentifierWithStorage.getSessionStorage()
                        .getSession(tenantIdentifierWithStorage, sessionHandle) != null) {
                    validHandles.add(sessionHandle);
                }
            }
        }

        int numberOfSessionsRevoked = tenantIdentifierWithStorage.getSessionStorage()
                .deleteSession(tenantIdentifierWithStorage, sessionHandles);

        // most of the time we will enter the below if statement
        if (numberOfSessionsRevoked == sessionHandles.length) {
            return sessionHandles;
        } else if (numberOfSessionsRevoked == 0) {
            return new String[0];
        } else {
            List<String> revokedSessionHandles = new ArrayList<>();
            for (String sessionHandle : sessionHandles) {
                if (!validHandles.contains(sessionHandle)) {
                    continue; // no need to check if the sessionHandle was invalid in the first place
                }
                if (tenantIdentifierWithStorage.getSessionStorage()
                        .getSession(tenantIdentifierWithStorage, sessionHandle) == null) {
                    revokedSessionHandles.add(sessionHandle);
                }
            }
            return revokedSessionHandles.toArray(new String[0]);
        }
    }

    @TestOnly
    public static String[] revokeAllSessionsForUser(Main main, String userId) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return revokeAllSessionsForUser(main,
                new AppIdentifierWithStorage(null, null, storage), userId, true);
    }

    public static String[] revokeAllSessionsForUser(Main main, AppIdentifierWithStorage appIdentifierWithStorage,
                                                    String userId, boolean revokeSessionsForLinkedAccounts)
            throws StorageQueryException {
        String[] sessionHandles = getAllNonExpiredSessionHandlesForUser(main, appIdentifierWithStorage, userId,
                revokeSessionsForLinkedAccounts);
        return revokeSessionUsingSessionHandles(main, appIdentifierWithStorage, sessionHandles);
    }

    public static String[] revokeAllSessionsForUser(Main main, TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                    String userId, boolean revokeSessionsForLinkedAccounts)
            throws StorageQueryException {
        String[] sessionHandles = getAllNonExpiredSessionHandlesForUser(tenantIdentifierWithStorage, userId,
                revokeSessionsForLinkedAccounts);
        return revokeSessionUsingSessionHandles(main, tenantIdentifierWithStorage.toAppIdentifierWithStorage(),
                sessionHandles);
    }

    @TestOnly
    public static String[] getAllNonExpiredSessionHandlesForUser(Main main, String userId)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getAllNonExpiredSessionHandlesForUser(main,
                new AppIdentifierWithStorage(null, null, storage), userId, true);
    }

    public static String[] getAllNonExpiredSessionHandlesForUser(
            Main main, AppIdentifierWithStorage appIdentifierWithStorage, String userId,
            boolean fetchSessionsForAllLinkedAccounts)
            throws StorageQueryException {
        TenantConfig[] tenants = Multitenancy.getAllTenantsForApp(
                appIdentifierWithStorage, main);

        List<String> sessionHandles = new ArrayList<>();

        Set<String> userIds = new HashSet<>();
        userIds.add(userId);
        if (fetchSessionsForAllLinkedAccounts) {
            if (appIdentifierWithStorage.getStorage().getType().equals(STORAGE_TYPE.SQL)) {
                AuthRecipeUserInfo primaryUser = appIdentifierWithStorage.getAuthRecipeStorage()
                        .getPrimaryUserById(appIdentifierWithStorage, userId);
                if (primaryUser != null) {
                    for (LoginMethod lM : primaryUser.loginMethods) {
                        userIds.add(lM.getSupertokensUserId());
                    }
                }
            }
        }

        for (String currUserId : userIds) {
            for (TenantConfig tenant : tenants) {
                TenantIdentifierWithStorage tenantIdentifierWithStorage = null;
                try {
                    tenantIdentifierWithStorage = tenant.tenantIdentifier.withStorage(
                            StorageLayer.getStorage(tenant.tenantIdentifier, main));
                    sessionHandles.addAll(Arrays.asList(getAllNonExpiredSessionHandlesForUser(
                            tenantIdentifierWithStorage, currUserId, false)));

                } catch (TenantOrAppNotFoundException e) {
                    // this might happen when a tenant was deleted after the tenant list was fetched
                    // it is okay to exclude that tenant in the results here
                }
            }
        }

        return sessionHandles.toArray(new String[0]);
    }

    public static String[] getAllNonExpiredSessionHandlesForUser(
            TenantIdentifierWithStorage tenantIdentifierWithStorage, String userId,
            boolean fetchSessionsForAllLinkedAccounts)
            throws StorageQueryException {
        Set<String> userIds = new HashSet<>();
        userIds.add(userId);
        if (fetchSessionsForAllLinkedAccounts) {
            AuthRecipeUserInfo primaryUser = tenantIdentifierWithStorage.getAuthRecipeStorage()
                    .getPrimaryUserById(tenantIdentifierWithStorage.toAppIdentifier(), userId);
            if (primaryUser != null) {
                for (LoginMethod lM : primaryUser.loginMethods) {
                    userIds.add(lM.getSupertokensUserId());
                }
            }
        }
        List<String> sessionHandles = new ArrayList<>();
        for (String currUserId : userIds) {
            sessionHandles.addAll(List.of(tenantIdentifierWithStorage.getSessionStorage()
                    .getAllNonExpiredSessionHandlesForUser(tenantIdentifierWithStorage, currUserId)));
        }
        return sessionHandles.toArray(new String[0]);
    }

    @TestOnly
    public static JsonObject getSessionData(Main main, String sessionHandle)
            throws StorageQueryException, UnauthorisedException {
        Storage storage = StorageLayer.getStorage(main);
        return getSessionData(
                new TenantIdentifierWithStorage(null, null, null, storage),
                sessionHandle);
    }

    @Deprecated
    public static JsonObject getSessionData(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                            String sessionHandle)
            throws StorageQueryException, UnauthorisedException {
        io.supertokens.pluginInterface.session.SessionInfo session = tenantIdentifierWithStorage.getSessionStorage()
                .getSession(tenantIdentifierWithStorage, sessionHandle);
        if (session == null || session.expiry <= System.currentTimeMillis()) {
            throw new UnauthorisedException("Session does not exist.");
        }
        return session.userDataInDatabase;
    }

    @TestOnly
    public static JsonObject getJWTData(Main main, String sessionHandle)
            throws StorageQueryException, UnauthorisedException {
        Storage storage = StorageLayer.getStorage(main);
        return getJWTData(
                new TenantIdentifierWithStorage(null, null, null, storage),
                sessionHandle);
    }

    @Deprecated
    public static JsonObject getJWTData(TenantIdentifierWithStorage tenantIdentifierWithStorage, String sessionHandle)
            throws StorageQueryException, UnauthorisedException {
        io.supertokens.pluginInterface.session.SessionInfo session = tenantIdentifierWithStorage.getSessionStorage()
                .getSession(tenantIdentifierWithStorage, sessionHandle);
        if (session == null || session.expiry <= System.currentTimeMillis()) {
            throw new UnauthorisedException("Session does not exist.");
        }
        return session.userDataInJWT;
    }

    @TestOnly
    public static io.supertokens.pluginInterface.session.SessionInfo getSession(Main main, String sessionHandle)
            throws StorageQueryException, UnauthorisedException {
        Storage storage = StorageLayer.getStorage(main);
        return getSession(
                new TenantIdentifierWithStorage(null, null, null, storage),
                sessionHandle);
    }

    /**
     * Used to retrieve all session information for a given session handle.
     * Used by:
     * - /recipe/session GET
     */
    public static io.supertokens.pluginInterface.session.SessionInfo getSession(
            TenantIdentifierWithStorage tenantIdentifierWithStorage, String sessionHandle)
            throws StorageQueryException, UnauthorisedException {
        io.supertokens.pluginInterface.session.SessionInfo session = tenantIdentifierWithStorage.getSessionStorage()
                .getSession(tenantIdentifierWithStorage, sessionHandle);

        // If there is no session, or session is expired
        if (session == null || session.expiry <= System.currentTimeMillis()) {
            throw new UnauthorisedException("Session does not exist.");
        }

        return session;
    }

    @TestOnly
    public static void updateSession(Main main, String sessionHandle,
                                     @Nullable JsonObject sessionData,
                                     @Nullable JsonObject jwtData,
                                     AccessToken.VERSION version)
            throws StorageQueryException, UnauthorisedException, AccessTokenPayloadError {
        Storage storage = StorageLayer.getStorage(main);
        updateSession(new TenantIdentifierWithStorage(null, null, null, storage),
                sessionHandle, sessionData, jwtData, version);
    }

    public static void updateSession(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                     String sessionHandle, @Nullable JsonObject sessionData,
                                     @Nullable JsonObject jwtData, AccessToken.VERSION version)
            throws StorageQueryException, UnauthorisedException, AccessTokenPayloadError {
        if (jwtData != null &&
                Arrays.stream(AccessTokenInfo.getRequiredAndProtectedProps(version)).anyMatch(jwtData::has)) {
            throw new AccessTokenPayloadError("The user payload contains protected field");
        }

        io.supertokens.pluginInterface.session.SessionInfo session = tenantIdentifierWithStorage.getSessionStorage()
                .getSession(tenantIdentifierWithStorage, sessionHandle);
        // If there is no session, or session is expired
        if (session == null || session.expiry <= System.currentTimeMillis()) {
            throw new UnauthorisedException("Session does not exist.");
        }

        int numberOfRowsAffected = tenantIdentifierWithStorage.getSessionStorage()
                .updateSession(tenantIdentifierWithStorage, sessionHandle, sessionData, jwtData);
        if (numberOfRowsAffected != 1) {
            throw new UnauthorisedException("Session does not exist.");
        }
    }

    @Deprecated
    public static void updateSessionBeforeCDI2_21(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                  String sessionHandle, @Nullable JsonObject sessionData,
                                                  @Nullable JsonObject jwtData)
            throws StorageQueryException, UnauthorisedException {

        io.supertokens.pluginInterface.session.SessionInfo session = tenantIdentifierWithStorage.getSessionStorage()
                .getSession(tenantIdentifierWithStorage, sessionHandle);
        // If there is no session, or session is expired
        if (session == null || session.expiry <= System.currentTimeMillis()) {
            throw new UnauthorisedException("Session does not exist.");
        }

        int numberOfRowsAffected = tenantIdentifierWithStorage.getSessionStorage()
                .updateSession(tenantIdentifierWithStorage, sessionHandle, sessionData,
                        jwtData);
        if (numberOfRowsAffected != 1) {
            throw new UnauthorisedException("Session does not exist.");
        }
    }

    public static String getTenantIdFromSessionHandle(String sessionHandle) {
        String[] parts = sessionHandle.split("_");
        if (parts.length == 1) {
            return null;
        }

        return parts[1];
    }
}
