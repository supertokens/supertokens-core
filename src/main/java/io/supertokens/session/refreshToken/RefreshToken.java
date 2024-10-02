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

package io.supertokens.session.refreshToken;

import com.google.gson.Gson;
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.session.info.TokenInfo;
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
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

public class RefreshToken {

    @TestOnly
    public static RefreshTokenInfo getInfoFromRefreshToken(@Nonnull Main main, @Nonnull String token)
            throws UnauthorisedException, StorageQueryException, StorageTransactionLogicException {
        try {
            return getInfoFromRefreshToken(new AppIdentifier(null, null), main, token);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static RefreshTokenInfo getInfoFromRefreshToken(AppIdentifier appIdentifier,
                                                           @Nonnull Main main, @Nonnull String token)
            throws UnauthorisedException, StorageQueryException, StorageTransactionLogicException,
            TenantOrAppNotFoundException {
        String key = RefreshTokenKey.getInstance(appIdentifier, main).getKey();
        try {
            TYPE tokenType = getTypeFromToken(token);

            // format of token is <encrypted part>.<nonce>.V1
            String[] splittedToken = token.split("\\.");
            if (splittedToken.length != 3) {
                throw new InvalidRefreshTokenFormatException(
                        "Refresh token split with dot yielded an array of length: " + splittedToken.length);
            }
            String nonce = splittedToken[1];
            String decrypted = Utils.decrypt(splittedToken[0], key);
            RefreshTokenPayload tokenPayload = new Gson().fromJson(decrypted, RefreshTokenPayload.class);
            if (tokenPayload.userId == null || tokenPayload.sessionHandle == null
                    || !nonce.equals(tokenPayload.nonce)) {
                throw new UnauthorisedException("Invalid refresh token");
            }

            return new RefreshTokenInfo(tokenPayload.sessionHandle, tokenPayload.userId,
                    tokenPayload.parentRefreshTokenHash1, null, tokenPayload.antiCsrfToken, tokenType,
                    new TenantIdentifier(appIdentifier.getConnectionUriDomain(), appIdentifier.getAppId(),
                            tokenPayload.tenantId));

        } catch (Exception e) {
            throw new UnauthorisedException(e);
        }
    }

    @TestOnly
    public static TokenInfo createNewRefreshToken(@Nonnull Main main, @Nonnull String sessionHandle,
                                                  @Nonnull String userId, @Nullable String parentRefreshTokenHash1,
                                                  @Nullable String antiCsrfToken)
            throws NoSuchAlgorithmException, StorageQueryException, NoSuchPaddingException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException, StorageTransactionLogicException,
            InvalidAlgorithmParameterException, InvalidKeySpecException {
        try {
            return createNewRefreshToken(new TenantIdentifier(null, null, null), main, sessionHandle, userId,
                    parentRefreshTokenHash1,
                    antiCsrfToken);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static TokenInfo createNewRefreshToken(TenantIdentifier tenantIdentifier, @Nonnull Main main,
                                                  @Nonnull String sessionHandle,
                                                  @Nonnull String userId, @Nullable String parentRefreshTokenHash1,
                                                  @Nullable String antiCsrfToken)
            throws NoSuchAlgorithmException, StorageQueryException, NoSuchPaddingException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException, StorageTransactionLogicException,
            InvalidAlgorithmParameterException, InvalidKeySpecException, TenantOrAppNotFoundException {
        String key = RefreshTokenKey.getInstance(tenantIdentifier.toAppIdentifier(), main).getKey();
        String nonce = Utils.hashSHA256(UUID.randomUUID().toString());
        RefreshTokenPayload payload = new RefreshTokenPayload(sessionHandle, userId, parentRefreshTokenHash1, nonce,
                antiCsrfToken, tenantIdentifier.getTenantId());
        String payloadSerialised = new Gson().toJson(payload);
        String encryptedPayload = Utils.encrypt(payloadSerialised, key);
        String token = encryptedPayload + "." + nonce + "." + TYPE.FREE_OPTIMISED.toString();
        long now = System.currentTimeMillis();
        return new TokenInfo(token,
                now + Config.getConfig(tenantIdentifier, main).getRefreshTokenValidityInMillis(),
                now);
    }

    private static TYPE getTypeFromToken(String token) throws InvalidRefreshTokenFormatException {
        try {
            // token format can <random_uuid>.V0 || <encrypted part>.<nonce>.V1
            String[] splitted = token.split("\\.");
            String typeStr = splitted[splitted.length - 1];
            TYPE t = TYPE.fromString(typeStr);
            if (t == null) {
                throw new InvalidRefreshTokenFormatException("version of refresh token not recognised");
            }
            return t;
        } catch (Exception e) {
            throw new InvalidRefreshTokenFormatException(e);
        }
    }

    public enum TYPE {
        FREE("V0"), PAID("V1"), FREE_OPTIMISED("V2");

        private String version;

        TYPE(String version) {
            this.version = version;
        }

        public static TYPE fromString(String text) {
            for (TYPE t : TYPE.values()) {
                if (t.version.equalsIgnoreCase(text)) {
                    return t;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return version;
        }
    }

    static class RefreshTokenPayload {
        @Nonnull
        final String sessionHandle;
        @Nonnull
        final String userId;
        @Nullable
        final String parentRefreshTokenHash1;
        @Nonnull
        final String nonce;
        @Nullable
        public final String antiCsrfToken;
        @Nullable
        public final String tenantId;

        RefreshTokenPayload(@Nonnull String sessionHandle, @Nonnull String userId,
                            @Nullable String parentRefreshTokenHash1, @Nonnull String nonce,
                            @Nullable String antiCsrfToken, @Nullable String tenantId) {
            this.sessionHandle = sessionHandle;
            this.userId = userId;
            this.parentRefreshTokenHash1 = parentRefreshTokenHash1;
            this.nonce = nonce;
            this.antiCsrfToken = antiCsrfToken;
            this.tenantId = tenantId == null || tenantId.equals(TenantIdentifier.DEFAULT_TENANT_ID) ? null : tenantId;
        }
    }

    public static class RefreshTokenInfo {
        @Nonnull
        public final String sessionHandle;
        @Nullable
        public final String userId;
        @Nullable
        public final String parentRefreshTokenHash1;
        @Nullable
        public final String parentRefreshTokenHash2;
        @Nonnull
        public final TYPE type;
        @Nullable
        public final String antiCsrfToken;
        @Nonnull
        public final TenantIdentifier tenantIdentifier;

        RefreshTokenInfo(@Nonnull String sessionHandle, @Nullable String userId,
                         @Nullable String parentRefreshTokenHash1, @Nullable String parentRefreshTokenHash2,
                         @Nullable String antiCsrfToken, @Nonnull TYPE type,
                         @Nullable TenantIdentifier tenantIdentifier) {
            this.sessionHandle = sessionHandle;
            this.userId = userId;
            this.parentRefreshTokenHash1 = parentRefreshTokenHash1;
            this.parentRefreshTokenHash2 = parentRefreshTokenHash2;
            this.antiCsrfToken = antiCsrfToken;
            this.type = type;
            this.tenantIdentifier = tenantIdentifier;
        }
    }

    public static class InvalidRefreshTokenFormatException extends Exception {

        private static final long serialVersionUID = 1L;

        InvalidRefreshTokenFormatException(String err) {
            super(err);
        }

        InvalidRefreshTokenFormatException(Exception e) throws InvalidRefreshTokenFormatException {
            super(e);
            if (e instanceof InvalidRefreshTokenFormatException) {
                throw (InvalidRefreshTokenFormatException) e;
            }
        }

    }
}
