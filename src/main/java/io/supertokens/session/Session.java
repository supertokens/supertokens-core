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
import io.supertokens.exceptions.TokenTheftDetectedException;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.session.noSqlStorage.SessionNoSQLStorage_1;
import io.supertokens.pluginInterface.session.sqlStorage.SessionSQLStorage;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.accessToken.AccessToken.AccessTokenInfo;
import io.supertokens.session.info.SessionInfo;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.session.info.TokenInfo;
import io.supertokens.session.refreshToken.RefreshToken;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

public class Session {

    @TestOnly
    public static SessionInformationHolder createNewSession(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                            Main main,
                                                            @Nonnull String userId,
                                                            @Nonnull JsonObject userDataInJWT,
                                                            @Nonnull JsonObject userDataInDatabase)
            throws NoSuchAlgorithmException, UnsupportedEncodingException, StorageQueryException, InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException, SignatureException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {
        try {
            return createNewSession(tenantIdentifierWithStorage, main, userId, userDataInJWT, userDataInDatabase,
                    false);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @TestOnly
    public static SessionInformationHolder createNewSession(Main main,
                                                            @Nonnull String userId,
                                                            @Nonnull JsonObject userDataInJWT,
                                                            @Nonnull JsonObject userDataInDatabase)
            throws NoSuchAlgorithmException, UnsupportedEncodingException, StorageQueryException, InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException, SignatureException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {
        Storage storage = StorageLayer.getStorage(main);
        return createNewSession(
                new TenantIdentifierWithStorage(null, null, null, storage), main,
                userId, userDataInJWT, userDataInDatabase);
    }

    @TestOnly
    public static SessionInformationHolder createNewSession(Main main, @Nonnull String userId,
                                                            @Nonnull JsonObject userDataInJWT,
                                                            @Nonnull JsonObject userDataInDatabase,
                                                            boolean enableAntiCsrf)
            throws NoSuchAlgorithmException, UnsupportedEncodingException, StorageQueryException, InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException, SignatureException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return createNewSession(
                    new TenantIdentifierWithStorage(null, null, null, storage), main,
                    userId, userDataInJWT, userDataInDatabase, enableAntiCsrf);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static SessionInformationHolder createNewSession(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main,
                                                            @Nonnull String userId,
                                                            @Nonnull JsonObject userDataInJWT,
                                                            @Nonnull JsonObject userDataInDatabase,
                                                            boolean enableAntiCsrf)
            throws NoSuchAlgorithmException, UnsupportedEncodingException, StorageQueryException, InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException, SignatureException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException,
            TenantOrAppNotFoundException {
        String sessionHandle = UUID.randomUUID().toString();
        String antiCsrfToken = enableAntiCsrf ? UUID.randomUUID().toString() : null;
        final TokenInfo refreshToken = RefreshToken.createNewRefreshToken(tenantIdentifierWithStorage, main,
                sessionHandle, userId, null,
                antiCsrfToken);

        TokenInfo accessToken = AccessToken.createNewAccessToken(tenantIdentifierWithStorage, main,
                sessionHandle,
                userId,
                Utils.hashSHA256(refreshToken.token), null, userDataInJWT, antiCsrfToken, System.currentTimeMillis(),
                null);

        tenantIdentifierWithStorage.getSessionStorage().createNewSession(tenantIdentifierWithStorage, sessionHandle, userId,
                Utils.hashSHA256(Utils.hashSHA256(refreshToken.token)), userDataInDatabase, refreshToken.expiry,
                userDataInJWT, refreshToken.createdTime); // TODO: add lmrt to database

        TokenInfo idRefreshToken = new TokenInfo(UUID.randomUUID().toString(), refreshToken.expiry,
                refreshToken.createdTime);
        return new SessionInformationHolder(new SessionInfo(sessionHandle, userId, userDataInJWT), accessToken,
                refreshToken, idRefreshToken, antiCsrfToken);

    }

    @TestOnly
    public static SessionInformationHolder regenerateToken(Main main,
                                                           @Nonnull String token,
                                                           @Nullable JsonObject userDataInJWT)
            throws StorageQueryException, StorageTransactionLogicException,
            UnauthorisedException, InvalidKeySpecException, SignatureException, NoSuchAlgorithmException,
            InvalidKeyException, UnsupportedEncodingException {
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
            InvalidKeyException, UnsupportedEncodingException, TenantOrAppNotFoundException {

        // We assume the token has already been verified at this point. It may be expired or JWT signing key may have
        // changed for it...
        AccessTokenInfo accessToken = AccessToken.getInfoFromAccessTokenWithoutVerifying(appIdentifier, token);
        TenantIdentifierWithStorage tenantIdentifierWithStorage = accessToken.tenantIdentifier.withStorage(
                StorageLayer.getStorage(accessToken.tenantIdentifier, main));
        JsonObject newJWTUserPayload = userDataInJWT == null ?
                getSession(tenantIdentifierWithStorage, accessToken.sessionHandle).userDataInJWT
                : userDataInJWT;
        long lmrt = System.currentTimeMillis();

        updateSession(tenantIdentifierWithStorage, accessToken.sessionHandle, null, newJWTUserPayload, lmrt);

        // if the above succeeds but the below fails, it's OK since the client will get server error and will try
        // again. In this case, the JWT data will be updated again since the API will get the old JWT. In case there
        // is a refresh call, the new JWT will get the new data.
        if (accessToken.expiryTime < System.currentTimeMillis()) {
            // in this case, we set the should not set the access token in the response since they will have to call
            // the refresh API anyway.
            return new SessionInformationHolder(
                    new SessionInfo(accessToken.sessionHandle, accessToken.userId, newJWTUserPayload), null, null, null,
                    null);
        }

        TokenInfo newAccessToken = AccessToken.createNewAccessToken(tenantIdentifierWithStorage, main,
                accessToken.sessionHandle, accessToken.userId,
                accessToken.refreshTokenHash1, accessToken.parentRefreshTokenHash1, newJWTUserPayload,
                accessToken.antiCsrfToken, lmrt, accessToken.expiryTime);

        return new SessionInformationHolder(
                new SessionInfo(accessToken.sessionHandle, accessToken.userId, newJWTUserPayload),
                new TokenInfo(newAccessToken.token, newAccessToken.expiry, newAccessToken.createdTime), null, null,
                null);
    }

    @TestOnly
    public static SessionInformationHolder getSession(Main main, @Nonnull String token, @Nullable String antiCsrfToken,
                                                      boolean enableAntiCsrf, Boolean doAntiCsrfCheck)
            throws StorageQueryException,
            StorageTransactionLogicException, TryRefreshTokenException, UnauthorisedException {
        try {
            return getSession(new AppIdentifier(null, null), main, token, antiCsrfToken, enableAntiCsrf,
                    doAntiCsrfCheck);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // pass antiCsrfToken to disable csrf check for this request
    public static SessionInformationHolder getSession(AppIdentifier appIdentifier, Main main,
                                                      @Nonnull String token, @Nullable String antiCsrfToken,
                                                      boolean enableAntiCsrf, Boolean doAntiCsrfCheck)
            throws StorageQueryException,
            StorageTransactionLogicException, TryRefreshTokenException, UnauthorisedException,
            TenantOrAppNotFoundException {

        AccessTokenInfo accessToken = AccessToken.getInfoFromAccessToken(appIdentifier, main,
                token,
                doAntiCsrfCheck && enableAntiCsrf);
        TenantIdentifierWithStorage tenantIdentifierWithStorage = accessToken.tenantIdentifier.withStorage(
                StorageLayer.getStorage(accessToken.tenantIdentifier, main));

        if (enableAntiCsrf && doAntiCsrfCheck
                && (antiCsrfToken == null || !antiCsrfToken.equals(accessToken.antiCsrfToken))) {
            throw new TryRefreshTokenException("anti-csrf check failed");
        }

        io.supertokens.pluginInterface.session.SessionInfo sessionInfoForBlacklisting = null;
        if (Config.getConfig(tenantIdentifierWithStorage, main).getAccessTokenBlacklisting()) {
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
                    new SessionInfo(accessToken.sessionHandle, accessToken.userId, accessToken.userData), null, null,
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
                                .getSessionInfo_Transaction(tenantIdentifierWithStorage, con, accessToken.sessionHandle);

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
                                storage.updateSessionInfo_Transaction(tenantIdentifierWithStorage, con, accessToken.sessionHandle,
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
                                        accessToken.userId, accessToken.refreshTokenHash1, null,
                                        sessionInfo.userDataInJWT, accessToken.antiCsrfToken);
                            } else {
                                assert accessToken.lmrt != null;
                                newAccessToken = AccessToken.createNewAccessToken(tenantIdentifierWithStorage,
                                        main,
                                        accessToken.sessionHandle,
                                        accessToken.userId, accessToken.refreshTokenHash1, null,
                                        sessionInfo.userDataInJWT, accessToken.antiCsrfToken, accessToken.lmrt, null);
                            }

                            return new SessionInformationHolder(
                                    new SessionInfo(accessToken.sessionHandle, accessToken.userId,
                                            sessionInfo.userDataInJWT),
                                    new TokenInfo(newAccessToken.token, newAccessToken.expiry,
                                            newAccessToken.createdTime),
                                    null, null, null);
                        }

                        storage.commitTransaction(con);
                        return new SessionInformationHolder(
                                new SessionInfo(accessToken.sessionHandle, accessToken.userId, accessToken.userData),
                                // here we purposely use accessToken.userData instead of sessionInfo.userDataInJWT
                                // because we are not returning a new access token
                                null, null, null, null);
                    } catch (UnauthorisedException | NoSuchAlgorithmException | UnsupportedEncodingException
                            | InvalidKeyException | InvalidKeySpecException | SignatureException | TenantOrAppNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof UnauthorisedException) {
                    throw (UnauthorisedException) e.actualException;
                } else if (e.actualException instanceof TenantOrAppNotFoundException) {
                    throw (TenantOrAppNotFoundException) e.actualException;
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
                        if (AccessToken.getAccessTokenVersion(accessToken) == AccessToken.VERSION.V1) {
                            newAccessToken = AccessToken.createNewAccessTokenV1(tenantIdentifierWithStorage,
                                    main,
                                    accessToken.sessionHandle,
                                    accessToken.userId, accessToken.refreshTokenHash1, null, sessionInfo.userDataInJWT,
                                    accessToken.antiCsrfToken);
                        } else {
                            assert accessToken.lmrt != null;
                            newAccessToken = AccessToken.createNewAccessToken(tenantIdentifierWithStorage, main,
                                    accessToken.sessionHandle,
                                    accessToken.userId, accessToken.refreshTokenHash1, null, sessionInfo.userDataInJWT,
                                    accessToken.antiCsrfToken, accessToken.lmrt, null);
                        }

                        return new SessionInformationHolder(
                                new SessionInfo(accessToken.sessionHandle, accessToken.userId,
                                        sessionInfo.userDataInJWT),
                                new TokenInfo(newAccessToken.token, newAccessToken.expiry, newAccessToken.createdTime),
                                null, null, null);
                    }

                    return new SessionInformationHolder(
                            new SessionInfo(accessToken.sessionHandle, accessToken.userId, accessToken.userData),
                            // here we purposely use accessToken.userData instead of sessionInfo.userDataInJWT
                            // because we are not returning a new access token
                            null, null, null, null);
                } catch (NoSuchAlgorithmException | UnsupportedEncodingException | InvalidKeyException
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
                                                          @Nullable String antiCsrfToken, boolean enableAntiCsrf)
            throws StorageTransactionLogicException,
            UnauthorisedException, StorageQueryException, TokenTheftDetectedException {
        try {
            return refreshSession(new AppIdentifier(null, null), main, refreshToken, antiCsrfToken,
                    enableAntiCsrf);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static SessionInformationHolder refreshSession(AppIdentifier appIdentifier, Main main,
                                                          @Nonnull String refreshToken,
                                                          @Nullable String antiCsrfToken, boolean enableAntiCsrf)
            throws StorageTransactionLogicException,
            UnauthorisedException, StorageQueryException, TokenTheftDetectedException, TenantOrAppNotFoundException {
        RefreshToken.RefreshTokenInfo refreshTokenInfo = RefreshToken.getInfoFromRefreshToken(
                appIdentifier, main,
                refreshToken);

        if (enableAntiCsrf && refreshTokenInfo.antiCsrfToken != null) {
            // anti csrf is enabled, and the refresh token contains an anticsrf token (it's not the older version)
            if (!refreshTokenInfo.antiCsrfToken.equals(antiCsrfToken)) {
                throw new UnauthorisedException("Anti CSRF token missing, or not matching");
            }
        }

        return refreshSessionHelper(
                refreshTokenInfo.tenantIdentifier.withStorage(
                        StorageLayer.getStorage(refreshTokenInfo.tenantIdentifier, main)),
                main, refreshToken, refreshTokenInfo,
                enableAntiCsrf);
    }

    private static SessionInformationHolder refreshSessionHelper(TenantIdentifierWithStorage tenantIdentifierWithStorage, Main main,
                                                                 String refreshToken,
                                                                 RefreshToken.RefreshTokenInfo refreshTokenInfo,
                                                                 boolean enableAntiCsrf)
            throws StorageTransactionLogicException, UnauthorisedException, StorageQueryException,
            TokenTheftDetectedException, TenantOrAppNotFoundException {
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
                                    tenantIdentifierWithStorage, main,
                                    sessionHandle,
                                    sessionInfo.userId, Utils.hashSHA256(refreshToken), antiCsrfToken);

                            TokenInfo newAccessToken = AccessToken.createNewAccessToken(
                                    tenantIdentifierWithStorage,
                                    main, sessionHandle,
                                    sessionInfo.userId, Utils.hashSHA256(newRefreshToken.token),
                                    Utils.hashSHA256(refreshToken), sessionInfo.userDataInJWT, antiCsrfToken,
                                    System.currentTimeMillis(), null); // TODO: get lmrt from database

                            TokenInfo idRefreshToken = new TokenInfo(UUID.randomUUID().toString(),
                                    newRefreshToken.expiry, newRefreshToken.createdTime);

                            return new SessionInformationHolder(
                                    new SessionInfo(sessionHandle, sessionInfo.userId, sessionInfo.userDataInJWT),
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
                                    refreshTokenInfo, enableAntiCsrf);
                        }

                        storage.commitTransaction(con);

                        throw new TokenTheftDetectedException(sessionHandle, sessionInfo.userId);

                    } catch (UnauthorisedException | NoSuchAlgorithmException | InvalidKeyException
                            | UnsupportedEncodingException | TokenTheftDetectedException | InvalidKeySpecException
                            | SignatureException | NoSuchPaddingException | InvalidAlgorithmParameterException
                            | IllegalBlockSizeException | BadPaddingException | TenantOrAppNotFoundException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof UnauthorisedException) {
                    throw (UnauthorisedException) e.actualException;
                } else if (e.actualException instanceof TokenTheftDetectedException) {
                    throw (TokenTheftDetectedException) e.actualException;
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
                                tenantIdentifierWithStorage, main,
                                sessionHandle,
                                sessionInfo.userId, Utils.hashSHA256(refreshToken), antiCsrfToken);
                        TokenInfo newAccessToken = AccessToken.createNewAccessToken(tenantIdentifierWithStorage,
                                main,
                                sessionHandle,
                                sessionInfo.userId, Utils.hashSHA256(newRefreshToken.token),
                                Utils.hashSHA256(refreshToken), sessionInfo.userDataInJWT, antiCsrfToken,
                                System.currentTimeMillis(), null); // TODO: get lmrt from database

                        TokenInfo idRefreshToken = new TokenInfo(UUID.randomUUID().toString(), newRefreshToken.expiry,
                                newRefreshToken.createdTime);

                        return new SessionInformationHolder(
                                new SessionInfo(sessionHandle, sessionInfo.userId, sessionInfo.userDataInJWT),
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
                                enableAntiCsrf);
                    }

                    throw new TokenTheftDetectedException(sessionHandle, sessionInfo.userId);

                } catch (NoSuchAlgorithmException | InvalidKeyException | UnsupportedEncodingException
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
        return revokeSessionUsingSessionHandles(
                new TenantIdentifierWithStorage(null, null, null, storage),
                sessionHandles);
    }

    public static String[] revokeSessionUsingSessionHandles(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                            String[] sessionHandles)
            throws StorageQueryException {
        int numberOfSessionsRevoked = tenantIdentifierWithStorage.getSessionStorage()
                .deleteSession(tenantIdentifierWithStorage, sessionHandles);

        // most of the time we will enter the below if statement
        if (numberOfSessionsRevoked == sessionHandles.length) {
            return sessionHandles;
        }

        String[] result = new String[numberOfSessionsRevoked];
        int indexIntoResult = 0;
        for (String sessionHandle : sessionHandles) {
            if (indexIntoResult >= numberOfSessionsRevoked) {
                break;
            }

            if (tenantIdentifierWithStorage.getSessionStorage().getSession(tenantIdentifierWithStorage, sessionHandle) ==
                    null) {
                result[indexIntoResult] = sessionHandle;
                indexIntoResult++;
            }
        }

        return result;
    }

    @TestOnly
    public static String[] revokeAllSessionsForUser(Main main,
                                                    String userId) throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return revokeAllSessionsForUser(
                new TenantIdentifierWithStorage(null, null, null, storage), userId);
    }

