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

package io.supertokens.webserver.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.exceptions.TokenTheftDetectedException;
import io.supertokens.exceptions.UnauthorisedException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.session.Session;
import io.supertokens.session.info.SessionInformationHolder;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class RefreshSessionAPI extends WebserverAPI {
    private static final long serialVersionUID = 7142317017402226537L;

    public RefreshSessionAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/session/refresh";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // Do not modify after and including this line
        if (sendRandomUnauthorisedIfDevLicenseAndSomeTimeHasPassed(resp)) {
            return;
        }
        // Do not modify before and including this line
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String refreshToken = InputParser.parseStringOrThrowError(input, "refreshToken", false);
        assert refreshToken != null;

        try {
            SessionInformationHolder sessionInfo = Session
                    .refreshSession(main, refreshToken);

            JsonObject result = new JsonParser().parse(new Gson().toJson(sessionInfo)).getAsJsonObject();

            if (super.getVersionFromRequest(req).equals("1.0")) {
                result.getAsJsonObject("accessToken").remove("sameSite");
                result.getAsJsonObject("refreshToken").remove("sameSite");
                result.getAsJsonObject("idRefreshToken").remove("sameSite");
                result.getAsJsonObject("idRefreshToken").remove("cookiePath");
                result.getAsJsonObject("idRefreshToken").remove("cookieSecure");
                result.getAsJsonObject("idRefreshToken").remove("domain");
            }

            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            throw new ServletException(e);
        } catch (UnauthorisedException e) {
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "UNAUTHORISED");
            reply.addProperty("message", e.getMessage());
            super.sendJsonResponse(200, reply, resp);
        } catch (TokenTheftDetectedException e) {
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "TOKEN_THEFT_DETECTED");

            JsonObject session = new JsonObject();
            session.addProperty("handle", e.sessionHandle);
            session.addProperty("userId", e.userId);
            reply.add("session", session);

            super.sendJsonResponse(200, reply, resp);
        }

        super.saveDeviceDriverInfoIfPresent(input);
    }
}
