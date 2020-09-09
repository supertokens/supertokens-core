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

package io.supertokens.webserver.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.exceptions.TryRefreshTokenException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessTokenSigningKey;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class VerifySessionAPI extends WebserverAPI {

    private static final long serialVersionUID = -9169174805902835488L;

    public VerifySessionAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/session/verify";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String accessToken = InputParser.parseStringOrThrowError(input, "accessToken", false);
        assert accessToken != null;
        String antiCsrfToken = InputParser.parseStringOrThrowError(input, "antiCsrfToken", true);
        Boolean doAntiCsrfCheck = InputParser.parseBooleanOrThrowError(input, "doAntiCsrfCheck", false);
        assert doAntiCsrfCheck != null;

        try {
            SessionInformationHolder sessionInfo = Session
                    .getSession(main, accessToken, antiCsrfToken, doAntiCsrfCheck, super.getVersionFromRequest(req));

            JsonObject result = new JsonParser().parse(new Gson().toJson(sessionInfo)).getAsJsonObject();

            if (result.has("accessToken") && super.getVersionFromRequest(req).equals("1.0")) {
                result.getAsJsonObject("accessToken").remove("sameSite");
            }

            result.addProperty("status", "OK");
            result.addProperty("jwtSigningPublicKey", AccessTokenSigningKey.getInstance(main).getKey().publicKey);
            result.addProperty("jwtSigningPublicKeyExpiryTime",
                    AccessTokenSigningKey.getInstance(main).getKeyExpiryTime());
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException | StorageTransactionLogicException e) {
            throw new ServletException(e);
        } catch (UnauthorisedException e) {
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "UNAUTHORISED");
            reply.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, reply, resp);
        } catch (TryRefreshTokenException e) {
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "TRY_REFRESH_TOKEN");
            reply.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, reply, resp);
        }
    }
}
