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
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.WebserverAPI;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Deprecated
public class HandshakeAPI extends WebserverAPI {
    private static final long serialVersionUID = -3647598432179106404L;

    public HandshakeAPI(Main main) {
        super(main, RECIPE_ID.SESSION.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/handshake";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        if (super.getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v2_20)) {
            super.sendTextResponse(404, "Not found", resp);
            return;
        }
        try {
            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");

            Utils.addLegacySigningKeyInfos(main, result, super.getVersionFromRequest(req).betweenInclusive(SemVer.v2_9, SemVer.v2_20));

            result.addProperty("accessTokenBlacklistingEnabled", Config.getConfig(main).getAccessTokenBlacklisting());
            result.addProperty("accessTokenValidity", Config.getConfig(main).getAccessTokenValidity());
            result.addProperty("refreshTokenValidity", Config.getConfig(main).getRefreshTokenValidity());
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | StorageTransactionLogicException | UnsupportedJWTSigningAlgorithmException e) {
            throw new ServletException(e);
        }
    }
}
