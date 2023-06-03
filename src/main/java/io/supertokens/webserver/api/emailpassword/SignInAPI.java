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
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class SignInAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public SignInAPI(Main main) {
        super(main, RECIPE_ID.EMAIL_PASSWORD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/signin";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String email = InputParser.parseStringOrThrowError(input, "email", false);
        String password = InputParser.parseStringOrThrowError(input, "password", false);
        assert password != null;
        assert email != null;

        // logic according to https://github.com/supertokens/supertokens-core/issues/104

        String normalisedEmail = Utils.normaliseEmail(email);

        TenantIdentifierWithStorage tenantIdentifierWithStorage = null;
        try {
            tenantIdentifierWithStorage = getTenantIdentifierWithStorageFromRequest(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        try {
            UserInfo user = EmailPassword.signIn(tenantIdentifierWithStorage, super.main, normalisedEmail, password);

            ActiveUsers.updateLastActive(tenantIdentifierWithStorage.toAppIdentifierWithStorage(), main, user.id); // use the internal user id

            // if a userIdMapping exists, pass the externalUserId to the response
            UserIdMapping userIdMapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(
                    tenantIdentifierWithStorage.toAppIdentifierWithStorage(), user.id, UserIdType.SUPERTOKENS);

            if (userIdMapping != null) {
                user.id = userIdMapping.externalUserId;
            }

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            JsonObject userJson = new JsonParser().parse(new Gson().toJson(user)).getAsJsonObject();
            if (getVersionFromRequest(req).lesserThan(SemVer.v3_0)) {
                userJson.remove("tenantIds");
            }
            result.add("user", userJson);
            super.sendJsonResponse(200, result, resp);

        } catch (WrongCredentialsException e) {
            Logging.debug(main, tenantIdentifierWithStorage, Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "WRONG_CREDENTIALS_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }

    }
}
