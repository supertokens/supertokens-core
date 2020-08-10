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
import io.supertokens.config.Config;
import io.supertokens.exceptions.TokenTheftDetectedException;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.licenseKey.LicenseKey;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.noSqlStorage.NoSQLStorage_1;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
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
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

public class Session {

    public static SessionInformationHolder createNewSession(Main main, @Nonnull String userId,
                                                            @Nonnull JsonObject userDataInJWT,
                                                            @Nonnull JsonObject userDataInDatabase)
            throws NoSuchAlgorithmException, UnsupportedEncodingException, StorageQueryException, InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException, SignatureException {
        String sessionHandle = UUID.randomUUID().toString();
        final TokenInfo refreshToken = RefreshToken.createNewRefreshToken(main, sessionHandle, null);
        String antiCsrfToken = Config.getConfig(main).getEnableAntiCSRF() ? UUID.randomUUID().toString() : null;
        TokenInfo accessToken = AccessToken.createNewAccessToken(main, sessionHandle, userId,
                Utils.hashSHA256(refreshToken.token), null, userDataInJWT, antiCsrfToken, System.currentTimeMillis(),
                null);

        StorageLayer.getStorageLayer(main).createNewSession(sessionHandle, userId,
                Utils.hashSHA256(Utils.hashSHA256(refreshToken.token)), userDataInDatabase, refreshToken.expiry,
                userDataInJWT, refreshToken.createdTime);   // TODO: add lmrt to database

        TokenInfo idRefreshToken = new TokenInfo(UUID.randomUUID().toString(), refreshToken.expiry,
                refreshToken.createdTime, accessToken.cookiePath, accessToken.cookieSecure,
                accessToken.domain, accessToken.sameSite);
        return new SessionInformationHolder(new SessionInfo(sessionHandle, userId, userDataInJWT), accessToken,
                refreshToken, idRefreshToken, antiCsrfToken);

    }

    @TestOnly
    public static SessionInformationHolder createNewSessionV1(Main main, @Nonnull String userId,
                                                              @Nonnull JsonObject userDataInJWT,
                                                              @Nonnull JsonObject userDataInDatabase)
            throws NoSuchAlgorithmException, UnsupportedEncodingException, StorageQueryException, InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException, SignatureException {

        String sessionHandle = UUID.randomUUID().toString();
        LicenseKey.PLAN_TYPE planType = LicenseKey.get(main).getPlanType();
        final TokenInfo refreshToken = RefreshToken.createNewRefreshToken(main, sessionHandle, null);
        String antiCsrfToken = Config.getConfig(main).getEnableAntiCSRF() ? UUID.randomUUID().toString() : null;
        TokenInfo accessToken = AccessToken.createNewAccessTokenV1(main, sessionHandle, userId,
                Utils.hashSHA256(refreshToken.token), null, userDataInJWT, antiCsrfToken);

        StorageLayer.getStorageLayer(main).createNewSession(sessionHandle, userId,
                Utils.hashSHA256(Utils.hashSHA256(refreshToken.token)), userDataInDatabase, refreshToken.expiry,
                userDataInJWT, refreshToken.createdTime);

        TokenInfo idRefreshToken = new TokenInfo(UUID.randomUUID().toString(), refreshToken.expiry,
                refreshToken.createdTime, accessToken.cookiePath, accessToken.cookieSecure,
                accessToken.domain, accessToken.sameSite);
        return new SessionInformationHolder(new SessionInfo(sessionHandle, userId, userDataInJWT), accessToken,
                refreshToken, idRefreshToken, antiCsrfToken);

    }

