/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.multitenancy;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.StorageAndUserIdMapping;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.AnotherPrimaryUserWithEmailAlreadyExistsException;
import io.supertokens.multitenancy.exception.AnotherPrimaryUserWithPhoneNumberAlreadyExistsException;
import io.supertokens.multitenancy.exception.AnotherPrimaryUserWithThirdPartyInfoAlreadyExistsException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicatePhoneNumberException;
import io.supertokens.pluginInterface.thirdparty.exception.DuplicateThirdPartyUserException;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class AssociateUserToTenantAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public AssociateUserToTenantAPI(Main main) {
        super(main, RECIPE_ID.MULTITENANCY.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/multitenancy/tenant/user";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String userId;

        if (getVersionFromRequest(req).lesserThan(SemVer.v4_0)) {
            userId = InputParser.parseStringOrThrowError(input, "userId", false);
        } else {
            userId = InputParser.parseStringOrThrowError(input, "recipeUserId", false);
        }
        // normalize userId
        userId = userId.trim();

        if (userId.length() == 0) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name 'userId' cannot be an empty String"));
        }

        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);

            StorageAndUserIdMapping storageAndUserIdMapping = getStorageAndUserIdMappingForTenantSpecificApi(
                    req, userId, UserIdType.ANY);
            if (storageAndUserIdMapping.userIdMapping != null) {
                userId = storageAndUserIdMapping.userIdMapping.superTokensUserId;
            }

            boolean addedToTenant = Multitenancy.addUserIdToTenant(main,
                    tenantIdentifier, storageAndUserIdMapping.storage, userId);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("wasAlreadyAssociated", !addedToTenant);
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException | TenantOrAppNotFoundException | FeatureNotEnabledException e) {
            throw new ServletException(e);

        } catch (UnknownUserIdException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "UNKNOWN_USER_ID_ERROR");
            super.sendJsonResponse(200, result, resp);

        } catch (DuplicateEmailException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "EMAIL_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, result, resp);

        } catch (DuplicatePhoneNumberException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "PHONE_NUMBER_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, result, resp);

        } catch (DuplicateThirdPartyUserException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "THIRD_PARTY_USER_ALREADY_EXISTS_ERROR");
            super.sendJsonResponse(200, result, resp);

        } catch (AnotherPrimaryUserWithEmailAlreadyExistsException |
                 AnotherPrimaryUserWithPhoneNumberAlreadyExistsException |
                 AnotherPrimaryUserWithThirdPartyInfoAlreadyExistsException e) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "ASSOCIATION_NOT_ALLOWED_ERROR");
            result.addProperty("reason", e.getMessage());
            super.sendJsonResponse(200, result, resp);
        }
    }
}
