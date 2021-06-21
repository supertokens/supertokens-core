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

package io.supertokens.webserver.api.thirdparty;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SignInUpAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public SignInUpAPI(Main main) {
        super(main, RECIPE_ID.THIRD_PARTY.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/signinup";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String thirdPartyId = InputParser.parseStringOrThrowError(input, "thirdPartyId", false);
        String thirdPartyUserId = InputParser.parseStringOrThrowError(input, "thirdPartyUserId", false);
        JsonObject emailObject = InputParser.parseJsonObjectOrThrowError(input, "email", false);
        String email = InputParser.parseStringOrThrowError(emailObject, "id", false);
        Boolean isEmailVerified = InputParser.parseBooleanOrThrowError(emailObject, "isVerified", false);

        assert thirdPartyId != null;
        assert thirdPartyUserId != null;
        assert email != null;
        assert isEmailVerified != null;

        // logic according to https://github.com/supertokens/supertokens-core/issues/190#issuecomment-774671873

        String normalisedEmail = Utils.normaliseEmail(email);

        try {
            ThirdParty.SignInUpResponse response = ThirdParty
                    .signInUp(super.main, thirdPartyId, thirdPartyUserId, normalisedEmail, isEmailVerified);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("createdNewUser", response.createdNewUser);
            JsonObject userJson = new JsonParser().parse(new Gson().toJson(response.user)).getAsJsonObject();
            result.add("user", userJson);
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }

    }
}
