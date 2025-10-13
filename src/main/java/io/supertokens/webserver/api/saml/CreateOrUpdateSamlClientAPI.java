/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.saml;

import java.io.IOException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.httpRequest.HttpRequest;
import io.supertokens.httpRequest.HttpResponseException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.saml.SAMLClient;
import io.supertokens.saml.SAML;
import io.supertokens.saml.exceptions.MalformedSAMLMetadataXMLException;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CreateOrUpdateSamlClientAPI extends WebserverAPI {

    public CreateOrUpdateSamlClientAPI(Main main) {
        super(main, "saml");
    }

    @Override
    public String getPath() {
        return "/recipe/saml/clients";
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String clientId = InputParser.parseStringOrThrowError(input, "clientId", true);
        String clientSecret = InputParser.parseStringOrThrowError(input, "clientSecret", true);
        String spEntityId = InputParser.parseStringOrThrowError(input, "spEntityId", false);
        String defaultRedirectURI = InputParser.parseStringOrThrowError(input, "defaultRedirectURI", false);
        JsonArray redirectURIs = InputParser.parseArrayOrThrowError(input, "redirectURIs", false);

        String metadataXML = InputParser.parseStringOrThrowError(input, "metadataXML", true);
        String metadataURL = InputParser.parseStringOrThrowError(input, "metadataURL", true);

        Boolean allowIDPInitiatedLogin = InputParser.parseBooleanOrThrowError(input, "allowIDPInitiatedLogin", true);

        if (allowIDPInitiatedLogin == null) {
            allowIDPInitiatedLogin = false;
        }

        if (metadataXML == null && metadataURL == null) {
            throw new ServletException(new BadRequestException("Either metadataXML or metadataURL is required in the input"));
        }

        if (metadataXML != null) {
            try {
                byte[] decodedBytes = java.util.Base64.getDecoder().decode(metadataXML);
                metadataXML = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                JsonObject res = new JsonObject();
                res.addProperty("status", "INVALID_METADATA_XML_ERROR");
                this.sendJsonResponse(200, res, resp);
                return;
            }
        } else {
            try {
                metadataXML = HttpRequest.sendGETRequest(this.main, null, metadataURL, null, 2000, 2000, 0);
            } catch (HttpResponseException | IOException e) {
                throw new ServletException(new BadRequestException("Could not fetch metadata from the URL"));
            }
        }

        if (clientId == null) {
            clientId = "st_saml_" + Utils.getUUID();
        }

        try {
            SAMLClient client = SAML.createOrUpdateSAMLClient(
                getTenantIdentifier(req), getTenantStorage(req),
                    clientId, clientSecret, spEntityId, defaultRedirectURI, redirectURIs, metadataXML, metadataURL, allowIDPInitiatedLogin);
            JsonObject res = client.toJson();
            res.addProperty("status", "OK");
            this.sendJsonResponse(200, res, resp);
        } catch (MalformedSAMLMetadataXMLException e) {
            JsonObject res = new JsonObject();
            res.addProperty("status", "INVALID_METADATA_XML_ERROR");
            this.sendJsonResponse(200, res, resp);
        } catch (TenantOrAppNotFoundException | StorageQueryException e) {
            throw new ServletException(e);
        }
    }
}
