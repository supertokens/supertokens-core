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

package io.supertokens.session.accessToken;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ProcessState;
import io.supertokens.ProcessState.PROCESS_STATE;
import io.supertokens.config.Config;
import io.supertokens.exceptions.AccessTokenPayloadError;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.jwt.JWTSigningFunctions;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.jwt.JWTAsymmetricSigningKeyInfo;
import io.supertokens.pluginInterface.jwt.JWTSigningKeyInfo;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.session.info.TokenInfo;
import io.supertokens.session.jwt.JWT;
import io.supertokens.session.jwt.JWT.JWTException;
import io.supertokens.signingkeys.JWTSigningKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.security.InvalidKeyException;
import java.security.KeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccessToken {

    // TODO: device fingerprint - store hash of this in JWT.

    private static AccessTokenInfo getInfoFromAccessToken(AppIdentifier appIdentifier,
                                                          @Nonnull Main main, @Nonnull String token, boolean retry,
                                                          boolean doAntiCsrfCheck)
            throws StorageQueryException, StorageTransactionLogicException, TryRefreshTokenException,
            TenantOrAppNotFoundException, UnsupportedJWTSigningAlgorithmException {
        List<JWTSigningKeyInfo> keyInfoList = SigningKeys.getInstance(appIdentifier, main).getAllKeys();
        Exception error = null;
        JWT.JWTInfo jwtInfo = null;
        JWT.JWTPreParseInfo preParseJWTInfo = null;
        try {
            preParseJWTInfo = JWT.preParseJWTInfo(token);
        } catch (JWTException e) {
            // This basically should never happen, but it means, that the token structure is wrong, can't verify
            throw new TryRefreshTokenException(e);
        }

        if (preParseJWTInfo.version != VERSION.V1 && preParseJWTInfo.version != VERSION.V2) {
            String kid = preParseJWTInfo.kid;

            JWTSigningKeyInfo keyInfo = SigningKeys.getInstance(appIdentifier, main).getSigningKeyById(kid);

            if (keyInfo == null) {
                error = new TryRefreshTokenException("Key not found");
            } else {
                try {
                    jwtInfo = JWT.verifyJWTAndGetPayload(preParseJWTInfo,
                            ((JWTAsymmetricSigningKeyInfo) keyInfo).publicKey);
                } catch (NoSuchAlgorithmException e) {
                    // This basically should never happen, but it means, that can't verify any tokens, no need to retry
                    throw new TryRefreshTokenException(e);
                } catch (JWTException e) {
                    // This basically should never happen, but it means, that the token structure is wrong, can't verify
                    throw new TryRefreshTokenException(e);
                } catch (InvalidKeyException e) {
                    // TODO: I don't think this should ever happen either
                    error = e;
                }
            }
        } else {
            for (JWTSigningKeyInfo keyInfo : keyInfoList) {
                try {
                    jwtInfo = JWT.verifyJWTAndGetPayload(preParseJWTInfo,
                            ((JWTAsymmetricSigningKeyInfo) keyInfo).publicKey);
                    error = null;
                    break;
                } catch (NoSuchAlgorithmException e) {
                    // This basically should never happen, but it means, that can't verify any tokens, no need to retry
                    throw new TryRefreshTokenException(e);
                } catch (KeyException | JWTException e) {
                    /*
                     * There are a couple of reasons the verification could fail:
                     * 1) The access token is "corrupted" - this is a rare scenario since it probably means
                     * that someone is trying to break the system. Here we don't mind fetching new keys from the db
                     *
                     * 2) The signing key was updated and an old access token is being used: In this case, the request
                     * should ideally not even come to the core: https://github
                     * .com/supertokens/supertokens-node/issues/136.
                     * TODO: However, we should replicate this logic here as well since we do not want to rely too much
                     * on the client of the core.
                     *
                     * 3) This access token was created with a new signing key, which was changed manually before its
                     * expiry. In here, we want to remove the older signing key from memory and fetch again.
                     *
                     * So overall, since (2) should not call the core in the first place, it's OK to always refetch
                     * the signing key from the db in case of failure and then retry.
                     *
                     */

                    // TODO: check if it's ok to throw only one of the exceptions received.
                    // We could log InvalidKeyExceptions separately, since it signals DB corruption.
                    // Other errors besides the JWTException("JWT verification failed") are always rethrown
                    // even with different keys.
                    // Realistically, only JWTException("JWT verification failed") should get here.
                    error = e;
                }
            }
        }

        if (jwtInfo == null) {
            if (retry) {
                ProcessState.getInstance(main).addState(PROCESS_STATE.RETRYING_ACCESS_TOKEN_JWT_VERIFICATION, error);

                // remove key from memory and retry
                SigningKeys.getInstance(appIdentifier, main).updateKeyCacheIfNotChanged(keyInfoList);
                return AccessToken.getInfoFromAccessToken(appIdentifier, main, token, false, doAntiCsrfCheck);
            }
            throw new TryRefreshTokenException(error);
        }
        AccessTokenInfo tokenInfo = AccessTokenInfo.fromJSON(appIdentifier, jwtInfo.payload, jwtInfo.version);

        if (tokenInfo.expiryTime < System.currentTimeMillis()) {
            throw new TryRefreshTokenException("Access token expired");
        }

        if (doAntiCsrfCheck && tokenInfo.antiCsrfToken == null) {
            throw new TryRefreshTokenException(
                    "Access token does not contain all the information. Maybe the structure has changed?");
        }

        return tokenInfo;
    }

    public static AccessTokenInfo getInfoFromAccessToken(AppIdentifier appIdentifier, @Nonnull Main main,
                                                         @Nonnull String token,
                                                         boolean doAntiCsrfCheck)
            throws StorageQueryException, StorageTransactionLogicException, TryRefreshTokenException,
            TenantOrAppNotFoundException, UnsupportedJWTSigningAlgorithmException {
        return getInfoFromAccessToken(appIdentifier, main, token, true, doAntiCsrfCheck);
    }

    @TestOnly
    public static AccessTokenInfo getInfoFromAccessToken(@Nonnull Main main,
                                                         @Nonnull String token,
                                                         boolean doAntiCsrfCheck)
            throws StorageQueryException, StorageTransactionLogicException, TryRefreshTokenException,
            UnsupportedJWTSigningAlgorithmException {
        try {
            return getInfoFromAccessToken(new AppIdentifier(null, null), main, token, doAntiCsrfCheck);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }


    @TestOnly
    public static AccessTokenInfo getInfoFromAccessTokenWithoutVerifying(@Nonnull String token)
            throws JWTException, TryRefreshTokenException {
        return getInfoFromAccessTokenWithoutVerifying(
                new AppIdentifier(null, null), token);
    }

    public static AccessTokenInfo getInfoFromAccessTokenWithoutVerifying(AppIdentifier appIdentifier,
                                                                         @Nonnull String token)
            throws JWTException, TryRefreshTokenException {
        JWT.JWTInfo jwtInfo = JWT.getPayloadWithoutVerifying(token);

        return AccessTokenInfo.fromJSON(appIdentifier, jwtInfo.payload, jwtInfo.version);
    }

    @TestOnly
    public static TokenInfo createNewAccessToken(@Nonnull Main main,
                                                 @Nonnull String sessionHandle,
                                                 @Nonnull String userId, @Nonnull String refreshTokenHash1,
                                                 @Nullable String parentRefreshTokenHash1,
                                                 @Nonnull JsonObject userData, @Nullable String antiCsrfToken,
                                                 @Nullable Long expiryTime, VERSION version,
                                                 boolean useStaticKey)
            throws StorageQueryException, StorageTransactionLogicException, InvalidKeyException,
            NoSuchAlgorithmException, InvalidKeySpecException, SignatureException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError {
        try {
            return createNewAccessToken(new TenantIdentifier(null, null, null), main, sessionHandle, userId, userId,
                    refreshTokenHash1,
                    parentRefreshTokenHash1,
                    userData, antiCsrfToken, expiryTime, version, useStaticKey);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static TokenInfo createNewAccessToken(TenantIdentifier tenantIdentifier, @Nonnull Main main,
                                                 @Nonnull String sessionHandle,
                                                 @Nonnull String recipeUserId, @Nonnull String primaryUserId,
                                                 @Nonnull String refreshTokenHash1,
                                                 @Nullable String parentRefreshTokenHash1,
                                                 @Nonnull JsonObject userData, @Nullable String antiCsrfToken,
                                                 @Nullable Long expiryTime, VERSION version, boolean useStaticKey)
            throws StorageQueryException, StorageTransactionLogicException, InvalidKeyException,
            NoSuchAlgorithmException, TenantOrAppNotFoundException, InvalidKeySpecException, SignatureException,
            AccessTokenPayloadError, UnsupportedJWTSigningAlgorithmException {

        Utils.PubPriKey signingKey;

        long now = System.currentTimeMillis();
        long expires;
        if (expiryTime != null) {
            expires = expiryTime;
        } else {
            expires = now + Config.getConfig(tenantIdentifier, main).getAccessTokenValidityInMillis();
        }
        AccessTokenInfo accessToken = new AccessTokenInfo(sessionHandle, recipeUserId, primaryUserId, refreshTokenHash1,
                expires,
                parentRefreshTokenHash1, userData, antiCsrfToken, now, version, tenantIdentifier);

        JWTSigningKeyInfo keyToUse;
        if (useStaticKey) {
            keyToUse = SigningKeys.getInstance(tenantIdentifier.toAppIdentifier(), main)
                    .getStaticKeyForAlgorithm(JWTSigningKey.SupportedAlgorithms.RS256);
        } else {
            keyToUse = Utils.getJWTSigningKeyInfoFromKeyInfo(
                    SigningKeys.getInstance(tenantIdentifier.toAppIdentifier(), main).getLatestIssuedDynamicKey());
        }

        String token;
        if (version != VERSION.V1 && version != VERSION.V2) {
            HashMap<String, Object> headers = new HashMap<>();
            headers.put("version", getVersionStringFromAccessTokenVersion(version));
            token = JWTSigningFunctions.createJWTToken(JWTSigningKey.SupportedAlgorithms.RS256, headers,
                    accessToken.toJSON(), null, expires, now, keyToUse);
        } else {
            signingKey = new Utils.PubPriKey(keyToUse.keyString);
            token = JWT.createAndSignLegacyAccessToken(accessToken.toJSON(), signingKey.privateKey, version);
        }

        return new TokenInfo(token, accessToken.expiryTime, accessToken.timeCreated);
    }

    @TestOnly
    public static TokenInfo createNewAccessTokenV1(@Nonnull Main main,
                                                   @Nonnull String sessionHandle,
                                                   @Nonnull String userId, @Nonnull String refreshTokenHash1,
                                                   @Nullable String parentRefreshTokenHash1,
                                                   @Nonnull JsonObject userData, @Nullable String antiCsrfToken)
            throws StorageQueryException, StorageTransactionLogicException, InvalidKeyException,
            NoSuchAlgorithmException, InvalidKeySpecException, SignatureException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError {
        try {
            return createNewAccessTokenV1(new TenantIdentifier(null, null, null), main, sessionHandle, userId,
                    refreshTokenHash1,
                    parentRefreshTokenHash1, userData, antiCsrfToken);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static TokenInfo createNewAccessTokenV1(TenantIdentifier tenantIdentifier, @Nonnull Main main,
                                                   @Nonnull String sessionHandle,
                                                   @Nonnull String userId, @Nonnull String refreshTokenHash1,
                                                   @Nullable String parentRefreshTokenHash1,
                                                   @Nonnull JsonObject userData, @Nullable String antiCsrfToken)
            throws StorageQueryException, StorageTransactionLogicException, InvalidKeyException,
            NoSuchAlgorithmException, InvalidKeySpecException, SignatureException,
            TenantOrAppNotFoundException, UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError {

        Utils.PubPriKey signingKey = new Utils.PubPriKey(
                SigningKeys.getInstance(tenantIdentifier.toAppIdentifier(), main).getLatestIssuedDynamicKey().value);
        long now = System.currentTimeMillis();
        AccessTokenInfo accessToken;

        long expiryTime = now + Config.getConfig(tenantIdentifier, main).getAccessTokenValidityInMillis();
        accessToken = new AccessTokenInfo(sessionHandle, userId, userId, refreshTokenHash1, expiryTime,
                parentRefreshTokenHash1,
                userData, antiCsrfToken, now, VERSION.V1, tenantIdentifier);

        String token = JWT.createAndSignLegacyAccessToken(accessToken.toJSON(), signingKey.privateKey,
                VERSION.V1);
        return new TokenInfo(token, accessToken.expiryTime, accessToken.timeCreated);

    }

    public static VERSION getAccessTokenVersion(AccessTokenInfo accessToken) {
        return accessToken.version;
    }

    public static VERSION getAccessTokenVersionForCDI(SemVer version) {
        if (version.greaterThanOrEqualTo(SemVer.v4_0)) {
            return AccessToken.VERSION.V5;
        }
        if (version.greaterThanOrEqualTo(SemVer.v3_0)) {
            return AccessToken.VERSION.V4;
        }

        if (version.greaterThanOrEqualTo(SemVer.v2_21)) {
            return AccessToken.VERSION.V3;
        }

        return AccessToken.VERSION.V2;
    }

    public static VERSION getVersionFromString(String versionString) {
        return VERSION.valueOf("V" + versionString);
    }

    public static class AccessTokenInfo {
        static String[] requiredAndProtectedPropsV2 = {
                "userId",
                "expiryTime",
                "timeCreated",
                "sessionHandle",
                "refreshTokenHash1"
        };

        static String[] requiredAndProtectedPropsV3 = {
                "sub",
                "exp",
                "iat",
                "sessionHandle",
                "refreshTokenHash1",
                "parentRefreshTokenHash1",
                "antiCsrfToken"
        };

        static String[] requiredAndProtectedPropsV4 = {
                "sub",
                "exp",
                "iat",
                "sessionHandle",
                "refreshTokenHash1",
                "parentRefreshTokenHash1",
                "antiCsrfToken",
                "tId"
        };
        static String[] requiredAndProtectedPropsV5 = {
                "sub",
                "exp",
                "iat",
                "sessionHandle",
                "refreshTokenHash1",
                "parentRefreshTokenHash1",
                "antiCsrfToken",
                "tId",
                "rsub"
        };

        @Nonnull
        public final String sessionHandle;
        @Nonnull
        public final String recipeUserId;
        @Nonnull
        public final String primaryUserId;
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

        @Nonnull
        public VERSION version;

        @Nonnull
        public TenantIdentifier tenantIdentifier;

        AccessTokenInfo(@Nonnull String sessionHandle, @Nonnull String recipeUserId, @Nonnull String primaryUserId,
                        @Nonnull String refreshTokenHash1,
                        long expiryTime, @Nullable String parentRefreshTokenHash1, @Nonnull JsonObject userData,
                        @Nullable String antiCsrfToken, long timeCreated, @Nonnull VERSION version,
                        TenantIdentifier tenantIdentifier) {
            this.sessionHandle = sessionHandle;
            this.recipeUserId = recipeUserId;
            this.primaryUserId = primaryUserId;
            this.refreshTokenHash1 = refreshTokenHash1;
            if (version == VERSION.V2 || version == VERSION.V1) {
                this.expiryTime = expiryTime;
            } else {
                // We round this down to match the data in the JWT which is truncated to seconds instead of MS
                this.expiryTime = expiryTime - (expiryTime % 1000);
            }
            this.parentRefreshTokenHash1 = parentRefreshTokenHash1;
            this.userData = userData;
            this.antiCsrfToken = antiCsrfToken;
            if (version == VERSION.V2 || version == VERSION.V1) {
                this.timeCreated = timeCreated;
            } else {
                // We round this down to match the data in the JWT which is truncated to seconds instead of MS
                this.timeCreated = timeCreated - (timeCreated % 1000);
            }
            this.version = version;
            this.tenantIdentifier = tenantIdentifier;
        }

        static AccessTokenInfo fromJSON(AppIdentifier appIdentifier, JsonObject payload, VERSION version)
                throws TryRefreshTokenException {
            JsonElement parentRefreshTokenHash = payload.get("parentRefreshTokenHash1");
            JsonElement antiCsrfToken = payload.get("antiCsrfToken");

            if (version != VERSION.V1 && version != VERSION.V2) {
                checkRequiredPropsExist(payload, version);
                JsonObject userData = new JsonObject();

                String[] protectedPropNames = getRequiredAndProtectedProps(version);

                for (Map.Entry<String, JsonElement> element : payload.entrySet()) {
                    if (Arrays.stream(protectedPropNames).noneMatch(element.getKey()::equals)) {
                        userData.add(element.getKey(), element.getValue());
                    }
                }

                TenantIdentifier tenantIdentifier;
                if (version == VERSION.V3) {
                    // V3 tokens are from core version 2.21 that did not have support for multi-tenancy
                    // so we always assume that the the tenant id is public always. User may have added `tId`
                    // as a custom payload and we don't want to use that as tenant id here.
                    tenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                            appIdentifier.getAppId(), null);
                } else {
                    tenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                            appIdentifier.getAppId(), payload.get("tId").getAsString());
                }

                String primaryUserId = payload.get("sub").getAsString();
                String recipeUserId = payload.get("sub").getAsString();
                if (version != VERSION.V3 && version != VERSION.V4) {
                    // this means >= v5
                    recipeUserId = payload.get("rsub").getAsString();
                }

                return new AccessTokenInfo(
                        payload.get("sessionHandle").getAsString(),
                        recipeUserId,
                        primaryUserId,
                        payload.get("refreshTokenHash1").getAsString(),
                        payload.get("exp").getAsLong() * 1000,
                        parentRefreshTokenHash == null || parentRefreshTokenHash.isJsonNull() ? null :
                                parentRefreshTokenHash.getAsString(),
                        userData,
                        antiCsrfToken == null || antiCsrfToken.isJsonNull() ? null : antiCsrfToken.getAsString(),
                        payload.get("iat").getAsLong() * 1000,
                        version, tenantIdentifier
                );
            } else {
                checkRequiredPropsExist(payload, version);
                return new AccessTokenInfo(
                        payload.get("sessionHandle").getAsString(),
                        payload.get("userId").getAsString(),
                        payload.get("userId").getAsString(),
                        payload.get("refreshTokenHash1").getAsString(),
                        payload.get("expiryTime").getAsLong(),
                        parentRefreshTokenHash == null || parentRefreshTokenHash.isJsonNull() ? null :
                                parentRefreshTokenHash.getAsString(),
                        payload.get("userData").getAsJsonObject(),
                        antiCsrfToken == null || antiCsrfToken.isJsonNull() ? null : antiCsrfToken.getAsString(),
                        payload.get("timeCreated").getAsLong(),
                        version,
                        appIdentifier.getAsPublicTenantIdentifier()
                );
            }

        }

        JsonObject toJSON() throws AccessTokenPayloadError {
            JsonObject res = new JsonObject();
            if (this.version != VERSION.V1 && this.version != VERSION.V2) {
                res.addProperty("sub", this.primaryUserId);
                res.addProperty("exp", this.expiryTime / 1000);
                res.addProperty("iat", this.timeCreated / 1000);

                if (this.version != VERSION.V3) {
                    res.addProperty("tId", this.tenantIdentifier.getTenantId());
                }
                if (this.version != VERSION.V3 && this.version != VERSION.V4) {
                    // this means >= v5
                    res.addProperty("rsub", this.recipeUserId);
                }
            } else {
                res.addProperty("userId", this.primaryUserId);
                res.addProperty("expiryTime", this.expiryTime);
                res.addProperty("timeCreated", this.timeCreated);
            }
            res.addProperty("sessionHandle", this.sessionHandle);
            res.addProperty("refreshTokenHash1", this.refreshTokenHash1);
            if (this.version == VERSION.V1 || this.version == VERSION.V2) {
                if (parentRefreshTokenHash1 != null) {
                    res.addProperty("parentRefreshTokenHash1", this.parentRefreshTokenHash1);
                }
                if (antiCsrfToken != null) {
                    res.addProperty("antiCsrfToken", this.antiCsrfToken);
                }
            } else {
                // in v3 onwards, we always add these even if they are null
                res.addProperty("parentRefreshTokenHash1", this.parentRefreshTokenHash1);
                res.addProperty("antiCsrfToken", this.antiCsrfToken);
            }

            if (this.version != VERSION.V1 && this.version != VERSION.V2) {
                for (Map.Entry<String, JsonElement> element : this.userData.entrySet()) {
                    if (res.has(element.getKey())) {
                        // The use is trying to add a protected prop into the payload (userId, etc) in V3 (this
                        // should be blocked by validation)
                        throw new AccessTokenPayloadError("The user payload contains protected field");
                    }
                    res.add(element.getKey(), element.getValue());
                }
            } else {
                res.add("userData", userData);
            }

            return res;
        }

        public static String[] getRequiredAndProtectedProps(VERSION version) {
            return switch (version) {
                case V1, V2 -> requiredAndProtectedPropsV2;
                case V3 -> requiredAndProtectedPropsV3;
                case V4 -> requiredAndProtectedPropsV4;
                case V5 -> requiredAndProtectedPropsV5;
                default -> throw new IllegalArgumentException("Unknown version: " + version);
            };
        }

        private static void checkRequiredPropsExist(JsonObject obj, VERSION version)
                throws TryRefreshTokenException {

            String[] propNames = getRequiredAndProtectedProps(version);

            for (String prop : propNames) {
                if (!obj.has(prop)) {
                    throw new TryRefreshTokenException(
                            "Access token does not contain all the information. Maybe the structure has changed? " +
                                    prop);
                }
            }
        }
    }

    public static VERSION getLatestVersion() {
        return VERSION.V5;
    }

    public static String getVersionStringFromAccessTokenVersion(VERSION version) {
        return version.toString().substring(1);
    }

    public enum VERSION {
        V1, V2, V3, V4, V5
    }
}