    /*
     * Quesiton: If the incoming access token is expired, do we throw try refresh token error and not update the db?
     * We should update in database, in SDK session object and not throw an error, nor set any cookie. This is
     * because, the user has already verified the session for this API. If it has expired, the refresh API will be
     * called, and the new JWT info will be set in the token then.
     *
     * Quesiton: If the incoming session has been revoked, do we throw an unauthorised error?
     * Yes. It's important that the user knows that this has happened.
     *
     * Quesiton: If this regenerates session tokens, while another API revokes it, then how will that work?
     * This is OK since the other API will cause a clearing of idRefreshToken and this will not set that. This means
     * that next API call, only the access token will go and that will not pass. In fact, it will be removed.
     *
     *
     * */
    public static SessionInformationHolder regenerateToken(Main main, @Nonnull String token,
                                                           @Nullable JsonObject userDataInJWT)
            throws StorageQueryException
            , StorageTransactionLogicException, UnauthorisedException, InvalidKeySpecException, SignatureException,
            NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {

        // We assume the token has already been verified at this point. It may be expired or JWT signing key may have
        // changed for it...
        AccessTokenInfo accessToken = AccessToken.getInfoFromAccessTokenWithoutVerifying(token);

        JsonObject newJWTUserPayload =
                userDataInJWT == null ? getJWTData(main, accessToken.sessionHandle) : userDataInJWT;
        long lmrt = System.currentTimeMillis();

        updateSession(main, accessToken.sessionHandle, null, newJWTUserPayload, lmrt);

        // if the above succeeds but the below fails, it's OK since the client will get server error and will try
        // again. In this case, the JWT data will be updated again since the API will get the old JWT. In case there
        // is a refresh call, the new JWT will get the new data.
        if (accessToken.expiryTime < System.currentTimeMillis()) {
            // in this case, we set the should not set the access token in the response since they will have to call
            // the refresh API anyway.
            return new SessionInformationHolder(
                    new SessionInfo(accessToken.sessionHandle, accessToken.userId,
                            newJWTUserPayload),
                    null, null, null, null);
        }

        TokenInfo newAccessToken = AccessToken.createNewAccessToken(main,
                accessToken.sessionHandle, accessToken.userId, accessToken.refreshTokenHash1,
                null, newJWTUserPayload, accessToken.antiCsrfToken, lmrt,
                accessToken.expiryTime);

        return new SessionInformationHolder(
                new SessionInfo(accessToken.sessionHandle, accessToken.userId,
                        newJWTUserPayload),
                new TokenInfo(newAccessToken.token, newAccessToken.expiry,
                        newAccessToken.createdTime, Config.getConfig(main).getAccessTokenPath(),
                        Config.getConfig(main).getCookieSecure(main),
                        Config.getConfig(main).getCookieDomain(),
                        Config.getConfig(main).getCookieSameSite()), null, null, null);
    }

