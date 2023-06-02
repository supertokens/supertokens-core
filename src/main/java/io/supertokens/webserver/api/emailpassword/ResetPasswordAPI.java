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

package io.supertokens.webserver.api.emailpassword;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.ResetPasswordInvalidTokenException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class ResetPasswordAPI extends WebserverAPI {
    private static final long serialVersionUID = -7529428297450682549L;

    public ResetPasswordAPI(Main main) {
        super(main, RECIPE_ID.EMAIL_PASSWORD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/user/password/reset";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String method = InputParser.parseStringOrThrowError(input, "method", false);
        String token = InputParser.parseStringOrThrowError(input, "token", false);
        String newPassword = InputParser.parseStringOrThrowError(input, "newPassword", false);
        assert newPassword != null;
        assert method != null;
        assert token != null;

        // logic according to https://github.com/supertokens/supertokens-core/issues/109

        if (!method.equals("token")) {
            throw new ServletException(new WebserverAPI.BadRequestException("Unsupported method for password reset"));
        }

        if (newPassword.equals("")) {
            throw new ServletException(new WebserverAPI.BadRequestException("Password cannot be an empty string"));
        }

        TenantIdentifierWithStorage tenantIdentifierWithStorage = null;
        try {
            tenantIdentifierWithStorage = getTenantIdentifierWithStorageFromRequest(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        try {
            String userId = EmailPassword.resetPassword(tenantIdentifierWithStorage, super.main, token, newPassword);

            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping.getUserIdMapping(
                    tenantIdentifierWithStorage.toAppIdentifierWithStorage(), userId, UserIdType.SUPERTOKENS);

            // if userIdMapping exists, pass the externalUserId to the response
            if (userIdMapping != null) {
                userId = userIdMapping.externalUserId;
            }

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");

            if (!(super.getVersionFromRequest(req).equals(SemVer.v2_7) || super.getVersionFromRequest(req).equals(SemVer.v2_8)
                    || super.getVersionFromRequest(req).equals(SemVer.v2_9) || super.getVersionFromRequest(req).equals(SemVer.v2_10)
                    || super.getVersionFromRequest(req).equals(SemVer.v2_11))) {
                // >= 2.12
                result.addProperty("userId", userId);
            }

            super.sendJsonResponse(200, result, resp);

        } catch (ResetPasswordInvalidTokenException e) {
            Logging.debug(main, tenantIdentifierWithStorage, Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "RESET_PASSWORD_INVALID_TOKEN_ERROR");
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException | NoSuchAlgorithmException | StorageTransactionLogicException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

    }

}
