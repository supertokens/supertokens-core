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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.supertokens.Main;
import io.supertokens.UserPaginationToken;
import io.supertokens.emailpassword.User;
import io.supertokens.emailpassword.UserPaginationContainer;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.utils.Utils;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SignInUpAPI extends WebserverAPI {

    private static final long serialVersionUID = -4641988458637882374L;

    public SignInUpAPI(Main main) {
        super(main, ThirdParty.RECIPE_ID);
    }

    @Override
    public String getPath() {
        return "/recipe/signinup";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        JsonObject input = InputParser.parseJsonObjectOrThrowError(req);
        String thirdPartyId = InputParser.parseStringOrThrowError(input, "thirdPartyId", false);
        String thirdPartyUserId = InputParser.parseStringOrThrowError(input, "thirdPartyUserId", false);
        JsonObject emailObject = InputParser.parseJsonObjectOrThrowError(input, "email", false);
        String email = InputParser.parseStringOrThrowError(emailObject, "email", false);
        Boolean isEmailVerified = InputParser.parseBooleanOrThrowError(emailObject, "isVerified", false);

        assert thirdPartyId != null;
        assert thirdPartyUserId != null;
        assert email != null;
        assert isEmailVerified != null;

        // logic according to https://github.com/supertokens/supertokens-core/issues/190#issuecomment-774671873

        String normalisedEmail = Utils.normaliseEmail(email);

        try {
            ThirdParty.SignInUpResponse response = ThirdParty
                    .signInUp(super.main, thirdPartyId, thirdPartyUserId, normalisedEmail, isEmailVerified);

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            result.addProperty("createdNewUser", response.createdNewUser);
            JsonObject userJson = new JsonParser().parse(new Gson().toJson(response.user)).getAsJsonObject();
            result.add("user", userJson);
            super.sendJsonResponse(200, result, resp);

        } catch (StorageQueryException e) {
            throw new ServletException(e);
        }

    }

    public static UserPaginationContainer getUsers(Main main, @Nullable String paginationToken, Integer limit,
                                                   String timeJoinedOrder)
            throws StorageQueryException, UserPaginationToken.InvalidTokenException {
        UserInfo[] users;
        if (paginationToken == null) {
            users = StorageLayer.getEmailPasswordStorage(main).getUsers(limit + 1, timeJoinedOrder);
        } else {
            UserPaginationToken tokenInfo = UserPaginationToken.extractTokenInfo(paginationToken);
            users = StorageLayer.getEmailPasswordStorage(main)
                    .getUsers(tokenInfo.userId, tokenInfo.timeJoined, limit + 1, timeJoinedOrder);
        }
        String nextPaginationToken = null;
        int maxLoop = users.length;
        if (users.length == limit + 1) {
            maxLoop = limit;
            nextPaginationToken = new UserPaginationToken(users[limit].id, users[limit].timeJoined).generateToken();
        }
        User[] resultUsers = new User[maxLoop];
        for (int i = 0; i < maxLoop; i++) {
            resultUsers[i] = new User(users[i].id, users[i].email, users[i].timeJoined);
        }
        return new UserPaginationContainer(resultUsers, nextPaginationToken);
    }

    public static long getUsersCount(Main main) throws StorageQueryException {
        return StorageLayer.getEmailPasswordStorage(main).getUsersCount();
    }
}
