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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.session.Session;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.signingkeys.SigningKeys.KeyInfo;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

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
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String accessToken = InputParser.parseStringOrThrowError(input, "accessToken", false);
        assert accessToken != null;
        String antiCsrfToken = InputParser.parseStringOrThrowError(input, "antiCsrfToken", true);
        Boolean doAntiCsrfCheck = InputParser.parseBooleanOrThrowError(input, "doAntiCsrfCheck", false);
        assert doAntiCsrfCheck != null;
        Boolean enableAntiCsrf = InputParser.parseBooleanOrThrowError(input, "enableAntiCsrf", false);
        assert enableAntiCsrf != null;

        try {
            SessionInformationHolder sessionInfo = Session.getSession(main, accessToken, antiCsrfToken, enableAntiCsrf,
                    doAntiCsrfCheck);

            JsonObject result = sessionInfo.toJsonObject();
            result.addProperty("status", "OK");

            if (!super.getVersionFromRequest(req).atLeast(SemVer.v2_19) ) {
                result.addProperty("jwtSigningPublicKey",
                        new Utils.PubPriKey(SigningKeys.getInstance(main).getLatestIssuedDynamicKey().value).publicKey);
                result.addProperty("jwtSigningPublicKeyExpiryTime",
                        SigningKeys.getInstance(main).getDynamicSigningKeyExpiryTime());
            }

            if (super.getVersionFromRequest(req).betweenInclusive(SemVer.v2_9, SemVer.v2_18)) {
                List<KeyInfo> keys = SigningKeys.getInstance(main).getDynamicKeys();
                JsonArray jwtSigningPublicKeyListJSON = Utils.keyListToJson(keys);
                result.add("jwtSigningPublicKeyList", jwtSigningPublicKeyListJSON);
            }

            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | StorageTransactionLogicException | UnsupportedJWTSigningAlgorithmException e) {
            throw new ServletException(e);
        } catch (UnauthorisedException e) {
            Logging.debug(main, Utils.exceptionStacktraceToString(e));
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "UNAUTHORISED");
            reply.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, reply, resp);
        } catch (TryRefreshTokenException e) {
            Logging.debug(main, Utils.exceptionStacktraceToString(e));
            try {
                JsonObject reply = new JsonObject();
                reply.addProperty("status", "TRY_REFRESH_TOKEN");

                if (!super.getVersionFromRequest(req).atLeast(SemVer.v2_19)) {
                    reply.addProperty("jwtSigningPublicKey", new Utils.PubPriKey(
                            SigningKeys.getInstance(main).getLatestIssuedDynamicKey().value).publicKey);
                    reply.addProperty("jwtSigningPublicKeyExpiryTime",
                            SigningKeys.getInstance(main).getDynamicSigningKeyExpiryTime());
                }

                if (super.getVersionFromRequest(req).betweenInclusive(SemVer.v2_9, SemVer.v2_18)) {
                    List<KeyInfo> keys = SigningKeys.getInstance(main).getDynamicKeys();
                    JsonArray jwtSigningPublicKeyListJSON = Utils.keyListToJson(keys);
                    reply.add("jwtSigningPublicKeyList", jwtSigningPublicKeyListJSON);
                }

                reply.addProperty("message", e.getMessage());
                super.sendJsonResponse(200, reply, resp);
            } catch (StorageQueryException | StorageTransactionLogicException e2) {
                throw new ServletException(e2);
            }
        }
    }
}
