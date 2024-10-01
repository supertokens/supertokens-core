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
import io.supertokens.StorageAndUserIdMapping;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.emailverification.User;
import io.supertokens.emailverification.exception.EmailVerificationInvalidTokenException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class VerifyEmailAPI extends WebserverAPI {
    private static final long serialVersionUID = -7529428297450682549L;

    public VerifyEmailAPI(Main main) {
        super(main, RECIPE_ID.EMAIL_VERIFICATION.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/user/email/verify";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String method = InputParser.parseStringOrThrowError(input, "method", false);
        String token = InputParser.parseStringOrThrowError(input, "token", false);
        assert method != null;
        assert token != null;

        // used to be according to logic in https://github.com/supertokens/supertokens-core/issues/141
        // but then changed slightly when extracting this into its own recipe

        if (!method.equals("token")) {
            throw new ServletException(new BadRequestException("Unsupported method for email verification"));
        }

        TenantIdentifier tenantIdentifier;
        Storage storage;
        try {
            tenantIdentifier = getTenantIdentifier(req);
            storage = this.getTenantStorage(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        try {
            User user = EmailVerification.verifyEmail(tenantIdentifier, storage, token);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("userId", user.id);
            result.addProperty("email", user.email);
            super.sendJsonResponse(200, result, resp);

        } catch (EmailVerificationInvalidTokenException e) {
            Logging.debug(main, tenantIdentifier, Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "EMAIL_VERIFICATION_INVALID_TOKEN_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | NoSuchAlgorithmException | StorageTransactionLogicException |
                 TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // API is app specific
        String userId = InputParser.getQueryParamOrThrowError(req, "userId", false);
        String email = InputParser.getQueryParamOrThrowError(req, "email", false);
        assert userId != null;
        assert email != null;

        try {
            AppIdentifier appIdentifier = getAppIdentifier(req);
            Storage storage;
            try {
                StorageAndUserIdMapping storageAndUserIdMapping =
                        enforcePublicTenantAndGetStorageAndUserIdMappingForAppSpecificApi(
                                req, userId, UserIdType.ANY, false);
                storage = storageAndUserIdMapping.storage;
            } catch (UnknownUserIdException e) {
                throw new IllegalStateException("should never happen");
            }
            boolean isVerified = EmailVerification.isEmailVerified(appIdentifier, storage, userId, email);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("isVerified", isVerified);
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }

    }

}
