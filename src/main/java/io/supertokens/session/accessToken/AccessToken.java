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

package io.supertokens.session.accessToken;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.config.Config;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.licenseKey.LicenseKey;
import io.supertokens.licenseKey.LicenseKey.PLAN_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.session.info.TokenInfo;
import io.supertokens.session.jwt.JWT;
import io.supertokens.session.jwt.JWT.JWTException;
import io.supertokens.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

public class AccessToken {

    // TODO: device fingerprint - store hash of this in JWT.

    private static AccessTokenInfo getInfoFromAccessToken(@Nonnull Main main, @Nonnull String token, boolean retry,
                                                          boolean doAntiCsrfCheck)
            throws StorageQueryException, StorageTransactionLogicException, TryRefreshTokenException {

        Utils.PubPriKey signingKey = AccessTokenSigningKey.getInstance(main).getKey();
        try {
            final JWT.JWTInfo jwtInfo;
            try {
                jwtInfo = JWT.verifyJWTAndGetPayload(token, signingKey.publicKey);
            } catch (InvalidKeyException | NoSuchAlgorithmException | JWTException e) {
                if (retry) {
                    ProcessState.getInstance(main).addState(PROCESS_STATE.RETRYING_ACCESS_TOKEN_JWT_VERIFICATION, e);

                    // remove key from memory and retry
                    AccessTokenSigningKey.getInstance(main).removeKeyFromMemory();
                    return AccessToken.getInfoFromAccessToken(main, token, false, doAntiCsrfCheck);
                } else {
                    throw e;
                }
            }
            AccessTokenInfo tokenInfo = new Gson().fromJson(jwtInfo.payload, AccessTokenInfo.class);
            if (jwtInfo.version == VERSION.V1) {
                if (tokenInfo.sessionHandle == null || tokenInfo.userId == null || tokenInfo.refreshTokenHash1 == null
                        || tokenInfo.userData == null
                        || (doAntiCsrfCheck && tokenInfo.antiCsrfToken == null)) {
                    throw new TryRefreshTokenException(
                            "Access token does not contain all the information. Maybe the structure has changed?");
                }
            } else {
                if (tokenInfo.sessionHandle == null || tokenInfo.userId == null || tokenInfo.refreshTokenHash1 == null
                        || tokenInfo.userData == null || tokenInfo.lmrt == null
                        || (doAntiCsrfCheck && tokenInfo.antiCsrfToken == null)) {
                    throw new TryRefreshTokenException(
                            "Access token does not contain all the information. Maybe the structure has changed?");
                }
            }
            if (tokenInfo.expiryTime < System.currentTimeMillis()) {
                throw new TryRefreshTokenException("Access token expired");
            }

            return tokenInfo;
        } catch (InvalidKeyException | NoSuchAlgorithmException | JWTException e) {
            throw new TryRefreshTokenException(e);
        }

    }

    public static AccessTokenInfo getInfoFromAccessToken(@Nonnull Main main, @Nonnull String token,
                                                         boolean doAntiCsrfCheck)
            throws StorageQueryException, StorageTransactionLogicException, TryRefreshTokenException {
        return getInfoFromAccessToken(main, token, true, doAntiCsrfCheck);
    }

    public static AccessTokenInfo getInfoFromAccessTokenWithoutVerifying(@Nonnull String token) {
        return new Gson()
                .fromJson(JWT.getPayloadWithoutVerifying(token).payload, AccessTokenInfo.class);
    }

