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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.supertokens.Main;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.dashboard.exceptions.UserIdNotFoundException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.utils.Utils;
import io.supertokens.webauthn.WebAuthN;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class OptionsRegisterAPI extends WebserverAPI {

    public OptionsRegisterAPI(Main main) {
        super(main, "webauthn");
    }

    @Override
    public String getPath() {
        return "/recipe/webauthn/options/register";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = getTenantStorage(req);

            String email = InputParser.parseStringOrThrowError(input, "email", false);
            email = Utils.normaliseEmail(email);

            String displayName = InputParser.parseStringOrThrowError(input, "displayName", true);
            if(displayName == null || displayName.equals("")){
                displayName = email;
            }
            String relyingPartyName = InputParser.parseStringOrThrowError(input, "relyingPartyName", false);
            String relyingPartyId = InputParser.parseStringOrThrowError(input, "relyingPartyId", false);
            String origin = InputParser.parseStringOrThrowError(input, "origin", false);

            Long timeout = InputParser.parseLongOrThrowError(input, "timeout", true);
            if(timeout == null) {
                timeout = 6000L;
            }

            String attestation = InputParser.parseStringOrThrowError(input, "attestation", true);
            if(attestation == null || attestation.equals("")){
                attestation = "none";
            }

            String residentKey = InputParser.parseStringOrThrowError(input, "residentKey", true);
            if(residentKey == null || residentKey.equals("")){
                residentKey = "required";
            }

            String userVerificitaion = InputParser.parseStringOrThrowError(input, "userVerificitaion", true);
            if(userVerificitaion == null || userVerificitaion.equals("")){
                userVerificitaion = "preferred";
            }

            JsonArray supportedAlgorithmIds = InputParser.parseArrayOrThrowError(input, "supportedAlgorithmIds", true);
            if(supportedAlgorithmIds == null || supportedAlgorithmIds.isJsonNull()) {
                supportedAlgorithmIds = new JsonArray();

                JsonPrimitive min8 = new JsonPrimitive(-8);
                JsonPrimitive min7 = new JsonPrimitive(-7);
                JsonPrimitive min257 = new JsonPrimitive(-257);
                supportedAlgorithmIds.add(min8);
                supportedAlgorithmIds.add(min7);
                supportedAlgorithmIds.add(min257);
            }

            JsonObject response = WebAuthN.generateOptions(tenantIdentifier, storage, email, displayName, relyingPartyName, relyingPartyId, origin, timeout, attestation, residentKey,
                    userVerificitaion, supportedAlgorithmIds);


            response.addProperty("status", "OK");
            super.sendJsonResponse(200, response, resp);

        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e); //will be handled by WebserverAPI
        } catch (StorageQueryException e) {
            throw new RuntimeException(e);
        } catch (UserIdNotFoundException e) {
            JsonObject response = new JsonObject();
            response.addProperty("error", "USER_WITH_EMAIL_NOT_FOUND_ERROR");
            super.sendJsonResponse(400, response, resp);
        }
    }
}
