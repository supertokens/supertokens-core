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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.session.accessToken.AccessTokenSigningKey;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class HandshakeAPI extends WebserverAPI {
    private static final long serialVersionUID = -3647598432179106404L;

    public HandshakeAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/handshake";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        try {
            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("jwtSigningPublicKey", AccessTokenSigningKey.getInstance(main).getKey().publicKey);
            result.addProperty("jwtSigningPublicKeyExpiryTime",
                    AccessTokenSigningKey.getInstance(main).getKeyExpiryTime());
            if (Config.getConfig(main).getCookieDomain(super.getVersionFromRequest(req)) != null) {
                result.addProperty("cookieDomain",
                        Config.getConfig(main).getCookieDomain(super.getVersionFromRequest(req)));
            }
            result.addProperty("cookieSecure", Config.getConfig(main).getCookieSecure(main));
            result.addProperty("accessTokenPath", Config.getConfig(main).getAccessTokenPath());
            result.addProperty("refreshTokenPath", Config.getConfig(main).getRefreshAPIPath());
            result.addProperty("enableAntiCsrf", Config.getConfig(main).getEnableAntiCSRF());
            result.addProperty("accessTokenBlacklistingEnabled", Config.getConfig(main).getAccessTokenBlacklisting());
            if (!super.getVersionFromRequest(req).equals("1.0")) {
                result.addProperty("cookieSameSite", Config.getConfig(main).getCookieSameSite());
                result.addProperty("idRefreshTokenPath", Config.getConfig(main).getAccessTokenPath());
                result.addProperty("sessionExpiredStatusCode", Config.getConfig(main).getSessionExpiredStatusCode());
            }
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            throw new ServletException(e);
        }
    }
}
