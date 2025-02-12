/*
 *    Copyright (c) 2024, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.webauthn;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webauthn.WebAuthN;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SignInOptionsAPI extends WebserverAPI {


    public SignInOptionsAPI(Main main) {
        super(main, "webauthn");
    }

    @Override
    public String getPath() {
        return "/recipe/webauthn/options/signin";
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = getTenantStorage(req);

            String relyingPartyId = InputParser.parseStringOrThrowError(input, "relyingPartyId", false);
            String relyingPartyName = InputParser.parseStringOrThrowError(input, "relyingPartyName", false);
            String origin = InputParser.parseStringOrThrowError(input, "origin", false);

            Long timeout = InputParser.parseLongOrThrowError(input, "timeout", true);
            if(timeout == null) {
                timeout = 6000L;
            }

            String userVerification = InputParser.parseStringOrThrowError(input, "userVerification", true);
            if(userVerification == null || userVerification.isEmpty()){
                userVerification = "preferred";
            }

            Boolean userPresence = InputParser.parseBooleanOrThrowError(input, "userPresence", true);
            if(userPresence == null){
                userPresence = Boolean.FALSE;
            }


            JsonObject response = WebAuthN.generateSignInOptions(tenantIdentifier, storage, relyingPartyId, relyingPartyName, origin, timeout,
                    userVerification, userPresence);


            response.addProperty("status", "OK");
            super.sendJsonResponse(200, response, resp);

        } catch (TenantOrAppNotFoundException | StorageQueryException e) {
            throw new ServletException(e); //will be handled by WebserverAPI
        }
    }
}
