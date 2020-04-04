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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

public class SessionAPI extends WebserverAPI {
    private static final long serialVersionUID = 7142317017402226537L;

    public SessionAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/session";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        assert userId != null;
        JsonObject userDataInJWT = InputParser.parseJsonObjectOrThrowError(input, "userDataInJWT", false);
        assert userDataInJWT != null;
        JsonObject userDataInDatabase = InputParser.parseJsonObjectOrThrowError(input, "userDataInDatabase", false);
        assert userDataInDatabase != null;

        try {
            SessionInformationHolder sessionInfo = Session
                    .createNewSession(main, userId, userDataInJWT, userDataInDatabase);

            JsonObject result = new JsonParser().parse(new Gson().toJson(sessionInfo)).getAsJsonObject();

            result.getAsJsonObject("accessToken").remove("sameSite");
            result.getAsJsonObject("refreshToken").remove("sameSite");
            result.getAsJsonObject("idRefreshToken").remove("sameSite");
            result.getAsJsonObject("idRefreshToken").remove("cookiePath");
            result.getAsJsonObject("idRefreshToken").remove("cookieSecure");
            result.getAsJsonObject("idRefreshToken").remove("domain");

            result.addProperty("status", "OK");
            result.addProperty("jwtSigningPublicKey", AccessTokenSigningKey.getInstance(main).getKey().publicKey);
            result.addProperty("jwtSigningPublicKeyExpiryTime",
                    AccessTokenSigningKey.getInstance(main).getKeyExpiryTime());
            super.sendJsonResponse(200, result, resp);
        } catch (NoSuchAlgorithmException | StorageQueryException | InvalidKeyException | InvalidKeySpecException |
                StorageTransactionLogicException | SignatureException e) {
            throw new ServletException(e);
        }

        super.saveDeviceDriverInfoIfPresent(input);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String userId = InputParser.parseStringOrThrowError(input, "userId", true);

        String sessionHandle = InputParser.parseStringOrThrowError(input, "sessionHandle", true);

        JsonArray arr = InputParser.parseArrayOrThrowError(input, "sessionHandles", true);
        String[] sessionHandles = null;
        if (arr != null) {
            sessionHandles = new String[arr.size()];
            for (int i = 0; i < sessionHandles.length; i++) {
                String session = InputParser.parseStringFromElementOrThrowError(arr.get(i), "sessionHandles", false);
                sessionHandles[i] = session;
            }
        }

        int numberOfNullItems = 0;
        if (userId != null) {
            numberOfNullItems++;
        }
        if (sessionHandle != null) {
            numberOfNullItems++;
        }
        if (sessionHandles != null) {
            numberOfNullItems++;
        }
        if (numberOfNullItems == 0 || numberOfNullItems > 1) {
            throw new ServletException(new BadRequestException(
                    "Invalid JSON input - use one of userId, sessionHandle, or sessionHandles array"));
        }

        if (userId != null) {
            try {
                int numberOfSessionsRevoked = Session.revokeAllSessionsForUser(main, userId);
                JsonObject result = new JsonObject();
                result.addProperty("status", "OK");
                result.addProperty("numberOfSessionsRevoked", numberOfSessionsRevoked);
                super.sendJsonResponse(200, result, resp);
            } catch (StorageQueryException e) {
                throw new ServletException(e);
            }
        } else if (sessionHandle != null) {
            try {
                int numberOfSessionsRevoked = Session.revokeSessionUsingSessionHandle(main, sessionHandle);
                JsonObject result = new JsonObject();
                result.addProperty("status", "OK");
                result.addProperty("numberOfSessionsRevoked", numberOfSessionsRevoked);
                super.sendJsonResponse(200, result, resp);
            } catch (StorageQueryException e) {
                throw new ServletException(e);
            }
        } else {
            try {
                int numberOfSessionsRevoked = Session.revokeSessionUsingSessionHandles(main, sessionHandles);
                JsonObject result = new JsonObject();
                result.addProperty("status", "OK");
                result.addProperty("numberOfSessionsRevoked", numberOfSessionsRevoked);
                super.sendJsonResponse(200, result, resp);
            } catch (StorageQueryException e) {
                throw new ServletException(e);
            }
        }
    }
}
