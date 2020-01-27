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
                                                  @Nullable String parentRefreshTokenHash2)
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
                Config.getConfig(main).getCookieSecure(main), Config.getConfig(main).getCookieDomain());

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
