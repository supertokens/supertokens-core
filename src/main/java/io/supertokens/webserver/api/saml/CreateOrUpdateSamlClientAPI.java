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
import java.security.cert.CertificateException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.saml.SAMLClient;
import io.supertokens.pluginInterface.saml.exception.DuplicateEntityIdException;
import io.supertokens.saml.SAML;
import io.supertokens.saml.SAMLAttributeMapping;
import io.supertokens.saml.exceptions.IDPInitiatedLoginRequiresACSURLsException;
import io.supertokens.saml.exceptions.InvalidAttributeMappingException;
import io.supertokens.saml.exceptions.MalformedSAMLMetadataXMLException;
import io.supertokens.saml.exceptions.SAMLClientFieldRequiredException;
import io.supertokens.utils.SemVer;
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
        String defaultRedirectURI = InputParser.parseStringOrThrowError(input, "defaultRedirectURI", false);
        JsonArray redirectURIs = InputParser.parseArrayOrThrowError(input, "redirectURIs", false);

        if (redirectURIs.size() == 0) {
            throw new ServletException(new BadRequestException("redirectURIs is required in the input"));
        }

        // Optional ACS allow-list and attribute mapping. Whether each key was sent is recorded separately from its
        // value: once a client has either field set, an update at CDI 5.8 or newer must send it (null, [] or {}
        // clear it); below 5.8 leaving it out keeps the stored value.
        boolean acsURLsPresent = input.has("acsURLs");
        JsonArray acsURLs = input.has("acsURLs") && input.get("acsURLs").isJsonNull()
                ? null
                : InputParser.parseArrayOrThrowError(input, "acsURLs", true);
        if (acsURLs != null && acsURLs.size() > 0 && !SAML.hasACSURLs(acsURLs)) {
            throw new ServletException(new BadRequestException("acsURLs must only contain non-empty strings"));
        }

        boolean attributeMappingPresent = input.has("attributeMapping");
        JsonObject attributeMapping;
        try {
            attributeMapping = SAMLAttributeMapping.parse(input.get("attributeMapping"));
        } catch (InvalidAttributeMappingException e) {
            throw new ServletException(new BadRequestException(e.getMessage()));
        }

        String metadataXML = InputParser.parseStringOrThrowError(input, "metadataXML", false);

        Boolean allowIDPInitiatedLogin = InputParser.parseBooleanOrThrowError(input, "allowIDPInitiatedLogin", true);
        Boolean enableRequestSigning = InputParser.parseBooleanOrThrowError(input, "enableRequestSigning", true);

        if (allowIDPInitiatedLogin == null) {
            allowIDPInitiatedLogin = false;
        }

        if (enableRequestSigning == null) {
            enableRequestSigning = true;
        }

        try {
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(metadataXML);
            metadataXML = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ServletException(new BadRequestException("metadataXML does not have a valid SAML metadata"));
        }

        if (clientId == null) {
            clientId = "st_saml_" + Utils.getUUID();
        }

        try {
            SAMLClient client = SAML.createOrUpdateSAMLClient(
                    main, getTenantIdentifier(req), getTenantStorage(req), clientId, clientSecret, defaultRedirectURI,
                    redirectURIs, metadataXML, allowIDPInitiatedLogin, enableRequestSigning, acsURLs, acsURLsPresent,
                    attributeMapping, attributeMappingPresent,
                    getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_8));
            JsonObject res = client.toJson();
            res.addProperty("status", "OK");
            this.sendJsonResponse(200, res, resp);
        } catch (SAMLClientFieldRequiredException e) {
            throw new ServletException(new BadRequestException(e.getMessage()));
        } catch (IDPInitiatedLoginRequiresACSURLsException e) {
            throw new ServletException(new BadRequestException(
                    "acsURLs must contain at least one URL when allowIDPInitiatedLogin is true"));
        } catch (DuplicateEntityIdException e) {
            JsonObject res = new JsonObject();
            res.addProperty("status", "DUPLICATE_IDP_ENTITY_ERROR");
            this.sendJsonResponse(200, res, resp);
        } catch (MalformedSAMLMetadataXMLException | CertificateException e) {
            throw new ServletException(new BadRequestException("metadataXML does not have a valid SAML metadata"));
        } catch (TenantOrAppNotFoundException | StorageQueryException | FeatureNotEnabledException e) {
            throw new ServletException(e);
        }
    }
}
