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
import io.supertokens.jwt.exceptions.UnsupportedJWTSigningAlgorithmException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class JWTSigningAPI extends WebserverAPI {
    private static final long serialVersionUID = 288744303462186631L;
    public static final String UNSUPPORTED_ALGORITHM_ERROR_STATUS = "UNSUPPORTED_ALGORITHM_ERROR";

    public JWTSigningAPI(Main main) {
        super(main, RECIPE_ID.JWT.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/jwt";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        SemVer version = super.getVersionFromRequest(req);

        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String algorithm = InputParser.parseStringOrThrowError(input, "algorithm", false);
        assert algorithm != null;

        String jwksDomain = InputParser.parseStringOrThrowError(input, "jwksDomain", false);
        assert jwksDomain != null;

        JsonObject payload = InputParser.parseJsonObjectOrThrowError(input, "payload", false);
        assert payload != null;

        long validity = InputParser.parseLongOrThrowError(input, "validity", false);

        if (validity <= 0) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("validity must be greater than or equal to 0"));
        }

        boolean useDynamicKey = false;
        if (version.greaterThanOrEqualTo(SemVer.v2_21)) {
            Boolean useStaticKeyInput = InputParser.parseBooleanOrThrowError(input, "useStaticSigningKey", true);
            // useStaticKeyInput defaults to true, so we check if it has been explicitly set to false
            useDynamicKey = Boolean.FALSE.equals(useStaticKeyInput);
        }

        try {
            String jwt = JWTSigningFunctions.createJWTToken(this.getAppIdentifierWithStorage(req), main,
                    algorithm.toUpperCase(), payload, jwksDomain,
                    validity, useDynamicKey);
            JsonObject reply = new JsonObject();
            reply.addProperty("status", "OK");
            reply.addProperty("jwt", jwt);
            super.sendJsonResponse(200, reply, resp);
        } catch (UnsupportedJWTSigningAlgorithmException e) {
            JsonObject reply = new JsonObject();
            reply.addProperty("status", UNSUPPORTED_ALGORITHM_ERROR_STATUS);
            super.sendJsonResponse(200, reply, resp);
        } catch (StorageQueryException | StorageTransactionLogicException | NoSuchAlgorithmException | InvalidKeySpecException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }
}
