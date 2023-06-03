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

import com.google.gson.*;
import io.supertokens.Main;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.thirdparty.UserInfo;
import io.supertokens.thirdparty.ThirdParty;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.utils.SemVer;
import io.supertokens.utils.Utils;
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
        // this API is tenant specific
        try {
            TenantIdentifierWithStorage tenantIdentifierWithStorage = this.getTenantIdentifierWithStorageFromRequest(req);
            AppIdentifierWithStorage appIdentifierWithStorage = this.getAppIdentifierWithStorage(req);

            String email = InputParser.getQueryParamOrThrowError(req, "email", false);
            email = Utils.normaliseEmail(email);
            UserInfo[] users = ThirdParty.getUsersByEmail(tenantIdentifierWithStorage, email);

            // return the externalUserId if a mapping exists for a user
            for (int i = 0; i < users.length; i++) {
                // we intentionally do not use the function that accepts an array of user IDs to get the mapping cause
                // this is simpler to use, and cause there shouldn't be that many userIds per email anyway
                io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                        .getUserIdMapping(appIdentifierWithStorage, users[i].id, UserIdType.SUPERTOKENS);
                if (userIdMapping != null) {
                    users[i].id = userIdMapping.externalUserId;
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("status", "OK");
            JsonArray usersJson = new JsonParser().parse(new Gson().toJson(users)).getAsJsonArray();

            if (getVersionFromRequest(req).lesserThan(SemVer.v3_0)) {
                for (JsonElement user : usersJson) {
                    user.getAsJsonObject().remove("tenantIds");
                }
            }

            result.add("users", usersJson);

            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }
}
