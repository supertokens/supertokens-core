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
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.session.Session;
import io.supertokens.session.accessToken.AccessTokenSigningKey;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class HandshakeAPI extends WebserverAPI {
    private static final long serialVersionUID = -3647598432179106404L;

    public HandshakeAPI(Main main) {
        super(main, Session.RECIPE_ID);
    }

    @Override
    public String getPath() {
        return "/recipe/handshake";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("jwtSigningPublicKey", AccessTokenSigningKey.getInstance(main).getKey().publicKey);
            result.addProperty("jwtSigningPublicKeyExpiryTime",
                    AccessTokenSigningKey.getInstance(main).getKeyExpiryTime());
            result.addProperty("accessTokenBlacklistingEnabled", Config.getConfig(main).getAccessTokenBlacklisting());
            result.addProperty("accessTokenValidity", Config.getConfig(main).getAccessTokenValidity());
            result.addProperty("refreshTokenValidity", Config.getConfig(main).getRefreshTokenValidity());
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | StorageTransactionLogicException e) {
            throw new ServletException(e);
        }
    }
}
