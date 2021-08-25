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
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Password cannot be an empty string"));
        }

        try {
            EmailPassword.resetPassword(super.main, token, newPassword);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);

        } catch (ResetPasswordInvalidTokenException e) {
            Logging.debug(main, Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "RESET_PASSWORD_INVALID_TOKEN_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | NoSuchAlgorithmException | StorageTransactionLogicException e) {
            throw new ServletException(e);
        }

    }

}
