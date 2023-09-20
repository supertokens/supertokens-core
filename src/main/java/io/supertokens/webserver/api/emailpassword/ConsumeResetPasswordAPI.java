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
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class ConsumeResetPasswordAPI extends WebserverAPI {
    private static final long serialVersionUID = -7529428297450682549L;

    public ConsumeResetPasswordAPI(Main main) {
        super(main, RECIPE_ID.EMAIL_PASSWORD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/user/password/reset/token/consume";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String token = InputParser.parseStringOrThrowError(input, "token", false);
        assert token != null;

        TenantIdentifierWithStorage tenantIdentifierWithStorage = null;
        try {
            tenantIdentifierWithStorage = getTenantIdentifierWithStorageFromRequest(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        try {
            EmailPassword.ConsumeResetPasswordTokenResult result = EmailPassword.consumeResetPasswordToken(
                    tenantIdentifierWithStorage, token);

            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping.getUserIdMapping(
                    tenantIdentifierWithStorage.toAppIdentifierWithStorage(), result.userId, UserIdType.SUPERTOKENS);

            // if userIdMapping exists, pass the externalUserId to the response
            if (userIdMapping != null) {
                result.userId = userIdMapping.externalUserId;
            }

            JsonObject resultJson = new JsonObject();
            resultJson.addProperty("status", "OK");
            resultJson.addProperty("userId", result.userId);
            resultJson.addProperty("email", result.email);

            super.sendJsonResponse(200, resultJson, resp);

        } catch (ResetPasswordInvalidTokenException e) {
            Logging.debug(main, tenantIdentifierWithStorage, Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "RESET_PASSWORD_INVALID_TOKEN_ERROR");
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException | NoSuchAlgorithmException | StorageTransactionLogicException |
                 TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

    }

}
