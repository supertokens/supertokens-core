/*
 *    Copyright (c) 2020, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.passwordless;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class DeleteCodeAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public DeleteCodeAPI(Main main) {
        super(main, RECIPE_ID.PASSWORDLESS.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/signinup/code/remove";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        // Logic based on: https://app.code2flow.com/DDhe9U1rsFsQ
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String codeId = InputParser.parseStringOrThrowError(
                input, "codeId", getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_0));
        String deviceIdHash = null;
        if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_0)) {
            deviceIdHash = InputParser.parseStringOrThrowError(input, "preAuthSessionId", true);
        }

        if (codeId == null && deviceIdHash == null) {
            throw new ServletException(new BadRequestException("Please provide either 'codeId' or 'preAuthSessionId'"));
        }

        if (codeId != null && deviceIdHash != null) {
            throw new ServletException(new BadRequestException("Please provide only one of 'codeId' or " +
                    "'preAuthSessionId'"));
        }

        try {
            if (codeId != null) {
                Passwordless.removeCode(getTenantIdentifier(req), getTenantStorage(req), codeId);
            } else {
                Passwordless.removeDevice(getTenantIdentifier(req), getTenantStorage(req),
                        deviceIdHash);
            }

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");

            super.sendJsonResponse(200, result, resp);
        } catch (StorageTransactionLogicException | StorageQueryException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }
}
