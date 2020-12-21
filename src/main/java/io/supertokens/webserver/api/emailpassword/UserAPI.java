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
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class UserAPI extends WebserverAPI {

    private static final long serialVersionUID = -2225750492558064634L;

    public UserAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/recipe/user";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String userId = InputParser.getQueryParamOrThrowError(req, "userId", true);
        String email = InputParser.getQueryParamOrThrowError(req, "email", true);

        // logic according to https://github.com/supertokens/supertokens-core/issues/111

        if (userId != null && email != null) {
            throw new ServletException(new BadRequestException("Please provide only one of userId or email"));
        }

        if (userId == null && email == null) {
            throw new ServletException(new BadRequestException("Please provide one of userId or email"));
        }

        try {
            User user = null;
            if (userId != null) {
                user = EmailPassword.getUserUsingId(main, userId);
            } else {
                String normalisedEmail = Utils.normaliseEmail(email);
                user = EmailPassword.getUserUsingEmail(main, normalisedEmail);
            }

            if (user == null) {
                JsonObject result = new JsonObject();
                result.addProperty("status", userId != null ? "UNKNOWN_USER_ID_ERROR" : "UNKNOWN_EMAIL_ERROR");
                super.sendJsonResponse(200, result, resp);
            } else {
                JsonObject result = new JsonObject();
                result.addProperty("status", "OK");
                result.add("user", new JsonParser().parse(new Gson().toJson(user)).getAsJsonObject());
                super.sendJsonResponse(200, result, resp);
            }

        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }

    }
}
