/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.jwt;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.jwt.JWTSigningFunctions;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JWTSigningAPI extends WebserverAPI {

    public JWTSigningAPI(Main main) {
        super(main, RECIPE_ID.JWT.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/jwt";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String algorithm = InputParser.parseStringOrThrowError(input, "algorithm", false);
        assert algorithm != null;

        if (!JWTSigningFunctions.isJWTAlgorithmSupported(algorithm)) {
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "UNSUPPORTED_ALGORITHM");
            super.sendJsonResponse(200, reply, resp);
            return;
        }

        String jwksDomain = InputParser.parseStringOrThrowError(input, "jwksDomain", false);
        assert jwksDomain != null;

        JsonObject payload = InputParser.parseJsonObjectOrThrowError(input, "payload", false);
        assert payload != null;

        long validity = InputParser.parseLongOrThrowError(input, "validity", false);

        try {
            String jwt = JWTSigningFunctions.createJWTToken(main, algorithm, payload, jwksDomain, validity);
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "OK");
            reply.addProperty("jwt", jwt);
            super.sendJsonResponse(200, reply, resp);
        } catch (Exception e) {
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "JWT_CREATION_ERROR");
            super.sendJsonResponse(200, reply, resp);
        }
    }
}