    // pass antiCsrfToken to disable csrf check for this request
    public static SessionInformationHolder getSession(Main main, @Nonnull String token, @Nullable String antiCsrfToken,
                                                      boolean allowAntiCsrf)
            throws StorageQueryException
            , StorageTransactionLogicException,
            TryRefreshTokenException, UnauthorisedException {

        AccessTokenInfo accessToken = AccessToken.getInfoFromAccessToken(main, token, allowAntiCsrf &&
                Config.getConfig(main).getEnableAntiCSRF());

        if (Config.getConfig(main).getEnableAntiCSRF() && allowAntiCsrf && (antiCsrfToken == null
                || !antiCsrfToken.equals(accessToken.antiCsrfToken))) {
            throw new TryRefreshTokenException("anti-csrf check failed");
        }

        if (accessToken.parentRefreshTokenHash1 == null) {
            // this means that the refresh token associated with this access token is
            // already the parent - most probably.
            return new SessionInformationHolder(
                    new SessionInfo(accessToken.sessionHandle, accessToken.userId, accessToken.userData), null, null,
                    null, null);
        }

        if (StorageLayer.getStorageLayer(main).getType() == STORAGE_TYPE.SQL) {
            SQLStorage storage = (SQLStorage) StorageLayer.getStorageLayer(main);
            try {
                return storage.startTransaction(con -> {
                    try {

                        SQLStorage.SessionInfo sessionInfo = storage
                                .getSessionInfo_Transaction(con, accessToken.sessionHandle);

                        if (sessionInfo == null) {
                            storage.commitTransaction(con);
                            throw new UnauthorisedException("Session missing in db");
                        }

                        boolean promote = sessionInfo.refreshTokenHash2
                                .equals(Utils.hashSHA256(accessToken.parentRefreshTokenHash1));
                        if (promote || sessionInfo.refreshTokenHash2.equals(accessToken.parentRefreshTokenHash1)) {
                            if (promote) {
                                storage.updateSessionInfo_Transaction(con, accessToken.sessionHandle,
                                        Utils.hashSHA256(accessToken.refreshTokenHash1),
                                        System.currentTimeMillis() + Config.getConfig(main).getRefreshTokenValidity());
                            }
                            storage.commitTransaction(con);

                            TokenInfo newAccessToken;
                            if (AccessToken.getAccessTokenVersion(accessToken) == AccessToken.VERSION.V1) {
                                newAccessToken = AccessToken.createNewAccessTokenV1(main,
                                        accessToken.sessionHandle, accessToken.userId, accessToken.refreshTokenHash1,
                                        null, accessToken.userData, accessToken.antiCsrfToken);
                            } else {
                                assert accessToken.lmrt != null;
                                newAccessToken = AccessToken.createNewAccessToken(main,
                                        accessToken.sessionHandle, accessToken.userId, accessToken.refreshTokenHash1,
                                        null, accessToken.userData, accessToken.antiCsrfToken, accessToken.lmrt, null);
                            }

                            return new SessionInformationHolder(
                                    new SessionInfo(accessToken.sessionHandle, accessToken.userId,
                                            accessToken.userData),
                                    new TokenInfo(newAccessToken.token, newAccessToken.expiry,
                                            newAccessToken.createdTime, Config.getConfig(main).getAccessTokenPath(),
                                            Config.getConfig(main).getCookieSecure(main),
                                            Config.getConfig(main).getCookieDomain(),
                                            Config.getConfig(main).getCookieSameSite()), null, null, null);
                        }

                        storage.commitTransaction(con);
                        return new SessionInformationHolder(
                                new SessionInfo(accessToken.sessionHandle, accessToken.userId, accessToken.userData),
                                null, null,
                                null, null);
                    } catch (UnauthorisedException | NoSuchAlgorithmException | UnsupportedEncodingException |
                            InvalidKeyException | InvalidKeySpecException | SignatureException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof UnauthorisedException) {
                    throw (UnauthorisedException) e.actualException;
                }
                throw e;
            }
        } else if (StorageLayer.getStorageLayer(main).getType() == STORAGE_TYPE.NOSQL_1) {
            NoSQLStorage_1 storage = (NoSQLStorage_1) StorageLayer.getStorageLayer(main);
            while (true) {
                try {

                    NoSQLStorage_1.SessionInfoWithLastUpdated sessionInfo = storage
                            .getSessionInfo_Transaction(accessToken.sessionHandle);

                    if (sessionInfo == null) {
                        throw new UnauthorisedException("Session missing in db");
                    }

                    boolean promote = sessionInfo.refreshTokenHash2
                            .equals(Utils.hashSHA256(accessToken.parentRefreshTokenHash1));
                    if (promote || sessionInfo.refreshTokenHash2.equals(accessToken.parentRefreshTokenHash1)) {
                        if (promote) {
                            boolean success = storage.updateSessionInfo_Transaction(accessToken.sessionHandle,
                                    Utils.hashSHA256(accessToken.refreshTokenHash1),
                                    System.currentTimeMillis() + Config.getConfig(main).getRefreshTokenValidity(),
                                    sessionInfo.lastUpdatedSign);
                            if (!success) {
                                continue;
                            }
                        }

                        TokenInfo newAccessToken;
                        if (AccessToken.getAccessTokenVersion(accessToken) == AccessToken.VERSION.V1) {
                            newAccessToken = AccessToken.createNewAccessTokenV1(main,
                                    accessToken.sessionHandle, accessToken.userId, accessToken.refreshTokenHash1,
                                    null, accessToken.userData, accessToken.antiCsrfToken);
                        } else {
                            assert accessToken.lmrt != null;
                            newAccessToken = AccessToken.createNewAccessToken(main,
                                    accessToken.sessionHandle, accessToken.userId, accessToken.refreshTokenHash1,
                                    null, accessToken.userData, accessToken.antiCsrfToken, accessToken.lmrt, null);
                        }

                        return new SessionInformationHolder(
                                new SessionInfo(accessToken.sessionHandle, accessToken.userId,
                                        accessToken.userData),
                                new TokenInfo(newAccessToken.token, newAccessToken.expiry,
                                        newAccessToken.createdTime, Config.getConfig(main).getAccessTokenPath(),
                                        Config.getConfig(main).getCookieSecure(main),
                                        Config.getConfig(main).getCookieDomain(),
                                        Config.getConfig(main).getCookieSameSite()), null, null, null);
                    }

                    return new SessionInformationHolder(
                            new SessionInfo(accessToken.sessionHandle, accessToken.userId, accessToken.userData),
                            null, null,
                            null, null);
                } catch (NoSuchAlgorithmException | UnsupportedEncodingException |
                        InvalidKeyException | InvalidKeySpecException | SignatureException e) {
                    throw new StorageTransactionLogicException(e);
                }
            }
        } else {
            throw new UnsupportedOperationException("");
        }
    }

    public static SessionInformationHolder refreshSession(Main main, @Nonnull String refreshToken)
            throws StorageTransactionLogicException, UnauthorisedException, StorageQueryException,
            TokenTheftDetectedException {
        RefreshToken.RefreshTokenInfo refreshTokenInfo = RefreshToken.getInfoFromRefreshToken(main, refreshToken);
        return refreshSessionHelper(main, refreshToken, refreshTokenInfo);
    }

    private static SessionInformationHolder refreshSessionHelper(Main main, String refreshToken,
                                                                 RefreshToken.RefreshTokenInfo refreshTokenInfo)
            throws StorageTransactionLogicException, UnauthorisedException, StorageQueryException,
            TokenTheftDetectedException {
        //////////////////////////////////////////SQL/////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        if (StorageLayer.getStorageLayer(main).getType() == STORAGE_TYPE.SQL) {
            SQLStorage storage = (SQLStorage) StorageLayer.getStorageLayer(main);
            try {
                return storage.startTransaction(con -> {
                    try {
                        String sessionHandle = refreshTokenInfo.sessionHandle;
                        SQLStorage.SessionInfo sessionInfo = storage
                                .getSessionInfo_Transaction(con, sessionHandle);

                        if (sessionInfo == null || sessionInfo.expiry < System.currentTimeMillis()) {
                            storage.commitTransaction(con);
                            throw new UnauthorisedException("Session missing in db or has expired");
                        }

                        if (sessionInfo.refreshTokenHash2.equals(Utils.hashSHA256(Utils.hashSHA256(refreshToken)))) {
                            // at this point, the input refresh token is the parent one.
                            storage.commitTransaction(con);
                            final TokenInfo newRefreshToken = RefreshToken
                                    .createNewRefreshToken(main, sessionHandle,
                                            Utils.hashSHA256(Utils.hashSHA256(refreshToken)));

                            String antiCsrfToken =
                                    Config.getConfig(main).getEnableAntiCSRF() ? UUID.randomUUID().toString() : null;
                            TokenInfo newAccessToken = AccessToken
                                    .createNewAccessToken(main, sessionHandle, sessionInfo.userId,
                                            Utils.hashSHA256(newRefreshToken.token), Utils.hashSHA256(refreshToken),
                                            sessionInfo.userDataInJWT, antiCsrfToken,
                                            System.currentTimeMillis(), null);    // TODO: get lmrt from database

                            TokenInfo idRefreshToken = new TokenInfo(UUID.randomUUID().toString(),
                                    newRefreshToken.expiry,
                                    newRefreshToken.createdTime, newAccessToken.cookiePath, newAccessToken.cookieSecure,
                                    newAccessToken.domain, newAccessToken.sameSite);

                            return new SessionInformationHolder(
                                    new SessionInfo(sessionHandle, sessionInfo.userId, sessionInfo.userDataInJWT),
                                    newAccessToken,
                                    newRefreshToken, idRefreshToken, antiCsrfToken);
                        }

                        if (refreshTokenInfo.parentRefreshTokenHash2 != null &&
                                refreshTokenInfo.parentRefreshTokenHash2
                                        .equals(sessionInfo.refreshTokenHash2)) {
                            storage.updateSessionInfo_Transaction(con, sessionHandle,
                                    Utils.hashSHA256(Utils.hashSHA256(refreshToken)),
                                    System.currentTimeMillis() + Config.getConfig(main).getRefreshTokenValidity());

                            storage.commitTransaction(con);

                            return refreshSessionHelper(main, refreshToken, refreshTokenInfo);
                        }

                        storage.commitTransaction(con);

                        throw new TokenTheftDetectedException(sessionHandle, sessionInfo.userId);

                    } catch (UnauthorisedException | NoSuchAlgorithmException | InvalidKeyException |
                            UnsupportedEncodingException | TokenTheftDetectedException | InvalidKeySpecException |
                            SignatureException e) {
                        throw new StorageTransactionLogicException(e);
                    }
                });
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof UnauthorisedException) {
                    throw (UnauthorisedException) e.actualException;
                } else if (e.actualException instanceof TokenTheftDetectedException) {
                    throw (TokenTheftDetectedException) e.actualException;
                }
                throw e;
            }

            //////////////////////////////////////////NOSQL_1/////////////////////////////////////////////
            //////////////////////////////////////////////////////////////////////////////////////////////
            //////////////////////////////////////////////////////////////////////////////////////////////
            //////////////////////////////////////////////////////////////////////////////////////////////
            //////////////////////////////////////////////////////////////////////////////////////////////
            //////////////////////////////////////////////////////////////////////////////////////////////
        } else if (StorageLayer.getStorageLayer(main).getType() == STORAGE_TYPE.NOSQL_1) {
            NoSQLStorage_1 storage = (NoSQLStorage_1) StorageLayer.getStorageLayer(main);
            while (true) {
                try {
                    String sessionHandle = refreshTokenInfo.sessionHandle;
                    NoSQLStorage_1.SessionInfoWithLastUpdated sessionInfo = storage
                            .getSessionInfo_Transaction(sessionHandle);

                    if (sessionInfo == null || sessionInfo.expiry < System.currentTimeMillis()) {
                        throw new UnauthorisedException("Session missing in db or has expired");
                    }

                    if (sessionInfo.refreshTokenHash2.equals(Utils.hashSHA256(Utils.hashSHA256(refreshToken)))) {
                        // at this point, the input refresh token is the parent one.
                        final TokenInfo newRefreshToken = RefreshToken.createNewRefreshToken(main, sessionHandle,
                                Utils.hashSHA256(Utils.hashSHA256(refreshToken)));


                        String antiCsrfToken =
                                Config.getConfig(main).getEnableAntiCSRF() ? UUID.randomUUID().toString() : null;
                        TokenInfo newAccessToken = AccessToken
                                .createNewAccessToken(main, sessionHandle, sessionInfo.userId,
                                        Utils.hashSHA256(newRefreshToken.token), Utils.hashSHA256(refreshToken),
                                        sessionInfo.userDataInJWT, antiCsrfToken,
                                        System.currentTimeMillis(), null);    // TODO: get lmrt from database

                        TokenInfo idRefreshToken = new TokenInfo(UUID.randomUUID().toString(),
                                newRefreshToken.expiry,
                                newRefreshToken.createdTime, newAccessToken.cookiePath, newAccessToken.cookieSecure,
                                newAccessToken.domain, newAccessToken.sameSite);

                        return new SessionInformationHolder(
                                new SessionInfo(sessionHandle, sessionInfo.userId, sessionInfo.userDataInJWT),
                                newAccessToken,
                                newRefreshToken, idRefreshToken, antiCsrfToken);
                    }

                    if (refreshTokenInfo.parentRefreshTokenHash2 != null &&
                            refreshTokenInfo.parentRefreshTokenHash2
                                    .equals(sessionInfo.refreshTokenHash2)) {
                        boolean success = storage.updateSessionInfo_Transaction(sessionHandle,
                                Utils.hashSHA256(Utils.hashSHA256(refreshToken)),
                                System.currentTimeMillis() + Config.getConfig(main).getRefreshTokenValidity(),
                                sessionInfo.lastUpdatedSign);
                        if (!success) {
                            continue;
                        }
                        return refreshSessionHelper(main, refreshToken, refreshTokenInfo);
                    }

                    throw new TokenTheftDetectedException(sessionHandle, sessionInfo.userId);

                } catch (NoSuchAlgorithmException | InvalidKeyException |
                        UnsupportedEncodingException | InvalidKeySpecException | SignatureException e) {
                    throw new StorageTransactionLogicException(e);
                }
            }

        } else {
            throw new UnsupportedOperationException("");
        }
    }

