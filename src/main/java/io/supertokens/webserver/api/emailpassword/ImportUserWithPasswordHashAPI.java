/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
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
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serial;

public class ImportUserWithPasswordHashAPI extends WebserverAPI {
    @Serial
    private static final long serialVersionUID = 201025871860374391L;

    public ImportUserWithPasswordHashAPI(Main main) {
        super(main, RECIPE_ID.EMAIL_PASSWORD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/user/passwordhash/import";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String email = InputParser.parseStringOrThrowError(input, "email", false);
        String passwordHash = InputParser.parseStringOrThrowError(input, "passwordHash", false);
        assert passwordHash != null;
        assert email != null;

        // logic according to https://github.com/supertokens/supertokens-core/issues/101

        String normalisedEmail = Utils.normaliseEmail(email);

        if (passwordHash.equals("")) {
            throw new ServletException(new WebserverAPI.BadRequestException("Password hash cannot be an empty string"));
        }

        try {
            EmailPassword.ImportUserResponse importUserResponse = EmailPassword
                    .importUserWithPasswordHashOrUpdatePasswordHashIfUserExists(main, normalisedEmail, passwordHash);
            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            JsonObject userJson = new JsonParser().parse(new Gson().toJson(importUserResponse.user)).getAsJsonObject();
            response.add("user", userJson);
            response.addProperty("didUserAlreadyExist", importUserResponse.didUserAlreadyExist);
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }

    }
}
