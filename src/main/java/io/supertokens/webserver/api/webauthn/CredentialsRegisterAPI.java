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
import com.google.gson.JsonParser;
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public class CredentialsRegisterAPI extends WebserverAPI {

    public CredentialsRegisterAPI(Main main) {
        super(main, "webauthn");
    }

    @Override
    public String getPath() {
        return "/recipe/webauthn/credential/register";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = getTenantStorage(req);

            JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
            String optionsId = InputParser.parseStringOrThrowError(input, "webauthGeneratedOptionsId", false);
            String credentialId = InputParser.parseStringOrThrowError(input, "credential.id", false);
            credentialId = new String(Base64.getDecoder().decode(credentialId.getBytes(StandardCharsets.UTF_8)));
            // do I need the rawId?

            String decodedClientData = new String(Base64.getDecoder().decode(
                    InputParser.parseStringFromElementOrThrowError(input, "credential.response.clientDataJson", false)
                            .getBytes(StandardCharsets.UTF_8)));
            JsonObject clientDataJson = new JsonParser().parse(decodedClientData).getAsJsonObject();

            if(clientDataJson == null || clientDataJson.equals("")){
                throw new ServletException(new BadRequestException("clientDataJson should not be empty!"));
            }

            String type = clientDataJson.get("type").getAsString(); //ex: webauthn.create
            String challenge = clientDataJson.get("challenge").getAsString(); //Base64 encoded
            String origin = clientDataJson.get("origin").getAsString();

            String attestationJson = InputParser.parseStringOrThrowError(input, "credential.response.attestationObject", false);

            JsonArray transportsJson = input.getAsJsonArray("credential.response.transports");
            Set<String> transports = new HashSet<>();
            for(int i = 0; i< transportsJson.size(); i++) {
                transports.add(transportsJson.get(i).getAsString());
            }

            String authenticatorAttachment = InputParser.parseStringOrThrowError(input, "credential.authenticatorAttachment", true);

            if(!input.has("credential.clientExtensionResult")) {
                throw new ServletException(
                        new WebserverAPI.BadRequestException("Field name 'credential.clientExtensionResult' is invalid in JSON input"));
            }
            String clientExtension = input.getAsJsonObject("credential.clientExtensionResults").getAsString();
            String credentialType = InputParser.parseStringOrThrowError(input, "credential.type", false);
            if(credentialType.equals("")){
                credentialType = "public-key";
            }

            WebAuthN.registerCredentials(storage, tenantIdentifier, optionsId, type, credentialId, clientDataJson.getAsString(), attestationJson,
                    transports, authenticatorAttachment, clientExtension, credentialType, origin, challenge);

        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        } catch (StorageQueryException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
