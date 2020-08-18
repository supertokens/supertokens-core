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

import io.supertokens.Main;
import io.supertokens.backendAPI.Ping;
import io.supertokens.config.Config;
import io.supertokens.exceptions.QuitProgramException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.licenseKey.LicenseKey;
import io.supertokens.licenseKey.LicenseKey.PLAN_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.tokenInfo.PastTokenInfo;
import io.supertokens.session.info.TokenInfo;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.utils.Utils;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class RefreshToken {

    public static RefreshTokenInfo getInfoFromRefreshToken(@Nonnull Main main, @Nonnull String token)
            throws UnauthorisedException, StorageQueryException {
        try {
            TYPE tokenType = getTypeFromToken(token);

            if (tokenType == TYPE.FREE) {   // Do not modify this line

                // format of token is <random_uuid>.V0
                PastTokenInfo pastTokenInfo = StorageLayer.getStorageLayer(main)
                        .getPastTokenInfo(Utils.hashSHA256(Utils.hashSHA256(token)));
                if (pastTokenInfo == null) {
                    throw new UnauthorisedException(
                            "Refresh token not found in database. Please create a new session.");
                }
                return new RefreshTokenInfo(pastTokenInfo.sessionHandle, null,
                        pastTokenInfo.parentRefreshTokenHash2, tokenType);

            } else {    // Do not modify this line
                Ping.getInstance(main).hadUsedProVersion = true;
                throw new QuitProgramException(
                        "ERROR: You have moved from a pro binary to a community binary. Please use the pro binary " +
                                "with your current license key");

            }
        } catch (InvalidRefreshTokenFormatException | NoSuchAlgorithmException | NullPointerException e) {
            throw new UnauthorisedException(e);
        }
    }

    public static TokenInfo createNewRefreshToken(@Nonnull Main main, @Nonnull String sessionHandle,
                                                  @Nullable String parentRefreshTokenHash2,
                                                  @Nullable String currCDIVersion)
            throws NoSuchAlgorithmException, StorageQueryException {
        if (LicenseKey.get(main).getPlanType() != PLAN_TYPE.FREE) {
            throw new UnsupportedOperationException("Using free create refresh function for non free version");
        }
        String token = UUID.randomUUID().toString() + "." + TYPE.FREE.toString();
        String refreshTokenHash2 = Utils.hashSHA256(Utils.hashSHA256(token));
        parentRefreshTokenHash2 = parentRefreshTokenHash2 == null ? refreshTokenHash2 : parentRefreshTokenHash2;

        long now = System.currentTimeMillis();
        PastTokenInfo pastTokenInfo = new PastTokenInfo(refreshTokenHash2, sessionHandle, parentRefreshTokenHash2,
                now);
        StorageLayer.getStorageLayer(main).insertPastToken(pastTokenInfo);

        return new TokenInfo(token, now + Config.getConfig(main).getRefreshTokenValidity(), now,
                Config.getConfig(main).getRefreshAPIPath(),
                Config.getConfig(main).getCookieSecure(main), Config.getConfig(main).getCookieDomain(currCDIVersion),
                Config.getConfig(main).getCookieSameSite());
    }

    @TestOnly
    public static TokenInfo createNewRefreshToken(@Nonnull Main main, @Nonnull String sessionHandle,
                                                  @Nullable String parentRefreshTokenHash2)
            throws NoSuchAlgorithmException, StorageQueryException {
        return createNewRefreshToken(main, sessionHandle, parentRefreshTokenHash2, null);
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
        FREE("V0"), PAID("V1");

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

    public static class RefreshTokenInfo {
        @Nonnull
        public final String sessionHandle;
        @Nullable
        public final String userId;
        @Nullable
        public final String parentRefreshTokenHash2;
        @Nonnull
        public final TYPE type;

        RefreshTokenInfo(@Nonnull String sessionHandle, @Nullable String userId,
                         @Nullable String parentRefreshTokenHash2,
                         @Nonnull TYPE type) {
            this.sessionHandle = sessionHandle;
            this.userId = userId;
            this.parentRefreshTokenHash2 = parentRefreshTokenHash2;
            this.type = type;
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
