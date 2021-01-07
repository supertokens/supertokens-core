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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.User;
import io.supertokens.emailpassword.exceptions.EmailVerificationInvalidTokenException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class VerifyEmailAPI extends WebserverAPI {
    private static final long serialVersionUID = -7529428297450682549L;

    public VerifyEmailAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/recipe/user/email/verify";
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String method = InputParser.parseStringOrThrowError(input, "method", false);
        String token = InputParser.parseStringOrThrowError(input, "token", false);
        assert method != null;
        assert token != null;

        // logic according to https://github.com/supertokens/supertokens-core/issues/141

        if (!method.equals("token")) {
            throw new ServletException(new BadRequestException("Unsupported method for email verification"));
        }

        try {
            User user = EmailPassword.verifyEmail(super.main, token);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.add("user", new JsonParser().parse(new Gson().toJson(user)).getAsJsonObject());
            super.sendJsonResponse(200, result, resp);

        } catch (EmailVerificationInvalidTokenException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "EMAIL_VERIFICATION_INVALID_TOKEN_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | NoSuchAlgorithmException | StorageTransactionLogicException e) {
            throw new ServletException(e);
        }

    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String userId = InputParser.getQueryParamOrThrowError(req, "userId", false);

        try {
            boolean isVerified = EmailPassword.isEmailVerified(super.main, userId);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("isVerified", isVerified);
            super.sendJsonResponse(200, result, resp);

        } catch (UnknownUserIdException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "UNKNOWN_USER_ID_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }

    }

}
