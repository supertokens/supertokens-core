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
import io.supertokens.emailpassword.exceptions.EmailAlreadyVerifiedException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class GenerateEmailVerificationTokenAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public GenerateEmailVerificationTokenAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/recipe/user/email/verify/token";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        assert userId != null;

        // logic according to https://github.com/supertokens/supertokens-core/issues/139

        try {
            String token = EmailPassword.generateEmailVerificationToken(super.main, userId);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("token", token);
            super.sendJsonResponse(200, result, resp);
        } catch (UnknownUserIdException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "UNKNOWN_USER_ID_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (EmailAlreadyVerifiedException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "EMAIL_ALREADY_VERIFIED_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new ServletException(e);
        }

    }
}
