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

import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.emailpassword.exceptions.EmailChangeNotAllowedException;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Objects;

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
        // API is tenant specific
        if (super.getVersionFromRequest(req).equals(SemVer.v2_7)) {
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

            // logic according to
            // https://github.com/supertokens/supertokens-core/issues/190#issuecomment-774671873

            email = Utils.normaliseEmail(email);

            try {
                TenantIdentifier tenantIdentifier = getTenantIdentifier(req);

                Storage storage = getTenantStorage(req);
                ThirdParty.SignInUpResponse response = ThirdParty.signInUp2_7(
                        tenantIdentifier, storage,
                        thirdPartyId, thirdPartyUserId, email, isEmailVerified);
                UserIdMapping.populateExternalUserIdForUsers(tenantIdentifier.toAppIdentifier(), storage,
                        new AuthRecipeUserInfo[]{response.user});

                ActiveUsers.updateLastActive(tenantIdentifier.toAppIdentifier(), main,
                        response.user.getSupertokensUserId());

                JsonObject result = new JsonObject();
                result.addProperty("status", "OK");
                result.addProperty("createdNewUser", response.createdNewUser);
                JsonObject userJson = response.user.toJsonWithoutAccountLinking();

                if (getVersionFromRequest(req).lesserThan(SemVer.v3_0)) {
                    userJson.remove("tenantIds");
                }

                result.add("user", userJson);
                if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v4_0)) {
                    for (LoginMethod loginMethod : response.user.loginMethods) {
                        if (loginMethod.recipeId.equals(RECIPE_ID.THIRD_PARTY)
                                && Objects.equals(loginMethod.thirdParty.id, thirdPartyId)
                                && Objects.equals(loginMethod.thirdParty.userId, thirdPartyUserId)) {
                            result.addProperty("recipeUserId", loginMethod.getSupertokensOrExternalUserId());
                            break;
                        }
                    }
                }

                super.sendJsonResponse(200, result, resp);

            } catch (StorageQueryException | TenantOrAppNotFoundException e) {
                throw new ServletException(e);
            }
        } else {
            JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
            String thirdPartyId = InputParser.parseStringOrThrowError(input, "thirdPartyId", false);
            String thirdPartyUserId = InputParser.parseStringOrThrowError(input, "thirdPartyUserId", false);
            JsonObject emailObject = InputParser.parseJsonObjectOrThrowError(input, "email", false);
            String email = InputParser.parseStringOrThrowError(emailObject, "id", false);

            // setting email verified behaviour is to be done only for CDI 4.0 onwards. version 3.0 and earlier
            // do not have this field
            Boolean isEmailVerified = false;
            if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v4_0)) {
                isEmailVerified = InputParser.parseBooleanOrThrowError(emailObject, "isVerified", false);
            }

            assert thirdPartyId != null;
            assert thirdPartyUserId != null;
            assert email != null;

            // logic according to
            // https://github.com/supertokens/supertokens-core/issues/190#issuecomment-774671873
            // and modifed according to
            // https://github.com/supertokens/supertokens-core/issues/295

            email = Utils.normaliseEmail(email);

            try {
                TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
                io.supertokens.webserver.api.thirdparty.Utils.assertIfThirdPartyIsEnabledForTenant(main,
                        tenantIdentifier, getVersionFromRequest(req));
                Storage storage = getTenantStorage(req);
                ThirdParty.SignInUpResponse response = ThirdParty.signInUp(
                        tenantIdentifier, storage, super.main, thirdPartyId, thirdPartyUserId,
                        email, isEmailVerified);
                UserIdMapping.populateExternalUserIdForUsers(tenantIdentifier.toAppIdentifier(), storage,
                        new AuthRecipeUserInfo[]{response.user});

                ActiveUsers.updateLastActive(tenantIdentifier.toAppIdentifier(), main,
                        response.user.getSupertokensUserId());

                JsonObject result = new JsonObject();
                result.addProperty("status", "OK");
                result.addProperty("createdNewUser", response.createdNewUser);
                JsonObject userJson =
                        getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v4_0) ? response.user.toJson() :
                                response.user.toJsonWithoutAccountLinking();

                if (getVersionFromRequest(req).lesserThan(SemVer.v3_0)) {
                    userJson.remove("tenantIds");
                }

                result.add("user", userJson);
                if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v4_0)) {
                    for (LoginMethod loginMethod : response.user.loginMethods) {
                        if (loginMethod.recipeId.equals(RECIPE_ID.THIRD_PARTY)
                                && Objects.equals(loginMethod.thirdParty.id, thirdPartyId)
                                && Objects.equals(loginMethod.thirdParty.userId, thirdPartyUserId)) {
                            result.addProperty("recipeUserId", loginMethod.getSupertokensOrExternalUserId());
                            break;
                        }
                    }
                }

                super.sendJsonResponse(200, result, resp);

            } catch (StorageQueryException | TenantOrAppNotFoundException | BadPermissionException e) {
                throw new ServletException(e);
            } catch (EmailChangeNotAllowedException e) {
                JsonObject result = new JsonObject();
                result.addProperty("status", "EMAIL_CHANGE_NOT_ALLOWED_ERROR");
                result.addProperty("reason", "Email already associated with another primary user.");
                super.sendJsonResponse(200, result, resp);
            }
        }

    }
}
