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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.config.Config;
import io.supertokens.passwordless.Passwordless;
import io.supertokens.passwordless.Passwordless.ConsumeCodeResponse;
import io.supertokens.passwordless.Passwordless.CreateCodeResponse;
import io.supertokens.passwordless.exceptions.ExpiredUserInputCodeException;
import io.supertokens.passwordless.exceptions.IncorrectUserInputCodeException;
import io.supertokens.passwordless.exceptions.RestartFlowException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
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
        // Logic based on: https://app.code2flow.com/OFxcbh1FNLXd
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);

        String linkCode = null;
        String deviceId = null;
        String userInputCode = null;

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
            ConsumeCodeResponse consumeCodeResponse = Passwordless.consumeCode(main, deviceId, userInputCode, linkCode);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            JsonObject userJson = new JsonParser().parse(new Gson().toJson(consumeCodeResponse.user)).getAsJsonObject();

            // We are renaming intentionally here to make the API more user friendly.
            result.addProperty("createdNewUser", consumeCodeResponse.createdNewUser);
            result.add("user", userJson);

            super.sendJsonResponse(200, result, resp);
        } catch (RestartFlowException ex) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "RESTART_FLOW_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (ExpiredUserInputCodeException ex) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "EXPIRED_USER_INPUT_CODE");
            result.addProperty("failedCodeInputAttemptCount", ex.failedCodeInputs);
            result.addProperty("maximumCodeInputAttempts", ex.maximumCodeInputAttempts);
            super.sendJsonResponse(200, result, resp);
        } catch (IncorrectUserInputCodeException ex) {
            JsonObject result = new JsonObject();
            result.addProperty("status", "INCORRECT_USER_INPUT_CODE");
            result.addProperty("failedCodeInputAttemptCount", ex.failedCodeInputs);
            result.addProperty("maximumCodeInputAttempts", ex.maximumCodeInputAttempts);

            super.sendJsonResponse(200, result, resp);
        } catch (StorageTransactionLogicException | StorageQueryException | NoSuchAlgorithmException
                | InvalidKeyException e) {
            throw new ServletException(e);
        }
    }
}
