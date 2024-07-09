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

package io.supertokens.webserver.api.passwordless;

import com.google.gson.JsonObject;
import io.supertokens.ActiveUsers;
import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.Passwordless.ConsumeCodeResponse;
import io.supertokens.passwordless.exceptions.*;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.authRecipe.LoginMethod;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.utils.SemVer;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public class ConsumeCodeAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public ConsumeCodeAPI(Main main) {
        super(main, RECIPE_ID.PASSWORDLESS.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/signinup/code/consume";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is tenant specific
        // Logic based on: https://app.code2flow.com/OFxcbh1FNLXd
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String linkCode = null;
        String deviceId = null;
        String userInputCode = null;

        String deviceIdHash = InputParser.parseStringOrThrowError(input, "preAuthSessionId", false);

        if (input.has("linkCode")) {
            if (input.has("userInputCode") || input.has("deviceId")) {
                throw new ServletException(
                        new BadRequestException("Please provide exactly one of linkCode or deviceId+userInputCode"));
            }
            linkCode = InputParser.parseStringOrThrowError(input, "linkCode", false);
        } else if (input.has("userInputCode") && input.has("deviceId")) {
            deviceId = InputParser.parseStringOrThrowError(input, "deviceId", false);
            userInputCode = InputParser.parseStringOrThrowError(input, "userInputCode", false);
        } else {
            throw new ServletException(
                    new BadRequestException("Please provide exactly one of linkCode or deviceId+userInputCode"));
        }

        try {
            TenantIdentifier tenantIdentifier = getTenantIdentifier(req);
            io.supertokens.webserver.api.passwordless.Utils.assertIfPasswordlessIsEnabledForTenant(main,
                    tenantIdentifier, getVersionFromRequest(req));
            Storage storage = this.getTenantStorage(req);
            ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(
                    tenantIdentifier,
                    storage, main,
                    deviceId, deviceIdHash,
                    userInputCode, linkCode,
                    // From CDI version 4.0 onwards, the email verification will be set
                    getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v4_0));
            io.supertokens.useridmapping.UserIdMapping.populateExternalUserIdForUsers(
                    tenantIdentifier.toAppIdentifier(), storage,
                    new AuthRecipeUserInfo[]{consumeCodeResponse.user});

            ActiveUsers.updateLastActive(tenantIdentifier.toAppIdentifier(), main,
                    consumeCodeResponse.user.getSupertokensUserId());

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            JsonObject userJson =
                    getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v4_0) ? consumeCodeResponse.user.toJson() :
                            consumeCodeResponse.user.toJsonWithoutAccountLinking();

            if (getVersionFromRequest(req).lesserThan(SemVer.v3_0)) {
                userJson.remove("tenantIds");
            }

            result.addProperty("createdNewUser", consumeCodeResponse.createdNewUser);
            result.add("user", userJson);
            if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v4_0)) {
                for (LoginMethod loginMethod : consumeCodeResponse.user.loginMethods) {
                    if (loginMethod.recipeId.equals(RECIPE_ID.PASSWORDLESS)
                            && (consumeCodeResponse.email == null ||
                            Objects.equals(loginMethod.email, consumeCodeResponse.email))
                            && (consumeCodeResponse.phoneNumber == null ||
                            Objects.equals(loginMethod.phoneNumber, consumeCodeResponse.phoneNumber))) {
                        result.addProperty("recipeUserId", loginMethod.getSupertokensOrExternalUserId());
                        break;
                    }
                }
            }

            if (getVersionFromRequest(req).greaterThanOrEqualTo(SemVer.v5_0)) {
                JsonObject jsonDevice = new JsonObject();
                jsonDevice.addProperty("preAuthSessionId", consumeCodeResponse.consumedDevice.deviceIdHash);
                jsonDevice.addProperty("failedCodeInputAttemptCount",
                        consumeCodeResponse.consumedDevice.failedAttempts);

                if (consumeCodeResponse.consumedDevice.email != null) {
                    jsonDevice.addProperty("email", consumeCodeResponse.consumedDevice.email);
                }

                if (consumeCodeResponse.consumedDevice.phoneNumber != null) {
                    jsonDevice.addProperty("phoneNumber", consumeCodeResponse.consumedDevice.phoneNumber);
                }

                result.add("consumedDevice", jsonDevice);
            }

            super.sendJsonResponse(200, result, resp);
        } catch (RestartFlowException ex) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "RESTART_FLOW_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (ExpiredUserInputCodeException ex) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "EXPIRED_USER_INPUT_CODE_ERROR");
            result.addProperty("failedCodeInputAttemptCount", ex.failedCodeInputs);
            result.addProperty("maximumCodeInputAttempts", ex.maximumCodeInputAttempts);
            super.sendJsonResponse(200, result, resp);
        } catch (IncorrectUserInputCodeException ex) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "INCORRECT_USER_INPUT_CODE_ERROR");
            result.addProperty("failedCodeInputAttemptCount", ex.failedCodeInputs);
            result.addProperty("maximumCodeInputAttempts", ex.maximumCodeInputAttempts);

            super.sendJsonResponse(200, result, resp);
        } catch (DeviceIdHashMismatchException ex) {
            throw new ServletException(new BadRequestException("preAuthSessionId and deviceId doesn't match"));
        } catch (StorageTransactionLogicException | StorageQueryException | NoSuchAlgorithmException |
                 InvalidKeyException | TenantOrAppNotFoundException | BadPermissionException e) {
            throw new ServletException(e);
        } catch (Base64EncodingException ex) {
            throw new ServletException(new BadRequestException("Input encoding error in " + ex.source));
        }
    }
}
