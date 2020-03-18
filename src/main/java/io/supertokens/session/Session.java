/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This program is licensed under the SuperTokens Community License (the
 *    "License") as published by VRAI Labs. You may not use this file except in
 *    compliance with the License. You are not permitted to transfer or
 *    redistribute this file without express written permission from VRAI Labs.
 *
 *    A copy of the License is available in the file titled
 *    "SuperTokensLicense.pdf" inside this repository or included with your copy of
 *    the software or its source code. If you have not received a copy of the
 *    License, please write to VRAI Labs at team@supertokens.io.
 *
 *    Please read the License carefully before accessing, downloading, copying,
 *    using, modifying, merging, transferring or sharing this software. By
 *    undertaking any of these activities, you indicate your agreement to the terms
 *    of the License.
 *
 *    This program is distributed with certain software that is licensed under
 *    separate terms, as designated in a particular file or component or in
 *    included license documentation. VRAI Labs hereby grants you an additional
 *    permission to link the program and your derivative works with the separately
 *    licensed software that they have included with this program, however if you
 *    modify this program, you shall be solely liable to ensure compliance of the
 *    modified program with the terms of licensing of the separately licensed
 *    software.
 *
 *    Unless required by applicable law or agreed to in writing, this program is
 *    distributed under the License on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *
 */

package io.supertokens.session;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.exceptions.TokenTheftDetectedException;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.sqlStorage.SQLStorage;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.accessToken.AccessToken.AccessTokenInfo;
import io.supertokens.session.info.SessionInfo;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.session.info.TokenInfo;
import io.supertokens.session.refreshToken.RefreshToken;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;

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

                            TokenInfo newAccessToken = AccessToken.createNewAccessToken(main,
                                    accessToken.sessionHandle, accessToken.userId, accessToken.refreshTokenHash1,
                                    null, accessToken.userData, accessToken.antiCsrfToken);

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
                                            sessionInfo.userDataInJWT, antiCsrfToken);

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
        } else {
            throw new UnsupportedOperationException("");
        }
    }

    // returns number of rows deleted
    public static int revokeSessionUsingSessionHandles(Main main, String[] sessionHandles)
            throws StorageQueryException {
        return StorageLayer.getStorageLayer(main).deleteSession(sessionHandles);
    }

    public static int revokeSessionUsingSessionHandle(Main main, String sessionHandle)
            throws StorageQueryException {
        String[] toRevoke = {sessionHandle};
        return revokeSessionUsingSessionHandles(main, toRevoke);
    }

    public static int revokeAllSessionsForUser(Main main, String userId) throws StorageQueryException {
        String[] sessionHandles = getAllSessionHandlesForUser(main, userId);
        return revokeSessionUsingSessionHandles(main, sessionHandles);
    }

    public static String[] getAllSessionHandlesForUser(Main main, String userId) throws StorageQueryException {
        return StorageLayer.getStorageLayer(main).getAllSessionHandlesForUser(userId);
    }

    public static JsonObject getSessionData(Main main, String sessionHandle)
            throws StorageQueryException, UnauthorisedException {
        JsonObject result = StorageLayer.getStorageLayer(main).getSessionData(sessionHandle);
        if (result == null) {
            throw new UnauthorisedException("Session does not exist.");
        }
        return result;
    }

    public static void updateSessionData(Main main, String sessionHandle, JsonObject updatedData)
            throws StorageQueryException, UnauthorisedException {
        int numberOfRowsAffected = StorageLayer.getStorageLayer(main).updateSessionData(sessionHandle, updatedData);
        if (numberOfRowsAffected != 1) {
            throw new UnauthorisedException("Session does not exist.");
        }
    }
}
