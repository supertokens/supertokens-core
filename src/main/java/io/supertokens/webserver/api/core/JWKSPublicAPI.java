/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.signingkeys.SigningKeys;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

public class JWKSPublicAPI extends WebserverAPI {
    public JWKSPublicAPI(Main main) {
        super(main, "");
    }

    @Override
    protected boolean versionNeeded(HttpServletRequest req) {
        return false;
    }


    @Override
    protected boolean checkAPIKey(HttpServletRequest req) {
        return false;
    }


    @Override
    public String getPath() {
        return "/.well-known/jwks.json";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            SigningKeys signingKeys = SigningKeys.getInstance(this.getAppIdentifier(req), main);
            List<JsonObject> jwks = signingKeys.getJWKS();
            JsonObject reply = new JsonObject();
            JsonArray jwksJsonArray = new JsonParser().parse(new Gson().toJson(jwks)).getAsJsonArray();
            reply.add("keys", jwksJsonArray);
            resp.setHeader("Cache-Control", "max-age=" + signingKeys.getCacheDurationInSeconds() + ", must-revalidate");
            super.sendJsonResponse(200, reply, resp);
        } catch (StorageQueryException | StorageTransactionLogicException | NoSuchAlgorithmException
                 | InvalidKeySpecException | TenantOrAppNotFoundException | UnsupportedJWTSigningAlgorithmException e) {
            throw new ServletException(e);
        }
    }
}
