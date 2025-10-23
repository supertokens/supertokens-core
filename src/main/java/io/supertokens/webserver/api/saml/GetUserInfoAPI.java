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

import com.google.gson.JsonObject;

import io.supertokens.Main;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.saml.SAML;
import io.supertokens.saml.exceptions.InvalidCodeException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class GetUserInfoAPI extends WebserverAPI {

    public GetUserInfoAPI(Main main) {
        super(main, "saml");
    }

    @Override
    public String getPath() {
        return "/recipe/saml/user";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String accessToken = InputParser.parseStringOrThrowError(input, "accessToken", false);
        String clientId = InputParser.parseStringOrThrowError(input, "clientId", false);

        try {
            JsonObject userInfo = SAML.getUserInfo(
                    main,
                    getTenantIdentifier(req),
                    getTenantStorage(req),
                    accessToken,
                    clientId,
                    false
            );
            userInfo.addProperty("status", "OK");

            super.sendJsonResponse(200, userInfo, resp);
        } catch (InvalidCodeException e) {
            JsonObject res = new JsonObject();
            res.addProperty("status", "INVALID_TOKEN_ERROR");
            
            super.sendJsonResponse(200, res, resp);
        } catch (TenantOrAppNotFoundException | StorageQueryException | StorageTransactionLogicException e) {
            throw new ServletException(e);
        }
    }
}
