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

package io.supertokens.webserver.api.emailverification;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class UnverifyEmailAPI extends WebserverAPI {
    private static final long serialVersionUID = -4077222295241424244L;

    public UnverifyEmailAPI(Main main) {
        super(main, RECIPE_ID.EMAIL_VERIFICATION.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/user/email/verify/remove";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        String email = InputParser.parseStringOrThrowError(input, "email", false);
        email = Utils.normaliseEmail(email);

        try {
            EmailVerification.unverifyEmail(this.getAppIdentifierWithStorage(req), userId, email);

            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");

            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }
}
