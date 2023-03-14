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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.exceptions.AccessTokenPayloadError;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.session.SessionInfo;
import io.supertokens.session.Session;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.signingkeys.SigningKeys.KeyInfo;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

public class SessionAPI extends WebserverAPI {
    private static final long serialVersionUID = 7142317017402226537L;

    public SessionAPI(Main main) {
        super(main, RECIPE_ID.SESSION.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/session";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        SemVer version = super.getVersionFromRequest(req);

        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        assert userId != null;
        Boolean enableAntiCsrf = InputParser.parseBooleanOrThrowError(input, "enableAntiCsrf", false);
        assert enableAntiCsrf != null;
        JsonObject userDataInJWT = InputParser.parseJsonObjectOrThrowError(input, "userDataInJWT", false);
        assert userDataInJWT != null;
        JsonObject userDataInDatabase = InputParser.parseJsonObjectOrThrowError(input, "userDataInDatabase", false);
        assert userDataInDatabase != null;

        boolean useStaticSigningKey = Config.getConfig(main).getAccessTokenSigningKeyDynamic();
        if (version.greaterThanOrEqualTo(SemVer.v2_19)) {
            Boolean inputUseStaticKey = InputParser.parseBooleanOrThrowError(input, "useStaticSigningKey", true);

            // useStaticKeyInput defaults to false, so we check if it has been explicitly set to true
            useStaticSigningKey = Boolean.TRUE.equals(inputUseStaticKey);
        }
        try {
            SessionInformationHolder sessionInfo = Session.createNewSession(main, userId, userDataInJWT,
                    userDataInDatabase, enableAntiCsrf, version.greaterThanOrEqualTo(SemVer.v2_19), useStaticSigningKey);

            JsonObject result = sessionInfo.toJsonObject();

            result.addProperty("status", "OK");

            if (super.getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v2_19)) {
                result.remove("idRefreshToken");
            } else {
                Utils.addLegacySigningKeyInfos(main, result, super.getVersionFromRequest(req).betweenInclusive(SemVer.v2_9, SemVer.v2_19));
            }

            super.sendJsonResponse(200, result, resp);
        } catch(AccessTokenPayloadError e) {
            throw new ServletException(new BadRequestException(e.getMessage()));
        } catch (NoSuchAlgorithmException | StorageQueryException | InvalidKeyException | InvalidKeySpecException
                 | StorageTransactionLogicException | SignatureException | IllegalBlockSizeException
                 | BadPaddingException | InvalidAlgorithmParameterException | NoSuchPaddingException |
                 UnsupportedJWTSigningAlgorithmException e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String sessionHandle = InputParser.getQueryParamOrThrowError(req, "sessionHandle", false);
        assert sessionHandle != null;

        try {
            SessionInfo sessionInfo = Session.getSession(main, sessionHandle);

            JsonObject result = new Gson().toJsonTree(sessionInfo).getAsJsonObject();
            result.add("userDataInJWT", Utils.toJsonTreeWithNulls(sessionInfo.userDataInJWT));
            result.add("userDataInDatabase", Utils.toJsonTreeWithNulls(sessionInfo.userDataInDatabase));

            result.addProperty("status", "OK");

            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException e) {
            throw new ServletException(e);
        } catch (UnauthorisedException e) {
            Logging.debug(main, Utils.exceptionStacktraceToString(e));
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "UNAUTHORISED");
            reply.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, reply, resp);
        }
    }
}
