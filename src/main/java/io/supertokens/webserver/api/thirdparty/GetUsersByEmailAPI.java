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

package io.supertokens.webserver.api.thirdparty;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.thirdparty.UserInfo;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class GetUsersByEmailAPI extends WebserverAPI {
    private static final long serialVersionUID = -4413719941975228004L;

    public GetUsersByEmailAPI(Main main) {
        super(main, RECIPE_ID.THIRD_PARTY.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/users/by-email";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            String email = InputParser.getQueryParamOrThrowError(req, "email", false);

            UserInfo[] users = ThirdParty.getUsersByEmail(super.main, email);

            // return the externalUserId if a mapping exists for a user
            for (int i = 0; i < users.length; i++) {
                // we intentionally do not use the function that accepts an array of user IDs to get the mapping cause
                // this is simpler to use, and cause there shouldn't be that many userIds per email anyway
                io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                        .getUserIdMapping(super.main, users[i].id, UserIdType.ANY);
                if (userIdMapping != null) {
                    users[i].id = userIdMapping.externalUserId;
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            JsonArray usersJson = new JsonParser().parse(new Gson().toJson(users)).getAsJsonArray();
            result.add("users", usersJson);

            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }
    }
}
