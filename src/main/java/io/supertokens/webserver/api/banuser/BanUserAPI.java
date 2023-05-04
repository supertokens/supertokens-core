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

package io.supertokens.webserver.api.banuser;

import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.ban.BannedUser;
import io.supertokens.output.Logging;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.ban.exceptions.DuplicateUserIdException;
import io.supertokens.pluginInterface.ban.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class BanUserAPI extends WebserverAPI {
    private static final long serialVersionUID = 7796391095386009705L;

    public BanUserAPI(Main main) {
        super(main, RECIPE_ID.BAN_USER.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/ban/user";
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String userId = InputParser.parseStringOrThrowError(input, "userId", false);
        assert userId != null;


        if (userId.equals("")) {
            throw new ServletException(new WebserverAPI.BadRequestException("UserId cannot be an empty string"));
        }

        try {
            BannedUser.insertBannedUser(super.main, userId);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            super.sendJsonResponse(200, result, resp);

        } catch (DuplicateUserIdException e) {
            Logging.debug(main, Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "USER_ALREADY_BANNED_ERROR");
            super.sendJsonResponse(200, result, resp);
        } catch (UnknownUserIdException e ){
            Logging.debug(main, Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "USER_ID_INCORRECT_ERROR");
            super.sendJsonResponse(400, result, resp);
        }
        catch (StorageQueryException e) {
            throw new ServletException(e);
        }

    }

    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String userId = InputParser.getQueryParamOrThrowError(req, "userId", false);
        // normalize role
        userId = userId.trim();
        if (userId.length() == 0) {
            throw new ServletException(
                    new WebserverAPI.BadRequestException("Field name 'userId' cannot be an empty String"));
        }

        try {
            boolean isBanned = BannedUser.isUserBanned(super.main, userId);

            JsonObject result = new JsonObject();
            result.addProperty("status", isBanned);
            super.sendJsonResponse(200, result, resp);

        } catch (UnknownUserIdException e ){
            Logging.debug(main, Utils.exceptionStacktraceToString(e));
            JsonObject result = new JsonObject();
            result.addProperty("status", "USER_ID_INCORRECT_ERROR");
            super.sendJsonResponse(200, result, resp);
        }
        catch (StorageQueryException e) {
            throw new ServletException(e);
        }

    }

}
