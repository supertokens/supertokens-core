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

import io.supertokens.Main;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class UsersAPI extends WebserverAPI {

    private static final long serialVersionUID = -2225750492558064634L;

    public UsersAPI(Main main) {
        super(main);
    }

    @Override
    public String getPath() {
        return "/recipe/users";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String userId = InputParser.getQueryParamOrThrowError(req, "userId", true);
        String email = InputParser.getQueryParamOrThrowError(req, "email", true);

        String paginationToken = InputParser.getQueryParamOrThrowError(req, "paginationToken", true);
        Integer limit = InputParser.getIntQueryParamOrThrowError(req, "limit", true);
        String timeJoinedOrder = InputParser.getQueryParamOrThrowError(req, "timeJoinedOrder", true);

        // logic according to TODO

        if (timeJoinedOrder != null) {
            if (!timeJoinedOrder.equals("ASC") && !timeJoinedOrder.equals("DESC")) {
                // TODO: throw bad input
            }
        } else {
            timeJoinedOrder = "ASC";
        }

        if (limit != null) {
            if (limit > 1000) {
                // TODO: throw bad request
            }
        } else {
            limit = 100;
        }


        //  try {
        // TODO:
//
//            User user = null;
//            if (userId != null) {
//                user = EmailPassword.getUserUsingId(main, userId);
//            } else {
//                String normalisedEmail = Utils.normaliseEmail(email);
//                user = EmailPassword.getUserUsingEmail(main, normalisedEmail);
//            }
//
//            if (user == null) {
//                JsonObject result = new JsonObject();
//                result.addProperty("status", userId != null ? "UNKNOWN_USER_ID_ERROR" : "UNKNOWN_EMAIL_ERROR");
//                super.sendJsonResponse(200, result, resp);
//            } else {
//                JsonObject result = new JsonObject();
//                result.addProperty("status", "OK");
//                JsonObject userJson = new JsonParser().parse(new Gson().toJson(user)).getAsJsonObject();
//                if (super.getVersionFromRequest(req).equals("2.4")) {
//                    userJson.remove("timeJoined");
//                }
//                result.add("user", userJson);
//                super.sendJsonResponse(200, result, resp);
//            }

        // } catch (StorageQueryException e) {
        //   throw new ServletException(e);
        //  }

    }
}
