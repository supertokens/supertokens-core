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

package io.supertokens.webserver.api.session;

import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.exceptions.AccessTokenPayloadError;
import io.supertokens.exceptions.TokenTheftDetectedException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessToken;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class RefreshSessionAPI extends WebserverAPI {
    private static final long serialVersionUID = 7142317017402226537L;

    public RefreshSessionAPI(Main main) {
        super(main, RECIPE_ID.SESSION.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/session/refresh";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific, but session is updated based on tenantId obtained from the refreshToken
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String refreshToken = InputParser.parseStringOrThrowError(input, "refreshToken", false);
        String antiCsrfToken = InputParser.parseStringOrThrowError(input, "antiCsrfToken", true);
        Boolean enableAntiCsrf = InputParser.parseBooleanOrThrowError(input, "enableAntiCsrf", false);
        assert enableAntiCsrf != null;
        assert refreshToken != null;

        AppIdentifierWithStorage appIdentifierWithStorage = null;
        try {
            appIdentifierWithStorage = this.getAppIdentifierWithStorage(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        SemVer version = super.getVersionFromRequest(req);
        try {
            AccessToken.VERSION accessTokenVersion = AccessToken.getAccessTokenVersionForCDI(version);

            SessionInformationHolder sessionInfo = Session.refreshSession(appIdentifierWithStorage, main,
                    refreshToken, antiCsrfToken,
                    enableAntiCsrf, accessTokenVersion);

            if (StorageLayer.getStorage(this.getTenantIdentifierWithStorageFromRequest(req), main).getType() ==
                    STORAGE_TYPE.SQL) {
                try {
                    UserIdMapping userIdMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                            this.getAppIdentifierWithStorage(req),
                            sessionInfo.session.userId, UserIdType.ANY);
                    if (userIdMapping != null) {
                        ActiveUsers.updateLastActive(appIdentifierWithStorage, main,
                                userIdMapping.superTokensUserId);
                    } else {
                        ActiveUsers.updateLastActive(appIdentifierWithStorage, main,
                                sessionInfo.session.userId);
                    }
                } catch (StorageQueryException ignored) {
                }
            }

            JsonObject result = sessionInfo.toJsonObject();

            if (version.greaterThanOrEqualTo(SemVer.v2_21)) {
                result.remove("idRefreshToken");
            }

            if (version.lesserThan(SemVer.v3_0)) {
                result.get("session").getAsJsonObject().remove("tenantId");
            }
            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | StorageTransactionLogicException | TenantOrAppNotFoundException | UnsupportedJWTSigningAlgorithmException e) {
            throw new ServletException(e);
        } catch (AccessTokenPayloadError | UnauthorisedException e) {
            Logging.debug(main, appIdentifierWithStorage.getAsPublicTenantIdentifier(), Utils.exceptionStacktraceToString(e));
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "UNAUTHORISED");
            reply.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, reply, resp);
        } catch (TokenTheftDetectedException e) {
            Logging.debug(main, appIdentifierWithStorage.getAsPublicTenantIdentifier(), Utils.exceptionStacktraceToString(e));
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "TOKEN_THEFT_DETECTED");

            JsonObject session = new JsonObject();
            session.addProperty("handle", e.sessionHandle);
            session.addProperty("userId", e.userId);
            reply.add("session", session);

            super.sendJsonResponse(200, reply, resp);
        }
    }
}
