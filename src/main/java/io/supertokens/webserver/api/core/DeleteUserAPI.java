/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.webserver.api.core;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.users.DeleteUserResult;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

public class DeleteUserAPI extends WebserverAPI {
    public DeleteUserAPI(Main main) {
        super(main, "");
    }

    @Override
    public String getPath() {
        return "/users/remove";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject reqBody = InputParser.parseJsonObjectOrThrowError(req);
        String userId = InputParser.parseStringOrThrowError(reqBody, "userId", false);

        try {
            DeleteUserResult result = AuthRecipe.deleteUser(main, userId);

            JsonObject respBody = getResponseBody(result);

            super.sendJsonResponse(200, respBody, resp);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }
    }

    private JsonObject getResponseBody(DeleteUserResult result) {
        JsonObject body = new JsonObject();

        if (result.isSuccess) {
            body.addProperty("status", "OK");
        }

        body.addProperty("status", "NOT_OK");

        if (result.reason == DeleteUserResult.FailureReason.UNKNOWN_USER_ID) {
            body.addProperty("status", "UNKNOWN_USER_ID_ERROR");
        }

        return body;
    }
}
