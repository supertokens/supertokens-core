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
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class DeleteCodesAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public DeleteCodesAPI(Main main) {
        super(main, RECIPE_ID.PASSWORDLESS.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/signinup/codes/remove";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        // Logic based on: https://app.code2flow.com/0493FY2rkyZm
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String email = InputParser.parseStringOrThrowError(input, "email", true);
        String phoneNumber = InputParser.parseStringOrThrowError(input, "phoneNumber", true);

        if (phoneNumber != null && email != null) {
            throw new ServletException(new BadRequestException("Please provide exactly one of email or phoneNumber"));
        }

        if (phoneNumber == null && email == null) {
            throw new ServletException(new BadRequestException("Please provide exactly one of email or phoneNumber"));
        }
        try {
            if (email != null) {
                email = Utils.normaliseEmail(email);
                Passwordless.removeCodesByEmail(this.getTenantIdentifierWithStorageFromRequest(req), email);
            } else {
                Passwordless.removeCodesByPhoneNumber(this.getTenantIdentifierWithStorageFromRequest(req), phoneNumber);
            }
        } catch (StorageTransactionLogicException | StorageQueryException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
        JsonObject result = new JsonObject();
        result.addProperty("status", "OK");

        super.sendJsonResponse(200, result, resp);
    }
}