    public static String[] revokeAllSessionsForUser(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                    String userId) throws StorageQueryException {
        String[] sessionHandles = getAllNonExpiredSessionHandlesForUser(tenantIdentifierWithStorage, userId);
        return revokeSessionUsingSessionHandles(tenantIdentifierWithStorage, sessionHandles);
    }

    @TestOnly
    public static String[] getAllNonExpiredSessionHandlesForUser(Main main, String userId)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getAllNonExpiredSessionHandlesForUser(
                new TenantIdentifierWithStorage(null, null, null, storage), userId);
    }

    public static String[] getAllNonExpiredSessionHandlesForUser(
            TenantIdentifierWithStorage tenantIdentifierWithStorage, String userId)
            throws StorageQueryException {
        return tenantIdentifierWithStorage.getSessionStorage()
                .getAllNonExpiredSessionHandlesForUser(tenantIdentifierWithStorage, userId);
    }

    @TestOnly
    @Deprecated
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
    @Deprecated
    public static JsonObject getJWTData(Main main, String sessionHandle)
            throws StorageQueryException, UnauthorisedException {
        Storage storage =StorageLayer.getStorage(main);
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
                                     @Nullable JsonObject jwtData, @Nullable Long lmrt)
            throws StorageQueryException, UnauthorisedException {
        Storage storage = StorageLayer.getStorage(main);
        updateSession(new TenantIdentifierWithStorage(null, null, null, storage),
                sessionHandle, sessionData, jwtData, lmrt);
    }

    public static void updateSession(TenantIdentifierWithStorage tenantIdentifierWithStorage, String sessionHandle,
                                     @Nullable JsonObject sessionData,
                                     @Nullable JsonObject jwtData, @Nullable Long lmrt)
            throws StorageQueryException, UnauthorisedException {
        io.supertokens.pluginInterface.session.SessionInfo session = tenantIdentifierWithStorage.getSessionStorage()
                .getSession(tenantIdentifierWithStorage, sessionHandle);
        // If there is no session, or session is expired
        if (session == null || session.expiry <= System.currentTimeMillis()) {
            throw new UnauthorisedException("Session does not exist.");
        }

        int numberOfRowsAffected = tenantIdentifierWithStorage.getSessionStorage()
                .updateSession(tenantIdentifierWithStorage, sessionHandle, sessionData,
                        jwtData); // TODO: update lmrt as well
        if (numberOfRowsAffected != 1) {
            throw new UnauthorisedException("Session does not exist.");
        }
    }
}