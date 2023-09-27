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
import io.supertokens.exceptions.AccessTokenPayloadError;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.session.Session;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.session.jwt.JWT;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

public class SessionRegenerateAPI extends WebserverAPI {

    private static final long serialVersionUID = -6614427303762598143L;

    public SessionRegenerateAPI(Main main) {
        super(main, RECIPE_ID.SESSION.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/session/regenerate";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific, but the session is updated based on tenantId obtained from the accessToken
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String accessToken = InputParser.parseStringOrThrowError(input, "accessToken", false);
        assert accessToken != null;

        JsonObject userDataInJWT = InputParser.parseJsonObjectOrThrowError(input, "userDataInJWT", true);

        AppIdentifierWithStorage appIdentifierWithStorage = null;
        try {
            appIdentifierWithStorage = this.getAppIdentifierWithStorage(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        try {
            SessionInformationHolder sessionInfo = getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v2_21) ?
                    Session.regenerateToken(this.getAppIdentifierWithStorage(req), main, accessToken, userDataInJWT) :
                    Session.regenerateTokenBeforeCDI2_21(appIdentifierWithStorage, main, accessToken,
                            userDataInJWT);

            JsonObject result = sessionInfo.toJsonObject();

            if (getVersionFromRequest(req).lesserThan(SemVer.v3_0)) {
                result.get("session").getAsJsonObject().remove("tenantId");
            }
            if (getVersionFromRequest(req).lesserThan(SemVer.v4_0)) {
                result.get("session").getAsJsonObject().remove("recipeUserId");
            }

            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException | StorageTransactionLogicException | NoSuchAlgorithmException
                 | InvalidKeyException | SignatureException | InvalidKeySpecException | JWT.JWTException |
                 UnsupportedJWTSigningAlgorithmException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        } catch (UnauthorisedException | TryRefreshTokenException e) {
            Logging.debug(main, appIdentifierWithStorage.getAsPublicTenantIdentifier(),
                    Utils.exceptionStacktraceToString(e));
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "UNAUTHORISED");
            reply.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, reply, resp);
        } catch (AccessTokenPayloadError e) {
            throw new ServletException(new BadRequestException(e.getMessage()));
        }
    }

}
