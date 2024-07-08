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
import io.supertokens.emailverification.exception.EmailAlreadyVerifiedException;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class GenerateEmailVerificationTokenAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public GenerateEmailVerificationTokenAPI(Main main) {
        super(main, RECIPE_ID.EMAIL_VERIFICATION.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/user/email/verify/token";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        String email = InputParser.parseStringOrThrowError(input, "email", false);
        assert userId != null;
        assert email != null;
        email = Utils.normaliseEmail(email);

        TenantIdentifier tenantIdentifier;
        Storage storage;
        try {
            tenantIdentifier = getTenantIdentifier(req);
            storage = getTenantStorage(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        // used to be according to logic according to https://github.com/supertokens/supertokens-core/issues/139
        // but then changed slightly when extracting this into its own recipe

        try {
            String token = EmailVerification.generateEmailVerificationToken(tenantIdentifier, storage, super.main,
                    userId, email);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("token", token);
            super.sendJsonResponse(200, result, resp);
        } catch (EmailAlreadyVerifiedException e) {
            Logging.debug(main, tenantIdentifier, Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "EMAIL_ALREADY_VERIFIED_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | NoSuchAlgorithmException | InvalidKeySpecException |
                 TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

    }
}
