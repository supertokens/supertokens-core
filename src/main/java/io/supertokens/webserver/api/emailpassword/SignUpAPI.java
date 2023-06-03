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

import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SignUpAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public SignUpAPI(Main main) {
        super(main, RECIPE_ID.EMAIL_PASSWORD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/signup";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String email = InputParser.parseStringOrThrowError(input, "email", false);
        String password = InputParser.parseStringOrThrowError(input, "password", false);
        assert password != null;
        assert email != null;

        // logic according to https://github.com/supertokens/supertokens-core/issues/101

        String normalisedEmail = Utils.normaliseEmail(email);

        if (password.equals("")) {
            throw new ServletException(new WebserverAPI.BadRequestException("Password cannot be an empty string"));
        }

        TenantIdentifier tenantIdentifier = null;
        try {
            tenantIdentifier = getTenantIdentifierWithStorageFromRequest(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        try {
            UserInfo user = EmailPassword.signUp(this.getTenantIdentifierWithStorageFromRequest(req), super.main, normalisedEmail, password);

            ActiveUsers.updateLastActive(this.getAppIdentifierWithStorage(req), main, user.id);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            JsonObject userJson = new JsonParser().parse(new Gson().toJson(user)).getAsJsonObject();

            if (getVersionFromRequest(req).lesserThan(SemVer.v3_0)) {
                userJson.remove("tenantIds");
            }

            result.add("user", userJson);
            super.sendJsonResponse(200, result, resp);

        } catch (DuplicateEmailException e) {
            Logging.debug(main, tenantIdentifier, Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "EMAIL_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }

    }
}