    public static TokenInfo createNewAccessToken(@Nonnull Main main, @Nonnull String sessionHandle,
                                                 @Nonnull String userId, @Nonnull String refreshTokenHash1,
                                                 @Nullable String parentRefreshTokenHash1,
                                                 @Nonnull JsonObject userData, @Nullable String antiCsrfToken,
                                                 long lmrt, @Nullable Long expiryTime)
            throws StorageQueryException, StorageTransactionLogicException, InvalidKeyException,
            NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeySpecException, SignatureException {

        Utils.PubPriKey signingKey = AccessTokenSigningKey.getInstance(main).getKey();
        long now = System.currentTimeMillis();
        if (expiryTime == null) {
            expiryTime = now + Config.getConfig(main).getAccessTokenValidity();
        }
        AccessTokenInfo accessToken = new AccessTokenInfo(sessionHandle, userId, refreshTokenHash1, expiryTime,
                parentRefreshTokenHash1, userData, antiCsrfToken, now,
                LicenseKey.get(main).getPlanType() != PLAN_TYPE.FREE, lmrt);
        String token = JWT.createJWT(new Gson().toJsonTree(accessToken), signingKey.privateKey, VERSION.V2);
        return new TokenInfo(token, expiryTime, now, Config.getConfig(main).getAccessTokenPath(),
                Config.getConfig(main).getCookieSecure(main), Config.getConfig(main).getCookieDomain(),
                Config.getConfig(main).getCookieSameSite());

    }

    public static TokenInfo createNewAccessTokenV1(@Nonnull Main main, @Nonnull String sessionHandle,
                                                   @Nonnull String userId, @Nonnull String refreshTokenHash1,
                                                   @Nullable String parentRefreshTokenHash1,
                                                   @Nonnull JsonObject userData, @Nullable String antiCsrfToken)
            throws StorageQueryException, StorageTransactionLogicException, InvalidKeyException,
            NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeySpecException, SignatureException {

        Utils.PubPriKey signingKey = AccessTokenSigningKey.getInstance(main).getKey();
        long now = System.currentTimeMillis();
        AccessTokenInfo accessToken;

        long expiryTime = now + Config.getConfig(main).getAccessTokenValidity();
        accessToken = new AccessTokenInfo(sessionHandle, userId, refreshTokenHash1, expiryTime,
                parentRefreshTokenHash1, userData, antiCsrfToken, now,
                LicenseKey.get(main).getPlanType() != PLAN_TYPE.FREE, null);


        String token = JWT.createJWT(new Gson().toJsonTree(accessToken), signingKey.privateKey, VERSION.V1);
        return new TokenInfo(token, expiryTime, now, Config.getConfig(main).getAccessTokenPath(),
                Config.getConfig(main).getCookieSecure(main), Config.getConfig(main).getCookieDomain(),
                Config.getConfig(main).getCookieSameSite());

    }

    public static VERSION getAccessTokenVersion(AccessTokenInfo accessToken) {
        if (accessToken.lmrt == null) {
            return VERSION.V1;
        }
        return VERSION.V2;
    }

    public static class AccessTokenInfo {
        @Nonnull
        public final String sessionHandle;
        @Nonnull
        public final String userId;
        @Nonnull
        public final String refreshTokenHash1;
        @Nullable
        public final String parentRefreshTokenHash1;
        @Nonnull
        public final JsonObject userData;
        @Nullable
        public final String antiCsrfToken;
        public final long expiryTime;
        final long timeCreated;
        final boolean isPaid;
        @Nullable
        public final Long lmrt; // lastManualRegenerationTime - nullable since v1 of JWT does not have this

        AccessTokenInfo(@Nonnull String sessionHandle, @Nonnull String userId, @Nonnull String refreshTokenHash1,
                        long expiryTime, @Nullable String parentRefreshTokenHash1, @Nonnull JsonObject userData,
                        @Nullable String antiCsrfToken, long timeCreated, boolean isPaid,
                        @Nullable Long lmrt) {
            this.sessionHandle = sessionHandle;
            this.userId = userId;
            this.refreshTokenHash1 = refreshTokenHash1;
            this.expiryTime = expiryTime;
            this.parentRefreshTokenHash1 = parentRefreshTokenHash1;
            this.userData = userData;
            this.antiCsrfToken = antiCsrfToken;
            this.timeCreated = timeCreated;
            this.isPaid = isPaid;
            this.lmrt = lmrt;
        }
    }

    public enum VERSION {
        V1, V2
    }
}
