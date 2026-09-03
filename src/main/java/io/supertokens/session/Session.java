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
import io.supertokens.auditlog.AuditLog;
import io.supertokens.pluginInterface.auditlog.AuditLogEvent;
import io.supertokens.ResourceDistributor;
import io.supertokens.config.Config;
import io.supertokens.config.CoreConfig;
import io.supertokens.exceptions.AccessTokenPayloadError;
import io.supertokens.exceptions.AccessTokenValidityOutOfRangeException;
import io.supertokens.exceptions.RefreshTokenReuseSubtype;
import io.supertokens.exceptions.TokenTheftDetectedException;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.output.Logging;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.StorageUtils;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantConfig;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.session.SessionStorage;
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
import io.supertokens.session.refreshToken.RefreshTokenKey;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
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

    // Validates the optional per-mint access token validity override (PLAN-002 decision 11, CDI >= 5.6).
    // Shorten-only: 0 < param <= the tenant's effective configured access_token_validity. Out-of-range is a hard
    // rejection (mapped to a 400 by the webserver), never a clamp. A null override (the common case) is a no-op.
    // access_token_validity is @NotConflictingInApp, so the tenant's value is the whole app's value.
    private static void validateAccessTokenValidityOverride(TenantIdentifier tenantIdentifier, Main main,
                                                            @Nullable Long accessTokenValidity)
            throws TenantOrAppNotFoundException, AccessTokenValidityOutOfRangeException {
        if (accessTokenValidity == null) {
            return;
        }
        long configuredValidity = Config.getConfig(tenantIdentifier, main).getAccessTokenValidityInMillis();
        if (accessTokenValidity <= 0 || accessTokenValidity > configuredValidity) {
            throw new AccessTokenValidityOutOfRangeException(
                    "accessTokenValidity must be greater than 0 and at most the configured access_token_validity ("
                            + configuredValidity + " ms)");
        }
    }

    @TestOnly
    public static SessionInformationHolder createNewSession(TenantIdentifier tenantIdentifier, Storage storage,
                                                            Main main,
                                                            @Nonnull String recipeUserId,
                                                            @Nonnull JsonObject userDataInJWT,
                                                            @Nonnull JsonObject userDataInDatabase)
            throws NoSuchAlgorithmException, StorageQueryException, InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException, SignatureException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException, UnauthorisedException,
            JWT.JWTException, UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError {
        try {
            return createNewSession(tenantIdentifier, storage, main, recipeUserId, userDataInJWT, userDataInDatabase,
                    false, AccessToken.getLatestVersion(), false);
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
                    ResourceDistributor.getAppForTesting(), storage, main,
                    recipeUserId, userDataInJWT, userDataInDatabase, false, AccessToken.getLatestVersion(), false);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // @TestOnly overload carrying a per-mint access token validity override (PLAN-002 decision 11); lets tests
    // exercise the shortened-validity mint and its range validation directly.
    @TestOnly
    public static SessionInformationHolder createNewSession(Main main,
                                                            @Nonnull String recipeUserId,
                                                            @Nonnull JsonObject userDataInJWT,
                                                            @Nonnull JsonObject userDataInDatabase,
                                                            @Nullable Long accessTokenValidity)
            throws NoSuchAlgorithmException, StorageQueryException, InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException, SignatureException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError, AccessTokenValidityOutOfRangeException {
        Storage storage = StorageLayer.getStorage(main);
        try {
            return createNewSession(
                    ResourceDistributor.getAppForTesting(), storage, main,
                    recipeUserId, userDataInJWT, userDataInDatabase, false, AccessToken.getLatestVersion(), false,
                    accessTokenValidity);
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
                    ResourceDistributor.getAppForTesting(), storage, main,
                    recipeUserId, userDataInJWT, userDataInDatabase, enableAntiCsrf, version, useStaticKey);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static SessionInformationHolder createNewSession(TenantIdentifier tenantIdentifier, Storage storage,
                                                            Main main, @Nonnull String recipeUserId,
                                                            @Nonnull JsonObject userDataInJWT,
                                                            @Nonnull JsonObject userDataInDatabase,
                                                            boolean enableAntiCsrf, AccessToken.VERSION version,
                                                            boolean useStaticKey)
            throws NoSuchAlgorithmException, StorageQueryException, InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException, SignatureException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException, AccessTokenPayloadError,
            UnsupportedJWTSigningAlgorithmException, TenantOrAppNotFoundException {
        try {
            return createNewSession(tenantIdentifier, storage, main, recipeUserId, userDataInJWT, userDataInDatabase,
                    enableAntiCsrf, version, useStaticKey, null);
        } catch (AccessTokenValidityOutOfRangeException e) {
            // Unreachable: a null override never fails range validation. Kept off this legacy signature so
            // existing callers are unaffected.
            throw new IllegalStateException(e);
        }
    }

    // accessTokenValidity (ms): the optional per-mint access token validity override (PLAN-002 decision 11,
    // CDI >= 5.6). null keeps the configured access_token_validity. When set it is validated shorten-only
    // (0 < param <= configured) - out-of-range throws AccessTokenValidityOutOfRangeException (the webserver
    // maps it to a 400, never a clamp). Nothing about the override is persisted.
    public static SessionInformationHolder createNewSession(TenantIdentifier tenantIdentifier, Storage storage,
                                                            Main main, @Nonnull String recipeUserId,
                                                            @Nonnull JsonObject userDataInJWT,
                                                            @Nonnull JsonObject userDataInDatabase,
                                                            boolean enableAntiCsrf, AccessToken.VERSION version,
                                                            boolean useStaticKey, @Nullable Long accessTokenValidity)
            throws NoSuchAlgorithmException, StorageQueryException, InvalidKeyException,
            InvalidKeySpecException, StorageTransactionLogicException, SignatureException, IllegalBlockSizeException,
            BadPaddingException, InvalidAlgorithmParameterException, NoSuchPaddingException, AccessTokenPayloadError,
            UnsupportedJWTSigningAlgorithmException, TenantOrAppNotFoundException,
            AccessTokenValidityOutOfRangeException {
        validateAccessTokenValidityOverride(tenantIdentifier, main, accessTokenValidity);
        String sessionHandle = UUID.randomUUID().toString();
        if (!tenantIdentifier.getTenantId().equals(TenantIdentifier.DEFAULT_TENANT_ID)) {
            sessionHandle += "_" + tenantIdentifier.getTenantId();
        }

        String primaryUserId = recipeUserId;

        if (storage.getType() == STORAGE_TYPE.SQL) {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping.getUserIdMapping(
                    tenantIdentifier.toAppIdentifier(), storage, recipeUserId, UserIdType.EXTERNAL);
            if (userIdMapping != null) {
                recipeUserId = userIdMapping.superTokensUserId;
            }

            primaryUserId = StorageUtils.getAuthRecipeStorage(storage)
                    .getPrimaryUserIdStrForUserId(tenantIdentifier.toAppIdentifier(), recipeUserId);
            if (primaryUserId == null) {
                primaryUserId = recipeUserId;
            }

            HashMap<String, String> userIdMappings = UserIdMapping.getUserIdMappingForSuperTokensUserIds(
                    tenantIdentifier.toAppIdentifier(), storage,
                    new ArrayList<>(Arrays.asList(primaryUserId, recipeUserId)));
            if (userIdMappings.containsKey(primaryUserId)) {
                primaryUserId = userIdMappings.get(primaryUserId);
            }
            if (userIdMappings.containsKey(recipeUserId)) {
                recipeUserId = userIdMappings.get(recipeUserId);
            }
        }

        String antiCsrfToken = enableAntiCsrf ? UUID.randomUUID().toString() : null;
        final TokenInfo refreshToken = RefreshToken.createNewRefreshToken(tenantIdentifier, main,
                sessionHandle, recipeUserId, null,
                antiCsrfToken);

        TokenInfo accessToken = AccessToken.createNewAccessToken(tenantIdentifier, main, sessionHandle,
                recipeUserId, primaryUserId, Utils.hashSHA256(refreshToken.token), null, userDataInJWT, antiCsrfToken,
                null, version, useStaticKey, true, accessTokenValidity); // fresh mint: apply jitter

        StorageUtils.getSessionStorage(storage)
                .createNewSession(tenantIdentifier, sessionHandle, recipeUserId,
                        Utils.hashSHA256(Utils.hashSHA256(refreshToken.token)), userDataInDatabase, refreshToken.expiry,
                        userDataInJWT, refreshToken.createdTime, useStaticKey);

        emitSessionCreatedEvent(main, storage, tenantIdentifier, recipeUserId, primaryUserId, sessionHandle);

        TokenInfo idRefreshToken = new TokenInfo(UUID.randomUUID().toString(), refreshToken.expiry,
                refreshToken.createdTime);
        return new SessionInformationHolder(
                new SessionInfo(sessionHandle, primaryUserId, recipeUserId, userDataInJWT,
                        tenantIdentifier.getTenantId()),
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
            return regenerateToken(ResourceDistributor.getAppForTesting().toAppIdentifier(), main, token, userDataInJWT);
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
        TenantIdentifier tenantIdentifier = accessToken.tenantIdentifier;
        Storage storage = StorageLayer.getStorage(accessToken.tenantIdentifier, main);
        io.supertokens.pluginInterface.session.SessionInfo sessionInfo = getSession(tenantIdentifier, storage,
                accessToken.sessionHandle);
        JsonObject newJWTUserPayload = userDataInJWT == null ? sessionInfo.userDataInJWT
                : userDataInJWT;
        updateSession(tenantIdentifier, storage, accessToken.sessionHandle, null, newJWTUserPayload,
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
                            tenantIdentifier.getTenantId()), null, null, null,
                    null);
        }

        TokenInfo newAccessToken = AccessToken.createNewAccessToken(tenantIdentifier, main,
                accessToken.sessionHandle, accessToken.recipeUserId, accessToken.primaryUserId,
                accessToken.refreshTokenHash1, accessToken.parentRefreshTokenHash1, newJWTUserPayload,
                accessToken.antiCsrfToken, accessToken.expiryTime, accessToken.version, sessionInfo.useStaticKey);

        return new SessionInformationHolder(
                new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId, accessToken.recipeUserId,
                        newJWTUserPayload,
                        tenantIdentifier.getTenantId()),
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
        TenantIdentifier tenantIdentifier = accessToken.tenantIdentifier;
        Storage storage = StorageLayer.getStorage(accessToken.tenantIdentifier, main);
        io.supertokens.pluginInterface.session.SessionInfo sessionInfo = getSession(tenantIdentifier, storage,
                accessToken.sessionHandle);
        JsonObject newJWTUserPayload = userDataInJWT == null ? sessionInfo.userDataInJWT
                : userDataInJWT;
        updateSessionBeforeCDI2_21(
                tenantIdentifier, storage,
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
                            tenantIdentifier.getTenantId()), null, null, null,
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
                        tenantIdentifier.getTenantId()),
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
        // No CDI version supplied -> legacy (CDI <= 5.4) verify semantics, byte-identical to before the
        // stateless-verification work. Existing tests keep exercising the old behaviour through this overload.
        return getSession(main, token, antiCsrfToken, enableAntiCsrf, doAntiCsrfCheck, checkDatabase, SemVer.v5_4);
    }

    @TestOnly
    public static SessionInformationHolder getSession(Main main, @Nonnull String token, @Nullable String antiCsrfToken,
                                                      boolean enableAntiCsrf, Boolean doAntiCsrfCheck,
                                                      boolean checkDatabase, SemVer cdiVersion)
            throws StorageQueryException,
            StorageTransactionLogicException, TryRefreshTokenException, UnauthorisedException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError {
        try {
            return getSession(ResourceDistributor.getAppForTesting().toAppIdentifier(), main, token, antiCsrfToken, enableAntiCsrf,
                    doAntiCsrfCheck, checkDatabase, cdiVersion);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // Legacy overload without an explicit CDI version -> CDI <= 5.4 verify semantics. Retained so callers and
    // tests that predate stateless verification keep compiling and behaving identically.
    public static SessionInformationHolder getSession(AppIdentifier appIdentifier, Main main, @Nonnull String token,
                                                      @Nullable String antiCsrfToken,
                                                      boolean enableAntiCsrf, Boolean doAntiCsrfCheck,
                                                      boolean checkDatabase) throws StorageQueryException,
            StorageTransactionLogicException, TryRefreshTokenException, UnauthorisedException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError, TenantOrAppNotFoundException {
        return getSession(appIdentifier, main, token, antiCsrfToken, enableAntiCsrf, doAntiCsrfCheck, checkDatabase,
                SemVer.v5_4);
    }

    // pass antiCsrfToken to disable csrf check for this request
    public static SessionInformationHolder getSession(AppIdentifier appIdentifier, Main main, @Nonnull String token,
                                                      @Nullable String antiCsrfToken,
                                                      boolean enableAntiCsrf, Boolean doAntiCsrfCheck,
                                                      boolean checkDatabase, SemVer cdiVersion)
            throws StorageQueryException,
            StorageTransactionLogicException, TryRefreshTokenException, UnauthorisedException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError, TenantOrAppNotFoundException {

        AccessTokenInfo accessToken = AccessToken.getInfoFromAccessToken(appIdentifier, main, token,
                doAntiCsrfCheck && enableAntiCsrf);
        TenantIdentifier tenantIdentifier = accessToken.tenantIdentifier;
        Storage storage = StorageLayer.getStorage(accessToken.tenantIdentifier, main);

        if (enableAntiCsrf && doAntiCsrfCheck
                && (antiCsrfToken == null || !antiCsrfToken.equals(accessToken.antiCsrfToken))) {
            throw new TryRefreshTokenException("anti-csrf check failed");
        }

        io.supertokens.pluginInterface.session.SessionInfo sessionInfoForBlacklisting = null;
        if (checkDatabase) {
            sessionInfoForBlacklisting = StorageUtils.getSessionStorage(storage)
                    .getSession(tenantIdentifier, accessToken.sessionHandle);
            if (sessionInfoForBlacklisting == null) {
                throw new UnauthorisedException("Either the session has ended or has been blacklisted");
            }
        }

        if (cdiVersion.greaterThanOrEqualTo(SemVer.v5_6)) {
            // ===== CDI >= 5.6: stateless verification (PLAN-002 unit 6, decisions 5-6). No DB write and no
            // token mint on any path; rotation now happens exclusively at refresh (unit 5). The
            // parentRefreshTokenHash1 == null precondition on the legacy early-return is dropped: any validly
            // signed, unexpired token short-circuits here. =====
            Boolean payloadUpdateAvailable = null;
            if (checkDatabase) {
                // sessionInfoForBlacklisting is non-null (existence checked above).
                // Fork rejection (decision 5 / option J): the access token's refresh lineage must still match
                // current or prev; otherwise its branch was rotated out -> force a refresh, where the real reuse
                // checks live. Not TOKEN_THEFT: a >= 2-generation-old still-unexpired in-flight token is a benign
                // false positive, so Unauthorised makes it cost one refresh round-trip and nothing more.
                String currentHash = sessionInfoForBlacklisting.refreshTokenHash2;
                String prevHash = sessionInfoForBlacklisting.prevRefreshTokenHash2;
                boolean lineageOk;
                try {
                    String tokenHash2 = Utils.hashSHA256(accessToken.refreshTokenHash1);
                    lineageOk = tokenHash2.equals(currentHash) || tokenHash2.equals(prevHash);
                    if (!lineageOk && accessToken.parentRefreshTokenHash1 != null) {
                        // Covers un-promoted-child tokens minted by old-CDI refreshes (parent still current/prev).
                        String parentHash2 = Utils.hashSHA256(accessToken.parentRefreshTokenHash1);
                        lineageOk = parentHash2.equals(currentHash) || parentHash2.equals(prevHash);
                    }
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
                if (!lineageOk) {
                    throw new UnauthorisedException("rotated-out token branch; refresh required");
                }
                // H3: report payload staleness as a read-only flag (computed from data already fetched); the
                // implicit verify-time token swap is gone. No token is minted here.
                payloadUpdateAvailable = !accessToken.userData.equals(sessionInfoForBlacklisting.userDataInJWT);
            }
            return new SessionInformationHolder(
                    new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId, accessToken.recipeUserId,
                            accessToken.userData, tenantIdentifier.getTenantId()),
                    null, null, null, null, payloadUpdateAvailable);
        }

        boolean JWTPayloadNeedsUpdating = sessionInfoForBlacklisting != null
                && !accessToken.userData.equals(sessionInfoForBlacklisting.userDataInJWT);
        if (accessToken.parentRefreshTokenHash1 == null && !JWTPayloadNeedsUpdating) {
            // this means that the refresh token associated with this access token is
            // already the parent - and JWT payload doesn't need to be updated.
            return new SessionInformationHolder(
                    new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId, accessToken.recipeUserId,
                            accessToken.userData,
                            tenantIdentifier.getTenantId()), null, null,
                    null, null);
        }

        ProcessState.getInstance(main).addState(ProcessState.PROCESS_STATE.GET_SESSION_NEW_TOKENS, null);

        if (StorageUtils.getSessionStorage(storage).getType() == STORAGE_TYPE.SQL) {
            SessionSQLStorage sessionStorage = (SessionSQLStorage) StorageUtils.getSessionStorage(storage);
            try {
                CoreConfig config = Config.getConfig(tenantIdentifier, main);
                warmSigningMaterial(tenantIdentifier, main);
                return sessionStorage.startTransaction(con -> {
                    try {

                        io.supertokens.pluginInterface.session.SessionInfo sessionInfo = sessionStorage
                                .getSessionInfo_Transaction(tenantIdentifier, con,
                                        accessToken.sessionHandle);

                        if (sessionInfo == null) {
                            sessionStorage.commitTransaction(con);
                            throw new UnauthorisedException("Session missing in db");
                        }

                        boolean promote = accessToken.parentRefreshTokenHash1 != null && sessionInfo.refreshTokenHash2
                                .equals(Utils.hashSHA256(accessToken.parentRefreshTokenHash1));
                        if (promote
                                || sessionInfo.refreshTokenHash2.equals(Utils.hashSHA256(accessToken.refreshTokenHash1))
                                || JWTPayloadNeedsUpdating) {
                            if (promote) {
                                // Dual-write invariant (PLAN-002 decision 10): a write that changes
                                // refresh_token_hash_2 also records the retired hash as prev and the rotation
                                // timestamp, so sessions migrate safely across CDI versions in both directions.
                                long now = System.currentTimeMillis();
                                sessionStorage.updateSessionInfo_Transaction(tenantIdentifier, con,
                                        accessToken.sessionHandle,
                                        Utils.hashSHA256(accessToken.refreshTokenHash1),
                                        sessionInfo.refreshTokenHash2, now,
                                        now + config.getRefreshTokenValidityInMillis(), sessionInfo.useStaticKey);
                            }
                            sessionStorage.commitTransaction(con);

                            TokenInfo newAccessToken;
                            if (AccessToken.getAccessTokenVersion(accessToken) == AccessToken.VERSION.V1) {
                                newAccessToken = AccessToken.createNewAccessTokenV1(tenantIdentifier,
                                        main,
                                        accessToken.sessionHandle,
                                        accessToken.recipeUserId, accessToken.refreshTokenHash1, null,
                                        sessionInfo.userDataInJWT, accessToken.antiCsrfToken);
                            } else {
                                newAccessToken = AccessToken.createNewAccessToken(tenantIdentifier, main,
                                        accessToken.sessionHandle,
                                        accessToken.recipeUserId, accessToken.primaryUserId,
                                        accessToken.refreshTokenHash1, null,
                                        sessionInfo.userDataInJWT, accessToken.antiCsrfToken, null, accessToken.version,
                                        sessionInfo.useStaticKey);
                            }

                            return new SessionInformationHolder(
                                    new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId,
                                            accessToken.recipeUserId,
                                            sessionInfo.userDataInJWT, tenantIdentifier.getTenantId()),
                                    new TokenInfo(newAccessToken.token, newAccessToken.expiry,
                                            newAccessToken.createdTime),
                                    null, null, null);
                        }

                        sessionStorage.commitTransaction(con);
                        return new SessionInformationHolder(
                                new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId,
                                        accessToken.recipeUserId, accessToken.userData,
                                        tenantIdentifier.getTenantId()),
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
        } else if (StorageUtils.getSessionStorage(storage).getType() ==
                STORAGE_TYPE.NOSQL_1) {
            SessionNoSQLStorage_1 sessionStorage = (SessionNoSQLStorage_1) StorageUtils.getSessionStorage(storage);
            while (true) {
                try {

                    io.supertokens.pluginInterface.session.noSqlStorage.SessionInfoWithLastUpdated sessionInfo =
                            sessionStorage
                                    .getSessionInfo_Transaction(accessToken.sessionHandle);

                    if (sessionInfo == null) {
                        throw new UnauthorisedException("Session missing in db");
                    }

                    boolean promote = accessToken.parentRefreshTokenHash1 != null && sessionInfo.refreshTokenHash2
                            .equals(Utils.hashSHA256(accessToken.parentRefreshTokenHash1));
                    if (promote || sessionInfo.refreshTokenHash2.equals(Utils.hashSHA256(accessToken.refreshTokenHash1))
                            || JWTPayloadNeedsUpdating) {
                        if (promote) {
                            boolean success = sessionStorage.updateSessionInfo_Transaction(accessToken.sessionHandle,
                                    Utils.hashSHA256(accessToken.refreshTokenHash1),
                                    System.currentTimeMillis() + Config.getConfig(tenantIdentifier, main)
                                            .getRefreshTokenValidityInMillis(),
                                    sessionInfo.lastUpdatedSign, sessionInfo.useStaticKey);
                            if (!success) {
                                continue;
                            }
                        }

                        TokenInfo newAccessToken;
                        if (accessToken.version == AccessToken.VERSION.V1) {
                            newAccessToken = AccessToken.createNewAccessTokenV1(tenantIdentifier, main,
                                    accessToken.sessionHandle,
                                    accessToken.recipeUserId, accessToken.refreshTokenHash1, null,
                                    sessionInfo.userDataInJWT,
                                    accessToken.antiCsrfToken);
                        } else {
                            newAccessToken = AccessToken.createNewAccessToken(tenantIdentifier, main,
                                    accessToken.sessionHandle,
                                    accessToken.recipeUserId, accessToken.primaryUserId, accessToken.refreshTokenHash1,
                                    null, sessionInfo.userDataInJWT,
                                    accessToken.antiCsrfToken, null, accessToken.version, sessionInfo.useStaticKey);
                        }

                        return new SessionInformationHolder(
                                new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId,
                                        accessToken.recipeUserId,
                                        sessionInfo.userDataInJWT, tenantIdentifier.getTenantId()),
                                new TokenInfo(newAccessToken.token, newAccessToken.expiry, newAccessToken.createdTime),
                                null, null, null);
                    }

                    return new SessionInformationHolder(
                            new SessionInfo(accessToken.sessionHandle, accessToken.primaryUserId,
                                    accessToken.recipeUserId, accessToken.userData,
                                    tenantIdentifier.getTenantId()),
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
        // No CDI version supplied -> legacy (CDI <= 5.4) refresh semantics, byte-identical to before the
        // refresh-time rotation work. Existing tests keep exercising the old behaviour through this overload.
        return refreshSession(main, refreshToken, antiCsrfToken, enableAntiCsrf, accessTokenVersion, SemVer.v5_4);
    }

    @TestOnly
    public static SessionInformationHolder refreshSession(Main main, @Nonnull String refreshToken,
                                                          @Nullable String antiCsrfToken, boolean enableAntiCsrf,
                                                          AccessToken.VERSION accessTokenVersion, SemVer cdiVersion)
            throws StorageTransactionLogicException,
            UnauthorisedException, StorageQueryException, TokenTheftDetectedException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError {
        try {
            return refreshSession(ResourceDistributor.getAppForTesting().toAppIdentifier(), main, refreshToken, antiCsrfToken,
                    enableAntiCsrf, accessTokenVersion, null, cdiVersion);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // @TestOnly overload carrying a per-mint access token validity override (PLAN-002 decision 11); lets tests
    // drive the CDI >= 5.6 refresh mint with a shortened validity without going over HTTP (CDI 5.6 is not yet
    // advertised - see the PR description).
    @TestOnly
    public static SessionInformationHolder refreshSession(Main main, @Nonnull String refreshToken,
                                                          @Nullable String antiCsrfToken, boolean enableAntiCsrf,
                                                          AccessToken.VERSION accessTokenVersion, SemVer cdiVersion,
                                                          @Nullable Long accessTokenValidity)
            throws StorageTransactionLogicException,
            UnauthorisedException, StorageQueryException, TokenTheftDetectedException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError,
            AccessTokenValidityOutOfRangeException {
        try {
            return refreshSession(ResourceDistributor.getAppForTesting().toAppIdentifier(), main, refreshToken,
                    antiCsrfToken, enableAntiCsrf, accessTokenVersion, null, cdiVersion, accessTokenValidity);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    // Legacy overload without an explicit CDI version -> CDI <= 5.4 refresh semantics. Retained so callers
    // and tests that predate refresh-time rotation keep compiling and behaving identically.
    public static SessionInformationHolder refreshSession(AppIdentifier appIdentifier, Main main,
                                                          @Nonnull String refreshToken,
                                                          @Nullable String antiCsrfToken, boolean enableAntiCsrf,
                                                          AccessToken.VERSION accessTokenVersion,
                                                          Boolean shouldUseStaticKey)
            throws StorageTransactionLogicException,
            UnauthorisedException, StorageQueryException, TokenTheftDetectedException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError, TenantOrAppNotFoundException {
        return refreshSession(appIdentifier, main, refreshToken, antiCsrfToken, enableAntiCsrf, accessTokenVersion,
                shouldUseStaticKey, SemVer.v5_4);
    }

    // Overload without a per-mint validity override -> configured access_token_validity, unchanged behaviour.
    public static SessionInformationHolder refreshSession(AppIdentifier appIdentifier, Main main,
                                                          @Nonnull String refreshToken,
                                                          @Nullable String antiCsrfToken, boolean enableAntiCsrf,
                                                          AccessToken.VERSION accessTokenVersion,
                                                          Boolean shouldUseStaticKey, SemVer cdiVersion)
            throws StorageTransactionLogicException,
            UnauthorisedException, StorageQueryException, TokenTheftDetectedException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError, TenantOrAppNotFoundException {
        try {
            return refreshSession(appIdentifier, main, refreshToken, antiCsrfToken, enableAntiCsrf, accessTokenVersion,
                    shouldUseStaticKey, cdiVersion, null);
        } catch (AccessTokenValidityOutOfRangeException e) {
            // Unreachable: a null override never fails range validation.
            throw new IllegalStateException(e);
        }
    }

    // accessTokenValidity (ms): the optional per-mint access token validity override (PLAN-002 decision 11,
    // CDI >= 5.6). null keeps the configured access_token_validity. When set it is validated shorten-only
    // (0 < param <= configured) and applies only to the CDI >= 5.6 rotation mint (refresh cases 1 and 3); it is
    // never persisted, and the refresh token validity/expiry is not overridable.
    public static SessionInformationHolder refreshSession(AppIdentifier appIdentifier, Main main,
                                                          @Nonnull String refreshToken,
                                                          @Nullable String antiCsrfToken, boolean enableAntiCsrf,
                                                          AccessToken.VERSION accessTokenVersion,
                                                          Boolean shouldUseStaticKey, SemVer cdiVersion,
                                                          @Nullable Long accessTokenValidity)
            throws StorageTransactionLogicException,
            UnauthorisedException, StorageQueryException, TokenTheftDetectedException,
            UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError, TenantOrAppNotFoundException,
            AccessTokenValidityOutOfRangeException {
        RefreshToken.RefreshTokenInfo refreshTokenInfo = RefreshToken.getInfoFromRefreshToken(appIdentifier, main,
                refreshToken);

        if (enableAntiCsrf && refreshTokenInfo.antiCsrfToken != null) {
            // anti csrf is enabled, and the refresh token contains an anticsrf token (it's not the older version)
            if (!refreshTokenInfo.antiCsrfToken.equals(antiCsrfToken)) {
                throw new UnauthorisedException("Anti CSRF token missing, or not matching");
            }
        }

        TenantIdentifier tenantIdentifier = refreshTokenInfo.tenantIdentifier;
        Storage storage = StorageLayer.getStorage(refreshTokenInfo.tenantIdentifier, main);
        validateAccessTokenValidityOverride(tenantIdentifier, main, accessTokenValidity);
        return refreshSessionHelper(
                tenantIdentifier, storage, main, refreshToken, refreshTokenInfo, enableAntiCsrf, accessTokenVersion,
                shouldUseStaticKey, cdiVersion, accessTokenValidity);
    }

    // True when the presented refresh token is a child (via its token-internal parent hash) of the refresh
    // token whose double-hash is parentHash2. Mirrors the legacy Case B lineage test; used by the CDI >= 5.6
    // flow for case 2 (child of current -> promote+rotate) and ORPHANED_BRANCH classification (child of prev).
    private static boolean refreshTokenChildMatches(RefreshToken.RefreshTokenInfo info, String parentHash2)
            throws NoSuchAlgorithmException {
        return (info.type == RefreshToken.TYPE.FREE && info.parentRefreshTokenHash2 != null
                && info.parentRefreshTokenHash2.equals(parentHash2))
                || (info.parentRefreshTokenHash1 != null
                && Utils.hashSHA256(info.parentRefreshTokenHash1).equals(parentHash2));
    }

    // Builds the refresh response for a CDI >= 5.6 rotation (cases 1/2/3): a fresh access token whose
    // parentRefreshTokenHash1 is null (there is exactly one live token and no lineage-acceptance rule on
    // CDI 5.6 - decision 3), alongside the just-minted refresh token.
    private static SessionInformationHolder buildRefreshedSession(TenantIdentifier tenantIdentifier, Main main,
            String sessionHandle, io.supertokens.pluginInterface.session.SessionInfo sessionInfo,
            TokenInfo newRefreshToken, String antiCsrfToken, AccessToken.VERSION accessTokenVersion,
            boolean useStaticKey, @Nullable Long accessTokenValidity)
            throws StorageQueryException, StorageTransactionLogicException, InvalidKeyException,
            NoSuchAlgorithmException, TenantOrAppNotFoundException, InvalidKeySpecException, SignatureException,
            AccessTokenPayloadError, UnsupportedJWTSigningAlgorithmException {
        TokenInfo newAccessToken = AccessToken.createNewAccessToken(tenantIdentifier, main, sessionHandle,
                sessionInfo.recipeUserId, sessionInfo.userId, Utils.hashSHA256(newRefreshToken.token),
                null, sessionInfo.userDataInJWT, antiCsrfToken, null, accessTokenVersion, useStaticKey,
                true, accessTokenValidity); // fresh mint: apply access_token_validity_jitter
        TokenInfo idRefreshToken = new TokenInfo(UUID.randomUUID().toString(), newRefreshToken.expiry,
                newRefreshToken.createdTime);
        return new SessionInformationHolder(
                new SessionInfo(sessionHandle, sessionInfo.userId, sessionInfo.recipeUserId,
                        sessionInfo.userDataInJWT, tenantIdentifier.getTenantId()),
                newAccessToken, newRefreshToken, idRefreshToken, antiCsrfToken);
    }

    // Warms the signing-material caches while this thread holds no DB connection. Token minting inside the
    // transaction lambdas of getSession/refreshSessionHelper is then served from cache: any key-cache refresh
    // that is due (cold start, dynamic-key rotation window) does its DB round trip here instead of checking
    // out a second connection while the transaction holds the first - which deadlocks the pool once pool-size
    // requests do it concurrently.
    private static void warmSigningMaterial(TenantIdentifier tenantIdentifier, Main main)
            throws StorageQueryException, StorageTransactionLogicException, TenantOrAppNotFoundException,
            UnsupportedJWTSigningAlgorithmException {
        SigningKeys.getInstance(tenantIdentifier.toAppIdentifier(), main).getAllKeys();
        RefreshTokenKey.getInstance(tenantIdentifier.toAppIdentifier(), main).getKey();
    }

    private static SessionInformationHolder refreshSessionHelper(
            TenantIdentifier tenantIdentifier, Storage storage, Main main, String refreshToken,
            RefreshToken.RefreshTokenInfo refreshTokenInfo,
            boolean enableAntiCsrf,
            AccessToken.VERSION accessTokenVersion, Boolean shouldUseStaticKey, SemVer cdiVersion,
            @Nullable Long accessTokenValidity)
            throws StorageTransactionLogicException, UnauthorisedException, StorageQueryException,
            TokenTheftDetectedException, UnsupportedJWTSigningAlgorithmException, AccessTokenPayloadError,
            TenantOrAppNotFoundException {
        ////////////////////////////////////////// SQL/////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////
        if (StorageUtils.getSessionStorage(storage).getType() == STORAGE_TYPE.SQL) {
            SessionSQLStorage sessionStorage = (SessionSQLStorage) StorageUtils.getSessionStorage(storage);
            try {
                CoreConfig config = Config.getConfig(tenantIdentifier, main);
                warmSigningMaterial(tenantIdentifier, main);
                SessionInformationHolder result = sessionStorage.startTransaction(con -> {
                    try {
                        String sessionHandle = refreshTokenInfo.sessionHandle;
                        io.supertokens.pluginInterface.session.SessionInfo sessionInfo = sessionStorage
                                .getSessionInfo_Transaction(tenantIdentifier, con, sessionHandle);

                        if (sessionInfo == null || sessionInfo.expiry < System.currentTimeMillis()) {
                            sessionStorage.commitTransaction(con);
                            throw new UnauthorisedException("Session missing in db or has expired");
                        }
                        boolean useStaticKey =
                                shouldUseStaticKey != null ? shouldUseStaticKey : sessionInfo.useStaticKey;

                        if (cdiVersion.greaterThanOrEqualTo(SemVer.v5_6)) {
                            // ===== CDI >= 5.6: refresh-time rotation with grace window (PLAN-002 cases 1-4) =====
                            long now = System.currentTimeMillis();
                            String presentedHash2 = Utils.hashSHA256(Utils.hashSHA256(refreshToken));
                            String currentHash = sessionInfo.refreshTokenHash2;
                            String prevHash = sessionInfo.prevRefreshTokenHash2;
                            Long rotatedAt = sessionInfo.refreshTokenRotatedAt;
                            long graceMs = config.getRefreshTokenRotationGracePeriodInMillis();

                            // Case 1 (presented == current) and case 2 (presented is an un-promoted child of
                            // current, minted by an old-CDI refresh) both rotate; the token being retired is the
                            // presented one, so prev := presentedHash2 in both.
                            if (presentedHash2.equals(currentHash)
                                    || refreshTokenChildMatches(refreshTokenInfo, currentHash)) {
                                String antiCsrfToken = enableAntiCsrf ? UUID.randomUUID().toString() : null;
                                final TokenInfo newRefreshToken = RefreshToken.createNewRefreshToken(
                                        tenantIdentifier, main, sessionHandle, sessionInfo.recipeUserId,
                                        Utils.hashSHA256(refreshToken), antiCsrfToken);
                                sessionStorage.updateSessionInfo_Transaction(tenantIdentifier, con, sessionHandle,
                                        Utils.hashSHA256(Utils.hashSHA256(newRefreshToken.token)),
                                        presentedHash2, now, now + config.getRefreshTokenValidityInMillis(),
                                        useStaticKey);
                                sessionStorage.commitTransaction(con);
                                return buildRefreshedSession(tenantIdentifier, main, sessionHandle, sessionInfo,
                                        newRefreshToken, antiCsrfToken, accessTokenVersion, useStaticKey,
                                        accessTokenValidity);
                            }

                            // Case 3: presented == prev within the grace window -> re-rotate. Single write of
                            // current only; prev, rotated_at and expiry stay put so repeated retries with the
                            // window root keep recovering for the whole window. The child displaced here is dead.
                            if (prevHash != null && rotatedAt != null && presentedHash2.equals(prevHash)
                                    && now <= rotatedAt + graceMs) {
                                String antiCsrfToken = enableAntiCsrf ? UUID.randomUUID().toString() : null;
                                final TokenInfo newRefreshToken = RefreshToken.createNewRefreshToken(
                                        tenantIdentifier, main, sessionHandle, sessionInfo.recipeUserId,
                                        Utils.hashSHA256(refreshToken), antiCsrfToken);
                                sessionStorage.updateSessionInfo_Transaction(tenantIdentifier, con, sessionHandle,
                                        Utils.hashSHA256(Utils.hashSHA256(newRefreshToken.token)),
                                        prevHash, rotatedAt, sessionInfo.expiry, useStaticKey);
                                sessionStorage.commitTransaction(con);
                                ProcessState.getInstance(main).addState(
                                        ProcessState.PROCESS_STATE.REFRESH_TOKEN_GRACE_PERIOD_HIT, null);
                                Logging.debug(main, tenantIdentifier,
                                        "Refresh token rotation grace-window re-rotation for session "
                                                + sessionHandle + " (" + (now - rotatedAt) + "ms since rotation)");
                                return buildRefreshedSession(tenantIdentifier, main, sessionHandle, sessionInfo,
                                        newRefreshToken, antiCsrfToken, accessTokenVersion, useStaticKey,
                                        accessTokenValidity);
                            }

                            // Case 4: reuse. Classify, log telemetry, and report per recent_token_reuse_behaviour.
                            // The session is revoked in the outer catch for every subtype (decision 4).
                            RefreshTokenReuseSubtype subtype;
                            if (prevHash != null && presentedHash2.equals(prevHash)) {
                                subtype = RefreshTokenReuseSubtype.RECENT_PREV;   // matched prev, window expired
                            } else if (prevHash != null && refreshTokenChildMatches(refreshTokenInfo, prevHash)) {
                                subtype = RefreshTokenReuseSubtype.ORPHANED_BRANCH; // displaced by a grace re-rotation
                            } else {
                                subtype = RefreshTokenReuseSubtype.STALE_LINEAGE;
                            }
                            sessionStorage.commitTransaction(con);
                            ProcessState.getInstance(main).addState(
                                    ProcessState.PROCESS_STATE.REFRESH_TOKEN_REUSE_DETECTED, null);
                            Logging.debug(main, tenantIdentifier, "Refresh token reuse (" + subtype
                                    + ") detected for session " + sessionHandle
                                    + (rotatedAt != null ? " (" + (now - rotatedAt) + "ms since rotation)" : ""));
                            // STALE_LINEAGE is always theft; RECENT_PREV / ORPHANED_BRANCH follow the config.
                            if (subtype == RefreshTokenReuseSubtype.STALE_LINEAGE
                                    || !"UNAUTHORISED".equals(config.getRecentTokenReuseBehaviour())) {
                                throw new TokenTheftDetectedException(sessionHandle, sessionInfo.recipeUserId,
                                        sessionInfo.userId, subtype);
                            }
                            throw new UnauthorisedException("refresh token reuse detected", subtype);
                        }

                        // ===== Legacy (CDI <= 5.4): byte-identical behaviour, plus the dual-write invariant
                        // (decision 10) so that any write which changes refresh_token_hash_2 also records prev
                        // and rotated_at, keeping sessions safe to cross CDI versions mid-lifetime. =====
                        if (sessionInfo.refreshTokenHash2.equals(Utils.hashSHA256(Utils.hashSHA256(refreshToken)))) {
                            if (useStaticKey != sessionInfo.useStaticKey) {
                                // We do not update anything except the static key status -> refresh_token_hash_2 is
                                // unchanged, so the existing rotation state is preserved (not a rotation).
                                sessionStorage.updateSessionInfo_Transaction(tenantIdentifier, con, sessionHandle,
                                        sessionInfo.refreshTokenHash2, sessionInfo.prevRefreshTokenHash2,
                                        sessionInfo.refreshTokenRotatedAt, sessionInfo.expiry,
                                        useStaticKey);
                            }

                            // at this point, the input refresh token is the parent one.
                            sessionStorage.commitTransaction(con);

                            String antiCsrfToken = enableAntiCsrf ? UUID.randomUUID().toString() : null;
                            final TokenInfo newRefreshToken = RefreshToken.createNewRefreshToken(
                                    tenantIdentifier, main, sessionHandle,
                                    sessionInfo.recipeUserId, Utils.hashSHA256(refreshToken), antiCsrfToken);

                            TokenInfo newAccessToken = AccessToken.createNewAccessToken(tenantIdentifier,
                                    main, sessionHandle,
                                    sessionInfo.recipeUserId, sessionInfo.userId,
                                    Utils.hashSHA256(newRefreshToken.token),
                                    Utils.hashSHA256(refreshToken), sessionInfo.userDataInJWT, antiCsrfToken,
                                    null, accessTokenVersion,
                                    useStaticKey, true, null); // fresh mint: apply access_token_validity_jitter

                            TokenInfo idRefreshToken = new TokenInfo(UUID.randomUUID().toString(),
                                    newRefreshToken.expiry, newRefreshToken.createdTime);

                            return new SessionInformationHolder(
                                    new SessionInfo(sessionHandle, sessionInfo.userId, sessionInfo.recipeUserId,
                                            sessionInfo.userDataInJWT,
                                            tenantIdentifier.getTenantId()),
                                    newAccessToken, newRefreshToken, idRefreshToken, antiCsrfToken);
                        }

                        if ((refreshTokenInfo.type == RefreshToken.TYPE.FREE
                                && refreshTokenInfo.parentRefreshTokenHash2 != null
                                && refreshTokenInfo.parentRefreshTokenHash2.equals(sessionInfo.refreshTokenHash2))
                                || (refreshTokenInfo.parentRefreshTokenHash1 != null
                                && Utils.hashSHA256(refreshTokenInfo.parentRefreshTokenHash1)
                                .equals(sessionInfo.refreshTokenHash2))) {
                            // Case B promote: refresh_token_hash_2 changes, so record prev := old current + now.
                            long nowLegacy = System.currentTimeMillis();
                            sessionStorage.updateSessionInfo_Transaction(tenantIdentifier, con, sessionHandle,
                                    Utils.hashSHA256(Utils.hashSHA256(refreshToken)),
                                    sessionInfo.refreshTokenHash2, nowLegacy,
                                    nowLegacy + config.getRefreshTokenValidityInMillis(),
                                    useStaticKey);

                            sessionStorage.commitTransaction(con);

                            // Case-B promoted the presented token; null tells the caller to retry AFTER this
                            // transaction has returned its connection to the pool. Recursing here (the previous
                            // shape) checked out a second connection while still holding this one, deadlocking
                            // the pool at pool-size concurrent Case-B refreshes.
                            return null;
                        }

                        sessionStorage.commitTransaction(con);

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
                if (result == null) {
                    // Case-B promote: retry now that the transaction above has released its connection, so a
                    // refresh never holds more than one connection at a time. The re-read sees the promoted
                    // state and takes the plain rotation path.
                    return refreshSessionHelper(tenantIdentifier, storage, main, refreshToken, refreshTokenInfo,
                            enableAntiCsrf, accessTokenVersion, shouldUseStaticKey, cdiVersion,
                            accessTokenValidity);
                }
                return result;
            } catch (StorageTransactionLogicException e) {
                if (e.actualException instanceof UnauthorisedException) {
                    UnauthorisedException ue = (UnauthorisedException) e.actualException;
                    if (ue.reuseSubtype != null) {
                        // CDI >= 5.6 recent-reuse reported as Unauthorised: the session is still revoked
                        // (decision 4 - the config alters reporting only, never enforcement).
                        revokeSessionUsingSessionHandles(tenantIdentifier, storage,
                                new String[]{refreshTokenInfo.sessionHandle});
                    }
                    throw ue;
                } else if (e.actualException instanceof TokenTheftDetectedException) {
                    TokenTheftDetectedException te = (TokenTheftDetectedException) e.actualException;
                    if (te.reuseSubtype != null) {
                        // CDI >= 5.6 reuse: revoke server-side so revocation no longer depends on the SDK acting
                        // on the theft response. Legacy (CDI <= 5.4) theft has a null subtype and is untouched.
                        revokeSessionUsingSessionHandles(tenantIdentifier, storage,
                                new String[]{refreshTokenInfo.sessionHandle});
                    }
                    throw te;
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
        } else if (StorageUtils.getSessionStorage(storage).getType() ==
                STORAGE_TYPE.NOSQL_1) {
            SessionNoSQLStorage_1 sessionStorage = (SessionNoSQLStorage_1) StorageUtils.getSessionStorage(storage);
            while (true) {
                try {
                    String sessionHandle = refreshTokenInfo.sessionHandle;
                    io.supertokens.pluginInterface.session.noSqlStorage.SessionInfoWithLastUpdated sessionInfo =
                            sessionStorage
                                    .getSessionInfo_Transaction(sessionHandle);

                    if (sessionInfo == null || sessionInfo.expiry < System.currentTimeMillis()) {
                        throw new UnauthorisedException("Session missing in db or has expired");
                    }

                    boolean useStaticKey = shouldUseStaticKey != null ? shouldUseStaticKey : sessionInfo.useStaticKey;

                    if (sessionInfo.refreshTokenHash2.equals(Utils.hashSHA256(Utils.hashSHA256(refreshToken)))) {
                        if (sessionInfo.useStaticKey != useStaticKey) {
                            // We do not update anything except the static key status
                            boolean success = sessionStorage.updateSessionInfo_Transaction(sessionHandle,
                                    sessionInfo.refreshTokenHash2, sessionInfo.expiry,
                                    sessionInfo.lastUpdatedSign, useStaticKey);
                            if (!success) {
                                continue;
                            }
                        }
                        // at this point, the input refresh token is the parent one.
                        String antiCsrfToken = enableAntiCsrf ? UUID.randomUUID().toString() : null;

                        final TokenInfo newRefreshToken = RefreshToken.createNewRefreshToken(
                                tenantIdentifier, main, sessionHandle,
                                sessionInfo.recipeUserId, Utils.hashSHA256(refreshToken), antiCsrfToken);
                        TokenInfo newAccessToken = AccessToken.createNewAccessToken(tenantIdentifier, main,
                                sessionHandle,
                                sessionInfo.recipeUserId, sessionInfo.userId, Utils.hashSHA256(newRefreshToken.token),
                                Utils.hashSHA256(refreshToken), sessionInfo.userDataInJWT, antiCsrfToken,
                                null, accessTokenVersion,
                                useStaticKey, true, null); // fresh mint: apply access_token_validity_jitter

                        TokenInfo idRefreshToken = new TokenInfo(UUID.randomUUID().toString(), newRefreshToken.expiry,
                                newRefreshToken.createdTime);

                        return new SessionInformationHolder(
                                new SessionInfo(sessionHandle, sessionInfo.userId, sessionInfo.recipeUserId,
                                        sessionInfo.userDataInJWT,
                                        tenantIdentifier.getTenantId()),
                                newAccessToken, newRefreshToken, idRefreshToken, antiCsrfToken);
                    }

                    if ((refreshTokenInfo.type == RefreshToken.TYPE.FREE
                            && refreshTokenInfo.parentRefreshTokenHash2 != null
                            && refreshTokenInfo.parentRefreshTokenHash2.equals(sessionInfo.refreshTokenHash2))
                            || (refreshTokenInfo.parentRefreshTokenHash1 != null
                            && Utils.hashSHA256(refreshTokenInfo.parentRefreshTokenHash1)
                            .equals(sessionInfo.refreshTokenHash2))) {
                        boolean success = sessionStorage.updateSessionInfo_Transaction(sessionHandle,
                                Utils.hashSHA256(Utils.hashSHA256(refreshToken)),
                                System.currentTimeMillis() +
                                        Config.getConfig(tenantIdentifier, main).getRefreshTokenValidityInMillis(),
                                sessionInfo.lastUpdatedSign, useStaticKey);
                        if (!success) {
                            continue;
                        }
                        return refreshSessionHelper(
                                tenantIdentifier, storage, main, refreshToken, refreshTokenInfo,
                                enableAntiCsrf, accessTokenVersion, shouldUseStaticKey, cdiVersion,
                                accessTokenValidity);
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
                ResourceDistributor.getAppForTesting().toAppIdentifier(), storage,
                sessionHandles);
    }

    public static String[] revokeSessionUsingSessionHandles(Main main,
                                                            AppIdentifier appIdentifier,
                                                            Storage storage,
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

            TenantIdentifier tenantIdentifier = new TenantIdentifier(appIdentifier.getConnectionUriDomain(),
                    appIdentifier.getAppId(), tenantId);
            Storage tenantStorage = null;
            try {
                tenantStorage = StorageLayer.getStorage(tenantIdentifier, main);
            } catch (TenantOrAppNotFoundException e) {
                // ignore as this can happen if the tenant has been deleted after fetching the sessionHandles
                continue;
            }

            String[] sessionHandlesRevokedForTenant = revokeSessionUsingSessionHandles(tenantIdentifier, tenantStorage,
                    sessionHandlesForTenant);
            revokedSessionHandles.addAll(Arrays.asList(sessionHandlesRevokedForTenant));
        }

        return revokedSessionHandles.toArray(new String[0]);
    }

    private static String[] revokeSessionUsingSessionHandles(TenantIdentifier tenantIdentifier,
                                                             Storage storage,
                                                             String[] sessionHandles)
            throws StorageQueryException {
        Set<String> validHandles = new HashSet<>();

        if (sessionHandles.length > 1) {
            // we need to identify which sessionHandles are valid if there are more than one sessionHandles to revoke
            // if there is only one sessionHandle to revoke, we would know if it was valid by the number of revoked
            // sessions
            for (String sessionHandle : sessionHandles) {
                if (((SessionStorage) storage)
                        .getSession(tenantIdentifier, sessionHandle) != null) {
                    validHandles.add(sessionHandle);
                }
            }
        }

        int numberOfSessionsRevoked = ((SessionStorage) storage)
                .deleteSession(tenantIdentifier, sessionHandles);

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
                if (((SessionStorage) storage)
                        .getSession(tenantIdentifier, sessionHandle) == null) {
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
                ResourceDistributor.getAppForTesting().toAppIdentifier(), storage, userId, true);
    }

    public static String[] revokeAllSessionsForUser(Main main, AppIdentifier appIdentifier,
                                                    Storage storage, String userId,
                                                    boolean revokeSessionsForLinkedAccounts)
            throws StorageQueryException {
        String[] sessionHandles = getAllNonExpiredSessionHandlesForUser(main, appIdentifier, storage, userId,
                revokeSessionsForLinkedAccounts);
        return revokeSessionUsingSessionHandles(main, appIdentifier, storage, sessionHandles);
    }

    public static String[] revokeAllSessionsForUser(Main main, TenantIdentifier tenantIdentifier, Storage storage,
                                                    String userId, boolean revokeSessionsForLinkedAccounts)
            throws StorageQueryException {
        String[] sessionHandles = getAllNonExpiredSessionHandlesForUser(tenantIdentifier, storage, userId,
                revokeSessionsForLinkedAccounts);
        return revokeSessionUsingSessionHandles(main, tenantIdentifier.toAppIdentifier(), storage,
                sessionHandles);
    }

    @TestOnly
    public static String[] getAllNonExpiredSessionHandlesForUser(Main main, String userId)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        return getAllNonExpiredSessionHandlesForUser(main,
                ResourceDistributor.getAppForTesting().toAppIdentifier(), storage, userId, true);
    }

    public static String[] getAllNonExpiredSessionHandlesForUser(
            Main main, AppIdentifier appIdentifier, Storage storage, String userId,
            boolean fetchSessionsForAllLinkedAccounts)
            throws StorageQueryException {
        TenantConfig[] tenants = Multitenancy.getAllTenantsForApp(
                appIdentifier, main);

        List<String> sessionHandles = new ArrayList<>();

        Set<String> userIds = new HashSet<>();
        userIds.add(userId);
        if (fetchSessionsForAllLinkedAccounts) {
            if (storage.getType().equals(STORAGE_TYPE.SQL)) {
                AuthRecipeUserInfo primaryUser = ((AuthRecipeStorage) storage)
                        .getPrimaryUserById(appIdentifier, userId);
                if (primaryUser != null) {
                    for (LoginMethod lM : primaryUser.loginMethods) {
                        userIds.add(lM.getSupertokensUserId());
                    }
                }
            }
        }

        for (String currUserId : userIds) {
            for (TenantConfig tenant : tenants) {
                try {
                    sessionHandles.addAll(Arrays.asList(getAllNonExpiredSessionHandlesForUser(
                            tenant.tenantIdentifier, StorageLayer.getStorage(tenant.tenantIdentifier, main),
                            currUserId, false)));

                } catch (TenantOrAppNotFoundException e) {
                    // this might happen when a tenant was deleted after the tenant list was fetched
                    // it is okay to exclude that tenant in the results here
                }
            }
        }

        return sessionHandles.toArray(new String[0]);
    }

    public static String[] getAllNonExpiredSessionHandlesForUser(
            TenantIdentifier tenantIdentifier, Storage storage, String userId,
            boolean fetchSessionsForAllLinkedAccounts)
            throws StorageQueryException {
        Set<String> userIds = new HashSet<>();
        userIds.add(userId);
        if (fetchSessionsForAllLinkedAccounts) {
            AuthRecipeUserInfo primaryUser = ((AuthRecipeStorage) storage)
                    .getPrimaryUserById(tenantIdentifier.toAppIdentifier(), userId);
            if (primaryUser != null) {
                for (LoginMethod lM : primaryUser.loginMethods) {
                    userIds.add(lM.getSupertokensUserId());
                }
            }
        }
        List<String> sessionHandles = new ArrayList<>();
        for (String currUserId : userIds) {
            sessionHandles.addAll(List.of(((SessionStorage) storage)
                    .getAllNonExpiredSessionHandlesForUser(tenantIdentifier, currUserId)));
        }
        return sessionHandles.toArray(new String[0]);
    }

    @TestOnly
    public static JsonObject getSessionData(Main main, String sessionHandle)
            throws StorageQueryException, UnauthorisedException {
        Storage storage = StorageLayer.getStorage(main);
        return getSessionData(
                ResourceDistributor.getAppForTesting(), storage,
                sessionHandle);
    }

    @Deprecated
    public static JsonObject getSessionData(TenantIdentifier tenantIdentifier, Storage storage,
                                            String sessionHandle)
            throws StorageQueryException, UnauthorisedException {
        io.supertokens.pluginInterface.session.SessionInfo session = StorageUtils.getSessionStorage(storage)
                .getSession(tenantIdentifier, sessionHandle);
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
                ResourceDistributor.getAppForTesting(), storage,
                sessionHandle);
    }

    @Deprecated
    public static JsonObject getJWTData(TenantIdentifier tenantIdentifier, Storage storage, String sessionHandle)
            throws StorageQueryException, UnauthorisedException {
        io.supertokens.pluginInterface.session.SessionInfo session = StorageUtils.getSessionStorage(storage)
                .getSession(tenantIdentifier, sessionHandle);
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
                ResourceDistributor.getAppForTesting(), storage,
                sessionHandle);
    }

    /**
     * Used to retrieve all session information for a given session handle.
     * Used by:
     * - /recipe/session GET
     */
    public static io.supertokens.pluginInterface.session.SessionInfo getSession(
            TenantIdentifier tenantIdentifier, Storage storage, String sessionHandle)
            throws StorageQueryException, UnauthorisedException {
        io.supertokens.pluginInterface.session.SessionInfo session = StorageUtils.getSessionStorage(storage)
                .getSession(tenantIdentifier, sessionHandle);

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
        updateSession(ResourceDistributor.getAppForTesting(), storage,
                sessionHandle, sessionData, jwtData, version);
    }

    public static void updateSession(TenantIdentifier tenantIdentifier, Storage storage,
                                     String sessionHandle, @Nullable JsonObject sessionData,
                                     @Nullable JsonObject jwtData, AccessToken.VERSION version)
            throws StorageQueryException, UnauthorisedException, AccessTokenPayloadError {
        if (jwtData != null &&
                Arrays.stream(AccessTokenInfo.getRequiredAndProtectedProps(version)).anyMatch(jwtData::has)) {
            throw new AccessTokenPayloadError("The user payload contains protected field");
        }

        io.supertokens.pluginInterface.session.SessionInfo session = StorageUtils.getSessionStorage(storage)
                .getSession(tenantIdentifier, sessionHandle);
        // If there is no session, or session is expired
        if (session == null || session.expiry <= System.currentTimeMillis()) {
            throw new UnauthorisedException("Session does not exist.");
        }

        int numberOfRowsAffected = StorageUtils.getSessionStorage(storage)
                .updateSession(tenantIdentifier, sessionHandle, sessionData, jwtData);
        if (numberOfRowsAffected != 1) {
            throw new UnauthorisedException("Session does not exist.");
        }
    }

    @Deprecated
    public static void updateSessionBeforeCDI2_21(TenantIdentifier tenantIdentifier, Storage storage,
                                                  String sessionHandle, @Nullable JsonObject sessionData,
                                                  @Nullable JsonObject jwtData)
            throws StorageQueryException, UnauthorisedException {

        io.supertokens.pluginInterface.session.SessionInfo session = StorageUtils.getSessionStorage(storage)
                .getSession(tenantIdentifier, sessionHandle);
        // If there is no session, or session is expired
        if (session == null || session.expiry <= System.currentTimeMillis()) {
            throw new UnauthorisedException("Session does not exist.");
        }

        int numberOfRowsAffected = StorageUtils.getSessionStorage(storage)
                .updateSession(tenantIdentifier, sessionHandle, sessionData,
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

    private static void emitSessionCreatedEvent(Main main, Storage storage, TenantIdentifier tenantIdentifier,
            String recipeUserId, String primaryUserId, String sessionHandle) {
        AuditLog.emit(main, storage, tenantIdentifier, new AuditLogEvent(
                tenantIdentifier.getAppId(),
                tenantIdentifier.getTenantId(),
                recipeUserId,
                primaryUserId,
                "session_created",
                "success",
                null,
                sessionHandle,
                System.currentTimeMillis(),
                null));
    }
}
