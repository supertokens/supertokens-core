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

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.config.CoreConfig;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.UnsupportedPasswordHashingFormatException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
        // API is tenant specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String email = InputParser.parseStringOrThrowError(input, "email", false);
        String passwordHash = InputParser.parseStringOrThrowError(input, "passwordHash", false);
        String hashingAlgorithmString = InputParser.parseStringOrThrowError(input, "hashingAlgorithm", true);

        assert passwordHash != null;
        assert email != null;

        // logic according to https://github.com/supertokens/supertokens-core/issues/101

        email = Utils.normaliseEmail(email);

        // normalise password hash
        passwordHash = passwordHash.trim();

        if (passwordHash.equals("")) {
            throw new ServletException(new WebserverAPI.BadRequestException("Password hash cannot be an empty string"));
        }

        CoreConfig.PASSWORD_HASHING_ALG passwordHashingAlgorithm = null;

        if (hashingAlgorithmString != null) {
            // normalise hashing algorithm string
            hashingAlgorithmString = hashingAlgorithmString.trim().toUpperCase();

            if (hashingAlgorithmString.equals("")) {
                throw new ServletException(
                        new WebserverAPI.BadRequestException("Hashing Algorithm cannot be an empty string"));
            }
            try {
                passwordHashingAlgorithm = CoreConfig.PASSWORD_HASHING_ALG.valueOf(hashingAlgorithmString);
            } catch (IllegalArgumentException e) {
                throw new ServletException(
                        new WebserverAPI.BadRequestException("Unsupported password hashing algorithm"));
            }
        }

        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            Storage storage = getTenantStorage(req);

            io.supertokens.webserver.api.emailpassword.Utils.assertIfEmailPasswordIsEnabledForTenant(main,
                    tenantIdentifier, getVersionFromRequest(req));

            EmailPassword.ImportUserResponse importUserResponse = EmailPassword.importUserWithPasswordHash(
                    tenantIdentifier, storage, main, email,
                    passwordHash, passwordHashingAlgorithm);
            UserIdMapping.populateExternalUserIdForUsers(tenantIdentifier.toAppIdentifier(), storage,
                    new AuthRecipeUserInfo[]{importUserResponse.user});
            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            JsonObject userJson =
                    getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v4_0) ? importUserResponse.user.toJson() :
                            importUserResponse.user.toJsonWithoutAccountLinking();

            if (getVersionFromRequest(req).lesserThan(SemVer.v3_0)) {
                userJson.remove("tenantIds");
            }

            response.add("user", userJson);
            response.addProperty("didUserAlreadyExist", importUserResponse.didUserAlreadyExist);
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException | StorageTransactionLogicException | TenantOrAppNotFoundException |
                 BadPermissionException e) {
            throw new ServletException(e);
        } catch (UnsupportedPasswordHashingFormatException e) {
            throw new ServletException(new WebserverAPI.BadRequestException(e.getMessage()));
        }
    }
}
