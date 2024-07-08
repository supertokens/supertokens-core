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
import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.exceptions.*;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifier;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.passwordless.PasswordlessDevice;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class CheckCodeAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public CheckCodeAPI(Main main) {
        super(main, RECIPE_ID.PASSWORDLESS.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/signinup/code/check";
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
            PasswordlessDevice consumedDevice = Passwordless.checkCodeAndReturnDevice(
                    tenantIdentifier,
                    storage, main,
                    deviceId, deviceIdHash,
                    userInputCode, linkCode, false);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");

            JsonObject jsonDevice = new JsonObject();
            jsonDevice.addProperty("preAuthSessionId", consumedDevice.deviceIdHash);
            jsonDevice.addProperty("failedCodeInputAttemptCount", consumedDevice.failedAttempts);

            if (consumedDevice.email != null) {
                jsonDevice.addProperty("email", consumedDevice.email);
            }

            if (consumedDevice.phoneNumber != null) {
                jsonDevice.addProperty("phoneNumber", consumedDevice.phoneNumber);
            }

            result.add("consumedDevice", jsonDevice);

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