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
import io.supertokens.StorageAndUserIdMapping;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class GeneratePasswordResetTokenAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public GeneratePasswordResetTokenAPI(Main main) {
        super(main, RECIPE_ID.EMAIL_PASSWORD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/user/password/reset/token";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String userId = InputParser.parseStringOrThrowError(input, "userId", false);

        // logic according to https://github.com/supertokens/supertokens-core/issues/106
        TenantIdentifier tenantIdentifier;
        try {
            tenantIdentifier = getTenantIdentifier(req);
        } catch (TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }

        try {
            io.supertokens.webserver.api.emailpassword.Utils.assertIfEmailPasswordIsEnabledForTenant(main,
                    tenantIdentifier, getVersionFromRequest(req));

            StorageAndUserIdMapping storageAndUserIdMapping =
                    getStorageAndUserIdMappingForTenantSpecificApi(req, userId, UserIdType.ANY);
            // if a userIdMapping exists, pass the superTokensUserId to the generatePasswordResetToken
            if (storageAndUserIdMapping.userIdMapping != null) {
                userId = storageAndUserIdMapping.userIdMapping.superTokensUserId;
            }

            String token;
            if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v4_0)) {
                String email = InputParser.parseStringOrThrowError(input, "email", false);
                token = EmailPassword.generatePasswordResetToken(
                        tenantIdentifier, storageAndUserIdMapping.storage, super.main, userId, email);
            } else {
                token = EmailPassword.generatePasswordResetTokenBeforeCdi4_0(
                        tenantIdentifier, storageAndUserIdMapping.storage, super.main, userId);
            }

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("token", token);
            super.sendJsonResponse(200, result, resp);

        } catch (UnknownUserIdException e) {
            Logging.debug(main, tenantIdentifier, Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "UNKNOWN_USER_ID_ERROR");
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException | BadPermissionException | NoSuchAlgorithmException | InvalidKeySpecException |
                 TenantOrAppNotFoundException | BadRequestException e) {
            throw new ServletException(e);
        }

    }
}
