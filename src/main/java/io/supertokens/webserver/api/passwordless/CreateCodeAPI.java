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
import io.supertokens.config.Config;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.Passwordless.CreateCodeResponse;
import io.supertokens.passwordless.exceptions.RestartFlowException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.passwordless.exception.DuplicateLinkCodeHashException;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class CreateCodeAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public CreateCodeAPI(Main main) {
        super(main, RECIPE_ID.PASSWORDLESS.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/signinup/code";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String email = null;
        String phoneNumber = null;
        String deviceId = null;
        if (input.has("email")) {
            if (input.has("phoneNumber") || input.has("deviceId")) {
                throw new ServletException(
                        new BadRequestException("Please provide exactly one of email, phoneNumber or deviceId"));
            }
            email = Utils.normaliseEmail(InputParser.parseStringOrThrowError(input, "email", false));
        } else if (input.has("phoneNumber")) {
            if (input.has("email") || input.has("deviceId")) {
                throw new ServletException(
                        new BadRequestException("Please provide exactly one of email, phoneNumber or deviceId"));
            }
            phoneNumber = InputParser.parseStringOrThrowError(input, "phoneNumber", false);
        } else if (input.has("deviceId")) {
            if (input.has("email") || input.has("phoneNumber")) {
                throw new ServletException(
                        new BadRequestException("Please provide exactly one of email, phoneNumber or deviceId"));
            }
            deviceId = InputParser.parseStringOrThrowError(input, "deviceId", false);
        } else {
            throw new ServletException(
                    new BadRequestException("Please provide exactly one of email, phoneNumber or deviceId"));
        }

        String userInputCode = InputParser.parseStringOrThrowError(input, "userInputCode", true);

        try {
            CreateCodeResponse createCodeResponse = Passwordless.createCode(main, email, phoneNumber, deviceId,
                    userInputCode);
            long passwordlessCodeLifetime = Config.getConfig(main).getPasswordlessCodeLifetime();

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");

            // We are renaming intentionally here to make the API more user friendly.
            result.addProperty("loginAttemptId", createCodeResponse.deviceIdHash);
            result.addProperty("codeId", createCodeResponse.codeId);
            result.addProperty("deviceId", createCodeResponse.deviceId);
            result.addProperty("userInputCode", createCodeResponse.userInputCode);
            result.addProperty("linkCode", createCodeResponse.linkCode);
            result.addProperty("timeCreated", createCodeResponse.timeCreated);
            result.addProperty("codeLifetime", passwordlessCodeLifetime);

            super.sendJsonResponse(200, result, resp);
        } catch (RestartFlowException ex) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "RESTART_FLOW_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (DuplicateLinkCodeHashException ex) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "USER_INPUT_CODE_ALREADY_USED_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new ServletException(e);
        }
    }
}
