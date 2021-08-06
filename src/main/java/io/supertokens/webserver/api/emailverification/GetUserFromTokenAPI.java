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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.emailverification.EmailVerification;
import io.supertokens.emailverification.User;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

public class GetUserFromTokenAPI extends WebserverAPI {
    public GetUserFromTokenAPI(Main main) {
        super(main, RECIPE_ID.EMAIL_VERIFICATION.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/user/email";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String token = InputParser.getQueryParamOrThrowError(req, "token", false);

        try {
            Optional<User> maybeUser = EmailVerification.getUserFromToken(main, token);
            JsonObject result = new JsonObject();

            if (maybeUser.isEmpty()) {
                result.addProperty("status", "EMAIL_VERIFICATION_INVALID_TOKEN_ERROR");

                super.sendJsonResponse(200, result, resp);

                return;
            }

            JsonObject user = new JsonParser().parse(new Gson().toJson(maybeUser.get())).getAsJsonObject();

            result.addProperty("status", "OK");
            result.add("user", user);

            super.sendJsonResponse(200, result, resp);
        } catch(StorageQueryException | NoSuchAlgorithmException e) {
            throw new ServletException(e);
        }
    }
}
