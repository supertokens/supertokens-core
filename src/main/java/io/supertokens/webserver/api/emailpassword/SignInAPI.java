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
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
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

        TenantIdentifier tenantIdentifier;
        Storage storage;
        try {
            tenantIdentifier = getTenantIdentifier(req);
            storage = getTenantStorage(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        try {
            io.supertokens.webserver.api.emailpassword.Utils.assertIfEmailPasswordIsEnabledForTenant(main,
                    tenantIdentifier, getVersionFromRequest(req));

            AuthRecipeUserInfo user = EmailPassword.signIn(tenantIdentifier, storage, super.main, normalisedEmail,
                    password);
            io.supertokens.useridmapping.UserIdMapping.populateExternalUserIdForUsers(
                    tenantIdentifier.toAppIdentifier(), storage, new AuthRecipeUserInfo[]{user});

            ActiveUsers.updateLastActive(tenantIdentifier.toAppIdentifier(), main,
                    user.getSupertokensUserId()); // use the internal user id

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            JsonObject userJson = getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v4_0) ? user.toJson() :
                    user.toJsonWithoutAccountLinking();
            if (getVersionFromRequest(req).lesserThan(SemVer.v3_0)) {
                userJson.remove("tenantIds");
            }
            result.add("user", userJson);
            if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v4_0)) {
                for (LoginMethod loginMethod : user.loginMethods) {
                    if (loginMethod.recipeId.equals(RECIPE_ID.EMAIL_PASSWORD) &&
                            normalisedEmail.equals(loginMethod.email)) {
                        result.addProperty("recipeUserId", loginMethod.getSupertokensOrExternalUserId());
                        break;
                    }
                }
            }

            super.sendJsonResponse(200, result, resp);

        } catch (WrongCredentialsException e) {
            Logging.debug(main, tenantIdentifier, Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "WRONG_CREDENTIALS_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        }

    }
}
