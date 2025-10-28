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

import org.opensaml.core.xml.io.UnmarshallingException;

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.saml.SAML;
import io.supertokens.saml.exceptions.IDPInitiatedLoginDisallowedException;
import io.supertokens.saml.exceptions.InvalidClientException;
import io.supertokens.saml.exceptions.InvalidRelayStateException;
import io.supertokens.saml.exceptions.SAMLResponseVerificationFailedException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.shibboleth.utilities.java.support.xml.XMLParserException;

public class HandleSamlCallbackAPI extends WebserverAPI {

    public HandleSamlCallbackAPI(Main main) {
        super(main, "saml");
    }

    @Override
    public String getPath() {
        return "/recipe/saml/callback";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String samlResponse = InputParser.parseStringOrThrowError(input, "samlResponse", false);
        String relayState = InputParser.parseStringOrThrowError(input, "relayState", true);

        try {
            String redirectURI = SAML.handleCallback(
                    main,
                    getTenantIdentifier(req),
                    getTenantStorage(req),
                    samlResponse, relayState
            );

            JsonObject res = new JsonObject();
            res.addProperty("status", "OK");
            res.addProperty("redirectURI", redirectURI);
            super.sendJsonResponse(200, res, resp);
        
        } catch (InvalidRelayStateException e) {
            JsonObject res = new JsonObject();
            res.addProperty("status", "INVALID_RELAY_STATE_ERROR");
            super.sendJsonResponse(200, res, resp);
        } catch (InvalidClientException e) {
            JsonObject res = new JsonObject();
            res.addProperty("status", "INVALID_CLIENT_ERROR");
            super.sendJsonResponse(200, res, resp);
        } catch (SAMLResponseVerificationFailedException e) {
            JsonObject res = new JsonObject();
            res.addProperty("status", "SAML_RESPONSE_VERIFICATION_FAILED_ERROR");
            super.sendJsonResponse(200, res, resp);

        } catch (IDPInitiatedLoginDisallowedException e) {
            JsonObject res = new JsonObject();
            res.addProperty("status", "IDP_LOGIN_DISALLOWED_ERROR");
            super.sendJsonResponse(200, res, resp);

        } catch (UnmarshallingException | XMLParserException e) {
            throw new ServletException(new BadRequestException("Invalid or malformed SAML response input"));

        } catch (TenantOrAppNotFoundException | StorageQueryException | CertificateException e) {
            throw new ServletException(e);
        }
    }
}