    public static String[] revokeSessionUsingSessionHandles(Main main, String[] sessionHandles)
            throws StorageQueryException {
        int numberOfSessionsRevoked = StorageLayer.getStorageLayer(main).deleteSession(sessionHandles);

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

            if (StorageLayer.getStorageLayer(main).getSession(sessionHandle) == null) {
                result[indexIntoResult] = sessionHandle;
                indexIntoResult++;
            }
        }

        return result;
    }

    public static String[] revokeAllSessionsForUser(Main main, String userId) throws StorageQueryException {
        String[] sessionHandles = getAllSessionHandlesForUser(main, userId);
        return revokeSessionUsingSessionHandles(main, sessionHandles);
    }

    public static String[] getAllSessionHandlesForUser(Main main, String userId) throws StorageQueryException {
        return StorageLayer.getStorageLayer(main).getAllSessionHandlesForUser(userId);
    }

    public static JsonObject getSessionData(Main main, String sessionHandle)
            throws StorageQueryException, UnauthorisedException {
        SQLStorage.SessionInfo session = StorageLayer.getStorageLayer(main).getSession(sessionHandle);
        if (session == null) {
            throw new UnauthorisedException("Session does not exist.");
        }
        return session.userDataInDatabase;
    }

    public static JsonObject getJWTData(Main main, String sessionHandle)
            throws StorageQueryException, UnauthorisedException {
        SQLStorage.SessionInfo session = StorageLayer.getStorageLayer(main).getSession(sessionHandle);
        if (session == null) {
            throw new UnauthorisedException("Session does not exist.");
        }
        return session.userDataInJWT;
    }

    public static void updateSession(Main main, String sessionHandle, @Nullable JsonObject sessionData,
                                     @Nullable JsonObject jwtData, @Nullable Long lmrt)
            throws StorageQueryException, UnauthorisedException {
        int numberOfRowsAffected = StorageLayer.getStorageLayer(main)
                .updateSession(sessionHandle, sessionData, jwtData);    // TODO: update lmrt as well
        if (numberOfRowsAffected != 1) {
            throw new UnauthorisedException("Session does not exist.");
        }
    }
}
