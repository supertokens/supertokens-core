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
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.exceptions.AccessTokenPayloadError;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.session.Session;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class VerifySessionAPI extends WebserverAPI {

    private static final long serialVersionUID = -9169174805902835488L;

    public VerifySessionAPI(Main main) {
        super(main, RECIPE_ID.SESSION.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/session/verify";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific, but the session is fetched based on tenantId obtained from the accessToken
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String accessToken = InputParser.parseStringOrThrowError(input, "accessToken", false);
        assert accessToken != null;
        String antiCsrfToken = InputParser.parseStringOrThrowError(input, "antiCsrfToken", true);
        Boolean doAntiCsrfCheck = InputParser.parseBooleanOrThrowError(input, "doAntiCsrfCheck", false);
        assert doAntiCsrfCheck != null;
        Boolean enableAntiCsrf = InputParser.parseBooleanOrThrowError(input, "enableAntiCsrf", false);
        assert enableAntiCsrf != null;

        AppIdentifier appIdentifier;
        try {
            // We actually don't use the storage because tenantId is obtained from the accessToken,
            // and appropriate storage is obtained later
            appIdentifier = this.getAppIdentifierWithStorage(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        try {

            boolean checkDatabase = Config.getConfig(appIdentifier.getAsPublicTenantIdentifier(), main)
                    .getAccessTokenBlacklisting();
            if (super.getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v2_21)) {
                checkDatabase = Boolean.TRUE.equals(
                        InputParser.parseBooleanOrThrowError(input, "checkDatabase", false));
            }

            SessionInformationHolder sessionInfo = Session.getSession(appIdentifier,
                    main, accessToken,
                    antiCsrfToken, enableAntiCsrf,
                    doAntiCsrfCheck, checkDatabase);

            JsonObject result = sessionInfo.toJsonObject();
            result.addProperty("status", "OK");

            if (!super.getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v2_21)) {
                result.addProperty("jwtSigningPublicKey",
                        new Utils.PubPriKey(SigningKeys.getInstance(appIdentifier, main)
                                .getLatestIssuedDynamicKey().value).publicKey);
                result.addProperty("jwtSigningPublicKeyExpiryTime",
                        SigningKeys.getInstance(appIdentifier, main).getDynamicSigningKeyExpiryTime());

                Utils.addLegacySigningKeyInfos(appIdentifier, main, result,
                        super.getVersionFromRequest(req).betweenInclusive(SemVer.v2_9, SemVer.v2_21));
            }

            if (getVersionFromRequest(req).lesserThan(SemVer.v3_0)) {
                result.get("session").getAsJsonObject().remove("tenantId");
            }
            if (getVersionFromRequest(req).lesserThan(SemVer.v4_0)) {
                result.get("session").getAsJsonObject().remove("recipeUserId");
            }

            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | StorageTransactionLogicException | TenantOrAppNotFoundException |
                 UnsupportedJWTSigningAlgorithmException e) {
            throw new ServletException(e);
        } catch (AccessTokenPayloadError e) {
            throw new ServletException(new BadRequestException(e.getMessage()));
        } catch (UnauthorisedException e) {
            Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), Utils.exceptionStacktraceToString(e));
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "UNAUTHORISED");
            reply.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, reply, resp);
        } catch (TryRefreshTokenException e) {
            Logging.debug(main, appIdentifier.getAsPublicTenantIdentifier(), Utils.exceptionStacktraceToString(e));
            try {
                JsonObject reply = new JsonObject();
                reply.addProperty("status", "TRY_REFRESH_TOKEN");

                if (!super.getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v2_21)) {
                    reply.addProperty("jwtSigningPublicKey", new Utils.PubPriKey(
                            SigningKeys.getInstance(appIdentifier, main).getLatestIssuedDynamicKey().value).publicKey);
                    reply.addProperty("jwtSigningPublicKeyExpiryTime",
                            SigningKeys.getInstance(appIdentifier, main).getDynamicSigningKeyExpiryTime());

                    Utils.addLegacySigningKeyInfos(this.getAppIdentifierWithStorage(req), main, reply,
                            super.getVersionFromRequest(req).betweenInclusive(SemVer.v2_9, SemVer.v2_21));
                }

                reply.addProperty("message", e.getMessage());
                super.sendJsonResponse(200, reply, resp);
            } catch (StorageQueryException | StorageTransactionLogicException | TenantOrAppNotFoundException |
                     UnsupportedJWTSigningAlgorithmException e2) {
                throw new ServletException(e2);
            }
        }
    }
}
